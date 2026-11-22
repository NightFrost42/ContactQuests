# ContactQuests 自动化回归测试

适用组合：Minecraft 1.21.1、NeoForge 21.1.267、FTB Quests 2101.1.39、FTB Library 2101.1.40、FTB Teams 2101.1.12、KotlinForForge 5.13.0、KubeJS 2101.7.2-build.382。具体依赖固定在 `gradle.properties`；FTB 旧版的 void claim 接口不在本次新构件支持范围。

## 一键测试

在 WSL 的项目目录执行：

```bash
bash gradlew --no-configuration-cache contactquestsAutoTest
```

这会先编译主模组和测试模组，导出当前依赖的启动参数，再依次运行服务端 GameTests、KubeJS、Windows 原生客户端的寄件／任务／编辑器场景、同存档新进程重进、三类独立自动填充，以及 Windows 独立服务端／客户端与服务端重启重连。最后使用独立世界实际移除 KubeJS／Rhino／JEI，再验证 Windows 服务端、客户端、寄件、五类领奖、物品格无查看器行为和重启重连。WSL 上客户端默认使用 Windows Java；可通过 `CQ_WINDOWS_JAVA=/mnt/c/…/bin/java.exe` 指定 Java 21。客户端需要本机图形会话。

集成世界在第二次进程领取持久化 withReward 暂存后，再以第三个独立 JVM 打开原存档，验证已领状态与空托管均落盘，重复领取不再次配送。当前完整 suite 共15阶段，含10个Windows客户端；该阶段不重建世界或重种物品。历史运行保留其原冻结阶段与清单。

无 KubeJS 的四个阶段另按本轮冻结的 `coverage-no-kubejs-manifest.json` 逐项检查。专服只读本进程的结果，客户端只读自身断言及同次连接的专服结果；其他环境或首进的同名 PASS 不能补重进的缺项。同阶段任意明确失败保留为失败，`run.json` 记录必须项数量、缺项和失败 ID。

每次创建新的 `run-test-<运行编号>/` 世界和 `test-results/<运行编号>/` 报告。不会清空旧世界、报告或正常的 `run/`。`test-results/latest.json` 指向最近一次；报告目录含 `report.md`、`report.json`、运行参数、游戏日志和逐项断言。

新导出的启动配置会将当前生产 jar 放进各测试世界的 `mods/`，移除主模组编译目录的加载入口，测试模组单独加载。每个 JVM 的报告核对 ContactQuests 实际类来源及 jar 校验和；重进时如果 jar 改变会拒绝继续。这样一键测试同时覆盖生产打包结果。

Windows 客户端把同版本 LWJGL 和 Windows 原生库放入 JVM 启动模块，避免 NeoForge 启动字体类在模组扫描期间从 UnionFS 读取。独立的测试启动 helper 仅开放 Minecraft 所需的 LWJGL 内存分配器访问；它不会进入生产 jar。每个客户端阶段都检查字体类、STB／FreeType 原生模块和访问设置的实际来源，启动字体错误、初始化崩溃及缺少完成报告会使验收失败。自动客户端关闭失去焦点时暂停，保证后台测试期间单人世界继续同步；该设置只应用于独立测试进程。

原 Windows 入口保持兼容，转发到同一 WSL runner：

```powershell
.\tests\runner\run-1.21.1.ps1 -Mode suite
```

## 定点调试

```bash
python3 tests/runner/run-tests.py server
python3 tests/runner/run-tests.py client --scenario client-smoke.json --client-platform windows
python3 tests/runner/run-tests.py client --scenario client-parcel-autofill.json --client-platform windows
python3 tests/runner/run-tests.py no-kubejs --client-platform windows
python3 tests/report/generate-report.py --latest --check
```

每次 runner 自动准备编译与启动参数；`--prepared` 只用于刚完成编译、导出且源代码没有继续修改的运行。指定 `--run-id` 时拒绝复用已有目录。`CQ_GRADLE`、`CQ_GRADLE_HOME`、`CQ_GRADLE_INIT`、`CQ_GRADLE_ARGS` 用于本机缓存、镜像或代理配置；不要把个人路径写入 CI。

真实 provider 验收显式设置 `CQ_TEST_LLM_ENDPOINT=http://127.0.0.1:3425/v1` 和 `CQ_TEST_LLM_MODEL=auto/deepseek-v4.1-flash`。完整 suite／multiplayer 在 Windows 独立服务器与客户端的一条会话中连续测试真实发布、回信阅读和服务端重启续聊；client 模式在集成服务器执行，独立 no-kubejs 模式则在无脚本服务器执行。每轮只运行一条真实会话，其余环境保留固定 mock 的实物寄件和重启矩阵。`run.json.realProviderPhases` 与冻结 coverage 清单记录真实调用的具体阶段，不将固定 mock 标成真实模型通过。

