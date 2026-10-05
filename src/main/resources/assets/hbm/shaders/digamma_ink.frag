#version 120

// Spreads ink over every surface and rolls red waves through it. Works purely from the depth buffer,
// so blocks, mobs and everything else that wrote depth are covered; the sky (depth 1.0) is left alone.

uniform sampler2D uScene;
uniform sampler2D uDepth;
uniform mat4 uInvViewProj;
uniform vec3 uCamRel;	// camera position relative to the singularity
uniform float uTime;	// seconds
uniform float uInk;		// 0 = clean, ~0.8 and up = everything covered
uniform float uWave;	// strength of the red waves
uniform float uShock;	// radius of the shockwave ring in blocks, negative when there is none
uniform float uRumble;	// 1 = ripple the ground, 0 = keep the picture still

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

// thin bright crests travelling outward from the singularity, wobbled by noise so they never look like perfect circles
float waves(float d, vec3 p) {
	float wob = noise(p * 0.06 + vec3(0.0, uTime * 0.25, 0.0)) * 3.0;
	float a = pow(max(sin(d * 0.13 - uTime * 5.0 + wob), 0.0), 5.0);
	float b = pow(max(sin(d * 0.41 - uTime * 11.0 - wob * 1.6), 0.0), 10.0) * 0.45;
	return clamp(a + b, 0.0, 1.0);
}

void main() {

	vec2 uv = gl_TexCoord[0].xy;
	float depth = texture2D(uDepth, uv).r;

	vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
	vec4 wp = uInvViewProj * clip;
	vec3 rel = wp.xyz / wp.w;

	// derivatives have to be taken before any pixel leaves early
	vec3 dx = dFdx(rel);
	vec3 dy = dFdy(rel);

	vec4 scene = texture2D(uScene, uv);
	if(depth >= 0.99999) {
		gl_FragColor = scene;
		return;
	}

	vec3 p = rel + uCamRel;
	float dist = length(p);

	// thin crests and fine noise alias badly at a distance, so settle them down there
	float far = smoothstep(120.0, 380.0, length(rel));

	float crest = waves(dist, p) * uWave * (1.0 - 0.7 * far);
	float ring = exp(-pow((dist - uShock) / 9.0, 2.0)) * step(0.0, uShock);
	float heave = crest + ring * 1.5;

	// the ground heaves under the crests
	vec2 off = vec2(sin(uTime * 41.0 + p.y * 0.8) * 0.0012 * min(heave, 1.0), heave * 0.010) * uRumble;
	vec2 uv2 = clamp(uv + off, vec2(0.0), vec2(1.0));
	if(texture2D(uDepth, uv2).r >= 0.99999) {
		uv2 = uv;
	}
	vec3 col = texture2D(uScene, uv2).rgb;

	// stretched along y: blobs on floors, drips down walls
	vec3 q = p * vec3(0.16, 0.055, 0.16) + vec3(0.0, uTime * 0.05, 0.0);
	float n = mix(fbm(q), 0.44, far);
	float gate = smoothstep(0.0, 0.1, uInk);
	float near = 1.0 - clamp(dist / 900.0, 0.0, 1.0);
	float level = uInk * 1.35 - 0.15 + (near * 0.3 + crest * 0.1) * gate - n;

	float m = smoothstep(0.0, 0.1, level);
	float rim = smoothstep(0.0, 0.02, level) * (1.0 - smoothstep(0.02, 0.08, level)) * (1.0 - far);

	vec3 nrm = normalize(cross(dx, dy) + vec3(0.0, 0.0001, 0.0));
	vec3 view = normalize(rel);
	if(dot(nrm, view) > 0.0) {
		nrm = -nrm;
	}
	float fres = pow(1.0 - clamp(-dot(nrm, view), 0.0, 1.0), 3.0);
	vec3 light = normalize(vec3(-0.35, 0.85, -0.4));
	float spec = pow(max(dot(reflect(-light, nrm), -view), 0.0), 48.0);

	// wet black that still keeps a trace of the shading underneath
	vec3 ink = vec3(0.010, 0.004, 0.013) + col * 0.04;
	ink += vec3(0.20, 0.01, 0.02) * fres * 0.35;
	ink += vec3(0.60, 0.12, 0.14) * spec * 0.7;

	col = mix(col, ink, m);

	// mixed toward red instead of added, so the waves and the rim read red over any block color
	float glow = clamp((crest + ring * 1.4) * (0.35 + 0.65 * m), 0.0, 1.0);
	col = mix(col, vec3(1.0, 0.05, 0.02), glow);
	col = mix(col, vec3(0.75, 0.03, 0.02), rim * 0.85 * gate);

	gl_FragColor = vec4(col, scene.a);
}
