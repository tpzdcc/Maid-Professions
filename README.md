# 女仆职业（Maid Professions）

> 车万女仆（Touhou Little Maid）的附属模组：给女仆分配**职业**，每个职业限定一组可从事的工作，好感度升级时自动获得属性与药水加成。

> English summary: An addon for Touhou Little Maid (TLM). Assign each maid a **profession** that limits which tasks she may perform. As her favorability level rises, she automatically gains attribute modifiers and potion effects. New maids are idle-only until you give them a Job Book.

---

## 功能一览

- **13 本转职书**：1 本空白转职书 + 12 本职业转职书（含创造模式限定的全能手册）。手持转职书右键女仆即可转职（只有女仆的主人可以）。
- **任务白名单**：每个职业在 `jobs.json` 里定义允许的工作。未转职的新女仆默认只能「空闲」（可用配置项关掉）。女仆界面里不允许的任务会置灰，点选会被拒绝并提示原因。
- **好感度加成**：绑定 TLM 好感度系统（0–3 级）。转职 / 好感度升级时自动重算：属性修正（固定 UUID，安全清除）+ 永久药水效果（精确记录、按清单清除，不误伤其他模组）。
- **管理员指令 `/maidjob`**：权限等级 2（op 级），列表 / 分配 / 查询 / 详情四组子命令。
- **数据驱动职业**：职业定义全部在 `config/maidtaskfilter/jobs.json`，改完重启即生效，无需重新编译。

## 依赖

| 项 | 版本 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47+ |
| **Touhou Little Maid（车万女仆）** | 1.5.3（`mods.toml` 声明 `[1.0,)`，实际按 1.5.3 开发） |

本模组 **只硬依赖 TLM**。联动职业（女仆餐厅 / 女仆烘焙坊 / 森罗物语：兼容 / 手摇工具 / 万法皆通）全是**软联动**：装了对应模组，职业才出现、转职书才可用；不装则自动隐藏。

## 安装

把 `maidtaskfilter-2.0.0.jar` 放进 `mods/` 即可。首次启动自动生成：

- `config/maidtaskfilter/jobs.json` —— 职业定义
- `config/maidtaskfilter-common.toml` —— 行为开关（4 项，默认值 = 本模组原始行为）

## 玩法教程

### 1. 制作转职书（TLM 祭坛）

转职书全部在 **TLM 本体的多方块祭坛** 合成（JEI / EMI 搜 `@maidtaskfilter` 可查全部配方）：

- **祭坛搭建**：羊毛 4×3×4 多方块结构，手持御币右击第四层中间偏左的红色羊毛正面完成构建；六个橡木柱子顶端放材料，P 点（Power）不足时合成会中断。
- **空白转职书**：书 + 纸 + 钻石（`forge:gems/diamond` 标签）×1
- **职业转职书**：空白转职书 + 该职业的配方材料（2–4 个，见下表）

### 2. 转职

手持转职书 **右键你的女仆**：

- 只有**主人**可以转职（别人右键会被拒）。
- 转职书**消耗**（生存模式 1 本转 1 个；`consumeJobBook=false` 则不消耗）。
- 已转职的女仆**默认允许再次覆盖**；设 `allowJobOverwrite=false` 后，已转职的女仆会被拒绝（且不消耗转职书），防止误点洗掉职业。
- **全能手册**（创造模式限定）：清空职业限制，解锁全部任务。
- 转职后女仆界面里，白名单之外的任务置灰不可选；强行点选会在聊天栏提示 `[职业限制]`。

### 3. 内置职业

| 职业（key） | 转职书配方材料 | 前置模组 |
|---|---|---|
| farmer 农夫 | 钻石锄 + 小麦种子 + 骨粉 | — |
| rancher 牧人 | 剪刀 + 奶桶 + 小麦 | — |
| melee 近卫 | 钻石剑 + 铁锭 | — |
| ranged 射手 | 弓 + 箭 | —（装 TACZ 后追加枪击任务） |
| fisher 渔师 | 钓鱼竿 + 水桶 | — |
| spellblade 魔剑 | 奥术精华 | maidspell（万法皆通） |
| baker 烘焙师 | 订单 + 烤箱便签 | maid_bakeries（女仆烘焙坊） |
| chef 厨师 | 餐厅菜单 | maid_restaurant（女仆餐厅） |
| waiter 服务员 | 碗 + 面包 | maid_restaurant |
| brewer 酿造师 | 玻璃瓶 + 糖 | kaleidoscope_compat（森罗物语：兼容） |
| operator 机械师 | 精密构件 + 铜锭 | muhc（手摇工具） |
| omni 全能 | —（创造/初始女仆限定） | — |

> 转职书配方材料的**权威来源是 jobs.json 的 `recipeItems` 字段**，不是硬编码——你改 jobs.json 里的材料，JEI 里的祭坛配方不会自动跟着变（配方是 JSON 文件），但游戏内使用逻辑一致。两个地方都要改。

