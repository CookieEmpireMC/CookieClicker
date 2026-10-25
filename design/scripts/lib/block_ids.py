"""Block id validator against the Paper 26.3 sources jar (org/bukkit/Material.java), the project's source of truth for ids.

    ids() -> set of block/item ids (lower case) from the sources jar ($BAKERY_SOURCES_JAR, default the ~/.m2 paper-api
    26.3.build.205-stable sources jar); None when the jar is absent (callers then fall back to the client jar's blockstates).
    missing(block_ids) -> sorted list of ids that do not exist.
"""
from __future__ import annotations

import os
import re
import zipfile
from functools import lru_cache
from pathlib import Path

DEFAULT = Path.home() / ".m2/repository/io/papermc/paper/paper-api/26.3.build.205-stable/paper-api-26.3.build.205-stable-sources.jar"
ENTRY = re.compile(r"^    ([A-Z][A-Z0-9_]*)\(", re.M)


@lru_cache(None)
def ids() -> frozenset | None:
    jar = Path(os.environ.get("BAKERY_SOURCES_JAR", DEFAULT))
    if not jar.exists():
        return None
    with zipfile.ZipFile(jar) as z:
        text = z.read("org/bukkit/Material.java").decode("utf-8")
    return frozenset(m.group(1).lower() for m in ENTRY.finditer(text))


def missing(block_ids) -> list:
    known = ids()
    if known is None:
        try:
            import block_models as bm
            return sorted(b for b in set(block_ids) if b not in ("air",) and bm.blockstate(b) is None)
        except ImportError:
            return []
    return sorted(b for b in set(block_ids) if b not in known)
