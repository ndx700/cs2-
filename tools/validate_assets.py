#!/usr/bin/env python3
"""Offline catalog integrity checks; independent of Android tooling."""
from pathlib import Path
import json
import math
import struct
import gzip
import hashlib
from urllib.parse import urlparse

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
def read(path):
    path=(ASSETS/path).resolve()
    assert path.is_relative_to(ASSETS.resolve()), 'Asset path escapes root'
    result=json.loads(path.read_text())
    assert result['schemaVersion']==1
    return result
def vec(v):
    assert len(v)==3 and all(isinstance(n,(int,float)) and math.isfinite(n) for n in v)
def check_mesh(asset):
    path=(ASSETS/asset).resolve()
    assert path.is_relative_to(ASSETS.resolve())
    if path.suffix=='.json':
        check_mobile_mesh(path)
        return
    total_triangles=0
    textures=set()
    with path.open('rb') as f:
        assert f.read(4)==b'C2M2'
        parts=struct.unpack('>I',f.read(4))[0]
        assert 1<=parts<=4096
        for _ in range(parts):
            n=struct.unpack('>H',f.read(2))[0]
            texture=f.read(n).decode('utf-8')
            if texture:
                p=(ASSETS/texture).resolve()
                assert p.is_relative_to(ASSETS.resolve()) and p.is_file()
                with p.open('rb') as png:
                    assert png.read(8)==b'\x89PNG\r\n\x1a\n'
                    png.read(8);w,h=struct.unpack('>II',png.read(8))
                    assert w>0 and h>0 and w&(w-1)==0 and h&(h-1)==0
                textures.add(texture)
            nv,ni,*colors=struct.unpack('>IIfff',f.read(20))
            assert 1<=nv<=65535 and 3<=ni<=600000 and ni%3==0
            assert all(math.isfinite(v) and 0<=v<=1 for v in colors)
            vertices=f.read(nv*32);indices=f.read(ni*2)
            assert len(vertices)==nv*32 and len(indices)==ni*2
            assert all(math.isfinite(v) for (v,) in struct.iter_unpack('<f',vertices))
            assert all(i<nv for (i,) in struct.iter_unpack('<H',indices))
            total_triangles+=ni//3
        assert f.read()==b''
    print(f'PASS: mesh {parts} parts, {total_triangles:,} triangles, {len(textures)} textures')

def check_mobile_mesh(path):
    scene=json.loads(path.read_text())
    assert scene['schema']=='dust2-mobile-d2m1-v1' and scene['stride']==36
    materials=scene['materials'];assert materials
    seen=set();textures=set();triangles=0
    low=[math.inf]*3;high=[-math.inf]*3
    def payload(row):
        p=(ASSETS/row['asset']).resolve()
        assert p.is_relative_to(ASSETS.resolve()) and p.is_file()
        raw=p.read_bytes()
        assert hashlib.sha256(raw).hexdigest()==row['sha256']
        return raw
    for row in scene['textures']:
        assert row['asset'] not in textures; textures.add(row['asset'])
        raw=payload(row)
        assert len(raw)==row['bytes']
        assert raw[:8]==b'\x89PNG\r\n\x1a\n' and raw[12:16]==b'IHDR'
        w,h=struct.unpack_from('>II',raw,16)
        assert (w,h)==(row['width'],row['height']) and w>0 and h>0
    for material in materials:
        for slot in ('base','layer','blend'):
            assert not material.get(slot) or material[slot] in textures
    for row in scene['parts']:
        assert row['asset'] not in seen;seen.add(row['asset'])
        assert 0<=row['material']<len(materials)
        raw=gzip.decompress(payload(row))
        magic,nv,ni,stride=struct.unpack_from('>4sIII',raw)
        assert magic==b'D2M1' and stride==36
        assert (nv,ni)==(row['vertexCount'],row['indexCount'])
        assert 1<=nv<=65535 and ni>=3 and ni%3==0
        end=16+nv*36
        assert len(raw)==end+ni*2 and row['bytes']==nv*36+ni*2
        for vertex in struct.iter_unpack('<3f3h2x2f8B',raw[16:end]):
            assert all(math.isfinite(v) for v in (*vertex[:3],*vertex[6:8]))
            for axis in range(3):
                low[axis]=min(low[axis],vertex[axis]);high[axis]=max(high[axis],vertex[axis])
        assert all(index<nv for (index,) in struct.iter_unpack('<H',raw[end:]))
        triangles+=ni//3
    assert seen and all(abs(actual-expected)<1e-4 for actual,expected in zip(low+high,scene['bounds'][0]+scene['bounds'][1]))
    print(f'PASS: D2M1 {len(seen)} parts, {triangles:,} triangles, {len(textures)} textures; hashes, layout, indices and bounds valid')

