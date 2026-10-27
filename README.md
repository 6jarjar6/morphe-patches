# 6jarjar6 Patches

Personal patch bundle for Morphe

## AI disclaimer

These patches were developed with AI assistance. Each patch was statically verified at the time of release. These patches are provided as-is, without a warranty of any kind.

---

### How to use these patches

Click here to add these patches to Morphe: https://morphe.software/add-source?github=6jarjar6/morphe-patches

## Patches list

<!-- PATCHES_START -->
> **[v1.0.0-dev.1](https://github.com/6jarjar6/morphe-patches/releases/tag/v1.0.0-dev.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;3 patches total
<details open>
<summary>📦 Bejeweled Classic&nbsp;&nbsp;•&nbsp;&nbsp;3 patches</summary>
<br>

**🎯 Supported versions:**

| 4.0.0 | 3.9.1 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Ad-free](#ad-free) | Removes ads. Rewarded ads still grant their reward without playing. |  |
| [Privacy-disabled](#privacy-disabled) | Forces the internal 'usage sharing' and 'targeted ads' privacy flags off. |  |
| [Telemetry-disabled](#telemetry-disabled) | Stops Nimble/Synergy telemetry from being posted to the server. |  |

</details>

<!-- PATCHES_END -->

### Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## License

6jarjar6 Patches are licensed under the [GNU General Public License v3.0](LICENSE)
