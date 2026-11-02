package com.tacticalpvphud.hud;

import com.tacticalpvphud.client.TacticalPvPHUDClient;
import com.tacticalpvphud.config.TacticalConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Вся HUD-логика. Правило оптимизации: в render() нет создания объектов и строк —
 * все тексты и данные считаются в tick() и кладутся в кэш.
 */
public class HudRenderer {

    // ===================== КЭШ СОСТОЯНИЯ =====================
    private static int tickCounter = 0;
    private static long sessionStart = 0;

    // CPS
    private static final Deque<Long> LMB = new ArrayDeque<>();
    private static final Deque<Long> RMB = new ArrayDeque<>();
    private static boolean lmbPrev, rmbPrev;
    private static String cpsText = "CPS: 0 | 0";

    // Бой
    private static long lastCombatMs, combatStartMs, lastAttackMs;
    private static final long COMBAT_TIMEOUT_MS = 10_000;
    private static boolean combatActive;
    private static String combatText = "";

    // Азимут / сессия / пинг
    private static String azText = "0°";
    private static String sessionText = "";
    private static long lastSessionSec = -1;
    private static String pingText = "";
    private static int pingColor = 0x55FF55;
    private static final int[] pingHist = new int[30];
    private static int pingIdx = 0;

    // Броня
    private static final EquipmentSlot[] ARMOR_SLOTS =
            {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ItemStack[] armorStacks = new ItemStack[4];
    private static final String[] armorStr = new String[4];
    private static final int[] armorPct = new int[4];
    private static final int[] armorColor = new int[4];

    // Урон
    private static final int IND_N = 8;
    private static final long[] indTime = new long[IND_N];
    private static final boolean[] indDealt = new boolean[IND_N];
    private static final String[] indText = new String[IND_N];
    private static int indHead = 0;
    private static float lastHp = -1, lastTargetHp = -1;
    private static int lastTargetId = -1;
    private static float dealtTotal, takenTotal;
    private static String tradesText = "";

    // Цель
    private static boolean targetValid;
    private static String tgtDist = "", tgtSwing = "", tgtShield = "";
    private static int tgtDistColor = 0xFFFFFF;
    private static float prevSwing;
    private static long lastSwingStart;
    private static int swingTargetId = -1;
    private static long shieldDisabledUntil;

    // Крит
    private static boolean critReady;
    private static float critCd;
    private static String critText = "";

    // Эффекты
    private static final HashMap<StatusEffect, Integer> EFF_MAX = new HashMap<>();
    private static final HashSet<StatusEffect> EFF_SEEN = new HashSet<>();
    private static final String[] effLines = new String[6];
    private static int effCount;
    private static final float[] ringFrac = new float[4];
    private static final int[] ringColor = new int[4];
    private static int ringCount;
    private static final float[] SIN = new float[32];
    private static final float[] COS = new float[32];
    static {
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI * 2 / 32;
            SIN[i] = (float) Math.sin(a);
            COS[i] = (float) Math.cos(a);
        }
    }

    // Предметы
    private static final Item[] COUNT_ITEMS = {Items.ENDER_PEARL, Items.TOTEM_OF_UNDYING, Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE, Items.END_CRYSTAL, Items.SPLASH_POTION, Items.EXPERIENCE_BOTTLE};
    private static final ItemStack[] COUNT_STACKS = new ItemStack[COUNT_ITEMS.length];
    private static final int[] itemCnt = new int[COUNT_ITEMS.length];
    private static final String[] itemStr = new String[COUNT_ITEMS.length];
    private static String freeSlotsText = "";
    static {
        for (int i = 0; i < COUNT_ITEMS.length; i++) {
            COUNT_STACKS[i] = new ItemStack(COUNT_ITEMS[i]);
            itemStr[i] = "0";
        }
    }

    // Еда, рука, хотбар
    private static int lastFood = -1;
    private static float lastSat = -1;
    private static String foodText = "";
    private static ItemStack offStack = ItemStack.EMPTY;
    private static String offText = "";
    private static boolean emptyHand;
    private static final int[] hotColor = new int[9];

