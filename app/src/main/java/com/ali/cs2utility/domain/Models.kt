package com.ali.cs2utility.domain

data class Vec3(val x: Float, val y: Float, val z: Float)
enum class UtilityType(val label: String, val color: Int) {
    SMOKE("烟雾", 0xff85baff.toInt()), FLASH("闪光", 0xfff1d177.toInt()),
    MOLOTOV("燃烧", 0xffff9173.toInt()), HE("高爆", 0xff9dd5a7.toInt())
}
enum class Verification(val label: String) {
    DEMO("流程示例 · 未校验"), UNVERIFIED("来源教程 · 待本地复测"), VERIFIED("已校验")
}
enum class VideoKind { DEMO, DIRECT, EXTERNAL, BILIBILI }
data class VideoSource(val kind: VideoKind, val url: String, val attribution: String,
    val bvid: String = "", val cid: Long = 0, val startSeconds: Int = 0, val endSeconds: Int = 0)
data class ImageCrop(val x: Int, val y: Int, val width: Int, val height: Int)
data class Lineup(
    val id: String, val mapId: String, val title: String, val type: UtilityType,
    val side: String, val area: String, val standName: String, val landingName: String,
    val stand: Vec3, val landing: Vec3?, val aim: String, val throwMode: String,
    val steps: List<String>, val status: Verification, val gameBuild: String,
    val verifiedAt: String, val source: String, val video: VideoSource,
    val groupId: String = "", val groupTitle: String = "", val spawnNumber: Int = 0,
    val aimImage: String = "", val spawnImage: String = "", val practiceCommand: String = "",
    val modelStand: Vec3? = null, val aimCrop: ImageCrop? = null
)
data class MapDefinition(
    val id: String, val name: String, val description: String,
    val sceneAsset: String, val lineupsAsset: String
)
data class Box(val center: Vec3, val size: Vec3, val color: FloatArray)
data class SceneLabel(val name: String, val position: Vec3)
data class TargetMarker(val id: String, val label: String, val position: Vec3,
    val focus: Vec3, val distance: Float, val yaw: Float = 90f, val pitch: Float = 78f)
data class SceneDefinition(
    val extent: Float, val boxes: List<Box>, val labels: List<SceneLabel>,
    val objAsset: String?, val schematic: Boolean,
    val meshAsset: String? = null, val cameraTarget: Vec3 = Vec3(0f,0f,0f),
    val displayLabel: String = "", val targets: List<TargetMarker> = emptyList(), val minimumDistance: Float = 6f,
    val credits: String = "", val overviewText: String = "", val emptyLineupsText: String = ""
)
data class LineupGroup(val id: String, val title: String, val items: List<Lineup>)
object TargetGroups {
    fun from(items: List<Lineup>) = items.groupBy { it.groupId.ifBlank { it.id } }.map { (id,list) ->
        LineupGroup(id,list.first().groupTitle.ifBlank { list.first().title },list.sortedBy { it.spawnNumber })
    }
}
data class Filter(val query: String = "", val type: UtilityType? = null, val favoritesOnly: Boolean = false)
object LineupFilter {
    fun apply(all: List<Lineup>, filter: Filter, favorites: Set<String>): List<Lineup> = all.filter {
        (filter.type == null || it.type == filter.type) &&
        (!filter.favoritesOnly || it.id in favorites) &&
        (filter.query.isBlank() || listOf(it.title, it.area, it.standName, it.landingName)
            .any { field -> field.contains(filter.query.trim(), ignoreCase = true) })
    }
}
