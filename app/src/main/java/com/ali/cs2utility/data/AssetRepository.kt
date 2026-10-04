package com.ali.cs2utility.data

import android.content.Context
import com.ali.cs2utility.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Catalog routes assets; adding maps never requires branching in the renderer or Activity. */
class AssetRepository(private val context: Context) {
    private fun json(path: String) = JSONObject(context.assets.open(path).bufferedReader().use { it.readText() })
    private fun JSONObject.vec(key: String): Vec3 {
        val a = getJSONArray(key)
        require(a.length() == 3) { "$key must contain xyz" }
        val values = (0..2).map { a.getDouble(it).toFloat().also { n -> require(n.isFinite()) } }
        return Vec3(values[0], values[1], values[2])
    }
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    fun maps(): List<MapDefinition> {
        val root = json("catalog.json")
        require(root.getInt("schemaVersion") == 1)
        return root.getJSONArray("maps").objects().map {
            MapDefinition(it.getString("id"), it.getString("name"), it.getString("description"),
                it.getString("sceneAsset"), it.getString("lineupsAsset"))
        }.also { require(it.isNotEmpty() && it.map { m -> m.id }.distinct().size == it.size) }
    }
    fun scene(map: MapDefinition): SceneDefinition {
        val root = json(map.sceneAsset)
        require(root.getInt("schemaVersion") == 1)
        val boxes = root.getJSONArray("boxes").objects().map {
            val c = it.getJSONArray("color")
            val size = it.vec("size")
            require(size.x > 0 && size.y > 0 && size.z > 0)
            Box(it.vec("center"), size, FloatArray(3) { i -> c.getDouble(i).toFloat().coerceIn(0f, 1f) })
        }
        val labels = root.getJSONArray("labels").objects().map { SceneLabel(it.getString("name"), it.vec("position")) }
        val extent = root.getDouble("extent").toFloat()
        require(extent.isFinite() && extent > 1f)
        return SceneDefinition(extent, boxes, labels, root.optString("objAsset").takeIf { it.isNotBlank() }, root.getBoolean("schematic"),
            root.optString("meshAsset").takeIf { it.isNotBlank() },
            if(root.has("cameraTarget")) root.vec("cameraTarget") else Vec3(0f,0f,0f),root.optString("displayLabel"),
            root.optJSONArray("targets")?.objects()?.map {
                TargetMarker(it.getString("id"),it.getString("label"),it.vec("position"),it.vec("focus"),
                    it.getDouble("distance").toFloat(),it.optDouble("yaw",90.0).toFloat(),it.optDouble("pitch",78.0).toFloat())
            } ?: emptyList(),root.optDouble("minimumDistance",6.0).toFloat(),
            root.optString("credits"),root.optString("overviewText"),root.optString("emptyLineupsText"))
    }
    fun lineups(map: MapDefinition): List<Lineup> {
        val root = json(map.lineupsAsset)
        require(root.getInt("schemaVersion") == 1)
        val items = root.getJSONArray("lineups").objects().map {
            val v = it.getJSONObject("video")
            val status = Verification.valueOf(it.getString("status"))
            val source = it.getString("source")
            val build = it.getString("gameBuild")
            val date = it.getString("verifiedAt")
            require(it.getString("mapId") == map.id)
            if (status == Verification.VERIFIED) require(source.isNotBlank() && build.isNotBlank() && date.isNotBlank())
            val kind = VideoKind.valueOf(v.getString("kind"))
            if (status == Verification.VERIFIED) require(kind != VideoKind.DEMO)
            val url = v.getString("url")
            if (kind != VideoKind.DEMO) {
                val uri = java.net.URI(url)
                require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "Video requires HTTPS" }
            }
            val a = it.getJSONArray("steps")
            Lineup(it.getString("id"), map.id, it.getString("title"), UtilityType.valueOf(it.getString("type")),
                it.getString("side"), it.getString("area"), it.getString("standName"), it.getString("landingName"),
                it.vec("stand"), if(it.isNull("landing")) null else it.vec("landing"), it.getString("aim"), it.getString("throwMode"),
                (0 until a.length()).map { i -> a.getString(i) }, status, build, date, source,
                VideoSource(kind, url, v.getString("attribution"),v.optString("bvid"),v.optLong("cid"),v.optInt("startSeconds"),v.optInt("endSeconds")),it.optString("groupId"),it.optString("groupTitle"),
                it.optInt("spawnNumber"),it.optString("aimImage"),it.optString("spawnImage"),it.optString("practiceCommand"),
                if(it.has("modelStand")) it.vec("modelStand") else null,
                it.optJSONObject("aimCrop")?.let { c -> ImageCrop(c.getInt("x"),c.getInt("y"),c.getInt("width"),c.getInt("height")) })
        }
        require(items.map { it.id }.distinct().size == items.size) { "Duplicate lineup IDs" }
        return items
    }
}