## 固定覆盖与判定

`scenarios/common/coverage.json` 是必须项清单。GameTest、Java 夹具和固定 KubeJS 脚本负责具体断言；报告不会从“调用了 API”、操作次数或整体加载成功推断业务通过。缺少证据为 NOT_RUN，断言失败为 FAIL。runner 检查预期 GameTest 全部出现；suite 任一必须项 FAIL／NOT_RUN 都返回非零。单独 server／client 模式只以所选阶段必须项判定，其他覆盖仍显示 NOT_RUN。

每轮开始冻结生产 Java/Kotlin 源码到 `source-manifest.json`。代码执行报告保留完整编译类身份的原始 `counters`，客户端／服务端经转换后的不同 class ID 单独保存 XML/HTML，不再互相覆盖。`sourceLineEvidence` 另按同一已冻结源码物理行合并执行证据：任一有效身份执行过该行即有执行证据；指令／分支不跨身份相加。源码不存在、哈希变化、行号越界等列为映射异常，不能据此声明全部代码通过。第三方 Mixin 宿主始终单列，不进入项目分母。26 项报告门槛测试和 40 项覆盖算法固定测试都在一键入口执行。

自动覆盖包括：

- 三类真实任务进度；延迟 1/5、分批提交、已有进度和在途预留数量。
- 真实邮筒发送普通／末影包裹和明信片、同封两任务同 tick 到期、在途删除任务六种边界。普通邮件等待200 tick，末影即时且无排队；同 tick 两任务仅发一次到达通知，删除任务不增加旧ID进度。服务端被动观察真实生产 tick，额外等待220 tick后检查无重复；精确核对数量、队列、误投次数和暂存，恢复隔离夹具原配置与背包。
- 同封任务／普通物品混装，任务先分配，再交脚本／NPC 策略；已完成任务 NPC 接收普通邮件。
- 多任务按本次可提高的提交百分比优先，整封跨槽评分与稳定 ID 平局规则。
- 五类奖励经真实 FTB claim 两次后的准确邮箱数量；空奖励／缺表失败。
- 锁队重试、寄出时队伍归属、离线脚本邮件保留、跨维度暂存与旧维度保存迁移。
- KubeJS NPC 配置、stash／触发次数保存恢复、工厂组件、实际寄件数量、五类文本替换、状态控制、三类任务／奖励字段及文本覆盖重载和客户端同步。
- Windows 11／Java 21 的真实邮筒、别名、三类邮件、FTB 编辑器对象数量与自动填充输入／错误物品保留。
- 真实 Contact 邮箱附件和队伍块绑定的 NBT 保存恢复，以及日志中的保存／网络编解码失败。
- NOW／SAVE／WITHREWARDS／DISCARD 的逐封寄错计数与保存，玩家／NPC 隔离、真实 getcount 消息和普通玩家／管理员命令权限。
- 中文／英文翻译表逐项参数格式、三类任务与五类奖励编辑字段、标签与互斥选项，以及真实工具提示宽度、长邮箱消息换行。
- Jade 邮箱仅显示 `队伍：队伍名`（英文 `Team: name`），普通场景使用短队名；超长名字只在边界测试中临时启用并恢复。检查实际 Jade 元素和字体宽度、完整文字及缩放后的屏幕边界，不以聊天换行代替 Jade 浮窗验收。
- 标签物品的实际右键解锁与可点击任务通知、队外邮箱的实际拒绝，以及单／多标签转换、取消和 Long 数量不截断。
- 原生 Quest 编辑器保存的 Tag／互斥规则直接串联：缺 Tag、解锁、互斥对象完成、原生重置；服务端开始状态与客户端实际绘制状态一致。
- 明信片任务与奖励按钮检查实际 FTB `getIcon()` 和 Contact 物品模型；新建对象不注入自定义图标，普通任务保持统一，末影奖励使用末影明信片模型。样式选择和阅读器继续显示 Contact 完整卡面。
- 标签解锁通过带有样式、文本、寄件人和解锁标签的实际 Contact 明信片右键执行，检查原生阅读器打开、任务通知、依赖／互斥不被绕过，以及服务端处理后物品数量和组件不变。
- 无 KubeJS／Rhino 的可选依赖组合：实际类缺席、Contact 混装邮件钻石×2／泥土×3、三类任务进度 [2,2,1]、NPC 回信一次、五类奖励数量和持久化的非个人队伍邮箱。
- 选择奖励的实际 FTB 按钮／选项、自然模板和隐藏选择绑定、Contact 原生明信片编辑及邮筒寄出；整篇改写正文后仍精确配送所选物品。标题和普通／高级 tooltip 不显示请求身份；无标记／伪造标记／换选旧卡／同 NPC 多申请／带原标记重放均有固定断言；未寄出与已接收卡片均跨 JVM 恢复。前面有只读来信、后面有空白卡时，实际 Write 使用主背包空白卡，旧信全部组件保留；空寄件人组件同样按原生只读规则拒绝绑定。旧系统地址待选申请加载后可重新选择并用自由正文完成领取。
- 命令通过真实 dispatcher 执行 givebinder、满库存掉落、open 无效 ID、setcount 数量边界、removedata 玩家与全局范围、tags 增删查及补全、冗余 NPC 清理与磁盘重载。KubeJS 补测真实 Rhino 公开创建重载、默认值、六种 Saved 编辑、同值／null／省略不变、Contact 工厂、独立扰动后恢复和新 JVM 精确对象 NBT。
- 三类 autofiller 从非编辑模式任务详情的实际按钮进入，再点击右侧提交按钮；补测主背包槽位的信封／红包／空白卡正常换手、恰好需求快移、红包预填只补缺量／错误物品不动／缺货，以及速度零中文换行、原生 Done 真卡保存、提前关窗超时和再次打开不恢复旧打字。
- 填写生命周期另外逐 tick 核对需求9、持有12的4／4／1拆分和余量归还；拿起／首批存入后同 tick 换另一份信纸，以及直接关闭不重开，都必须停止旧任务且双端物品守恒。共享收件人的3＋5需求、预填2应填入总8、余4；该项只验填写，不把它算作逐任务寄件进度验收。原生手动改字后停止自动输入，Done保存精确草稿。未限定物品种类的红包可打开填写页、填写祝福并原生Seal；空红包本身不贡献物品数量进度。
- Rhino图配置另验customId、同章节autofocus、已注册视觉预设及继承、有名称／样式／自定义数据的Map图标、Task／Chapter依赖和循环原子拒绝。描述数字／单字符串／混合数组必须完整拒绝；Saved空数组隐藏原文、Global空数组清空原生翻译并仅移除描述覆盖，分别保存并在新JVM读取。客户端通过真实章节按钮检查同步显示与自动聚焦。
- 明信片用玩家实际字体和 Contact 原生编辑器／阅读器的 Page.lines 核对每行坐标、宽度和底边。覆盖默认八份填充模板、选择奖励、收到的普通与真实模型来信，以及瞬间／逐字填写。超过原生字符限制、过多换行、窄卡面分别拒绝自动填入，原草稿及客户端／服务端实际卡片不变。长固定模型回信走真正邮筒／邮箱，逐张通过物品使用打开阅读器，精确拼接全文，并在新 JVM 再验原库存中卡片顺序、隐藏分片组件和全文。
- 包装纸使用真实四槽原生界面，覆盖从五颗钻石中精准填两颗、预填一颗后只补一颗、四槽满时保留目标物品，以及缺物时不生成内容；客户端和服务端逐槽、背包、光标与总量均有固定断言。
- 非隐藏选择奖励在普通玩家界面使用空 option_labels，检查实际原生物品名、数量、图标及选项 tooltip，再经真正的 Contact Done／邮筒寄卡领取所选苹果×3，无钻石且不能重复配送；原六类隐藏断言独立保留。
- 普通任务提交页实际 Back／ESC／背包键 E 与物品格 JEI 配方查看，检查返回原页面、完整库存／进度不变，未调度填充；暂停菜单 Mods→ContactQuests→Config→Cancel→返回游戏核对原生工厂入口与配置未保存。
- 同一真实配置入口进一步保存非默认值，再清空两输入及输入超长数字，核对默认1/10、开关与磁盘保存；Cancel保持磁盘字节不变，最后使用原生Done恢复。原生Quest required_tags和ParcelReward unlock_tags分别经列表Add、Tag选择、列表Done、对象Done；独立服务器快照核对，重开检查tooltip并实际删除测试行，再保存恢复。
- 重进执行原生 `/reload`，等待资源／命令替换、TagsUpdated与全局同步完成；检查被扰动的任务／奖励配置、原生章节／任务／孩子文字、世界文字覆盖、索引及NPC缓存恢复，五种Rhino替换器各注册一次，队伍／Tag／进度／在途不变且零剧情事件。Rhino非法结构和非字符串键以原生图、ID索引、SavedData与磁盘快照验证原子拒绝；合法整数浮点NBT仍可输入。
- Tag 创建行通过真实键盘输入与 Enter 验证空白不新增、有效名称去空白且只保存一次；真实局部 Cancel 保留 Tag／配置且不触发保存，重新创建草稿为空。Choice 请求页实际点击复制正文，精确核对剪贴板、卡片组件与领取状态不变；写卡按钮 tooltip 和非隐藏外层奖励 tooltip 检查 NPC、条目及寄卡提示。明信片样式列表保留真实绘制帧，检查卡面纹理、居中尺寸、原生宽高比及过期图像缓存刷新。
- 固定版 FTB 原配方助手为空实现时，由可选 JEI 插件使用其公共 OUTPUT 焦点显示配方；已有原生助手优先。当前 JEI 19.70.0.469 只用于编译／测试，不打进生产 jar 或声明为必需。无脚本独立环境同时从两套实际类路径移除 JEI，核对服务器／客户端代表类真缺席，并真实点击任务物品格，页面、库存、进度不变且无缺类错误。
- 客户端手持真实绑定卡潜行右键邮箱上／下半，独立服务端观察真实交互并核对 Contact owner、双端队伍标记及扣卡一次；已占用邮箱拒绝时卡片和原邮件不变，结束恢复原队伍邮箱、玩家与隔离方块。
- 六类邮件奖励隐藏时核对实际标题、包裹图标、tooltip／概率和物品查看入口；隐藏不改变五类原奖励配送。Random 和 All 的真实按钮跨帧绘制两种原生内容图标，Random 百分比直接来自原生 RewardTable。
- 创建接口空白／null 默认、完整 Rhino 章节／任务及孩子配置、非法 bundle 无半创建对象或孤立翻译；逐项对比固定版本 19 类任务和 19 类奖励的工厂默认 NBT，包含 NeoForge energy。内嵌箱／drops 留空使用原生构造默认，明确的 0／false 保留；同表、其他表／对象和创建批次的条目 ID 冲突均拒绝，自己的 ID 可幂等更新。默认目录存于 `server/creation-defaults.json`。
- mock NPC 的人设、模型／思考等级、两轮上下文、AI 明确决定原卡保留／退回／丢弃、Contact 明信片回复、授权图工具、真实新进程未完成工具续执行；重启续执行回信单独核对 NPC、原生样式、完整正文、已发布任务 ID 和数量一次，无脚本原五类奖励仍保持精确数量断言。get_quest 只能访问当前玩家／队伍会话发布的 key，非法 provider 配置替换保留原有效配置。响应缺少处置决定或 provider 暂时故障时，不回退旧反应、不伪造回复或处置，原信和上下文保持待处理并按间隔重试。固定 mock 和本机 Magpie endpoint/model 分别验收；远程部署环境中的真实 provider 不在自动 suite 内。
- `withReward` 必须等领取同 NPC 的任务奖励时返还：逐类真实 FTB claim 覆盖六类 Contact 奖励，断言原队伍／原玩家、其他 NPC 暂存不受影响、未领不返还、两次 claim 仅一次配送。表奖励委托非物品子奖励成功时也触发所属 NPC 的返还。固定 keep／return／withReward／discard 都经真实 Contact 邮筒寄入，并逐轮核对原卡与回信数量。
- keep 暂存只合并完整组件相同的栈；整封无法放入时不部分合并、不自动退回，保留已受理模型决定和原邮件等待。固定断言核对满仓前后原状态、保存恢复、释放空间后的单次完成与总数守恒。withReward非空暂存跨真实JVM后原玩家原生领取，第三次JVM再查持久化已领状态和不重放。
- 实物 Contact 卡与真实 Rhino Tag 授予、原生 FTB 完成／重置、真实 mutex 和脚本 blockQuest 的独立事件，字符串 Quest ID 与队伍／玩家身份、前后状态、读取与同步不重复触发。

