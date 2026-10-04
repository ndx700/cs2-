package com.ali.cs2utility.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import com.ali.cs2utility.data.*
import com.ali.cs2utility.domain.*
import com.ali.cs2utility.domain.Filter
import com.ali.cs2utility.presentation.ExplorerState
import com.ali.cs2utility.scene.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private data class LoadedMap(val definition: SceneDefinition,val vertices: FloatArray,val lineups: List<Lineup>,val detailed: TexturedMesh?,val mobile: MobileScene?)
    private lateinit var repository: AssetRepository
    private lateinit var store: PreferenceStore
    private lateinit var state: ExplorerState
    private lateinit var scene: MapSceneView
    private lateinit var details: LinearLayout
    private lateinit var detailsScroll: ScrollView
    private lateinit var title: TextView
    private lateinit var sceneHint: TextView
    private lateinit var gestureHint: TextView
    private lateinit var runButton: Button
    private lateinit var search: EditText
    private lateinit var types: LinearLayout
    private val loader=Executors.newSingleThreadExecutor()
    private var requestToken=0
    private var loaded=false
    private var sceneError: String?=null
    private var restored: Bundle?=null
    private var definition: SceneDefinition?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restored=savedInstanceState
        repository=AssetRepository(this); store=PreferenceStore(this)
        try {
            state=ExplorerState(repository.maps())
            state.favorites=store.favorites
            state.selectedGroupId=savedInstanceState?.getString("group")
            state.filter=Filter(savedInstanceState?.getString("query") ?: "",
                savedInstanceState?.getString("type")?.let { UtilityType.valueOf(it) },
                savedInstanceState?.getBoolean("favoritesOnly") ?: false)
            val id=savedInstanceState?.getString("map") ?: store.mapId
            state.map=state.maps.firstOrNull { it.id==id } ?: state.maps.first()
            buildUi()
            openMap(state.map)
        } catch (e: Exception) {
            setContentView(column().apply {
                setPadding(dp(24),dp(48),dp(24),dp(24))
                addView(text("无法加载项目数据",24f,bold=true))
                addView(text(e.message ?: "未知错误",14f,Palette.muted))
                addView(button("重新加载") { recreate() })
            })
        }
    }
    private fun buildUi() {
        val root=column().apply { setBackgroundColor(Palette.bg); fitsSystemWindows=true }
        val header=column().apply { setPadding(dp(16),dp(8),dp(16),dp(6)) }
        val top=row()
        top.addWeighted(column().apply {
            addView(text("UTILITY LAB  /  CS2",11f,Palette.accent,true))
            title=text(state.map.name,26f,bold=true); addView(title)
        })
        top.addView(button("地图 ▾") {
            AlertDialog.Builder(this).setTitle("选择地图")
                .setItems(state.maps.map { it.name }.toTypedArray()) { _,i ->
                    state.select(null);state.selectedGroupId=null; store.selectedId=null; restored=null; openMap(state.maps[i])
                }.show()
        })
        header.addView(top)
        search=EditText(this).apply {
            hint="搜索站位、落点或区域"; textSize=14f; setSingleLine(true)
            setTextColor(Palette.text); setHintTextColor(Palette.muted)
            background=surface(Palette.card,dp(12).toFloat()); setPadding(dp(14),0,dp(14),0)
            contentDescription="搜索道具点位"; setText(state.filter.query)
        }
        header.addView(search,LinearLayout.LayoutParams(-1,dp(48)))
        types=row()
        header.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(types) })
        root.addView(header)
        val wide=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE || resources.configuration.smallestScreenWidthDp>=600
        val body=LinearLayout(this).apply { orientation=if(wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        val mapPanel=column()
        val frame=android.widget.FrameLayout(this)
        scene=MapSceneView(this) { id ->
            if(id.startsWith("target:")) openGroup(id.removePrefix("target:"))
            else state.lineups.firstOrNull { it.id==id }?.let { select(id);playVideo(it) }
        }
        scene.onStatus={message,error ->
            if(!isFinishing && !isDestroyed) {
                sceneHint.text=message
                if(error) {sceneError=message;loaded=false;showLoadError()}
            }
        }
        frame.addView(scene,android.widget.FrameLayout.LayoutParams(-1,-1))
        sceneHint=text("3D 地图 · 加载中",11f,Palette.accent,true).apply {
            maxWidth=(resources.displayMetrics.widthPixels-dp(135)).coerceAtLeast(dp(120))
            setPadding(dp(10),dp(5),dp(10),dp(5)); background=surface(0xdd10151d.toInt(),dp(8).toFloat())
        }
        frame.addView(sceneHint,android.widget.FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START).apply { leftMargin=dp(12); topMargin=dp(10) })
        gestureHint=text("单指旋转 · 双指缩放/拖动",11f,Palette.muted).apply {
            setPadding(dp(10),dp(5),dp(10),dp(5)); background=surface(0xdd10151d.toInt())
        }
        frame.addView(gestureHint,android.widget.FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin=dp(6) })
        mapPanel.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        mapPanel.addView(row().apply {
            setPadding(dp(12),dp(4),dp(6),dp(4))
            addWeighted(button("全图") { state.select(null);state.selectedGroupId=null;store.selectedId=null;refresh();scene.resetCamera() })
            runButton=button("自由跑图") {scene.toggleFreeMode()};addWeighted(runButton)
            scene.onModeChanged={free ->
                runButton.text=if(free)"退出跑图" else "自由跑图";runButton.contentDescription=runButton.text
                gestureHint.text=if(free)"左侧移动 · 空白处转头 · 右侧升降" else "单指旋转 · 双指缩放/拖动"
            }
            addWeighted(button("道具点位") { openFirstGroup() })
            addWeighted(button("跳到区域") {
                val labels=definition?.labels.orEmpty()
                if(labels.isNotEmpty()) {
                    AlertDialog.Builder(this@MainActivity).setTitle("浏览区域")
                        .setItems(labels.map {it.name}.toTypedArray()) { _,i ->
                            val p=labels[i].position;scene.flyTo(CameraState(38f,55f,35f,p.x,p.z,p.y))
                        }.show()
                    return@button
                }
                state.select(null);state.selectedGroupId=null;store.selectedId=null;refresh()
                definition?.targets?.firstOrNull()?.let { t -> scene.flyTo(CameraState(t.yaw,t.pitch,t.distance,t.position.x,t.position.z,t.position.y)) }
                    ?: Toast.makeText(this@MainActivity,"目标区域待接入，可先旋转和缩放浏览全图",Toast.LENGTH_SHORT).show()
            })
        })
        val side=column()
        details=column().apply { setPadding(dp(16),dp(6),dp(16),dp(16)) }
        detailsScroll=ScrollView(this).apply { isFillViewport=true; addView(details) }
        side.addView(detailsScroll,LinearLayout.LayoutParams(-1,-1))
        if(wide) {
            body.addView(mapPanel,LinearLayout.LayoutParams(0,-1,1.3f)); body.addView(side,LinearLayout.LayoutParams(0,-1,1f))
        } else {
            body.addView(mapPanel,LinearLayout.LayoutParams(-1,0,1.8f)); body.addView(side,LinearLayout.LayoutParams(-1,0,.65f))
        }
        root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        updateChips()
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) {
                state.filter=state.filter.copy(query=s.toString()); filterChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    private fun notifySelect() { Toast.makeText(this,"请先点击地图数字或下方道具卡片",Toast.LENGTH_SHORT).show() }
    private fun openMap(map: MapDefinition) {
        val token=++requestToken
        loaded=false; sceneError=null; definition=null; state.map=map; state.lineups=emptyList(); state.select(null)
        store.mapId=map.id; title.text=map.name; sceneHint.text="3D 地图加载中…"
        scene.show(emptyList(),null); scene.load(SceneDefinition(28f,emptyList(),emptyList(),null,true),floatArrayOf())
        details.removeAllViews(); details.addView(text("正在加载地图与点位…",14f,Palette.muted))
        loader.execute {
            val result=runCatching {
                val definition=repository.scene(map)
                val mesh=Geometry.boxes(definition) + (definition.objAsset?.let { path ->
                    assets.open(path).reader().use { Geometry.obj(it) }
                } ?: floatArrayOf())
                val mobile=definition.meshAsset?.takeIf { it.endsWith("manifest.json") }?.let { MobileSceneLoader.load(this,it) }
                val detailed=if(mobile!=null)null else definition.meshAsset?.let {assets.open(it).use(TexturedMeshLoader::load)}
                LoadedMap(definition,mesh,repository.lineups(map),detailed,mobile)
            }
            runOnUiThread {
                if(isFinishing || isDestroyed || token!=requestToken) return@runOnUiThread
                result.fold({ (definition,mesh,items,detailed,mobile) ->
                    this.definition=definition
                    state.lineups=items; scene.load(definition,mesh,detailed,mobile)
                    state.select(restored?.getString("selected") ?: store.selectedId)
                    state.reconcileSelection()
                    sceneHint.text=definition.displayLabel.ifBlank { if(definition.schematic) "${map.name} 概念示意 · 非实测坐标" else "3D 地图 · ${map.name}" }
                    val savedCamera=restored?.getFloatArray("camera")?.takeIf { it.size>=5 }
                    savedCamera?.let { c ->
                        scene.restoreCamera(CameraState(c[0],c[1],c[2],c[3],c[4],c.getOrElse(5) { 0f },restored?.getBoolean("freeCamera") ?: false,restored?.getFloat("runSpeed",6f) ?: 6f))
                    }
                    restored=null; loaded=true; refresh()
                    if(savedCamera==null) definition.targets.firstOrNull { it.id==state.selectedGroupId }?.let { t ->
                        scene.flyTo(CameraState(t.yaw,t.pitch,t.distance,t.focus.x,t.focus.z,t.focus.y))
                    }
                }, { error ->
                    sceneError=error.message ?: "未知错误"
                    sceneHint.text="地图加载失败"; showLoadError()
                })
            }
        }
    }
    private fun showLoadError() {
        details.removeAllViews()
        details.addView(text("地图数据暂不可用",20f,bold=true))
        details.addView(text(sceneError ?: "未知错误",14f,Palette.muted))
        details.addView(button("重试") { openMap(state.map) })
    }
    private fun updateChips() {
        types.removeAllViews()
        fun chip(name: String, selected: Boolean, action: () -> Unit) {
            types.addView(button(name,selected,action),LinearLayout.LayoutParams(-2,dp(48)).apply { marginEnd=dp(6); topMargin=dp(6) })
        }
        chip("全部",state.filter.type==null) { state.filter=state.filter.copy(type=null); filterChanged() }
        UtilityType.entries.forEach { type -> chip(type.label,state.filter.type==type) { state.filter=state.filter.copy(type=type); filterChanged() } }
        chip("★ 收藏",state.filter.favoritesOnly) { state.filter=state.filter.copy(favoritesOnly=!state.filter.favoritesOnly); filterChanged() }
    }
    private fun filterChanged() {
        state.reconcileSelection(); store.selectedId=state.selectedId; updateChips()
        if(loaded) refresh()
    }
    private fun select(id: String) {
        state.select(id); store.selectedId=state.selectedId
        refresh(); detailsScroll.smoothScrollTo(0,0)
    }
    private fun refresh() {
        val group=state.selectedGroup
        scene.targets(if(group==null) definition?.targets?.filter { t -> state.groups.any { it.id==t.id } } ?: emptyList() else emptyList())
        scene.show(group?.items ?: emptyList(),state.selected)
        details.removeAllViews()
        state.selected?.let { showDetail(it); return }
        state.selectedGroup?.let { showGroup(it);return }
        if(state.lineups.isEmpty()) {
            details.addView(text("${state.map.name} · 地图浏览",20f,bold=true))
            details.addView(text(definition?.overviewText.orEmpty().ifBlank { state.map.description },14f,Palette.muted))
            details.addView(text("道具教学待接入",18f,bold=true))
            details.addView(text(definition?.emptyLineupsText.orEmpty().ifBlank { "这张地图暂未加入道具教学。可先单指旋转、双指缩放浏览地图。" },13f,Palette.muted))
            definition?.credits?.takeIf { it.isNotBlank() }?.let { details.addView(text(it,11f,Palette.muted)) }
            return
        }
        val groups=state.groups
        details.addView(row().apply {
            addWeighted(text("先选目标",20f,bold=true)); addView(text("${groups.size} 组",12f,Palette.muted))
        })
        details.addView(text("点选地图目标或下方卡片，再选站位查看瞄点和视频",12f,Palette.muted))
        definition?.credits?.takeIf { it.isNotBlank() }?.let { details.addView(text(it,11f,Palette.muted)) }
        if(groups.isEmpty()) {
            details.addView(text("没有匹配点位",18f,bold=true))
            details.addView(button("清除筛选") {
                state.filter=Filter(); search.setText(""); filterChanged()
            })
        }
        groups.forEach { group ->
            val lineup=group.items.first()
            val card=column().apply {
                background=surface(Palette.card,dp(14).toFloat()); setPadding(dp(14),dp(10),dp(14),dp(10))
                isFocusable=true; contentDescription="${group.title}，${group.items.size} 个出生位"
                addView(text("${lineup.type.label}  ·  ${lineup.side}  ·  ${lineup.area}",11f,lineup.type.color,true))
                addView(text(group.title,22f,bold=true))
                addView(text("${group.items.size} 个出生位置 · 每个位置独立示例",13f,Palette.muted))
                addView(text("点击展开出生位 →",13f,Palette.accent,true))
                setOnClickListener { openGroup(group.id) }
            }
            details.addView(card,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        }
    }
    private fun openFirstGroup() {
        state.groups.firstOrNull()?.let { openGroup(it.id) }
            ?: Toast.makeText(this,if(state.lineups.isEmpty()) "这张地图的道具教学待接入" else "当前筛选下没有匹配点位，请清除筛选",Toast.LENGTH_SHORT).show()
    }
    private fun openGroup(id: String) {
        val group=state.groups.firstOrNull { it.id==id }
        if(group==null) { Toast.makeText(this,"当前筛选下没有匹配点位，请清除筛选",Toast.LENGTH_SHORT).show();return }
        state.select(null);state.selectedGroupId=group.id;store.selectedId=null;refresh();detailsScroll.smoothScrollTo(0,0)
        definition?.targets?.firstOrNull { it.id==group.id }?.let { t ->
            scene.flyTo(CameraState(t.yaw,t.pitch,t.distance,t.focus.x,t.focus.z,t.focus.y))
        }
    }
    private fun playVideo(item: Lineup) {
        startActivity(Intent(this,VideoActivity::class.java).apply {
            putExtra("title",item.title);putExtra("kind",item.video.kind.name)
            putExtra("url",item.video.url);putExtra("attribution",item.video.attribution);putExtra("source",item.source)
            putExtra("autoplay",true)
            putExtra("bvid",item.video.bvid);putExtra("cid",item.video.cid)
            putExtra("startSeconds",item.video.startSeconds);putExtra("endSeconds",item.video.endSeconds)
            putExtra("aim",item.aim);putExtra("throwMode",item.throwMode);putExtra("aimImage",item.aimImage)
            item.aimCrop?.let { c -> putExtra("aimCrop",intArrayOf(c.x,c.y,c.width,c.height)) }
        })
    }
    private fun showGroup(group: LineupGroup) {
        details.addView(button("‹ 返回道具目标") { state.selectedGroupId=null;refresh();scene.resetCamera() })
        details.addView(text(group.title,23f,bold=true))
        details.addView(text("点击 3D 地图里的编号站位，打开对应瞄点与教学视频。",13f,Palette.accent))
        val image=group.items.first().spawnImage
        if(image.isNotBlank()) details.addView(TutorialImageView(this,image),LinearLayout.LayoutParams(-1,-2))
        details.addView(text("三维站位与瞄点以条目的来源教程和校验状态为准。",11f,Palette.muted))
        group.items.forEach { item ->
            val line=row().apply {
                addWeighted(button("▶ ${item.spawnNumber} 号位示例",true) { select(item.id);playVideo(item) })
                addView(button("瞄点 / 步骤") { select(item.id) })
            }
            details.addView(line,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        }
        details.addView(text("模型内可旋转、缩放；每个位置的视频从对应片段开始播放。",11f,Palette.muted))
    }
    private fun showDetail(item: Lineup) {
        details.addView(row().apply {
            addWeighted(button("‹ 其他出生位") { state.select(null); store.selectedId=null; refresh() })
            addWeighted(button(if(item.id in state.favorites) "★ 已收藏" else "☆ 收藏") {
                state.toggleFavorite(item.id); store.favorites=state.favorites; state.reconcileSelection(); refresh()
            })
        })
        details.addView(text(item.title,23f,bold=true))
        details.addView(text("${item.type.label} · ${item.side} · ${item.status.label}",12f,item.type.color,true))
        if(item.status==Verification.DEMO) {
            details.addView(text("此条目用于演示流程，站位、瞄点与曲线尚未实测。请勿用于比赛复现。",13f,Palette.accent))
        } else if(item.status==Verification.UNVERIFIED) {
            details.addView(text("已接入来源教程；请按对应出生位练习并自行复测。",13f,Palette.accent))
        }
        details.addView(text("站位  ${item.standName}\n落点  ${item.landingName}",16f,bold=true))
        details.addView(text("瞄点：${item.aim}\n投掷：${item.throwMode}",14f,Palette.muted))
        if(item.aimImage.isNotBlank()) details.addView(TutorialImageView(this,item.aimImage,crop=item.aimCrop),LinearLayout.LayoutParams(-1,-2))
        if(item.practiceCommand.isNotBlank()) {
            details.addView(button("复制练习坐标") {
                val clipboard=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("CS2 practice",item.practiceCommand))
                Toast.makeText(this,"已复制 setpos / setang，仅用于练习服",Toast.LENGTH_SHORT).show()
            })
        }
        item.steps.forEachIndexed { i,step -> details.addView(text("${i+1}. $step",14f)) }
        details.addView(button(if(item.video.kind==VideoKind.DEMO) "▶ 播放流程样片" else "▶ 播放教学视频",true) {
            playVideo(item)
        },LinearLayout.LayoutParams(-1,dp(50)).apply { topMargin=dp(12); bottomMargin=dp(8) })
        details.addView(text("素材：${item.video.attribution}\n来源：${item.source.ifBlank { "待录制与核验" }}\n游戏版本：${item.gameBuild.ifBlank { "未登记" }} · 校验：${item.verifiedAt.ifBlank { "尚未校验" }}",11f,Palette.muted))
        details.addView(button("查看原教程页面") {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(item.source))) }
                .onFailure { Toast.makeText(this,"未找到浏览器",Toast.LENGTH_SHORT).show() }
        })
    }
    override fun onResume() { super.onResume(); if(::scene.isInitialized) scene.resume() }
    override fun onPause() { if(::scene.isInitialized) scene.pause(); super.onPause() }
    override fun onSaveInstanceState(out: Bundle) {
        if(::state.isInitialized && ::scene.isInitialized && ::title.isInitialized) {
            out.putString("map",state.map.id); out.putString("selected",state.selectedId)
            out.putString("group",state.selectedGroupId)
            out.putString("query",state.filter.query); out.putString("type",state.filter.type?.name)
            out.putBoolean("favoritesOnly",state.filter.favoritesOnly)
            val c=scene.cameraState();out.putBoolean("freeCamera",c.free);out.putFloat("runSpeed",c.speed); out.putFloatArray("camera",floatArrayOf(c.yaw,c.pitch,c.distance,c.x,c.z,c.y))
        }
        super.onSaveInstanceState(out)
    }
    @Deprecated("Legacy Activity back handling for API 24+ skeleton")
    override fun onBackPressed() {
        if(::state.isInitialized && state.selected!=null && ::details.isInitialized) {
            state.select(null); store.selectedId=null; refresh()
        } else if(::state.isInitialized && state.selectedGroupId!=null && ::details.isInitialized) {
            state.selectedGroupId=null;refresh();scene.resetCamera()
        } else super.onBackPressed()
    }
    override fun onDestroy() { requestToken++; loader.shutdownNow();if(::scene.isInitialized)scene.dispose();super.onDestroy() }
}
