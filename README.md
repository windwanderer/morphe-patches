# 👋🧩 Morphe Patches template

Template repository for Morphe Patches.

## ❓ About

Patches for apps I like.

<!-- TODO: Update this about section with a brief introduction/summary about this repo and what it offers. -->

### How to use these patches

Click the button below, or manually add this repo url to Morphe Manager: https://github.com/xyz-user/xyz-patches

<a href="https://morphe.software/add-source?github=xyz-user/xyz-patches">
  <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
</a>

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.11.0-dev.15](https://github.com/windwanderer/morphe-patches/releases/tag/v1.11.0-dev.15)**&nbsp;&nbsp;&middot;&nbsp;&nbsp;`dev`&nbsp;&nbsp;&middot;&nbsp;&nbsp;**5 patches** across **2 apps**&nbsp;&nbsp;&middot;&nbsp;&nbsp;[Full details](PATCHES.md)

| # | App | Patches | Version | Package |
|---|---|---|---|---|
| 1 | [**MetService**](PATCHES.md#metservice-commetservicekryten) | 2 | `2.37.1`, `2.35.1`, `2.35.0`, `2.34.1`, `2.33.0` | [`com.metservice.kryten`](https://play.google.com/store/apps/details?id=com.metservice.kryten) |
| 2 | [**Stellarium**](PATCHES.md#stellarium-comnoctuasoftwarestellarium_free) | 3 | `1.16.8`, `1.16.7`, `1.16.5`, `1.16.3` | [`com.noctuasoftware.stellarium_free`](https://play.google.com/store/apps/details?id=com.noctuasoftware.stellarium_free) |
<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## 📜 License

UserXYZ Patches are licensed under the [GNU General Public License v3.0](LICENSE)