    // Метки, подсказка
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final String[] wpLines = new String[8];
    private static int wpCount;
    private static String tutorialText = "";

    // Зум
    private static Integer savedFov = null;

    // Бинды
    private static final boolean[] KEY_PREV = new boolean[400];

    // ===================== ТИК =====================
    public static void tick(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        if (p == null) { sessionStart = 0; return; }
        tickCounter++;
        long now = System.currentTimeMillis();
        if (sessionStart == 0) sessionStart = now;
        TacticalConfig c = TacticalConfig.get();

        long window = mc.getWindow().getHandle();
        boolean lmb = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rmb = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        Entity t = mc.targetedEntity;
        if (lmb && !lmbPrev && mc.currentScreen == null) {
            LMB.add(now);
            lastAttackMs = now;
            if (t instanceof LivingEntity le) {
                markCombat(now);
                // Эвристика: удар топором по поднятому щиту отключает его ~5 секунд
                String hand = Registries.ITEM.getId(p.getMainHandStack().getItem()).getPath();
                if (le.isBlocking() && hand.endsWith("_axe")) shieldDisabledUntil = now + 5000;
            }
        }
        if (rmb && !rmbPrev && mc.currentScreen == null) RMB.add(now);
        lmbPrev = lmb;
        rmbPrev = rmb;
        while (!LMB.isEmpty() && now - LMB.peek() > 1000) LMB.poll();
        while (!RMB.isEmpty() && now - RMB.peek() > 1000) RMB.poll();
        cpsText = "CPS: " + LMB.size() + " | " + RMB.size();

        handleZoom(mc, c);
        tickBinds(mc, c);
        trackDamage(mc, p, now);

        // Азимут
        float az = ((p.getYaw() + 180f) % 360f + 360f) % 360f;
        azText = Math.round(az) + "°";

        // Бой
        combatActive = now - lastCombatMs <= COMBAT_TIMEOUT_MS && lastCombatMs != 0;
        if (combatActive) {
            long sec = (now - combatStartMs) / 1000;
            combatText = "В бою: " + (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60);
        }

        // Броня
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getEquippedStack(ARMOR_SLOTS[i]);
            armorStacks[i] = s;
            if (s.isEmpty()) continue;
            int pct = 100;
            if (s.getMaxDamage() > 0) pct = (s.getMaxDamage() - s.getDamage()) * 100 / s.getMaxDamage();
            armorPct[i] = pct;
            armorStr[i] = pct + "%";
            String id = Registries.ITEM.getId(s.getItem()).getPath();
            int col = 0xFFFFFF;
            if (id.startsWith("diamond_")) col = 0x55FFFF;
            else if (id.startsWith("netherite_")) col = 0x888888;
            armorColor[i] = col;
        }

        updateTarget(mc, p, t, now);
        updateCrit(p);

        // Левая рука, пустая рука
        offStack = p.getOffHandStack();
        offText = offStack.getCount() > 1 ? String.valueOf(offStack.getCount()) : "";
        emptyHand = p.getMainHandStack().isEmpty();

        // Еда
        int food = p.getHungerManager().getFoodLevel();
        float sat = p.getHungerManager().getSaturationLevel();
        if (food != lastFood || sat != lastSat) {
            lastFood = food;
            lastSat = sat;
            foodText = "Еда " + food + " | Нас. " + (Math.round(sat * 10) / 10f);
        }

        // Сессия
        long secTotal = (now - sessionStart) / 1000;
        if (secTotal != lastSessionSec) {
            lastSessionSec = secTotal;
            sessionText = "Сессия " + two(secTotal / 3600) + ":" + two(secTotal / 60 % 60) + ":" + two(secTotal % 60);
        }

