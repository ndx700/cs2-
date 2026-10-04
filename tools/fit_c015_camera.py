"""Reproducible C015 image-fit candidates; no promotion to runtime calibration."""
import argparse
import hashlib
import json
import math
import tempfile
import zipfile
from pathlib import Path

import numpy as np
from scipy.optimize import least_squares

from c015_scene_geometry import SceneGeometry, camera_basis, pixel_ray
from dust2_collision_query import CollisionQuery, display_to_source


def camera_from_parameters(values, name='candidate'):
    eye = np.asarray(values[:3], dtype=float)
    yaw, pitch, fov, stretch = values[3:]
    direction = np.array([math.sin(yaw)*math.cos(pitch), math.sin(pitch), -math.cos(yaw)*math.cos(pitch)])
    return {'name': name, 'eye': eye.tolist(), 'target': (eye+direction).tolist(),
            'verticalFovDegrees': math.degrees(fov), 'horizontalStretch': float(stretch)}


def project(view, points, viewport):
    width, height = viewport
    eye = np.asarray(view['eye'], dtype=float)
    right, up, direction = camera_basis(eye, view['target'])
    delta = np.asarray(points) - eye
    depth = delta @ direction
    scale = height / 2 / math.tan(math.radians(view['verticalFovDegrees']) / 2)
    return np.column_stack((width/2+view.get('horizontalStretch', 1)*scale*(delta@right)/depth,
                            height/2-scale*(delta@up)/depth))


BOUNDS = ([-20, 2, 12, -1, -.4, math.radians(35), .8],
          [-5, 8, 30, 1, .5, math.radians(100), 1.6])


def fit_standing(points, pixels, viewport, initial=None):
    initial = initial if initial is not None else [-10, 5.5, 18, 0, .15, math.radians(74), 1.33]
    residual = lambda x: (project(camera_from_parameters(x), points, viewport)-pixels).ravel()
    fit = least_squares(residual, initial, bounds=BOUNDS, loss='soft_l1', f_scale=3, max_nfev=2000)
    if not fit.success:
        raise ValueError('camera fit failed: '+fit.message)
    return fit.x, residual(fit.x).reshape(-1, 2)


def app_angles(view):
    direction = np.asarray(view['target']) - view['eye']
    direction /= np.linalg.norm(direction)
    return float(math.degrees(math.atan2(-direction[0], -direction[2]))), float(-math.degrees(math.asin(direction[1])))


