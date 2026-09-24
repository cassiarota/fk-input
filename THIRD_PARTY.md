# Third-party notices

| Component | Purpose | License | Source and pinned version |
| --- | --- | --- | --- |
| librime | Local Pinyin engine | BSD-3-Clause | [rime/librime](https://github.com/rime/librime); the binary comes from the pinned prebuilt commit below |
| glog | Static dependency of librime | BSD-3-Clause | [google/glog](https://github.com/google/glog) |
| LevelDB | Static dependency of librime | BSD-3-Clause | [google/leveldb](https://github.com/google/leveldb) |
| marisa-trie | Static dependency of librime | BSD-2-Clause OR LGPL-2.1-or-later; this project uses the BSD-2-Clause option | [s-yata/marisa-trie](https://github.com/s-yata/marisa-trie/blob/master/COPYING.md) |
| Lua | Static dependency of librime | MIT | [lua.org/license.html](https://www.lua.org/license.html) |
| OpenCC | Static dependency of librime | Apache-2.0 | [BYVoid/OpenCC](https://github.com/BYVoid/OpenCC) |
| yaml-cpp | Static dependency of librime | MIT | [jbeder/yaml-cpp](https://github.com/jbeder/yaml-cpp) |
| rime-pinyin-simp | Pinyin dictionary and word frequencies | Apache-2.0 | [rime/rime-pinyin-simp](https://github.com/rime/rime-pinyin-simp/tree/0c6861ef7420ee780270ca6d993d18d4101049d0), commit `0c6861ef7420ee780270ca6d993d18d4101049d0`; its dictionary notes derivation from AOSP PinyinIME |
| Unihan 17.0.0 `kMandarin` | Pronunciations converted to `readings.tsv` at build time | Unicode License v3 | [Unicode Unihan data](https://www.unicode.org/Public/17.0.0/ucd/Unihan.zip) |
| OkHttp / Okio | WSS client | Apache-2.0 | [square/okhttp](https://github.com/square/okhttp) / [square/okio](https://github.com/square/okio); OkHttp 4.12.0 |
| Kotlin standard library | Android client | Apache-2.0 | [JetBrains/kotlin](https://github.com/JetBrains/kotlin); version 2.2.20 |
| sensitive-stop-words | Bundled default sensitive-word entries | Apache-2.0 | [fwwdn/sensitive-stop-words](https://github.com/fwwdn/sensitive-stop-words), commit `a7d06bb1c321e669943b6841570d9da6dad8ce2b` |
| Sensitive-lexicon | Bundled default sensitive-word entries | MIT | [konsheng/Sensitive-lexicon](https://github.com/konsheng/Sensitive-lexicon), commit `d967c30b053fa40b06c5a0dddf0be493f2dfae46` |

The ARM64 static libraries are fetched from [fcitx5-android/prebuilt](https://github.com/fcitx5-android/prebuilt/tree/e9a277e3f3151f7b978945a3f8701285146d609d) at commit `e9a277e3f3151f7b978945a3f8701285146d609d`. This pins the prebuilt artifacts, not the source commits of every upstream library.

Apache-2.0, Unicode, BSD, and MIT license texts and attributions are packaged in `app/src/main/assets/licenses/`. This project does not reuse the GPL-licensed Trime client, JNI bridge, or skin assets.
