#!/usr/bin/env python3
"""Convert the author's public Mirage GLB: keep Source coordinates, spatially split uint16 meshes.
Requires assimp_py, numpy, Pillow. Usage: python convert_detailed_mirage.py GLB TEXTURE_DIR
"""
from pathlib import Path
import sys,struct,json
import numpy as np
import assimp_py as assimp
from PIL import Image
root=Path(__file__).resolve().parents[1]
p=Path(sys.argv[1]);texture_dir=Path(sys.argv[2]);out=root/'app/src/main/assets/maps/mirage/detailed';out.mkdir(parents=True,exist_ok=True)
with p.open('rb') as f:
 assert f.read(4)==b'glTF';version,total=struct.unpack('<II',f.read(8));n,k=struct.unpack('<I4s',f.read(8));gltf=json.loads(f.read(n))
 bin_length,k=struct.unpack('<I4s',f.read(8));binary_offset=f.tell()
 print('Importing model...',flush=True)
 scene=assimp.import_file(str(p),assimp.Process_Triangulate|assimp.Process_PreTransformVertices|assimp.Process_GenNormals)
 texture_paths={}
 for i,entry in enumerate(gltf['images']):
  name=entry.get('name',str(i));file=texture_dir/(name+'.png')
  if file.is_file():im=Image.open(file).convert('RGBA')
  else:
   view=gltf['bufferViews'][entry['bufferView']];f.seek(binary_offset+view.get('byteOffset',0))
   import io
   im=Image.open(io.BytesIO(f.read(view['byteLength']))).convert('RGBA')
  im.thumbnail((256,256),Image.Resampling.LANCZOS)
  # Power-of-two textures allow mipmapping on all GLES 2 devices.
  w=2**int(np.floor(np.log2(im.width)));h=2**int(np.floor(np.log2(im.height)))
  if im.size!=(w,h):im=im.resize((w,h),Image.Resampling.LANCZOS)
  dest=out/f'tex-{i:03d}.png';im.save(dest,optimize=True);texture_paths['*'+str(i)]='maps/mirage/detailed/'+dest.name
 parts=[];lo=np.full(3,np.inf);hi=np.full(3,-np.inf);triangles=0
 for mesh in scene.meshes:
  mat=scene.materials[mesh.material_index];tex=texture_paths.get(mat.get('TEXTURE_BASE',''),'')
  v=np.asarray(mesh.vertices).reshape(-1,3);normal=np.asarray(mesh.normals).reshape(-1,3)
  assert np.isfinite(v).all(), 'Nonfinite source position'
  normal=normal.copy()
  bad=~np.isfinite(normal).all(axis=1) | (np.linalg.norm(normal,axis=1)<1e-6)
  normal[bad]=[0,1,0]
  uv=np.asarray(mesh.texcoords[0]).reshape(mesh.num_vertices,-1)[:,:2] if mesh.texcoords else np.zeros((mesh.num_vertices,2))
  uv=uv.copy();uv[:,1]=1-uv[:,1]
  assert np.isfinite(uv).all(), 'Nonfinite source UV'
  data=np.concatenate([v,normal,uv],axis=1).astype('<f4');faces=np.asarray(mesh.indices,dtype=np.int32).reshape(-1,3)
  lo=np.minimum(lo,v.min(0));hi=np.maximum(hi,v.max(0));triangles+=len(faces)
  # Spatial buckets make close-up viewing draw only nearby geometry.
  centers=v[faces].mean(1);cells=np.floor(centers[:,[0,2]]/16).astype(np.int32)
  keys,groups=np.unique(cells,axis=0,return_inverse=True)
  for group in range(len(keys)):
   local=faces[groups==group]
   for start in range(0,len(local),20000):
    indices=local[start:start+20000].ravel();unique,inverse=np.unique(indices,return_inverse=True)
    assert len(unique)<=60000
    parts.append((tex,data[unique],inverse.astype('<u2')))
 with (out/'mirage.c2m').open('wb') as f:
  f.write(b'C2M2');f.write(struct.pack('>I',len(parts)))
  for tex,data,indices in parts:
   b=tex.encode();f.write(struct.pack('>H',len(b)));f.write(b)
   f.write(struct.pack('>IIfff',len(data),len(indices),1,1,1));f.write(data.tobytes());f.write(indices.tobytes())
report={'title':'Mirage CS2 public GLB','author':'frostychaos144','originalGameMap':'Valve / Counter-Strike 2',
 'source':'https://sketchfab.com/3d-models/lab-standoff-2-98d4915da22144aaa98a088390f81120',
 'download':'https://drive.google.com/file/d/1bHKCApk-WfiEev1V6FrM4nXGa8Emo2Sg/view',
 'triangles':triangles,'parts':len(parts),'textures':len(texture_paths),'bounds':[lo.tolist(),hi.tolist()],
 'coordinateTransform':'GLB preserves game metres: model=[sourceX*0.0254, sourceZ*0.0254, -sourceY*0.0254]. setpos uses eye height; subtract 64*0.0254 to place a ground marker.',
 'changes':'Geometry retained; spatially split into uint16 meshes; textures resized to maximum 256 and power of two; converted to C2M2. No game shader or lighting reproduction.'}
(out/'manifest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(report),flush=True)
