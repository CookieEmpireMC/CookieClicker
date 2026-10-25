"""Mock-up of the Recipe Book GUI at GUI scale 3 with the recipe icons on their slots and the ingredient glyphs.

Run from the repo root: python design/scripts/lib/render_recipe_book_mock.py [out_png]
Default out: design/scripts/previews/recipe_book_mock.png. No text is drawn (the game draws it): the title sits at
(8, 6), the "Inventory" label at (8, 92), both on the light paper.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

A = Path("resourcepack/assets/cookieclicker/textures")
K = 3  # GUI scale

# slot (raw index) -> texture, the suggested layout: row 1 columns 1..7, a locked sample in row 2
SLOTS = {10: "butter", 11: "brown_sugar", 12: "chocolate_chips", 13: "oatmeal", 14: "hazelnut", 15: "macadamia",
         16: "caramel", 19: "locked", 20: "locked"}


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("design/scripts/previews/recipe_book_mock.png")
    bg = Image.open(A / "font/gui/recipe_book.png").convert("RGBA")
    canvas = bg.resize((bg.width * K, bg.height * K), Image.NEAREST)
    for slot, name in SLOTS.items():
        r, c = divmod(slot, 9)
        icon = Image.open(A / f"item/recipe/{name}.png").convert("RGBA").resize((16 * K, 16 * K), Image.NEAREST)
        canvas.alpha_composite(icon, ((8 + 18 * c) * K, (18 + 18 * r) * K))
    d = ImageDraw.Draw(canvas)
    d.rectangle((8 * K, 6 * K, 8 * K + 60 * K, 6 * K + 8 * K), outline=(64, 64, 64, 255))      # title text box
    d.rectangle((8 * K, 92 * K, 8 * K + 40 * K, 92 * K + 8 * K), outline=(64, 64, 64, 255))    # "Inventory" label box
    strip = Image.open(A / "font/ingredients.png").convert("RGBA")
    sheet = Image.new("RGBA", (canvas.width, canvas.height + 16 * K + 12), (40, 44, 52, 255))
    sheet.paste(canvas, (0, 0))
    sheet.alpha_composite(strip.resize((strip.width * K // 2 * 2, strip.height * K // 2 * 2), Image.NEAREST), (8, canvas.height + 6))
    small = strip.resize((strip.width // 2, strip.height // 2), Image.NEAREST)           # as rendered in chat (8 px)
    sheet.alpha_composite(small.resize((small.width * K, small.height * K), Image.NEAREST), (canvas.width // 2 + 20, canvas.height + 6))
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
