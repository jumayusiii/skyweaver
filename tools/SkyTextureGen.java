import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Skyweaver procedural sky generator.
 *
 * Produces seamless equirectangular sky hemispheres (4096x2048), plus sun/moon
 * sprites and small fallback textures. Everything is generated from value/gradient
 * noise, no external assets.
 *
 * Usage:
 *   java tools/SkyTextureGen.java            -> resource pack (4K)
 *   java tools/SkyTextureGen.java --fallback -> 512px built-in textures for the mod jar
 */
public final class SkyTextureGen {
    static final float TAU = (float) (Math.PI * 2.0);
    static final float HALF_PI = (float) (Math.PI / 2.0);

    // ---------------------------------------------------------------- palette

    record Palette(
            String file,
            float[] zenith,
            float[] horizon,
            float[] horizonCool,
            float[] glow,
            float glowBroad,
            float glowTight,
            float sunElevation,
            float coverage,
            float stretch,
            float[] cloudLit,
            float[] cloudShadow,
            float cloudOpacity,
            float rim,
            boolean stars,
            boolean aurora,
            float exposure
    ) {}

    static final Palette DAY = new Palette(
            "day",
            new float[]{0.065f, 0.22f, 0.62f},
            new float[]{0.60f, 0.78f, 0.95f},
            new float[]{0.56f, 0.74f, 0.93f},
            new float[]{1.00f, 0.95f, 0.82f},
            0.22f, 0.80f, (float) Math.toRadians(46.0),
            0.50f, 1.0f,
            new float[]{1.00f, 0.99f, 0.97f},
            new float[]{0.52f, 0.59f, 0.71f},
            0.96f, 0.35f, false, false, 1.0f
    );

    static final Palette SUNSET = new Palette(
            "sunset",
            new float[]{0.055f, 0.11f, 0.30f},
            new float[]{1.00f, 0.48f, 0.16f},
            new float[]{0.20f, 0.19f, 0.36f},
            new float[]{1.00f, 0.55f, 0.20f},
            0.55f, 1.40f, (float) Math.toRadians(2.0),
            0.46f, 2.1f,
            new float[]{1.00f, 0.62f, 0.40f},
            new float[]{0.26f, 0.19f, 0.30f},
            0.94f, 0.85f, false, false, 1.0f
    );

    static final Palette NIGHT = new Palette(
            "night",
            new float[]{0.008f, 0.020f, 0.060f},
            new float[]{0.045f, 0.090f, 0.150f},
            new float[]{0.030f, 0.060f, 0.115f},
            new float[]{0.16f, 0.22f, 0.36f},
            0.20f, 0.35f, (float) Math.toRadians(-14.0),
            0.30f, 1.3f,
            new float[]{0.10f, 0.13f, 0.21f},
            new float[]{0.025f, 0.035f, 0.065f},
            0.62f, 0.15f, true, false, 1.0f
    );

    static final Palette MIDNIGHT = new Palette(
            "midnight",
            new float[]{0.002f, 0.006f, 0.018f},
            new float[]{0.012f, 0.026f, 0.055f},
            new float[]{0.008f, 0.018f, 0.042f},
            new float[]{0.05f, 0.07f, 0.14f},
            0.06f, 0.10f, (float) Math.toRadians(-45.0),
            0.24f, 1.5f,
            new float[]{0.035f, 0.045f, 0.075f},
            new float[]{0.008f, 0.012f, 0.026f},
            0.45f, 0.05f, true, true, 1.0f
    );

    // ---------------------------------------------------------------- noise

    static final class Noise {
        private final int[] p = new int[512];

        Noise(long seed) {
            int[] perm = new int[256];
            for (int i = 0; i < 256; i++) perm[i] = i;
            Random r = new Random(seed);
            for (int i = 255; i > 0; i--) {
                int j = r.nextInt(i + 1);
                int t = perm[i];
                perm[i] = perm[j];
                perm[j] = t;
            }
            for (int i = 0; i < 512; i++) p[i] = perm[i & 255];
        }

