# AE: 幻梦编码

**Fantasy Technology** (AE: 幻梦编码) adds a compact, ME-native crafting pipeline to Applied Energistics 2 for NeoForge 1.21.1: a *Blank Fantasy Pattern* (空白幻梦样板) that the *Fantasy Encoding Terminal* (幻梦编码终端) turns into a *Fantasy Recombination Pattern* (幻梦重组样板) storing a processing recipe as data, and a *Fantasy Molecular Reconfiguration Pattern Provider* (幻梦分子重构演算样板供应器) block that executes them instantly with no intermediate machine.

---

## Features

### 🧩 Fantasy Recombination Pattern
- Blank fantasy patterns (`ae2:blank_pattern` + amethyst shard) are consumed by the encoding terminal; the encoded recombination pattern carries the recipe as data: up to **81 ingredient entries** and **6 results**, items, fluids and (with Mekanism) chemicals alike.
- Ingredient slots can be tag-based: a bookshelf pattern asks for any plank (`#minecraft:planks`) instead of one specific wood, so the crafting planner picks whatever your network actually has.
- Same-kind entries merge on encoding — six planks + three books encode as one `6x planks` + one `3x book` entry.
- Patterns are fully persistent: crafting jobs survive world reloads.
- Fantasy patterns (blank or encoded) cannot be placed into AE2's own pattern providers — they only run in the Fantasy Molecular Reconfiguration Pattern Provider.

### 🖥️ Fantasy Encoding Terminal
- An ME terminal part (attach it to your network like any terminal) with the full network item list on the side.
- **Transfer any JEI recipe** into the encoding grid with one click — smelting, stonecutting, and modded machine categories work, not just vanilla crafting. Amounts always come from what JEI shows (so machine mods such as Modern Industrialization transfer correct counts), and tags are restored from the server-side recipe.
- Result slots are free ghost slots; ingredients are preview-only so tags are never lost.

### ⚡ Fantasy Molecular Reconfiguration Pattern Provider
- An ME crafting device that holds Fantasy Patterns and registers them with the network's crafting service.
- Crafting is **instant** again: there is no processing timer or Acceleration Card requirement.
- Every accepted craft consumes one fuel charge. Fuel and its per-item craft count are configured together as `item_id:crafts` (for example, `ae2:matter_ball:100000`); by default, one AE2 Matter Ball supplies **100,000 crafts**, and a batch of N repetitions consumes N charges.
- Matter-ball fuel and the short output-delivery queue survive world or chunk reloads.

---

## Requirements

| Dependency | Required | Notes |
|---|---|---|
| Minecraft **1.21.1** | ✅ | |
| NeoForge **21.1.x** | ✅ | |
| [Applied Energistics 2](https://modrinth.com/mod/ae2) | ✅ | `19.2.x` |
| [JEI](https://modrinth.com/mod/jei) | ✅ | Required — recipe transfer into the encoding terminal |
| [OmniSequence-Transfinite](https://github.com/AyaYumi/OmniSequence-Transfinite) | 🔶 Optional | `1.3.9+`; enables fast planning and batch dispatch |
| [AE2-VM modified](https://github.com/lyuxc-unknow/AE2-VM-Fantasy-Pattern-Fork) | ✅ | Bundled; owns durable-input planning |
| [Mekanism](https://modrinth.com/mod/mekanism) | 🔶 Optional | Chemical inputs/outputs in patterns |
| [Applied Mekanistics](https://modrinth.com/mod/applied-mekanistics) | 🔶 Optional | Bridges chemicals into AE2 |

Without Mekanism/Applied Mekanistics the mod runs fine; chemical slots simply aren't available.

---

## Usage

1. **Craft a Blank Fantasy Pattern** (`ae2:blank_pattern` + amethyst shard) and place it in the Fantasy Encoding Terminal.
2. Open a recipe in JEI and press the transfer button (the `+`) — inputs and results are filled in, tags and amounts preserved.
3. Optionally press the **double button** next to the results to scale the recipe up.
4. Press **Encode** — the blank fantasy pattern becomes an encoded Fantasy Recombination Pattern.
5. Place encoded patterns and AE2 Matter Balls in a **Fantasy Molecular Reconfiguration Pattern Provider** connected to your ME network. Request the pattern's output from any terminal; by default, each Matter Ball powers 100,000 instant crafts.

**Tip:** hovering an encoded pattern in your inventory shows its full recipe (ingredients and results) in the tooltip.

---

## Compatibility notes

- **Fast crafting plans** are provided by OmniSequence-Transfinite when installed and its Transfinite Computation Core is enabled. Fantasy patterns are admitted to its aggregated planner; planner modes, limits, diagnostics, fallback, and CPU accounting remain owned by Omni. Without OmniSequence, AE2 uses its normal calculation and dispatch behavior, with durable-input compatibility still supplied by the bundled `ae2vm-modified`.
- **Batch execution** uses Omni's native batch dispatch when available. Durable and reusable inputs use the exact plan produced by Omni together with the bundled `ae2vm-modified`, including per-tool wear for tool pools. A batch of N repetitions consumes N fuel charges. When `consume_fuel=false`, the unbounded task is split into finite batches so deep recursive recipes cannot overflow compatibility paths. `batch_dispatch_enabled` can disable batching and takes effect after re-entering the world or restarting the game.
- **Modern Industrialization & other machine mods**: recipe transfer uses the amounts JEI displays, so multi-count machine recipes (e.g. `2x iron plate`) fill in correctly.
- **AllTheLeaks**: if a mod's `getIngredients()` has side effects that AllTheLeaks locks against, the server falls back to the displayed recipe instead of failing — the first occurrence logs a warning, and that recipe type is remembered for the session.
- **JEI tag-information pages** (`minecraft:tag_recipes/*`) and the **P2P tunnel attunement page** (from the AE2-JEI-Integration addon) are blocked by default because they are browse-only pages, not recipes. The category-id list is configurable through `blocked_jei_category_ids` in the server config; changes require leaving and re-entering the world or restarting the game.
- **Device-access datapack defaults** cover vanilla crafting stations, AE2 processing, and representative Modern Industrialization and Mekanism machines. Optional-mod rules use standard `neoforge:conditions`; unlisted categories retain the four-catalyst fallback. Pack overrides live in `data/<namespace>/device_access/*.json`.

---

## Building from source

```bash
./gradlew build
```

Requires JDK 21. The mod uses official Mojang mappings (see [NeoForm licensing](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md)).

---

## License

This project is licensed under the **MIT License** — see [LICENSE](LICENSE) for details.

Report issues and join the discussion on the project's issue tracker.
