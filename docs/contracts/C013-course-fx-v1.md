# C013 APP 课程与动态效果接口 v1

任务 C013-APP；基线 main b3290464d18d92aa1eb21d219d77b07b8f9d6612；对齐 C010/C012/C013。
APP 已实现 `CourseJson.parse(text, actualMapVersion)`、`Course.validate` 与 `MapSceneView.startCourse`。
本版可供资源方提交小样并回执；尚不代表双方已确认、正式课程校准或手机验收完成。

## 身份、来源和草稿

- schema 固定 `c013-course-fx-v1`，mapId 固定 `dust2`。
- `lessonId` 是稳定身份，正式内容沿用 D2-001～D2-014；组合课可用独立组名及 `relatedIds`，不得把开发演示冒充原编号。
- `mapVersion` 绑定实际加载的移动 manifest 原始字节 SHA256，当前为 `fc60c032290dffcb233e3de9da3c59694293362ec72643cc1ebde9c1b13a17d0`。加载器实际计算 SHA，再校验，资源变化拒绝旧课。
- `coordinateSpace=display-m-y-up-v1`，坐标 [x,y,z] 是显示米制 Y-up。Source 坐标只在资源导入时转换一次；不在课程加载时重复缩放或旋转。
- `status` 为 DEVELOPMENT / CALIBRATED / PENDING_CALIBRATION。`importable=false` 的草稿可保存 null；播放器拒绝草稿，不自动用默认站位或统一秒数补齐。
- `evidence` 至少保留 url、gameBuild、releaseVideoSeconds；待测值为 null。正式标 CALIBRATED 必须有来源、版本和释放视频时间；实际游戏复测证据仍由项目经理审查，非只靠字段通过。
- `title`、`throwHint` 表达人能理解的站位／投法。资源侧补参考截图、卡位面、动作资源与测量记录时使用同一 lessonId，原文件仍由资源侧管理。

## 相机与空间字段

`foot`、`eye`、`aim` 分别为脚底、实际眼位和世界瞄点。`bodyYaw` 是本 APP 显示空间绕 Y 的人物朝向，不直接使用 Source 欧拉角。
`cameras` 包含 stance / aim / overview，每项有 position [x,y,z]、yaw、pitch、distance。
stance/overview 的 position 是轨道目标，distance 为相机距离；aim 的 position 是眼位，distance 保留正数但不用于轨道计算。
镜头 yaw=0 朝 -Z，正 pitch 向下；`aimFov` 为垂直视场角（30～110度）。开发样例70度不是经校准 CS2 视场。
正式课程检查第一人称相机与 eye 一致、朝向对准 aim。不同宽高比仍以世界瞄点为中心，准星绘制在实际地图视口中心。

## 同一课程时间轴

`timeOrigin=throw-release-seconds`：释放为0秒，站位/瞄点/准备可为负数。
begin ≤ aimAt ≤ throwAt ≤ 0；duration 为课程结束。所有效果阶段时间相对这个零点；不能直接填视频进度条或参考片段长度。

| 数据 | 字段 | 当前行为 |
| --- | --- | --- |
| path | [{seconds,position,bounce}] | 相邻节点线性插值，节点时间严格递增，首节点0；bounce作为可追溯预设反弹节点，不是物理解算 |
| smoke | [{id,center,radius:[x,y,z],start,formed,fade,end}] | 起烟后扩张/增密、成型维持、消散；阶段严格递增 |
| fire | [{id,center,radius,start,fade,end}] | 每个地面区域独立起燃/维持/熄灭；晚到区 start 更晚；地面中心需资源方标定 |
| he | [{id,center,radius,start,open,refill,end,smokeIds}] | 短暂爆闪/烟尘；只对引用烟施加局部径向缺口，随后回填；不会删除整团烟 |

pause/play 控制统一课程时间，切视角只变相机；replay把课程时间归零点前，不保留旧轨迹、火区或缺口。
CPU效果由课程时间解析求值，重播/seek不依赖上一次粒子缓存。暂停或 Activity 后台冻结，恢复不补算后台经过时间；GL重建只重建程序，课程状态不因重建归零。
同课当前只定义一条主要投掷路径；HE交互有独立事件，第二个人物/HE飞行路径、多角色动作同步作为后续扩展，不声称已经实现完整两投掷者教学。

## 边界和有界资源

