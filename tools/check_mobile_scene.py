#!/usr/bin/env python3
"""Independent chunk/texture validation and actual GLES shader/pixel checks on Mesa EGL.
This is NOT Android startup, touch, phone memory or phone FPS evidence.
"""
import ctypes as C, ctypes.util, gzip, hashlib, json, math, struct, sys
from pathlib import Path
import numpy as np
from PIL import Image
import argparse
parser=argparse.ArgumentParser()
parser.add_argument('--debug-material',action='store_true')
parser.add_argument('--capture-camera',type=Path,help='C015 capture JSON or a camera object; never implies calibrated course')
parser.add_argument('--capture-output',type=Path)
args=parser.parse_args()
ROOT=Path(__file__).resolve().parents[1];ASSETS=ROOT/'app/src/main/assets';OUT=ROOT/'docs/preview';OUT.mkdir(parents=True,exist_ok=True)
if args.capture_output:OUT=args.capture_output;OUT.mkdir(parents=True,exist_ok=True)
scene=json.loads((ASSETS/'maps/dust2/mobile/manifest.json').read_text())
DEBUG='--debug-material' in sys.argv
capture=None
capture_width,capture_height=1200,800
capture_extent=json.loads((ASSETS/'maps/dust2/scene.json').read_text())['extent']
if args.capture_camera:
    assert args.capture_camera.stat().st_size<=65536
    capture=json.loads(args.capture_camera.read_text())
    camera=capture.get('camera',capture)
    from c015_display_capture import camera_frame,ray_at,query
    capture_width,capture_height=capture.get('viewportWidth',1200),capture.get('viewportHeight',800)
    assert isinstance(capture_width,int) and isinstance(capture_height,int) and 0<capture_width*capture_height<=4_000_000
    capture_eye,capture_fov,capture_vp=camera_frame(camera,capture_width,capture_height,capture_extent)
egl=C.CDLL(ctypes.util.find_library('EGL'))
def efn(name,restype,args):
    f=getattr(egl,name);f.restype=restype;f.argtypes=args;return f
addr=efn('eglGetProcAddress',C.c_void_p,[C.c_char_p])
platform=C.CFUNCTYPE(C.c_void_p,C.c_uint,C.c_void_p,C.POINTER(C.c_int))(addr(b'eglGetPlatformDisplayEXT'))
display=platform(0x31dd,None,None)
init=efn('eglInitialize',C.c_uint,[C.c_void_p,C.POINTER(C.c_int),C.POINTER(C.c_int)])
major,minor=C.c_int(),C.c_int();assert init(display,C.byref(major),C.byref(minor))
assert efn('eglBindAPI',C.c_uint,[C.c_uint])(0x30a0)
choose=efn('eglChooseConfig',C.c_uint,[C.c_void_p,C.POINTER(C.c_int),C.POINTER(C.c_void_p),C.c_int,C.POINTER(C.c_int)])
attrs=(C.c_int*17)(0x3033,1,0x3040,4,0x3024,8,0x3023,8,0x3022,8,0x3021,8,0x3025,24,0x3038,0,0)
config=C.c_void_p();count=C.c_int();assert choose(display,attrs,C.byref(config),1,C.byref(count)) and count.value
w,h=capture_width,capture_height
surface=efn('eglCreatePbufferSurface',C.c_void_p,[C.c_void_p,C.c_void_p,C.POINTER(C.c_int)])(display,config,(C.c_int*5)(0x3057,w,0x3056,h,0x3038))
context=efn('eglCreateContext',C.c_void_p,[C.c_void_p,C.c_void_p,C.c_void_p,C.POINTER(C.c_int)])(display,config,None,(C.c_int*3)(0x3098,2,0x3038))
assert context and surface
assert efn('eglMakeCurrent',C.c_uint,[C.c_void_p,C.c_void_p,C.c_void_p,C.c_void_p])(display,surface,surface,context)
def gf(name,restype,args):return C.CFUNCTYPE(restype,*args)(addr(name.encode()))
u=C.c_uint;i=C.c_int;f=C.c_float;p=C.c_void_p
getstr=gf('glGetString',C.c_char_p,[u]);geterr=gf('glGetError',u,[])
print('EGL GLES',getstr(0x1f02),getstr(0x1f01),flush=True)
cs=gf('glCreateShader',u,[u]);ss=gf('glShaderSource',None,[u,i,C.POINTER(C.c_char_p),C.POINTER(i)]);compile_=gf('glCompileShader',None,[u])
giv=gf('glGetShaderiv',None,[u,u,C.POINTER(i)]);slog=gf('glGetShaderInfoLog',None,[u,i,C.POINTER(i),p])
def shader(kind,name):
    raw=(ASSETS/'shaders'/name).read_bytes()
    if DEBUG and name=='mobile.frag':raw=raw.replace(b'gl_FragColor=vec4(pow(max(c.rgb*tint*vLight,vec3(0.0)),vec3(1.0/2.2)),a);',b'gl_FragColor=vec4(uTint+0.000001*pow(max(c.rgb*tint*vLight,vec3(0.0)),vec3(1.0/2.2)),a);')
    sid=cs(kind);s= C.c_char_p(raw);ss(sid,1,C.byref(s),None);compile_(sid);ok=i();giv(sid,0x8b81,C.byref(ok))
    log=C.create_string_buffer(8192);slog(sid,8192,None,log);assert ok.value,log.value;return sid
