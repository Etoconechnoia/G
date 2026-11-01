package com.tacticalpvphud.hud;

import com.tacticalpvphud.config.TacticalConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.Registries;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Вся HUD-отрисовка и клиентская логика модулей.
 * Каждый модуль — отдельный метод, включается тумблером из конфига.
 */
public class HudRenderer {

    // CPS: метки времени кликов за последнюю секунду
    private static final Deque<Long> LMB = new ArrayDeque<>();
    private static final Deque<Long> RMB = new ArrayDeque<>();
    private static boolean lmbPrev, rmbPrev;

    // Режим боя
    private static long lastCombatMs = 0;
    private static long combatStartMs = 0;
    private static final long COMBAT_TIMEOUT_MS = 10_000;

    // ===================== ТИК =====================
    public static void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        long window = mc.getWindow().getHandle();

        boolean lmb = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rmb = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (lmb && !lmbPrev && mc.currentScreen == null) {
            LMB.add(now);
            if (mc.targetedEntity instanceof LivingEntity) markCombat(now);
        }
        if (rmb && !rmbPrev && mc.currentScreen == null) RMB.add(now);
        lmbPrev = lmb;
        rmbPrev = rmb;

        while (!LMB.isEmpty() && now - LMB.peek() > 1000) LMB.poll();
        while (!RMB.isEmpty() && now - RMB.peek() > 1000) RMB.poll();

        if (mc.player.hurtTime > 0) markCombat(now);

