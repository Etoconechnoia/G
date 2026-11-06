package com.tacticalpvphud.hud;

/** Состояние свободного обзора (FreeLook): камера вращается отдельно от взгляда игрока. */
public final class FreeLook {
    public static boolean active;
    /** Становится true, когда хук камеры реально сработал (иначе мышь не перехватываем). */
    public static boolean hookOk;
    public static float camYaw, camPitch;

    public static float yaw(float original) {
        hookOk = true;
        return active ? camYaw : original;
    }

    public static float pitch(float original) {
        return active ? camPitch : original;
    }

    private FreeLook() {}
}
