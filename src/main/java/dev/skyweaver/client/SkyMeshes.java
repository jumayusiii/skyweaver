package dev.skyweaver.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.skyweaver.Skyweaver;
import java.util.OptionalDouble;
import org.jetbrains.annotations.Nullable;

/**
 * Static geometry for the sky: a zenith-to-horizon dome and a unit celestial quad.
 */
final class SkyMeshes {
    private static final int DOME_LONGITUDES = 192;
    private static final int DOME_LATITUDES = 96;
    private static final float DOME_RADIUS = 100.0F;

    record Mesh(GpuBuffer buffer, int indexCount) {
    }

    final Mesh dome;
    final Mesh quad;
    final int maxIndexCount;

    private static @Nullable SkyMeshes instance;
    private static boolean failed;
    private static @Nullable GpuSampler repeatSampler;
    private static @Nullable GpuSampler clampSampler;

    static @Nullable SkyMeshes get() {
        if (instance != null) {
            return instance;
        }
        if (failed) {
            return null;
        }
        try {
            instance = new SkyMeshes();
            return instance;
        } catch (Throwable throwable) {
            failed = true;
            Skyweaver.LOGGER.error("Skyweaver could not create its sky geometry; custom skies are disabled.", throwable);
            return null;
        }
    }

    private SkyMeshes() {
        this.dome = buildDome();
        this.quad = buildQuad();
        this.maxIndexCount = Math.max(this.dome.indexCount(), this.quad.indexCount());
    }

    static GpuSampler repeatSampler() {
        if (repeatSampler == null) {
            repeatSampler = RenderSystem.getDevice().createSampler(
                    AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR,
                    1, OptionalDouble.empty()
            );
        }
        return repeatSampler;
    }

    static GpuSampler clampSampler() {
        if (clampSampler == null) {
            clampSampler = RenderSystem.getDevice().createSampler(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR,
                    1, OptionalDouble.empty()
            );
        }
        return clampSampler;
    }

    private static Mesh buildDome() {
        int quadCount = DOME_LONGITUDES * DOME_LATITUDES;
        int vertexCount = quadCount * 4;
        int indexCount = quadCount * 6;
        double tau = Math.PI * 2.0;

        try (ByteBufferBuilder byteBuffer = ByteBufferBuilder.exactlySized(vertexCount * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(byteBuffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

            for (int latitude = 0; latitude < DOME_LATITUDES; latitude++) {
                // The panorama covers the visible sky only: v = 0 is the zenith and v = 1 is the
                // horizon. Mapping it over a full sphere would put the painted horizon underground.
                float v0 = (float) latitude / DOME_LATITUDES;
                float v1 = (float) (latitude + 1) / DOME_LATITUDES;
                double theta0 = (1.0 - v0) * (Math.PI * 0.5);
                double theta1 = (1.0 - v1) * (Math.PI * 0.5);
                double y0 = Math.sin(theta0) * DOME_RADIUS;
                double y1 = Math.sin(theta1) * DOME_RADIUS;
                double r0 = Math.cos(theta0) * DOME_RADIUS;
                double r1 = Math.cos(theta1) * DOME_RADIUS;

                for (int longitude = 0; longitude < DOME_LONGITUDES; longitude++) {
                    float u0 = (float) longitude / DOME_LONGITUDES;
                    float u1 = (float) (longitude + 1) / DOME_LONGITUDES;
                    double phi0 = (u0 - 0.5) * tau;
                    double phi1 = (u1 - 0.5) * tau;

                    addVertex(builder, r0 * Math.cos(phi0), y0, r0 * Math.sin(phi0), u0, v0);
                    addVertex(builder, r0 * Math.cos(phi1), y0, r0 * Math.sin(phi1), u1, v0);
                    addVertex(builder, r1 * Math.cos(phi1), y1, r1 * Math.sin(phi1), u1, v1);
                    addVertex(builder, r1 * Math.cos(phi0), y1, r1 * Math.sin(phi0), u0, v1);
                }
            }

            return upload("Skyweaver sky dome", builder, indexCount);
        }
    }

    private static Mesh buildQuad() {
        try (ByteBufferBuilder byteBuffer = ByteBufferBuilder.exactlySized(4 * DefaultVertexFormat.POSITION_TEX.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(byteBuffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(-1.0F, 0.0F, -1.0F).setUv(0.0F, 0.0F);
            builder.addVertex(1.0F, 0.0F, -1.0F).setUv(1.0F, 0.0F);
            builder.addVertex(1.0F, 0.0F, 1.0F).setUv(1.0F, 1.0F);
            builder.addVertex(-1.0F, 0.0F, 1.0F).setUv(0.0F, 1.0F);
            return upload("Skyweaver celestial quad", builder, 6);
        }
    }

    private static void addVertex(BufferBuilder builder, double x, double y, double z, float u, float v) {
        builder.addVertex((float) x, (float) y, (float) z).setUv(u, v);
    }

    private static Mesh upload(String label, BufferBuilder builder, int indexCount) {
        try (MeshData mesh = builder.buildOrThrow()) {
            GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            return new Mesh(buffer, indexCount);
        }
    }
}
