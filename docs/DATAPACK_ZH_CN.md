# 数据包编写指南

本文说明 Fantasy Technology（AE：幻梦编码）的两个数据包目录：

- `data/<namespace>/recipe_provider/*.json`：注册可信服务端配方。
- `data/<namespace>/device_access/*.json`：定义编码或选择配方时需要的设备及数量。

两类文件都由服务端在数据包重载时读取。修改后执行 `/reload`，或重新进入世界使规则同步到客户端。

`recipe_provider` 配方通过终端内置的服务端配方供应器使用，需要服务端配置 `trust_server_recipe_parsing=true`。`device_access` 规则在 `REQUIRE_DEVICES` 模式下同时约束 JEI 转移和服务端配方选择；设置为 `UNRESTRICTED` 时会跳过设备检查。

## 数据包目录

一个最小的数据包结构如下：

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

在存档中使用时，数据包应放在 `<存档目录>/datapacks/my_pack/`。`src/test/resources` 只用于自动化测试，不会被游戏作为数据包读取。

## recipe_provider

### 配方 ID

文件路径就是配方 ID：

| 文件路径 | 配方 ID |
|---|---|
| `data/example/recipe_provider/chemicals.json` | `example:chemicals` |
| `data/example/recipe_provider/washing/iron.json` | `example:washing/iron` |

`category` 不是配方 ID。比如 `chemicals.json` 可以写 `"category": "example:chemical"`，但配方 ID 仍然是 `example:chemicals`。`device_access` 的 `recipes` 必须填写文件路径对应的完整 ID。

### 完整示例

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

### 顶层字段

| 字段 | 必填 | 说明 |
|---|---:|---|
| `category` | 否 | 配方类别资源 ID。默认是 `fantasy_technology:datapack`，用于匹配 `device_access.categories`。 |
| `inputs` | 是 | 输入列表，至少 1 项，最多 81 项。 |
| `outputs` | 是 | 输出列表，至少 1 项，最多 6 项。输出不能使用 `tag`。 |
| `catalysts` | 否 | 仅支持物品 ID 列表，供没有匹配 `device_access` 规则时使用。 |
| `neoforge:conditions` | 否 | NeoForge 标准条件数组；条件不满足时整份配方不会注册。 |

每个输入或输出条目支持：

| 字段 | 默认值 | 说明 |
|---|---:|---|
| `type` | `minecraft:item` | `minecraft:item`、`minecraft:fluid`；安装 Mekanism 与 Applied Mekanistics 后可用 `mekanism:chemical`。 |
| `id` | 无 | 对应类型的资源 ID，必填。 |
| `amount` | `1` | 正整数，最大为 `2147483647`。流体和化学品使用内部单位。 |
| `tag` | 无 | 仅输入允许使用，表示可从该物品 tag 中选择。 |
| `ignore_data` | `false` | 忽略物品或输出的额外数据，例如工具耐久或组件差异。 |

`catalysts` 不带数量字段，也不支持 tag。列表中的物品只是可接受的设备类型；没有 `device_access` 规则时，列表中物品的总数量必须达到固定的 4 个：

```json
"catalysts": ["minecraft:iron_block", "minecraft:gold_block"]
```

上例中铁块和金块可以混合计数，合计 4 个即可。要自定义数量，必须使用 `device_access`。

## device_access

### 按配方 ID 自定义数量

假设配方文件为 `data/example/recipe_provider/chemicals.json`，其配方 ID 是 `example:chemicals`。对应规则可以写成：

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

这会覆盖 `catalysts` 的固定 4 个回退，改为只需要 1 个铁块。若配方完全不需要设备：

```json
{
  "recipes": ["example:chemicals"],
  "devices": []
}
```

### 按类别设置规则

类别可以写成字符串：

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

也可以按输出物品缩小范围：

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

`items` 只匹配物品输出；纯流体或化学品输出不能用它筛选。

### devices 与 matches

每个 `devices` 条目是一个独立要求，所有条目都必须满足：

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

上例要求熔炉和高炉中任意组合共 2 个，并且 `c:chests` 标签中的设备至少 1 个。`count` 必须是 1 到 `2147483647` 的整数；`matches` 使用 Minecraft `Ingredient` 格式，支持单个物品、物品列表和物品 tag。

### 匹配优先级

规则匹配优先级如下：

1. `recipes` 精确匹配配方 ID；
2. `categories` 中带 `items` 的类别和输出物品匹配；
3. `categories` 只写类别 ID 的宽泛匹配；
4. 没有规则时，使用 `recipe_provider.catalysts` 的固定 4 个回退。

匹配到 `device_access` 后，规则中的 `devices` 是完整替换，不会再与 `catalysts` 相加。

## 条件加载

两个目录都支持 NeoForge 顶层条件。例如只在 Mekanism 加载时注册：

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

条件不满足时，文件会被忽略，不会产生设备规则或配方条目。

## 生效与排错

1. 确认文件位于活动数据包的 `data/<namespace>/...` 下，而不是 `assets/`、`src/test/resources/` 或错误的 `data/data/...` 路径。
2. 用文件路径计算配方 ID：`recipe_provider/chemicals.json` 是 `<namespace>:chemicals`，不是 `<namespace>:chemical`。
3. `device_access.recipes` 必须使用完整命名空间和正确的路径 ID。
4. `category` 只用于类别匹配，不能替代 `recipes` 中的配方 ID。
5. 确认 `devices`、`count` 和 `matches` 拼写正确；`catalysts` 不支持数量字段。
6. 执行 `/reload` 后重新打开编码终端。可信服务端配方目录和设备规则都会在服务端重载，并同步给客户端。
7. 检查服务端日志：错误的配方文件会记录 `Skipping server recipe provider entry ...`，错误的设备规则会记录 `Skipping device access rule ...`。

当 `device_access_mode` 为 `UNRESTRICTED` 时，所有设备检查都会被跳过；只有 `REQUIRE_DEVICES` 模式会执行上述规则。
