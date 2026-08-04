# AE: 幻梦编码

**Fantasy Technology** (AE: 幻梦编码) adds a compact, ME-native crafting pipeline to Applied Energistics 2 for NeoForge 1.21.1: a *Blank Fantasy Pattern* (空白幻梦样板) that the *Fantasy Encoding Terminal* (幻梦编码终端) turns into a *Fantasy Recombination Pattern* (幻梦重组样板) storing a processing recipe as data, and a *Fantasy Molecular Reconfiguration Pattern Provider* (幻梦分子重构演算样板供应器) block that executes them instantly — consuming the ingredients from your ME network and injecting the results back, with no intermediate machine in between.

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
- When a crafting CPU executes a pattern, the block consumes the ingredients and inserts the results into the network **instantly** — no progress bar, never busy, so a single block can process as many crafts per tick as the CPU requests.

---

## Requirements

| Dependency | Required | Notes |
|---|---|---|
| Minecraft **1.21.1** | ✅ | |
| NeoForge **21.1.x** | ✅ | |
| [Applied Energistics 2](https://modrinth.com/mod/ae2) | ✅ | `19.2.x` |
| [JEI](https://modrinth.com/mod/jei) | ✅ | Required — recipe transfer into the encoding terminal |
| [Mekanism](https://modrinth.com/mod/mekanism) | 🔶 Optional | Chemical inputs/outputs in patterns |
| [Applied Mekanistics](https://modrinth.com/mod/applied-mekanistics) | 🔶 Optional | Bridges chemicals into AE2 |

Without Mekanism/Applied Mekanistics the mod runs fine; chemical slots simply aren't available.

---

## Usage

1. **Craft a Blank Fantasy Pattern** (`ae2:blank_pattern` + amethyst shard) and place it in the Fantasy Encoding Terminal.
2. Open a recipe in JEI and press the transfer button (the `+`) — inputs and results are filled in, tags and amounts preserved.
3. Optionally press the **double button** next to the results to scale the recipe up.
4. Press **Encode** — the blank fantasy pattern becomes an encoded Fantasy Recombination Pattern.
5. Place encoded patterns in a **Fantasy Molecular Reconfiguration Pattern Provider** block connected to your ME network. Request the pattern's output from any terminal: the block consumes the ingredients and produces the result instantly.

**Tip:** hovering an encoded pattern in your inventory shows its full recipe (ingredients and results) in the tooltip.

---

## Compatibility notes

- **Modern Industrialization & other machine mods**: recipe transfer uses the amounts JEI displays, so multi-count machine recipes (e.g. `2x iron plate`) fill in correctly.
- **AllTheLeaks**: if a mod's `getIngredients()` has side effects that AllTheLeaks locks against, the server falls back to the displayed recipe instead of failing — the first occurrence logs a warning, and that recipe type is remembered for the session.
- **JEI tag-information pages** (`minecraft:tag_recipes/*`) and the **P2P tunnel attunement page** (from the AE2-JEI-Integration addon) are intentionally blocked from transfer — browse-only pages, not recipes.

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
