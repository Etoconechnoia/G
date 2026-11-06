package com.tacticalpvphud.hud;

import com.tacticalpvphud.config.TacticalConfig;

/** Все перетаскиваемые элементы HUD: имена, позиции по умолчанию (доля экрана), размеры. */
public final class HudLayout {
    public static final int CROSSHAIR = 0, AZIMUTH = 1, ARMOR = 2, CPS = 3, COMBAT = 4,
            INDICATORS = 5, TARGET = 6, PING = 7, SESSION = 8, EFFECTS = 9, ITEMS = 10,
            FOOD = 11, OFFHAND = 12, CRIT = 13, WAYPOINTS = 14, TRADES = 15, TUTORIAL = 16, EMPTYHAND = 17,
            KEYS = 18, COMBO = 19, KILLS = 20, INFO = 21, NEAREST = 22, OWNHP = 23, INVENTORY = 24,
            DPS = 25, FALL = 26, ALERTS = 27, KILLFEED = 28, USEBAR = 29, STATS = 30, MINIMAP = 31, MODEPANEL = 32, MINIINFO = 33;
    public static final int COUNT = 34;

    public static final String[] NAMES = {
            "Прицел", "Азимут", "Броня", "CPS", "Таймер боя", "Урон (числа)", "Цель", "Пинг", "Сессия",
            "Эффекты", "Предметы", "Еда", "Левая рука", "Крит/КД", "Метки", "Обмен уроном", "Подсказка", "Пустая рука",
            "Клавиши", "Комбо", "Убийства", "Инфо (FPS/коорд.)", "Ближайший игрок", "Моё HP", "Инвентарь",
            "DPS", "Падение", "Тревоги", "Лента убийств", "Полоса действия", "Статистика", "Мини-карта", "Режим PvP", "MiniHUD-инфо"};

    // Доля ширины/высоты экрана
    public static final float[] DX = {0.5f, 0.5f, 0.01f, 0.88f, 0.5f, 0.5f, 0.5f, 0.86f, 0.01f,
            0.80f, 0.01f, 0.62f, 0.30f, 0.5f, 0.01f, 0.01f, 0.5f, 0.5f,
            0.01f, 0.5f, 0.88f, 0.01f, 0.5f, 0.38f, 0.30f,
            0.88f, 0.5f, 0.5f, 0.5f, 0.5f, 0.01f, 0.80f, 0.20f, 0.20f};
    public static final float[] DY = {0.5f, 0.01f, 0.40f, 0.01f, 0.07f, 0.42f, 0.58f, 0.06f, 0.01f,
            0.30f, 0.78f, 0.84f, 0.90f, 0.66f, 0.55f, 0.22f, 0.74f, 0.30f,
            0.65f, 0.34f, 0.12f, 0.07f, 0.12f, 0.80f, 0.20f,
            0.14f, 0.18f, 0.26f, 0.38f, 0.72f, 0.27f, 0.16f, 0.45f, 0.07f};

    // Размер рамки (для редактора)
    public static final int[] W = {30, 40, 56, 70, 90, 60, 110, 90, 90, 100, 150, 80, 24, 60, 120, 120, 130, 100,
            50, 80, 80, 100, 150, 50, 162,
            70, 120, 120, 120, 60, 130, 94, 150, 190};
    public static final int[] H = {30, 10, 76, 10, 10, 24, 40, 30, 10, 50, 22, 10, 20, 18, 50, 20, 10, 10,
            46, 10, 10, 40, 22, 10, 54,
            10, 10, 10, 10, 8, 30, 94, 60, 60};

    /** 0 = якорь слева-сверху, 1 = по центру по горизонтали, 2 = по центру в обе стороны. */
    public static final int[] MODE = {2, 1, 0, 0, 1, 2, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1,
            0, 1, 0, 0, 1, 0, 0,
            0, 1, 1, 1, 1, 0, 0, 0, 0};

    public static int left(int i, int px) { return px - (MODE[i] >= 1 ? W[i] / 2 : 0); }
    public static int top(int i, int py) { return py - (MODE[i] == 2 ? H[i] / 2 : 0); }

    public static void reset(TacticalConfig c) {
        c.posX = DX.clone();
        c.posY = DY.clone();
    }

    /** Дополняет сохранённые позиции новыми элементами (старые позиции не трогаем). */
    public static void migrate(TacticalConfig c) {
        float[] ox = c.posX, oy = c.posY;
        c.posX = DX.clone();
        c.posY = DY.clone();
        if (ox != null && oy != null) {
            int n = Math.min(Math.min(ox.length, oy.length), COUNT);
            for (int i = 0; i < n; i++) { c.posX[i] = ox[i]; c.posY[i] = oy[i]; }
        }
    }

    private HudLayout() {}
}
