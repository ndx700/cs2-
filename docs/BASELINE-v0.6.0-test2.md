# CS2 沙二 · v0.6.0-test2

项目经理第二版手机测试包，在 v0.5.0-test1 的完整地图上加入自由跑图并优化可见区域加载。支持俯瞰与自由视角切换；保留单指旋转、双指缩放/拖动、全图复位以及 A/B/匪家/警家跳转。道具教学仍为空。

资源基线：R003 主模型、R004 材质、R006 纹理审计、R007 精确对应表。APP 基线：A005 / v0.4.0。双方原始交接保持独立，此工程为整合分支。

## 测试范围

- 3661 个移动网格块，4,610,873 个源三角形全部保存在移动资源内。
- 273 个主地图材质，恢复 2184 个原始向量参数；291 张颜色/混合移动图，最高 512 像素。
- Source `_TEXCOORD_4` 混合流与顶点颜色分别保存，不能混用顶点颜色 alpha 代替层混合。
- CPU 后台按可见块读取，线程数为 clamp(可用核心数−2, 2, 6)，最多 2×线程数个在途任务；解码与待上传数据合计预留 24 MiB。大型可见结构先加载，再预读镜头附近 18 米内的块；GPU 上传按静止 10 ms、移动 4 ms 时间预算分批执行，一次调用可能超过预算。网格上限 192 MiB，纹理上限 96 MiB。预算不是手机实测峰值。
- 分别处理不透明、镂空、透明、双面和 overlay。
- 4 种环境特效材质（47 块、852 三角形）保留文件但暂不绘制，避免尚未接入的烟尘/光束 Shader 显示成白色实体面。它们不是手雷烟模拟。

## 已知差异

采用预览光照，尚未复现原版烘焙光照、反射、法线/粗糙度、完整边界混合与复杂 Shader。多套 UV 和切线保留于原始 R003 GLB，当前移动 Shader 只消费普通 UV、颜色与层混合流；不得宣称与原版材质等价。足球模型尚未认证摆放，本版未加入。已实现自由第一人称视角；玩家碰撞、地面吸附与投掷物理尚未实现，可穿过墙体和地面。

## 构建

标准 Android 工程入口：`./gradlew assembleDebug`。需要 JDK 17、Android SDK 34、Build Tools 34.0.0、Gradle 8.7 和可解析的 Gradle 依赖。本轮没有运行 Gradle/Lint，实际 APK 使用官方 Kotlin 1.9.24 与 Build Tools 直接构建，完整脚本如下：

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
export CS2_KOTLIN_LIB_DIR=/path/to/kotlin-1.9.24-jars
python3 tools/build_apk_offline.py
```

Kotlin 依赖目录须含 compiler-embeddable、stdlib、script-runtime、daemon-embeddable 1.9.24、reflect 1.6.10、trove4j 1.0.20200330、annotations 13.0、JUnit 4.13.2、hamcrest-core 1.3。使用官方发布依赖。脚本编译本工程源文件与生成 R.java，运行 19 项测试（10 项基线 + 9 项相机/加载），生成 DEX，打包、对齐、签名并逐字节核对全部 assets；不复用预编译 APP。

源码压缩包已经包含转换后的资源，可直接重新构建。重新转换还需要共享的 R003 GLB、R007 bridge、R004 KV3 和按 bridge 散列解码的 R006 PNG，使用 `tools/convert_dust2_mobile.py --help` 查看入口。原始素材不要覆盖。

`tools/debug.keystore` 是既有项目开发测试密钥，仅供开发测试。调试包 `com.ali.cs2utility.dust2`，versionCode 6，minSdk 24，纯 Kotlin/Java/GLES，无打包本机 native ABI。

## 验证与手机测试

见 `docs/apk-build.json`、`docs/mobile-conversion.json`、`docs/egl-verification.json`、`docs/landmark-verification.json` 与 `docs/preview/`。EGL 截图来自桌面软件渲染，不是 Android 手机截图，也不能替代启动、触控、内存或帧率测试。

安装后检查俯瞰手势，再点“自由跑图”：左侧摇杆前后左右移动，空白处拖动转头，右侧上升/下降，右上速度切换 3/6/12 m/s。首次从全图进入位于匪家；跑图中“跳到区域”将视角直接移至区域锚点上方 1.65 米。摇杆、转头、升降可同时按住；松手、触控取消、失去窗口焦点或进入后台会停止移动。“全图”退出跑图并复位。横竖屏重建保存模式、位置、朝向、速度，不保存按键状态。

加载提速还包含 64 KiB gzip 读取缓冲、复用顶点检查偏移数组、缓存材质贴图路径、减少状态文字刷新，gz/PNG 在 APK 内直接存储。已完成并发和缓存策略修改，尚无手机提速倍数/耗时实测；顶部区域就绪耗时是当前一段连续缺资源等待过程，移动时可见区域会变化，不能视作全图加载耗时。

请在同一手机和相同区域对比旧版，记录首次区域耗时、转身补图等待、持续跑图帧率/发热、横竖屏及后台返回。优先测试 B 点附近下降进入通道，若迷失方向可全图复位。

原地图 Valve · Powered by Source 2 Viewer (https://s2v.app)。材质层混合语义参照 Source 2 Viewer 的 `complex.vert.slang` 与 `complex.frag.slang`，源参数保留以便继续完善。

`docs/` 中 A004/A005 及旧验证文档是基线历史记录；本轮版本以本 README 与新的 JSON 验证记录为准。
