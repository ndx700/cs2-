package com.ali.cs2utility.tutorial

import java.util.ArrayDeque

/** Small CPU query prototype. Occupancy must come from verified collision semantics, not display layers. */
class SmokeGrid(val width: Int,val height: Int,val depth: Int,blocked: BooleanArray) {
    private val solid=blocked.copyOf()
    init {require(width in 1..32 && height in 1..32 && depth in 1..32);require(solid.size==width*height*depth)}
    fun index(x: Int,y: Int,z: Int): Int {require(x in 0 until width && y in 0 until height && z in 0 until depth);return (y*depth+z)*width+x}
    /** Distances follow connected empty cells, so a wall and a door have different reachability. */
    fun arrivalSteps(origin: Int,maxSteps: Int): IntArray {
        require(origin in solid.indices && !solid[origin] && maxSteps in 0..128)
        val result=IntArray(solid.size) {-1};val queue=ArrayDeque<Int>();result[origin]=0;queue.add(origin)
        while(queue.isNotEmpty()) {
            val i=queue.removeFirst();if(result[i]>=maxSteps)continue
            val x=i%width;val z=i/width%depth;val y=i/(width*depth)
            fun visit(nx: Int,ny: Int,nz: Int) {
                if(nx !in 0 until width || ny !in 0 until height || nz !in 0 until depth)return
                val n=(ny*depth+nz)*width+nx
                if(!solid[n] && result[n]<0) {result[n]=result[i]+1;queue.add(n)}
            }
            visit(x-1,y,z);visit(x+1,y,z);visit(x,y-1,z);visit(x,y+1,z);visit(x,y,z-1);visit(x,y,z+1)
        }
        return result
    }
}

/** Ground graph prototype; disconnected floors do not ignite merely because XY bounds overlap. */
object GroundFireSchedule {
    fun arrivalSeconds(adjacency: List<List<Int>>,origin: Int,secondsPerEdge: Double): List<Double?> {
        require(adjacency.size in 1..512 && origin in adjacency.indices)
        require(secondsPerEdge.isFinite() && secondsPerEdge>0)
        require(adjacency.all {ns->ns.size<=32 && ns.all {it in adjacency.indices}})
        val result=MutableList<Double?>(adjacency.size) {null};val q=ArrayDeque<Int>();result[origin]=0.0;q.add(origin)
        while(q.isNotEmpty()) {
            val n=q.removeFirst()
            adjacency[n].forEach {next->if(result[next]==null) {result[next]=result[n]!!+secondsPerEdge;q.add(next)}}
        }
        return result
    }
}
