package com.ali.cs2utility.tutorial

import com.ali.cs2utility.domain.Vec3
import java.nio.FloatBuffer
import kotlin.math.*

/** Exact CPU mesh consumed by GLES; deterministic at a course time and bounded to 1024 billboards. */
object CourseFxGeometry {
    const val CAPACITY=1024*6*9
    private data class Puff(val p: Vec3,val r: Float,val red: Float,val green: Float,val blue: Float,val alpha: Float)
    fun fill(c: Course,t: Double,eye: FloatArray,view: FloatArray,data: FloatBuffer): Int {
        require(eye.size==3 && view.size==16 && data.capacity()>=CAPACITY)
        val puffs=ArrayList<Puff>(1024)
        c.smoke.forEach {s->
            val a=CourseEffects.smokeAmount(s,t);val growth=sqrt(a)
            if(a>0)for(i in 0 until 96) {
                // Fixed lattice with time-varying micro-motion; replay/seek are deterministic.
                val u=(i%6-2.5f)/3;val v=(i/6%4-1.5f)/2;val w=(i/24-1.5f)/2
                if(u*u+v*v+w*w>1)continue
                val p=Vec3(s.center.x+(u+.03f*sin((t+i)*2).toFloat())*s.radius.x*growth,
                    s.center.y+v*s.radius.y*growth,s.center.z+w*s.radius.z*growth)
                val density=CourseEffects.density(c,s,p,t)
                if(density>.001f)puffs.add(Puff(p,.75f*growth,.62f,.67f,.7f,density*.7f))
            }
        }
        c.fire.forEachIndexed {i,f->
            val a=CourseEffects.fireAmount(f,t)
            if(a>0)for(j in 0..5) {
                val angle=j*PI/3;val flicker=.7f+.3f*sin((t*13+i*3+j)).toFloat()
                puffs.add(Puff(Vec3(f.center.x+cos(angle).toFloat()*f.radius*.45f,
                    f.center.y+.15f+j*.08f*flicker,f.center.z+sin(angle).toFloat()*f.radius*.45f),
                    f.radius*(.65f+.3f*flicker),1f,.22f+j*.08f,.025f,a*.9f))
            }
        }
        c.he.forEach {h->
            val age=t-h.start
            if(age in 0.0..1.2)for(i in 0..31) {
                val angle=i*2.399963;val radius=h.radius*(age.toFloat()+.1f)
                val p=Vec3(h.center.x+cos(angle).toFloat()*radius,h.center.y+sin(i*1.31).toFloat()*radius*.5f,h.center.z+sin(angle).toFloat()*radius)
                val flash=age<.15
                puffs.add(Puff(p,if(flash).7f else .4f+age.toFloat()*.4f,if(flash)1f else .45f,
                    if(flash).7f else .4f,if(flash).2f else .35f,(1-age/1.2).toFloat()*.8f))
            }
        }
        c.grenade(t)?.let {puffs.add(Puff(it,.12f,.4f,.9f,.5f,1f))}
        if(puffs.isEmpty()){data.clear();data.flip();return 0}
        check(puffs.size<=1024)
        fun distance(p: Vec3)=(p.x-eye[0]).pow(2)+(p.y-eye[1]).pow(2)+(p.z-eye[2]).pow(2)
        data.clear()
        val corners=floatArrayOf(-1f,-1f,1f,-1f,1f,1f,-1f,-1f,1f,1f,-1f,1f)
        puffs.sortedByDescending {distance(it.p)}.forEach {p->
            for(i in 0 until 6) {
                val u=corners[i*2];val v=corners[i*2+1]
                data.put(p.p.x+p.r*(view[0]*u+view[1]*v))
                data.put(p.p.y+p.r*(view[4]*u+view[5]*v))
                data.put(p.p.z+p.r*(view[8]*u+view[9]*v))
                data.put(p.red);data.put(p.green);data.put(p.blue);data.put(p.alpha);data.put(u);data.put(v)
            }
        }
        data.flip()
        return puffs.size*6
    }
}
