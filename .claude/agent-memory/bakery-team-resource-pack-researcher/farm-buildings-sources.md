---
name: farm-buildings-sources
description: What worked/failed for build-showcase research (PMC, BuiltByBit, GrabCraft, Wikipedia) 2026-10-25
metadata:
  type: reference
---

- planetminecraft.com, builtbybit.com, createmod.com, minecraft-schematics.com, builditapp.com: WebFetch = 403 always. Only WebSearch snippets (mode extended gives the best captions). Don't retry.
- store.abfielder.com (BuiltByBit seller store) fetches fine: full listing text.
- grabcraft.com listing pages fetch fine: creator, W x H x D, block count, material counts. Search with allowed_domains grabcraft.com lists sizes of many builds.
- Wikipedia fetches fine: Watermill, Windmill, Post_mill, Tower_mill, Windmill_sail, Barn, Granary, Dovecote, Oast_house, Sugar_mill, Orchard, Chicken_coop (thin). 404: Threshing_barn, Chocolate_production.
- minecraftramblings.wordpress.com barn post and minecraftcircle-generator.net windmill guide fetch fine.
- Result note: docs/research/farm-buildings.md (2026-10-25).
- No shell: cannot create the git branch; orchestrator commits notes.
