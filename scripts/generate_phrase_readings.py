#!/usr/bin/env python3
"""Generate contextual tone overrides offline; requires pypinyin==0.55.0."""

import argparse
from importlib.metadata import version
from pathlib import Path

from pypinyin import Style, lazy_pinyin, load_phrases_dict


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("dictionaries", nargs="+", type=Path)
    parser.add_argument("--readings", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    assert version("pypinyin") == "0.55.0", "Use the pinned pronunciation data version"
    # Common spoken neutral tones absent from the upstream phrase list.
    load_phrases_dict({
        "谢谢": [["xiè"], ["xie"]],
        "骂得": [["mà"], ["de"]],
        "干嘛": [["gàn"], ["má"]],
    })
    defaults = {}
    for line in args.readings.read_text(encoding="utf-8").splitlines():
        if not line.startswith("#"):
            char, syllable, tone, _ = line.split("\t")
            defaults[char] = syllable + tone
    phrases = {}
    for dictionary in args.dictionaries:
        for line in dictionary.read_text(encoding="utf-8").splitlines():
            fields = line.split("\t")
            if len(fields) < 3 or len(fields[0]) < 2:
                continue
            text, spelling = fields[:2]
            tones = lazy_pinyin(text, style=Style.TONE3, neutral_tone_with_five=True)
            if len(tones) != len(text) or any(not tone[-1:].isdigit() for tone in tones):
                continue
            if " ".join(tone[:-1] for tone in tones) != spelling:
                continue
            if tones != [defaults.get(char) for char in text]:
                phrases[text] = " ".join(tones)
    args.output.write_text(
        "# Contextual readings from pypinyin 0.55.0; see THIRD_PARTY.md.\n"
        + "".join(f"{text}\t{tones}\n" for text, tones in sorted(phrases.items())),
        encoding="utf-8",
    )
    print(f"Wrote {len(phrases)} contextual phrase readings")


if __name__ == "__main__":
    main()
