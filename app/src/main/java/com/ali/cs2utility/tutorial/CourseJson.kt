package com.ali.cs2utility.tutorial

import com.ali.cs2utility.domain.Vec3
import com.ali.cs2utility.scene.CameraState
import org.json.JSONObject

/** Runtime import only; incomplete calibration drafts stay in docs/calibration and are not playable. */
object CourseJson {
    fun parse(text: String,actualMapVersion: String): Course {
        require(text.length<=65536) {"课程数据超出 64K 字符上限"}
        val j=JSONObject(text)
        require(j.getString("schema")=="c013-course-fx-v1" && j.getString("mapId")=="dust2")
        require(j.getString("timeOrigin")=="throw-release-seconds")
        require(j.getBoolean("importable")) {"待校准草稿不可导入播放"}
        val status=j.getString("status");require(status in listOf("DEVELOPMENT","CALIBRATED"))
        val source=j.getJSONObject("evidence")
        fun optional(key: String)=if(source.isNull(key))null else source.getString(key)
        val evidence=SourceEvidence(optional("url"),optional("gameBuild"),
            if(source.isNull("releaseVideoSeconds"))null else source.getDouble("releaseVideoSeconds"),status=="CALIBRATED")
        fun point(o: JSONObject,key: String): Vec3 {
            val a=o.getJSONArray(key);require(a.length()==3)
            return Vec3(a.getDouble(0).toFloat(),a.getDouble(1).toFloat(),a.getDouble(2).toFloat())
        }
        fun <T> list(key: String,max: Int,read: (JSONObject)->T): List<T> {
            val a=j.getJSONArray(key);require(a.length()<=max)
            return (0 until a.length()).map {read(a.getJSONObject(it))}
        }
        val cameras=j.getJSONObject("cameras")
        fun camera(key: String,free: Boolean): CameraState {
            val o=cameras.getJSONObject(key);val p=point(o,"position")
            return CameraState(o.getDouble("yaw").toFloat(),o.getDouble("pitch").toFloat(),
                o.getDouble("distance").toFloat(),p.x,p.z,p.y,free)
        }
        val related=j.getJSONArray("relatedIds");require(related.length()<=14)
        val acceptanceJson=j.optJSONObject("aimAcceptance")
        val acceptance=acceptanceJson?.let {AimAcceptance(it.getString("mapVersion"),it.getString("appBuild"),it.getString("referenceDescription"),
            it.getString("cs2Frame"),it.getString("appHintVisibleFrame"),it.getString("appHintHiddenFrame"),
            it.getString("localAssetAudit"),it.getString("phoneDevice"),it.getInt("viewportWidth"),it.getInt("viewportHeight"),
            it.getDouble("verticalFov").toFloat(),it.getBoolean("stanceContactReadable"),it.getBoolean("referenceConsistent"),it.getBoolean("phoneReadableWithoutHint"))}
        val c=Course(j.getString("lessonId"),(0 until related.length()).map {related.getString(it)},j.getString("title"),
            j.getString("mapVersion"),j.getString("coordinateSpace"),evidence,
            point(j,"foot"),point(j,"eye"),point(j,"aim"),j.getDouble("bodyYaw").toFloat(),j.getString("throwHint"),
            camera("stance",false),camera("aim",true),camera("overview",false),
            list("path",256) {PathKey(it.getDouble("seconds"),point(it,"position"),it.optBoolean("bounce",false))},
            list("smoke",8) {SmokeEvent(it.getString("id"),point(it,"center"),point(it,"radius"),it.getDouble("start"),it.getDouble("formed"),it.getDouble("fade"),it.getDouble("end"))},
            list("fire",64) {FireZone(it.getString("id"),point(it,"center"),it.getDouble("radius").toFloat(),it.getDouble("start"),it.getDouble("fade"),it.getDouble("end"))},
            list("he",16) {
                val a=it.getJSONArray("smokeIds");require(a.length()<=8)
                HeEvent(it.getString("id"),point(it,"center"),it.getDouble("radius").toFloat(),it.getDouble("start"),it.getDouble("open"),it.getDouble("refill"),it.getDouble("end"),(0 until a.length()).map {i->a.getString(i)}.toSet())
            },j.getDouble("begin"),j.getDouble("aimAt"),j.getDouble("throwAt"),j.getDouble("duration"),j.getDouble("aimFov").toFloat(),acceptance)
        c.validate(actualMapVersion);return c
    }
}
