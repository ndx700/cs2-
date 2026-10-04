package com.ali.cs2utility

import com.ali.cs2utility.domain.Vec3
import com.ali.cs2utility.tutorial.*
import org.junit.Assert.*
import org.junit.Test

class AimGuidanceTest {
    private fun player()=CoursePlayer().apply {load(DevelopmentCourses.make(),DevelopmentCourses.MAP_VERSION);setView(LessonView.AIM)}
    private fun matrix()=FloatArray(16) {if(it%5==0)1f else 0f}
    @Test fun fadesOnCourseClockButFreezesOnPauseAndBackground() {
        val p=player();p.play();p.tick(0);p.tick(2000);assertEquals(.5f,p.aimHintAlpha(),0f)
        p.pause();p.tick(100000);assertEquals(.5f,p.aimHintAlpha(),0f)
        p.play();p.suspend();p.tick(200000);assertEquals(.5f,p.aimHintAlpha(),0f)
        p.resume();p.tick(300000);p.tick(300500);assertEquals(0f,p.aimHintAlpha(),0f)
    }
    @Test fun hidingDoesNotChangeClockOrReappearOnReplay() {
        val p=player();p.seek(8.0);p.setAimHint(false)
        assertEquals(8.0,p.seconds,0.0);assertEquals(0f,p.aimHintAlpha(),0f)
        p.replay();assertEquals(0f,p.aimHintAlpha(),0f)
        p.setView(LessonView.OVERVIEW);p.setView(LessonView.AIM);assertEquals(0f,p.aimHintAlpha(),0f)
        p.setAimHint(true);assertEquals(1f,p.aimHintAlpha(),0f)
    }
    @Test fun recreationPreservesHiddenAndPartlyFadedState() {
        val p=player();p.seek(-4.0)
        val q=player();q.restore(p.snapshot());assertEquals(p.snapshot(),q.snapshot());assertEquals(.5f,q.aimHintAlpha(),0f)
        p.setAimHint(false);q.restore(p.snapshot());assertEquals(0f,q.aimHintAlpha(),0f)
    }
    @Test fun changingViewDoesNotRestartFadedHint() {
        val p=player();p.seek(1.0);p.setView(LessonView.OVERVIEW);p.setView(LessonView.AIM)
        assertEquals(1.0,p.seconds,0.0);assertEquals(0f,p.aimHintAlpha(),0f)
    }
    @Test fun projectExactWorldPointAndDifferentAspectRatios() {
        val m=matrix();val a=AimGuidance.project("lesson",Vec3(0f,0f,0f),m,1000,500,1f)!!
        assertEquals(500f,a.x,0f);assertEquals(250f,a.y,0f)
        val b=AimGuidance.project("lesson",Vec3(.5f,.5f,0f),m,400,800,1f)!!
        assertEquals(300f,b.x,0f);assertEquals(200f,b.y,0f)
    }
    @Test fun hiddenOffscreenAndBehindCameraDoNotProject() {
        assertNull(AimGuidance.project("lesson",Vec3(0f,0f,0f),matrix(),400,800,0f))
        assertNull(AimGuidance.project("lesson",Vec3(2f,0f,0f),matrix(),400,800,1f))
        val m=matrix();m[15]=-1f
        assertNull(AimGuidance.project("lesson",Vec3(0f,0f,0f),m,400,800,1f))
    }
}
