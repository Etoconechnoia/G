package com.tacticalpvphud.hud;

import com.tacticalpvphud.config.TacticalConfig;

/** Все перетаскиваемые элементы HUD: имена, позиции по умолчанию (доля экрана), размеры. */
public final class HudLayout {
    public static final int CROSSHAIR = 0, AZIMUTH = 1, ARMOR = 2, CPS = 3, COMBAT = 4,
            INDICATORS = 5, TARGET = 6, PING = 7, SESSION = 8, EFFECTS = 9, ITEMS = 10,
            FOOD = 11, OFFHAND = 12, CRIT = 13, WAYPOINTS = 14, TRADES = 15, TUTORIAL = 16, EMPTYHAND = 17;
    public static final int COUNT = 18;

    public static final String[] NAMES = {
            "Прицел", "Азимут", "Броня", "CPS", "Таймер боя", "Урон (числа)", "Цель", "Пинг", "Сессия",
            "Эффекты", "Предметы", "Еда", "Левая рука", "Крит/КД", "Метки", "Обмен уроном", "Подсказка", "Пустая рука"};

    // Доля ширины/высоты экрана
    public static final float[] DX = {0.5f, 0.5f, 0.01f, 0.88f, 0.5f, 0.5f, 0.5f, 0.86f, 0.01f,
            0.80f, 0.01f, 0.62f, 0.30f, 0.5f, 0.01f, 0.01f, 0.5f, 0.5f};
    public static final float[] DY = {0.5f, 0.01f, 0.40f, 0.01f, 0.07f, 0.42f, 0.58f, 0.06f, 0.01f,
            0.30f, 0.78f, 0.84f, 0.90f, 0.66f, 0.55f, 0.22f, 0.74f, 0.30f};

    // Размер рамки (для редактора)
    public static final int[] W = {30, 40, 56, 70, 90, 60, 110, 90, 90, 100, 150, 80, 24, 60, 120, 120, 130, 100};
    public static final int[] H = {30, 10, 76, 10, 10, 24, 30, 30, 10, 50, 22, 10, 20, 18, 50, 20, 10, 10};

    /** 0 = якорь слева-сверху, 1 = по центру по горизонтали, 2 = по центру в обе стороны. */
    public static final int[] MODE = {2, 1, 0, 0, 1, 2, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1};

    public static int left(int i, int px) { return px - (MODE[i] >= 1 ? W[i] / 2 : 0); }
    public static int top(int i, int py) { return py - (MODE[i] == 2 ? H[i] / 2 : 0); }

    public static void reset(TacticalConfig c) {
        c.posX = DX.clone();
        c.posY = DY.clone();
    }

    private HudLayout() {}
}
