#!/usr/bin/env python3
"""R003/R004/R007 -> D2M1 paged mobile preview. Never edits source resources."""
import argparse, gzip, hashlib, json, math, re, struct
from collections import Counter
from pathlib import Path
import numpy as np
from PIL import Image

def sha(p): return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def writejson(p,j):
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(j,ensure_ascii=False,indent=2)+'\n')

def vectors(text):
    match=re.search(r'm_vectorParams\s*=\s*\[',text)
    if not match:return {}
    start=match.end();level=1;quoted=False;escaped=False;end=start
    for end in range(start,len(text)):
        c=text[end]
        if escaped:escaped=False;continue
        if c=='\\' and quoted:escaped=True;continue
        if c=='"':quoted=not quoted
        if quoted:continue
        if c=='[':level+=1
        if c==']':
            level-=1
            if level==0:break
    assert level==0,'unbalanced KV3 vector table'
    found={}
    for name,raw in re.findall(r'm_name\s*=\s*"([^"]+)"\s+m_value\s*=\s*\[([^]]*)\]',text[start:end]):
        vals=[float(x) for x in re.findall(r'[-+]?\d+(?:\.\d*)?(?:[eE][-+]?\d+)?',raw)]
        assert name not in found and len(vals)==4 and all(math.isfinite(x) for x in vals),(name,vals)
        found[name]=vals
    return found

class GLB:
    def __init__(self,p):
        self.data=Path(p).read_bytes();magic,ver,total=struct.unpack_from('<III',self.data)
        assert magic==0x46546c67 and ver==2 and total==len(self.data)
        n,t=struct.unpack_from('<II',self.data,12);assert t==0x4e4f534a
        self.j=json.loads(self.data[20:20+n]);self.bin=28+n;self.cache={}
    def acc(self,i):
        if i in self.cache:return self.cache[i]
        a=self.j['accessors'][i];assert 'sparse' not in a
        v=self.j['bufferViews'][a['bufferView']];assert v.get('buffer',0)==0
        d={5126:'<f4',5125:'<u4',5123:'<u2',5122:'<i2',5121:'u1',5120:'i1'}[a['componentType']]
        nc={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4}[a['type']];size=np.dtype(d).itemsize
        ar=np.ndarray((a['count'],nc),dtype=d,buffer=self.data,offset=self.bin+v.get('byteOffset',0)+a.get('byteOffset',0),strides=(v.get('byteStride',size*nc),size))
        if a.get('normalized'):
            ar=ar.astype(np.float32)/np.iinfo(np.dtype(d)).max
            if d.endswith('i1') or d.endswith('i2'):ar=np.maximum(ar,-1)
        self.cache[i]=ar;return ar
    def reachable(self):
        def walk(i,parent):
            node=self.j['nodes'][i]
            if 'matrix' in node:local=np.array(node['matrix']).reshape(4,4,order='F')
            else:
                assert 'rotation' not in node and 'scale' not in node,'TRS unsupported in this source'
                local=np.eye(4);local[:3,3]=node.get('translation',[0,0,0])
            mat=parent@local
            if 'mesh' in node:yield i,node['mesh'],mat
            for child in node.get('children',[]):yield from walk(child,mat)
        for root in self.j['scenes'][self.j.get('scene',0)]['nodes']:yield from walk(root,np.eye(4))

