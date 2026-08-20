# AE: 幻梦编码

**AE: 幻梦编码**（Fantasy Technology / 幻梦科技）为 NeoForge 1.21.1 上的 Applied Energistics 2 添加了一套紧凑的 ME 原生合成管线：**空白幻梦样板**（Blank Fantasy Pattern）投入**幻梦编码终端**（Fantasy Encoding Terminal）后变成**幻梦重组样板**（Fantasy Recombination Pattern），把处理配方以数据形式存入单个物品；**幻梦分子重构演算样板供应器**（Fantasy Molecular Reconfiguration Pattern Provider）方块则无需中间机器即可瞬间执行这些配方。

---

## 特性

### 🧩 幻梦重组样板
- 空白幻梦样板（`ae2:blank_pattern` + 紫水晶碎片合成）被编码终端消耗后，变成携带配方的幻梦重组样板：最多 **81 个原料条目**、**6 个产物**，物品、流体、（装有 Mekanism 时）化学品皆可。
- 原料槽支持 tag：书架样板会要求任意木板（`#minecraft:planks`）而非某一种特定木头，合成规划器会选用网络中实际存在的物品。
- 同类条目在编码时自动合并——六块木板加三本书会编码成一条 `6x 木板` 加一条 `3x 书`。
- 样板完全持久化：合成任务跨世界重载依然有效。
- 幻梦样板（空白或已编码）无法放入 AE2 自身的样板供应器——它们只能在幻梦分子重构演算样板供应器中执行。

### 🖥️ 幻梦编码终端
- 一个 ME 终端部件（像其他终端一样接在你的网络上），侧边带有完整网络物品列表。
- **一键转移任意 JEI 配方**到编码网格——熔炼、切石、以及模组机器配方类别都能用，不止原版合成。数量始终以 JEI 显示为准（因此现代化工业等机器模组的多数量配方也能正确转移），tag 会从服务端配方还原。
- 服务端可启用 `trust_server_recipe_parsing`（默认 `false`）。启用后 JEI 转移被禁用，终端会显示内置的**服务端配方供应器**按钮；客户端只提交供应器 ID 与配方 ID，原料、产物、tag 和数量全部由服务端重新解析。编码后的可信样板会记录不暴露配方内容的配方指纹，服务端在请求原料和真正执行合成前再次查找并校验对应配方。可信样板与 JEI/客户端鉴权样板互相禁用。内置供应器默认只公开固定产物的工作台配方。
- **翻倍按钮**：一次性把全部已编码数量翻倍，带全有或全无的溢出保护（任一条目超过 int 上限则整次点击取消）。物品数量可超过堆叠上限，与 AE2 原版样板终端一致。
- 产物槽是自由幽灵槽；原料槽为预览专用，避免 tag 信息丢失。

### ⚡ 幻梦分子重构演算样板供应器
- 一个 ME 合成设备，存放幻梦样板并注册到网络的合成服务。
- 恢复为**即时合成**：不再经过处理计时，也不再需要加速卡。
- 每接受一次合成消耗 1 次燃料额度；燃料及其单个物品提供的次数统一按 `物品ID:次数` 配置（例如 `ae2:matter_ball:100000`）。默认每个 AE2 物质球可支持 **100,000 次合成**，批量合成 N 次就消耗 N 次额度。
- 物质球燃料额度与短暂的待输出队列都会随存档和区块卸载持久化。

---

## 依赖要求

