#version 300 es
precision highp float;
uniform sampler2D uBrushTex;
uniform vec4 uColor;
uniform float uOpacity;
out vec4 FragColor;
void main() {
    vec2 texCoord = gl_PointCoord;
    vec4 texColor = texture(uBrushTex, texCoord);
    FragColor = texColor * uColor;
    FragColor.a *= uOpacity;
}
