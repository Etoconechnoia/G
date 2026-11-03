package com.tacticalpvphud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tacticalpvphud.TacticalPvPHUD;
import com.tacticalpvphud.hud.HudLayout;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Настройки мода (config/tacticalpvphud.json). Поля публичные — GUI пишет в них напрямую. */
public class TacticalConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tacticalpvphud.json");
    private static TacticalConfig instance;

    public static final int PIX = 11; // сетка попиксельного прицела 11x11

    // ---------- Визуал ----------
    public boolean azimuthEnabled = true;
    public boolean customCrosshair = true;
    public boolean dynamicCrosshair = true;
    public boolean crosshairPixelMode = false;
    public float crosshairThickness = 2f;
    public float crosshairGap = 3f;
    public float crosshairLength = 5f;
    public float crosshairHue = 0.33f;
    public float crosshairPixelScale = 2f;
    public boolean[] pixels = defaultPixels();
    public boolean armorHudEnabled = true;
    public boolean brokenArmorAlert = true;
    public float hudOpacity = 1f;
    public boolean hideEffectsOverlay = false;
    public boolean coloredHotbar = true;

    // ---------- Индикаторы ----------
    public boolean damageIndicators = true;
    public float indicatorDuration = 1.2f;
    public boolean reachInfo = true;
    public boolean swingTracker = true;
    public boolean shieldTimer = true;
    public boolean itemCounter = true;
    public boolean foodInfo = true;
    public boolean offhandHud = true;
    public boolean emptyHandAlert = true;
    public boolean potionRings = true;
    public boolean debuffTracker = true;
    public boolean pingHud = true;
    public boolean critHelper = true;

    // ---------- PvP ----------
    public boolean cpsEnabled = true;
    public boolean combatMode = true;
    public boolean combatFrame = true;
    public boolean tradeLogger = true;
    public boolean sessionTimer = true;
    public boolean comboTutorial = false;

    // ---------- Новое в 0.5 (по умолчанию выключено, чтобы не захламлять экран) ----------
    public boolean comboCounter = false;
    public boolean killCounter = false;
    public boolean keystrokes = false;
    public boolean infoFps = false;
    public boolean infoSpeed = false;
    public boolean infoCoords = false;
    public boolean infoClock = false;
    public boolean ownHp = false;
    public boolean nearestEnemy = false;
    public boolean toolDurability = false;
    public boolean lowHpAlert = true;
    public boolean hitFlash = true;
    public boolean biomeInfo = false;
    public boolean inventoryHud = false;

    // ---------- Прочее ----------
    public boolean zoomEnabled = true;
    public float zoomFov = 30f;
    public boolean chatFilter = false;
    public String chatFilterWords = "";
    public boolean waypointsHud = true;
    public List<Waypoint> waypoints = new ArrayList<>();

    // ---------- Круг предметов (колесо) ----------
    public boolean radialEnabled = true;
    public int radialKey = 71; // G
    public List<String> radialItems = new ArrayList<>();
    public boolean radialSeeded = false;

    // ---------- Бинды предметов ----------
    public boolean bindsEnabled = true;
    public List<Bind> binds = new ArrayList<>();

    // ---------- Позиции элементов HUD ----------
    public float[] posX;
    public float[] posY;

    public static class Bind {
        public String item;
        public int key;
        public Bind(String item, int key) { this.item = item; this.key = key; }
    }

    public static class Waypoint {
        public String name;
        public int x, y, z;
        public Waypoint(String name, int x, int y, int z) { this.name = name; this.x = x; this.y = y; this.z = z; }
    }

    /** Пресеты HUD: 0 = минимум, 1 = PvP, 2 = всё. */
    public void applyPreset(int p) {
        boolean all = p == 2, pvp = p == 1, mid = all || pvp;
        azimuthEnabled = all;
        customCrosshair = true;
        armorHudEnabled = mid;
        brokenArmorAlert = true;
        coloredHotbar = mid;
        damageIndicators = true;
        reachInfo = mid;
        swingTracker = all;
        shieldTimer = all;
        critHelper = mid;
        itemCounter = all;
        foodInfo = all;
        offhandHud = mid;
        emptyHandAlert = mid;
        potionRings = mid;
        debuffTracker = all;
        pingHud = all;
        cpsEnabled = mid;
        combatMode = mid;
        combatFrame = mid;
        tradeLogger = all;
        sessionTimer = all;
        comboTutorial = all;
        waypointsHud = all;
        comboCounter = mid;
        killCounter = all;
        keystrokes = all;
        infoFps = all;
        infoSpeed = all;
        infoCoords = all;
        infoClock = all;
        ownHp = mid;
        nearestEnemy = mid;
        toolDurability = all;
        lowHpAlert = true;
        hitFlash = true;
        biomeInfo = all;
        inventoryHud = all;
    }

    public static boolean[] defaultPixels() {
        boolean[] p = new boolean[PIX * PIX];
        int c = PIX / 2;
        for (int i = 0; i < PIX; i++) {
            if (Math.abs(i - c) >= 2) { p[c * PIX + i] = true; p[i * PIX + c] = true; }
        }
        return p;
    }

    // ---------- Фильтр чата (кэш слов) ----------
    private transient String filterSrc = null;
    private transient String[] filterArr = new String[0];

    public String[] filterWords() {
        if (!Objects.equals(filterSrc, chatFilterWords)) {
            filterSrc = chatFilterWords;
            List<String> out = new ArrayList<>();
            if (chatFilterWords != null) {
                for (String s : chatFilterWords.split(",")) {
                    String t = s.trim().toLowerCase(Locale.ROOT);
                    if (!t.isEmpty()) out.add(t);
                }
            }
            filterArr = out.toArray(new String[0]);
        }
        return filterArr;
    }

    // ---------- Загрузка / сохранение ----------
    public static TacticalConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        boolean existed = Files.exists(FILE);
        try {
            if (existed) instance = GSON.fromJson(Files.readString(FILE), TacticalConfig.class);
        } catch (Exception e) {
            TacticalPvPHUD.LOGGER.warn("Не удалось прочитать конфиг, значения по умолчанию", e);
        }
        if (instance == null) instance = new TacticalConfig();
        if (instance.binds == null) instance.binds = new ArrayList<>();
        if (instance.waypoints == null) instance.waypoints = new ArrayList<>();
        if (instance.pixels == null || instance.pixels.length != PIX * PIX) instance.pixels = defaultPixels();
        if (instance.chatFilterWords == null) instance.chatFilterWords = "";
        if (instance.radialItems == null) instance.radialItems = new ArrayList<>();
        if (!instance.radialSeeded && instance.radialItems.isEmpty()) {
            instance.radialItems.add("minecraft:totem_of_undying");
            instance.radialItems.add("minecraft:ender_pearl");
            instance.radialItems.add("minecraft:golden_apple");
            instance.radialItems.add("minecraft:shield");
            instance.radialSeeded = true;
        }
        if (instance.posX == null || instance.posY == null
                || instance.posX.length != HudLayout.COUNT || instance.posY.length != HudLayout.COUNT) {
            HudLayout.migrate(instance);
        }
        if (!existed && instance.binds.isEmpty()) {
            instance.binds.add(new Bind("minecraft:ender_pearl", 72)); // пример: жемчуг на H
        }
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(get()));
        } catch (IOException e) {
            TacticalPvPHUD.LOGGER.error("Не удалось сохранить конфиг", e);
        }
    }
}
