# Game Design Document: Minecraft Cookie Clicker

This document describes the concept and the full gameplay of the Cookie Clicker minigame in Minecraft, from the first click to the endgame. It is the **source of truth for the game's vision**: every agent reads it before designing or building a feature, and feature briefs (`docs/briefs/`) must not contradict it. Written by the user; only the orchestrator edits it, and only on the user's instruction.

> [!note] About the numbers
> All costs, percentages and times in this document are guideline values. They are meant as consistent starting points and must be tuned in playtests. The real values live in `buildings.yml` / `upgrades.yml` (balance-designer, `docs/balance/`).

> [!note] Missing sections
> Sections 4 (gameplay & shop) and later, including 4.3 (milestones), 4.7 (shop menu) and 6.4 (level titles), haven't been added yet. When a feature touches one of them, ask the user instead of guessing.

## 1. Server concept
A Minecraft server where every player builds their own cookie empire. It is an idle clicker that you don't play in a menu but in the world itself. You walk around your own plot, watch your factory grow and see the cookies roll towards you on the conveyor belt.

- **Relaxed:** no PvP, no stress. Competition happens on leaderboards.
- **Short and long sessions:** the factory keeps baking while you're offline.
- **Physically idle:** everything you buy is visible in the world.
- **Own identity:** custom resource pack, custom models, no standard survival.

| Part | Description |
|---|---|
| Hub | Spawn with leaderboards and a portal to your own plot. |
| Own plot | A private region per player where the game takes place. |
| Visitor mode | View the plots of friends or top players (look, don't break). |
| Leaderboards | Total cookies, highest CPS, number of rebirths, highest level. |

## 2. Atmosphere & lore (short)
Warm, cosy and a little absurd: a bakery fairy tale with wooden fences, cookie smell (particles), cheerful music and light humour.

**Premise:** you inherit a run-down little bakery with a single cookie on a pedestal. You expand it into a cookie empire and eventually ascend to the "Heavenly Bakery". Lore comes out in small pieces through hologram texts and the titles that come with your level (see 6.4). There is no mandatory storyline.

## 3. The world: plot & layout

### 3.1 The starter plot
- **Size:** compact, about 11×11, surrounded by wooden fences with a clear entrance. There is about 5 blocks of free space between the factory and the fence.
- **The factory:** an atmospheric little building in which the cookies are baked (virtually).
- **The pedestal:** in front of or inside the factory. A 3D cookie (Item Display) floats/spins on it. Players click (left or right click) to collect cookies by hand.
- **Conveyor belt:** runs from the factory to the pedestal. With the resource pack you see cookies moving along the belt. The higher the CPS, the more cookies and the faster the belt.
- **Holograms:** floating text by the building with real-time statistics (cookies, CPS, cookies per click).
- **Shop panel:** an interactive NPC/Interaction entity next to the factory. Right-click opens the buy menu (see 4.7).

### 3.2 Visual growth
Every building you buy appears physically on the plot and gets nicer at every milestone (see 4.3). To prevent lag there is a visual limit per building type (e.g. at most 10 visible entities), while the number behind it keeps counting as normal.
