# PM-C013-MAPFIX：第二版地图加载修复与 Codex 停用

日期：2026-10-04（北京时间）。负责人：项目经理。手机验收：待用户复测。

## 触发与修复

用户测试 CS2_C013_Second_Test.apk 后报告地图资源加载失败、地图为空、所有依赖地图的功能无法测试。截图路径为 maps/dust2/mobile/chunks/p03036.d2m.gz。

已检查交付包：3661 个 manifest 分块路径均带 .gz；最终 APK 中全部是去掉 .gz 的原始 D2M1 文件。p03036 长度 46240，头部为 D2M1 / 1140 顶点 / 2592 索引 / stride 36。原加载器既找不到旧路径，也无条件使用 GZIPInputStream。

新增 SceneChunkInput：先读原路径，仅在 FileNotFoundException 且原路径以 .gz 结尾时尝试去掉 .gz 的路径；根据输入魔数选择 gzip 解压或直接读取。保留原来的头部、计数、精确长度、有限坐标和索引范围检查；读取失败正确关闭输入。仅删除后缀不足以修复，gz noCompress 也未避免本次最终 APK 的展开结果。

新增 6 项 JVM 回归覆盖原 gzip、打包后原始数据、按魔数识别格式、非缺失 IO 错误、无 .gz 路径、损坏 gzip 关闭流。新增 tools/validate_packaged_scene.py，并将最终 APK 校验加入现有 Android CI 构建后、产物上传前。

## 已执行验证与测试包

- 已交付第二版源码基线：APP PR #5 head 516f2ce41ccd55a7aac441a2ee52d80d550e71a1；原 CI run 37168412786。35 个源码／资源文件已按 Git blob SHA 核对一致后再修复。
- 全部主 Kotlin 编译通过；41 既有＋6 新增 JVM 测试通过，共 47 项。
- 官方 Android SDK 34 aapt2、Kotlin 1.9.24、D8、zipalign、apksigner 完成修复原型包构建、对齐和签名核对。本地未运行完整 Gradle/Lint，也未连接手机；该包不冒充 GitHub CI 产物。
- 最终修复 APK 的 3661 个地图分块经实际 Kotlin SceneChunkInput 读取通过；Python 校验全部几何头部、长度、有限坐标、索引及 291 张贴图 SHA-256 通过。
- 3960 个资产与原第二版逐文件字节相同；APK ZIP 完整性通过。manifest 与地图版本保持原第二版身份。
- 包名 com.ali.cs2utility.dust2；versionCode 7；versionName 0.6.1-test2-mapfix。签名证书 SHA-256 c50bc89ee4cd661083508a64ca9cf9fea02503332005eb9238106e7c66bfb817，与旧包相同，满足覆盖安装身份条件。
- 修复包 CS2_C013_Second_Test_MapFix.apk，110498570 字节；SHA-256 9636ce54e4360692bb16efdf4b106fe000b191d36dc2995639e50620e6dac4ea。

本 PR 从 main 提交可独立合入的加载器修复；交付原型包基于 APP PR #5 加同一兼容修复，保留第二版课程演示。它不代表 APP PR #5 已合并 main。APP 工程师将此修复合入其下一包时，应保留 PR #5 的 manifestSha256 校验；本修复不改该字段。

## 剩余与协作边界

用户复测首屏地图、自由跑图、烟＋HE／延迟火样例。尚未证明手机渲染、GL 上传或所有教学功能正常；真实课程仍待校准，人物仍为占位。

用户先前已要求停止并完全撤下 Codex。此次同时撤下其角色、接入步骤、任务入口、验收职责及 codex/* 工作流触发，旧任务文件改为 STOPPED 通知。两项项目经理定时检查已处于暂停。未宣称已取消外部 Codex 页面中的独立运行任务或修改账户授权；工程师与项目经理仍按三边协作执行。
