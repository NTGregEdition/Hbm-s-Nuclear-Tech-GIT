#version 120

uniform float u_whiteAmount;  // 0 (black) -> 1 (white) - the charge-phase ramp, and the final flash
uniform float u_alpha;        // overall overlay opacity, 0.0 - 1.0

void main() {
	gl_FragColor = vec4(vec3(u_whiteAmount), u_alpha);
}
