package com.ali.cs2utility

import com.ali.cs2utility.tutorial.*
import com.ali.cs2utility.domain.Vec3
import org.junit.Assert.*
import org.junit.Test

class CourseTest {
    private fun course()=DevelopmentCourses.make()
    private fun player()=CoursePlayer().apply {load(course(),DevelopmentCourses.MAP_VERSION)}
    @Test fun clockFreezesOnPauseAndResumesWithoutCatchup() {
        val p=player();p.play();p.tick(1000);p.tick(3000);assertEquals(-4.0,p.seconds,0.0)
        p.pause();p.tick(100000);assertEquals(-4.0,p.seconds,0.0)
        p.play();p.tick(101000);p.tick(102000);assertEquals(-3.0,p.seconds,0.0)
    }
    @Test fun lifecycleSuspendPreservesIntentWithoutBackgroundTime() {
        val p=player();p.play();p.tick(0);p.tick(1000);p.suspend();p.tick(500000)
        assertEquals(-5.0,p.seconds,0.0);assertTrue(p.playing)
        p.resume();p.tick(600000);p.tick(601000);assertEquals(-4.0,p.seconds,0.0)
    }
    @Test fun cameraSwitchOnlyChangesView() {
        val p=player();p.seek(8.0);p.play();p.setView(LessonView.AIM)
        assertEquals(8.0,p.seconds,0.0);assertTrue(p.playing);assertEquals(LessonView.AIM,p.view)
    }
    @Test fun replayRemovesHoleAndRewindsDelayedFire() {
        val p=player();p.seek(8.0);assertEquals(1f,CourseEffects.holeAmount(course().he.first(),p.seconds),.3f)
        p.replay();assertEquals(course().begin,p.seconds,0.0)
        assertEquals(0f,CourseEffects.holeAmount(course().he.first(),p.seconds),0f)
        assertEquals(0f,CourseEffects.fireAmount(DevelopmentCourses.make(true).fire.last(),p.seconds),0f)
    }
    @Test fun holeIsLocalAndRefillsWithoutDeletingWholeSmoke() {
        val c=course();val s=c.smoke.first();val h=c.he.first()
        val before=CourseEffects.density(c,s,h.center,6.0)
        assertTrue(before>0);assertEquals(0f,CourseEffects.density(c,s,h.center,7.5),0f)
        assertEquals(before,CourseEffects.density(c,s,h.center,10.0),0f)
        assertTrue(CourseEffects.density(c,s,Vec3(s.center.x-1.8f,s.center.y,s.center.z),7.5)>0)
    }
    @Test fun fireLaterZoneStartsLaterAndAllZonesExtinguish() {
        val c=DevelopmentCourses.make(true)
        assertTrue(CourseEffects.fireAmount(c.fire.first(),3.0)>0)
        assertEquals(0f,CourseEffects.fireAmount(c.fire.last(),3.0),0f)
        assertTrue(CourseEffects.fireAmount(c.fire.last(),6.5)>0)
        assertTrue(c.fire.all {CourseEffects.fireAmount(it,16.0)==0f})
    }
    @Test fun smokeGrowsStaysAndFades() {
        val s=course().smoke.first()
        assertEquals(0f,CourseEffects.smokeAmount(s,1.0),0f)
        assertTrue(CourseEffects.smokeAmount(s,3.0) in .01f.. .99f)
        assertEquals(1f,CourseEffects.smokeAmount(s,6.0),0f)
        assertTrue(CourseEffects.smokeAmount(s,14.0) in .01f.. .99f)
        assertEquals(0f,CourseEffects.smokeAmount(s,16.0),0f)
    }
    @Test fun snapshotsRestoreDeterministicallyAndStopAtEnd() {
        val p=player();p.seek(7.6);p.setView(LessonView.OVERVIEW);p.play()
        val q=player();q.restore(p.snapshot());assertEquals(p.snapshot(),q.snapshot())
        q.tick(0);q.tick(300000);assertEquals(16.0,q.seconds,0.0);assertFalse(q.playing)
    }
    @Test fun trajectoryHitsExactNodesAndStopsAfterLanding() {
        val c=course();assertEquals(c.path[1].position,c.grenade(1.0))
        assertEquals(c.path.last().position,c.grenade(2.0));assertNull(c.grenade(2.1))
    }
    @Test(expected=IllegalArgumentException::class) fun mismatchedMapRejected() {course().validate("old-map")}
    @Test(expected=IllegalArgumentException::class) fun nanTimeRejected() {player().seek(Double.NaN)}
    @Test(expected=IllegalArgumentException::class) fun unknownSmokeReferenceRejected() {
        val c=course();c.copy(he=listOf(c.he.first().copy(smokeIds=setOf("missing")))).validate(c.mapVersion)
    }
    @Test(expected=IllegalArgumentException::class) fun unboundedZonesRejected() {
        val c=DevelopmentCourses.make(true);c.copy(fire=(0..64).map {c.fire.first().copy(id="fire-$it")}).validate(c.mapVersion)
    }
    @Test(expected=IllegalArgumentException::class) fun unprovenVerifiedCourseRejected() {
        val c=course();c.copy(evidence=c.evidence.copy(calibrated=true)).validate(c.mapVersion)
    }
}
