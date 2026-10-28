package dev.skyweaver.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.skyweaver.Skyweaver;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable description of one skybox, decoded from
 * {@code assets/<namespace>/skyboxes/<path>.json}.
 */
public final class SkyboxDefinition {
    /** The time-of-day looks a skybox can provide. Sunrise is optional. */
    public enum Phase {
        DAY("day"),
        SUNRISE("sunrise"),
        SUNSET("sunset"),
        NIGHT("night"),
        MIDNIGHT("midnight");

        private final String key;

        Phase(String key) {
            this.key = key;
        }

        public String key() {
            return this.key;
        }
    }

    /**
     * A crossfade sample: draw {@code from} at full strength, then {@code to} at {@code mix}.
     */
    public record Sample(Phase from, Phase to, float mix) {
        public boolean isBlend() {
            return this.from != this.to && this.mix > 0.001F;
        }
    }

    // The sun angle is 0 at noon, PI/2 at sunset, PI at midnight and 3PI/2 at sunrise.
    private static final float[] KEY_TIMES = {0.00F, 0.20F, 0.25F, 0.33F, 0.44F, 0.56F, 0.67F, 0.73F, 0.77F, 0.85F, 1.00F};
    private static final Phase[] KEY_PHASES = {
            Phase.DAY, Phase.DAY, Phase.SUNSET, Phase.NIGHT, Phase.MIDNIGHT,
            Phase.MIDNIGHT, Phase.NIGHT, Phase.SUNRISE, Phase.SUNRISE, Phase.DAY, Phase.DAY
    };

    private final Map<Phase, Identifier> skies;
    private final Set<Identifier> dimensions;
    private final @Nullable Identifier sun;
    private final @Nullable Identifier moon;
    private final float sunScale;
    private final float moonScale;

    private SkyboxDefinition(
            Map<Phase, Identifier> skies,
            Set<Identifier> dimensions,
            @Nullable Identifier sun,
            @Nullable Identifier moon,
            float sunScale,
            float moonScale
    ) {
        this.skies = Map.copyOf(skies);
        this.dimensions = Set.copyOf(dimensions);
        this.sun = sun;
        this.moon = moon;
        this.sunScale = sunScale;
        this.moonScale = moonScale;
    }

    public static @Nullable SkyboxDefinition parse(Identifier id, JsonObject root) {
        JsonElement texturesElement = root.get("textures");
        if (texturesElement == null || !texturesElement.isJsonObject()) {
            Skyweaver.LOGGER.warn("Skybox {} has no 'textures' object.", id);
            return null;
        }

        JsonObject textures = texturesElement.getAsJsonObject();
        Map<Phase, Identifier> skies = new EnumMap<>(Phase.class);
        for (Phase phase : Phase.values()) {
            Identifier texture = identifier(textures.get(phase.key()));
            if (texture == null) {
                // Sunrise is optional so older four-phase skyboxes keep working.
                if (phase == Phase.SUNRISE) {
                    continue;
                }
                Skyweaver.LOGGER.warn("Skybox {} is missing the '{}' texture.", id, phase.key());
                return null;
            }
            skies.put(phase, texture);
        }

        Set<Identifier> dimensions = new HashSet<>();
        dimensions.add(Identifier.withDefaultNamespace("overworld"));
        JsonElement dimensionsElement = root.get("dimensions");
        if (dimensionsElement != null && dimensionsElement.isJsonArray()) {
            dimensions.clear();
            JsonArray array = dimensionsElement.getAsJsonArray();
            for (JsonElement element : array) {
                Identifier dimension = identifier(element);
                if (dimension != null) {
                    dimensions.add(dimension);
                }
            }
            if (dimensions.isEmpty()) {
                dimensions.add(Identifier.withDefaultNamespace("overworld"));
            }
        }

        return new SkyboxDefinition(
                skies,
                dimensions,
                identifier(root.get("sun")),
                identifier(root.get("moon")),
                number(root, "sun_scale", 30.0F),
                number(root, "moon_scale", 20.0F)
        );
    }

    public boolean appliesTo(ResourceKey<Level> dimension) {
        return this.dimensions.contains(dimension.identifier());
    }

    public Identifier texture(Phase phase) {
        Identifier texture = this.skies.get(phase);
        if (texture == null && phase == Phase.SUNRISE) {
            return this.skies.get(Phase.SUNSET);
        }
        return texture;
    }

    public Sample sample(float sunAngleRadians) {
        float time = (float) (sunAngleRadians / (Math.PI * 2.0));
        time -= (float) Math.floor(time);

        for (int i = 0; i < KEY_TIMES.length - 1; i++) {
            if (time >= KEY_TIMES[i] && time <= KEY_TIMES[i + 1]) {
                float span = KEY_TIMES[i + 1] - KEY_TIMES[i];
                float mix = span <= 0.0F ? 0.0F : (time - KEY_TIMES[i]) / span;
                mix = mix * mix * (3.0F - 2.0F * mix);
                return new Sample(KEY_PHASES[i], KEY_PHASES[i + 1], mix);
            }
        }

        return new Sample(Phase.DAY, Phase.DAY, 0.0F);
    }

    public @Nullable Identifier sun() {
        return this.sun;
    }

    public @Nullable Identifier moon() {
        return this.moon;
    }

    public float sunScale() {
        return this.sunScale;
    }

    public float moonScale() {
        return this.moonScale;
    }

    private static @Nullable Identifier identifier(@Nullable JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        return Identifier.tryParse(element.getAsString());
    }

    private static float number(JsonObject object, String key, float fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsFloat();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
