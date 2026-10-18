"""Render review previews of the 3D cookie model into a folder (not part of the pack).

Usage: python design/scripts/lib/render_cookie_previews.py <out_dir>
Writes cookie3d_angles.png (front, 3/4, side, back from below), cookie3d_closeup.png and
cookie3d_gui.png (the model's `gui` display transform, as in the inventory).
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from model_render import render, sheet  # noqa: E402

MODEL = Path("resourcepack/assets/cookieclicker/models/item/cookie.json")


def main() -> int:
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    views = [(0, 0), (35, 20), (90, 0), (160, -25), (215, 10)]
    sheet([render(MODEL, y, p) for y, p in views]).save(out / "cookie3d_angles.png")
    sheet([render(MODEL, 30, 15, px_per_unit=40, size_units=22)]).save(out / "cookie3d_closeup.png")
    disp = json.loads(MODEL.read_text())["display"]
    gui = render(MODEL, 0, 0, display=disp["gui"], px_per_unit=4, size_units=16)
    big = gui.resize((gui.width * 8, gui.height * 8), 0)
    sheet([gui, big], bg=(139, 139, 139, 255)).save(out / "cookie3d_gui.png")
    for name in ("cookie3d_angles.png", "cookie3d_closeup.png", "cookie3d_gui.png"):
        print(out / name)
    return 0


if __name__ == "__main__":
    sys.exit(main())
