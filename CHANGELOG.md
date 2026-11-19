# Changelog

All notable changes to **ContactQuests** are documented in this file.

## [2.1.6-1.21.1] - 2026-11-19

### 🇨🇳 中文更新日志

#### ✨ 新增功能
- **明信片选择奖励（`contactquests:parcel_choice`）**：新增第六类邮件奖励。玩家点击奖励后可在选择界面挑选关联奖励表中的条目，并进入回信引导界面查看预设信件内容（支持一键复制或一键填充到背包内空白明信片）。通过邮筒向对应 NPC 寄回该明信片后，即可收到所选奖励。
  - 支持在奖励编辑中设置模板分组（`template_group`）与自定义选项名称（`option_labels`），并默认排除在“一键全部领取”之外。
  - 服务端配置文件 `config/contactquests/choice_templates.json` 支持按玩家客户端语言自动选用英文（`en_us`）或默认中文信件模板，支持 `{recipient}`、`{player}`、`{quest}`、`{task_goal}`、`{reward}` 占位符，并随 `/reload` 热重载。
- **盲盒 / 神秘包裹隐藏内容（`hide_contents`）**：所有邮件奖励均支持勾选 `hide_contents`（隐藏奖励内容）。开启后，玩家在任务界面将看到密封包裹图标、“神秘包裹”标题与提示文案，领取并寄达后才揭晓具体内容（处于任务编辑权限下仍可直接预览完整奖励）。
- **可选 AI NPC 书信往来与动态发任务（`config/contactquests/npc_llm.json`）**：新增纯服务端 OpenAI 兼容大模型 NPC 交互配置（默认关闭，需管理员显式配置并启用）。
  - **密钥与传输安全**：`providers` 中仅填写服务端环境变量名 `apiKeyEnv`，凭证永不下发至客户端；支持 `mock`（测试）与 `openai_compatible`（HTTPS 远端或本机回环 HTTP）传输。
  - **沉浸式书信体验**：支持按玩家（`player`）或按 FTB 队伍（`team`）隔离对话记忆（`historyTurns`），超长回信按 Contact 明信片渲染区域与 1024 字符上限自动分卡连寄，支持来信附件保留（`keep`）、退回（`return`）、随下次领奖一并寄回（`withReward`）与丢弃（`discard`）四种处置策略。
  - **结构化任务工具与防剧透**：可通过工具白名单开放 `get_quest` 与 `publish_quest`（结合 `chapterId`、`questPrefix`、`maxPublishedQuests`），由 NPC 在当前对话上下文中发布带专属解锁标签的新任务；未解锁对应任务且无历史对话且 `autoFill: false` 时，邮筒地址补全不会提前剧透 NPC 名称；AI NPC 同样严格遵守 `deliveryTime` 邮寄延迟规则。
- **KubeJS 持久化任务图谱构建 API 与状态变更事件（可选联动）**：
  - `ContactQuests` 全局对象新增持久化章节、任务、子任务与奖励增删改查及原子发布接口：`createChapter`、`createQuest`、`publishQuest`、`getChapter`/`Quest`/`Task`/`Reward`、`listChapters`/`Quests`/`Tasks`/`Rewards`、`updateChapter`/`Quest`/`Task`/`Reward`、`moveQuest`、`deleteChapter`/`Quest`/`Task`/`Reward`，支持内联奖励表 `table_data` 跨存档重载保留与完整任务属性/依赖折线控制点设置。
  - `ContactEvents` 新增 `tagUnlocked`、`mutexChanged`、`scriptBlockChanged` 三个服务端状态事件（`QuestStateEventJS`），安全暴露 16 位十六进制对象 ID、`teamId`、`playerId`、`before`/`after` 状态、`cause` 与递增 `sequence`。

