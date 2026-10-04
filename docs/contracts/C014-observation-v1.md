# C014 APP 自动镜头与落点窗口补充接口

对齐main aec0b1936f4de5053ba35100a30367e25915f505的C014要求，延续c013-course-fx-v1。APP提案已实现，资源侧确认和真实三课小样仍待提供。

`cameras`新增`follow`和`landing`，与stance/overview采用同样的position/yaw/pitch/distance结构，都是轨道镜头（distance≥1显示米、pitch 20～85度）。follow的position在播放时由当前道具位置覆盖，出手前以eye为目标、落地后固定最后轨迹点；yaw/pitch/distance必须由课程标定，当前没有墙体避让。landing的position是固定观察目标，与主镜头独立。所有坐标沿用display-m-y-up-v1，仅转换一次。

CALIBRATED两项必填，旧DEVELOPMENT缺省时使用overview联调。正式导入仍要求原有来源、地图SHA和瞄点贴图验收材料；字段齐全不能代替项目经理审查材料。

自动镜头按照课程阶段选择STANCE、AIM、FOLLOW。手动选择停用自动镜头；“自动镜头”按钮按当前时间恢复。重播从begin恢复自动镜头；“跳到落点效果”从最后path节点时间播放FOLLOW。主视图与右侧固定落点窗口每帧仅tick一次，不建立第二个播放器。

HE课可在begin到0之间配置烟start/formed，使烟在HE释放前已经成型；全部时间仍相对主要投掷释放0秒。火/HE事件仍从0到duration。不得把开发秒数或测试空间坐标直接复制到正式三课。

窗口可放大、返回、关闭名称/范围；这些操作不改变课程时间。只共享一套地图缓存和有界队列，但增加第二次绘制，手机帧时间与内存待实测。

效果当前使用世界尺寸三角形billboard（最多1024片、216KiB固定缓冲），替代C013初版GL_POINTS/32KiB。HE缺口按每个烟团中心采样，仍有billboard重叠近似；真实墙/门/楼层约束、软粒子深度及CS2外观对照仍未完成。

联调课身份DEV-C014-SMOKE/FIRE/HE，分别关联D2-001/D2-010/D2-014，全部calibrated=false；正式课待资源侧来源、版本、站位/瞄点、轨迹、镜头、效果时间、截图/录屏后再接入。
