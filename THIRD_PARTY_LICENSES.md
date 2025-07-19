# Third-Party Licenses

## Android Jetpack (Compose, AppCompat, Lifecycle, etc.)

License: Apache License 2.0
https://developer.android.com/jetpack

## ffmpeg-kit

License: GPL-3.0
https://github.com/arthenica/ffmpeg-kit

Cause of missing maintainability and build complications, building this library during the app build
was not an option. I decided to build and provide it separately:
https://github.com/scovillo/ffmpeg-kit/releases/tag/main

```bash
android.sh --enable-gnutls --disable-x86 --disable-x86-64 --disable-arm-v7a --api-level=21
```

Contained libraries in this build:

- **android-zlib**
  License: zlib/libpng License  
  https://github.com/madler/zlib

- **GNU MP (GMP)**
  License: LGPL-3.0 or GPL-3.0
  https://gmplib.org/

- **GnuTLS**
  License: LGPL-2.1 or GPL-compatible
  https://www.gnutls.org/

- **libiconv**
  License: LGPL-2.1
  https://www.gnu.org/software/libiconv/

## NewPipeExtractor

License: GPL-3.0
https://github.com/TeamNewPipe/NewPipeExtractor

## OkHttp

License: Apache License 2.0
https://square.github.io/okhttp/

## nanohttpd

License: BSD 3-Clause
https://github.com/NanoHttpd/nanohttpd

## smart-exception-java

License: BSD 3-Clause  
https://github.com/arthenica/ffmpeg-kit
