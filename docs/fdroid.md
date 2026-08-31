# Publishing BlowTorch 2 on F-Droid

F-Droid builds from source and signs with their key. Do not copy this recipe
until the placeholders are filled **after** a GitHub tag exists. Do not submit
the `btTest` flavor.

The in-tree YAML is a skeleton (`X.Y.Z` / `NNN` / `PUT_FULL_SHA_HERE`). Copy it
into [fdroiddata](https://gitlab.com/fdroid/fdroiddata) only after
`git rev-parse 'vX.Y.Z^{commit}'`.

## What is in the tree

| Path | Purpose |
|------|---------|
| `fastlane/metadata/android/en-US/` | Title, short/full description, changelog, icon. Phone screenshots must be dummy-host shots on the tag you submit. |
| `metadata/com.resurrection.blowtorch2.yml` | Skeleton to copy into fdroiddata (same filename) |
| `BT_Free/build.gradle` | `production` flavor; release unsigned unless `BT_LOCAL_SIGN=1` |
| `./build_ndk_libraries.sh` | Builds native `.so` files into `BTLib/libs/` before Gradle assemble |

Package id: **`com.resurrection.blowtorch2`**. The F-Droid recipe appends
`blowtorch.fdroid=true` so that APK defaults the GitHub update check **off**.
GitHub APKs do not set that property.

## Checklist before opening the MR

1. Tag a release on GitHub (`vX.Y.Z`, three numbers, no `-test`).
2. Put that tag's full commit SHA in `commit:`.
3. Confirm local build:

   ```bash
   export ANDROID_HOME=…
   export NDK_HOME=…   # NDK r26+
   ./build_ndk_libraries.sh
   ./gradlew :BT_Free:assembleProductionRelease
   ```

   APK under `BT_Free/build/outputs/apk/production/release/`, unsigned unless
   `BT_LOCAL_SIGN=1`. This local command does **not** set `blowtorch.fdroid`;
   the APK still defaults the GitHub update check on. F-Droid CI appends that
   property in `prebuild`.
4. Dummy-host screenshots under
   `fastlane/metadata/android/en-US/images/phoneScreenshots/` (PNG, portrait).
5. Optional: `featureGraphic.png` (1024×500) next to `icon.png`.

## Submit

1. Fork fdroiddata on GitLab (public fork).
2. Copy this repo's YAML into fdroiddata `metadata/` (same filename). Do not
   copy Fastlane into fdroiddata.
3. Open an **App inclusion** merge request.
   [Submitting to F-Droid](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/).
4. First inclusion is days to weeks. Do not open an RFP in parallel.

## Notes for reviewers

- **License:** MIT.
- **No proprietary deps** in the production Gradle graph.
- **Permissions / All files access:** [`FDROID_README.md`](FDROID_README.md).
  Connecting does not need the grant. Import/export/backup use SAF or app
  storage without opening the All-files screen first. The grant is only so
  `/BlowTorch/` is visible in a file manager.
- **LuaJIT 2.1** is vendored in `LuaJIT-2.1/`. No srclib, no download during
  the build. `HOST_CC="gcc -m32"` needs `gcc-multilib`.
- Submit **`production`** only. Prebuild sets `blowtorch.fdroid=true`.
- Fastlane text is read from the tagged commit.
