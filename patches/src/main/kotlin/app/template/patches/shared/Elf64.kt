package app.template.patches.shared

import app.morphe.patcher.patch.PatchException

/**
 * A minimal reader for ELF64 little endian shared libraries (AArch64).
 *
 * Patches use it to work with what a library declares about itself instead of
 * hardcoded file offsets, so they keep working when a new release shuffles code
 * around:
 *
 * - [symbol] finds a function by its mangled name, which stays the same as long
 *   as the C++ signature does not change,
 * - [findPltStub] finds the PLT stub of an imported function, so rewritten code
 *   can call into the runtime (for example a `QVariant` constructor) without
 *   knowing where the linker placed anything,
 * - [offsetOf] converts a virtual address into a file offset.
 *
 * Unsupported or malformed libraries fail with a [PatchException] instead of
 * being patched blindly.
 */
internal class Elf64(private val bytes: ByteArray) {

    /** An entry of the dynamic symbol table. */
    internal class Symbol(
        val name: String,
        val address: Long,
        val size: Long,
        /** Index in `.dynsym`, which is what relocation tables refer to. */
        val index: Int,
    )

    private class Section(
        var name: String,
        val type: Long,
        /** Virtual address the section is mapped at. */
        val address: Long,
        /** File offset the section starts at. */
        val offset: Int,
        val size: Int,
        val entrySize: Int,
    ) {
        val endAddress get() = address + size
        val endOffset get() = offset + size
    }

    private val sections: List<Section> = readSections()
    private val sectionsByName: Map<String, Section> = sections.associateBy { it.name }
    private val symbolsByName: Map<String, Symbol> by lazy { readSymbols() }

    /** The dynamic symbol named [name]. */
    fun symbol(name: String): Symbol = symbolsByName[name]
        ?: throw PatchException("Native library has no \"$name\" symbol")

    /**
     * Address of the PLT stub that calls [name]. Tail calling it is how patched
     * code reaches an imported function without knowing its address.
     *
     * @throws PatchException If the symbol is not imported, or has no PLT entry.
     */
    fun findPltStub(name: String): Long {
        val symbol = symbol(name)
        val slot = readGotSlots()[symbol.index]
            ?: throw PatchException("Native library has no PLT relocation for \"$name\"")
        return readPltStubs()[slot]
            ?: throw PatchException("Native library has no PLT stub for \"$name\"")
    }

    /** File offset of the data mapped at the virtual [address]. */
    fun offsetOf(address: Long): Int {
        val section = sections.firstOrNull {
            it.type != SHT_NOBITS && it.size > 0 && address >= it.address && address < it.endAddress
        } ?: throw PatchException("Virtual address 0x${address.toString(16)} belongs to no section")

        return (section.offset + (address - section.address)).toInt()
    }

    /** The section named [name], or null when the library has no such section. */
    private fun section(name: String): Section? = sectionsByName[name]

    private fun readSections(): List<Section> {
        checkFileHeader()

        val tableOffset = u64(0x28)
        if (tableOffset == 0L) throw PatchException("Native library has no section headers")

        val entrySize = u16(0x3A).takeIf { it >= SECTION_HEADER_SIZE } ?: SECTION_HEADER_SIZE
        var count = u16(0x3C)
        // Extended numbering stores the real count in the size field of section 0.
        if (count == 0) count = u64(tableOffset.toInt() + 0x20).toInt()
        if (count <= 0) throw PatchException("Native library has no section headers")

        val nameTableIndex = u16(0x3E)
        val nameOffsets = IntArray(count)
        val parsed = ArrayList<Section>(count)
        for (index in 0 until count) {
            val header = (tableOffset + index.toLong() * entrySize).toInt()
            ensure(header, SECTION_HEADER_SIZE)
            nameOffsets[index] = u32(header).toInt()
            parsed += Section(
                name = "",
                type = u32(header + 4),
                address = u64(header + 0x10),
                offset = u64(header + 0x18).toInt(),
                size = u64(header + 0x20).toInt(),
                entrySize = u64(header + 0x38).toInt(),
            )
        }

        val nameTable = parsed.getOrNull(nameTableIndex)
            ?: throw PatchException("Native library has no section name table")
        parsed.forEachIndexed { index, section ->
            section.name = cString(nameTable.offset + nameOffsets[index])
        }

        return parsed
    }

    private fun readSymbols(): Map<String, Symbol> {
        val symbols = section(".dynsym") ?: throw PatchException("Native library has no .dynsym section")
        val names = section(".dynstr") ?: throw PatchException("Native library has no .dynstr section")
        val entrySize = symbols.entrySize.takeIf { it > 0 } ?: SYMBOL_SIZE

        val result = LinkedHashMap<String, Symbol>()
        var index = 0
        var entry = symbols.offset
        while (entry + entrySize <= symbols.endOffset) {
            val nameOffset = u32(entry).toInt()
            if (nameOffset != 0) {
                val name = cString(names.offset + nameOffset)
                result[name] = Symbol(
                    name = name,
                    address = u64(entry + 8),
                    size = u64(entry + 16),
                    index = index,
                )
            }
            index++
            entry += entrySize
        }

        return result
    }

