package com.ali.cs2utility

import com.ali.cs2utility.tutorial.*
import org.junit.Assert.*
import org.junit.Test

class SpatialPrototypeTest {
    @Test fun wallBlocksButDoorConnectsBothSides() {
        val blocked=BooleanArray(5*3*5)
        fun i(x: Int,y: Int,z: Int)=(y*5+z)*5+x
        for(y in 0..2)for(z in 0..4)blocked[i(2,y,z)]=true
        val closed=SmokeGrid(5,3,5,blocked);assertEquals(-1,closed.arrivalSteps(i(0,1,2),30)[i(4,1,2)])
        blocked[i(2,1,2)]=false
        val open=SmokeGrid(5,3,5,blocked);assertEquals(4,open.arrivalSteps(i(0,1,2),30)[i(4,1,2)])
    }
    @Test fun stairsConnectLevelsButSolidFloorDoesNot() {
        val b=BooleanArray(3*3*3)
        for(z in 0..2)for(x in 0..2)b[(3+z)*3+x]=true
        assertEquals(-1,SmokeGrid(3,3,3,b).arrivalSteps(0,30)[18])
        b[9]=false;assertEquals(2,SmokeGrid(3,3,3,b).arrivalSteps(0,30)[18])
    }
    @Test fun fireDelayUsesGroundConnectivityAndLeavesOtherFloorCold() {
        val t=GroundFireSchedule.arrivalSeconds(listOf(listOf(1),listOf(0,2),listOf(1),emptyList()),0,.5)
        assertEquals(0.0,t[0]!!,0.0);assertEquals(1.0,t[2]!!,0.0);assertNull(t[3])
    }
    @Test(expected=IllegalArgumentException::class) fun oversizedSmokeGridRejected() {SmokeGrid(33,1,1,BooleanArray(33))}
}
