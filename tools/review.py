"""Generate design/review/index.html: a local, static page to review every asset.

Run via `mise run review`, then open design/review/index.html in a browser (file://).
Shows every texture (pixelated, scaled up), every .ogg with an audio player and its
measurements, and every effect preview, grouped per feature (from the Contract tables in
docs/briefs/*.md). Each item shows its spec notes and a placeholder/final label.
Nothing is uploaded or published; design/review/ is git-ignored.
"""
import html
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from sound_check import check as measure_sound  # noqa: E402

OUT = Path("design/review/index.html")
ASSET_ROOTS = [Path("resourcepack"), Path("itemsadder")]
UNASSIGNED = "Not in any brief"


def rel(path: Path) -> str:
    return os.path.relpath(path, OUT.parent).replace(os.sep, "/")


def feature_ids() -> dict[str, set[str]]:
    """feature -> contract IDs (last path segment, lower-case) from each brief's Contract table."""
    features = {}
    for brief in sorted(Path("docs/briefs").glob("*.md")):
        if brief.name == "TEMPLATE.md":
            continue
        text = brief.read_text(encoding="utf-8")
        section = text.split("## Contract", 1)[1].split("\n## ", 1)[0] if "## Contract" in text else ""
        ids = set()
        for row in section.splitlines():
            cells = [c.strip(" `") for c in row.strip().strip("|").split("|")]
            if len(cells) >= 2 and cells[1] and not set(cells[1]) <= {"-"} and cells[1] != "ID":
                ids.add(re.split(r"[:/.]", cells[1])[-1].lower())
        features[brief.stem] = ids
    return features


def feature_of(name: str, features: dict[str, set[str]]) -> str:
    stem = re.sub(r"\d+$", "", Path(name).stem.lower())
    for feature, ids in features.items():
        if stem in ids or any(i and i in stem for i in ids):
            return feature
    return UNASSIGNED


def spec_notes(spec: Path, needle: str) -> str:
    """The spec's text: the whole file for per-asset specs, or the paragraphs mentioning `needle`."""
    if not spec.is_file():
        return ""
    text = spec.read_text(encoding="utf-8")
    if spec.stem == needle:
        return text
    blocks = [b for b in re.split(r"\n(?=#{1,4} )|\n\n", text) if needle.lower() in b.lower()]
    return "\n\n".join(blocks[:4])


def label(notes: str) -> str:
    return "final" if re.search(r"status:\s*final", notes, re.I) else "placeholder"


def card(title: str, media: str, notes: str, extra: str = "") -> str:
    tag = label(notes)
    body = f"<pre>{html.escape(notes.strip())}</pre>" if notes.strip() else "<p class=muted>No spec notes found.</p>"
    return (f'<article class="card"><header><h3>{html.escape(title)}</h3>'
            f'<span class="tag {tag}">{tag}</span></header>{media}{extra}'
            f"<details><summary>Spec / notes</summary>{body}</details></article>")


def textures(features):
    items = []
    for root in ASSET_ROOTS:
        for png in sorted(root.rglob("textures/**/*.png")) if root.is_dir() else []:
            notes = spec_notes(Path("design") / f"{png.stem}.md", png.stem)
            media = f'<img class="pixel" src="{rel(png)}" alt="{html.escape(png.stem)}">'
            extra = f"<p class=muted>{html.escape(str(png))}</p>"
            items.append((feature_of(png.name, features), card(png.stem, media, notes, extra)))
    return items


def sounds(features):
    items = []
    for root in ASSET_ROOTS:
        for ogg in sorted(root.rglob("*.ogg")) if root.is_dir() else []:
            try:
                line, errors, warnings = measure_sound(ogg)
                stats = line.split("  ", 1)[1].strip()
            except Exception as e:  # unreadable file: still show it
                stats, errors, warnings = "", [f"unreadable: {e}"], []
            preview = next(Path("design/sound-previews").rglob(f"{ogg.stem}.png"), None) \
                if Path("design/sound-previews").is_dir() else None
            media = f'<audio controls preload="none" src="{rel(ogg)}"></audio>'
            if preview:
                media += f'<img class="wave" src="{rel(preview)}" alt="waveform">'
            checks = "".join(f"<li class=fail>{html.escape(m)}</li>" for m in errors)
            checks += "".join(f"<li class=warn>{html.escape(m)}</li>" for m in warnings)
            extra = f"<p class=stats>{html.escape(stats)}</p>" + (f"<ul>{checks}</ul>" if checks else "")
            notes = spec_notes(Path("design/sounds.md"), re.sub(r"\d+$", "", ogg.stem))
            items.append((feature_of(ogg.name, features), card(ogg.stem, media, notes, extra)))
    return items


