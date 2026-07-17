# Publishing BlowTorch 2 on F-Droid

This repo is prepared for an [fdroiddata](https://gitlab.com/fdroid/fdroiddata) merge request. F-Droid builds from source and signs with their key; we do not ship a Play-style release keystore in the recipe.

## What is already in the tree

| Path | Purpose |
|------|---------|
| `fastlane/metadata/android/en-US/` | Title, short/full description, changelog (`changelogs/225.txt`), icon, phone screenshots |
| `metadata/com.resurrection.blowtorch2.yml` | Draft recipe to copy into fdroiddata (`metadata/com.resurrection.blowtorch2.yml`) |
| `BT_Free/build.gradle` | `production` flavor; release unsigned unless `BT_LOCAL_SIGN=1` |
| `./build_ndk_libraries.sh` | Builds native `.so` files into `BTLib/libs/` before Gradle assemble |

Package id for the store: **`com.resurrection.blowtorch2`** (flavor `production`). Do not submit the `btTest` flavor to F-Droid.

## Checklist before opening the MR

1. **Tag a release** on GitHub (e.g. `v2.1.1`) from the commit you want F-Droid to build.
2. In `metadata/com.resurrection.blowtorch2.yml`, set `commit:` to that tag (or exact SHA).
3. Confirm local build with the same steps F-Droid will use:

   ```bash
   export ANDROID_HOME=…
   export NDK_HOME=…   # NDK r26+
   ./build_ndk_libraries.sh
   ./gradlew :BT_Free:assembleProductionRelease
   ```

   The APK should be under `BT_Free/build/outputs/apk/production/release/` and **unsigned** (or only locally signed if you set `BT_LOCAL_SIGN=1`).

4. Optional: add more screenshots under `fastlane/metadata/android/en-US/images/phoneScreenshots/` (PNG, portrait).
5. Optional: `featureGraphic.png` (1024×500) next to `icon.png` for a nicer client header.

## Submit to F-Droid

1. Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata) on GitLab.
2. Copy this repo’s `metadata/com.resurrection.blowtorch2.yml` into fdroiddata’s `metadata/` folder (same filename).
3. Open a merge request following [Submitting to F-Droid](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/).
4. Wait for the buildserver / CI review. First inclusion can take days to weeks.

## Notes for reviewers

- **License:** MIT (original Offset Null + this fork).
- **No proprietary deps** in the production Gradle graph.
- **Permissions / privacy / All files access:** see **[`FDROID_README.md`](FDROID_README.md)** — please read that before flagging storage permissions. Summary: All files access is optional UX for a shared `/BlowTorch/` tree; the client can play without it.
- **scandelete** removes `BTLib/key` (local debug keystore used only for developer installs) and optional unused `LuaJIT-2.1` tree.
- **Mapper (ForgeMap)** is work in progress and excluded from the production APK; do not enable `btTest` in the recipe.
- Fastlane text is read from the tagged commit under `fastlane/metadata/android/`.

## After inclusion

- Bump `versionCode` / `versionName` in `BT_Free/build.gradle`.
- Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
- Tag and push; AutoUpdateMode `Version` + UpdateCheckMode `Tags` should pick it up once the recipe is live (adjust if maintainers prefer another mode).
