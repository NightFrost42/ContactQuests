# Welcome to ContactQuests Wiki / 欢迎来到 ContactQuests 维基

<div align="center">

**Immersive Logistics & Questing Integration | 沉浸式物流与任务联动**

[🇺🇸 English](#-english-introduction) | [🇨🇳 中文介绍](#-中文介绍)

</div>

---

<a id="english-introduction"></a>

## 🇺🇸 English Introduction

**ContactQuests** is a bridge mod that creates a deep integration between **[Contact]** (Logistics/Mail) and *
*[FTB Quests]**.

Unlike traditional quests where items are instantly detected in your inventory, ContactQuests requires players to engage
with the physical world of logistics. It transforms the abstract concept of "Quest Submission" into an immersive
gameplay loop involving packaging, mailing, and waiting for deliveries.

### 🧩 Key Features

* **Immersive Questing**: Move beyond simple "checkmarks." Submit quest items by physically mailing packages to specific
  NPCs via Mailboxes.
* **New Task Types**:
* 📦 **Parcel Task**: Pack required items and send them to the target NPC.
* 📨 **Postcard Task**: Write and send postcards with specific content or styles.
* 🧧 **Red Packet Task**: Send currency or items via Red Packets (if supported).


* **Postal Rewards**: Rewards aren't just clicked; they arrive at your doorstep! Receive quest rewards as parcels
  delivered directly to your mailbox.
* **Team Integration**: Seamless support for FTB Teams. Use the **Team Binding Card** to ensure rewards are delivered to
  your team's shared mailbox.
* **NPC Interaction System**: Define custom responses when players send incorrect or extra items. Create NPCs with
  personality who can reject mail, confiscate items, or send easter egg replies.

### ℹ️ Note: When Quest & Addressee Changes Take Effect

The list of valid addressees is built from your quests. The mod notices an edit as soon as the quest file is marked
changed and rebuilds that list **the next time you use a mailbox**, so a quest you have just created or edited can be
mailed to **immediately** — no restart needed.

If the list ever looks stale (for example after hand-editing `npc_config.json`, or in a very large quest pack), run
`/reload` or rejoin the world to force a full rebuild. A new addressee also gets its own entry in `npc_config.json`
automatically the first time it appears in a quest.

### 📚 Documentation Navigation

* **[[Getting Started]](https://github.com/NightFrost42/ContactQuests/wiki/Getting-Started---%E5%BF%AB%E9%80%9F%E5%85%A5%E9%97%A8)**: How to bind mailboxes and complete your first delivery quest.
* **[[Task & Rewards]](https://github.com/NightFrost42/ContactQuests/wiki/Tasks-&-Rewards-Mechanics---%E4%BB%BB%E5%8A%A1%E4%B8%8E%E5%A5%96%E5%8A%B1%E6%9C%BA%E5%88%B6%E8%AF%A6%E8%A7%A3)**: Detailed breakdown of new task types and reward configurations.
* **[[Configuration Guide]](https://github.com/NightFrost42/ContactQuests/wiki/Configuration-Guide---%E9%85%8D%E7%BD%AE%E6%96%87%E4%BB%B6%E6%8C%87%E5%8D%97)**: Global settings and setting up delivery delays.
* **[[NPC Interaction]](https://github.com/NightFrost42/ContactQuests/wiki/NPC-Interaction-Config---NPC-%E4%BA%A4%E4%BA%92%E9%85%8D%E7%BD%AE)**: How to configure `npc_config.json` for custom reply logic.
* **[[Commands & Permissions]](https://github.com/NightFrost42/ContactQuests/wiki/Commands-&-Permissions---%E5%91%BD%E4%BB%A4%E4%B8%8E%E6%9D%83%E9%99%90)**: How to use the mod's command function
* **[[Advanced Quest Logic]](https://github.com/NightFrost42/ContactQuests/wiki/Advanced-Quest-Logic---%E8%BF%9B%E9%98%B6%E4%BB%BB%E5%8A%A1%E9%80%BB%E8%BE%91)**: (Advanced) How to configure the advanced additional configuration items for a Quest
* **[[KubeJS Integration]](https://github.com/NightFrost42/ContactQuests/wiki/KubeJS-Integration---KubeJS-%E8%81%94%E5%8A%A8)**: (Advanced) Scripting support for custom logistics events.

---

<a id="中文介绍"></a>

## 🇨🇳 中文介绍

**ContactQuests** 是一个旨在实现 **[Contact]** (邮件/物流) 与 **[FTB Quests]** (任务) 深度联动的模组。

本模组打破了传统任务“点击即完成”的抽象模式，将任务流程与物理物流紧密结合。它让玩家必须通过真实的打包、投递和等待收件来推进任务进度，为整合包带来更具沉浸感的“叙事”体验。

### 🧩 核心特性

* **沉浸式任务体验**：告别单纯的物品检测。你需要将任务物品打包，并通过邮筒寄送给指定的 NPC 来完成任务。
* **全新的任务类型**：
* 📦 **邮递任务 (Parcel Task)**：将所需物品打包并寄往指定收件人。
* 📨 **明信片任务 (Postcard Task)**：向 NPC 寄送特定内容或样式的明信片。
* 🧧 **红包任务 (Red Packet Task)**：通过红包寄送指定的物品。


* **物流奖励系统**：奖励不再凭空出现！完成任务后，奖励将以包裹的形式由物流系统派送到你的邮箱中。
* **团队无缝协作**：完美支持 FTB Teams 队伍系统。使用 **团队绑定器 (Team Binding Card)** 将邮箱与队伍绑定，确保以队伍为单位收到任务奖励。
* **NPC 交互系统**：高度可配置的 NPC 智能反馈。当玩家寄错物品时，NPC 可以根据配置做出拒收、没收甚至触发彩蛋回复等反应。

### ℹ️ 小提示：任务与收件人的改动何时生效

模组的"有效收件人"名单来自你的任务。任务文件一旦被标记为已改动，模组会在**下一次使用邮筒时**重建这份名单，
因此刚新建或刚改完的任务可以**立刻**寄送，无需重启游戏。

如果名单看起来仍是旧的（例如你手工修改了 `npc_config.json`，或整合包任务量极大），执行 `/reload` 或重进世界
即可强制完整重建。当某个收件人第一次出现在任务里时，模组也会自动在 `npc_config.json` 中补上对应条目。

### 📚 文档导航

* **[[快速入门]](https://github.com/NightFrost42/ContactQuests/wiki/Getting-Started---%E5%BF%AB%E9%80%9F%E5%85%A5%E9%97%A8)**：如何绑定队伍邮箱以及完成你的第一个物流任务。
* **[[任务与奖励详解]](https://github.com/NightFrost42/ContactQuests/wiki/Tasks-&-Rewards-Mechanics---%E4%BB%BB%E5%8A%A1%E4%B8%8E%E5%A5%96%E5%8A%B1%E6%9C%BA%E5%88%B6%E8%AF%A6%E8%A7%A3)**：各种新任务类型（邮递、明信片、红包）的详细说明。
* **[[配置文件指南]](https://github.com/NightFrost42/ContactQuests/wiki/Configuration-Guide---%E9%85%8D%E7%BD%AE%E6%96%87%E4%BB%B6%E6%8C%87%E5%8D%97)**：通用设置与物流延迟机制的配置。
* **[[NPC 交互配置]](https://github.com/NightFrost42/ContactQuests/wiki/NPC-Interaction-Config---NPC-%E4%BA%A4%E4%BA%92%E9%85%8D%E7%BD%AE)**：如何编写 `npc_config.json` 来自定义 NPC 的回信逻辑。
* **[[命令与权限]](https://github.com/NightFrost42/ContactQuests/wiki/Commands-&-Permissions---%E5%91%BD%E4%BB%A4%E4%B8%8E%E6%9D%83%E9%99%90)**: 如何使用模组的命令功能
* **[[进阶任务逻辑]](https://github.com/NightFrost42/ContactQuests/wiki/Advanced-Quest-Logic---%E8%BF%9B%E9%98%B6%E4%BB%BB%E5%8A%A1%E9%80%BB%E8%BE%91)**：(进阶)如何配置任务的高级额外配置项
* **[[KubeJS 联动]](https://github.com/NightFrost42/ContactQuests/wiki/KubeJS-Integration---KubeJS-%E8%81%94%E5%8A%A8)**：(进阶) 使用脚本自定义物流事件。

---

### 🔗 Links / 链接

* **Source Code**: [GitHub](https://github.com/NightFrost42/ContactQuests)
* **Issue Tracker**: [Report Bugs / 反馈问题](https://github.com/NightFrost42/ContactQuests/issues)
* **Mod Download**: [[CurseForge](https://www.curseforge.com/minecraft/mc-mods/contactquests)/[Modrinth](https://modrinth.com/mod/contactquests)]
