package com.tacticalpvphud.client;

import com.tacticalpvphud.TacticalPvPHUD;
import com.tacticalpvphud.client.gui.TacticalSettingsScreen;
import com.tacticalpvphud.config.TacticalConfig;
import com.tacticalpvphud.hud.HudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Клиентский инициализатор (Minecraft 1.21.11): клавиши, тики и HUD. */
public class TacticalPvPHUDClient implements ClientModInitializer {

    /** С 1.21.9 категория клавиш — отдельный объект. Перевод: key.category.tacticalpvphud.main */
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of(TacticalPvPHUD.MOD_ID, "main"));

    public static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        TacticalConfig.load();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tacticalpvphud.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        // HudRenderCallback устарел — используем HudElementRegistry
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.of(TacticalPvPHUD.MOD_ID, "tactical_hud"),
                (ctx, tickCounter) -> HudRenderer.render(ctx, MinecraftClient.getInstance()));

        TacticalPvPHUD.LOGGER.info("TacticalPvPHUD: клиент инициализирован");
    }

    private void onClientTick(MinecraftClient client) {
        HudRenderer.tick(client);
        while (openMenuKey.wasPressed()) {
            client.setScreen(new TacticalSettingsScreen(null));
        }
    }
}
