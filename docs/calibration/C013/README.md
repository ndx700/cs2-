# C013-RES 第一批：来源证据与空间查询基础

批次 M1；已读 AGENTS、C010、C012、C013；基线 `b3290464d18d92aa1eb21d219d77b07b8f9d6612` / v0.6.0-test2。

本目录交付可复核的来源、截图、部分坐标与碰撞查询基础。可独立审查整合；正式课程坐标、烟火寿命和 HE 回填标定仍为 BLOCKED。不会把这四条自动加入正式课程。

| 文件 | 用途与证据边界 |
| --- | --- |
| source-pages.json | 四个优先教学页面的实际获取结果、HTML 散列、媒体 URL／散列、源命令；网页声明不等于游戏验证 |
| screenshot-index.json / screenshots/ | 15 张站位／瞄点／落点／覆盖参考图及 4 条观察图带；来源与派生图片各自有散列 |
| clip-observations.json | 实际下载短片的编码时长、帧率、取样时间和检查结论；时长不是烟火寿命 |
| calibration-records.json | D2-001、D2-014、D2-009、D2-010 的资源侧待标定记录和烟＋HE 关联；未测项为 null，formalImportAllowed=false |
| coordinate-query-samples.json | 两条源命令坐标转换、向下世界碰撞命中结果；不是已核验站位或角色脚底 |
| collision-audit.json | R002 五条原始流、10,096 形状、全部六层及表面覆盖审计 |
| collision/world-collision-raw-v1.zip | 约 3.3 MB 的处理后查询输入：完整原 manifest 与五条已核验的 raw 流，无 GLB／vphys／完整录像 |
| reusable-resources.json | 4 份环境 dust／steam／lightshaft 材质、移动贴图路径与散列；不是烟火／HE 运行时逻辑 |
| missing-items.md | 每条场景的具体缺项、实体覆盖、APP 接口依赖和继续方案 |
| app-adapter-report.json / app-pending/ | 针对 APP PR #5 head 516f2ce 的 v1 合同适配回执及四条草稿；importable=false，运行时坐标、镜头与时序仍为 null |
| aim-texture-checks.json | 全部 14 条稳定编号的新增纹理／手机验收记录；四条优先参考的实际图像观察，未测条件保持 null |
| local-texture-inventory.json | 两条真实页面命令位置附近的保守材质候选、移动纹理与原图尺寸／散列／UV；不是精确瞄点表面或视觉验收 |

## 本轮真实发现

- D2-001、D2-009 页面有 setpos/setang 数值；D2-014、D2-010 页面未提取到相同命令。源值保留在 Source XYZ / pitch-yaw-roll 字段，派生显示位置另列。
- 两条命令点向下查询命中地面与命令位置相差约 1.62～1.64 显示单位。**不能将命令 Z 直接当人物脚底，也不能擅自减去固定眼高。** 位置语义仍待实机确认。
- 四条页面实录有运镜或剪切。两个火短片在同一落点观察视角的前后取样中能看见火区扩大，但未包含熄灭；HE 片段缺少完整、连续的成型烟→缺口→回填证据。
- 指定 Nart YouTube 原链接获取两次均超时；本包 GetReplay 媒体仅作可追溯的补充证据，不声称是同一个 Nart 投法、同一游戏／地图版本。
- R002 的层 0～5 三角形数量依次为 266,963 / 334 / 792 / 12,125 / 1,128 / 228。显示适配排除了后面三层，烟火查询不能照搬显示过滤。
- 两个 Mesh（10093、10094）存在逐三角形表面覆盖；查询返回 triangle_surfaces.u8 的值，不能压成形状的默认表面。

## 坐标与查询规则

统一显示坐标 `dust2_display_y_up_v1`：Source `(x,y,z)` → `(0.0254*x,0.0254*z,-0.0254*y)`。不重居中，只做一次；R003/D2M1 已经转换，不能再旋转或缩放。单位标 `display_unit`，0.0254 是已有显示约定，不认证官方物理单位。Source 角度不能直接复制到显示欧拉角。脚底、眼睛、准星瞄点与爆点均为不同量。

`tools/dust2_collision_query.py` 提供离线线段命中和向下地面查询，返回三角形／形状／层／表面索引与 hash、位置及朝向射线起点的法线。调用方必须显式指定层。此实现是 O(N) 桌面参照，不是 Android 加速结构、球体扫掠、动态实体、烟雾扩散或官方过滤策略。playerclip、csgo_grenadeclip、sky 分别保留，尚不决定其是否阻挡烟扩散或火蔓延。

