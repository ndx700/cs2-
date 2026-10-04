precision mediump float;
uniform sampler2D uBase,uLayer,uBlendMap;
uniform int uHasBase,uHasLayer,uHasBlend,uMode,uBlendMode,uPaint;
uniform float uCutoff,uOpacity,uSoftness;
uniform vec3 uTint;
uniform vec2 uLayerScale,uLayerOffset,uBlendScale;
varying mediump vec2 vUV;
varying mediump vec4 vColor,vBlend;
varying mediump float vLight;
void main() {
    vec4 c=uHasBase==1?texture2D(uBase,vUV):vec4(1.0);
    c.rgb=pow(max(c.rgb,vec3(0.0)),vec3(2.2));
    if(uHasLayer==1) {
        vec4 b=texture2D(uLayer,vUV*uLayerScale+uLayerOffset);
        b.rgb=pow(max(b.rgb,vec3(0.0)),vec3(2.2));
        float f=vBlend.r;
        if(uHasBlend==1 && uBlendMode>0) {
            vec4 mask=texture2D(uBlendMap,vUV*uBlendScale);
            float m=uBlendMode==3?mask.a:mask.g;
            float s=uBlendMode==1?max(mask.r,0.001):uSoftness;
            float lo=max(0.0,m-s),hi=min(1.0,m+s);
            f=smoothstep(lo,max(lo+0.001,hi),f);
        }
        c=mix(c,b,f);
    }
    if(uMode==1 && c.a<uCutoff)discard;
    float a=uMode==2?c.a*uOpacity:1.0;
    vec3 tint=pow(max(uTint,vec3(0.0)),vec3(2.2));
    if(uPaint==1)tint*=pow(max(vColor.rgb,vec3(0.0)),vec3(2.2));
    gl_FragColor=vec4(pow(max(c.rgb*tint*vLight,vec3(0.0)),vec3(1.0/2.2)),a);
}