        static float fade(float t) {
            return t * t * t * (t * (t * 6f - 15f) + 10f);
        }

        static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        static float grad(int hash, float x, float y, float z) {
            int h = hash & 15;
            float u = h < 8 ? x : y;
            float v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }

        float noise(float x, float y, float z) {
            int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
            float xf = x - xi, yf = y - yi, zf = z - zi;
            int X = xi & 255, Y = yi & 255, Z = zi & 255;
            float u = fade(xf), v = fade(yf), w = fade(zf);
            int A = p[X] + Y, AA = p[A] + Z, AB = p[A + 1] + Z;
            int B = p[X + 1] + Y, BA = p[B] + Z, BB = p[B + 1] + Z;
            return lerp(
                    lerp(lerp(grad(p[AA], xf, yf, zf), grad(p[BA], xf - 1, yf, zf), u),
                            lerp(grad(p[AB], xf, yf - 1, zf), grad(p[BB], xf - 1, yf - 1, zf), u), v),
                    lerp(lerp(grad(p[AA + 1], xf, yf, zf - 1), grad(p[BA + 1], xf - 1, yf, zf - 1), u),
                            lerp(grad(p[AB + 1], xf, yf - 1, zf - 1), grad(p[BB + 1], xf - 1, yf - 1, zf - 1), u), v),
                    w);
        }

        float fbm(float x, float y, float z, int octaves, float gain) {
            float amp = 0.5f, sum = 0f, norm = 0f;
            for (int i = 0; i < octaves; i++) {
                sum += amp * noise(x, y, z);
                norm += amp;
                x = x * 2.03f + 17.17f;
                y = y * 2.01f - 9.31f;
                z = z * 2.02f + 4.77f;
                amp *= gain;
            }
            return sum / norm;
        }
    }

    // ---------------------------------------------------------------- helpers

