"""Check asset files: every JSON/YAML parses, references inside resource packs resolve,
and the plugin's sounds.yml / effects.yml point at things that exist.

Run via `mise run validate-assets`.
"""
import json
import sys
from pathlib import Path

import yaml

ROOTS = ["resourcepack", "itemsadder", "modelengine"]
JSON_SUFFIXES = {".json", ".mcmeta", ".bbmodel"}
YAML_SUFFIXES = {".yml", ".yaml"}
SOUNDS_CONFIG = Path("src/main/resources/sounds.yml")
# Sound sources: Bukkit SoundCategory and Adventure Sound.Source names, lower-case
SOUND_SOURCES = {"master", "music", "record", "records", "weather", "block", "blocks", "hostile",
                 "neutral", "player", "players", "ambient", "voice", "ui"}
EFFECTS_CONFIG = Path("src/main/resources/effects.yml")


def parse(path: Path):
    text = path.read_text(encoding="utf-8")
    return json.loads(text) if path.suffix in JSON_SUFFIXES else yaml.safe_load(text)


def split_id(ref: str, default_ns: str) -> tuple[str, str]:
    ns, _, rest = ref.partition(":") if ":" in ref else (default_ns, "", ref)
    return ns, rest


def find_assets_dirs() -> list[Path]:
    """Every `assets/` folder whose parent has a pack.mcmeta or is an ItemsAdder content resourcepack."""
    dirs = []
    for root in ROOTS:
        base = Path(root)
        if base.is_dir():
            dirs += [p for p in base.rglob("assets") if p.is_dir()]
    return dirs


def check_references(assets: Path) -> list[str]:
    """Resolve item definition → model → texture and sounds.json → .ogg for our own namespaces.

    References into `minecraft:` (or other namespaces not present in this pack) are skipped,
    since they come from the vanilla client or another pack.
    """
    errors = []
    namespaces = {p.name for p in assets.iterdir() if p.is_dir()} - {"minecraft"}

    def exists(ns: str, kind: str, rest: str, suffix: str) -> bool:
        return ns not in namespaces or (assets / ns / kind / f"{rest}{suffix}").is_file()

    def walk_item_model(node, ns: str, origin: Path):
        if isinstance(node, dict):
            if node.get("type", "").removeprefix("minecraft:") == "model" and isinstance(node.get("model"), str):
                m_ns, m_rest = split_id(node["model"], "minecraft")
                if not exists(m_ns, "models", m_rest, ".json"):
                    errors.append(f"{origin}: model '{node['model']}' not found")
            for value in node.values():
                walk_item_model(value, ns, origin)
        elif isinstance(node, list):
            for value in node:
                walk_item_model(value, ns, origin)

    for ns in namespaces:
        for path in (assets / ns / "items").rglob("*.json"):
            try:
                walk_item_model(parse(path), ns, path)
            except (json.JSONDecodeError, OSError):
                pass  # parse errors are reported separately
        for path in (assets / ns / "models").rglob("*.json"):
            try:
                model = parse(path)
            except (json.JSONDecodeError, OSError):
                continue
            if isinstance(parent := model.get("parent"), str) and not parent.startswith("builtin/"):
                p_ns, p_rest = split_id(parent, "minecraft")
                if not exists(p_ns, "models", p_rest, ".json"):
                    errors.append(f"{path}: parent '{parent}' not found")
            for key, tex in (model.get("textures") or {}).items():
                if isinstance(tex, str) and not tex.startswith("#"):
                    t_ns, t_rest = split_id(tex, "minecraft")
                    if not exists(t_ns, "textures", t_rest, ".png"):
                        errors.append(f"{path}: texture '{key}' -> '{tex}' not found")
        sounds_json = assets / ns / "sounds.json"
        if sounds_json.is_file():
            try:
                events = parse(sounds_json)
            except (json.JSONDecodeError, OSError):
                continue
            lang_file = assets / ns / "lang" / "en_us.json"
            try:
                lang = parse(lang_file) if lang_file.is_file() else {}
            except (json.JSONDecodeError, OSError):
                lang = {}
            for event, spec in events.items():
                subtitle = spec.get("subtitle")
                if isinstance(subtitle, str) and subtitle not in lang:
                    errors.append(f"{sounds_json}: '{event}' subtitle '{subtitle}' missing from {lang_file}")
                for sound in spec.get("sounds", []):
                    name = sound.get("name") if isinstance(sound, dict) else sound
                    if isinstance(sound, dict) and sound.get("type") == "event":
                        continue
                    s_ns, s_rest = split_id(name, "minecraft")
                    if not exists(s_ns, "sounds", s_rest, ".ogg"):
                        errors.append(f"{sounds_json}: '{event}' -> '{name}.ogg' not found")
    return errors