| 依赖 | 必需 | 说明 |
|---|---|---|
| Minecraft **1.21.1** | ✅ | |
| NeoForge **21.1.x** | ✅ | |
| [Applied Energistics 2](https://modrinth.com/mod/ae2) | ✅ | `19.2.x` |
| [JEI](https://modrinth.com/mod/jei) | ✅ 必需 | 向编码终端转移配方 |
| [OmniSequence-Transfinite](https://github.com/AyaYumi/OmniSequence-Transfinite) | 🔶 可选 | `1.3.9+`；启用快速规划与批量发配 |
| [AE2-VM modified](https://github.com/lyuxc-unknow/AE2-VM-Fantasy-Pattern-Fork) | ✅ | 内嵌；负责耐久输入规划 |
| [Mekanism](https://modrinth.com/mod/mekanism) | 🔶 可选 | 样板中的化学品输入/输出 |
| [Applied Mekanistics](https://modrinth.com/mod/applied-mekanistics) | 🔶 可选 | 把化学品桥接进 AE2 |

未安装 Mekanism / Applied Mekanistics 时模组正常运行，只是化学品槽不可用。

---

## 使用方法

1. **合成一张空白幻梦样板**（`ae2:blank_pattern` + 紫水晶碎片），放进幻梦编码终端。
2. 默认模式下在 JEI 中按转移按钮（`+`）；启用服务端可信解析时，改用终端编码区右侧的工作台图标打开内置配方供应器。
3. 可选：按下产物旁边的**翻倍按钮**放大配方。
4. 按下**编码**——空白幻梦样板变成已编码的幻梦重组样板。
5. 把已编码样板与 AE2 物质球放进连接 ME 网络的**幻梦分子重构演算样板供应器**方块。从任意终端请求产物：默认每个物质球可支持 100,000 次即时合成。

**提示：** 悬停在物品栏中的已编码样板上，tooltip 会显示完整配方（原料与产物）。

### 服务端配方供应器 DSL

数据包可在 `data/<命名空间>/recipe_provider/*.json` 中补充服务端可信配方。文件路径本身就是配方 ID；下例会注册 `example:washing/iron`：

```json
{
  "category": "example:washer",
  "inputs": [
    { "id": "minecraft:iron_ingot", "amount": 2, "tag": "c:ingots/iron" },
    { "type": "minecraft:fluid", "id": "minecraft:water", "amount": 1000 }
  ],
  "outputs": [
    { "id": "minecraft:gold_ingot", "amount": 1 }
  ],
  "catalysts": ["example:washer"]
}
```

- `type` 默认为 `minecraft:item`，内置支持 `minecraft:fluid`；同时安装 Mekanism 与 Applied Mekanistics 时支持 `mekanism:chemical`。例如 `{ "type": "mekanism:chemical", "id": "mekanism:oxygen", "amount": 1000 }`。
- 输入可带 `tag` 与 `ignore_data`；输出可带 `ignore_data`，但不能使用 tag。数量必须为正数，并继续受单张样板 81 个输入、6 个输出的上限约束。
- `category` 用于既有的 `device_access` 规则；没有匹配规则时，`catalysts` 是四设备回退检查所使用的物品列表。也可在 `device_access` 中按该文件的配方 ID 单独定义设备要求。
- 文件支持标准顶层 `neoforge:conditions`。其他 AE Key 桥接模组可通过 `RecipeStackDefinition.registerResolver` 注册新的 `type`，无需修改 DSL 或网络协议。

---

## 兼容性说明

- **快速合成规划**在安装 OmniSequence 并启用万物演算核心时由 Omni 提供。幻梦样板会进入其配方树聚合规划器；规划模式、节点与时间预算、诊断、回退和 CPU 记账均由 Omni 负责。未安装 OmniSequence 时，AE2 使用自身的普通计算与发配行为，耐久输入兼容仍由内嵌 `ae2vm-modified` 提供。
- **批量执行**在 Omni 可用时使用其原生批量发配。耐久与可复用输入直接采用 Omni 与内嵌 `ae2vm-modified` 生成的精确计划，包括工具池中每把工具的磨损。批量合成 N 次消耗 N 次燃料额度。当 `consume_fuel=false` 时，无限任务会拆分为有限批次，避免深层递归配方在兼容路径中发生整数溢出；`batch_dispatch_enabled` 仍可关闭批量发配，修改后需要重新进入存档或重启游戏。
- **现代化工业及其他机器模组**：配方转移使用 JEI 显示的数量，多数量机器配方（如 `2x 铁板`）可正确填入。
- **AllTheLeaks**：若某模组的 `getIngredients()` 具有 AllTheLeaks 锁定的副作用，服务端会回退到显示配方而不是失败——首次发生会记录一条警告，并在本次会话内记住该配方类型。
- **JEI tag 信息页**（`minecraft:tag_recipes/*`）与 **P2P 谐调页面**（来自 AE2-JEI-Integration 附属）默认禁止转移，因为它们只是浏览页，并非配方。可在服务端配置中通过 `blocked_jei_category_ids` 配置 category ID 列表；修改后需要退出并重新进入存档或重启游戏。
- **设备接入数据包默认规则**覆盖原版工作站、AE2 处理类别，以及有代表性的现代化工业和 Mekanism 机器；可选模组规则使用标准 `neoforge:conditions`，未列出的类别继续使用四个 catalyst 的回退规则。数据包覆盖文件位于 `data/<namespace>/device_access/*.json`。

---

## 从源码构建

```bash
./gradlew build
```

需要 JDK 21。模组使用官方 Mojang 映射（见 [NeoForm 许可](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md)）。

---

## 许可证

本项目采用 **MIT 许可证** —— 详见 [LICENSE](LICENSE)。

如有问题或建议，请在项目的 issue 追踪器中反馈。