def effects(features):
    folder = Path("design/vfx-previews")
    items = []
    for png in sorted(folder.glob("*.png")) if folder.is_dir() else []:
        notes = spec_notes(Path("design/effects.md"), png.stem)
        media = f'<img class="wide" src="{rel(png)}" alt="{html.escape(png.stem)}">'
        items.append((feature_of(png.name, features), card(png.stem, media, notes)))
    return items


CSS = """
:root { --bg:#fbf7f1; --fg:#2b2118; --card:#fff; --muted:#7a6a5a; --line:#e6dccf;
        --ph:#b4561b; --final:#2e7d32; --fail:#c62828; --warn:#a66a00; }
@media (prefers-color-scheme: dark) {
  :root { --bg:#1d1813; --fg:#f1e7da; --card:#2a231c; --muted:#b3a290; --line:#3d342a;
          --ph:#f0a060; --final:#81c784; --fail:#ef9a9a; --warn:#ffcc80; } }
* { box-sizing: border-box; }
body { margin:0; padding:24px 16px 48px; background:var(--bg); color:var(--fg);
       font: 15px/1.5 system-ui, sans-serif; }
main { max-width: 1200px; margin: 0 auto; }
h1 { margin: 0 0 4px; } h2 { margin: 32px 0 8px; border-bottom: 1px solid var(--line); padding-bottom: 4px; }
h3 { margin: 0; font-size: 15px; word-break: break-word; }
.muted { color: var(--muted); font-size: 12px; word-break: break-all; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
.card { background: var(--card); border: 1px solid var(--line); border-radius: 10px; padding: 12px; min-width: 0; }
.card header { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 8px; }
.tag { font-size: 11px; text-transform: uppercase; letter-spacing: .05em; border-radius: 99px;
       padding: 2px 8px; border: 1px solid currentColor; }
.tag.placeholder { color: var(--ph); } .tag.final { color: var(--final); }
img.pixel { image-rendering: pixelated; width: 256px; max-width: 100%; height: auto; display: block;
            background: repeating-conic-gradient(#0001 0 25%, transparent 0 50%) 0 0 / 16px 16px; }
img.wave, img.wide { width: 100%; height: auto; display: block; margin-top: 8px; }
audio { width: 100%; }
.stats { font-family: ui-monospace, monospace; font-size: 12px; }
li.fail { color: var(--fail); } li.warn { color: var(--warn); }
pre { white-space: pre-wrap; font-size: 12px; max-height: 320px; overflow: auto; }
"""


def main() -> int:
    features = feature_ids()
    sections = {"Textures": textures(features), "Sounds": sounds(features), "Effects": effects(features)}
    groups = sorted({f for items in sections.values() for f, _ in items}, key=lambda f: (f == UNASSIGNED, f))
    total = sum(len(v) for v in sections.values())
    parts = [f"<!doctype html><html lang=en><head><meta charset=utf-8>"
             f"<meta name=viewport content='width=device-width,initial-scale=1'>"
             f"<title>Asset Review</title><style>{CSS}</style></head><body><main>",
             "<h1>CookieClicker asset review</h1>",
             f"<p class=muted>{total} item(s). Generated by <code>mise run review</code>; local only.</p>"]
    if not groups:
        parts.append("<p>No assets yet.</p>")
    for feature in groups:
        parts.append(f"<h2>{html.escape(feature)}</h2>")
        for kind, items in sections.items():
            cards = [c for f, c in items if f == feature]
            if cards:
                parts.append(f"<h3 class=muted>{kind}</h3><div class=grid>{''.join(cards)}</div>")
    parts.append("</main></body></html>")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(parts), encoding="utf-8")
    print(f"Wrote {OUT.resolve()} ({total} item(s)). Open it in your browser.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
