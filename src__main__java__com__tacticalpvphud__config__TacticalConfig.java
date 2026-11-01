package com.tacticalpvphud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tacticalpvphud.TacticalPvPHUD;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Настройки мода. Все поля публичные — GUI читает/пишет их напрямую.
 * Сохраняется в config/tacticalpvphud.json.
 */
public class TacticalConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tacticalpvphud.json");
    private static TacticalConfig instance;

    // ---------- 1. Интерфейс ----------
    public boolean azimuthEnabled = true;
    public boolean armorHudEnabled = true;
    public boolean brokenArmorAlert = true;
    public boolean customCrosshair = true;
    public boolean dynamicCrosshair = true;
    public float crosshairThickness = 2f;   // 1..5 px
    public float crosshairGap = 3f;         // 0..10 px
    public float crosshairHue = 0.33f;      // 0..1 (оттенок)
    public float hudOpacity = 1f;           // 0.1..1

    // ---------- 2. PvP ----------
    public boolean cpsEnabled = true;
    public boolean combatMode = true;
    public boolean combatFrame = true;

    // ---------- 3. Бинды предметов ----------
    public boolean bindsEnabled = true;
    public List<Bind> binds = new ArrayList<>();

    /** Одна привязка: предмет -> клавиша (GLFW-код, -1 = не задана). */
    public static class Bind {
        public String item;
        public int key;
        public Bind(String item, int key) { this.item = item; this.key = key; }
    }

    // ---------- Загрузка / сохранение ----------
    public static TacticalConfig get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                instance = GSON.fromJson(Files.readString(FILE), TacticalConfig.class);
            }
        } catch (Exception e) {
            TacticalPvPHUD.LOGGER.warn("Не удалось прочитать конфиг, используем значения по умолчанию", e);
        }
        if (instance == null) instance = new TacticalConfig();
        if (instance.binds == null) instance.binds = new ArrayList<>();
        // Пример по умолчанию: эндер-жемчуг на H (можно изменить или удалить в меню)
        if (!Files.exists(FILE) && instance.binds.isEmpty()) {
            instance.binds.add(new Bind("minecraft:ender_pearl", 72));
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
