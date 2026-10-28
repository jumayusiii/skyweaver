package dev.skyweaver.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SkyweaverConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Render Skyweaver skyboxes. Disable to compare against the vanilla sky.")
            .define("enabled", true);

    public static final ModConfigSpec.ConfigValue<String> SKYBOX = BUILDER
            .comment("Skybox to use, in namespace:path form.",
                    "The mod reads assets/<namespace>/skyboxes/<path>.json from enabled resource packs.",
                    "Default: skyweaver:default, which the bundled 4K resource pack upgrades.")
            .define("skybox", "skyweaver:default");

    public static final ModConfigSpec.BooleanValue RESPECT_SHADER_PACKS = BUILDER
            .comment("Yield to Iris shader packs while a shader pack is active.",
                    "Shader packs render their own sky, so Skyweaver is skipped to avoid conflicts.")
            .define("respect_shader_packs", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private SkyweaverConfig() {
    }
}