#### 🔧 优化与调整
- **同收件人多任务公平分配**：向同一 NPC 寄出可同时匹配多个进行中任务的物品时，按单次提交推进完成度百分比最大者优先分配（比值相同时按任务 ID 排序），剩余未消耗物品再交由 NPC 后续处理策略或 AI NPC。
- **NPC 错信配置与多堆叠奖品改进**：
  - `npc_config.json` 的 `styleType: "Same"` 现会优先沿用玩家来信明信片样式，来信无样式时再回退规则 `style`。
  - 包裹与红包奖励（包含团队人数倍率计算后）超过单组堆叠上限（64）时自动拆分并分包投递；随机奖励包裹支持单抽项 `only_one`（玩家背包已有相同物品时不再重复发放）。
  - 队伍任务缓存保留动态 `<player_name>`，改由实际交付或领奖玩家实时解析，防止多人同队互盖名字。
- **邮箱团队归属、UI 与兼容体验提升**：
  - 单人队伍玩家使用团队邮箱绑定器也会持久化个人队伍 UUID；邮箱顶部 3D 队伍悬浮标记仅对同队成员可见；已绑定坐标在邮箱被破坏、爆炸、脚本移除或区块卸载期间队伍解散时自动清理绑定。
  - 新增 Jade 邮箱队伍提示自动换行与未知队伍兼容；新增可选 JEI 任务提交页面配方查看桥接；修复窗口短暂占用系统剪贴板导致复制全文失败的问题，加入退避重试。
  - `/reload` 同步重载自选信件模板、AI NPC 配置、占位符注册与任务索引；保持无 KubeJS 与无 JEI 环境下的零依赖独立运行。

#### 🐛 问题修复
- 修复 **ContactQuests Tag 设置** 中 `mode = ANY` 判定反转导致无法正常按“任一标签”解锁的问题，并使 FTB Quests **灵活进度（`FLEXIBLE`）** 章节严格遵守标签锁、互斥锁与脚本锁定规则。
- 修复 `/contactquests admin getcount <npc> [player]` 因错误读取不存在的参数导致无法查询触发次数的问题。
- 修复玩家与队伍同名引起的可重复刷取/翻倍邮箱物品漏洞及历史双归属别名残留，并补充 `CollectionSavedData` 与 `RewardDeliverySavedData` 从早期按维度分存到全局主世界的惰性合并迁移。
- 修复 `npc_config.json` 中 `WithReward` 在暂存/返还阶段的触发次数与回复时机（改错信时静默托管、领奖时连同回信一并寄回并直接按当前计数匹配阈值）、修复 `Discard` 漏传当次物品导致回信占位符读取为空，并持久化记录来信样式供 `"Same"` 样式继承。
- 修复明信片样式需精准匹配规范化样式 ID、红包任务禁止空壳红包误推进度并修剪祝福语首尾空白，以及纯客户端 UI / Mixin 类在专用服务端错误加载的问题。

#### 📦 前置与兼容环境
- **Minecraft**：`1.21.1` · **NeoForge**：`[21.1.266, 21.2)`（基于 `21.1.267` 构建）
- **必需依赖**：Contact `[1.2.3,)` · FTB Quests `[2101.1.38, 2101.2)` · FTB Library `[2101.1.39, 2101.2)` · FTB Teams `2101.1.12+` · Kotlin for Forge `[5.12.0, 6)` · OELib · Architectury API
- **可选联动**：KubeJS · Jade `[15,)` · JEI

---

### 🇺🇸 English Changelog

#### ✨ New Features
- **Postcard Choice Reward (`contactquests:parcel_choice`)**: Added the sixth mail reward type. Clicking the reward opens a selection screen for the linked Reward Table, then displays a postcard preparation screen where players can copy the letter text or auto-fill a writable Contact postcard. Sending that postcard to the assigned NPC delivers the chosen reward.
  - Supports custom `template_group`, `option_labels`, and is excluded from "Claim All" by default.
  - Server-side `config/contactquests/choice_templates.json` automatically selects client-locale groups (`en_us` or `default`) with `{recipient}`, `{player}`, `{quest}`, `{task_goal}`, and `{reward}` placeholders, and hot-reloads on `/reload`.