原始输入来自共享的 `CS2_Dust2_Collision_R002.zip`，内部 `dust2_collision_r002/`。本轮重新核验其全部五个流与形状范围，已在 collision/ 提交约 3.3 MB 的纯查询输入副本。它避开约 32 MB 原研究包中的 GLB／vphys／预览，解压即可运行离线查询，另一侧不用重新下载原包。此副本不替代 APK 资源。

## 复现

Python 3.10+、numpy、Pillow；媒体取样需要 ffmpeg / ffprobe。来源请求失败会逐条记录。

```bash
python3 tools/collect_c013_references.py --output docs/calibration/C013 --media-dir /tmp/c013-media
python3 tools/build_c013_reference_pack.py --media-dir /tmp/c013-media
python3 tools/audit_c013_collision.py /path/to/dust2_collision_r002 --report docs/calibration/C013/collision-audit.json --archive docs/calibration/C013/collision/world-collision-raw-v1.zip
python3 -m unittest discover -s tools/tests -v
python3 tools/validate_c013_references.py
```

查询示例（两端为显示坐标；层只是显式实验选择）：

```bash
python3 tools/dust2_collision_query.py /path/to/dust2_collision_r002 --start -11.486629 4.535248 16.765569 --end -11.486629 -0.464752 16.765569 --layers 0 1 2
```

## 时钟与来源

正式课程统一时间零点计划采用投掷释放，当前未能标定连续游戏时钟，所以 effectStages.ageSeconds 为 null。观察图带只写编码视频时间；跨镜头剪切、视频慢放／加速与不同片段的秒数不串成课程年龄。烟与 HE 的课程关联已建立，但偏移、缺口半径、回填时刻没有数值。

截图和录像来源为各条 GetReplay 原链接，Valve 为原游戏／地图来源；Source 2 Viewer / ValveResourceFormat 为既有资源解析工具。本包缩小截图与取样帧仅供内部核对，没有把第三方完整视频写入 APP。网页、截图中的日期与标记不自动等同游戏构建号，实际构建字段保持 null。

APP 自有合同已从 PR #5 head `516f2ce41ccd55a7aac441a2ee52d80d550e71a1` 读取（合同 blob `f168be008e7c60334665b1a600dd13f5c9c84dd4`）。本包在资源侧导出四份合同一致的待测草稿，没有修改 APP 合同或 Kotlin。检查 CourseJson.parse 确认其先拒绝 importable=false；本轮没有执行 Kotlin 导入测试，不把草稿称为可播放课程。后续 APP head／合同修改后须重新核对。

## 2026-10-04 瞄点精度补充回执

已同步最新 main `b28be1c09a6e4debe38c88a6b9c31f1f0fabdd04`，并读取 PR #4 项目经理意见及 C013-aim-texture-acceptance。Ultra 保持未来规划。

- 对 D2-001／014／009／010 的参考图作了实际视觉检查；瞄点附近的墙面污斑、窗框、管线、门梁、拱门和小门等说明记录在 aim-texture-checks。它们是第三方参考图可见内容，没有认证当前 CS2 版本或精确像素瞄点。
- D2-001 的第三人称图中人物脚部呈空中姿态，不能拿这一帧证明脚底落地。D2-014／009／010 缺少单独脚部接触图。
- 以两条未认证角色／眼位的命令位置为中心、3 显示单位半径做 manifest 包围球候选查询：D2-001 为 38 分块／26 材质／34 纹理，D2-009 为 52 分块／23 材质／29 纹理。分别 32／28 张纹理有降采样，最大边长比 8；这说明可定位原图，不能证明这些纹理都影响瞄点，或手机已经不可辨认。
- 选择是保守球体重叠，不等于可见性、精确距离或瞄点射线命中；大型分块可能被纳入。D2-014／010 的位置未知，因此没有编造查询中心。
- 尚未取得同站位、朝向、FOV 的 APP／CS2 对照图和手机隐藏提示画面，全部验收条件保持 null；没有修改贴图、UV、材质、地图分卷或版本，避免在未确定教学偏差时无差别升级整图。
- APP 合同名称 display-m-y-up-v1 与既有 dust2_display_y_up_v1 数值一致，只做字段名称对齐，不再缩放／旋转；R002 的 0.0254 仍是显示约定，未额外认证 Valve 物理米制。

新增复现命令：

```bash
python3 tools/adapt_c013_pending.py
python3 tools/inventory_c013_local_textures.py
python3 tools/validate_c013_references.py
```
