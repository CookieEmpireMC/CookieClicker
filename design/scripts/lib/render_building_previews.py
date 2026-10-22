"""Preview sheets of the building world models: one PNG per building, 3 stages x 2 views (front 3/4 and back 3/4).

Run from the repo root: python design/scripts/lib/render_building_previews.py [out_dir]
Default out_dir: design/scripts/previews. The models are shifted down 8 units (their floor sits at model y 8).
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from PIL import Image  # noqa: E402

import model_render  # noqa: E402

IDS = ["villager_baker", "industrial_oven", "extra_belt", "shipping_centre", "alchemy_lab", "portal_bakery"]


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("design/scripts/previews")
    out.mkdir(parents=True, exist_ok=True)
    disp = {"translation": [0, -8, 0]}
    for bid in IDS:
        imgs = []
        for yaw in (150, 330):
            for s in (1, 2, 3):
                p = Path(f"resourcepack/assets/cookieclicker/models/item/building_world/{bid}_{s}.json")
                im = model_render.render(p, yaw, 25, display=disp, px_per_unit=14, size_units=30, background=(58, 64, 76, 255))
                imgs.append(im)
        w, h = imgs[0].size
        sheet = Image.new("RGBA", (w * 3, h * 2))
        for i, im in enumerate(imgs):
            sheet.paste(im, ((i % 3) * w, (i // 3) * h))
        sheet.save(out / f"building_{bid}.png")
        print(out / f"building_{bid}.png")


if __name__ == "__main__":
    main()
