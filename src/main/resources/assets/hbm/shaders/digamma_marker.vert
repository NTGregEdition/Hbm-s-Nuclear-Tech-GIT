#version 120

varying vec2 v_uv;

void main() {
	v_uv = gl_MultiTexCoord0.st;
	gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
}
