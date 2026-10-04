# 扩充目标与教程

同一目标的条目使用相同 `groupId` / `groupTitle`，例如 `dust2-ct-smoke`（仅为字段示例，当前未加入该教程）。页面先选择目标，再按 `spawnNumber` 显示位置。

| 字段 | 用途 |
| --- | --- |
| `id`、`mapId` | 稳定条目与地图标识 |
| `groupId`、`groupTitle` | 目标组 |
| `spawnNumber` | 原视频编号，不能直接混用其他来源编号 |
| `modelStand` | 本模型内的三维参考站位 |
| `aimImage`、`aimCrop` | 在线参考图及原始像素裁切区域 |
| `video.kind` | BILIBILI、DIRECT、EXTERNAL、DEMO |
| `video.bvid`、`cid` | B站原视频标识，CID 使用 Long |
| `video.startSeconds`、`endSeconds` | 该身位示例范围 |
| `status` | UNVERIFIED 来源教程；VERIFIED 游戏内复测；DEMO 流程样例 |
| `source`、`gameBuild`、`verifiedAt` | 来源、复测版本、日期 |

在 `scene.json.targets` 登记目标位置与站位镜头：`position` 为目标标记，`focus` / `distance` / `yaw` / `pitch` 为点选后的相机位置。三维点击区域随投影更新；下方按钮提供同等功能。

当前沙二显示适配方向（显示缩放约定，尚未认证物理单位）：模型 xyz = [游戏 x×0.0254，游戏 z×0.0254，−游戏 y×0.0254]。来源 setpos 的高度不能直接认作地面高度。新站位必须对照模型、原视频和游戏内实际坐标逐项核对，不能仅凭两份教程同号就认为完全相同。

精确落点未知时 `landing` 留 null。三维曲线只有在有真实数据时才启用。

`tools/validate_assets.py` 检查网格每个记录、顶点、索引、贴图路径和二次幂尺寸，核对目标出生位与视频分段。

源码包含已转换的沙二显示网格。重新接收 R002 导出需要 NumPy，使用 `tools/import_dust2_collision.py`。