### 4. 好感度加成

TLM 女仆好感度 0–3 级。每个职业的 `favorabilityBonuses` 定义每级的加成：

- **attribute**：任意已注册属性 + 数值（如 `minecraft:generic.movement_speed` + 0.15）。全模组共用一个固定 UUID，转职 / 升级时先摘除旧值再施加新值。
- **effect**：任意药水效果 + 等级（永久、无粒子、不显示）。模组记录「施加过哪些」，清除时按清单精确移除。

同属性在多个等级有加成时，**等级高的胜出**（内部按 level 升序应用，后写覆盖先写）。

### 5. 管理员指令

```
/maidjob list                列出全部职业
/maidjob set <职业key> [女仆名]  分配职业（不填名字选最近的女仆）
/maidjob get [女仆名]          查看女仆当前职业
/maidjob info <职业key>        职业详情与任务列表
```

- 权限等级 2（op 级），与 TLM 本体 `/tlm` 一致。
- 按名字查找时优先匹配命名牌名字，其次匹配模型名（换模型会变，建议先挂命名牌）。

---

## 任务 UID 查询（自定义职业必读）

女仆界面的任务按钮上，**按住 F3+H 打开高级提示**后，TLM 会在提示里追加一行：

```
工作模式 ID：<任务path>
```

注意它**只显示 path，不带命名空间**。补全规则：

- TLM 本体任务：`touhou_little_maid:<path>`
- 第三方任务：`<该模组的 modId>:<path>`

### TLM 1.5.3 全部任务 UID（已逐类反编译验证）

```
touhou_little_maid:idle             空闲
touhou_little_maid:farm             耕作
touhou_little_maid:sugar_cane       甘蔗
touhou_little_maid:melon            瓜类
touhou_little_maid:cocoa            可可
touhou_little_maid:grass            除草
touhou_little_maid:feed_animal      饲养动物
touhou_little_maid:shears           剪毛
touhou_little_maid:milk             挤奶
touhou_little_maid:honey            采蜜
touhou_little_maid:snow             清雪
touhou_little_maid:attack           近战
touhou_little_maid:ranged_attack    弓箭
touhou_little_maid:crossbow_attack  弩
touhou_little_maid:danmaku_attack   弹幕
touhou_little_maid:trident_attack   三叉戟
touhou_little_maid:gun_attack       枪械（装 TACZ 时可用）
touhou_little_maid:fishing          钓鱼
touhou_little_maid:torch            插火把
touhou_little_maid:extinguishing    灭火
touhou_little_maid:board_games      下棋
touhou_little_maid:feed             投喂主人
```

> ⚠️ 易错点：投喂主人是 `feed`，弓箭是 `ranged_attack`——语言文件里也这么叫（`task.touhou_little_maid.feed.desc` / `ranged_attack.desc`）。旧版本 jobs.json 里的 `feed_owner` / `bow_attack` 是**无效 UID**（过滤永远匹配不上），2.0.0 起已修正。

### 已验证的联动任务 UID（逐 jar 反编译）

```
maid_restaurant:cook / waiter                女仆餐厅 0.2.9
maid_bakeries:baking / oven / blender / cut / drink   女仆烘焙坊 1.0.3
muhc:hand_crank_task                         手摇工具 1.6.1
kaleidoscope_compat:pressing_tub / chopping_board / millstone   森罗物语：兼容 2.6.5
maidspell:spell_combat / spell_combat_far / spell_combat_melee  万法皆通（未在测试环境验证，如有出入以 F3+H 实测为准）
```

---

## 自定义职业教程

### 方案 A：只加职业数据（不需要转职书物品，管理员用 /maidjob 分配）

1. 打开 `config/maidtaskfilter/jobs.json`
2. 在 `jobs` 数组末尾加一个对象：

```jsonc
{
  "key": "lumberjack",            // 唯一英文 key
  "name": "伐木小女仆",            // 显示名
  "icon": "minecraft:iron_axe",   // 图标物品
  "description": "负责砍树",
  "tasks": [
    "touhou_little_maid:..."     // 任务 UID（查法见上节）
  ]
}
```

3. 重启服务器 → `/maidjob list` 可见 → `/maidjob set lumberjack` 分配。

### 方案 B：给新职业配转职书物品（需要改源码重新编译）

`jobs.json` 里的 `assignmentItem` / `recipeItems` 字段**只是说明性数据，不会自动生成物品和配方**。要一本真正能用的转职书，需要三步：

1. **注册物品**（`ModItems.java`）：

```java
public static final RegistryObject<JobBookItem> LUMBERJACK_BOOK =
        ITEMS.register("lumberjack_book", () -> new JobBookItem("lumberjack"));
```

并把物品加进创造标签页的 `displayItems`。

2. **加语言条目**（`assets/maidtaskfilter/lang/zh_cn.json` 与 `en_us.json`）：

```json
"item.maidtaskfilter.lumberjack_book": "伐木手册"
```

