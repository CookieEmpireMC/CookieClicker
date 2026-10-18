"""Zip resourcepack/ into target/cookieclicker-pack.zip and print its SHA-1.

Run via `mise run pack`. Entries are sorted and timestamped identically, so the same
pack contents always give the same SHA-1.
"""
import hashlib
import sys
import zipfile
from pathlib import Path

SOURCE = Path("resourcepack")
OUTPUT = Path("target/cookieclicker-pack.zip")
FIXED_TIME = (1980, 1, 1, 0, 0, 0)


def main() -> int:
    if not (SOURCE / "pack.mcmeta").is_file():
        print(f"{SOURCE}/pack.mcmeta not found; nothing to pack.")
        return 1
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    files = sorted(p for p in SOURCE.rglob("*") if p.is_file())
    with zipfile.ZipFile(OUTPUT, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for path in files:
            entry = zipfile.ZipInfo(path.relative_to(SOURCE).as_posix(), FIXED_TIME)
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            zf.writestr(entry, path.read_bytes())
    sha1 = hashlib.sha1(OUTPUT.read_bytes()).hexdigest()
    print(f"{OUTPUT}  {len(files)} file(s)  {OUTPUT.stat().st_size} bytes")
    print(f"SHA-1 {sha1}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
