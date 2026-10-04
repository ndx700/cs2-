# CS2 Dust2 项目协作约定

本仓库是 Android Kotlin/GLES 的 CS2 Dust2 道具教学沙盘，用户称项目负责人为“项目经理”。

## 开工前

- 阅读 `docs/STATUS.md`、`docs/COLLABORATION.md`、`docs/requirements/C010.md`、最新 `docs/materials/C012-materials.md` 与开工安排 `docs/dispatch/C013-parallel-kickoff.md`。
- 实现基线 v0.6.0-test2：地图、自由观察跑图及加载优化已经交付；正式教学课程、人物教学展示、烟火和 HE 炸烟尚未证明完成。
- 不把需求说明、素材、截图或 CI 构建成功当成功能验收完成。
- 用户最新明确指令优先；C010 处理与 C008/C009 冲突的首版范围，C012 记录最新去重素材计划及待校准项，原文保留作历史。

## 分支与交付

- APP 侧使用 `app/*`，资源侧使用 `assets/*`，项目经理整合使用 `pm/*`，Codex 审查／修复使用 `codex/*`。
- 从最新 `main` 建任务分支，通过 PR 交付；不要在两个会话中同时写同一工作分支。
- 提交可独立核对的代码与资源变化；PR 注明基线提交、任务编号、验证结果和未完成项。
- 不强制推送，不覆盖其他侧的提交，不仅以“完成”文字替代交付证据。
- 修复审查发现可以提交任务分支；合并依照用户授权及项目经理整合安排执行。

## 构建与资源

1. `python3 tools/restore_assets.py`
2. `python3 tools/validate_assets.py`
3. `chmod +x gradlew`
4. `./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon`

需要 Python 3.10+、JDK 17、Android SDK 34、Build Tools 34.0.0 和 Gradle 依赖访问。无需外部地图下载；全部移动资源已包含在 `asset-packs/` 分卷中。

资源内容修改后执行 `python3 tools/package_assets.py --version <实际资源版本>`，提交分卷、清单和可审查的文字元数据。不要直接编辑分卷。恢复脚本发现本地资产修改时会停下，避免覆盖未打包变化。

`tools/debug.keystore` 仅为既有 Android 开发测试密钥，不作正式发布密钥。不要加入访问令牌、个人配置、`local.properties`、构建缓存或 APK 到普通代码提交。

## 必须保护的产品行为

- 保留地图整体、跑图控制、触控及加载提速；真实地面行走／碰撞仍需独立实现与验证。
- 正式课程必须让站位、参照物和第一人称准星瞄点看得清。
- 首版允许预设动作、轨迹和效果动画；烟的扩散成型消散、火的延迟蔓延、HE 炸烟的局部缺口及回填必须动态展示。
- 闪光致盲延期，保留站位、瞄点及轨迹资料。
- 时间参数来源须可追溯；无实机参考时标待测，不编造真实秒数。
- Android 性能不足可减少粒子／复杂度，但保留动态阶段和关键时序。
- 报告本轮实际运行的测试，区分历史桌面 EGL 结果、CI 结果和真实手机验证。

## Code Review Rules

Review lifecycle, GL-thread confinement, bounded loading memory, simultaneous touch controls, preset timeline pause/replay, map/asset version consistency, and evidence for smoke/fire/HE behavior. Do not accept static effect images as completion. Identify unmeasured performance claims and fabricated CS2 timings. Explain concrete triggers and effects; cite relevant files and functions.
