# 完整地图采集探针，非挂门烟定位

`input.json`来自当前main scene.json匪家标签位置的通用自由相机，加1.65显示单位仅用于开发观察；不是用户视频站位或CS2眼位标定。

`capture.png`是全部恢复地图在Mesa llvmpipe的离屏图（1200×800，FOV70°）。`capture.json`用中心像素查询箱体，`ground.json`用[200,650]查询地面；共享同一图和相机。法线朝上只说明显示支撑候选，不证明角色脚底/净空。APP实际Kotlin对两条射线查询结果已与离线全图结果核对，见上级APP-C015-receipt.json。

复现主图/中心采集：

```bash
python3 tools/restore_assets.py
python3 tools/validate_assets.py
python3 tools/check_mobile_scene.py --capture-camera docs/validation/C015-map-probe/input.json --capture-output docs/validation/C015-map-probe
```

本图证明完整地图的渲染/坐标采集链可工作；挂门烟墙缝/污点与人物同站位对照仍待空间候选、拟合误差和手机证据。
