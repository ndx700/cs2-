#!/usr/bin/env python3
"""Offline counterpart of the APP display-ray capture, not a CS2 calibration solver."""
import gzip
import hashlib
import math
import struct
from pathlib import Path
import numpy as np

def camera_frame(camera, width, height, extent):
    position=np.asarray(camera['position'],dtype=float)
    yaw,pitch=map(math.radians,(camera['yaw'],camera['pitch']))
    distance=float(camera['distance']);free=camera['free']
    assert position.shape==(3,) and np.isfinite(position).all() and np.max(np.abs(position))<=1000
    assert isinstance(free,bool) and all(math.isfinite(v) for v in (yaw,pitch,distance)) and distance>=1
    assert ( -85 if free else 20)<=camera['pitch']<=85 and width>0 and height>0
    forward=np.array([-math.sin(yaw)*math.cos(pitch),-math.sin(pitch),-math.cos(yaw)*math.cos(pitch)])
    eye=position if free else position-distance*forward
    fov=70 if free else 45;near=.05 if free else .1;far=extent*10
    right=np.cross(forward,[0,1,0]);right/=np.linalg.norm(right);up=np.cross(right,forward)
    view=np.eye(4);view[:3,:3]=[right,up,-forward];view[:3,3]=-view[:3,:3]@eye
    t=1/math.tan(math.radians(fov)/2)
    projection=np.array([[t/(width/height),0,0,0],[0,t,0,0],[0,0,(far+near)/(near-far),2*far*near/(near-far)],[0,0,-1,0]])
    return eye,fov,projection@view

def ray_at(eye,vp,width,height,x,y):
    assert 0<=x<=width and 0<=y<=height
    inv=np.linalg.inv(vp)
    a=inv@np.array([2*x/width-1,1-2*y/height,-1,1]);b=inv@np.array([2*x/width-1,1-2*y/height,1,1])
    direction=b[:3]/b[3]-a[:3]/a[3];direction/=np.linalg.norm(direction)
    return np.asarray(eye,dtype='f4'),direction.astype('f4')

def sphere_entry(origin,direction,center,radius,limit):
    delta=origin-np.asarray(center);b=float(delta@direction);disc=b*b-float(delta@delta)+radius*radius
    if disc<0 or -b+math.sqrt(disc)<0:return None
    entry=max(0,-b-math.sqrt(disc))
    return entry if entry<=limit else None

def query(assets,scene,origin,direction,limit):
    candidates=[]
    for part in scene['parts']:
        if not scene['materials'][part['material']].get('previewEnabled',True):continue
        entry=sphere_entry(origin,direction,part['center'],part['radius'],limit)
        if entry is not None:candidates.append((entry,part))
    candidates.sort(key=lambda row:row[0]);best=None;read_parts=0;triangles=0
    for entry,part in candidates:
        if best is not None and entry>best['distance']:break
        path=Path(assets)/part['asset'];raw=path.read_bytes()
        assert hashlib.sha256(raw).hexdigest()==part['sha256']
        data=gzip.decompress(raw) if raw[:2]==b'\x1f\x8b' else raw
        magic,nv,ni,stride=struct.unpack_from('>4sIII',data);assert magic==b'D2M1' and stride==36 and nv==part['vertexCount'] and ni==part['indexCount']
        assert len(data)==16+nv*36+ni*2
        vertices=np.ndarray((nv,3),dtype='<f4',buffer=data,offset=16,strides=(36,4))
        indices=np.frombuffer(data,dtype='<u2',count=ni,offset=16+nv*36).reshape(-1,3)
        assert np.isfinite(vertices).all() and indices.max()<nv
        a,b,c=(vertices[indices[:,i]] for i in range(3));e1=b-a;e2=c-a
        p=np.cross(np.broadcast_to(direction,e2.shape),e2);det=np.einsum('ij,ij->i',e1,p)
        safe=np.where(np.abs(det)>1e-7,det,np.inf);t=origin-a
        u=np.einsum('ij,ij->i',t,p)/safe;q=np.cross(t,e1);v=q@direction/safe;dist=np.einsum('ij,ij->i',e2,q)/safe
        valid=(np.abs(det)>1e-7)&(u>=0)&(v>=0)&(u+v<=1)&(dist>.0001)&(dist<=limit)
        read_parts+=1;triangles+=len(indices)
        ids=np.flatnonzero(valid)
        if not len(ids):continue
        i=int(ids[np.argmin(dist[ids])]);d=float(dist[i])
        if best is not None and d>=best['distance']:continue
        normal=np.cross(e1[i],e2[i]);normal/=np.linalg.norm(normal)
        if normal@direction>0:normal=-normal
        best={'position':(origin+direction*d).tolist(),'normalAgainstRay':normal.tolist(),'distance':d,'triangle':i,'part':part['asset'],'materialIndex':part['material'],'barycentric':[float(1-u[i]-v[i]),float(u[i]),float(v[i])],'supportCandidate':bool(direction[1]<0 and normal[1]>=.65)}
    return best,{'candidateParts':len(candidates),'readParts':read_parts,'testedTriangles':triangles}
