package com.tacticalpvphud;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Главный (общий) класс мода.
 * Мод чисто клиентский, поэтому здесь только логгер и ID мода.
 */
public class TacticalPvPHUD implements ModInitializer {

    /** Идентификатор мода — должен совпадать с "id" в fabric.mod.json. */
    public static final String MOD_ID = "tacticalpvphud";

    /** Общий логгер мода. */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("TacticalPvPHUD: общая инициализация завершена");
    }
}
