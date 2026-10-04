# GitHub 自动协作检查

配置统一放在 `.github/workflows/android.yml`，不添加第二个重复 APK 构建工作流。

| 触发 | 协作快照 | Android 测试、Lint、APK |
| --- | --- | --- |
| main 提交 | 执行 | 执行 |
| app/**、assets/**、pm/** 提交 | 执行 | 建 PR 后执行 |
| 目标为 main 的 PR | 执行 | 执行 |
| Actions 手动运行 | 执行 | 执行 |
| 每小时 07、22、37、52 分钟 | 执行 | 跳过 |

定时检查通过只读 GitHub API 查询分支头、打开的 PR 和近期 CI，不下载完整地图、不重复构建未改动的 APK。快照包含查询时间与 SHA；查询过程中的新提交由下一次查询反映。近期 CI 只列最近运行，不能当作所有分支的完整检查历史。

在 Actions 的 `Project sync and Android checks` 运行摘要中查看结果；下载 `project-sync-*` artifact 可读取 `project-sync.json` 和 Markdown。快照保留三天，APK 保留十四天，Android 报告保留七天。自动 APK artifact 是构建产物，正式发布仍由项目经理核验后决定。

每个 AI 下次开工先读 AGENTS、STATUS、最新需求、自己的任务 PR 和最近快照。GitHub Actions 不会自动唤醒三个独立 ChatGPT 会话，不会使它们互相通话，也不会执行 Codex 智能审查。Codex 已退出项目，旧接入和启用说明作废；不再派发 Codex 审查任务。

四次/小时是计划时间，GitHub 高负载时可能延迟或丢弃。schedule 只在默认分支执行；配置须先合入 main 才能生效。定时检查的并发组独立，不会取消正在执行的 PR 或 main 构建。

Android 检查保留 JDK 17、SDK 34、地图分卷还原与校验、单元测试、Lint 和 APK 构建；失败也保留已产生的检查报告。所有工作流权限只读，不自动合并、更新产品验收或写回源码。运行结果不得冒充手机实测。

本配置依据用户共享对话的通用 YAML 适配现有项目。仅配置已提交不能说明定时任务已经实际执行。

官方参考：
- https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule
- https://github.com/actions/github-script
