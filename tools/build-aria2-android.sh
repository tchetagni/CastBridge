#!/usr/bin/env bash
# Builds aria2c for Android from the OFFICIAL source releases of aria2 and of its libraries, for CastBridge TV.
#
#   tools/build-aria2-android.sh                 # both ABIs, output in android/receiver/src/main/jniLibs/<abi>/libaria2c.so
#   ABIS=armeabi-v7a tools/build-aria2-android.sh
#
# Needs: Android NDK r26 or newer (ANDROID_NDK_HOME, or $ANDROID_HOME/ndk/<version>), curl, perl, make, pkg-config,
# shasum or sha256sum. Runs on Linux and macOS. Nothing prebuilt is downloaded: only source tarballs, each checked against
# the SHA-256 pinned below (the build stops on any mismatch).
#
# Result: a position-independent executable, dynamically linked only against Android's own libc/libm/libdl, with every
# library (OpenSSL, c-ares, libssh2, expat, zlib, libc++) linked in statically, then stripped. It is named libaria2c.so
# because Android (targetSdk >= 29) only lets an app execute files from its nativeLibraryDir, where the package manager
# extracts lib*.so files (the app is packaged with useLegacyPackaging = true / extractNativeLibs="true").
#
# aria2 is GPL-2.0-or-later: whoever distributes the APK must offer this exact corresponding source. This script, with its
# pinned URLs and hashes, and the tarballs it names, are that source (see docs/DOWNLOADS.md, "Licence").
set -euo pipefail

API=26
ABIS="${ABIS:-armeabi-v7a arm64-v8a}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${WORK:-$ROOT/build/aria2-android}"          # build/ is git-ignored
OUT="${OUT:-$ROOT/android/receiver/src/main/jniLibs}"
INFO="${INFO:-$ROOT/tools/aria2-android-build.txt}"
JOBS="${JOBS:-$( (nproc || sysctl -n hw.ncpu) 2>/dev/null || echo 4)}"

# ---- pinned sources: official URL + SHA-256 --------------------------------------------------------------------
ARIA2_VER=1.37.0
ARIA2_URL=https://github.com/aria2/aria2/releases/download/release-$ARIA2_VER/aria2-$ARIA2_VER.tar.xz
# Hashes checked when pinned (2026-09-30): OpenSSL against its published .sha256; aria2, zlib, expat and c-ares against the
# independent Homebrew formulas; libssh2 is the release asset of the official repository (also signed, .asc).
ARIA2_SHA=60a420ad7085eb616cb6e2bdf0a7206d68ff3d37fb5a956dc44242eb2f79b66b
OPENSSL_VER=3.5.9                                    # LTS branch (supported until 2030)
OPENSSL_URL=https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VER/openssl-$OPENSSL_VER.tar.gz
OPENSSL_SHA=603f5602e2eef00d77fbd429d34dcd5822bb301757a1bc9cdb24c670f1eb859a
ZLIB_VER=1.3.2
ZLIB_URL=https://github.com/madler/zlib/releases/download/v$ZLIB_VER/zlib-$ZLIB_VER.tar.gz   # same file as https://zlib.net/
ZLIB_SHA=bb329a0a2cd0274d05519d61c667c062e06990d72e125ee2dfa8de64f0119d16
EXPAT_VER=2.8.5
EXPAT_URL=https://github.com/libexpat/libexpat/releases/download/R_${EXPAT_VER//./_}/expat-$EXPAT_VER.tar.xz
EXPAT_SHA=1e727b8933ec51a77a9a9d9afcf8e688bce45d907c13e36ab7393fe36e703182
CARES_VER=1.34.8
CARES_URL=https://github.com/c-ares/c-ares/releases/download/v$CARES_VER/c-ares-$CARES_VER.tar.gz
CARES_SHA=c222b6d681096f9444d2c4863d2c1174019e27cacca0a4a5c114d36dd7d7bf78
SSH2_VER=1.11.1
SSH2_URL=https://github.com/libssh2/libssh2/releases/download/libssh2-$SSH2_VER/libssh2-$SSH2_VER.tar.xz
SSH2_SHA=9954cb54c4f548198a7cbebad248bdc87dd64bd26185708a294b2b50771e3769
# sqlite3 is left out on purpose: aria2 only uses it to read Firefox/Chromium cookie databases (--load-cookies).