def sound_events(assets_dirs: list[Path]) -> set[str]:
    """All `<ns>:<event>` keys registered in our sounds.json files."""
    events = set()
    for assets in assets_dirs:
        for sounds_json in assets.glob("*/sounds.json"):
            try:
                events |= {f"{sounds_json.parent.name}:{e}" for e in parse(sounds_json)}
            except (json.JSONDecodeError, OSError, AttributeError):
                pass
    return events


def check_sounds_config(events: set[str]) -> list[str]:
    """Every event in sounds.yml has a key that exists (non-vanilla keys must be in a sounds.json) and sane values."""
    if not SOUNDS_CONFIG.is_file():
        return []
    errors = []
    try:
        config = parse(SOUNDS_CONFIG) or {}
    except (yaml.YAMLError, OSError):
        return []  # reported by the parse pass
    for name, entry in config.items():
        where = f"{SOUNDS_CONFIG}: '{name}'"
        if not isinstance(entry, dict) or not isinstance(entry.get("key"), str):
            errors.append(f"{where} needs a 'key'")
            continue
        key = entry["key"] if ":" in entry["key"] else f"minecraft:{entry['key']}"
        if not key.startswith("minecraft:") and key not in events:
            errors.append(f"{where} -> '{key}' is not in any sounds.json")
        fallback = entry.get("fallback")
        if not isinstance(fallback, str) or not fallback.startswith("minecraft:"):
            errors.append(f"{where} needs a vanilla 'fallback' (a minecraft:… sound key)")
        if str(entry.get("source", "master")).lower() not in SOUND_SOURCES:
            errors.append(f"{where} has unknown source '{entry.get('source')}'")
        volume = entry.get("volume", 1.0)
        low, high = entry.get("pitch-min", 1.0), entry.get("pitch-max", 1.0)
        if not all(isinstance(v, (int, float)) for v in (volume, low, high)):
            errors.append(f"{where} volume/pitch must be numbers")
        elif not 0 < volume <= 1.0 or not 0.5 <= low <= high <= 2.0:
            errors.append(f"{where} needs 0 < volume <= 1.0 and 0.5 <= pitch-min <= pitch-max <= 2.0")
    return errors


def check_effects_config() -> list[str]:
    """Every preset in effects.yml has a non-empty list of layers, each with a type."""
    if not EFFECTS_CONFIG.is_file():
        return []
    try:
        config = parse(EFFECTS_CONFIG) or {}
    except (yaml.YAMLError, OSError):
        return []  # reported by the parse pass
    errors = []
    for name, preset in config.items():
        if name == "limits":
            continue
        layers = preset.get("layers") if isinstance(preset, dict) else None
        if not isinstance(layers, list) or not layers:
            errors.append(f"{EFFECTS_CONFIG}: '{name}' needs a non-empty 'layers' list")
            continue
        for i, layer in enumerate(layers):
            if not isinstance(layer, dict) or "type" not in layer:
                errors.append(f"{EFFECTS_CONFIG}: '{name}' layer {i} needs a 'type'")
    return errors


def main() -> int:
    checked, errors = 0, []
    files = [p for root in ROOTS if Path(root).is_dir() for p in sorted(Path(root).rglob("*"))]
    files += [p for p in (SOUNDS_CONFIG, EFFECTS_CONFIG) if p.is_file()]
    for path in files:
        if path.is_file() and path.suffix in JSON_SUFFIXES | YAML_SUFFIXES:
            checked += 1
            try:
                parse(path)
            except (OSError, UnicodeDecodeError, json.JSONDecodeError, yaml.YAMLError) as e:
                errors.append(f"{path}: {e}")
    assets_dirs = find_assets_dirs()
    for assets in assets_dirs:
        errors += check_references(assets)
    errors += check_sounds_config(sound_events(assets_dirs))
    errors += check_effects_config()
    for error in errors:
        print(f"INVALID {error}")
    print(f"Checked {checked} file(s), {len(errors)} problem(s).")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