def main():
    a=argparse.ArgumentParser();a.add_argument('--glb',required=True);a.add_argument('--bridge',required=True);a.add_argument('--kv3',required=True);a.add_argument('--png',required=True);a.add_argument('--soccer-root',required=True);a.add_argument('--assets',required=True);a.add_argument('--report',required=True);args=a.parse_args()
    assets=Path(args.assets);out=assets/'maps/dust2/mobile';out.mkdir(parents=True,exist_ok=True)
    bridge=json.loads(Path(args.bridge).read_text());g=GLB(args.glb);kvroot=Path(args.kv3);soccer=Path(args.soccer_root)
    mats=[];sources={m['source']:m for m in bridge['materials']};texture_sources={t['source']:t for t in bridge['textures']}
    texture_use=Counter();used_texture_files={};vector_count=0
    ordered=[sources[m['compiled_material_path']] for m in bridge['glb_material_mappings']]
    for idx,m0 in enumerate(ordered):
        m=dict(m0);p=kvroot/(m['source'].replace('.vmat_c','.txt'));v=vectors(p.read_text());vector_count+=len(v);m['vector_params']=v
        params={p['parameter']:p['path'] for p in m['texture_params']};ips=m['int_params'];fps=m['float_params']
        def tex(param):
            name=params.get(param)
            if not name:return ''
            key=name+'_c';t=texture_sources[key];assert t['source_depth']==1
            image=t['images'][0];rel=image['path'];p=Path(args.png)/rel
            if not p.exists():p=soccer/'soccer_png'/rel
            assert p.is_file() and sha(p)==image['sha256'],str(p)
            used_texture_files[key]=(p,image);texture_use[key]+=1
            return key
        tint=v.get('g_vColorTint',[1,1,1,1])[:3]
        if m['shader']=='csgo_black_unlit.vfx':tint=[0,0,0]
        alpha='blend' if m['translucent'] or ips.get('F_BLEND_MODE',0) in (1,3,4,5,6) else ('mask' if m['alpha_test'] or ips.get('F_BLEND_MODE')==2 else 'opaque')
        mats.append({'id':idx,'source':m['source'],'shader':m['shader'],'base':tex('g_tColor'),'layer':tex('g_tLayer2Color'),'blend':tex('g_tBlendModulation'),'alphaMode':alpha,'cutoff':fps.get('g_flAlphaTestReference',0.5),'opacity':fps.get('g_flOpacityScale',1),'doubleSided':m['render_backfaces'],'overlay':m['overlay'],'tint':tint,'uvScale':v.get('g_vTexCoordScale',[1,1])[:2],'uvOffset':v.get('g_vTexCoordOffset',[0,0])[:2],'uvRotation':fps.get('g_flTexCoordRotation',0),'layerScale':v.get('g_vLayer2TexCoordScale',v.get('g_vTexCoordScale2',[1,1]))[:2],'layerOffset':v.get('g_vLayer2TexCoordOffset',[0,0])[:2],'blendScale':v.get('g_vBlendModulateTexCoordScale',[1,1])[:2],'blendMode':ips.get('F_FANCY_BLENDING',0),'softness':max(0.001,fps.get('g_flBlendSoftness',0.5)),'paint':bool(ips.get('F_VERTEX_COLOR') or ips.get('F_PAINT_VERTEX_COLORS')),'allSourceParameters':m})
    assert len(mats)==len(g.j['materials'])==273
    for m in mats:m['previewEnabled']=m['shader']!='csgo_effects.vfx'
    # All referenced albedo/mask images fit a deterministic 72 MiB RGBA8 base-level budget.
    sizes={}
    for key,(p,im) in used_texture_files.items():
        w,h=im['width'],im['height'];ratio=min(1,512/max(w,h));nw=max(1,2**int(round(math.log2(max(1,w*ratio)))));nh=max(1,2**int(round(math.log2(max(1,h*ratio)))))
        sizes[key]=[min(512,nw),min(512,nh)]
    while sum(w*h*4 for w,h in sizes.values())>72*1024*1024:
        key=max(sizes,key=lambda k:math.prod(sizes[k])/max(1,texture_use[k]))
        w,h=sizes[key];assert max(w,h)>32,'texture budget too small'
        sizes[key]=[max(1,w//2),max(1,h//2)]
    texmap={};texrecords=[]
    for num,(key,(p,im)) in enumerate(sorted(used_texture_files.items())):
        name=f't{num:03}.png';dst=out/'textures'/name;dst.parent.mkdir(exist_ok=True)
        with Image.open(p) as img:
            img=img.convert('RGBA');img=img.resize(tuple(sizes[key]),Image.Resampling.LANCZOS);img.save(dst,optimize=True)
        texmap[key]='maps/dust2/mobile/textures/'+name
        texrecords.append({'source':key,'sourcePNG':im,'asset':texmap[key],'width':sizes[key][0],'height':sizes[key][1],'sha256':sha(dst),'bytes':dst.stat().st_size})
    for m in mats:
        for k in ('base','layer','blend'):m[k]=texmap.get(m[k],'')
    # Compact vertices: f32 xyz, snorm16 normal+pad, f32 uv, unorm8 color, unorm8 Source blend stream.
    dt=np.dtype([('pos','<f4',(3,)),('normal','<i2',(3,)),('pad','<i2'),('uv','<f4',(2,)),('color','u1',(4,)),('blend','u1',(4,))]);assert dt.itemsize==36
    parts=[];low=np.full(3,np.inf);high=-low.copy();source_tri=0;attrs=Counter();reachable_mesh=set();rootmat=None
    for node_id,mesh_id,matrix in g.reachable():
        reachable_mesh.add(mesh_id);normalmat=np.linalg.inv(matrix[:3,:3]).T
        for primitive_id,prim in enumerate(g.j['meshes'][mesh_id]['primitives']):
            if 'material' not in prim:continue
            assert prim.get('mode',4)==4
            material=prim['material'];attrib=prim['attributes'];attrs.update(attrib.keys());indices=g.acc(prim['indices']).reshape(-1);assert len(indices)%3==0
            source_tri+=len(indices)//3
            uniq=np.unique(indices)
            batches=[indices] if len(uniq)<=65535 else [indices[k:k+45000] for k in range(0,len(indices),45000)]
            for batch in batches:
                unique,remap=np.unique(batch,return_inverse=True);assert len(unique)<=65535
                pos=g.acc(attrib['POSITION'])[unique,:3];pos=pos@matrix[:3,:3].T+matrix[:3,3]
                norm=g.acc(attrib['NORMAL'])[unique,:3]@normalmat.T;norm/=np.maximum(np.linalg.norm(norm,axis=1,keepdims=True),1e-12)
                uv=g.acc(attrib['TEXCOORD_0'])[unique,:2]
                color=g.acc(attrib['COLOR_0'])[unique,:4] if 'COLOR_0' in attrib else np.ones((len(unique),4))
                blend=g.acc(attrib['_TEXCOORD_4'])[unique,:4] if '_TEXCOORD_4' in attrib else np.zeros((len(unique),4))
                assert all(np.isfinite(x).all() for x in (pos,norm,uv,color,blend))
                v=np.zeros(len(unique),dtype=dt);v['pos']=pos;v['normal']=np.rint(np.clip(norm,-1,1)*32767);v['uv']=uv;v['color']=np.rint(np.clip(color,0,1)*255);v['blend']=np.rint(np.clip(blend,0,1)*255)
                ii=remap.astype('<u2')
                if np.linalg.det(matrix[:3,:3])<0:ii=ii.reshape(-1,3)[:,[0,2,1]].copy().reshape(-1)
                mn=pos.min(0);mx=pos.max(0);center=(mn+mx)/2;radius=np.linalg.norm((mx-mn)/2);low=np.minimum(low,mn);high=np.maximum(high,mx)
                name=f'p{len(parts):05}.d2m.gz';path=out/'chunks'/name;path.parent.mkdir(exist_ok=True)
                raw=struct.pack('>4sIII',b'D2M1',len(v),len(ii),36)+v.tobytes()+ii.tobytes();path.write_bytes(gzip.compress(raw,compresslevel=6,mtime=0))
                parts.append({'asset':'maps/dust2/mobile/chunks/'+name,'material':material,'vertexCount':len(v),'indexCount':len(ii),'bytes':len(raw)-16,'center':center.tolist(),'radius':float(radius),'sha256':sha(path),'sourceNode':node_id,'sourceMesh':mesh_id,'sourcePrimitive':primitive_id})
        if node_id%400==0:print('converted node',node_id,'parts',len(parts),flush=True)
    assert sum(p['indexCount']//3 for p in parts)==source_tri
    manifest={'schema':'dust2-mobile-d2m1-v1','stride':36,'parts':parts,'materials':mats,'textures':texrecords,'bounds':[low.tolist(),high.tolist()],'limits':{'geometryGpuMiB':192,'textureGpuMiB':96,'maxPendingLoads':4},'source':{'renderVersion':'R003','bridgeVersion':'R007','glbSha256':sha(args.glb)},'knownIssues':['Preview lighting; no original baked lightmaps/reflections','Normal/roughness/tangent maps retained in source metadata, deferred in preview shader','Secondary UVs retained in original GLB, deferred for lightmaps/detail sampling','Layer border tint, complex decals and original shader equivalence are not certified','Soccer placement not certified; excluded from first preview rather than guessed']}
    manifest['knownIssues'].append('Ambient smoke/steam/lightshaft effect polygons are retained but hidden until mask/fade/depth-feather shader support; map buildings/roads remain enabled')
    writejson(out/'manifest.json',manifest)
    scene_path=assets/'maps/dust2/scene.json';scene=json.loads(scene_path.read_text());scene['meshAsset']='maps/dust2/mobile/manifest.json';scene['cameraTarget']=((low+high)/2).tolist();scene['extent']=float(max(high-low));scene['displayLabel']='沙二 · 全图贴图测试版';scene['overviewText']='已接入完整沙二渲染几何与主要颜色贴图。旋转、缩放、双指拖动和全图复位可用。首版采用预览光照，原版光照、复杂材质与教学继续完善。';writejson(scene_path,scene)
    report={'sourceTriangles':source_tri,'outputTriangles':sum(p['indexCount']//3 for p in parts),'parts':len(parts),'vertices':sum(p['vertexCount'] for p in parts),'geometryBytes':sum(p['bytes'] for p in parts),'materials':len(mats),'vectorParameters':vector_count,'sourceAttributes':dict(attrs),'textures':len(texrecords),'textureBaseRGBABytes':sum(t['width']*t['height']*4 for t in texrecords),'textureRGBAWithMipEstimate':sum(t['width']*t['height']*4 for t in texrecords)*4/3,'bounds':manifest['bounds'],'unreachableMeshesExcluded':len(g.j['meshes'])-len(reachable_mesh),'knownIssues':manifest['knownIssues']}
    report['deferredEffects']={'materials':sum(not m['previewEnabled'] for m in mats),'parts':sum(not mats[p['material']]['previewEnabled'] for p in parts),'triangles':sum(p['indexCount']//3 for p in parts if not mats[p['material']]['previewEnabled'])}
    writejson(Path(args.report),report);print(json.dumps(report,ensure_ascii=False,indent=2),flush=True)

if __name__=='__main__':main()