def build(root, input_path, out):
    payload = input_path.read_bytes()
    data = json.loads(payload)
    scene = SceneGeometry(root/'app/src/main/assets')
    if data['mapVersion'] != scene.map_version or data['lessonId'] != 'D2-001':
        raise ValueError('candidate source/map mismatch')
    out.mkdir(parents=True, exist_ok=True)
    groups = {}
    for key in ('standing', 'crouching'):
        group = data[key]
        frame = (root/group['referenceFrame']).resolve()
        if not frame.is_relative_to(root.resolve()) or hashlib.sha256(frame.read_bytes()).hexdigest() != group['referenceSha256']:
            raise ValueError('reference frame hash/path mismatch')
        records = []
        for row in group['landmarks']:
            hit = scene.pick(*pixel_ray(group['seedView'], row['seedPixel'], *data['viewport']))
            if hit is None:
                raise ValueError('seed pixel has no map geometry')
            records.append(dict(row, meshHit=hit, worldCandidate=hit['position']))
        groups[key] = records
    points = np.array([r['worldCandidate'] for r in groups['standing']])
    pixels = np.array([r['referencePixel'] for r in groups['standing']])
    values, residual = fit_standing(points, pixels, data['viewport'])
    standing = camera_from_parameters(values, 'D2-001-aim-fit-video-aspect')
    for row, error in zip(groups['standing'], residual):
        row['residualPixels'] = error.tolist()
    # Sensitivity is conditional on the same map/picks/model, not a calibrated accuracy bound.
    rng = np.random.default_rng(15)
    perturbed = []
    for _ in range(64):
        x, _ = fit_standing(points, pixels+rng.normal(0, data['pointUncertaintyPixels'], pixels.shape), data['viewport'], values)
        perturbed.append(x)
    perturbed = np.asarray(perturbed)
    sensitivity = {'method': '64 deterministic Gaussian reference-pixel perturbations; sigma=3px; fixed world picks',
                   'seed': 15, 'eyeDisplayPercentile5': np.percentile(perturbed[:, :3], 5, axis=0).tolist(),
                   'eyeDisplayPercentile95': np.percentile(perturbed[:, :3], 95, axis=0).tolist(),
                   'verticalFovPercentiles5_95': np.degrees(np.percentile(perturbed[:, 5], [5, 95])).tolist(),
                   'horizontalStretchPercentiles5_95': np.percentile(perturbed[:, 6], [5, 95]).tolist(),
                   'limitation': 'Conditional fitting sensitivity only; model/game version, seed corner selection and video scaling errors excluded.'}
    crouch_points = np.array([r['worldCandidate'] for r in groups['crouching']])
    crouch_pixels = np.array([r['referencePixel'] for r in groups['crouching']])
    def crouch_view(x):
        return camera_from_parameters([values[0], x[0], values[2], x[1], x[2], values[5], values[6]], 'D2-001-crouch-seam-fit')
    crouch_fit = least_squares(lambda x: (project(crouch_view(x), crouch_points, data['viewport'])-crouch_pixels).ravel(),
                              [3.2, 0, -.55], bounds=([2.5, -.5, -1], [3.8, .5, -.1]), loss='soft_l1', f_scale=3)
    if not crouch_fit.success:
        raise ValueError('crouch camera fit failed')
    crouch = crouch_view(crouch_fit.x)
    crouch_residual = project(crouch, crouch_points, data['viewport'])-crouch_pixels
    for row, error in zip(groups['crouching'], crouch_residual):
        row['residualPixels'] = error.tolist()
    display_support = scene.pick(standing['eye'], [0, -1, 0], 12)
    archive = root/'docs/calibration/C013/collision/world-collision-raw-v1.zip'
    with tempfile.TemporaryDirectory() as temp:
        with zipfile.ZipFile(archive) as z:
            for info in z.infolist():
                target = (Path(temp)/info.filename).resolve()
                if not target.is_relative_to(Path(temp).resolve()):
                    raise ValueError('collision archive traversal')
            z.extractall(temp)
        manifest = next(Path(temp).rglob('manifest.json'))
        collision = CollisionQuery.from_raw(manifest.parent)
        support = collision.ground_below(standing['eye'], max_drop=12, layers=[0])
        probes = []
        for dx, dz in [(0, 0), (.1, 0), (-.1, 0), (0, .1), (0, -.1)]:
            eye = np.asarray(standing['eye'])+[dx, 0, dz]
            probes.append({'offsetDisplayXZ': [dx, dz], 'hit': collision.ground_below(eye, max_drop=12, layers=[0])})
    if support is None or display_support is None:
        raise ValueError('candidate has no ground support')
    foot = support['positionDisplay']
    aim_hit = scene.pick(*pixel_ray(standing, [data['viewport'][0]/2, data['viewport'][1]/2], *data['viewport']))
    yaw, pitch = app_angles(standing)
    aim_camera = {'position': standing['eye'], 'yaw': yaw, 'pitch': pitch, 'distance': 1}
    material = scene.manifest['materials'][aim_hit['materialId']]
    textures = [t for t in scene.manifest['textures'] if t['asset'] in [material['base'], material['layer'], material['blend']]]
    normal = dict(standing, name='D2-001-aim-normal-aspect', horizontalStretch=1)
    overview = {'name': 'D2-001-stance-overview', 'eye': [foot[0]+3, foot[1]+5, foot[2]+3],
                'target': foot, 'verticalFovDegrees': 55, 'horizontalStretch': 1}
    package = {'schema': 'c015-spatial-evidence-v1', 'lessonId': 'D2-001', 'referenceRevision': data['sourceRevision'],
               'status': 'PENDING_CALIBRATION', 'importable': False, 'mapVersion': scene.map_version,
               'coordinateSpace': 'display-m-y-up-v1', 'inputSha256': hashlib.sha256(payload).hexdigest(),
               'candidateValues': {'foot': foot, 'eye': standing['eye'], 'aim': aim_hit['position'], 'bodyYaw': yaw,
                                   'aimFov': standing['verticalFovDegrees'],
                                   'cameras': {'stance': None, 'aim': aim_camera, 'overview': None, 'follow': None, 'landing': None}},
               'verifiedRuntimeValues': {'foot': None, 'eye': None, 'aim': None, 'bodyYaw': None, 'aimFov': None},
               'sourceSpaceCandidates': {'foot': display_to_source(foot).tolist(), 'eye': display_to_source(standing['eye']).tolist(),
                                         'aim': display_to_source(aim_hit['position']).tolist(), 'semantics': 'computed inverse of display convention; not console measurement'},
               'fit': {'standing': {'camera': standing, 'landmarks': groups['standing'], 'rmsePixelsPerCoordinate': float(np.sqrt(np.mean(residual**2)))},
                       'crouching': {'camera': crouch, 'landmarks': groups['crouching'], 'rmsePixelsPerCoordinate': float(np.sqrt(np.mean(crouch_residual**2))),
                                     'constraint': 'same horizontal position and projection as standing; eye height independently fitted'},
                       'conditionalSensitivity': sensitivity},
               'support': {'displayGeometryHit': display_support, 'worldPhysicsHit': support, 'queryLayers': [0],
                           'collisionArchiveSha256': hashlib.sha256(archive.read_bytes()).hexdigest(),
                           'displayCollisionHeightDifference': float(abs(display_support['position'][1]-foot[1])),
                           'eyeHeightAboveSupport': standing['eye'][1]-foot[1], 'crouchEyeHeightAboveSupport': crouch['eye'][1]-foot[1],
                           'nearbyProbes': probes, 'standingSemanticsVerified': False,
                           'limitation': 'Ground support consistency does not certify player hull fit, corner alignment or original CS2 stance.'},
               'aimSurface': aim_hit, 'localMaterialAudit': {'material': material, 'textures': textures, 'changes': [],
                   'referenceConsistent': None, 'phoneReadableWithoutHint': None,
                   'issue': 'Window/roof silhouette approximately matches; crosshair wall stain and crouch wall edge remain visually inconsistent. Material 38 blend and source game/map version need comparison.'},
               'videoProjection': {'horizontalStretchCandidate': standing['horizontalStretch'], 'recordingModeConfirmed': False,
                   'runtimeNote': 'Horizontal stretch is evidence-only. Existing runtime schema remains unchanged; use normal aspect to inspect candidate world camera.'},
               'runtimeDraft': 'docs/calibration/C014/app-pending/D2-001.json',
               'throwFlags': {'crouchToAlign': True, 'standBeforeRelease': True, 'jump': True, 'leftClick': True},
               'trajectory': None, 'landing': None, 'releaseRelativeEffectTimes': None,
               'missing': ['semantic wall seam/contact and stain verification', 'CS2 map/build and native video projection',
                           'APP capture at candidate stance and aim without hints', 'release/path/bounces/landing and complete smoke lifecycle',
                           'follow/landing cameras and phone acceptance'],
               'acceptance': {'calibrated': False, 'phoneVerified': False, 'gameMeasured': False}}
    write = lambda path, value: path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n')
    write(out/'D2-001-spatial-candidate.json', package)
    write(out/'D2-001-render-views.json', {'width': data['viewport'][0], 'height': data['viewport'][1], 'views': [standing, normal, crouch, overview]})
    print(json.dumps({'lessonId': 'D2-001', 'status': package['status'], 'candidate': package['candidateValues'],
                      'standingRmse': package['fit']['standing']['rmsePixelsPerCoordinate'], 'sensitivity': sensitivity,
                      'supportHeightDifference': package['support']['displayCollisionHeightDifference']}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    root = args.root.resolve()
    build(root, root/'docs/calibration/C015/D2-001-correspondences.json', root/'docs/calibration/C015')
