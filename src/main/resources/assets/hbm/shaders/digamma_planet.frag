#version 120

// Digamma corruption on a body seen from the sky: black spreads out from the impact site, red crests run
// across it and red veins glow in the dark. Snapped to a pixel grid so it fits the body textures.

uniform float uTime;		// seconds
uniform float uInk;			// 0 = clean, 1 = the whole body is covered
uniform float uWave;		// strength of the red crests
uniform float uPulse;		// heartbeat, 0 to 1
uniform float uBoom;		// seconds since the detonation, negative before it
uniform float uFlash;		// strength of the detonation flash
uniform float uOffset;		// texture scroll of a body that is being orbited
uniform float uVisibility;
uniform vec2 uOrigin;		// impact site in surface coordinates

#define GRID 64.0
#define TAU 6.2831853

float hash(vec3 p) {
	p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
	p *= 17.0;
	return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise(vec3 x) {
	vec3 i = floor(x);
	vec3 f = fract(x);
	f = f * f * (3.0 - 2.0 * f);
	return mix(mix(mix(hash(i + vec3(0.0, 0.0, 0.0)), hash(i + vec3(1.0, 0.0, 0.0)), f.x),
	               mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),
	           mix(mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), f.x),
	               mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
	float v = 0.0;
	float a = 0.5;
	for(int i = 0; i < 3; i++) {
		v += a * noise(p);
		p = p * 2.03 + vec3(7.1, 3.7, 5.3);
		a *= 0.5;
	}
	return v;
}

// wraps around the body horizontally, so the pattern has no seam while the surface scrolls
vec3 surface(vec2 s, float scale) {
	float a = s.x * TAU;
	float r = scale / TAU;
	return vec3(cos(a) * r, sin(a) * r, s.y * scale);
}

vec4 over(vec4 top, vec4 bottom) {
	float a = top.a + bottom.a * (1.0 - top.a);
	vec3 c = (top.rgb * top.a + bottom.rgb * bottom.a * (1.0 - top.a)) / max(a, 0.0001);
	return vec4(c, a);
}

float posterize(float v, float steps) {
	return floor(v * steps + 0.5) / steps;
}

void main() {

	// one pixel of the grid in surface space, so the pixels travel with the planet texture
	vec2 s = (floor((gl_TexCoord[0].xy + vec2(uOffset, 0.0)) * GRID) + 0.5) / GRID;
	s.x = fract(s.x);

	vec2 d = abs(s - uOrigin);
	d.x = min(d.x, 1.0 - d.x);
	float dist = length(d);

	// the black: a noisy front moving out from the impact site, with thin tendrils racing ahead of it
	float gate = smoothstep(0.0, 0.04, uInk);
	float n = fbm(surface(s, 5.0) + vec3(0.0, 0.0, uTime * 0.04));
	float ridge = 1.0 - abs(2.0 * fbm(surface(s, 8.0) + 3.7) - 1.0);
	float level = uInk * uInk * 1.2 + 0.02 - dist + (n - 0.45) * 0.55 + pow(ridge, 6.0) * 0.2;

	float inside = step(0.0, level) * gate;
	float rim = inside * (1.0 - step(0.05, level));
	float halo = posterize((1.0 - inside) * gate * smoothstep(-0.16, 0.0, level), 3.0);

	// thin crests travelling outward from the impact site
	float wob = noise(surface(s, 3.0) + vec3(0.0, 0.0, uTime * 0.25)) * 3.0;
	float crestA = pow(max(sin(dist * TAU * 2.5 - uTime * 4.0 + wob), 0.0), 12.0);
	float crestB = pow(max(sin(dist * TAU * 7.0 - uTime * 9.0 - wob * 1.6), 0.0), 14.0) * 0.4;
	float crest = posterize(clamp(crestA + crestB, 0.0, 1.0), 3.0);

	// glowing cracks inside the black, with energy running out along them
	float crackBase = 1.0 - abs(2.0 * noise(surface(s, 7.0) + vec3(0.0, 0.0, uTime * 0.08)) - 1.0);
	float flow = 0.55 + 0.45 * sin(dist * TAU * 4.0 - uTime * 4.0);
	float growth = inside * smoothstep(0.15, 0.7, uInk);
	float vein = step(0.3, pow(crackBase, 18.0)) * flow * growth;
	float veinGlow = posterize(pow(crackBase, 4.0), 4.0) * growth;

	float flicker = noise(vec3(s * 40.0, uTime * 6.0));

	vec3 inkColor = vec3(0.012, 0.004, 0.014) + vec3(0.07, 0.0, 0.012) * smoothstep(0.35, 0.75, n);

	vec4 c = vec4(inkColor, inside);
	c = over(vec4(0.55, 0.02, 0.03, halo * 0.45), c);
	c = over(vec4(1.0, 0.07, 0.03, crest * uWave * (0.22 + 0.78 * inside)), c);
	c = over(vec4(0.60, 0.03, 0.03, veinGlow * 0.3 * (0.4 + 0.6 * uPulse)), c);
	c = over(vec4(1.0, 0.28, 0.10, vein * (0.35 + 0.65 * uPulse)), c);
	c = over(vec4(mix(vec3(0.85, 0.05, 0.03), vec3(1.0, 0.45, 0.18), flicker), rim * (0.7 + 0.3 * uPulse)), c);

	// the detonation: a white-hot flare at the impact site and a ring racing across the body
	float bt = max(uBoom, 0.0);
	float on = step(0.0, uBoom) * uFlash;
	float core = posterize(on * exp(-bt * 2.5) * (1.0 - smoothstep(0.0, 0.2 + bt * 0.7, dist)), 5.0);
	float ring = on * exp(-bt * 1.1) * (1.0 - step(0.03, abs(dist - bt * 0.9)));
	float blast = on * exp(-bt * 9.0);
	c = over(vec4(1.0, 0.40, 0.18, blast * 0.55), c);
	c = over(vec4(mix(vec3(1.0, 0.20, 0.08), vec3(1.0, 0.93, 0.80), core * core), clamp(core * 1.6, 0.0, 1.0)), c);
	c = over(vec4(1.0, 0.35, 0.20, clamp(ring, 0.0, 1.0)), c);

	gl_FragColor = vec4(c.rgb, c.a * uVisibility);
}