        if (tickCounter % 2 == 0) updateEffects(p);
        if (tickCounter % 10 == 0) { updateItems(p); updateHotbar(p); }
        if (tickCounter % 5 == 0) updateWaypoints(p, c);
        if (tickCounter % 20 == 0) updatePing(mc, p);
        updateTutorial(p);
    }

    private static String two(long v) { return v < 10 ? "0" + v : String.valueOf(v); }

    private static void markCombat(long now) {
        if (now - lastCombatMs > COMBAT_TIMEOUT_MS) combatStartMs = now;
        lastCombatMs = now;
    }

    // ----- урон: получен / нанесён -----
    private static void trackDamage(MinecraftClient mc, PlayerEntity p, long now) {
        float hp = p.getHealth() + p.getAbsorptionAmount();
        if (lastHp >= 0 && hp < lastHp - 0.01f) {
            float d = lastHp - hp;
            takenTotal += d;
            addIndicator(d, false, now);
            markCombat(now);
        }
        lastHp = hp;

        Entity t = mc.targetedEntity;
        if (t instanceof LivingEntity le) {
            float thp = le.getHealth() + le.getAbsorptionAmount();
            if (le.getId() == lastTargetId && lastTargetHp >= 0 && thp < lastTargetHp - 0.01f
                    && now - lastAttackMs < 700) {
                float d = lastTargetHp - thp;
                dealtTotal += d;
                addIndicator(d, true, now);
            }
            lastTargetId = le.getId();
            lastTargetHp = thp;
        } else {
            lastTargetId = -1;
            lastTargetHp = -1;
        }
    }

    private static void addIndicator(float v, boolean dealt, long now) {
        int i = indHead;
        indHead = (indHead + 1) % IND_N;
        indTime[i] = now;
        indDealt[i] = dealt;
        indText[i] = (dealt ? "-" : "-") + (Math.round(v * 10) / 10f);
        tradesText = "Нанесено " + (Math.round(dealtTotal * 10) / 10f) + " | Получено " + (Math.round(takenTotal * 10) / 10f);
    }

    private static void updateTarget(MinecraftClient mc, PlayerEntity p, Entity t, long now) {
        if (t instanceof LivingEntity le) {
            targetValid = true;
            if (le.getId() != swingTargetId) { swingTargetId = le.getId(); lastSwingStart = 0; prevSwing = 0; }
            float d = p.distanceTo(le);
            tgtDist = "Дист: " + (Math.round(d * 10) / 10f);
            tgtDistColor = d <= 3.0f ? 0x55FF55 : (d <= 4.5f ? 0xFFAA00 : 0xFF5555);
            float sp = le.getHandSwingProgress(1f);
            if (sp > 0.01f && prevSwing <= 0.01f) lastSwingStart = now;
            prevSwing = sp;
            if (lastSwingStart == 0) {
                tgtSwing = "Замах: нет";
            } else {
                long since = now - lastSwingStart;
                tgtSwing = "Замах " + (since / 100 / 10f) + " с назад" + (since >= 600 ? " (готов)" : "");
            }
            if (le.isBlocking()) tgtShield = "ЩИТ ПОДНЯТ";
            else if (now < shieldDisabledUntil) tgtShield = "Щит выкл. ~" + ((shieldDisabledUntil - now) / 100 / 10f) + " с";
            else tgtShield = "";
        } else {
            targetValid = false;
        }
    }

    // Условия крита из ванильного кода: кулдаун >90%, падение, не на земле, не лестница, не вода,
    // нет слепоты, не верхом, не в спринте
    private static void updateCrit(PlayerEntity p) {
        critCd = p.getAttackCooldownProgress(0.5f);
        critReady = critCd > 0.9f && p.fallDistance > 0 && !p.isOnGround() && !p.isClimbing()
                && !p.isTouchingWater() && !p.hasVehicle() && !p.isSprinting()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS);
        critText = critReady ? "КРИТ ГОТОВ" : "КД " + Math.round(critCd * 100) + "%";
    }

    private static void updateEffects(PlayerEntity p) {
        EFF_SEEN.clear();
        effCount = 0;
        ringCount = 0;
        for (StatusEffectInstance inst : p.getStatusEffects()) {
            StatusEffect eff = inst.getEffectType().value();
            EFF_SEEN.add(eff);
            int dur = inst.getDuration();
            Integer mx = EFF_MAX.get(eff);
            if (mx == null || dur > mx) { mx = dur; EFF_MAX.put(eff, dur); }
            boolean harmful = eff.getCategory() == StatusEffectCategory.HARMFUL;
            float frac = inst.isInfinite() ? 1f : MathHelper.clamp(dur / (float) Math.max(1, mx), 0f, 1f);
            if (ringCount < 4) {
                ringFrac[ringCount] = frac;
                ringColor[ringCount] = harmful ? 0xFF5555 : 0x55FFFF;
                ringCount++;
            }
            if (harmful && effCount < 6) {
                effLines[effCount++] = eff.getName().getString() + " " + (inst.isInfinite() ? "∞" : (dur / 20 + 1) + "с");
            }
        }
        EFF_MAX.keySet().retainAll(EFF_SEEN);
    }

    private static void updateItems(PlayerEntity p) {
        int free = 0;
        for (int i = 0; i < 36; i++) if (p.getInventory().getStack(i).isEmpty()) free++;
        freeSlotsText = "Свободно слотов: " + free + "/36";
        ItemStack off = p.getOffHandStack();
        for (int k = 0; k < COUNT_ITEMS.length; k++) {
            int n = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack s = p.getInventory().getStack(i);
                if (s.isOf(COUNT_ITEMS[k])) n += s.getCount();
            }
            if (off.isOf(COUNT_ITEMS[k])) n += off.getCount();
            itemCnt[k] = n;
            itemStr[k] = String.valueOf(n);
        }
    }

    private static void updateHotbar(PlayerEntity p) {
        for (int i = 0; i < 9; i++) hotColor[i] = classify(p.getInventory().getStack(i));
    }

    private static int classify(ItemStack s) {
        if (s.isEmpty()) return 0;
        Item it = s.getItem();
        if (it == Items.ENDER_PEARL) return 0x3355FF;
        if (it == Items.TOTEM_OF_UNDYING) return 0xFFD700;
        if (it == Items.END_CRYSTAL || it == Items.RESPAWN_ANCHOR || it == Items.TNT) return 0xFF55FF;
        if (it == Items.SHIELD) return 0xAAAAAA;
        if (it == Items.GOLDEN_APPLE || it == Items.ENCHANTED_GOLDEN_APPLE) return 0x55FF55;
        String id = Registries.ITEM.getId(it).getPath();
        if (id.endsWith("_sword") || id.endsWith("_axe") || id.equals("mace") || id.equals("trident")) return 0xFF3333;
        if (id.contains("potion")) return 0xAA00FF;
        if (id.endsWith("_helmet") || id.endsWith("_chestplate") || id.endsWith("_leggings") || id.endsWith("_boots")) return 0x33DDDD;
        return 0;
    }

    private static void updateWaypoints(PlayerEntity p, TacticalConfig c) {
        wpCount = 0;
        for (TacticalConfig.Waypoint wp : c.waypoints) {
            if (wpCount >= wpLines.length) break;
            double dx = wp.x + 0.5 - p.getX();
            double dz = wp.z + 0.5 - p.getZ();
            float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float rel = MathHelper.wrapDegrees(target - p.getYaw());
            int idx = ((Math.round(rel / 45f) % 8) + 8) % 8;
            wpLines[wpCount++] = ARROWS[idx] + " " + wp.name + " " + (int) Math.sqrt(dx * dx + dz * dz) + "м";
        }
    }

    private static void updatePing(MinecraftClient mc, PlayerEntity p) {
        if (mc.getNetworkHandler() == null) return;
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
        if (e == null) return;
        int ms = e.getLatency();
        pingHist[pingIdx] = ms;
        pingIdx = (pingIdx + 1) % pingHist.length;
        pingText = "Пинг: " + ms + " мс";
        pingColor = ms < 60 ? 0x55FF55 : (ms < 120 ? 0xFFFF55 : 0xFF5555);
    }

    private static void updateTutorial(PlayerEntity p) {
        if (!targetValid) { tutorialText = ""; return; }
        if (tgtShield.startsWith("ЩИТ")) tutorialText = "Щит поднят: бей топором";
        else if (critCd < 0.9f) tutorialText = "Подожди кулдаун";
        else if (p.fallDistance > 0 && !p.isOnGround() && p.isSprinting()) tutorialText = "Отпусти спринт для крита";
        else if (critReady) tutorialText = "Крит! Бей!";
        else tutorialText = "Бей!";
    }

    // ----- зум: временно меняем FOV в опциях (минимум 30) -----
    private static void handleZoom(MinecraftClient mc, TacticalConfig c) {
        boolean want = c.zoomEnabled && mc.currentScreen == null
                && TacticalPvPHUDClient.zoomKey != null && TacticalPvPHUDClient.zoomKey.isPressed();
        if (want) {
            if (savedFov == null) savedFov = mc.options.getFov().getValue();
            int z = Math.max(30, Math.round(c.zoomFov));
            if (mc.options.getFov().getValue().intValue() != z) mc.options.getFov().setValue(z);
        } else if (savedFov != null) {
            mc.options.getFov().setValue(savedFov);
            savedFov = null;
        }
    }

    // ----- бинды предметов -----
    private static void tickBinds(MinecraftClient mc, TacticalConfig c) {
        if (!c.bindsEnabled || mc.currentScreen != null) { Arrays.fill(KEY_PREV, false); return; }
        long window = mc.getWindow().getHandle();
        boolean[] now = new boolean[400];
        for (TacticalConfig.Bind b : c.binds) {
            if (b.key > 0 && b.key < 400) now[b.key] = GLFW.glfwGetKey(window, b.key) == GLFW.GLFW_PRESS;
        }
        for (TacticalConfig.Bind b : c.binds) {
            if (b.key > 0 && b.key < 400 && now[b.key] && !KEY_PREV[b.key]) selectItem(mc, b.item);
        }
        System.arraycopy(now, 0, KEY_PREV, 0, 400);
    }

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
    private static int px(TacticalConfig c, int i, int w) { return (int) (c.posX[i] * w); }
    private static int py(TacticalConfig c, int i, int h) { return (int) (c.posY[i] * h); }
    private static int argb(int a, int rgb) { return a | (rgb & 0xFFFFFF); }

    public static void render(DrawContext ctx, MinecraftClient mc) {
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen != null) return;
        TacticalConfig c = TacticalConfig.get();
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int a = (int) (MathHelper.clamp(c.hudOpacity, 0.1f, 1f) * 255) << 24;
        long now = System.currentTimeMillis();
        boolean blink = (now / 250) % 2 == 0;
        var tr = mc.textRenderer;

        if (c.azimuthEnabled)
            ctx.drawCenteredTextWithShadow(tr, azText, px(c, HudLayout.AZIMUTH, w), py(c, HudLayout.AZIMUTH, h), argb(a, 0xFFFFFF));

        if (c.customCrosshair) renderCrosshair(ctx, mc, c, w, h, a);

        if (c.armorHudEnabled) {
            int x = px(c, HudLayout.ARMOR, w), y = py(c, HudLayout.ARMOR, h);
            for (int i = 0; i < 4; i++) {
                ItemStack s = armorStacks[i];
                if (s == null || s.isEmpty()) { y += 18; continue; }
                ctx.drawItem(s, x, y);
                int col = armorColor[i];
                if (c.brokenArmorAlert && armorPct[i] < 15 && blink) col = 0xFF2222;
                ctx.drawTextWithShadow(tr, armorStr[i], x + 20, y + 4, argb(0xFF000000, col));
                y += 18;
            }
        }

        if (c.cpsEnabled)
            ctx.drawTextWithShadow(tr, cpsText, px(c, HudLayout.CPS, w), py(c, HudLayout.CPS, h), 0xFFFFFFFF);

        if (c.sessionTimer)
            ctx.drawTextWithShadow(tr, sessionText, px(c, HudLayout.SESSION, w), py(c, HudLayout.SESSION, h), 0xFFAAAAAA);

        if (c.combatMode && combatActive) {
            ctx.drawCenteredTextWithShadow(tr, combatText, px(c, HudLayout.COMBAT, w), py(c, HudLayout.COMBAT, h), 0xFFFF5555);
            if (c.combatFrame) {
                int col = 0x66FF0000, b = 2;
                ctx.fill(0, 0, w, b, col);
                ctx.fill(0, h - b, w, h, col);
                ctx.fill(0, 0, b, h, col);
                ctx.fill(w - b, 0, w, h, col);
            }
            if (c.emptyHandAlert && emptyHand && blink)
                ctx.drawCenteredTextWithShadow(tr, "ПУСТАЯ РУКА!", px(c, HudLayout.EMPTYHAND, w), py(c, HudLayout.EMPTYHAND, h), 0xFFFF2222);
        }

        if (c.damageIndicators) {
            int bx = px(c, HudLayout.INDICATORS, w), by = py(c, HudLayout.INDICATORS, h);
            long dur = (long) (c.indicatorDuration * 1000);
            for (int i = 0; i < IND_N; i++) {
                if (indText[i] == null) continue;
                long age = now - indTime[i];
                if (age > dur) continue;
                float pr = age / (float) dur;
                int alpha = Math.max(8, (int) (255 * (1f - pr)));
                int col = (alpha << 24) | (indDealt[i] ? 0x55FF55 : 0xFF5555);
                ctx.drawCenteredTextWithShadow(tr, indText[i], bx + (indDealt[i] ? 16 : -16), by - (int) (pr * 20), col);
            }
        }

        if (c.reachInfo && targetValid) {
            int x = px(c, HudLayout.TARGET, w), y = py(c, HudLayout.TARGET, h);
            ctx.drawCenteredTextWithShadow(tr, tgtDist, x, y, argb(0xFF000000, tgtDistColor));
            if (c.swingTracker) { y += 10; ctx.drawCenteredTextWithShadow(tr, tgtSwing, x, y, 0xFFDDDDDD); }
            if (c.shieldTimer && !tgtShield.isEmpty()) { y += 10; ctx.drawCenteredTextWithShadow(tr, tgtShield, x, y, 0xFFFFAA00); }
        }

        if (c.critHelper) {
            int x = px(c, HudLayout.CRIT, w), y = py(c, HudLayout.CRIT, h);
            ctx.drawCenteredTextWithShadow(tr, critText, x, y, critReady ? 0xFF55FF55 : 0xFFDDDDDD);
            ctx.fill(x - 30, y + 10, x + 30, y + 13, 0x80000000);
            ctx.fill(x - 30, y + 10, x - 30 + (int) (60 * critCd), y + 13, critReady ? 0xFF55FF55 : 0xFFFFAA00);
        }

        if (c.pingHud && !pingText.isEmpty()) {
            int x = px(c, HudLayout.PING, w), y = py(c, HudLayout.PING, h);
            ctx.drawTextWithShadow(tr, pingText, x, y, argb(0xFF000000, pingColor));
            for (int k = 0; k < pingHist.length; k++) {
                int v = Math.min(16, pingHist[(pingIdx + k) % pingHist.length] / 10);
                ctx.fill(x + k * 3, y + 28 - v, x + k * 3 + 2, y + 28, argb(0xFF000000, pingColor));
            }
        }

        if (c.debuffTracker && effCount > 0) {
            int x = px(c, HudLayout.EFFECTS, w), y = py(c, HudLayout.EFFECTS, h);
            for (int i = 0; i < effCount; i++) { ctx.drawTextWithShadow(tr, effLines[i], x, y, 0xFFFF5555); y += 10; }
        }

        if (c.itemCounter) {
            int x = px(c, HudLayout.ITEMS, w), y = py(c, HudLayout.ITEMS, h);
            ctx.drawTextWithShadow(tr, freeSlotsText, x, y - 10, 0xFFAAAAAA);
            for (int k = 0; k < COUNT_ITEMS.length; k++) {
                if (itemCnt[k] <= 0) continue;
                ctx.drawItem(COUNT_STACKS[k], x, y);
                ctx.drawTextWithShadow(tr, itemStr[k], x + 17, y + 8, 0xFFFFFFFF);
                x += 32;
            }
        }

        if (c.foodInfo)
            ctx.drawTextWithShadow(tr, foodText, px(c, HudLayout.FOOD, w), py(c, HudLayout.FOOD, h), 0xFFFFAA33);

        if (c.offhandHud && !offStack.isEmpty()) {
            int x = px(c, HudLayout.OFFHAND, w), y = py(c, HudLayout.OFFHAND, h);
            ctx.drawItem(offStack, x, y);
            if (!offText.isEmpty()) ctx.drawTextWithShadow(tr, offText, x + 17, y + 8, 0xFFFFFFFF);
        }

        if (c.waypointsHud && wpCount > 0) {
            int x = px(c, HudLayout.WAYPOINTS, w), y = py(c, HudLayout.WAYPOINTS, h);
            for (int i = 0; i < wpCount; i++) { ctx.drawTextWithShadow(tr, wpLines[i], x, y, 0xFF55FFFF); y += 10; }
        }

        if (c.tradeLogger && !tradesText.isEmpty())
            ctx.drawTextWithShadow(tr, tradesText, px(c, HudLayout.TRADES, w), py(c, HudLayout.TRADES, h), 0xFFDDDDDD);

        if (c.comboTutorial && !tutorialText.isEmpty())
            ctx.drawCenteredTextWithShadow(tr, tutorialText, px(c, HudLayout.TUTORIAL, w), py(c, HudLayout.TUTORIAL, h), 0xFFFFFF55);

        if (c.coloredHotbar) {
            for (int i = 0; i < 9; i++) {
                if (hotColor[i] == 0) continue;
                int x = w / 2 - 91 + i * 20;
                ctx.fill(x + 1, h - 24, x + 19, h - 22, argb(0xFF000000, hotColor[i]));
            }
        }
    }

    private static void renderCrosshair(DrawContext ctx, MinecraftClient mc, TacticalConfig c, int w, int h, int a) {
        PlayerEntity p = mc.player;
        int cx = px(c, HudLayout.CROSSHAIR, w), cy = py(c, HudLayout.CROSSHAIR, h);
        int color = argb(0xFF000000, hueToRgb(c.crosshairHue));

        if (c.crosshairPixelMode) {
            int s = Math.max(1, Math.round(c.crosshairPixelScale));
            int n = TacticalConfig.PIX;
            int x0 = cx - (n * s) / 2, y0 = cy - (n * s) / 2;
            for (int i = 0; i < n * n; i++) {
                if (!c.pixels[i]) continue;
                int x = x0 + (i % n) * s, y = y0 + (i / n) * s;
                ctx.fill(x, y, x + s, y + s, color);
            }
        } else {
            int t = Math.max(1, Math.round(c.crosshairThickness));
            float gap = c.crosshairGap;
            if (c.dynamicCrosshair) {
                double sp = Math.sqrt(p.getVelocity().x * p.getVelocity().x + p.getVelocity().z * p.getVelocity().z);
                gap += (float) Math.min(sp * 20, 8);
                if (!p.isOnGround()) gap += 3;
            }
            int g = Math.round(gap), len = Math.max(1, Math.round(c.crosshairLength));
            int o = t / 2;
            ctx.fill(cx - g - len, cy - o, cx - g, cy - o + t, color);
            ctx.fill(cx + g, cy - o, cx + g + len, cy - o + t, color);
            ctx.fill(cx - o, cy - g - len, cx - o + t, cy - g, color);
            ctx.fill(cx - o, cy + g, cx - o + t, cy + g + len, color);
        }

        // Круговые таймеры эффектов вокруг прицела
        if (c.potionRings) {
            for (int k = 0; k < ringCount; k++) {
                int radius = 18 + k * 5;
                int segs = (int) (ringFrac[k] * 32);
                int rc = argb(0xFF000000, ringColor[k]);
                for (int j = 0; j < segs; j++) {
                    int x = cx + (int) (SIN[j] * radius) - 1;
                    int y = cy - (int) (COS[j] * radius) - 1;
                    ctx.fill(x, y, x + 2, y + 2, rc);
                }
            }
        }
    }

    /** Оттенок 0..1 -> RGB (насыщенность и яркость максимальные). */
    public static int hueToRgb(float hue) {
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
}
