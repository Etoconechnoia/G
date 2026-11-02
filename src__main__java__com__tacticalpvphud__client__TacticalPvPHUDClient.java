package com.tacticalpvphud.client;

import com.tacticalpvphud.TacticalPvPHUD;
import com.tacticalpvphud.client.gui.TacticalSettingsScreen;
import com.tacticalpvphud.config.TacticalConfig;
import com.tacticalpvphud.hud.HudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Клиентский инициализатор (Minecraft 1.21.11). */
public class TacticalPvPHUDClient implements ClientModInitializer {

    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of(TacticalPvPHUD.MOD_ID, "main"));

    public static KeyBinding openMenuKey;
    public static KeyBinding zoomKey;

    @Override
    public void onInitializeClient() {
        TacticalConfig.load();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tacticalpvphud.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.tacticalpvphud.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        // Наш HUD
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.of(TacticalPvPHUD.MOD_ID, "tactical_hud"),
                (ctx, tickCounter) -> HudRenderer.render(ctx, MinecraftClient.getInstance()));

        // Скрытие значков эффектов (ванильный элемент заменяем «пустым», если включено)
        HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, original ->
                (ctx, tickCounter) -> {
                    if (!TacticalConfig.get().hideEffectsOverlay) original.render(ctx, tickCounter);
                });

        // Фильтр чата (системные сообщения)
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            TacticalConfig c = TacticalConfig.get();
            if (!c.chatFilter || overlay) return true;
            String[] words = c.filterWords();
            if (words.length == 0) return true;
            String s = message.getString().toLowerCase(Locale.ROOT);
            for (String w : words) if (s.contains(w)) return false;
            return true;
        });

        TacticalPvPHUD.LOGGER.info("TacticalPvPHUD: клиент инициализирован");
    }

    private void onClientTick(MinecraftClient client) {
        HudRenderer.tick(client);
        while (openMenuKey.wasPressed()) {
            client.setScreen(new TacticalSettingsScreen(null));
        }
    }
}
