# FK Input

[简体中文](README.md)

FK Input is an experimental Android keyboard focused on optional homophone suggestions for Chinese Pinyin. It does not guarantee that a particular game or chat service will accept a suggestion.

## Features

- QWERTY Pinyin in both portrait and landscape, direct English input, numbers, and punctuation. Phone, numeric, email, and URL fields switch to dedicated keys.
- Tap “切换” in the keyboard toolbar to return to the previous keyboard, or hold it to open the system keyboard picker. A tap also opens the picker when no previous keyboard is available.
- Two synchronized Chinese candidate rows: homophone rewrites above and ordinary words below, paired by column. Up to five columns are initially visible. Replacements require identical Pinyin and tones, including neutral tones; an unavailable rewrite appears as a disabled `—`.
- Complete dictionary words and frequencies take priority. A supplement adds 112 conversational and gaming expressions, including “妈的” as the first candidate for `made`. Rime sentence conversion is preserved, with local concatenation as a fallback.
- A local, editable homophone-rewrite term list. A complete matching term is rewritten character by character; long-pressing an ordinary candidate adds the whole term without submitting it.
- A local sensitive-word library seeded with 2,482 entries. Bundled and user-added words share one SQLite table and remain synchronized with the private UTF-8 file. In chat apps that expose Android's standard text-selection menu, long-press a message, select text, and choose “加入敏感词库” to save it. This library does not yet affect candidates or filter messages.
- The editing bar moves the cursor and selects editor text; holding Backspace deletes continuously. In a long Pinyin phrase, “短词/长词” changes the candidate span. Selected segments can be tapped to reopen and revise them before committing the whole phrase.
- A local Pinyin preference queue. Choosing an ordinary candidate moves it forward by exactly one position and updates its paired homophone; homophone and speech selections do not train the queue.
- Optional push-to-talk recognition through a user-configured, compatible WSS service. The keyboard has no built-in service address or credential.

## Install and use

