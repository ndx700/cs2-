package com.ali.cs2utility.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.widget.EditText
import android.widget.Toast
import com.ali.cs2utility.scene.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executor
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Debug-only hidden entry. No development coordinates appear in normal teaching UI. */
class CalibrationPanel(private val activity: Activity,private val scene: MapSceneView,private val worker: Executor) {
    companion object {const val SAVE_REQUEST=15015}
    private var lesson="D2-001"
    private var busy=false
    private val preferences=activity.getSharedPreferences("c015-capture",Activity.MODE_PRIVATE)
    private var exporting: File?=preferences.getString("pendingExport",null)?.takeIf {it in listOf("D2-001","D2-014","D2-010")}?.let {File(activity.filesDir,"c015-capture/$it")}
    private val enabled=activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE!=0
    fun available()=enabled
    private fun folder()=File(activity.filesDir,"c015-capture/$lesson").apply {mkdirs()}
    private fun toast(s: String) {Toast.makeText(activity,s,Toast.LENGTH_LONG).show()}
    fun show() {
        if(!enabled)return
        scene.cancelCapture()
        val choices=arrayOf("选择课号（当前 $lesson）","点选脚底候选","记录当前真实相机眼位","点选瞄点表面","点选落点候选","输入相机候选参数","导出JSON与同帧地图截图ZIP")
        AlertDialog.Builder(activity).setTitle("开发采集 · 不是课程校准")
            .setMessage("先浏览到真实参照物；点选查询显示三角形，不含实体/碰撞净空或透明像素过滤。导出不会生成可播放课。")
            .setPositiveButton("选择操作") {_,_->AlertDialog.Builder(activity).setTitle("C015采集操作").setItems(choices) {_,i->when(i) {
                0->AlertDialog.Builder(activity).setTitle("证据课号").setItems(arrayOf("D2-001","D2-014","D2-010")) {_,n->lesson=arrayOf("D2-001","D2-014","D2-010")[n];show()}.show()
                1->arm("foot");2->{val id=lesson;if(!busy)scene.captureEye {sample->accept("eye",id,sample)}};3->arm("aim");4->arm("landing");5->camera();6->export()
            }}.show()}.setNegativeButton("返回浏览",null).show()
    }
    private fun arm(value: String) {
        if(busy) {toast("请等待上次保存");return}
        val id=lesson;toast("点选地图表面采集 $value；长按地图名称可取消")
        scene.captureNextTap {sample->accept(value,id,sample)}
    }
    private fun accept(field: String,lessonId: String,sample: CalibrationSample?) {
        if(activity.isFinishing || activity.isDestroyed)return
        if(sample==null) {toast("区域尚未就绪、采集冲突或视口无效，请重试");return}
        if(field!="eye" && sample.hit==null) {toast(sample.error ?: "未命中显示表面");return}
        if(busy) {toast("正在保存，请稍候");return}
        busy=true;val capturedField=field;val dir=File(activity.filesDir,"c015-capture/$lessonId").apply {mkdirs()}
        worker.execute {
            val result=runCatching {
                val f=sample.frame
                require(f.mapVersion.matches(Regex("[a-f0-9]{64}"))) {"缺少实际地图SHA"}
                val png="$capturedField.png";var screenshot: String?=null
                f.rgba?.let {raw->
                    val pixels=IntArray(f.width*f.height)
                    for(y in 0 until f.height)for(x in 0 until f.width) {
                        val p=((f.height-1-y)*f.width+x)*4
                        pixels[y*f.width+x]=((raw[p+3].toInt() and 255) shl 24) or ((raw[p].toInt() and 255) shl 16) or ((raw[p+1].toInt() and 255) shl 8) or (raw[p+2].toInt() and 255)
                    }
                    val bitmap=Bitmap.createBitmap(pixels,f.width,f.height,Bitmap.Config.ARGB_8888)
                    try {File(dir,png).outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}} finally {bitmap.recycle()}
                    screenshot=png
                }
                val json=CalibrationCapture.json(capturedField,sample,screenshot).put("lessonId",dir.name)
                    .put("referenceRevision","user-selected-20261004-v2").put("gameBuild",JSONObject.NULL)
                    .put("appBuild",activity.packageManager.getPackageInfo(activity.packageName,0).versionName)
                    .put("operatorVerification","UNVERIFIED_MANUAL_CANDIDATE")
                screenshot?.let {name->json.put("screenshotSha256",sha(File(dir,name)))}
                File(dir,"$capturedField.json").writeText(json.toString(2))
                "$capturedField 已存候选；命中 ${sample.hit?.partAsset ?: "眼位不要求表面命中"}。${if(screenshot==null)"本次无截图" else "已保存同帧地图截图"}"
            }
            activity.runOnUiThread {busy=false;if(!activity.isDestroyed)toast(result.getOrElse {"保存失败：${it.message}"})}
        }
    }
    private fun sha(file: File)=java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {"%02x".format(it.toInt() and 255)}
    private fun camera() {
        val c=scene.cameraState()
        val input=EditText(activity).apply {setText(JSONObject().put("position",JSONArray(listOf(c.x,c.y,c.z))).put("yaw",c.yaw).put("pitch",c.pitch).put("distance",c.distance).put("free",c.free).toString(2))}
        AlertDialog.Builder(activity).setTitle("相机候选；轨道position是目标").setView(input).setPositiveButton("应用") {_,_->
            runCatching {
                val j=JSONObject(input.text.toString());val p=j.getJSONArray("position");require(p.length()==3)
                val c=CameraState(j.getDouble("yaw").toFloat(),j.getDouble("pitch").toFloat(),j.getDouble("distance").toFloat(),p.getDouble(0).toFloat(),p.getDouble(2).toFloat(),p.getDouble(1).toFloat(),j.getBoolean("free"))
                require(listOf(c.x,c.y,c.z,c.yaw,c.pitch,c.distance).all {it.isFinite()});require(listOf(c.x,c.y,c.z).all {kotlin.math.abs(it)<=1000});require(c.distance>=1 && c.pitch in (if(c.free)-85f else 20f)..85f)
                scene.restoreCamera(c)
            }.onFailure {toast("参数拒绝：${it.message}")}
        }.setNegativeButton("取消",null).show()
    }
    private fun export() {
        if(busy) {toast("请等待保存完成");return}
        val dir=folder();val files=listOf("foot","eye","aim","landing").map {File(dir,"$it.json")}.filter {it.isFile}
        if(files.isEmpty()) {toast("请先采集至少一个参数");return}
        exporting=dir
        preferences.edit().putString("pendingExport",dir.name).apply()
        activity.startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE);type="application/zip";putExtra(Intent.EXTRA_TITLE,"C015-$lesson-DEVELOPMENT.zip")
        },SAVE_REQUEST)
    }
    fun saveTo(uri: Uri) {
        val dir=exporting ?: return;exporting=null;busy=true
        preferences.edit().remove("pendingExport").apply()
        worker.execute {
            val result=runCatching {
                val jsons=listOf("foot","eye","aim","landing").map {File(dir,"$it.json")}.filter {it.isFile}
                val versions=jsons.map {JSONObject(it.readText()).getString("mapVersion")}.toSet();require(versions.size==1) {"采集地图版本混杂，请在当前版本重新采集各字段"}
                val files=jsons+jsons.mapNotNull {j->JSONObject(j.readText()).optString("screenshot").takeIf {it in listOf("foot.png","eye.png","aim.png","landing.png")}?.let {File(dir,it)}}
                val manifest=JSONObject().put("schema","c015-capture-bundle-v1").put("status","DEVELOPMENT").put("importable",false).put("lessonId",dir.name).put("mapVersion",versions.single()).put("files",JSONArray(files.map {JSONObject().put("path",it.name).put("sha256",sha(it))}))
                activity.contentResolver.openOutputStream(uri)?.use {out->ZipOutputStream(out).use {zip->
                    zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest.toString(2).toByteArray());zip.closeEntry()
                    for(file in files) {zip.putNextEntry(ZipEntry(file.name));file.inputStream().use {it.copyTo(zip)};zip.closeEntry()}
                }} ?: error("无法打开保存位置")
            }
            activity.runOnUiThread {busy=false;if(!activity.isDestroyed)toast(result.fold({"采集候选ZIP已导出，尚未校准"},{"导出失败：${it.message}"}))}
        }
    }
}
