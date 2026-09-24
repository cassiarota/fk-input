#!/usr/bin/env python3
"""Generate the compact on-device Mandarin reading table from pinned Unicode data."""

from __future__ import annotations

import argparse
import unicodedata
import zipfile
from pathlib import Path

TONE_MARKS = {
    "\u0304": 1,
    "\u0301": 2,
    "\u030c": 3,
    "\u0300": 4,
}


def normalize(value: str) -> tuple[str, int]:
    tone = 5
    letters: list[str] = []
    for character in unicodedata.normalize("NFD", value.lower()):
        if character in TONE_MARKS:
            tone = TONE_MARKS[character]
        elif character == "\u0308":
            if letters and letters[-1] == "u":
                letters[-1] = "v"
        elif "a" <= character <= "z":
            letters.append(character)
    return "".join(letters), tone


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("unihan_zip", type=Path)
    parser.add_argument("pinyin_dictionary", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    frequency: dict[str, int] = {}
    with args.pinyin_dictionary.open(encoding="utf-8") as source:
        for line in source:
            fields = line.rstrip("\n").split("\t")
            if len(fields) >= 3 and len(fields[0]) == 1:
                try:
                    frequency[fields[0]] = max(frequency.get(fields[0], 0), int(fields[2]))
                except ValueError:
                    continue

    readings: dict[str, tuple[str, int]] = {}
    with zipfile.ZipFile(args.unihan_zip).open("Unihan_Readings.txt") as source:
        for raw in source:
            fields = raw.decode("utf-8").rstrip("\n").split("\t")
            if len(fields) != 3 or fields[1] != "kMandarin":
                continue
            character = chr(int(fields[0][2:], 16))
            if len(character) != 1:
                continue
            readings[character] = normalize(fields[2].split(" ")[0])

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="\n") as output:
        output.write("# Unihan 17.0.0 kMandarin; frequency from rime-pinyin-simp\n")
        for character, (syllable, tone) in sorted(readings.items()):
            if syllable:
                output.write(f"{character}\t{syllable}\t{tone}\t{frequency.get(character, 0)}\n")


if __name__ == "__main__":
    main()