3. **加祭坛配方**（`data/maidtaskfilter/recipes/altar/craft_lumberjack_book.json`，仿照现有文件）：

```jsonc
{
  "type": "touhou_little_maid:altar_crafting",
  "output": { "type": "minecraft:item", "nbt": { "Item": { "id": "maidtaskfilter:lumberjack_book", "Count": 1 } } },
  "power": 0.2,
  "ingredients": [
    { "item": "maidtaskfilter:blank_job_book" },
    { "item": "minecraft:iron_axe" }
  ]
}
```

然后 `./gradlew build`，把新 jar 放回 `mods/`。

### jobs.json 字段速查

| 字段 | 说明 |
|---|---|
| `commonTasks` | 所有职业共享的通用任务（始终生效） |
| `conditionalCommonTasks` | `{ modId: [任务UID] }` —— 装了这个 mod 才追加的通用任务 |
| `jobs[].key` | 职业内部标识，对应转职书 jobKey |
| `jobs[].requiresMod` | 硬前置 modId，未装则职业隐藏 |
| `jobs[].tasks` | 基础任务列表 |
| `jobs[].conditionalTasks` | `{ modId: [任务UID] }` —— 装了才注入 |
| `jobs[].favorabilityBonuses` | 好感度加成数组 `{ "level": 0-3, "attribute": ..., "value": ... }` 或 `{ "level": 0-3, "effect": ..., "effectLevel": ... }` |

注意事项：

- 以 `_` 开头的键（`_readme` / `_comment`）**只是给人看的注释**，解析时被忽略。
- **同一等级多个加成 → 写多条 entry**（level 相同即可），不要自造 `effect2` / `effectLevel2` 这类字段——解析器会静默丢弃，效果不生效且不报错。
- 某个职业写错了只会跳过它自己（日志有 error），不影响其他职业。
- **任务 UID 写错不会报错**——它会变成一个永远匹配不上的白名单项，症状是「女仆界面里该任务置灰 / 点不了」。拿不准就用 F3+H 查真实 ID。

### 行为开关（`config/maidtaskfilter-common.toml`）

| 项 | 默认 | 说明 |
|---|---|---|
| `restrictUnassignedMaid` | true | 未转职女仆是否只能「空闲」。false = 不限制，职业系统变纯可选 |
| `consumeJobBook` | true | 转职书是否消耗。false = 一本反复用 |
| `allowJobOverwrite` | true | 已转职女仆能否被覆盖。false = 拒绝覆盖且不消耗书 |
| `commandSearchRange` | 16 | `/maidjob set/get` 搜索女仆的半径（1–128 格） |

---

## 构建（源码）

```bash
git clone https://github.com/tpzdcc/Maid-Professions.git
cd Maid-Professions/forge-1.20.1-47.4.0-mdk
./gradlew build
# 产物：build/libs/maidtaskfilter-2.0.0.jar
```

- JDK 17；TLM 依赖经 CurseMaven 拉取（`curse.maven:touhou-little-maid-355044:8061847`），首次构建需要联网。

## 项目结构

```
src/main/java/com/maidtaskfilter/
├── MaidTaskFilterMod.java        # @Mod 主类、TaskDataKey、白名单合并、加成应用/清除
├── MaidFilterExtension.java      # TLM @LittleMaidExtension（注册 TaskDataKey）
├── MaidTaskFilterConfig.java     # ForgeConfigSpec（4 项行为开关）
├── JobConfig.java                # jobs.json 加载（含默认模板）
├── JobDefinition.java            # 职业定义 POJO
├── FavorabilityBonus.java        # 好感度加成 POJO
├── JobBookItem.java              # 转职书物品逻辑
├── JobCommand.java               # /maidjob 指令
├── ForgeEventHandler.java        # 指令注册、配置加载、好感度变化
├── MaidTaskEnableHandler.java    # 任务拦截（取消事件 + 聊天提示）
├── ModItems.java                 # 物品与创造标签页注册
└── mixin/TaskManagerMixin.java   # 客户端任务列表过滤
```

## 作者与许可

- 作者：DoomsdayRestaurantDev（末日餐馆整合包）
- 版本：2.0.0
- 许可：All Rights Reserved（当前）

---

## 已知说明

- 本模组的前身（1.0.0）带有一个「订单完成自动加声誉」的 Mixin（`CoinUtilsMixin`），2026-09-15 已**摘出**，将作为独立的 `otc-reputation` 模组单独发布（与「下单了」联动的声誉系统，与本模组的职业功能无关）。
- 转职书贴图目前复用原版书贴图（`minecraft:item/book`），正式贴图制作中。
- 任务拦截机制基于 TLM 的 `MaidTaskEnableEvent`：服务端 `setCanceled(true)` 拒绝设置（TLM 收到取消后在 `isEnable`/`setTask` 之前直接返回），客户端置灰按钮，并给主人发一条可翻译的聊天提示。
