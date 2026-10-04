attribute vec3 aPosition;
attribute vec3 aNormal;
attribute vec2 aUV;
attribute vec4 aColor;
attribute vec4 aBlend;
uniform mat4 uVP;
uniform vec2 uUVScale,uUVOffset;
uniform float uUVRotation;
varying mediump vec2 vUV;
varying mediump vec4 vColor,vBlend;
varying mediump float vLight;
void main() {
    gl_Position=uVP*vec4(aPosition,1.0);
    float r=radians(uUVRotation);
    vec2 p=aUV-vec2(0.5);
    vUV=(mat2(cos(r),sin(r),-sin(r),cos(r))*p)*uUVScale+vec2(0.5)+uUVOffset;
    vColor=aColor;vBlend=aBlend;
    vLight=0.72+0.28*abs(dot(normalize(aNormal),normalize(vec3(0.3,0.8,0.5))));
}
