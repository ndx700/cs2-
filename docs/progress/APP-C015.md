# C015 APP 实际进度

[NADE-ATLAS][C015][APP][IN_PROGRESS] — 2026-10-05

- 已读最新main `b329ef098bfff85f0cf593b8d0f8bd98b0973335`的AGENTS/C015/STATUS/COLLABORATION/C010/C012及旧开工安排，继续APP工程师任务；不恢复已撤销Codex审查。
- 仓库ID 1403742663已改名`ndx700/nade-atlas`；实际读写与新分支成功。从该main创建`app/c015-coordinate-capture`，接单提交`88c267b9100a62a8e6643907556831a92eb775cc`。本轮继承APP #10 `346c20fc5d36654b9ce7ca6bbfe956000780d428`，保留地图修复及PM新安排，不合并资源PR旧head。
- 已读资源检查点`3d1a74fed7a96597313ed1073d5684927f7bfe19`用户视频/五镜头回执/三课待测包；五镜头接口回执已确认，采集证据新格式尚待资源侧核对。

## 本轮实际产物

1. 开发隐藏入口：调试版长按地图名称，选课号后点选foot/aim/landing显示表面，或记录当前真实camera eye；支持输入相机候选和系统文件保存ZIP。普通发布版无入口，开发字段不进入教学流程。
2. 射线拾取：屏幕像素按本帧实际VP反投影；包围球排序/剪枝，最近双面显示三角形；逐块查询复用现有地图工作线程与24MiB预算，无全图CPU缓存。记录part/material/triangle、重心坐标、朝向射线的法线和距离。向下/朝上仅标支撑面候选，未实现人物净空或物理脚底验证。
3. 导出：实际manifest SHA、相机yaw/pitch/distance及轨道目标/真实眼位区别、实际垂直FOV/视口，主地图同帧PNG和逐文件SHA。未命中或未测值不默认补齐。状态固定DEVELOPMENT/importable=false。区域未就绪拒绝采集；截图最多400万像素，缺图如实记录。私有文件保留，导出由Android保存位置选择；实际手机/SAF/旋转流程仍未测。
4. 完整地图离屏工具：`check_mobile_scene.py --capture-camera ... --capture-output ...`复用原材质与3661分块/291纹理，生成候选图及最近显示三角形。`C015-map-probe/`有通用匪家图、箱体瞄点/地面候选及FOV/相机/查询数据，**不对应用户新挂门烟站位**。
5. APP真实Kotlin查询与离线全图查询交叉核对：箱体`p01579`三角形1093、距离2.9971657；地面`p00538`三角形166、距离7.1523。part/triangle一致、距离差<0.001。不是CS2坐标真值。
6. 三课实际CourseJson解析回执在`docs/validation/APP-C015-receipt.json`，绑定输入提交/文件SHA。均因importable=false正确拒绝，逐字段列出null。未把旧中门烟坐标或DEV实验数据补入草稿。
7. 原DEV入口改为烟/火/HE“渲染试验”，明确不等于新挂门烟/蓝车火/保持蹲姿跳投课程。候选versionCode=9、versionName=0.6.3-c015-dev；应用ID/签名配置保留，无新APK交付。

## 本轮实际验证

- restore_assets：3960文件、132056609字节；validate_assets：3661分块、4610873三角形、291纹理的SHA/长度/索引/边界通过；正式lineup仍0。
- 官方Android34/aapt2实际资源生成与链接、全部主Kotlin及测试源码编译通过；JUnit 85/85（新增10个拾取/证据格式测试），Python采集数学6/6。
- 完整地图Mesa EGL：原着色器编译/链接，全分块/纹理哈希与绘制无GL错误；通用图非背景像素覆盖85.76%。已查看图。与此前空场景效果检查区分，仍不是Android或选定视频对点证据。
- 本地Gradle testDebugUnitTest/assembleDebug/lintDebug实际尝试，wrapper下载阶段JVM Network is unreachable，未记通过。新PR CI待运行；上轮 #10 CI成功只作历史结果。
- 无连接手机，未测导出UI、GL读回、横竖屏/后台、效果录像、帧耗时/内存或覆盖安装。

## 第一项尚未全部达成与下一项产物

| 项目 | 具体缺项/处理 |
| --- | --- |
| 挂门烟同站位第三/第一人称对照 | 资源检查点foot/eye/aim及五相机均null；旧命令被新视频替换。本轮已查看墙缝/污点参考，但未做有误差记录的3D拟合。当前只交通用全图采集探针，不冒充该课对照。下一项：资源空间候选/支撑面/参照物索引到达后，APP用本工具生成同站位图并回执；可在DEVELOPMENT内部验证 |
| 三种投法人物动作 | 挂门烟蹲对齐→站起跳投、蓝车火桶上W跳投、HE保持蹲姿跳投已读；当前正式动作未接入。需支撑面与移动/姿态/释放关键帧，不能共用旧实验动作报完成 |
| 轨迹/落点/效果、避墙镜头 | 当前草稿path、事件、镜头/时序均null；连续消散/熄灭/回填及同烟配套未证实。镜头自动避墙/实体碰撞未实现；先逐课小包，不等待三课一起交付 |
| 手机测试包与验收 | 框架候选经PR交PM，内部联调和手机验证待完成，不因CI或通用全图图晋升CALIBRATED/VERIFIED |

采集能力已形成可审查候选；C015整体仍IN_PROGRESS，首项的选定挂门烟对照尚未完成。接口与使用方式见`docs/contracts/C015-development-capture.md`。APP、资源、PM文件各自维护，本轮没有修改资源校准记录/分卷或PM进度。
