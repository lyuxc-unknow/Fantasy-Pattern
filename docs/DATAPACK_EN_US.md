# Datapack Authoring Guide

This guide covers the two Fantasy Technology datapack directories:

- `data/<namespace>/recipe_provider/*.json`: trusted server-side recipes.
- `data/<namespace>/device_access/*.json`: device requirements and their counts.

Both directories are loaded by the server during datapack reloads. Run `/reload`, or leave and re-enter the world, after changing them so the updated rules are synchronized to clients.

`recipe_provider` entries are used through the terminal's built-in Server Recipe Provider and require `trust_server_recipe_parsing=true` in the server config. In `REQUIRE_DEVICES` mode, `device_access` rules apply to both JEI transfers and server-recipe selections. `UNRESTRICTED` skips device checks.

## Datapack Layout

A minimal datapack looks like this:

```text
my_pack/
├── pack.mcmeta
└── data/
    └── example/
        ├── recipe_provider/
        │   └── washing/iron.json
        └── device_access/
            └── washing.json
```

For a world datapack, place the pack at `<world directory>/datapacks/my_pack/`. `src/test/resources` is only used by automated tests; the game does not load it as a datapack.

## recipe_provider

### Recipe IDs

The file path is the recipe ID:

| File path | Recipe ID |
|---|---|
| `data/example/recipe_provider/chemicals.json` | `example:chemicals` |
| `data/example/recipe_provider/washing/iron.json` | `example:washing/iron` |

`category` is not the recipe ID. For example, `chemicals.json` may contain `"category": "example:chemical"`, but its recipe ID is still `example:chemicals`. The `recipes` field in `device_access` must use the complete ID derived from the file path.

### Complete Example

```json
{
  "category": "example:washer",
  "inputs": [
    {
      "id": "minecraft:iron_ingot",
      "amount": 2,
      "tag": "c:ingots/iron",
      "ignore_data": true
    },
    {
      "type": "minecraft:fluid",
      "id": "minecraft:water",
      "amount": 1000
    },
    {
      "type": "mekanism:chemical",
      "id": "mekanism:oxygen",
      "amount": 500
    }
  ],
  "outputs": [
    { "id": "minecraft:gold_ingot", "amount": 1 },
    {
      "type": "minecraft:fluid",
      "id": "minecraft:lava",
      "amount": 250,
      "ignore_data": true
    }
  ],
  "catalysts": ["example:washer"]
}
```

### Top-Level Fields

| Field | Required | Description |
|---|---:|---|
| `category` | No | Recipe category resource ID. Defaults to `fantasy_technology:datapack` and is used by `device_access.categories`. |
| `inputs` | Yes | Input list; at least 1 and at most 81 entries. |
| `outputs` | Yes | Output list; at least 1 and at most 6 entries. Outputs cannot use `tag`. |
| `catalysts` | No | Item IDs used only by the fallback check when no `device_access` rule matches. |
| `neoforge:conditions` | No | Standard NeoForge condition array. If it fails, the entire recipe is skipped. |

Each input or output entry supports:

| Field | Default | Description |
|---|---:|---|
| `type` | `minecraft:item` | `minecraft:item`, `minecraft:fluid`, or `mekanism:chemical` when both Mekanism and Applied Mekanistics are installed. |
| `id` | None | Resource ID for the selected type. Required. |
| `amount` | `1` | Positive integer up to `2147483647`. Fluids and chemicals use their internal units. |
| `tag` | None | Inputs only. The planner may choose any item in this item tag. |
| `ignore_data` | `false` | Ignore extra item or output data, such as tool durability or component differences. |

`catalysts` has no amount field and does not support tags. The entries are accepted device types; when no `device_access` rule matches, their total amount must reach a fixed fallback of four:

```json
"catalysts": ["minecraft:iron_block", "minecraft:gold_block"]
```

The example allows iron and gold blocks to be mixed, as long as their total is four. Use `device_access` to define a different count.

## device_access

### Custom Count for One Recipe

Assume the recipe file is `data/example/recipe_provider/chemicals.json`, so its recipe ID is `example:chemicals`. Add a rule such as:

```json
{
  "recipes": ["example:chemicals"],
  "devices": [
    {
      "count": 1,
      "matches": { "item": "minecraft:iron_block" }
    }
  ]
}
```

This replaces the four-catalyst fallback and requires only one iron block. To declare that the recipe needs no device at all:

```json
{
  "recipes": ["example:chemicals"],
  "devices": []
}
```

### Category Rules

A category can be written as a string:

```json
{
  "categories": ["example:washer"],
  "devices": [
    {
      "count": 1,
      "matches": { "item": "example:washer" }
    }
  ]
}
```

It can also be narrowed to recipes producing selected item outputs:

```json
{
  "categories": [
    {
      "id": "example:washer",
      "items": ["minecraft:gold_ingot"]
    }
  ],
  "devices": [
    {
      "count": 2,
      "matches": { "item": "example:washer" }
    }
  ]
}
```

`items` only matches item outputs. A recipe whose outputs are exclusively fluids or chemicals cannot be narrowed with this field.

### devices and matches

Each `devices` entry is an independent requirement, and every entry must be satisfied:

```json
{
  "devices": [
    {
      "count": 2,
      "matches": [
        { "item": "minecraft:furnace" },
        { "item": "minecraft:blast_furnace" }
      ]
    },
    {
      "count": 1,
      "matches": { "tag": "c:chests" }
    }
  ]
}
```

This requires any combination of two furnaces and blast furnaces, plus at least one device from the `c:chests` tag. `count` must be an integer from 1 through `2147483647`. `matches` uses Minecraft's `Ingredient` format and supports a single item, an item list, or an item tag.

### Match Priority

Rules are selected in this order:

1. Exact recipe ID in `recipes`.
2. A category entry with `items` matching an output item.
3. A category entry containing only the category ID.
4. If nothing matches, the fixed four-device fallback from `recipe_provider.catalysts`.

Once a `device_access` rule matches, its `devices` list completely replaces the fallback; it is not added to `catalysts`.

## Conditional Loading

Both directories support top-level NeoForge conditions. For example, this rule is loaded only when Mekanism is present:

```json
{
  "neoforge:conditions": [
    { "type": "neoforge:mod_loaded", "modid": "mekanism" }
  ],
  "recipes": ["example:chemicals"],
  "devices": [
    {
      "count": 1,
      "matches": { "item": "mekanism:chemical_washer" }
    }
  ]
}
```

When the condition fails, the file is ignored and contributes neither a recipe nor a device rule.

## Reloading and Troubleshooting

1. Make sure the file is under the active pack's `data/<namespace>/...`, not `assets/`, `src/test/resources/`, or an accidental `data/data/...` path.
2. Derive the recipe ID from the file path: `recipe_provider/chemicals.json` is `<namespace>:chemicals`, not `<namespace>:chemical`.
3. `device_access.recipes` must use the complete namespace and path-based ID.
4. `category` is only for category matching; it does not replace the recipe ID in `recipes`.
5. Check the spelling and types of `devices`, `count`, and `matches`. `catalysts` does not support quantities.
6. Run `/reload` and reopen the encoding terminal. The trusted recipe catalogue and device rules are reloaded on the server and synchronized to the client.
7. Check the server log. Invalid recipe files log `Skipping server recipe provider entry ...`; invalid device rules log `Skipping device access rule ...`.

When `device_access_mode` is `UNRESTRICTED`, all device checks are skipped. The rules above apply only in `REQUIRE_DEVICES` mode.