    /** GOT slot backing each symbol, from the `R_AARCH64_JUMP_SLOT` entries of `.rela.plt`. */
    private fun readGotSlots(): Map<Int, Long> {
        val relocations = section(".rela.plt") ?: return emptyMap()
        val entrySize = relocations.entrySize.takeIf { it > 0 } ?: RELOCATION_SIZE

        val result = HashMap<Int, Long>()
        var entry = relocations.offset
        while (entry + entrySize <= relocations.endOffset) {
            val info = u64(entry + 8)
            if ((info and 0xFFFFFFFFL).toInt() == R_AARCH64_JUMP_SLOT) {
                result[(info ushr 32).toInt()] = u64(entry)
            }
            entry += entrySize
        }

        return result
    }

    private fun readPltStubs(): Map<Long, Long> {
        val plt = section(".plt") ?: section(".plt.sec") ?: return emptyMap()

        val result = HashMap<Long, Long>()
        var entry = plt.offset
        while (entry + PLT_ENTRY_SIZE <= plt.endOffset) {
            val stub = plt.address + (entry - plt.offset)
            decodePltStub(entry, stub)?.let { result[it] = stub }
            entry += PLT_ENTRY_SIZE
        }

        return result
    }

    /**
     * Decodes `adrp x16, <page>; ldr x17, [x16, <offset>]; br x17` and returns the
     * GOT slot it loads, or null for anything else in `.plt` (the header entry,
     * or a stub that was not emitted in this shape).
     *
     * The `add x16, x16, <offset>` that often sits between the first two
     * instructions only feeds the lazy resolver: the `ldr` already carries the
     * complete address, so it is deliberately ignored.
     */
    private fun decodePltStub(offset: Int, stub: Long): Long? {
        val adrp = u32(offset).toInt()
        val load = u32(offset + 4).toInt()
        val branch = u32(offset + 12).toInt()

        if ((adrp and ADRP_MASK) != ADRP_PATTERN || (adrp and REGISTER_MASK) != REGISTER_X16) return null
        if ((load and LOAD_MASK) != LOAD_PATTERN || (load and REGISTER_MASK) != REGISTER_X17) return null
        if (((load ushr 5) and REGISTER_MASK) != REGISTER_X16) return null
        if (branch != BRANCH_X17) return null

        // adrp immediates are two 19/2 bit fields combined and sign extended from 21 bits.
        val low = (adrp ushr 29) and 0x3
        var immediate = (((adrp ushr 5) and 0x7FFFF) shl 2) or low
        if ((immediate and (1 shl 20)) != 0) immediate -= 1 shl 21

        // adrp addresses its page, the load adds the byte offset within it.
        val page = ((stub ushr 12) shl 12) + (immediate.toLong() shl 12)
        val displacement = ((load ushr 10) and 0xFFF).toLong() * 8L

        return page + displacement
    }

    private fun checkFileHeader() {
        if (bytes.size < ELF_HEADER_SIZE) {
            throw PatchException("Native library is too small to be an ELF file (${bytes.size} bytes)")
        }
        if (bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() ||
            bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) {
            throw PatchException("File is not an ELF library")
        }
        if (bytes[4].toInt() != ELF_CLASS_64) {
            throw PatchException("Native library is not 64 bit (ELF class ${bytes[4].toInt()})")
        }
        if (bytes[5].toInt() != ELF_DATA_LSB) {
            throw PatchException("Native library is not little endian")
        }
    }

    private fun ensure(offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > bytes.size - length) {
            throw PatchException("Native library is truncated: read $length bytes at $offset of ${bytes.size}")
        }
    }

    private fun u8(offset: Int): Int {
        ensure(offset, 1)
        return bytes[offset].toInt() and 0xFF
    }

    private fun u16(offset: Int): Int {
        ensure(offset, 2)
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun u32(offset: Int): Long {
        ensure(offset, 4)
        return u16(offset).toLong() or (u16(offset + 2).toLong() shl 16)
    }

    private fun u64(offset: Int): Long {
        ensure(offset, 8)
        var value = 0L
        for (byte in 7 downTo 0) value = (value shl 8) or (bytes[offset + byte].toLong() and 0xFFL)
        return value
    }

    private fun cString(offset: Int): String {
        var end = offset
        while (true) {
            ensure(end, 1)
            if (bytes[end] == 0.toByte()) break
            end++
        }
        return String(bytes, offset, end - offset, Charsets.UTF_8)
    }

    private companion object {
        const val ELF_HEADER_SIZE = 0x40
        const val SECTION_HEADER_SIZE = 0x40
        const val SYMBOL_SIZE = 24
        const val RELOCATION_SIZE = 24
        const val PLT_ENTRY_SIZE = 16

        const val ELF_CLASS_64 = 2
        const val ELF_DATA_LSB = 1

        /** Section type of a section that occupies no bytes in the file. */
        const val SHT_NOBITS = 8L

        const val R_AARCH64_JUMP_SLOT = 1026

        const val REGISTER_MASK = 0x1F
        const val REGISTER_X16 = 0x10
        const val REGISTER_X17 = 0x11

        val ADRP_MASK = 0x9F000000.toInt()
        val ADRP_PATTERN = 0x90000000.toInt()

        /** `ldr x17, [x16, #imm]`, 64 bit, unsigned offset. */
        val LOAD_MASK = 0xFFC00000.toInt()
        val LOAD_PATTERN = 0xF9400000.toInt()

        val BRANCH_X17 = 0xD61F0220.toInt()
    }
}
