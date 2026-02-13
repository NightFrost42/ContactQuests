# ContactQuests

<div align="center">

**[ 🇺🇸 English Guide ](#english-guide) | [ 🇨🇳 中文介绍 ](#中文介绍)**

</div>

---

<a id="english-guide"></a>

## 🇺🇸 English Guide

**ContactQuests** is a mod designed to achieve deep integration between **[Contact] (Mail/Logistics Mod)** and *
*[FTB Quests] (Quest Mod)**.

This mod extends FTB Quests with brand new **Tasks** and **Rewards** types, allowing modpack creators to design quest
lines based on "Logistics Delivery" and "Letter Interaction". Players need to send items to specific NPCs via Contact's
mailboxes to complete tasks, or receive quest reward packages through their mailboxes.

### Player Usage

#### How to Submit Tasks

1. Prepare the packaging materials and the items required for submission.
2. Click the item in the Quest UI, then click **Submit** to auto-fill (red text will appear if materials are
   insufficient).
3. Pack the package using the packing interface.
4. Put the package into a Mailbox and paste the recipient's address.
5. Wait for delivery to complete the quest.

#### How to Claim Rewards

1. Enter the FTB Quests interface and claim the **Mailbox Binder** from the sidebar (or emergency items).
2. Hold the Mailbox Binder, crouch (Shift), and right-click to bind your Mailbox.
3. Go to the Quest interface and click **Claim Reward**.
4. Wait for the reward to arrive in your mailbox.
5. Take out the reward from the mail (if it is a package, hold it and right-click to unpack).

---

### Configuration

ContactQuests provides two main configuration files to control global mechanics and custom NPC interaction logic.

#### 1. General Config

**Path:** `config/contactquests/contactquests-common.toml`

Controls global game mechanics and client-side behavior.

| Config Option            | Type    | Default | Description                                                                                                                                                                             |
|:-------------------------|:--------|:--------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **enable_delivery_time** | Boolean | `false` | **Enable Delivery Delay**<br>If `false`, all packages and letters arrive instantly.<br>If `true`, delivery follows the `deliveryTime` set in the NPC config.                            |
| **auto_fill_speed**      | Int     | `1`     | **Auto-fill Typing Speed** (Client)<br>Controls the typewriter animation speed when auto-filling postcards.<br>`0`: Instant<br>`1`: Very Fast<br>`2`: Normal<br>`>2`: Slow (Unit: Tick) |
| **retry_interval**       | Int     | `10`    | **Retry Interval**<br>The interval at which the system attempts to redeliver when the target mailbox is full or sending fails (Unit: Tick).                                             |

---

#### 2. NPC Interaction Config

**Path:** `config/contactquests/npc_config.json`

This is the core configuration file. When a player sends **Wrong Items** (not matching quest requirements) or **Extra
Items** (quest already completed or item not needed) to a Quest NPC, the mod intercepts these items and triggers the
logic defined here.

**Note:** When the mod starts or reloads (using `/reload`), it automatically scans all "Target Addressees" defined in
FTB Quests and syncs them to this file. New NPCs default to an empty configuration.

**JSON Structure**
The root object is a Map where the **Key** is the **NPC Name** and the **Value** is the NPC's detailed config.

```json
"NPC_Name": {
"deliveryTime": 0,
"errorSolve": [...]
}

```

* **deliveryTime** (Int): Delivery time (Unit: Tick). Only effective when `enable_delivery_time` is `true`.
* **errorSolve** (List): Interaction logic list. The system matches the most appropriate config based on the *
  *accumulated count** (triggerCount) of wrong/extra items sent by the player to this NPC.

**Interaction Logic (ErrorSolveData)**

| Field          | Type    | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|----------------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **count**      | Int     | **Trigger Threshold**.<br><br>Active when the accumulated error count reaches or exceeds this value. The system selects the entry with the highest `count` that fits.                                                                                                                                                                                                                                                                                                                                                                                                                  |
| **returnType** | String  | **Response Behavior**. Decides how the NPC handles the wrong items.<br><br>- `NOW`: **Return Immediately**. NPC writes back and returns the item (simulates rejection).<br><br>- `DISCARD`: **Discard**. NPC confiscates the item (item vanishes, **NO REFUND**), only replies with a letter.<br><br>- `SAVE`: **Storage**. NPC accepts the item and stores it in cache data, only replies with a letter.<br><br>- `WITHREWARDS`: **Return with Rewards**. Usually used for Easter eggs/rewards. Triggers a return of all previously `SAVE` items plus the current item to the player. |
| **styleType**  | String  | **Postcard Style Strategy**.<br><br>- `RANDOM`: Randomly select from all registered styles.<br><br>- `SPECIFIC`: Use styles defined in the `message` list below.<br><br>- `SAME`: Enforce the style defined in the `style` field below.                                                                                                                                                                                                                                                                                                                                                |
| **style**      | String  | **Forced Style ID**.<br><br>Effective when `styleType` is `SAME`. Example: `contact:touhou_little_maid` or `contact:creeper`.                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| **isAllEnder** | Boolean | **Global Ender Mail Switch**.<br><br>If `true`, ignores settings in `message` and **forces** all replies via Ender channel (instant, ignores distance).                                                                                                                                                                                                                                                                                                                                                                                                                                |
| **message**    | List    | **Reply Message Pool**. Contains multiple `MessageData` objects; the system randomly picks one as the reply content.                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |

**Message Data (MessageData)**

| Field       | Type    | Description                                                                          |
|-------------|---------|--------------------------------------------------------------------------------------|
| **text**    | String  | **Reply Content**. Supports `\n` for line breaks.                                    |
| **style**   | String  | **Specific Style**. Used when `styleType` is `SPECIFIC`. Example: `contact:default`. |
| **isEnder** | Boolean | **Single Message Ender**. Only effective when `isAllEnder` is `false`.               |

---

### Commands

* **Clear NPC Cache**: `contactquests admin removedata <npc_name>`
* *Desc*: Clears items stored (`SAVE` state) and interaction counts for the specified NPC.


* **Set Interaction Count**: `contactquests admin setcount <npc_name> <count>`
* *Desc*: Manually modifies the interaction count between a player and an NPC. Used for testing `errorSolve` stages.


* **Get Team Binder**:
* `contactquests get_binder_internal` (Self)
* `contactquests admin givebinder <player>` (Give to others)
* *Desc*: Obtains the item used to bind a Mailbox to an FTB Team, ensuring rewards are correctly delivered to the team.

---

---

<a id="中文介绍"></a>

## 🇨🇳 中文介绍

**ContactQuests** 是一个旨在实现 **[Contact] (邮件/物流模组)** 与 **[FTB Quests] (任务模组)** 深度联动的模组。

本模组为 FTB Quests 扩展了全新的 **任务 (Tasks)** 和 **奖励 (Rewards)** 类型，允许整合包作者创建基于“物流寄送”和“信件交互”的任务流程。玩家需要通过
Contact 的邮筒向指定的 NPC 发送物品来完成任务，或通过邮箱查收来自 NPC 的任务奖励包裹。

### 玩家使用方式

#### 任务提交方式

1. 根据任务准备打包用的材料以及要提交的材料。
2. 点击任务中的物品，然后点击 **submit** 自动填充（如果材料不够会有红字提示）。
3. 将放好的打包界面打包。
4. 塞入邮筒，在地址栏黏贴收件人。
5. 等待送达完成任务。

#### 奖励领取方式

1. 进入 FTB Quests 的任务界面后，在侧边栏领取 **邮箱绑定器**。
2. 手持邮箱绑定器蹲下右键绑定邮箱。
3. 进入任务界面点击领取。
4. 等待奖励到达邮箱。
5. 邮件取出奖励（如果是包裹，拿在手里点击邮件拆包）。

---

### 配置文件说明

ContactQuests 提供了两个主要的配置文件，分别用于控制全局机制和自定义 NPC 的交互逻辑。

#### 1. 通用配置 (General Config)

**文件位置：** `config/contactquests/contactquests-common.toml`

控制全局的游戏机制和客户端表现。

| 配置项                      | 类型      | 默认值     | 说明                                                                                                                        |
|--------------------------|---------|---------|---------------------------------------------------------------------------------------------------------------------------|
| **enable_delivery_time** | Boolean | `false` | **启用物流延迟**<br><br>如果是 `false`，所有包裹和信件将立即送达。<br><br>如果是 `true`，则会根据 NPC 配置中的 `deliveryTime` 进行延迟派送。                        |
| **auto_fill_speed**      | Int     | `1`     | **自动填充打字速度** (客户端)<br><br>控制“一键填充”明信片时的打字机动画速度。<br><br>`0`: 瞬间完成<br><br>`1`: 极快<br><br>`2`: 正常<br><br>`>2`: 慢速 (单位: Tick) |
| **retry_interval**       | Int     | `10`    | **重试间隔**<br><br>当目标邮箱已满或发送失败时，系统尝试重新投递的检测间隔 (单位: Tick)。                                                                   |

---

#### 2. NPC 交互配置 (NPC Config)

**文件位置：** `config/contactquests/npc_config.json`

这是本模组的核心配置文件。当玩家向任务 NPC 发送了**错误的物品**（不符合任务需求）或者**多余的物品**（任务已完成或不需要该物品）时，模组会拦截这些物品并触发此文件中定义的交互逻辑。

**注意：** 模组启动或重载（使用`/reload`指令）时，会自动扫描所有 FTB 任务中定义的“接收人名称”（Target Addressee），并将其同步到此文件中。如果是新添加的 NPC，默认配置为空。

**JSON 数据结构**
根对象是一个 Map，键（Key）是 **NPC 的名字**，值是该 NPC 的详细配置。

```json
"NPC名称": {
  "deliveryTime": 0,
  "errorSolve": [ ... ]
}

```

* **deliveryTime** (Int): 物流耗时（单位：Tick）。仅在通用配置 `enable_delivery_time` 为 `true` 时生效。
* **errorSolve** (List): 交互逻辑列表。系统会根据玩家与该 NPC 发送错误/多余物品的**累计次数**（triggerCount）来匹配列表中最合适的配置。

**交互逻辑 (ErrorSolveData)**

`errorSolve` 列表中的每一项包含以下字段：

| 字段             | 类型      | 说明                                                                                                                                                                                                                                                                                       |
|----------------|---------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **count**      | Int     | **触发阈值**。<br><br>当玩家累计发送错误/多余物品次数达到或超过此数值时生效。系统会选取所有符合条件中 `count` 最大的那一项。                                                                                                                                                                                                                |
| **returnType** | String  | **回复行为类型**。决定 NPC 如何处理你发过去的错误物品。<br><br>- `NOW`: **立即退回**。NPC 写回信并将物品原样退回（模拟拒收）。<br><br>- `DISCARD`: **丢弃**。NPC 没收物品（物品消失，**不退还**），仅回复一封信。<br><br>- `SAVE`: **暂存**。NPC 收下物品并存入缓存数据中，仅回复一封信。<br><br>- `WITHREWARDS`: **随奖励返还**。通常用于 Quest 奖励阶段。触发时，NPC 会将之前所有 `SAVE` 的物品以及本次发送的物品打包发回给玩家。 |
| **styleType**  | String  | **明信片样式策略**。<br><br>- `RANDOM`: 从所有已注册的明信片样式中随机选择。<br><br>- `SPECIFIC`: 使用下方 `message` 列表中定义的样式。<br><br>- `SAME`: 强制使用下方 `style` 字段定义的样式。                                                                                                                                                |
| **style**      | String  | **强制样式 ID**。<br><br>当 `styleType` 为 `SAME` 时生效。例值：`contact:touhou_little_maid` 或 `contact:creeper`。                                                                                                                                                                                      |
| **isAllEnder** | Boolean | **全局末影邮件开关**。<br><br>如果为 `true`，则无视下方 `message` 中的设置，**强制**所有回复通过末影渠道发送（无视距离和时间）。                                                                                                                                                                                                        |
| **message**    | List    | **回复消息池**。包含多个 `MessageData` 对象，系统会从中随机抽取一条作为回信内容。                                                                                                                                                                                                                                       |

**消息数据 (MessageData)**

`message` 列表中的每一项包含：

| 字段          | 类型      | 说明                                                               |
|-------------|---------|------------------------------------------------------------------|
| **text**    | String  | **回信内容**。支持使用 `\n` 换行。                                           |
| **style**   | String  | **特定样式**。当 `styleType` 为 `SPECIFIC` 时使用此样式。例值：`contact:default`。 |
| **isEnder** | Boolean | **单条消息是否末影**。仅在 `isAllEnder` 为 `false` 时生效。                      |

---

### 配置示例 (Config Example)

以下是一个完整的 `npc_config.json` 示例，展示了多种交互逻辑的组合：

```json
{
  "QuestNPC": {
    "deliveryTime": 1200,
    "errorSolve": [
      {
        "count": 1,
        "returnType": "NOW",
        "styleType": "RANDOM",
        "style": "contact:default",
        "isAllEnder": false,
        "message": [
          {
            "text": "这是你第一次给我寄错东西，请拿回去吧。",
            "style": "contact:default",
            "isEnder": false
          }
        ]
      },
      {
        "count": 3,
        "returnType": "DISCARD",
        "styleType": "SAME",
        "style": "contact:creeper",
        "isAllEnder": true,
        "message": [
          {
            "text": "你已经寄错三次了！我很生气，没收了你的物品！(物品已丢失)",
            "isEnder": false
          }
        ]
      },
      {
        "count": 5,
        "returnType": "SAVE",
        "styleType": "SPECIFIC",
        "isAllEnder": false,
        "message": [
          {
            "text": "好吧，这次我先帮你保管着。",
            "style": "contact:touhou_little_maid"
          }
        ]
      },
      {
        "count": 10,
        "returnType": "WITHREWARDS",
        "styleType": "SAME",
        "style": "contact:meikai",
        "isAllEnder": false,
        "message": [
          {
            "text": "到时候我给你寄东西的时候连带我这里帮你保留的东西一起寄回去吧"
          }
        ]
      }
    ]
  }
}

```

### 逻辑解析

1. **第 1-2 次错误交互** (`count: 1`, `NOW`)：NPC 拒收，物品被立即退回。
2. **第 3-4 次错误交互** (`count: 3`, `DISCARD`)：**触发 DISCARD**。NPC 没收了玩家的物品（不退还），并使用 `contact:creeper` 样式的明信片回复。由于 `isAllEnder` 为 `true`，这封斥责信会通过末影渠道瞬间送达。
3. **第 5-9 次错误交互** (`count: 5`, `SAVE`)：NPC 收下物品（玩家背包中消失，但存入服务端数据），回复样式指定为 `contact:touhou_little_maid`。
4. **第 10 次及以后** (`count: 10`, `WITHREWARDS`)：NPC 将之前第 5-9 次暂存的物品在领取任务奖励时一并打包退还给玩家（注意：第
   3-4 次被 `DISCARD` 的物品**不会**找回）。此时回复使用了指定的 `contact:meikai` 样式。

---

### 管理命令 (Admin Commands)

在游戏中可以使用以下命令来调试或重置 NPC 数据：

* **清除 NPC 缓存数据**：
  `contactquests admin removedata <npc_name>`
* *说明*：清除指定 NPC 名下暂存的物品（即 `SAVE` 状态下的物品）和交互计数。


* **设置交互次数**：
  `contactquests admin setcount <npc_name> <count>`
* *说明*：手动修改玩家与该 NPC 的交互计数。用于测试不同的 `errorSolve` 阶段。


* **获取团队绑定器**：
  `contactquests get_binder_internal` (自身获取)
  `contactquests admin givebinder <player>` (给予他人)
* *说明*：获取用于绑定邮箱与 FTB 队伍的道具，确保任务奖励能正确发放给队伍。