The project targets Android 10+ (`minSdk 29`) and currently builds an ARM64 APK. Download a signed APK from [GitHub Releases](https://github.com/cassiarota/fk-input/releases), or follow [Build and test](#build-and-test) to build a debug APK and install `app/build/outputs/apk/debug/app-debug.apk`. No APK or signing material is committed to Git.

Open FK Input and enable it in Android's input-method settings. Android shows its standard warning when enabling any third-party keyboard. Enabling FK does not replace the current keyboard, so you can keep using your usual keyboard.

When you need FK in a game or another app, select it through the system keyboard switcher or tap “选择 / 切换输入法” in FK's settings. When finished, tap “切换” in FK's toolbar to return directly to the previous keyboard. Hold the key to choose another enabled keyboard; a tap also opens the picker if no previous keyboard is available. Pending input is committed using the existing candidate rules before switching; finish choosing a candidate first if manual selection is required. Android's keyboard selection applies to all apps. FK does not switch automatically when entering or leaving a game.

Both orientations use QWERTY. Previously saved nine-key preferences are ignored. Upgrades refresh the bundled Rime data while preserving user terms and learned ordering.

For example, “妈的” is paired with “妈地” (`ma1 de5`). “吗得” has different tones and is excluded. Contextual phrase readings disambiguate polyphones and neutral tones; uncertain pronunciations or unavailable rewrites leave a placeholder.

In Chinese mode, Space, punctuation, and Enter submit the first homophone candidate before the following character/action. If a listed term has no valid homophone, the original term is **not** submitted automatically; choose an ordinary candidate explicitly. Manage terms, UTF-8 import/export, and learning reset in the app's settings. The bundled example terms are removable.

The first app or keyboard launch copies the bundled sensitive-word list to the app-private `files/sensitive-words.txt`. Later launches preserve the user file. The entry appears through Android's `PROCESS_TEXT` selection action when the chat app supports it. The app shows the selected phrase for confirmation, and duplicate entries leave the file unchanged. See the [source and license notes](app/src/main/assets/data/SENSITIVE-WORDS-SOURCES.md).

Password fields disable suggestions, learning, and speech input.

## Optional speech service

Speech is off until you grant microphone permission and enter both a WSS endpoint and an access credential in the app's settings. Both values are encrypted locally with Android Keystore; neither is bundled in the APK. Long-press Space to start recording. Hold to record, release to finish, or swipe up to cancel. Recording stops after 30 seconds.

The client sends a `start` message followed by approximately 100 ms frames of 16 kHz mono PCM16 audio. A compatible service responds with `ready`, revisable `partial` events, `final` events keyed by `segment_id`, and `done` after `end`. The client uses a Bearer authorization header. Recognition text becomes a candidate only after `done`. The client does not persist audio or recognized text; the retention policy of any service you configure is outside this app's control. Network or authentication failures leave existing keyboard input intact.

## Build and test

Use JDK 17, Android Gradle Plugin 8.13.2, Gradle 8.13, Kotlin 2.2.20, Android SDK 36, NDK 28.0.13004108, and CMake 3.22.1. Set `ANDROID_HOME` or provide a local, untracked `local.properties`.

```sh
./scripts/fetch-native.sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

The native fetch script downloads pinned ARM64 archives outside Git's tracked files. The JNI bridge is original to this project; no Trime client or JNI code is used. Rime's user-frequency learning is disabled so the local SQLite queue controls ordering.

Phrase tones are generated offline; the Android app has no Python runtime dependency. To regenerate after dictionary changes, install `pypinyin==0.55.0` in a virtual environment and run:

```sh
python scripts/generate_phrase_readings.py --readings app/src/main/assets/data/readings.tsv --output app/src/main/assets/data/phrase_readings.tsv app/src/main/assets/data/pinyin_simp.dict.yaml app/src/main/assets/data/conversation.dict.yaml
```

To sign a release build, keep your keystore outside the repository. Set `FK_SIGNING_KEYSTORE`, `FK_SIGNING_ALIAS`, and `FK_SIGNING_PASSWORD` in your local environment or secret manager, then run `./scripts/sign-release.sh`. Do not put signing values in tracked files or shell history. The resulting `release/` APK is ignored by Git.

GitHub Actions signs with the encrypted repository secrets `FK_SIGNING_KEYSTORE_B64`, `FK_SIGNING_ALIAS`, and `FK_SIGNING_PASSWORD`. Pushing a `vX.Y.Z` tag runs unit tests, builds Debug and Release APKs, and publishes the signed APK as that version's GitHub Release marked Latest. Use a new tag for each version; the tag determines the version name and Android `versionCode`. You can also run the workflow manually from Actions to download test artifacts without publishing a Release. Debug and release APKs use different signing keys, so one cannot be installed as an update over the other.

## Current validation

The Android 36 ARM64 emulator has been used to verify installation, keyboard registration, QWERTY candidates in both orientations, password-field behavior, and long-press candidate insertion. Quick switching has been checked for keeping the current keyboard when enabling FK, returning to the previous keyboard on tap, opening the picker on hold or when no previous keyboard is available, committing a completed candidate before switching, and preserving unfinished local candidate input. JVM tests cover candidate rules, ordering, and the sensitive-word file; device tests cover SQLite persistence, Rime data upgrades, common-word ordering, fallback composition, polyphones and neutral tones, committing each paired row and synchronized scrolling, mock-WebSocket speech events, and confirmation, writing, and deduplication through the system text-selection action. Whether a chat app displays this system action depends on its selection UI and has not been checked app by app. A physical-phone test and real-audio test with a user-configured service are still pending.

## Licenses

See [third-party notices](THIRD_PARTY.md) and the license texts packaged under `app/src/main/assets/licenses/`. No license has yet been granted for the original FK Input application code; public repository visibility alone does not grant reuse rights.