服务端使用新建 FTB 对象和独立队伍。Windows 场景分别使用集成服务器及独立原生服务端／客户端网络。测试命令和结果接口全部位于 `src/testmod`，生产 jar 检查禁止包含测试模组。中文／英文检查在测试世界中切换资源语言，结束后恢复原选择。

无 KubeJS 场景由 Java 夹具准备，使用 `scenarios/common/coverage-no-kubejs.json` 保存独立验收清单。不能直接去掉 KubeJS 后让含脚本的测试准备逻辑继续运行；完整 suite 同时保留有脚本和无脚本两组环境，任一环境缺项或失败都会使一键测试失败。启动参数的 JVM／NeoForge 两套类路径都会移除 KubeJS、Rhino 和 JEI，并在运行中检查其代表类不存在；日志出现相关缺类错误也会被判为失败。

## 手动项目与边界

配置界面的保存、取消和输入范围已经加入自动化。下面的人工入口仍可用于额外视觉检查，不进入自动通过判定：

```bash
bash gradlew runContactquestsClickTest -PcontactquestsClickGameDir=run-click-1.21.1
```

自动测试不等于所有模组整合包、旧世界或远程服务器组合验收。实际执行结果与剩余风险见 `development/20261113/` 续开发记录和最新隔离报告；保留历史失败报告，不将局部通过表述为整套通过。

发布工作流配置与平台上传重试步骤见 `tests/release/README.md`。