        tickBinds(mc);
    }

    private static void markCombat(long now) {
        if (now - lastCombatMs > COMBAT_TIMEOUT_MS) combatStartMs = now;
        lastCombatMs = now;
    }

    private static boolean inCombat() {
        return System.currentTimeMillis() - lastCombatMs <= COMBAT_TIMEOUT_MS;
    }

    // ===================== БИНДЫ ПРЕДМЕТОВ =====================
    private static final boolean[] KEY_PREV = new boolean[400];

    /** Проверяет нажатия назначенных клавиш и переключает хотбар на нужный предмет. */
    private static void tickBinds(MinecraftClient mc) {
        TacticalConfig c = TacticalConfig.get();
        if (!c.bindsEnabled || mc.currentScreen != null) {
            java.util.Arrays.fill(KEY_PREV, false);
            return;
        }
        long window = mc.getWindow().getHandle();
        boolean[] now = new boolean[400];
        for (TacticalConfig.Bind b : c.binds) {
            if (b.key > 0 && b.key < 400) {
                now[b.key] = GLFW.glfwGetKey(window, b.key) == GLFW.GLFW_PRESS;
            }
        }
        for (TacticalConfig.Bind b : c.binds) {
            if (b.key > 0 && b.key < 400 && now[b.key] && !KEY_PREV[b.key]) {
                selectItem(mc, b.item);
            }
        }
        System.arraycopy(now, 0, KEY_PREV, 0, 400);
    }

    /** Ищет предмет в хотбаре (слоты 0–8) и выбирает его. */
    private static void selectItem(MinecraftClient mc, String itemId) {
        if (mc.player == null) return;
        Item item = Registries.ITEM.get(Identifier.of(itemId));
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) {
                mc.player.getInventory().setSelectedSlot(i);
                return;
            }
        }
    }

    // ===================== ОТРИСОВКА =====================
    public static void render(DrawContext ctx, MinecraftClient mc) {
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen != null) return;
        TacticalConfig c = TacticalConfig.get();
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int a = (int) (MathHelper.clamp(c.hudOpacity, 0.1f, 1f) * 255) << 24;

        if (c.azimuthEnabled) renderAzimuth(ctx, mc, w, a);
        if (c.customCrosshair) renderCrosshair(ctx, mc, c, w, h);
        if (c.armorHudEnabled) renderArmor(ctx, mc, c, h);
        if (c.cpsEnabled) renderCps(ctx, mc, w, h);
        if (c.combatMode) renderCombat(ctx, mc, c, w, h, a);
    }

    /** Азимут 0–360° (0 = север). */
    private static void renderAzimuth(DrawContext ctx, MinecraftClient mc, int w, int a) {
        float yaw = mc.player.getYaw();
        float az = ((yaw + 180f) % 360f + 360f) % 360f;
        String txt = String.format("%.0f°", az);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, txt, w / 2, 4, a | 0xFFFFFF);
    }

    /** Кастомный прицел: толщина, зазор, цвет, динамическое расширение при беге. */
    private static void renderCrosshair(DrawContext ctx, MinecraftClient mc, TacticalConfig c, int w, int h) {
        PlayerEntity p = mc.player;
        int cx = w / 2, cy = h / 2;
        int t = Math.max(1, Math.round(c.crosshairThickness));
        float gap = c.crosshairGap;
        if (c.dynamicCrosshair) {
            double speed = Math.sqrt(p.getVelocity().x * p.getVelocity().x + p.getVelocity().z * p.getVelocity().z);
            gap += (float) Math.min(speed * 20, 8);
            if (!p.isOnGround()) gap += 3;
        }
        int g = Math.round(gap), len = 5;
        int color = 0xFF000000 | hueToRgb(c.crosshairHue);
        int o = t / 2;
        ctx.fill(cx - g - len, cy - o, cx - g, cy - o + t, color); // лево
        ctx.fill(cx + g, cy - o, cx + g + len, cy - o + t, color); // право
        ctx.fill(cx - o, cy - g - len, cx - o + t, cy - g, color); // верх
        ctx.fill(cx - o, cy + g, cx - o + t, cy + g + len, color); // низ
    }

    private static final EquipmentSlot[] ARMOR_SLOTS =
            {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** HUD брони: иконка, цвет по материалу, % прочности, мигание при <15%. */
    private static void renderArmor(DrawContext ctx, MinecraftClient mc, TacticalConfig c, int h) {
        int x = 4, y = h / 2 - 38;
        boolean blink = (System.currentTimeMillis() / 250) % 2 == 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack s = mc.player.getEquippedStack(slot);
            if (s.isEmpty()) { y += 18; continue; }
            ctx.drawItem(s, x, y);
            int pct = 100;
            if (s.getMaxDamage() > 0) pct = (s.getMaxDamage() - s.getDamage()) * 100 / s.getMaxDamage();
            // Материал определяем по ID предмета (ArmorItem.getMaterial в 1.21.2+ удалён)
            String id = Registries.ITEM.getId(s.getItem()).getPath();
            int col = 0xFFFFFF;
            if (id.startsWith("diamond_")) col = 0x55FFFF;
            else if (id.startsWith("netherite_")) col = 0x555555;
            if (c.brokenArmorAlert && pct < 15 && blink) col = 0xFF2222;
            ctx.drawTextWithShadow(mc.textRenderer, pct + "%", x + 20, y + 4, 0xFF000000 | col);
            y += 18;
        }
    }

    /** Оттенок (0..1) -> RGB, насыщенность и яркость максимальные. */
    private static int hueToRgb(float hue) {
        float h6 = (hue - (float) Math.floor(hue)) * 6f;
        int i = (int) h6;
        float f = h6 - i;
        int q = (int) ((1f - f) * 255), t = (int) (f * 255);
        return switch (i) {
            case 0 -> (255 << 16) | (t << 8);
            case 1 -> (q << 16) | (255 << 8);
            case 2 -> (255 << 8) | t;
            case 3 -> (q << 8) | 255;
            case 4 -> (t << 16) | 255;
            default -> (255 << 16) | q;
        };
    }

    private static void renderCps(DrawContext ctx, MinecraftClient mc, int w, int h) {
        ctx.drawTextWithShadow(mc.textRenderer, "CPS: " + LMB.size() + " | " + RMB.size(),
                w - 70, 4, 0xFFFFFFFF);
    }

    /** Таймер боя + красная рамка по краям экрана. */
    private static void renderCombat(DrawContext ctx, MinecraftClient mc, TacticalConfig c, int w, int h, int a) {
        if (!inCombat()) return;
        long sec = (System.currentTimeMillis() - combatStartMs) / 1000;
        ctx.drawCenteredTextWithShadow(mc.textRenderer, String.format("В бою: %d:%02d", sec / 60, sec % 60),
                w / 2, 16, 0xFFFF5555);
        if (c.combatFrame) {
            int col = 0x66FF0000, b = 2;
            ctx.fill(0, 0, w, b, col);
            ctx.fill(0, h - b, w, h, col);
            ctx.fill(0, 0, b, h, col);
            ctx.fill(w - b, 0, w, h, col);
        }
    }
}
