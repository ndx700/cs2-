# C015 开发采集与资源联调

沿用C013运行课与C014五镜头接口。本文件描述独立的开发证据格式，不增加第二套可播放课程格式，也不更改资源方的校准记录。

调试版地图加载完成后，长按地图名称进入隐藏开发面板。先选D2-001/D2-014/D2-010证据课号，浏览真实参照物，再点选脚底候选、瞄点或落点；眼位直接记录当前相机真实眼位。可以输入相机position/yaw/pitch/distance/free候选，FOV由现有渲染器固定为轨道45°或自由70°，不填成未测的CS2 FOV。普通发布版无此入口。

拾取使用启用的D2M1显示三角形：射线先筛分块包围球，再逐分块查询最近双面三角形。复用地图工作线程和24MiB解码预算，每次一块，不持有全图CPU缓存，不在GL线程读地图。上下文/地图generation变化使查询作废。忙时明确提示重试。显示表面包含透明材质的三角形，并非透明纹理逐像素测试、world_physics、动态实体、人物净空或正式脚底验证。向下命中且法线朝上只标supportCandidate。

`c015-display-capture-v1`每次记录field/value、射线、命中位置/法线/重心坐标/part/material/triangle，及camera、cameraPositionMeaning、actualCameraEye、verticalFov、viewportWidth/Height、实际manifest SHA。轨道camera.position是观察目标；actualCameraEye才是眼位，不能混用。未命中点保持null，不自动填当前眼位、原点或脚底加固定高度。状态固定DEVELOPMENT、importable=false。

截图在主视图绘制完成且可见区域就绪后、窗口绘制前从GL帧缓冲读取，与射线及相机同帧；不含Android标签/辅助小圈。最多400万像素，超限/读回失败记录无截图；截图不存在不能当作对照证据。采集结果保存于应用私有目录，按课号/字段覆盖自身候选；JSON/PNG经系统“保存文件”导出为ZIP，manifest附逐文件SHA256。旋转后已有文件保留，待导出课号通过偏好恢复；手机UI/SAF和GL读回仍需实际设备验收。

离线核对：

```bash
python3 tools/restore_assets.py
python3 tools/validate_assets.py
python3 -m unittest discover -s tools/tests -v
python3 tools/check_mobile_scene.py --capture-camera path/to/capture.json --capture-output path/to/output
```

离屏图用完整已恢复地图和原GLES材质着色器，读输入camera/视口，重新按同FOV生成图和最近显示三角形候选。图默认不证明用户视频同一站位；资源侧提供的参数应附来源/拟合误差、支撑面、选定参照纹理与视频关键帧。正式课仍用CourseJson解析，不能把采集证据JSON直接导入播放器。

用户三投法按C015区分：挂门烟蹲下对齐→站起跳投；蓝车火桶上抵墙→W跳投；HE保持蹲姿跳投。具体脚底/眼位、姿态和移动/出手关键帧须与逐课支撑面/轨迹小包一起接入。当前尚未实现这三种正式动作；原空中DEV样例已改标“渲染试验”，不用于代替本轮教学。

下一项交接：资源側先回传挂门烟空间候选和误差/支撑面/墙缝污点证据。APP使用此采集/离屏工具给同站位第三/第一人称图，逐字段回执，再接动作/轨迹；未收到前不复用已作废的旧命令坐标。资源回执只确认五镜头/坐标/时钟约定，尚未确认本采集证据格式。
