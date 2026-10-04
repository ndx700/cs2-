"""Validate pending spatial candidates and evidence bindings, not calibration."""
import hashlib
import json
from pathlib import Path

import numpy as np

from dust2_collision_query import source_to_display


def validate(root):
    folder = root/'docs/calibration/C015'
    candidate = json.loads((folder/'D2-001-spatial-candidate.json').read_text())
    if candidate['lessonId'] != 'D2-001' or candidate['referenceRevision'] != 'user-selected-20261004-v2':
        raise ValueError('wrong selected lesson/source')
    if candidate['status'] != 'PENDING_CALIBRATION' or candidate['importable'] is not False or any(candidate['acceptance'].values()):
        raise ValueError('candidate promoted without acceptance')
    if any(v is not None for v in candidate['verifiedRuntimeValues'].values()):
        raise ValueError('image estimates are not verified runtime values')
    if any(candidate[k] is not None for k in ('trajectory', 'landing', 'releaseRelativeEffectTimes')):
        raise ValueError('unknown path/effect values must remain pending')
    actual_map = hashlib.sha256((root/'app/src/main/assets/maps/dust2/mobile/manifest.json').read_bytes()).hexdigest()
    if candidate['mapVersion'] != actual_map:
        raise ValueError('map mismatch')
    if candidate['inputSha256'] != hashlib.sha256((folder/'D2-001-correspondences.json').read_bytes()).hexdigest():
        raise ValueError('correspondence mismatch')
    for key in ('foot', 'eye', 'aim'):
        point = candidate['candidateValues'][key]
        if np.asarray(point).shape != (3,) or not np.isfinite(point).all() or not np.allclose(source_to_display(candidate['sourceSpaceCandidates'][key]), point, atol=1e-8):
            raise ValueError('candidate coordinate conversion mismatch')
    draft = json.loads((root/candidate['runtimeDraft']).read_text())
    if draft['importable'] is not False or draft['status'] != 'PENDING_CALIBRATION' or any(draft[key] is not None for key in ('foot', 'eye', 'aim', 'path', 'duration')):
        raise ValueError('formal runtime draft was overwritten by image guesses')
    report = json.loads((folder/'renders/render-report.json').read_text())
    if report['mapVersion'] != actual_map or report['inputSha256'] != hashlib.sha256((folder/'D2-001-render-views.json').read_bytes()).hexdigest():
        raise ValueError('render map/camera mismatch')
    for row in report['renders']:
        file = (folder/'renders'/row['image']).resolve()
        if not file.is_relative_to((folder/'renders').resolve()) or hashlib.sha256(file.read_bytes()).hexdigest() != row['sha256']:
            raise ValueError('render artifact mismatch')
    comparison = json.loads((folder/'comparison-evidence.json').read_text())
    for entry in [comparison] + comparison['inputs']:
        path = (root/entry.get('path', entry.get('image'))).resolve()
        if not path.is_relative_to(root.resolve()) or hashlib.sha256(path.read_bytes()).hexdigest() != entry['sha256']:
            raise ValueError('comparison evidence mismatch')
    support = candidate['support']
    archive = root/'docs/calibration/C013/collision/world-collision-raw-v1.zip'
    if support['collisionArchiveSha256'] != hashlib.sha256(archive.read_bytes()).hexdigest() or support['queryLayers'] != [0] or support['standingSemanticsVerified'] is not False:
        raise ValueError('support evidence/layer/semantics mismatch')
    if support['displayCollisionHeightDifference'] > .02:
        raise ValueError('support surfaces disagree')
    aim = candidate['aimSurface']
    material = candidate['localMaterialAudit']['material']
    if hashlib.sha256((root/'app/src/main/assets'/aim['part']).read_bytes()).hexdigest() != aim['partSha256'] or aim['materialId'] != material['id']:
        raise ValueError('aim mesh/material binding mismatch')
    receipt_path = folder/'app-capture-receipt.json'
    if receipt_path.exists():
        receipt = json.loads(receipt_path.read_text())
        if receipt['lessonId'] != 'D2-001' or receipt['mapVersion'] != actual_map or receipt['importable'] is not False or receipt['projection']['sameProjectionConfirmed'] is not False:
            raise ValueError('capture identity or projection promoted')
        for binding in receipt['bindings']:
            path = (root/binding['path']).resolve()
            if not path.is_relative_to(root.resolve()) or hashlib.sha256(path.read_bytes()).hexdigest() != binding['sha256']:
                raise ValueError('APP capture binding mismatch')
        capture = json.loads((folder/'app-capture/capture.json').read_text())
        if capture['field'] != 'aim' or capture['verticalFov'] != 70 or capture['surface']['part'] != aim['part'] or capture['surface']['triangle'] != aim['triangleInPart']:
            raise ValueError('actual APP probe does not agree with reported wall')
        if np.linalg.norm(np.asarray(capture['value'])-aim['position']) > .0001:
            raise ValueError('independent aim query mismatch')
    print('C015 candidate/source/map/coordinate/render/support isolation checks passed; no calibration or phone acceptance')
    return candidate


if __name__ == '__main__':
    validate(Path(__file__).resolve().parents[1])
