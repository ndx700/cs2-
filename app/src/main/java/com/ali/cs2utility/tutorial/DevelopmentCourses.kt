package com.ali.cs2utility.tutorial

import com.ali.cs2utility.domain.Vec3
import com.ali.cs2utility.scene.CameraState
import kotlin.math.*

/** Synthetic bench above the T anchor; deliberately NOT a calibrated D2-001/014 course. */
object DevelopmentCourses {
    const val MAP_VERSION="fc60c032290dffcb233e3de9da3c59694293362ec72643cc1ebde9c1b13a17d0"
    fun c014(kind: String): Course {
        require(kind in listOf("SMOKE","FIRE","HE"))
        val base=make(kind=="FIRE")
        val id=when(kind){"SMOKE"->"D2-001";"FIRE"->"D2-010";else->"D2-014"}
        val title=when(kind){"SMOKE"->"中门烟";"FIRE"->"Car 火";else->"中门炸烟 HE"}
        val c=base.copy(lessonId="DEV-C014-$kind",relatedIds=listOf(id),title="$id $title · 联调未校准",
            followCamera=base.overviewCamera.copy(pitch=30f,distance=7f),
            landingCamera=base.overviewCamera.copy(pitch=38f,distance=9f,y=base.foot.y+.8f),
            he=if(kind=="HE")base.he.map {it.copy(start=2.0,open=2.2,refill=2.8,end=4.5)}else emptyList(),
            smoke=if(kind=="HE")base.smoke.map {it.copy(start=-4.0,formed=-2.0,fade=10.0,end=14.0)}else base.smoke,
            path=if(kind=="HE")base.path.dropLast(1)+base.path.last().copy(position=base.he.first().center)else base.path)
        c.validate(MAP_VERSION);return c
    }
    fun make(fire: Boolean=false): Course {
        val foot=Vec3(-20.888075f,7.828009f,20.209309f)
        val eye=Vec3(foot.x,foot.y+1.65f,foot.z)
        val center=Vec3(foot.x,foot.y+.8f,foot.z-5)
        val aim=Vec3(foot.x,eye.y+1,foot.z-9)
        val pitch=(-atan2((aim.y-eye.y).toDouble(),9.0)*180/PI).toFloat()
        return Course(if(fire)"DEV-C013-FIRE" else "DEV-C013-SMOKE-HE",if(fire)listOf("D2-009","D2-010")else listOf("D2-001","D2-014"),
            if(fire)"开发样例：分区延迟着火" else "开发样例：烟成型 → HE局部缺口 → 回填",
            MAP_VERSION,"display-m-y-up-v1",SourceEvidence(null,null,null,false),foot,eye,aim,0f,
            "开发预设投掷 · 实战投法待校准",
            CameraState(35f,24f,8f,foot.x,foot.z-2,foot.y+.8f),
            CameraState(0f,pitch,1f,eye.x,eye.z,eye.y,true),
            CameraState(20f,65f,14f,center.x,center.z,foot.y),
            listOf(PathKey(0.0,eye),PathKey(1.0,Vec3(foot.x,foot.y+4,foot.z-3)),PathKey(2.0,center,true)),
            if(fire)emptyList()else listOf(SmokeEvent("smoke-dev",center,Vec3(2.8f,1.5f,2.2f),2.0,4.0,12.0,16.0)),
            if(fire)(0..8).map {i->FireZone("fire-$i",Vec3(center.x+(i%3-1)*1.2f,foot.y,center.z+(i/3-1)*1.2f),.7f,2.0+i*.45,10.0+i*.1,13.0+i*.1)}else emptyList(),
            if(fire)emptyList()else listOf(HeEvent("he-dev",Vec3(center.x+.8f,center.y,center.z),1.8f,7.0,7.2,7.8,9.5,setOf("smoke-dev"))),duration=16.0)
    }
}
