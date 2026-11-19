# 新版发布后的自动分发

`.github/workflows/publish-release.yml` 在 GitHub Release 正式发布后触发。它从发布标签对应的代码构建生产 jar，核对版本与发布说明，运行服务端 GameTest／KubeJS 回归，再将同一构件分别上传到 Modrinth 和 CurseForge。两个平台是独立作业，一个失败不会取消另一个。

本工作流适用于 Minecraft 1.21.1／NeoForge 分支。目前开发版本 `2.1.6-dev-1.21.1` 被发布校验拒绝；发布前需要设置正式版本或 `alpha`／`beta`／`rc` 版本，并让标签与 `gradle.properties` 中的版本完全对应。标签可以使用 `v` 前缀。预发布版本需要在 GitHub Release 上标记为 prerelease。

在仓库的 Settings → Secrets and variables → Actions 配置：

| 类型 | 名称 | 用途 |
|---|---|---|
| Secret | `MODRINTH_TOKEN` | 对 ContactQuests 项目有上传权限的 Modrinth 令牌 |
| Secret | `CURSEFORGE_TOKEN` | CurseForge API 上传令牌 |
| Variable | `CURSEFORGE_PROJECT_ID` | ContactQuests 在 CurseForge 的数字项目 ID |

Modrinth 项目固定为 `Kv1T6Imo`，已核对其名称、源码仓库和现有加载器。当前分发元数据列出了 Contact、Kotlin for Forge、OELib 和 Architectury 的 Modrinth 项目；FTB 三项依赖列在 CurseForge 分发元数据及 jar 的依赖声明中。平台依赖链接不能替代安装说明里的精确 FTB 兼容组合。

发布前在本机运行 `contactquestsAutoTest`，完成 Windows 客户端、三类自动填充、编辑器、存档重进和独立服务端重连验收。GitHub 的 Ubuntu 发布作业运行服务端回归，无法替代这些本机图形测试。

上传失败后，可在 Actions 中手动运行该工作流，填写已有的 GitHub Release 标签，并选择 `modrinth` 或 `curseforge` 重试失败的平台。重试前先核对平台是否已收到该版本，以免重复上传。`both` 会对两个平台都执行上传。

工作流没有创建 GitHub Release，也不会合并 PR。校验和及发布说明作为 `release-dist` 构件保存；服务端报告单独保存为 `release-verification`。令牌只来自仓库 Secret，不写入源码或报告。

本机可运行 `python3 tests/release/test-release.py` 检查正式／预发布版本、错误标签、空说明、草稿、jar 版本不符、测试类误打包等正负夹具。本次仅验证配置和构件，尚未执行真实平台上传，也未读取或设置仓库 Secret。