# ---- NDK --------------------------------------------------------------------------------------------------------
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [ -z "$NDK" ]; then
  SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  [ -d "$SDK/ndk" ] && NDK="$(ls -d "$SDK"/ndk/* 2>/dev/null | sort -V | tail -1)"
fi
[ -n "$NDK" ] && [ -f "$NDK/source.properties" ] || { echo "Android NDK not found: set ANDROID_NDK_HOME (r26 or newer)" >&2; exit 1; }
NDK_REV="$(sed -n 's/^Pkg.Revision *= *//p' "$NDK/source.properties")"
[ "${NDK_REV%%.*}" -ge 26 ] || { echo "NDK $NDK_REV is too old: r26 or newer needed" >&2; exit 1; }
case "$(uname -s)" in Darwin) HOSTTAG=darwin-x86_64 ;; Linux) HOSTTAG=linux-x86_64 ;; *) echo "unsupported host" >&2; exit 1 ;; esac
TC="$NDK/toolchains/llvm/prebuilt/$HOSTTAG"

sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }

# Tarballs already in $WORK/dl (copied there by hand, e.g. on a slow link) are used as they are, but still verified.
fetch() {  # url sha
  local f; f="$WORK/dl/$(basename "$1")"
  mkdir -p "$WORK/dl"
  if [ ! -s "$f" ]; then curl -fL --retry 3 -o "$f.tmp" "$1"; mv "$f.tmp" "$f"; fi
  local got; got="$(sha256 "$f")"
  if [ "$got" != "$2" ]; then echo "SHA-256 MISMATCH for $(basename "$1"): got $got, expected $2" >&2; rm -f "$f"; exit 1; fi
  echo "ok  $(basename "$1")  $got"
}

echo "== sources"
fetch "$ARIA2_URL" "$ARIA2_SHA"; fetch "$OPENSSL_URL" "$OPENSSL_SHA"; fetch "$ZLIB_URL" "$ZLIB_SHA"
fetch "$EXPAT_URL" "$EXPAT_SHA"; fetch "$CARES_URL" "$CARES_SHA"; fetch "$SSH2_URL" "$SSH2_SHA"
BUILD_TRIPLE="$(tar -xOf "$WORK/dl/aria2-$ARIA2_VER.tar.xz" "aria2-$ARIA2_VER/config.guess" | sh 2>/dev/null)"