    static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static float smoothstep(float a, float b, float x) {
        float t = clamp01((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    static float[] mix3(float[] a, float[] b, float t) {
        return new float[]{mix(a[0], b[0], t), mix(a[1], b[1], t), mix(a[2], b[2], t)};
    }

    static int pack(float[] c, int alpha) {
        int r = (int) (clamp01(c[0]) * 255f + 0.5f);
        int g = (int) (clamp01(c[1]) * 255f + 0.5f);
        int b = (int) (clamp01(c[2]) * 255f + 0.5f);
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    static void add(float[] dst, float[] src, float amt) {
        dst[0] += src[0] * amt;
        dst[1] += src[1] * amt;
        dst[2] += src[2] * amt;
    }

    static float[] sunDir(Palette p) {
        float el = p.sunElevation();
        return new float[]{(float) (Math.cos(el)), (float) Math.sin(el), 0f};
    }

    // ---------------------------------------------------------------- clouds

    static final class CloudField {
        final int w, h;
        final float[] alpha, shade, density;

        CloudField(int w, int h) {
            this.w = w;
            this.h = h;
            alpha = new float[w * h];
            shade = new float[w * h];
            density = new float[w * h];
        }

        float sampleAlpha(float u, float v) {
            return bilinear(alpha, u, v);
        }

        float sampleShade(float u, float v) {
            return bilinear(shade, u, v);
        }

        private float bilinear(float[] data, float u, float v) {
            float fx = u * (w - 1), fy = v * (h - 1);
            int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
            float tx = fx - x0, ty = fy - y0;
            int x1 = (x0 + 1) % w;
            int y1 = Math.min(h - 1, y0 + 1);
            y0 = Math.max(0, Math.min(h - 1, y0));
            x0 = Math.floorMod(x0, w);
            float a = mix(data[y0 * w + x0], data[y0 * w + x1], tx);
            float b = mix(data[y1 * w + x0], data[y1 * w + x1], tx);
            return mix(a, b, ty);
        }
    }

    static CloudField buildClouds(Palette p, int scale) {
        int cw = (int) Math.round(4096.0 / scale);
        int ch = (int) Math.round(2048.0 / scale);
        CloudField field = new CloudField(cw, ch);
        Noise n = new Noise(1337L);
        float[] sd = sunDir(p);
        IntStream.range(0, ch).parallel().forEach(y -> {
            float theta = (1f - (y + 0.5f) / ch) * HALF_PI;
            float sinT = (float) Math.sin(theta);
            float cosT = (float) Math.cos(theta);
            float horizonFade = smoothstep(0.015f, 0.10f, sinT);
            for (int x = 0; x < cw; x++) {
                float phi = ((x + 0.5f) / cw - 0.5f) * TAU;
                float cosP = (float) Math.cos(phi), sinP = (float) Math.sin(phi);
                float dx = cosT * cosP, dy = sinT, dz = cosT * sinP;

                // Clouds live on a sphere around the observer (a painted skybox
                // rather than a flat deck). Mild perspective makes them a little
                // larger overhead and smaller near the horizon.
                float persp = 1.0f / (0.62f + 0.38f * sinT);
                float sx = 2.5f * persp / p.stretch();
                float sy = 4.2f * persp;
                float px = dx * sx + 13.1f;
                float py = dy * sy + 3.7f;
                float pz = dz * sx - 7.3f;

                float warp = n.fbm(px * 0.55f, py * 0.55f, pz * 0.55f, 2, 0.5f);
                float base = n.fbm(px + warp * 0.75f, py - warp * 0.75f, pz + 0.4f, 5, 0.55f);
                float billow = 1f - Math.abs(n.fbm(px * 1.7f, py * 1.7f, pz * 1.7f, 3, 0.5f));
                float detail = n.fbm(px * 3.1f, py * 3.1f, pz * 3.1f, 2, 0.5f);
                float d = 0.5f + base * 0.42f + (billow - 0.62f) * 0.22f + detail * 0.06f;
                float coverage = p.coverage();
                float mask = smoothstep(coverage, coverage + 0.13f, d);
                mask *= 0.55f + 0.45f * smoothstep(coverage + 0.02f, coverage + 0.28f, d);
                // Break the deck up with a second, larger scale field so it reads as
                // individual cloud streets instead of a uniform sheet.
                float gap = n.fbm(px * 0.22f + 31f, py * 0.22f, pz * 0.22f, 2, 0.5f);
                mask *= smoothstep(-0.22f, 0.04f, gap);
                mask *= horizonFade;

                // Fake directional lighting: sample the field slightly towards the sun.
                float shifted = n.fbm(px + warp * 0.75f + sd[0] * 0.30f,
                        py - warp * 0.75f + sd[1] * 0.45f, pz + 0.4f + sd[2] * 0.30f, 4, 0.55f);
                float delta = (base - shifted) * 2.8f;
                float shade = clamp01(0.63f + delta);

                int idx = y * cw + x;
                field.alpha[idx] = mask;
                field.shade[idx] = shade;
                field.density[idx] = clamp01(d);
            }
        });
        return field;
    }

    // ---------------------------------------------------------------- sky dome

    static BufferedImage renderDome(Palette p, int W, int H, int cloudScale) {
        int[] px = new int[W * H];
        CloudField clouds = buildClouds(p, cloudScale);
        float[] sd = sunDir(p);
        float sunEl = p.sunElevation();
        Noise noise = new Noise(99L);
        float galaxyTilt = (float) Math.toRadians(32.0);

        IntStream.range(0, H).parallel().forEach(y -> {
            float v = (y + 0.5f) / H;
            float theta = (1f - v) * HALF_PI;
            float sinT = (float) Math.sin(theta);
            float cosT = (float) Math.cos(theta);

            for (int x = 0; x < W; x++) {
                float u = (x + 0.5f) / W;
                float phi = (u - 0.5f) * TAU;
                float cosP = (float) Math.cos(phi), sinP = (float) Math.sin(phi);
                float dx = cosT * cosP, dy = sinT, dz = cosT * sinP;

                // Base atmospheric gradient, warmer towards the sun for sunset.
                float az = 0.5f + 0.5f * cosP;
                float[] horizon = mix3(p.horizonCool(), p.horizon(), (float) Math.pow(az, 0.7f));
                float lift = (float) Math.pow(clamp01(sinT), 0.42f);
                float[] col = mix3(horizon, p.zenith(), lift);
                // Subtle mid-sky band.
                float mid = smoothstep(0.0f, 0.55f, sinT) * (1f - smoothstep(0.55f, 0.95f, sinT));
                add(col, p.zenith(), mid * 0.06f);

                // Sun/mie scattering.
                float cosA = dx * sd[0] + dy * sd[1] + dz * sd[2];
                // Broad atmospheric scattering only: the sun and moon are drawn by the mod, so the
                // panorama must not bake in a second, stationary sun.
                float broad = (float) Math.exp(-(1f - cosA) * 8.5f) * p.glowBroad();
                float scatter = broad;
                add(col, p.glow(), scatter * (0.85f + 0.15f * clamp01(-sunEl * 4f)));

                // Horizon haze / airglow.
                float haze = (float) Math.exp(-sinT * 7.5f);
                add(col, horizon, haze * 0.35f);

                // Clouds.
                float ca = clouds.sampleAlpha(u, v);
                float alpha = ca * p.cloudOpacity();
                if (alpha > 0.002f) {
                    float sh = clouds.sampleShade(u, v);
                    float[] lit = mix3(p.cloudShadow(), p.cloudLit(), sh);
                    float rim = (float) Math.exp(-(1f - cosA) * 18f) * p.rim();
                    add(lit, p.glow(), rim * 0.55f);
                    col = mix3(col, lit, alpha);
                }

                // Milky way band.
                if (p.stars()) {
                    float lat = dy * (float) Math.cos(galaxyTilt) - dz * (float) Math.sin(galaxyTilt) * 0.55f
                            - dx * (float) Math.sin(galaxyTilt) * 0.35f;
                    float band = (float) Math.exp(-(lat * lat) / (2f * 0.085f * 0.085f));
                    float mwNoise = 0.55f + 0.45f * noise.fbm(dx * 3.4f, dy * 3.4f, dz * 3.4f, 4, 0.55f);
                    float dust = smoothstep(0.12f, 0.62f, 0.5f + 0.5f * noise.fbm(dx * 8.5f, dy * 8.5f, dz * 8.5f, 3, 0.5f));
                    float mw = band * mwNoise * (0.42f + 0.58f * dust) * (1f - alpha);
                    add(col, new float[]{0.52f, 0.60f, 0.82f}, mw * 0.16f);
                    add(col, new float[]{0.82f, 0.76f, 0.68f}, band * band * 0.05f);
                }

                // Aurora curtains (midnight only).
                if (p.aurora()) {
                    float bandV = smoothstep(0.03f, 0.22f, sinT) * (1f - smoothstep(0.50f, 0.78f, sinT));
                    float curtain = 0.5f + 0.5f * noise.fbm(cosP * 2.4f, sinP * 2.4f, sinT * 5.5f, 4, 0.55f);
                    float ray = (float) Math.pow(0.5f + 0.5f * noise.noise(cosP * 21f, sinP * 21f, sinT * 2.2f), 3);
                    float aur = bandV * (float) Math.pow(curtain, 2.2f) * ray * 0.20f;
                    add(col, new float[]{0.20f, 0.95f, 0.62f}, aur);
                    add(col, new float[]{0.35f, 0.45f, 1.00f}, aur * 0.35f);
                }

                // Gentle exposure curve so the horizon never clips harshly.
                col[0] = (float) Math.pow(clamp01(col[0] * p.exposure()), 0.94f);
                col[1] = (float) Math.pow(clamp01(col[1] * p.exposure()), 0.94f);
                col[2] = (float) Math.pow(clamp01(col[2] * p.exposure()), 0.94f);
                px[y * W + x] = pack(col, 255);
            }
        });

        if (p.stars()) {
            splatStars(px, W, H, clouds, new Random(4242L), H > 1500 ? 24000 : 5200);
        }
        return toImage(px, W, H);
    }

    static void splatStars(int[] px, int W, int H, CloudField clouds, Random rnd, int count) {
        for (int i = 0; i < count; i++) {
            float el = (float) Math.asin(rnd.nextDouble());       // uniform on the hemisphere
            float az = (float) (rnd.nextDouble() * TAU);
            float u = (float) ((0.5 + az / TAU) % 1.0);             // 0..1 with sun at 0.5
            float v = 1f - el / HALF_PI;                           // 1 = zenith, 0 = horizon
            if (v <= 0.02f) continue;
            int x = (int) (u * W) % W;
            int y = (int) (v * H);
            if (y < 0 || y >= H) continue;
            float occl = 1f - clouds.sampleAlpha(u, v) * 0.95f;
            if (occl < 0.05f) continue;

            float bright = (float) Math.pow(rnd.nextDouble(), 4.2) * 1.15f + 0.02f;
            float warm = rnd.nextFloat();
            float[] tint = mix3(new float[]{0.72f, 0.82f, 1.0f}, new float[]{1.0f, 0.88f, 0.70f}, warm * warm);
            float radius = 0.45f + bright * 0.85f;
            float energy = bright * 0.75f * occl;
            int r = (int) Math.ceil(radius * 2.2f);
            for (int oy = -r; oy <= r; oy++) {
                int yy = y + oy;
                if (yy < 0 || yy >= H) continue;
                for (int ox = -r; ox <= r; ox++) {
                    int xx = Math.floorMod(x + ox, W);
                    float d2 = ox * ox + oy * oy;
                    float fall = (float) Math.exp(-d2 / (2f * radius * radius));
                    if (fall < 0.01f) continue;
                    int idx = yy * W + xx;
                    int c = px[idx];
                    float nr = Math.min(1f, ((c >> 16) & 255) / 255f + tint[0] * energy * fall);
                    float ng = Math.min(1f, ((c >> 8) & 255) / 255f + tint[1] * energy * fall);
                    float nb = Math.min(1f, (c & 255) / 255f + tint[2] * energy * fall);
                    px[idx] = 0xFF000000 | ((int) (nr * 255) << 16) | ((int) (ng * 255) << 8) | (int) (nb * 255);
                }
            }
        }
    }

    static BufferedImage renderSun(int S) {
        int[] px = new int[S * S];
        Noise n = new Noise(11L);
        float cx = S / 2f, cy = S / 2f;
        float radius = S * 0.115f;
        IntStream.range(0, S).parallel().forEach(y -> {
            for (int x = 0; x < S; x++) {
                float dx = (x + 0.5f - cx) / radius;
                float dy = (y + 0.5f - cy) / radius;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float disc = 1f - smoothstep(0.93f, 1.0f, d);
                float limb = 1f - 0.18f * smoothstep(0.5f, 1.0f, d);
                float gran = 1f + 0.05f * n.fbm(dx * 8f, dy * 8f, 0f, 3, 0.5f);
                float glow = (float) Math.exp(-Math.pow(d / 2.1f, 2)) * 0.55f + (float) Math.exp(-d * 3.2f) * 0.25f;
                float core = disc * 1.2f;
                float alpha = clamp01(core + glow * 0.85f);
                px[y * S + x] = pack(new float[]{1.0f, 0.95f * limb * gran, 0.80f * limb * gran},
                        (int) (alpha * 255f));
            }
        });
        return toImage(px, S, S, true);
    }

    static BufferedImage renderMoon(int S) {
        int[] px = new int[S * S];
        Noise n = new Noise(31L);
        float cx = S / 2f, cy = S / 2f, radius = S * 0.24f;
        IntStream.range(0, S).parallel().forEach(y -> {
            for (int x = 0; x < S; x++) {
                float dx = (x + 0.5f - cx) / radius;
                float dy = (y + 0.5f - cy) / radius;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > 1.55f) {
                    px[y * S + x] = 0;
                    continue;
                }
                float disc = 1f - smoothstep(0.965f, 1.0f, d);
                float maria = 0.5f + 0.5f * n.fbm(dx * 1.7f + 3f, dy * 1.7f - 2f, 0.5f, 4, 0.55f);
                float craters = 0.5f + 0.5f * n.fbm(dx * 9f, dy * 9f, 9.5f, 4, 0.55f);
                float limb = 1f - 0.55f * smoothstep(0.55f, 1.0f, d);
                float lum = (0.72f - 0.24f * smoothstep(0.35f, 0.95f, maria)) * (0.92f + 0.13f * craters) * limb;
                float glow = (float) Math.exp(-Math.pow((d - 0.9f) / 1.4f, 2)) * 0.10f;
                float a = disc * (0.97f) + glow;
                px[y * S + x] = pack(new float[]{0.93f * lum + 0.10f, 0.95f * lum + 0.12f, 1.0f * lum + 0.16f},
                        (int) (clamp01(a) * 255f));
            }
        });
        return toImage(px, S, S, true);
    }

    // ---------------------------------------------------------------- output

    static BufferedImage toImage(int[] px, int w, int h) {
        return toImage(px, w, h, false);
    }

    static BufferedImage toImage(int[] px, int w, int h, boolean alpha) {
        BufferedImage img = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        int[] dst = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        System.arraycopy(px, 0, dst, 0, px.length);
        return img;
    }

    static void write(BufferedImage img, File file) throws Exception {
        file.getParentFile().mkdirs();
        ImageIO.write(img, "png", file);
        System.out.printf("wrote %-62s %d x %d  (%.1f MB)%n", file.getPath(), img.getWidth(), img.getHeight(),
                file.length() / 1048576.0);
    }

    static BufferedImage downscale(BufferedImage src, int w, int h) {
        int type = src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage dst = new BufferedImage(w, h, type);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    static BufferedImage crop(BufferedImage src, int x, int y, int w, int h, int out) {
        BufferedImage sub = src.getSubimage(x, y, w, h);
        return downscale(sub, out, out);
    }

    public static void main(String[] args) throws Exception {
        boolean fallback = args.length > 0 && args[0].equals("--fallback");
        ImageIO.setUseCache(false);
        long start = System.currentTimeMillis();

        if (fallback) {
            File dir = new File("src/main/resources/assets/skyweaver/textures/sky");
            int W = 1024, H = 512;
            for (Palette p : new Palette[]{DAY, SUNSET, NIGHT, MIDNIGHT}) {
                write(downscale(renderDome(p, W, H, 2), W, H), new File(dir, p.file() + ".png"));
            }
            write(renderSun(128), new File(dir, "sun.png"));
            write(renderMoon(128), new File(dir, "moon.png"));
            System.out.println("fallback textures done in " + (System.currentTimeMillis() - start) / 1000.0 + "s");
            return;
        }

        File dir = new File("resourcepack/assets/skyweaver/textures/sky");
        BufferedImage day = renderDome(DAY, 4096, 2048, 2);
        BufferedImage sunset = renderDome(SUNSET, 4096, 2048, 2);
        BufferedImage night = renderDome(NIGHT, 4096, 2048, 2);
        BufferedImage midnight = renderDome(MIDNIGHT, 4096, 2048, 2);
        BufferedImage sun = renderSun(512);
        BufferedImage moon = renderMoon(512);

        write(day, new File(dir, "day.png"));
        write(sunset, new File(dir, "sunset.png"));
        write(night, new File(dir, "night.png"));
        write(midnight, new File(dir, "midnight.png"));
        write(sun, new File(dir, "sun.png"));
        write(moon, new File(dir, "moon.png"));

        // Preview images and pack icon.
        File prev = new File("previews");
        write(downscale(day, 1920, 960), new File(prev, "day.png"));
        write(downscale(sunset, 1920, 960), new File(prev, "sunset.png"));
        write(downscale(night, 1920, 960), new File(prev, "night.png"));
        write(downscale(midnight, 1920, 960), new File(prev, "midnight.png"));
        write(crop(day, 1500, 760, 1200, 1200, 256), new File("resourcepack/pack.png"));

        System.out.println("done in " + (System.currentTimeMillis() - start) / 1000.0 + "s");
    }
}
