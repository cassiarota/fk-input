# Sensitive-word list sources

`sensitive-words.txt` combines unique entries from these pinned source files. Each entry is one UTF-8 line. The list excludes stop words and URL lists; inclusion does not mean that every use of a term is unsafe.

- [fwwdn/sensitive-stop-words](https://github.com/fwwdn/sensitive-stop-words), commit `a7d06bb1c321e669943b6841570d9da6dad8ce2b`: `广告.txt`, `政治类.txt`, `涉枪涉爆违法信息关键词.txt`, and `色情类.txt`. Licensed under Apache-2.0; see `LICENSE.sensitive-stop-words`.
- [konsheng/Sensitive-lexicon](https://github.com/konsheng/Sensitive-lexicon), commit `d967c30b053fa40b06c5a0dddf0be493f2dfae46`: `Vocabulary/暴恐词库.txt`, `Vocabulary/其他词库.txt`, and `Vocabulary/补充词库.txt`. Licensed under MIT; see `LICENSE.Sensitive-lexicon`.

FK Input copies this list to its private `files/sensitive-words.txt` only when that file does not exist. Later additions belong to the user file. This list does not yet affect candidate generation or message filtering.
