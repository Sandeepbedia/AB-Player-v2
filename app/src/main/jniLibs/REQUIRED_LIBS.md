# Required Native Libraries

Place the following `.so` files inside the matching architecture folder.
Gradle (`useLegacyPackaging = true`) will copy them as-is into the final
APK's `lib/<abi>/` folder at compile time — no renaming needed.

## Folders
- `arm64-v8a/`   → 64-bit devices (most modern phones)
- `armeabi-v7a/` → 32-bit devices (older phones)

Same filenames go in **both** folders (get the correct build for each ABI
from the respective library's release — arm64-v8a build in arm64-v8a/,
armeabi-v7a build in armeabi-v7a/).

## Required list
```
libffmpeg.so
libavcodec.so
libavformat.so
libavutil.so
libswresample.so
libswscale.so
libavfilter.so
libavdevice.so
libpostproc.so
libvlc.so
libvlccore.so
libmpv.so
```

## Notes
- `abiFilters` in `app/build.gradle` is already set to `arm64-v8a` and
  `armeabi-v7a` only — matches these two folders.
- `packaging { jniLibs { useLegacyPackaging = true } }` is already set in
  `app/build.gradle`, so these libs get packaged uncompressed and load
  correctly on all devices (no extraction issues).
- After dropping the `.so` files here and rebuilding, verify with:
  `unzip -l app-release.apk | grep "lib/"` to confirm they landed in
  `lib/arm64-v8a/` and `lib/armeabi-v7a/` inside the APK.
- These folders are currently empty (only `.gitkeep` placeholders) — build
  will succeed without the libs, but any code referencing them via
  `System.loadLibrary(...)` will crash with `UnsatisfiedLinkError` until
  the real `.so` files are added.
