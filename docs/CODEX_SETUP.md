# 把本仓库接入 Codex

按 2026-10-04 查阅的 OpenAI 官方文档整理。

## 云端完整代码审查

1. 用同一 ChatGPT 账号打开网页或桌面应用，在工作位置选择 Cloud／云端，创建环境。
2. 选择 `ndx700/cs2-`（改名后选新名）；提示连接 GitHub 时授权此私有仓库。
3. 让 Codex 检查并准备工程，配置 Python 3.10+、JDK 17、Android SDK 34 / Build Tools 34.0.0，以及 Gradle 依赖所需访问。
4. 初始化运行 `python3 tools/restore_assets.py` 与 `python3 tools/validate_assets.py`，再按 AGENTS.md 检查构建与测试。查看准备和测试结果后发布环境。
5. 环境发布后新建任务：阅读 AGENTS.md、STATUS 和 C010，审查现有实现；报告具体文件、触发条件、影响和测试证据，修复放入独立分支／PR。

手机可在 Codex 中选择已有环境；官方当前指引要求新环境在网页或桌面端创建。接入仓库不会自动把三个旧会话的完整对话历史搬进 Codex，仓库中的需求和基线说明用于传递可核对的工作上下文。

## 每个 PR 再审一遍

连接仓库后，在 Codex 的 code review 设置里选择本仓库，开启 Review code 下的 Automatic review。也可在需要审查的 PR 评论中使用 `@codex review`；配置正确后 Codex会在该 PR 留下审查。

PR 审查针对改动差异。首次全面检查已有 main 代码，请单独启动云端审查任务，不能把一次 PR 审查当成所有基线文件都已检查。

## 本项目检查重点

加载线程／内存边界、GL 线程、相机与多点触控、Android 生命周期、地图和资源版本、课程时间轴暂停／重播、动态烟、火的晚着火和 HE 炸烟回填。区分历史验证记录、实际本轮检查和手机实测。

官方来源：

- [Codex Cloud](https://learn.chatgpt.com/docs/cloud)
- [GitHub PR review](https://learn.chatgpt.com/docs/third-party/github)

本次仅提供连接步骤与工程准备文件，尚未替用户创建或发布 Codex 环境，也未开启自动 review。

## C013 已安排的具体职责

接入后执行 [C013 Codex 审查任务](dispatch/C013-codex-review.md)：先复查最新 APP PR #5 与资源 PR #4，核对 C013 瞄点精度补充要求，把具体问题反馈到各自 PR；使用独立 codex/* 分支提交审查记录／获分配的修复。Ultra 不在本轮实现范围。

角色与审查规则已写入仓库。连接／发布环境、GitHub 授权和自动审查设置仍需各自实际验证；本次文档提交不表示这些设置已经完成。
