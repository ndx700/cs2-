package com.ali.cs2utility.scene

import com.ali.cs2utility.domain.Vec3
import org.json.JSONArray
import org.json.JSONObject

data class CalibrationFrame(val camera: CameraState,val eye: Vec3,val verticalFov: Float,
    val width: Int,val height: Int,val mapVersion: String,val ray: SceneRay,val rgba: ByteArray?)
data class CalibrationSample(val frame: CalibrationFrame,val hit: SurfacePick?,val error: String?)

/** Evidence format, deliberately separate from playable/calibrated course JSON. */
object CalibrationCapture {
    fun json(field: String,sample: CalibrationSample,screenshot: String?): JSONObject {
        require(field in listOf("foot","eye","aim","landing"))
        val f=sample.frame;val c=f.camera
        fun p(v: Vec3)=JSONArray(listOf(v.x,v.y,v.z))
        fun pose()=JSONObject().put("position",p(Vec3(c.x,c.y,c.z))).put("yaw",c.yaw).put("pitch",c.pitch).put("distance",c.distance).put("free",c.free)
        val surface=sample.hit?.let {h->JSONObject().put("position",p(h.position)).put("normalAgainstRay",p(h.normal)).put("distance",h.distance).put("part",h.partAsset).put("materialIndex",h.material).put("triangle",h.triangle).put("barycentric",p(h.barycentric)).put("supportCandidate",h.supportCandidate(f.ray))} ?: JSONObject.NULL
        return JSONObject().put("schema","c015-display-capture-v1").put("status","DEVELOPMENT").put("importable",false)
            .put("coordinateSpace","display-m-y-up-v1").put("mapVersion",f.mapVersion).put("field",field)
            .put("value",if(field=="eye")p(f.eye) else sample.hit?.let {p(it.position)} ?: JSONObject.NULL)
            .put("camera",pose()).put("cameraPositionMeaning",if(c.free)"eye" else "orbit-target")
            .put("actualCameraEye",p(f.eye)).put("verticalFov",f.verticalFov).put("viewportWidth",f.width).put("viewportHeight",f.height)
            .put("ray",JSONObject().put("origin",p(f.ray.origin)).put("direction",p(f.ray.direction)).put("maxDistance",f.ray.maxDistance))
            .put("surface",surface).put("error",sample.error ?: JSONObject.NULL).put("screenshot",screenshot ?: JSONObject.NULL)
            .put("screenshotScope","Main GL map framebuffer only, same camera/ray frame; no Android labels or hint overlay")
            .put("semantics","Visible enabled display triangles including transparent/alpha geometry; not world_physics, hull clearance, or certified CS2 placement")
    }
}
