package com.tacticalpvphud.client;

import com.tacticalpvphud.TacticalPvPHUD;
import com.tacticalpvphud.client.gui.TacticalSettingsScreen;
import com.tacticalpvphud.config.TacticalConfig;
import com.tacticalpvphud.hud.HudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Клиентский инициализатор (Minecraft 1.21.11). */
public class TacticalPvPHUDClient implements ClientModInitializer {

    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of(TacticalPvPHUD.MOD_ID, "main"));

    public static KeyBinding openMenuKey;
    public static KeyBinding zoomKey;
    private static String lastBlockedCmd = "";
    private static long lastBlockedMs = 0;

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

        // Скрытие босс-бара и таблицы очков
        HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, original ->
                (ctx, tickCounter) -> {
                    if (!TacticalConfig.get().hideBossBar) original.render(ctx, tickCounter);
                });
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, original ->
                (ctx, tickCounter) -> {
                    if (!TacticalConfig.get().hideScoreboard) original.render(ctx, tickCounter);
                });

        // Время в чате и скрытие ника (для сообщений, приходящих как системные)
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            TacticalConfig c = TacticalConfig.get();
            if (overlay || (!c.chatTime && !c.nickHider)) return message;
            Text out = message;
            if (c.nickHider) {
                String me = MinecraftClient.getInstance().getSession().getUsername();
                out = Text.literal(message.getString().replace(me, "Игрок"));
            }
            if (c.chatTime) {
                String t = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                out = Text.literal("[" + t + "] ").append(out);
            }
            return out;
        });

        // Защита команд: опасную команду нужно ввести дважды за 6 секунд
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            TacticalConfig c = TacticalConfig.get();
            if (!c.cmdProtect) return true;
            String first = command.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
            boolean risky = false;
            for (String w : c.cmdProtectWords.split(",")) {
                if (!w.trim().isEmpty() && w.trim().toLowerCase(Locale.ROOT).equals(first)) risky = true;
            }
            if (!risky) return true;
            long now = System.currentTimeMillis();
            if (command.equals(lastBlockedCmd) && now - lastBlockedMs < 6000) {
                lastBlockedCmd = "";
                return true;
            }
            lastBlockedCmd = command;
            lastBlockedMs = now;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal("§c[Защита] /" + first + " заблокирована. Введи ту же команду ещё раз за 6 секунд, чтобы подтвердить."), false);
            }
            return false;
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
