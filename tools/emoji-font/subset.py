"""
Builds wasm-app/resources/emoji.ttf: Noto Color Emoji reduced to the symbols used in the UI code.

Compose for web draws text with its own bundled fonts and cannot use the system emoji font, so the
web app loads this file as a fallback (see wasm-app/src/main.kt). Re-run after adding new emoji:

    python3 tools/emoji-font/subset.py [path/to/NotoColorEmoji.ttf]

Needs fonttools (pip install fonttools). Noto Color Emoji is licensed under the SIL Open Font License 1.1.
"""
import pathlib
import sys

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCES = [ROOT / "shared" / "src", ROOT / "wasm-app" / "src"]
OUTPUT = ROOT / "wasm-app" / "resources" / "emoji.ttf"
DEFAULT_FONT = "/usr/share/fonts/noto/NotoColorEmoji.ttf"


def main():
    font_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_FONT
    cmap = TTFont(font_path)["cmap"].getBestCmap()

    used = set()
    for source in SOURCES:
        for file in source.rglob("*.kt"):
            used.update(ord(c) for c in file.read_text(encoding="utf-8") if ord(c) > 0x2000)
    # Keep the emoji presentation selector and joiner so sequences like "🛍️" still resolve
    codepoints = sorted(c for c in used if c in cmap) + [0xFE0F, 0x200D]

    options = subset.Options()
    options.layout_features = ["*"]
    subsetter = subset.Subsetter(options)
    subsetter.populate(unicodes=codepoints)
    font = TTFont(font_path)
    subsetter.subset(font)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    font.save(OUTPUT)
    print(f"{len(codepoints)} symbols -> {OUTPUT.relative_to(ROOT)} ({OUTPUT.stat().st_size // 1024} KiB)")


if __name__ == "__main__":
    main()
