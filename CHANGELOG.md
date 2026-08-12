# Changelog

## 1.0.13 - 2026-08-12

This release moves fast planning and batch execution onto OmniSequence's native compatibility interfaces and fixes deep recursive crafts when fuel consumption is disabled.

### Changed

- Replaced the copied OmniSequence planner and crafting-CPU implementations with narrowly scoped compatibility Mixins. When OmniSequence-Transfinite 1.3.9 or newer is installed and its Transfinite Computation Core is enabled, it owns fast planning, batch extraction, dispatch and CPU accounting for fantasy patterns.
- OmniSequence-Transfinite remains optional. Without it, crafting calculations and dispatch fall back to AE2's normal behavior; durable and reusable input compatibility remains provided by the bundled `ae2vm-modified` component.
- Omni batches now use Omni's reusable-input plan directly, including the exact returned remainder and per-tool wear for durable tool pools.
- Removed the local `max_fast_mode`, planner node/time budget and planner diagnostics settings because those concerns are now owned by OmniSequence. `batch_dispatch_enabled` remains available for disabling the optional batch path.

### Fixed

- Fixed deep recursive crafts, such as sixteen-times compressed cobblestone, blocking the server thread when `consume_fuel=false`. Batch limits now retain long-sized aggregation while applying overflow-safe bounds instead of expanding the request into thousands of small synchronous batches.
- Fixed those same large batches remaining stuck without dispatch after the thread-blocking issue was removed. Intermediate outputs are no longer rejected against physical ME storage before AE2 has registered the crafting CPU as their destination; delivery remains deferred until the CPU is ready, and any unaccepted remainder stays queued without item loss.

## 1.0.12 - 2026-08-11

This release expands Fantasy Pattern from a universal recipe encoder into a configurable, ME-native instant-crafting pipeline with device ownership checks and safer server-side transfer handling.

### Added

- Added the Fantasy Device Access Block. Items placed in connected access blocks represent machines owned by that ME network.
- Added device requirements for recipe encoding. Built-in datapack rules cover vanilla stations, AE2 processing, Modern Industrialization and Mekanism; custom rules can be supplied through `data/<namespace>/device_access/*.json`.
- Added a four-category-catalyst fallback for recipe categories without a datapack rule.
- Added server configuration for device checks, blocked JEI categories, fuel consumption, batch dispatch, planner limits and diagnostics.
- Added an in-game configuration screen for local worlds. Remote-server settings remain read-only.

### Changed

- Each accepted craft now consumes one fuel charge when fuel consumption is enabled.
- Fuel values are configured per item in `annihilation_fuel_items` using `item_id:crafts`, for example `ae2:matter_ball:100000`.
- Pending crafted outputs and remaining prepaid fuel charges now persist across saves and chunk reloads.
- Reworked the Fantasy Molecular Reconfiguration Pattern Provider and Fantasy Device Access Block models and textures to match AE2's visual language.

### Fixed

- Fixed pending output changes not being marked for saving after delivery.
- Fixed an empty device-catalyst snapshot not being sent when a terminal was first opened.
- Fixed the Fantasy Device Access Block not exposing its inventory through NeoForge's ItemHandler capability.
- Hardened recipe transfer on the server by resolving submitted recipe ids, validating recipe categories and outputs, and enforcing blocked JEI categories server-side.

### Configuration Migration

Replace the old pair:

```toml
annihilation_fuel_items = ["ae2:matter_ball:100000"]
```

Additional fuels can use different values in the same list, such as `minecraft:diamond:500000`.
