#!/usr/bin/env python3
"""Convert the author's CC-BY Mirage LITE FBX into a small Android indexed mesh.
Requires assimp_py, numpy, Pillow. Usage: python convert_mirage_model.py MODEL_DIR
"""
from pathlib import Path
import sys, struct, json
import numpy as np
import assimp_py as assimp
from PIL import Image

root=Path(__file__).resolve().parents[1]
source=Path(sys.argv[1]);target=root/'app/src/main/assets/maps/mirage/model'
target.mkdir(parents=True,exist_ok=True)
scene=assimp.import_file(str(source/'Level (merge).fbx'),assimp.Process_Triangulate|assimp.Process_PreTransformVertices|assimp.Process_GenNormals)
allv=np.concatenate([np.asarray(m.vertices).reshape(-1,3) for m in scene.meshes])
offset=np.array([-(allv[:,0].min()+allv[:,0].max())/2,-allv[:,1].min(),-(allv[:,2].min()+allv[:,2].max())/2])
mapping={'ArabicWall_01':'ArabicWall_01_stylized.png','ArabicWall_02':'ArabicWall_02_stylized.png',
 'ArabicWall_03':'ArabicWall_03_stylized.png','ArabicWall_04':'ArabicWall_04_stylized.png',
 'BlockWall_02':'BlockWall_04.png','BlockWall_03':'BlockWall_04.png','BlockWall_04':'BlockWall_04.png',
 'DirtyConcrete':'DirtyConcrete.png','DirtyConcrete2':'DirtyConcrete_gray.png',
 'Plants_mtl':'Plants_d.png','Pavement':'BrokenPavement_gray.png','Tiles':'FloorTiles_stylized.png',
 'DoorsAndWindows':'DoorsAndWindows.png','Roof':'Roof.png','WoodPlanks':'WoodenPlanks_stylized.png'}
parts=[]
with (target/'mirage.c2m').open('wb') as f:
 f.write(b'C2M2');f.write(struct.pack('>I',len(scene.meshes)))
 for mesh in scene.meshes:
  material=scene.materials[mesh.material_index];name=material['NAME'];tex=mapping.get(name,'')
  if tex:
   image=Image.open(source/tex).convert('RGBA');image.save(target/tex)
  v=np.asarray(mesh.vertices).reshape(-1,3)+offset
  n=np.asarray(mesh.normals).reshape(-1,3)
  uv=np.asarray(mesh.texcoords[0]).reshape(mesh.num_vertices,-1)[:,:2] if mesh.texcoords else np.zeros((mesh.num_vertices,2))
  uv=uv.copy();uv[:,1]=1-uv[:,1]
  data=np.concatenate([v,n,uv],axis=1).astype('<f4')
  indices=np.asarray(mesh.indices,dtype='<u2')
  assert len(v)<=65535 and indices.max()<len(v)
  path=('maps/mirage/model/'+tex if tex else '').encode('utf8')
  color=[.48,.49,.47] if name=='Metal' else [.7,.64,.51] if name=='Blocker' else [1.,1.,1.]
  f.write(struct.pack('>H',len(path)));f.write(path)
  f.write(struct.pack('>IIfff',len(v),len(indices),*color));f.write(data.tobytes());f.write(indices.tobytes())
  parts.append({'name':name,'texture':tex,'vertices':len(v),'triangles':len(indices)//3})
report={'author':'frostychaos144','title':'maps mirage LITE','license':'CC-BY-4.0',
 'source':'https://sketchfab.com/3d-models/maps-mirage-lite-8de567de170b4998a7aa2d6a041a6e43',
 'offset':offset.tolist(),'bounds':[(allv.min(0)+offset).tolist(),(allv.max(0)+offset).tolist()],
 'triangles':sum(p['triangles'] for p in parts),'parts':parts,
 'changes':'Triangulated, recentered, UV vertical flipped, converted to indexed Android binary. Some shared wall textures substituted by matching atlas.'}
(target/'manifest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:report[k] for k in ('bounds','triangles','offset')}))
