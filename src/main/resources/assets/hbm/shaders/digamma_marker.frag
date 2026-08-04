#version 120

varying vec2 v_uv;

uniform float u_time;      // seconds elapsed (tick-driven), drives the pulse
uniform float u_progress;  // 0 -> 1 across the whole event, brightens the ring as things escalate

void main() {

	vec2 centered = (v_uv - vec2(0.5)) * 2.0; // -1 to 1 across the quad
	float dist = length(centered);

	float pulse = 0.92 + 0.08 * sin(u_time * 5.0);

	float discRadius = 0.55;
	float ringInner = discRadius;
	float ringOuter = 0.85 * pulse;

	// solid black core - this is the "hole" itself
	float disc = 1.0 - smoothstep(discRadius - 0.03, discRadius, dist);

	// bright, sharp-edged glowing rim around it - not a soft blob, an actual outline
	float ring = smoothstep(ringInner - 0.02, ringInner + 0.03, dist)
			- smoothstep(ringOuter - 0.05, ringOuter, dist);
	ring = clamp(ring, 0.0, 1.0);

	// past the ring, fully transparent - no dot/blob halo
	float outside = 1.0 - smoothstep(ringOuter, ringOuter + 0.05, dist);

	vec3 ringColor = vec3(1.0, 0.18, 0.04) * (0.85 + 0.3 * u_progress);

	vec3 finalColor = ringColor * ring;
	float finalAlpha = clamp((disc * 0.97 + ring) * outside, 0.0, 1.0);

	gl_FragColor = vec4(finalColor, finalAlpha);
}