- **Sealed / Mystery Rewards (`hide_contents`)**: All mail reward types can enable `hide_contents` to hide the reward icon, title (`Sealed parcel`), and tooltips behind a mystery parcel appearance until claimed (while quest editors still see the full preview).
- **Optional AI NPC Correspondence & Dynamic Quests (`config/contactquests/npc_llm.json`)**: Added a server-only OpenAI-compatible LLM integration for NPC mail (disabled by default).
  - **Security & Transport**: Stores only the server environment variable name (`apiKeyEnv`)—credentials are never sent to clients. Supports `mock` and `openai_compatible` (HTTPS or loopback HTTP) providers.
  - **Immersive Mail Flow**: Supports per-`player` or per-`team` conversation history (`historyTurns`), automatic multi-postcard splitting within Contact's rendering bounds and 1024-character limit, configurable item dispositions (`keep`, `return`, `withReward` escrow returned on next reward claim from that NPC, `discard`), delivery delay compliance, and anti-spoiler address autocomplete.
  - **Structured Tools**: Optionally grant `get_quest` and `publish_quest` per NPC/provider (`chapterId`, `questPrefix`, `maxPublishedQuests`) so an NPC can publish new tag-gated quests within the active conversation.
- **Persistent KubeJS Quest Graph CRUD & State Events (Optional)**:
  - Added server-persistent chapter, quest, task, reward, and atomic quest publishing APIs on `ContactQuests` (`createChapter`, `createQuest`, `publishQuest`, `get*`, `list*`, `update*`, `moveQuest`, `delete*`), preserving inline `table_data` and full dependency/display/rule options.
  - Added `ContactEvents.tagUnlocked`, `ContactEvents.mutexChanged`, and `ContactEvents.scriptBlockChanged` (`QuestStateEventJS` exposing full 64-bit hex IDs, `teamId`, `playerId`, `before`/`after` states, `cause`, and `sequence`).

#### 🔧 Improvements
- **Fair Multi-Task Mail Allocation**: Incoming mail matching multiple active tasks for the same NPC prioritizes the task with the largest single-submission completion percentage gain (breaking ties deterministically by task ID) and routes leftover items cleanly to NPC handling.
- **Reply Style Inheritance & Reward Delivery**: `npc_config.json` `styleType: "Same"` now inherits the incoming postcard's style before falling back to the rule's `style`. Multiplied item rewards exceeding max stack size (`64`) split cleanly across stacked parcels; `ParcelRandomReward` respects `only_one` item checks; dynamic `<player_name>` placeholders stay deferred per interacting player.
- **Team Mailbox Privacy, UI & Compatibility**: Single-player teams preserve personal team markers on bound mailboxes; 3D team indicators above mailboxes render only for teammates; loaded mailbox destruction/explosions/block changes and off-chunk team disbanding clean up stale bindings cleanly. Added wrapped Jade tooltips, optional JEI recipe lookup fallback, clipboard contention retries on copy, `/reload` support for choice templates & AI NPC config, and full decoupling when KubeJS or JEI is absent.

#### 🐛 Bug Fixes
- Fixed inverted `DependencyMode.ANY` evaluation in `ContactQuests Tag Settings` and enforced explicit tag locks, mutex locks, and script blocks inside `FLEXIBLE` progression chapters.
- Fixed `/contactquests admin getcount <npc> [player]` failing to read target counts.
- Fixed a mailbox item duplication bug when a player and team shared the same name, cleaned legacy dual-owner aliases, and added lazy cross-dimension migration for `CollectionSavedData` and `RewardDeliverySavedData` to overworld storage.
- Fixed `WithReward` trigger-count evaluation and reply timing (now escrows mismatched items silently on arrival, evaluates the actual trigger count on reward claim, and mails back escrowed items with a reply postcard, including when non-item rewards execute) and fixed `Discard` omitting current-batch items from reply placeholders.
- Enforced normalized exact style ID matching on Postcard tasks, prevented empty Red Packets from advancing progress, trimmed Red Packet blessing comparisons, and isolated client-only UI/Mixin classes from dedicated servers.

#### 📦 Dependencies & Compatibility
- **Minecraft**: `1.21.1` · **NeoForge**: `[21.1.266, 21.2)` (built on `21.1.267`)
- **Required**: Contact `[1.2.3,)` · FTB Quests `[2101.1.38, 2101.2)` · FTB Library `[2101.1.39, 2101.2)` · FTB Teams `2101.1.12+` · Kotlin for Forge `[5.12.0, 6)` · OELib · Architectury API
- **Optional**: KubeJS · Jade `[15,)` · JEI