JSON文本≤64K字符；path≤256节点，烟≤8、火区≤64、HE≤16；ID唯一，HE引用烟必须存在。
最坏点元预算 `96*烟+6*火区+32*HE+1≤1024`。数值要求有限、时间在课程边界内；位置绝对值≤1000显示米。
程序性GLES2点元使用固定32KiB直接缓冲；CPU准备与GL绘制都在GL线程，无大贴图解码、新线程池或地图分卷变更。
此初版尚无软粒子深度/光照匹配、烟内视觉质量实测及真实沙二墙体约束。设备点尺寸能力影响近景效果，必须实机检查后调质量，不能凭编译声称CS2效果等价。

## 小样与缺项

`C013-development-smoke-he.json` 是可解析的纯实验数据；运行界面使用同等 `DevelopmentCourses.make()`，位置在匪家实体上方测试空间，非可落脚地面或正式站位。
其关联 D2-001/D2-014 只说明联调目标；所有秒数/范围均是实验参数，不能移入已校准清单。
`C013-D2-001-pending.json` 提供正式身份和已知参考，未知项保持 null，importable=false；资源侧适配时保留来源与待测值。
火样例是另一独立开发课，不修改D2-009/D2-010候选清单。

`SmokeGrid` 和 `GroundFireSchedule` 为单独CPU原型：前者在三维空单元连通图上查询扩散步数，后者在地面邻接图上计算区域到达时间。合成墙/门/楼层测试不等于真实地图碰撞完成；尚未连接R002完整碰撞层/实体，也未用于当前程序烟火绘制。

接入正式课程仍待：真实站位/眼位/准星、卡位截图、投法/轨迹/反弹、烟生命周期、火延迟区、HE缺口与回填测量、人物动作资源、碰撞查询语义，以及Android录屏/帧时间/内存证据。缺项不阻塞本框架的代码审查。


## 2026-10-04 瞄点补充：提示与正式证据

对齐 main `b28be1c09a6e4debe38c88a6b9c31f1f0fabdd04` 的 `C013-aim-texture-acceptance.md`。原接口为 APP 提案；本次为可选字段扩展，不宣称资源侧已经适配。

第一人称黄色小圈投影到实际 `aim` 世界坐标，不使用地图标签的+0.3米高度偏移。白色准星始终位于视口中心。提示按同一课程时钟显示1.5秒并在随后1秒淡出；这些是界面提示参数，不是 CS2 效果时序。暂停/后台冻结，切视角不重新计龄。按钮可立即隐藏或重新显示；隐藏选择在重播、切镜头和 Activity 恢复后保留。退出课不留提示。不提供墙缝/贴图自动识别，开发点仍是实验瞄点。

运行课可选 `aimAcceptance`；DEVELOPMENT允许缺省/null。CALIBRATED必须有完整对象、校验通过及可审查证据索引，否则拒绝导入/播放。`C013-aim-acceptance-pending.json`为待补模板，不能作为课程直接播放。

| 字段 | 含义/结构校验 |
| --- | --- |
| mapVersion | 实际地图 manifest SHA，与课程版本相同 |
| appBuild | 对应截图/录屏的APP构建标识 |
| referenceDescription | 站位/瞄点周边真实参照物描述 |
| cs2Frame | CS2实机参考截图/录像帧索引；来源/游戏版本/零点仍由evidence记录 |
| appHintVisibleFrame / appHintHiddenFrame | 同一课程显示/关闭提示的两份画面索引，不能填同一项 |
| localAssetAudit | 局部纹理/几何核对与修改记录索引，须可追溯UV/材质版本或无须修改的检查记录 |
| phoneDevice | 实际验收手机标识，不得用桌面渲染器名称冒充 |
| viewportWidth / viewportHeight / verticalFov | 地图实际视口像素与垂直FOV，FOV必须与课程aimFov一致 |
| stanceContactReadable | 脚部/墙箱接触与站位参照物检查通过 |
| referenceConsistent | 当前CS2与APP纹理/位置一致性检查通过 |
| phoneReadableWithoutHint | 手机关闭提示后依靠真实纹理对点检查通过 |

最后三项须全部true；未测项保留null在待补模板中，不标通过。本校验只阻止缺字段、资源版本/FOV不符和明确未通过的记录，不能判断文件是否真实、画面是否清楚。项目经理仍须查看索引指向的真实材料，核对手机、构建和资源版本，并逐条验收。只有小圆圈或来源字符串不等于校准证据。尚未提供任何正式CALIBRATED课程或手机视觉验收结果。