catalog=read('catalog.json')
assert catalog['maps'], 'Empty catalog'
map_ids=set()
count=0
for m in catalog['maps']:
    assert m['id'] not in map_ids
    map_ids.add(m['id'])
    scene=read(m['sceneAsset'])
    assert scene['extent']>1
    for b in scene['boxes']:
        vec(b['center']);vec(b['size']); assert all(n>0 for n in b['size'])
        assert len(b['color'])==3 and all(0<=n<=1 for n in b['color'])
    for label in scene['labels']: vec(label['position'])
    if scene.get('objAsset'): assert (ASSETS/scene['objAsset']).is_file()
    if scene.get('meshAsset'): check_mesh(scene['meshAsset'])
    targets=scene.get('targets',[])
    assert len({t['id'] for t in targets})==len(targets)
    for t in targets:
        vec(t['position']);vec(t['focus']);assert 0<t['distance']<=scene['extent']*3.2
        assert 20<=t.get('pitch',78)<=85
    seen=set()
    groups={}
    for row in read(m['lineupsAsset'])['lineups']:
        assert row['mapId']==m['id'] and row['id'] not in seen
        seen.add(row['id']);count+=1
        assert row['type'] in ('SMOKE','FLASH','MOLOTOV','HE')
        assert row['status'] in ('DEMO','UNVERIFIED','VERIFIED')
        for key in ('stand','landing'):
            if row[key] is None:
                assert key=='landing' and not row.get('landingCoordinateKnown',True)
                continue
            vec(row[key]);assert max(abs(row[key][0]),abs(row[key][2]))<=scene['extent']
        assert row['title'] and row['steps']
        if row['status']=='VERIFIED':
            assert row['source'] and row['gameBuild'] and row['verifiedAt']
            assert row['video']['kind']!='DEMO'
        v=row['video'];assert v['kind'] in ('DEMO','DIRECT','EXTERNAL','BILIBILI')
        if v['kind']=='BILIBILI':
            assert v['bvid'].startswith('BV') and v['cid']>0
            assert 0<=v['startSeconds']<v['endSeconds']
        if row.get('modelStand'):
            vec(row['modelStand'])
        if row.get('aimCrop'):
            c=row['aimCrop'];assert c['x']>=0 and c['y']>=0 and c['width']>0 and c['height']>0
        for key in ('aimImage','spawnImage'):
            if row.get(key):
                uri=urlparse(row[key]);assert uri.scheme=='https' and uri.hostname
        if row.get('groupId'):
            assert row.get('groupTitle') and row.get('spawnNumber',0)>0
            groups.setdefault(row['groupId'],[]).append(row)
        if v['kind']!='DEMO':
            uri=urlparse(v['url']);assert uri.scheme=='https' and uri.hostname
        else:
            assert (ROOT/'app/src/main/res/raw/flow_demo.mp4').is_file()
    for t in targets:
        rows=groups[t['id']]
        assert len({r['spawnNumber'] for r in rows})==len(rows)
        assert all(r.get('modelStand') for r in rows)
        assert all(math.dist(r['modelStand'],t['focus'])<t['distance'] for r in rows)
        if all(r['video']['kind']=='BILIBILI' for r in rows):
            segments=[r['video'] for r in sorted(rows,key=lambda r:r['spawnNumber'])]
            assert all(a['endSeconds']==b['startSeconds'] for a,b in zip(segments,segments[1:]))
print(f'PASS: {len(map_ids)} map(s), {count} lineup(s), references and coordinate bounds valid')
