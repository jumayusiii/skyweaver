package dev.skyweaver.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skyweaver.Skyweaver;
import dev.skyweaver.config.SkyweaverConfig;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the configured skybox from the active resource packs and caches parsed definitions and
 * textures until the next resource reload.
 */
public final class SkyboxManager {
    private static final Map<Identifier, AbstractTexture> TEXTURES = new HashMap<>();
    private static final Set<Identifier> MISSING_TEXTURES = new HashSet<>();
    private static String loadedId;
    private static @Nullable SkyboxDefinition loaded;

    public static @Nullable SkyboxDefinition current() {
        if (!SkyweaverConfig.ENABLED.get()) {
            return null;
        }

        String id = SkyweaverConfig.SKYBOX.get();
        if (id.equals(loadedId)) {
            return loaded;
        }

        loadedId = id;
        loaded = null;
        Identifier location = Identifier.tryParse(id);
        if (location == null) {
            Skyweaver.LOGGER.warn("Skyweaver skybox id '{}' is not a valid namespace:path identifier.", id);
            return null;
        }

        loaded = load(location);
        if (loaded == null) {
            Skyweaver.LOGGER.warn("Skyweaver could not load skybox '{}'. Keeping the vanilla sky.", id);
        }
        return loaded;
    }

    public static @Nullable AbstractTexture texture(@Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        AbstractTexture cached = TEXTURES.get(id);
        if (cached != null) {
            return cached;
        }
        if (MISSING_TEXTURES.contains(id)) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Optional<Resource> resource = minecraft.getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            MISSING_TEXTURES.add(id);
            Skyweaver.LOGGER.warn("Skyweaver texture '{}' is missing; skipping that layer.", id);
            return null;
        }

        AbstractTexture texture = minecraft.getTextureManager().getTexture(id);
        TEXTURES.put(id, texture);
        return texture;
    }

    /** Called after a resource reload so newly enabled packs and edited PNGs take effect. */
    public static void invalidate() {
        loadedId = null;
        loaded = null;
        TEXTURES.clear();
        MISSING_TEXTURES.clear();
    }

    private static @Nullable SkyboxDefinition load(Identifier id) {
        Minecraft minecraft = Minecraft.getInstance();
        Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "skyboxes/" + id.getPath() + ".json");
        Optional<Resource> resource = minecraft.getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            Skyweaver.LOGGER.warn("Skyweaver skybox file '{}' was not found in any resource pack.", file);
            return null;
        }

        try (BufferedReader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            return SkyboxDefinition.parse(id, root);
        } catch (Exception exception) {
            Skyweaver.LOGGER.error("Skyweaver failed to parse skybox '{}'.", file, exception);
            return null;
        }
    }

    private SkyboxManager() {
    }
}
