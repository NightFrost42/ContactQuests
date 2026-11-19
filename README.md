# ContactQuests

[中文介绍](#中文介绍) · [English Introduction](#english-introduction)

<a id="中文介绍"></a>

## 中文介绍

ContactQuests 把 **Contact** 的邮件系统接到 **FTB Quests**：任务物品可以寄出去，邮件奖励会寄到队伍邮箱（配合 **FTB Teams** 区分队伍）。

### 怎么玩

1. 在任务界面看清要求，备好所需物品和对应邮件材料。
2. 点提交按钮帮助填充内容并复制收件人，核对、完成包装后通过 **邮筒（Postbox）** 寄出。
3. 使用 **团队绑定器（Team Binding Card）** 蹲下右键 **邮箱（Mailbox）** 绑定队伍。
4. 完成任务后在任务界面领取邮件奖励，回邮箱收取；若奖励为 **明信片选择奖励**，选好心仪选项并向 NPC 寄回明信片后即可领奖。

具体操作见 [快速入门](https://github.com/NightFrost42/ContactQuests/wiki/Getting-Started---%E5%BF%AB%E9%80%9F%E5%85%A5%E9%97%A8#中文指南)。

### 主要功能

* **三类邮件任务**：
  * **邮递任务**：寄出指定物品。
  * **红包任务**：寄出指定物品，可设祝福语。
  * **明信片任务**：按设定的样式和文字寄出。
* **六类邮件奖励**：包裹、红包、明信片、随机奖励表包裹（按权重取一项）、全奖励表包裹（处理所有条目），以及 **明信片选择奖励（玩家自选奖励表条目并寄出明信片领奖）**；奖励表里的非物品条目按本来的奖励形式发放，不会都变成邮件物品；所有邮件奖励还支持 **隐藏奖励内容（神秘包裹盲盒）**。
* **收件人、寄错处理与可选 AI NPC 书信**：任务 NPC 默认只需配置收件人名称，不需要世界里有实体 NPC；支持阶梯式寄错物品处理与回信；整合包/服主还可按需在 `config/contactquests/npc_llm.json` 启用服务端 OpenAI 兼容大模型 NPC，让玩家通过明信片自由书信互动、托管附件或动态接取任务。
* **标签与互斥**：主手右键带解锁标签的物品为当前队伍解锁标签（支持 `ALL` 与 `ANY` 模式，任务仍需满足其他条件）；互斥规则可让整合包作者设定任务分支。
* **可选 KubeJS 与三方联动**：提供收信事件、占位符扩展、`tagUnlocked`/`mutexChanged`/`scriptBlockChanged` 状态事件与持久化任务图谱 CRUD API（可脚本化创建/发布/修改章节、任务、子任务与奖励），并在客户端可选联动 **Jade** 与 **JEI**（无 KubeJS 时亦可独立运行）。

### 版本、文档与下载

当前正式版本为 [**2.1.6-1.21.1**](https://github.com/NightFrost42/ContactQuests/releases/tag/2.1.6-1.21.1)，适用于 **Minecraft 1.21.1 / NeoForge**。详细更新说明见 [CHANGELOG.md](CHANGELOG.md)，依赖与兼容范围请查看发布页。

* **玩家入门**：[Wiki 首页](https://github.com/NightFrost42/ContactQuests/wiki/Home#中文指南) · [快速入门](https://github.com/NightFrost42/ContactQuests/wiki/Getting-Started---%E5%BF%AB%E9%80%9F%E5%85%A5%E9%97%A8#中文指南)
* **整合包与配置**：[任务与奖励机制](https://github.com/NightFrost42/ContactQuests/wiki/Tasks-%26-Rewards-Mechanics---%E4%BB%BB%E5%8A%A1%E4%B8%8E%E5%A5%96%E5%8A%B1%E6%9C%BA%E5%88%B6%E8%AF%A6%E8%A7%A3#中文指南) · [配置文件指南](https://github.com/NightFrost42/ContactQuests/wiki/Configuration-Guide---%E9%85%8D%E7%BD%AE%E6%96%87%E4%BB%B6%E6%8C%87%E5%8D%97#中文配置) · [NPC 交互配置](https://github.com/NightFrost42/ContactQuests/wiki/NPC-Interaction-Config---NPC-%E4%BA%A4%E4%BA%92%E9%85%8D%E7%BD%AE#中文配置) · [进阶任务逻辑](https://github.com/NightFrost42/ContactQuests/wiki/Advanced-Quest-Logic---%E8%BF%9B%E9%98%B6%E4%BB%BB%E5%8A%A1%E9%80%BB%E8%BE%91#中文指南) · [命令与权限](https://github.com/NightFrost42/ContactQuests/wiki/Commands-%26-Permissions---%E5%91%BD%E4%BB%A4%E4%B8%8E%E6%9D%83%E9%99%90#中文指南) · [KubeJS 联动](https://github.com/NightFrost42/ContactQuests/wiki/KubeJS-Integration---KubeJS-%E8%81%94%E5%8A%A8#中文指南)
* **下载与发布**：[CurseForge](https://www.curseforge.com/minecraft/mc-mods/contactquests) · [Modrinth](https://modrinth.com/mod/contactquests) · [GitHub 发布页](https://github.com/NightFrost42/ContactQuests/releases/tag/2.1.6-1.21.1)
* **项目仓库**：[源码仓库](https://github.com/NightFrost42/ContactQuests) · [问题反馈](https://github.com/NightFrost42/ContactQuests/issues)

---

<a id="english-introduction"></a>

## English Introduction

ContactQuests connects **Contact**'s mail system to **FTB Quests**: quest items go out by mail, and mail rewards arrive in your team Mailbox (using **FTB Teams** to separate teams).

### How to Play

1. Check the requirements on the quest screen and prepare the items and the required mail materials.
2. Click the submit button to help fill in the mail and copy the recipient, then check, pack, and send it through a **Postbox**.
3. Sneak-right-click a **Mailbox** with a **Team Binding Card** to bind it to your team.
4. Collect mail rewards from the quest screen and pick them up at your Mailbox. For **Postcard Choice** rewards, choose an option in the UI and mail the prepared postcard back to the NPC to claim it.

See [Getting Started](https://github.com/NightFrost42/ContactQuests/wiki/Getting-Started---%E5%BF%AB%E9%80%9F%E5%85%A5%E9%97%A8#english-guide) for details.

### Key Features

* **Three mail tasks**:
  * **Parcel**: Send specified items.
  * **Red Packet**: Send specified items, with an optional greeting.
  * **Postcard**: Match configured styles and text.
* **Six mail rewards**: Parcel, Red Packet, Postcard, Random Reward Table Parcel (draws one entry by weight), Full Reward Table Parcel (processes all entries), and **Postcard Choice Reward** (let players pick an entry from a Reward Table and mail a postcard back to claim it). Non-item entries in reward tables are granted in their original form instead of all becoming mailed items, and any mail reward can enable **Hide reward contents** to appear as a sealed parcel until claimed.
* **Recipients, wrong-item handling & optional AI NPCs**: Quest NPCs work as configured recipient names with no physical entity required, supporting tiered wrong-item handling and reply letters. Server admins/modpack authors can also opt into server-side OpenAI-compatible AI NPCs in `config/contactquests/npc_llm.json` for free-form postcard correspondence, item escrow, and dynamic quest publishing.
* **Tags & mutual exclusion**: Right-click an item carrying unlock tags in your main hand to unlock them for your team (supports both `ALL` and `ANY` modes alongside normal quest prerequisites). Mutual exclusion lets modpack authors set up branching quests.
* **Optional KubeJS, Jade & JEI integrations**: Includes mail/state events (`mailReceived`, `registerReplacers`, `tagUnlocked`, `mutexChanged`, `scriptBlockChanged`), persistent quest-graph CRUD APIs (`createChapter`, `createQuest`, `publishQuest`, `update*`, `moveQuest`, `delete*`), Jade team tooltips, and JEI recipe lookup fallback—while running cleanly without KubeJS or JEI installed.

### Version, Docs & Downloads

Current official release: [**2.1.6-1.21.1**](https://github.com/NightFrost42/ContactQuests/releases/tag/2.1.6-1.21.1) for **Minecraft 1.21.1 / NeoForge**. See [CHANGELOG.md](CHANGELOG.md) and the release pages for full notes, dependencies, and compatible versions.

* **For players**: [Wiki Home](https://github.com/NightFrost42/ContactQuests/wiki/Home#english-guide) · [Getting Started](https://github.com/NightFrost42/ContactQuests/wiki/Getting-Started---%E5%BF%AB%E9%80%9F%E5%85%A5%E9%97%A8#english-guide)
* **For modpack authors**: [Tasks & Rewards Mechanics](https://github.com/NightFrost42/ContactQuests/wiki/Tasks-%26-Rewards-Mechanics---%E4%BB%BB%E5%8A%A1%E4%B8%8E%E5%A5%96%E5%8A%B1%E6%9C%BA%E5%88%B6%E8%AF%A6%E8%A7%A3#english-guide) · [Configuration Guide](https://github.com/NightFrost42/ContactQuests/wiki/Configuration-Guide---%E9%85%8D%E7%BD%AE%E6%96%87%E4%BB%B6%E6%8C%87%E5%8D%97#english-config) · [NPC Interaction Config](https://github.com/NightFrost42/ContactQuests/wiki/NPC-Interaction-Config---NPC-%E4%BA%A4%E4%BA%92%E9%85%8D%E7%BD%AE#english-guide) · [Advanced Quest Logic](https://github.com/NightFrost42/ContactQuests/wiki/Advanced-Quest-Logic---%E8%BF%9B%E9%98%B6%E4%BB%BB%E5%8A%A1%E9%80%BB%E8%BE%91#english-guide) · [Commands & Permissions](https://github.com/NightFrost42/ContactQuests/wiki/Commands-%26-Permissions---%E5%91%BD%E4%BB%A4%E4%B8%8E%E6%9D%83%E9%99%90#english-guide) · [KubeJS Integration](https://github.com/NightFrost42/ContactQuests/wiki/KubeJS-Integration---KubeJS-%E8%81%94%E5%8A%A8#english-guide)
* **Downloads & releases**: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/contactquests) · [Modrinth](https://modrinth.com/mod/contactquests) · [GitHub Release](https://github.com/NightFrost42/ContactQuests/releases/tag/2.1.6-1.21.1)
* **Project**: [Source Code](https://github.com/NightFrost42/ContactQuests) · [Issue Tracker](https://github.com/NightFrost42/ContactQuests/issues)
