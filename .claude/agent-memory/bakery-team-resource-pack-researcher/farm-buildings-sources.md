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
- Result notes: docs/research/farm-buildings.md, docs/research/farm-details.md (both 2026-10-25).
- Round 2 (animal pens, mill water) fetched OK: diamondlobby.com chicken coop, buildingguide.app/tutorials/chicken-coop, builds.astroworldmc.com/builds/animal-pen, 4netplayers fence ideas, gamerheadquarters fancy-brick-fence-guide, wealddown.co.uk/buildings/cattle-sheds, motherearthnews overshot wheel; Wikipedia Water_wheel, Mill_race, Hedge, Stile, Chicken_coop, Dairy_farming (Pasture/Gristmill/Watermill: little on races). 403/405: planetminecraft blog, shockbyte, sportskeeda.
- Round 2 finding: no Reddit/builder-blog source on pens or mill races exists in search; searches return SEO spam. Trapdoor-fence "chicken wire" comes from Diamond Lobby/buildingguide.app. Don't repeat the Reddit searches.
- Mill "stone brick thing" = house_mill.py lines 279-291 (sluice tunnel + beam box over race).
- No shell: cannot create the git branch; orchestrator commits notes.
