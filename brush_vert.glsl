#version 300 es
in vec2 aPos;
uniform vec2 uOffset;
uniform vec2 uSize; // width, height of brush stamp
void main() {
    vec2 pos = aPos * uSize + uOffset;
    gl_Position = vec4(pos, 0.0, 1.0);
}