vs=shader(0x8b31,'mobile.vert');fs=shader(0x8b30,'mobile.frag')
program=gf('glCreateProgram',u,[])();attach=gf('glAttachShader',None,[u,u]);attach(program,vs);attach(program,fs);gf('glLinkProgram',None,[u])(program)
ok=i();gf('glGetProgramiv',None,[u,u,C.POINTER(i)])(program,0x8b82,C.byref(ok));log=C.create_string_buffer(8192);gf('glGetProgramInfoLog',None,[u,i,C.POINTER(i),p])(program,8192,None,log);assert ok.value,log.value
gf('glUseProgram',None,[u])(program)
ul=gf('glGetUniformLocation',i,[u,C.c_char_p]);loc=lambda s:ul(program,s.encode())
ui=gf('glUniform1i',None,[i,i]);uf=gf('glUniform1f',None,[i,f]);uv=gf('glUniform2fv',None,[i,i,p]);uc=gf('glUniform3fv',None,[i,i,p]);um=gf('glUniformMatrix4fv',None,[i,i,C.c_ubyte,p])
def array_uniform(name,v):
    a=np.asarray(v,dtype='f4');(uc if len(v)==3 else uv)(loc(name),1,a.ctypes.data)
enable=gf('glEnable',None,[u]);disable=gf('glDisable',None,[u]);bindtex=gf('glBindTexture',None,[u,u]);active=gf('glActiveTexture',None,[u]);gt=gf('glGenTextures',None,[i,C.POINTER(u)])
param=gf('glTexParameteri',None,[u,u,i]);teximage=gf('glTexImage2D',None,[u,i,i,i,i,i,u,u,p]);mips=gf('glGenerateMipmap',None,[u])
textures={}
for t in scene['textures']:
    raw=(ASSETS/t['asset']).read_bytes();assert hashlib.sha256(raw).hexdigest()==t['sha256']
    image=Image.open(ASSETS/t['asset']).convert('RGBA');assert image.size==(t['width'],t['height']);data=np.asarray(image).copy()
    tid=u();gt(1,C.byref(tid));bindtex(0x0de1,tid.value)
    for key,val in [(0x2801,0x2703),(0x2800,0x2601),(0x2802,0x2901),(0x2803,0x2901)]:param(0x0de1,key,val)
    teximage(0x0de1,0,0x1908,image.width,image.height,0,0x1908,0x1401,data.ctypes.data);mips(0x0de1);assert geterr()==0
    textures[t['asset']]=tid.value
gb=gf('glGenBuffers',None,[i,C.POINTER(u)]);bb=gf('glBindBuffer',None,[u,u]);bd=gf('glBufferData',None,[u,C.c_ssize_t,p,u])
attribloc=gf('glGetAttribLocation',i,[u,C.c_char_p]);pointer=gf('glVertexAttribPointer',None,[u,i,u,C.c_ubyte,i,p]);enabled=gf('glEnableVertexAttribArray',None,[u])
attribute_specs=[('aPosition',3,0x1406,0,0),('aNormal',3,0x1402,1,12),('aUV',2,0x1406,0,20),('aColor',4,0x1401,1,28),('aBlend',4,0x1401,1,32)]
spec=[(attribloc(program,name.encode()),n,ty,norm,offset) for name,n,ty,norm,offset in attribute_specs]
for aid,*_ in spec:assert aid>=0;enabled(aid)
draws=[];triangles=0;low=np.full(3,np.inf);high=-low.copy()
for num,part in enumerate(scene['parts']):
    raw=(ASSETS/part['asset']).read_bytes();assert hashlib.sha256(raw).hexdigest()==part['sha256'];data=gzip.decompress(raw);magic,nv,ni,stride=struct.unpack_from('>4sIII',data);assert magic==b'D2M1' and stride==36 and nv==part['vertexCount'] and ni==part['indexCount']
    assert len(data)==16+nv*36+ni*2
    vb=np.frombuffer(data[16:16+nv*36],dtype='u1').copy();ib=np.frombuffer(data[16+nv*36:],dtype='<u2').copy();assert ib.max()<nv
    positions=np.ndarray((nv,3),dtype='<f4',buffer=vb,strides=(36,4));uvs=np.ndarray((nv,2),dtype='<f4',buffer=vb,offset=20,strides=(36,4));assert np.isfinite(positions).all() and np.isfinite(uvs).all()
    low=np.minimum(low,positions.min(0));high=np.maximum(high,positions.max(0));triangles+=ni//3
    ids=(u*2)();gb(2,ids);bb(0x8892,ids[0]);bd(0x8892,vb.nbytes,vb.ctypes.data,0x88e4);bb(0x8893,ids[1]);bd(0x8893,ib.nbytes,ib.ctypes.data,0x88e4);assert geterr()==0
    draws.append((ids[0],ids[1],part))
    if num%700==0:print('checked chunks',num,flush=True)
