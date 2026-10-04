import importlib.util
import unittest
from pathlib import Path
import numpy as np

spec=importlib.util.spec_from_file_location('capture',Path(__file__).parents[1]/'c015_display_capture.py')
capture=importlib.util.module_from_spec(spec);spec.loader.exec_module(capture)

class CaptureMathTest(unittest.TestCase):
    def pose(self,free):return {'position':[1,2,3],'yaw':0,'pitch':30,'distance':10,'free':free}
    def test_orbit_target_is_not_eye(self):
        eye,fov,_=capture.camera_frame(self.pose(False),1200,800,191)
        np.testing.assert_allclose(eye,[1,7,3+10*np.cos(np.pi/6)])
        self.assertEqual(fov,45)
    def test_free_eye_and_direction_match_android(self):
        eye,fov,vp=capture.camera_frame(self.pose(True),1200,800,191)
        origin,direction=capture.ray_at(eye,vp,1200,800,600,400)
        np.testing.assert_allclose(origin,[1,2,3]);np.testing.assert_allclose(direction,[0,-.5,-np.cos(np.pi/6)],atol=1e-6);self.assertEqual(fov,70)
    def test_different_aspect_keeps_center_aim(self):
        results=[]
        for w,h in [(1200,800),(800,1200)]:
            eye,_,vp=capture.camera_frame(self.pose(True),w,h,191)
            results.append(capture.ray_at(eye,vp,w,h,w/2,h/2)[1])
        np.testing.assert_allclose(*results,atol=1e-6)
    def test_ray_sphere_inside_behind_and_miss(self):
        o=np.array([0,2,0]);d=np.array([0,-1,0])
        self.assertEqual(capture.sphere_entry(o,d,[0,2,0],1,10),0)
        self.assertIsNone(capture.sphere_entry(o,d,[0,8,0],1,10))
        self.assertIsNone(capture.sphere_entry(o,d,[8,0,0],1,10))
    def test_invalid_orbit_pitch_rejected(self):
        c=self.pose(False);c['pitch']=0
        with self.assertRaises(AssertionError):capture.camera_frame(c,1200,800,191)
    def test_nonfinite_camera_rejected(self):
        c=self.pose(True);c['position'][0]=float('nan')
        with self.assertRaises(AssertionError):capture.camera_frame(c,1200,800,191)

if __name__=='__main__':unittest.main()
