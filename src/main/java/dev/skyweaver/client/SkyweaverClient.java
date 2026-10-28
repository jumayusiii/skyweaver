package dev.skyweaver.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.skyweaver.Skyweaver;
import dev.skyweaver.config.SkyweaverConfig;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterCustomEnvironmentEffectRendererEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.jetbrains.annotations.Nullable;

public final class SkyweaverClient {
    public static final Identifier DEFAULT_SKYBOX = Identifier.fromNamespaceAndPath(Skyweaver.MODID, "default");
    private static final SkyweaverSkyRenderer RENDERER = new SkyweaverSkyRenderer();
    private static @Nullable RenderPipeline skyPipeline;

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Skyweaver.MODID, "pipeline/sky"))
                .withVertexShader("core/position_tex")
                .withFragmentShader("core/position_tex")
                .withSampler("Sampler0")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS)
                .withCull(false)
                .build();

        event.registerPipeline(pipeline);
        skyPipeline = pipeline;
    }

    public static void registerSkyboxes(RegisterCustomEnvironmentEffectRendererEvent event) {
        event.registerSkyboxRenderer(DEFAULT_SKYBOX, RENDERER);
    }

    public static void onExtractRenderState(ExtractLevelRenderStateEvent event) {
        if (!SkyweaverConfig.ENABLED.get()) {
            return;
        }

        SkyRenderState sky = event.getRenderState().skyRenderState;
        if (sky.skybox == DimensionType.Skybox.NONE) {
            return;
        }
        // Another mod (or a datapack-selected Skyweaver skybox) already owns this sky.
        if (event.getRenderState().customSkyboxRenderer != null) {
            return;
        }

        SkyboxDefinition definition = SkyboxManager.current();
        if (definition == null || !definition.appliesTo(event.getLevel().dimension())) {
            return;
        }

        if (SkyweaverConfig.RESPECT_SHADER_PACKS.get() && IrisCompat.isShaderPackInUse()) {
            return;
        }

        event.getRenderState().customSkyboxRenderer = RENDERER;
    }

    public static void onResourceReload(ClientResourceLoadFinishedEvent event) {
        SkyboxManager.invalidate();
    }

    static @Nullable RenderPipeline skyPipeline() {
        return skyPipeline;
    }

    private SkyweaverClient() {
    }
}
