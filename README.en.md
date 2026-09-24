# FK Input

[简体中文](README.md)

FK Input is an experimental Android keyboard focused on optional homophone suggestions for Chinese Pinyin. It does not guarantee that a particular game or chat service will accept a suggestion.

## Features

- Nine-key and full QWERTY Pinyin layouts, direct English input, numbers, and punctuation. Portrait and landscape remember their layout choices separately.
- Two independently scrollable Chinese candidate rows: homophone rewrites above, ordinary Pinyin candidates below. Up to five candidates are initially visible in each row.
- A local, editable global term list. A complete matching term is rewritten character by character; long-pressing an ordinary candidate adds the whole term without submitting it.
- A local Pinyin preference queue shared by the two layouts. Choosing an ordinary candidate moves it forward by exactly one position; homophone and speech selections do not train the queue.
- Optional push-to-talk recognition through a user-configured, compatible WSS service. The keyboard has no built-in service address or credential.

## Install and use

The project targets Android 10+ (`minSdk 29`) and currently builds an ARM64 APK. [Download the signed ARM64 pre-release APK](https://github.com/cassiarota/fk-input/releases/tag/v0.1.0), or follow [Build and test](#build-and-test) to build a debug APK and install `app/build/outputs/apk/debug/app-debug.apk`. No APK or signing material is committed to Git.

Open FK Input, enable it in Android's input-method settings, and select it as the current keyboard. Android shows its standard warning when enabling any third-party keyboard. Portrait defaults to nine-key; landscape defaults to full QWERTY. Use the keyboard toolbar to change either layout.

In Chinese mode, Space, punctuation, and Enter submit the first homophone candidate before the following character/action. If a listed term has no valid homophone, the original term is **not** submitted automatically; choose an ordinary candidate explicitly. Manage terms, UTF-8 import/export, and learning reset in the app's settings. The bundled example terms are removable.

Password fields disable suggestions, learning, and speech input.

## Optional speech service

Speech is off until you grant microphone permission and enter both a WSS endpoint and an access credential in the app's settings. Both values are encrypted locally with Android Keystore; neither is bundled in the APK. Nine-key uses a long press on `0`, and full QWERTY uses a long press on Space. Hold to record, release to finish, or swipe up to cancel. Recording stops after 30 seconds.

The client sends a `start` message followed by approximately 100 ms frames of 16 kHz mono PCM16 audio. A compatible service responds with `ready`, revisable `partial` events, `final` events keyed by `segment_id`, and `done` after `end`. The client uses a Bearer authorization header. Recognition text becomes a candidate only after `done`. The client does not persist audio or recognized text; the retention policy of any service you configure is outside this app's control. Network or authentication failures leave existing keyboard input intact.

## Build and test

Use JDK 17, Android Gradle Plugin 8.13.2, Gradle 8.13, Kotlin 2.2.20, Android SDK 36, NDK 28.0.13004108, and CMake 3.22.1. Set `ANDROID_HOME` or provide a local, untracked `local.properties`.

```sh
./scripts/fetch-native.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

The native fetch script downloads pinned ARM64 archives outside Git's tracked files. The JNI bridge is original to this project; no Trime client or JNI code is used. Rime's user-frequency learning is disabled so the local SQLite queue controls ordering.

To sign a release build, keep your keystore outside the repository. Set `FK_SIGNING_KEYSTORE`, `FK_SIGNING_ALIAS`, and `FK_SIGNING_PASSWORD` in your local environment or secret manager, then run `./scripts/sign-release.sh`. Do not put signing values in tracked files or shell history. The resulting `release/` APK is ignored by Git.

## Current validation

The Android 36 ARM64 emulator has been used to verify installation, keyboard registration, portrait nine-key and landscape QWERTY candidates, password-field behavior, and long-press term insertion. JVM tests cover candidate rules and ordering; device tests cover SQLite persistence and mock-WebSocket speech events. A physical-phone test and real-audio test with a user-configured service are still pending.

## Licenses

See [third-party notices](THIRD_PARTY.md) and the license texts packaged under `app/src/main/assets/licenses/`. No license has yet been granted for the original FK Input application code; public repository visibility alone does not grant reuse rights.
