package com.ali.cs2utility

import com.ali.cs2utility.tutorial.*
import org.junit.Assert.*
import org.junit.Test

class LessonObservationTest {
    private fun c(kind: String="SMOKE")=DevelopmentCourses.c014(kind)
    private fun p()=CoursePlayer().apply {load(c(),c().mapVersion)}
    @Test fun automaticStagesPresentStandAimAndFlightWithoutManualButtons() {
        val p=p();p.play();p.tick(0);assertEquals(LessonView.STANCE,p.view)
        p.tick(3000);assertEquals(LessonView.AIM,p.view)
        p.tick(6000);assertEquals(LessonView.FOLLOW,p.view)
        p.tick(10000);assertEquals(LessonView.FOLLOW,p.view)
        assertEquals(4.0,p.seconds,0.0)
    }
    @Test fun manualViewOverridesAutoWithoutResetAndCanReturnToAuto() {
        val p=p();p.play();p.tick(0);p.setView(LessonView.STANCE);p.tick(8000)
        assertEquals(LessonView.STANCE,p.view);assertEquals(2.0,p.seconds,0.0)
        p.setAutomaticCamera();assertEquals(LessonView.FOLLOW,p.view);assertEquals(2.0,p.seconds,0.0)
    }
    @Test fun replayRestartsTeachingSequenceAndRemovesLateEffects() {
        val p=p();p.jumpToEffect();p.replay();assertEquals(c().begin,p.seconds,0.0)
        assertEquals(LessonView.STANCE,p.view);assertTrue(p.automaticCamera)
        assertEquals(0f,CourseEffects.smokeAmount(c().smoke.first(),p.seconds),0f)
    }
    @Test fun jumpToEffectSelectsFollowAndUsesSamePlayerTime() {
        val p=p();p.jumpToEffect();assertEquals(c().path.last().seconds,p.seconds,0.0)
        assertEquals(LessonView.FOLLOW,p.view);assertTrue(p.playing)
        p.pause();p.tick(99999);assertEquals(c().path.last().seconds,p.seconds,0.0)
    }
    @Test fun mainFollowMovesWhileLandingTargetRemainsFixed() {
        val c=c();val a=LessonObservation.following(c,0.0);val b=LessonObservation.following(c,1.0)
        assertNotEquals(a,b);assertEquals(c.path[1].position.x,b.x,0f);assertEquals(c.path[1].position.y,b.y,0f)
        assertEquals(c.path.last().position.z,LessonObservation.following(c,10.0).z,0f)
        assertEquals(c.landingCamera,LessonObservation.landing(c))
    }
    @Test fun viewportFitsBothOrientationsAndLeavesMainCenterClear() {
        for((w,h) in listOf(1080 to 1920,1920 to 1080,400 to 800,800 to 400)) {
            val r=LessonObservation.viewport(w,h,2.75f,false)
            assertTrue(r.x>w/2);assertTrue(r.top>=0);assertTrue(r.x+r.width<=w);assertTrue(r.top+r.height<=h)
            assertTrue(r.contains((r.x+r.width/2).toFloat(),(r.top+r.height/2).toFloat()))
            assertFalse(r.contains(w/2f,h/2f))
        }
    }
    @Test fun expandedViewportAndTinySurfaceStayWithinBounds() {
        assertEquals(LessonViewport(0,0,400,800),LessonObservation.viewport(400,800,2f,true))
        val r=LessonObservation.viewport(1,1,3f,false);assertEquals(LessonViewport(0,0,1,1),r)
    }
    @Test fun heHasFormedSmokeBeforeMainGrenadeReleaseAndRecoversLocalHole() {
        val c=c("HE");val s=c.smoke.first();val h=c.he.first()
        assertEquals(1f,CourseEffects.smokeAmount(s,0.0),0f)
        assertEquals(c.path.last().seconds,h.start,0.0)
        assertEquals(0f,CourseEffects.density(c,s,h.center,h.open+.1),0f)
        assertTrue(CourseEffects.density(c,s,h.center,h.end+.1)>0)
    }
    @Test fun restoreKeepsAutomaticOrManualModeWithTheClock() {
        val p=p();p.jumpToEffect();p.setView(LessonView.AIM)
        val q=p();q.restore(p.snapshot());assertEquals(p.snapshot(),q.snapshot());assertFalse(q.automaticCamera)
    }
    @Test(expected=IllegalArgumentException::class) fun calibratedCourseMustSupplyFollowAndLandingCameras() {
        val c=c();c.copy(evidence=c.evidence.copy(calibrated=true),followCamera=null).validate(c.mapVersion)
    }
}
