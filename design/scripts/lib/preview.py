"""Preview helpers: nearest-neighbour upscales and size strips for reviewing pixel art.

Usage: python design/scripts/lib/preview.py <png> <out_dir>
Writes <name>_x8.png (on a checkerboard) and <name>_sizes.png (1x and x4 side by side,
on a light and a dark background).
"""
import sys
from pathlib import Path

from PIL import Image


def checker(size: tuple[int, int], cell: int = 8, a=(200, 200, 200), b=(160, 160, 160)) -> Image.Image:
    img = Image.new("RGBA", size)
    px = img.load()
    for y in range(size[1]):
        for x in range(size[0]):
            px[x, y] = (*(a if ((x // cell) + (y // cell)) % 2 == 0 else b), 255)
    return img


def upscale(img: Image.Image, factor: int, cell: int | None = None) -> Image.Image:
    big = img.resize((img.width * factor, img.height * factor), Image.NEAREST)
    bg = checker(big.size, cell or factor * 4)
    bg.alpha_composite(big)
    return bg


def size_strip(img: Image.Image, scales=(1, 4)) -> Image.Image:
    """Each scale on a light and a dark background, left to right."""
    pad = 8
    tiles = []
    for bgc in ((235, 235, 235, 255), (40, 44, 52, 255)):
        for s in scales:
            t = img.resize((img.width * s, img.height * s), Image.NEAREST)
            tile = Image.new("RGBA", (t.width + 2 * pad, t.height + 2 * pad), bgc)
            tile.alpha_composite(t, (pad, pad))
            tiles.append(tile)
    w = sum(t.width for t in tiles)
    h = max(t.height for t in tiles)
    out = Image.new("RGBA", (w, h), (128, 128, 128, 255))
    x = 0
    for t in tiles:
        out.alpha_composite(t, (x, (h - t.height) // 2))
        x += t.width
    return out


def main() -> int:
    src, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    out_dir.mkdir(parents=True, exist_ok=True)
    img = Image.open(src).convert("RGBA")
    upscale(img, 8).save(out_dir / f"{src.stem}_x8.png")
    size_strip(img).save(out_dir / f"{src.stem}_sizes.png")
    print(out_dir / f"{src.stem}_x8.png")
    print(out_dir / f"{src.stem}_sizes.png")
    return 0


if __name__ == "__main__":
    sys.exit(main())
