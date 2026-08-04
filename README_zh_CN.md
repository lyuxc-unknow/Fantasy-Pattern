# AE: 幻梦编码

**AE: 幻梦编码**（Fantasy Technology / 幻梦科技）为 NeoForge 1.21.1 上的 Applied Energistics 2 添加了一套紧凑的 ME 原生合成管线：**空白幻梦样板**（Blank Fantasy Pattern）投入**幻梦编码终端**（Fantasy Encoding Terminal）后变成**幻梦重组样板**（Fantasy Recombination Pattern），把处理配方以数据形式存入单个物品；**幻梦分子重构演算样板供应器**（Fantasy Molecular Reconfiguration Pattern Provider）方块则瞬间执行样板——从你的 ME 网络消耗原料、把产物直接注入网络，全程不需要任何中间机器。

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
- **翻倍按钮**：一次性把全部已编码数量翻倍，带全有或全无的溢出保护（任一条目超过 int 上限则整次点击取消）。物品数量可超过堆叠上限，与 AE2 原版样板终端一致。
- 产物槽是自由幽灵槽；原料槽为预览专用，避免 tag 信息丢失。

### ⚡ 幻梦分子重构演算样板供应器
- 一个 ME 合成设备，存放幻梦样板并注册到网络的合成服务。
- 合成 CPU 执行样板时，方块**瞬间**消耗原料并把产物插入网络——没有进度条、永不忙碌，单方块每 tick 可处理 CPU 请求的任意多次合成。

---

## 依赖要求

| 依赖 | 必需 | 说明 |
|---|---|---|
| Minecraft **1.21.1** | ✅ | |
| NeoForge **21.1.x** | ✅ | |
| [Applied Energistics 2](https://modrinth.com/mod/ae2) | ✅ | `19.2.x` |
| [JEI](https://modrinth.com/mod/jei) | ✅ 必需 | 向编码终端转移配方 |
| [Mekanism](https://modrinth.com/mod/mekanism) | 🔶 可选 | 样板中的化学品输入/输出 |
| [Applied Mekanistics](https://modrinth.com/mod/applied-mekanistics) | 🔶 可选 | 把化学品桥接进 AE2 |

未安装 Mekanism / Applied Mekanistics 时模组正常运行，只是化学品槽不可用。

---

## 使用方法

1. **合成一张空白幻梦样板**（`ae2:blank_pattern` + 紫水晶碎片），放进幻梦编码终端。
2. 在 JEI 中打开配方，按下转移按钮（`+`）——原料与产物自动填入，tag 与数量一并保留。
3. 可选：按下产物旁边的**翻倍按钮**放大配方。
4. 按下**编码**——空白幻梦样板变成已编码的幻梦重组样板。
5. 把已编码样板放进连接 ME 网络的**幻梦分子重构演算样板供应器**方块。从任意终端请求样板的产物：方块消耗原料并瞬间产出。

**提示：** 悬停在物品栏中的已编码样板上，tooltip 会显示完整配方（原料与产物）。

---

## 兼容性说明

- **快速合成规划**（改编自 [OmniSequence-Transfinite](https://github.com/AyaYumi/OmniSequence-Transfinite)，MIT）：当网络上存在**幻梦分子重构演算样板供应器**时，合成计算会启用"配方树聚合"规划器——递归深树（如神秘农业的层层精华合成）去重成图后一次线性遍历；只借用而不消耗工具/容器的配方走特化路径（工具只借 1 份、磨损物回收）。凡是无法证明与原版等价的部分都会交还 AE2：要么单独退化成一棵子树，要么在尚未委派任何工作之前整体放弃聚合。
- **批量发配**：同一供应器支持一次推送多份同配方，合成 CPU 由此把 N 次库存遍历压缩成一次。吞吐量刻意保持不变——每份合成仍占用 CPU 一次操作，协处理器的加速表现与原版 AE2 完全一致。
- 两者均为服务端配置项，位于 `fantasy_technology-server.toml`（`max_fast_mode`、`batch_dispatch_enabled`）；`diagnostics` 可输出规划器聚合/回退的原因。版权声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
- **与 OmniSequence-Transfinite 共存**：若同时安装 OmniSequence，`FantasyTechnologyMixinPlugin` 会跳过本模组的批量发配与样板输入缓存——这两项 OmniSequence 本就提供，而两层抽取包装叠在一起会把同一份配方放大两次。配方树聚合规划则照常工作：本模组以自己的方块为触发条件，OmniSequence 以它们的方块为触发条件，两层包装可以正确嵌套。
- **现代化工业及其他机器模组**：配方转移使用 JEI 显示的数量，多数量机器配方（如 `2x 铁板`）可正确填入。
- **AllTheLeaks**：若某模组的 `getIngredients()` 具有 AllTheLeaks 锁定的副作用，服务端会回退到显示配方而不是失败——首次发生会记录一条警告，并在本次会话内记住该配方类型。
- **JEI tag 信息页**（`minecraft:tag_recipes/*`）与 **P2P 谐调页面**（来自 AE2-JEI-Integration 附属）被有意屏蔽转移——它们只是浏览页，并非配方。

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