build_abi() {
  local abi=$1 target ossl
  case $abi in
    armeabi-v7a) target=armv7a-linux-androideabi; ossl=android-arm ;;
    arm64-v8a)   target=aarch64-linux-android;    ossl=android-arm64 ;;
    *) echo "unknown ABI $abi" >&2; exit 1 ;;
  esac
  local B="$WORK/$abi" P="$WORK/$abi/prefix"
  rm -rf "$B"; mkdir -p "$B" "$P"
  ( cd "$B"
    for t in "$ARIA2_URL" "$OPENSSL_URL" "$ZLIB_URL" "$EXPAT_URL" "$CARES_URL" "$SSH2_URL"; do tar -xf "$WORK/dl/$(basename "$t")"; done )

  export PATH="$TC/bin:$PATH"
  local CC="$TC/bin/$target$API-clang" CXX="$TC/bin/$target$API-clang++"
  local AR="$TC/bin/llvm-ar" RANLIB="$TC/bin/llvm-ranlib" STRIP="$TC/bin/llvm-strip"
  local CF="-Os -fPIC -fstack-protector-strong -D_FORTIFY_SOURCE=2 -ffunction-sections -fdata-sections"
  local common=(CC="$CC" CXX="$CXX" AR="$AR" RANLIB="$RANLIB" CFLAGS="$CF" CXXFLAGS="$CF" CPPFLAGS="-I$P/include" LDFLAGS="-L$P/lib")

  echo "== [$abi] zlib $ZLIB_VER"
  ( cd "$B/zlib-$ZLIB_VER" && env "${common[@]}" CHOST="$target" ./configure --static --prefix="$P" >/dev/null && make -j"$JOBS" >/dev/null && make install >/dev/null )

  echo "== [$abi] OpenSSL $OPENSSL_VER"
  ( cd "$B/openssl-$OPENSSL_VER" && env -u CC -u CXX ANDROID_NDK_ROOT="$NDK" ./Configure "$ossl" -D__ANDROID_API__=$API \
      no-shared no-tests no-docs no-apps no-engine no-module no-legacy no-dso no-comp --prefix="$P" --libdir=lib -Os >/dev/null \
    && make -j"$JOBS" build_libs >/dev/null && make install_dev >/dev/null )

  echo "== [$abi] expat $EXPAT_VER"
  ( cd "$B/expat-$EXPAT_VER" && env "${common[@]}" ./configure --host="$target" --build="$BUILD_TRIPLE" --prefix="$P" \
      --disable-shared --enable-static --without-docbook --without-examples --without-tests --without-xmlwf >/dev/null \
    && make -j"$JOBS" >/dev/null && make install >/dev/null )

  echo "== [$abi] c-ares $CARES_VER"
  ( cd "$B/c-ares-$CARES_VER" && env "${common[@]}" ./configure --host="$target" --build="$BUILD_TRIPLE" --prefix="$P" \
      --disable-shared --enable-static --disable-tests >/dev/null && make -j"$JOBS" >/dev/null && make install >/dev/null )

  echo "== [$abi] libssh2 $SSH2_VER"
  ( cd "$B/libssh2-$SSH2_VER" && env "${common[@]}" ./configure --host="$target" --build="$BUILD_TRIPLE" --prefix="$P" \
      --disable-shared --enable-static --with-crypto=openssl --with-libssl-prefix="$P" --with-libz --with-libz-prefix="$P" \
      --disable-examples-build --disable-docker-tests >/dev/null && make -j"$JOBS" >/dev/null && make install >/dev/null )

  echo "== [$abi] aria2 $ARIA2_VER"
  ( cd "$B/aria2-$ARIA2_VER" && env "${common[@]}" \
      LDFLAGS="-L$P/lib -static-libstdc++ -Wl,--gc-sections -pie" CPPFLAGS="-I$P/include -fPIE" \
      PKG_CONFIG="pkg-config --static" PKG_CONFIG_LIBDIR="$P/lib/pkgconfig" PKG_CONFIG_PATH="" \
      ./configure --host="$target" --build="$BUILD_TRIPLE" --prefix="$P" \
      --disable-nls --disable-websocket --without-gnutls --with-openssl --without-sqlite3 --without-libxml2 --with-libexpat \
      --with-libcares --with-libz --with-libssh2 --without-libuv --without-appletls --without-wintls --without-libgcrypt \
      --without-libnettle --without-libgmp --without-jemalloc --without-tcmalloc ARIA2_STATIC=no > "$B/aria2-configure.log" \
    && tail -25 "$B/aria2-configure.log" \
    && make -j"$JOBS" >/dev/null )

  mkdir -p "$OUT/$abi"
  "$STRIP" --strip-unneeded -o "$OUT/$abi/libaria2c.so" "$B/aria2-$ARIA2_VER/src/aria2c"
  chmod 755 "$OUT/$abi/libaria2c.so"
  local needed; needed="$("$TC/bin/llvm-readelf" -d "$OUT/$abi/libaria2c.so" | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p' | tr '\n' ' ')"
  echo "   -> $OUT/$abi/libaria2c.so  $(wc -c < "$OUT/$abi/libaria2c.so") bytes, needs: $needed"
  printf '%s  libaria2c.so  %s bytes  sha256 %s  needs: %s\n' "$abi" "$(wc -c < "$OUT/$abi/libaria2c.so" | tr -d ' ')" \
    "$(sha256 "$OUT/$abi/libaria2c.so")" "$needed" >> "$INFO.tmp"
}

: > "$INFO.tmp"
for abi in $ABIS; do build_abi "$abi"; done

{
  echo "aria2c for CastBridge TV, built by tools/build-aria2-android.sh"
  echo "NDK $NDK_REV ($HOSTTAG), API $API, $(date -u +%Y-%m-%d)"
  echo "aria2 $ARIA2_VER ($ARIA2_URL, sha256 $ARIA2_SHA)"
  echo "OpenSSL $OPENSSL_VER ($OPENSSL_URL, sha256 $OPENSSL_SHA)"
  echo "zlib $ZLIB_VER ($ZLIB_URL, sha256 $ZLIB_SHA)"
  echo "expat $EXPAT_VER ($EXPAT_URL, sha256 $EXPAT_SHA)"
  echo "c-ares $CARES_VER ($CARES_URL, sha256 $CARES_SHA)"
  echo "libssh2 $SSH2_VER ($SSH2_URL, sha256 $SSH2_SHA)"
  echo
  cat "$INFO.tmp"
} > "$INFO"
rm -f "$INFO.tmp"
echo "== done"; cat "$INFO"
