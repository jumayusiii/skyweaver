package dev.skyweaver.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Draws a clean sky dome and, optionally, moving sun and moon discs. Everything uses one
 * translucent, depth-less pipeline, so it composes with any terrain renderer that leaves the
 * vanilla sky hook intact.
 */
public final class SkyweaverSkyRenderer implements CustomSkyboxRenderer {
    private static final float SUN_HEIGHT = 100.0F;
    private static final float MOON_HEIGHT = 100.0F;
    private static final Vector3f NO_OFFSET = new Vector3f();
    private static final Matrix4f NO_TEXTURE_TRANSFORM = new Matrix4f();

    private record Draw(SkyMeshes.Mesh mesh, AbstractTexture texture, GpuSampler sampler, GpuBufferSlice uniforms) {
    }

    @Override
    public boolean renderSky(LevelRenderState levelRenderState, SkyRenderState sky, Matrix4fc modelViewMatrix, Runnable setupFog) {
        SkyboxDefinition definition = SkyboxManager.current();
        RenderPipeline pipeline = SkyweaverClient.skyPipeline();
        SkyMeshes meshes = SkyMeshes.get();
        if (definition == null || pipeline == null || meshes == null) {
            return false;
        }

        SkyboxDefinition.Sample sample = definition.sample(sky.sunAngle);
        AbstractTexture from = SkyboxManager.texture(definition.texture(sample.from()));
        if (from == null) {
            return false;
        }
        AbstractTexture to = sample.isBlend() ? SkyboxManager.texture(definition.texture(sample.to())) : null;

        setupFog.run();

        float weather = 0.55F + 0.45F * sky.rainBrightness;
        GpuSampler repeatSampler = SkyMeshes.repeatSampler();
        GpuSampler clampSampler = SkyMeshes.clampSampler();

        // 26.1 requires all dynamic-uniform writes to happen before a render pass is opened.
        List<Draw> draws = new ArrayList<>(4);
        draws.add(new Draw(meshes.dome, from, repeatSampler,
                uniforms(modelViewMatrix, new Vector4f(weather, weather, weather, 1.0F))));
        if (to != null && to != from) {
            draws.add(new Draw(meshes.dome, to, repeatSampler,
                    uniforms(modelViewMatrix, new Vector4f(weather, weather, weather, sample.mix()))));
        }

        addCelestial(draws, meshes.quad, definition.sun(), sky.sunAngle, definition.sunScale(),
                SUN_HEIGHT, sky.rainBrightness, modelViewMatrix, clampSampler);
        addCelestial(draws, meshes.quad, definition.moon(), sky.moonAngle, definition.moonScale(),
                MOON_HEIGHT, sky.rainBrightness, modelViewMatrix, clampSampler);

        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = indexBuffer.getBuffer(meshes.maxIndexCount);
        VertexFormat.IndexType indexType = indexBuffer.type();

        Minecraft minecraft = Minecraft.getInstance();
        var mainTarget = minecraft.getMainRenderTarget();
        GpuTextureView colorView = mainTarget.getColorTextureView();
        GpuTextureView depthView = mainTarget.getDepthTextureView();

        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Skyweaver sky", colorView, OptionalInt.empty(), depthView, OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setIndexBuffer(indices, indexType);

            for (Draw draw : draws) {
                pass.bindTexture("Sampler0", draw.texture().getTextureView(), draw.sampler());
                pass.setUniform("DynamicTransforms", draw.uniforms());
                pass.setVertexBuffer(0, draw.mesh().buffer());
                pass.drawIndexed(0, 0, draw.mesh().indexCount(), 1);
            }
        }

        return true;
    }

    private static void addCelestial(
            List<Draw> draws,
            SkyMeshes.Mesh quad,
            @Nullable Identifier textureId,
            float angle,
            float scale,
            float height,
            float rainBrightness,
            Matrix4fc modelViewMatrix,
            GpuSampler sampler
    ) {
        if (textureId == null || Mth.cos(angle) < -0.03F) {
            return;
        }

        AbstractTexture texture = SkyboxManager.texture(textureId);
        if (texture == null) {
            return;
        }

        Matrix4f transform = new Matrix4f(modelViewMatrix)
                .rotateY((float) Math.toRadians(-90.0))
                .rotateX(angle)
                .translate(0.0F, height, 0.0F)
                .scale(scale, 1.0F, scale);

        float alpha = Mth.clamp(rainBrightness, 0.0F, 1.0F);
        draws.add(new Draw(quad, texture, sampler, uniforms(transform, new Vector4f(alpha, alpha, alpha, alpha))));
    }

    private static GpuBufferSlice uniforms(Matrix4fc transform, Vector4f color) {
        return RenderSystem.getDynamicUniforms().writeTransform(transform, color, NO_OFFSET, NO_TEXTURE_TRANSFORM);
    }
}
