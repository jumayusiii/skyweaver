package dev.skyweaver;

import com.mojang.logging.LogUtils;
import dev.skyweaver.client.SkyweaverClient;
import dev.skyweaver.config.SkyweaverConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Skyweaver - data-driven, resource-pack custom skies for NeoForge.
 *
 * <p>The mod never touches terrain rendering and installs no mixins. It plugs into NeoForge's
 * {@code CustomSkyboxRenderer} hook, so optimisation mods that replace the terrain pipeline
 * (Sodium, Lithium) are unaffected, and it steps aside whenever an Iris shader pack owns the sky.
 */
@Mod(value = Skyweaver.MODID, dist = Dist.CLIENT)
public final class Skyweaver {
    public static final String MODID = "skyweaver";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Skyweaver(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SkyweaverConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(SkyweaverClient::registerPipelines);
        modBus.addListener(SkyweaverClient::registerSkyboxes);

        NeoForge.EVENT_BUS.addListener(SkyweaverClient::onExtractRenderState);
        NeoForge.EVENT_BUS.addListener(SkyweaverClient::onResourceReload);
    }
}
