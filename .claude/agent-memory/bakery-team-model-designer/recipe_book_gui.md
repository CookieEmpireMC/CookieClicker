---
name: recipe-book-gui
description: Recipe Book GUI/ingredient/recipe icon generators, codepoints, chicken_farm model (2026-10-25)
metadata:
  type: project
---

- Scripts (2026-10-25): `design/scripts/recipe_book_gui.py` (font gui.json + 176x186 bg, spaces U+E100..E111, bg U+E200), `ingredient_glyphs.py` (font ingredient, U+E000..E004, atlas 80x16), `recipe_icons.py` (upgrade/recipe_book + recipe/<id> + recipe/locked, 32x32), chicken_farm in `building_icons.py` and `building_world.py` (added to IDS; the Beehive never had a world model). Spec `design/recipe-book-gui.md`. Mock-up `design/scripts/lib/render_recipe_book_mock.py`.
- Run single scripts with `mise exec -- python <script>` after `mise run assets-deps` (the worktree venv starts empty). `mise run assets` regenerates portal_bakery_3.json and portal_bakery.png differently each time: `git checkout --` them.
- Sandbox: write scratch scripts with Write; heredocs combined with shell paths or git were refused.