assert triangles==4610873
assert np.allclose(low,scene['bounds'][0],atol=1e-4) and np.allclose(high,scene['bounds'][1],atol=1e-4)
def lookat(eye,target):
    forward=target-eye;forward/=np.linalg.norm(forward);right=np.cross(forward,[0,1,0]);right/=np.linalg.norm(right);up=np.cross(right,forward)
    m=np.eye(4);m[:3,:3]=[right,up,-forward];m[:3,3]=-m[:3,:3]@eye;return m
def projection(aspect,free=False):
    t=1/math.tan(math.radians(70 if free else 45)/2);near=.05 if free else .1;far=2000
    return np.array([[t/aspect,0,0,0],[0,t,0,0],[0,0,(far+near)/(near-far),2*far*near/(near-far)],[0,0,-1,0]])
gf('glViewport',None,[i,i,i,i])(0,0,w,h);clearcolor=gf('glClearColor',None,[f,f,f,f]);clear=gf('glClear',None,[u]);depthmask=gf('glDepthMask',None,[C.c_ubyte]);blendfunc=gf('glBlendFunc',None,[u,u]);polyoffset=gf('glPolygonOffset',None,[f,f]);draw=gf('glDrawElements',None,[u,i,u,p]);read=gf('glReadPixels',None,[i,i,i,i,u,u,p])
enable(0x0b71);ui(loc('uBase'),0);ui(loc('uLayer'),1);ui(loc('uBlendMap'),2)
center=(low+high)/2;extent=max(high-low);dist=extent*1.55
yaw=math.radians(38);pitch=math.radians(65)
overview=center+dist*np.array([math.cos(pitch)*math.sin(yaw),math.sin(pitch),math.cos(pitch)*math.cos(yaw)])
views=[('overview',overview,center),('courtyard',np.array([25.,20.,-15.]),np.array([-35.,5.,-25.])),('ct_spawn',np.array([25.,16.,-35.]),np.array([9.7,-2.77,-53.4])),('t_spawn',np.array([-2.,22.,37.]),np.array([-20.9,3.83,20.2]))]
views += [('free_b_site',np.array([-39.5128646,1.7807047,-67.2448919]),np.array([-40.,1.7807047,-40.])),('free_t_spawn',np.array([-20.8880754,5.4780086,20.2093091]),np.array([-30.,5.4780086,-10.]))]
views += [('free_b_default',np.array([-39.5128646,1.7807047,-67.2448919]),np.array([-39.5128646,1.7807047,-67.2448919])+np.array([-math.sin(math.radians(38)),0,-math.cos(math.radians(38))])),('free_t_default',np.array([-20.8880754,5.4780086,20.2093091]),np.array([-20.8880754,5.4780086,20.2093091])+np.array([-math.sin(math.radians(38)),0,-math.cos(math.radians(38))]))]
if DEBUG:views=[v for v in views if v[0]=='courtyard']
if capture is not None:views=[('capture',capture_eye,capture_eye+np.array([-math.sin(math.radians(camera['yaw']))*math.cos(math.radians(camera['pitch'])),-math.sin(math.radians(camera['pitch'])),-math.cos(math.radians(camera['yaw']))*math.cos(math.radians(camera['pitch']))]))]
for name,eye,target in views:
    clearcolor(.071,.095,.13,1);depthmask(1);clear(0x4000|0x100)
    vp=projection(w/h,name.startswith("free_"))@lookat(eye,target);mat=vp.T.astype('f4').copy();um(loc('uVP'),1,0,mat.ctypes.data)
    if capture is not None:
        vp=capture_vp;mat=vp.T.astype('f4').copy();um(loc('uVP'),1,0,mat.ctypes.data)
    def distance(row):return np.linalg.norm(np.array(row[2]['center'])-eye)
    eligible=[d for d in draws if scene['materials'][d[2]['material']].get('previewEnabled',True)]
    opaque=sorted([d for d in eligible if scene['materials'][d[2]['material']]['alphaMode']!='blend'],key=lambda d:d[2]['material'])
    translucent=sorted([d for d in eligible if scene['materials'][d[2]['material']]['alphaMode']=='blend'],key=distance,reverse=True)
    for vbo,ibo,part in opaque+translucent:
        m=scene['materials'][part['material']];isblend=m['alphaMode']=='blend'
        (enable if isblend else disable)(0x0be2);blendfunc(0x0302,0x0303);depthmask(not isblend)
        (disable if m['doubleSided'] else enable)(0x0b44)
        (enable if m['overlay'] else disable)(0x8037);polyoffset(-1,-1)
        for unit,key in enumerate(('base','layer','blend')):active(0x84c0+unit);bindtex(0x0de1,textures.get(m[key],0))
        for uniform,key in [('uHasBase','base'),('uHasLayer','layer'),('uHasBlend','blend')]:ui(loc(uniform),int(bool(m[key])))
        ui(loc('uMode'),{'opaque':0,'mask':1,'blend':2}[m['alphaMode']]);ui(loc('uBlendMode'),m['blendMode']);ui(loc('uPaint'),int(m['paint']))
        for uniform,key in [('uCutoff','cutoff'),('uOpacity','opacity'),('uSoftness','softness'),('uUVRotation','uvRotation')]:uf(loc(uniform),m[key])
        for uniform,key in [('uTint','tint'),('uUVScale','uvScale'),('uUVOffset','uvOffset'),('uLayerScale','layerScale'),('uLayerOffset','layerOffset'),('uBlendScale','blendScale')]:array_uniform(uniform,m[key])
        if DEBUG:
            code=part['material']+1;array_uniform('uTint',[(code&255)/255,(code>>8)/255,0])
        bb(0x8892,vbo);bb(0x8893,ibo)
        for aid,n,ty,norm,offset in spec:pointer(aid,n,ty,norm,36,C.c_void_p(offset))
        draw(4,part['indexCount'],0x1403,None)
    gf('glFinish',None,[])();assert geterr()==0
    pixels=np.empty((h,w,4),dtype='u1');read(0,0,w,h,0x1908,0x1401,pixels.ctypes.data);assert geterr()==0
    bg=np.array([18,24,33]);coverage=(np.abs(pixels[:,:,:3].astype('i4')-bg).max(2)>8).mean();assert coverage>.02,(name,coverage)
    Image.fromarray(pixels[::-1]).convert('RGB').save(OUT/(name+('_material_ids' if DEBUG else '')+'.png'));print('rendered',name,'coverage',float(coverage),flush=True)
    if capture is not None:
        x,y=capture.get('pixel',[w/2,h/2]);origin,direction=ray_at(eye,vp,w,h,x,y)
        hit,stats=query(ASSETS,scene,origin,direction,capture_extent*10)
        record={'schema':'c015-display-capture-v1','status':'DEVELOPMENT','importable':False,'coordinateSpace':'display-m-y-up-v1','mapVersion':hashlib.sha256((ASSETS/'maps/dust2/mobile/manifest.json').read_bytes()).hexdigest(),'field':capture.get('field','aim'),'camera':camera,'cameraPositionMeaning':'eye' if camera['free'] else 'orbit-target','actualCameraEye':eye.tolist(),'verticalFov':capture_fov,'viewportWidth':w,'viewportHeight':h,'ray':{'origin':origin.tolist(),'direction':direction.tolist(),'maxDistance':capture_extent*10},'surface':hit,'value':hit['position'] if hit else None,'screenshot':'capture.png','screenshotSha256':hashlib.sha256((OUT/'capture.png').read_bytes()).hexdigest(),'environment':getstr(0x1f01).decode(),'scope':'Full restored enabled display map rendered offline with repository shaders. Unverified map probe, not selected-video stance/aim or Android/phone evidence.','queryStats':stats}
        (OUT/'capture.json').write_text(json.dumps(record,indent=2)+'\n')
report={'environment':getstr(0x1f01).decode(),'glVersion':getstr(0x1f02).decode(),'checks':'all chunk SHA/length/index/finiteness/bounds; all PNG SHA/size; exact GLES shader compile/link; enabled scene draws without GL errors','triangles':triangles,'parts':len(draws),'textures':len(textures),'views':[v[0] for v in views],'deferredAmbientEffects':{'materials':sum(not m.get('previewEnabled',True) for m in scene['materials']),'parts':sum(not scene['materials'][p['material']].get('previewEnabled',True) for p in scene['parts']),'triangles':sum(p['indexCount']//3 for p in scene['parts'] if not scene['materials'][p['material']].get('previewEnabled',True))},'androidRuntime':'not tested; EGL desktop software rendering is not Android evidence'}
(OUT/'egl-report.json' if capture is not None else ROOT/'docs'/('egl-debug-materials.json' if DEBUG else 'egl-verification.json')).write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
