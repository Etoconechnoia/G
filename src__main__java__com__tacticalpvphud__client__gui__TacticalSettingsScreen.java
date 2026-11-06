package com.tacticalpvphud.client.gui;

import com.tacticalpvphud.config.TacticalConfig;
import com.tacticalpvphud.hud.HudRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;

/**
 * Меню настроек (клавиша M). Слева категории, справа строки настроек с листанием страниц.
 */
public class TacticalSettingsScreen extends Screen {

    private enum Category {
        VISUAL("Визуал"), INDICATORS("Индикаторы"), PVP("PvP"), BINDS("Бинды"), RADIAL("Круг"), WAYPOINTS("Метки"), MISC("Прочее");
        final String label;
        Category(String l) { label = l; }
    }

    private record IconDraw(ItemStack stack, int x, int y) {}

    private final Screen parent;
    private Category current = Category.VISUAL;
    private int page = 0;
    private boolean picking = false;
    private boolean pickingRadial = false;
    private boolean capturingRadial = false;
    private int capturingPhrase = -1;
    private boolean capturingFreelook = false;
    private boolean capturingWpKey = false;
    private String wpName = "", wpX = "", wpY = "", wpZ = "";
    private int capturing = -1;
    private final List<IconDraw> icons = new ArrayList<>();

    public TacticalSettingsScreen(Screen parent) {
        super(Text.translatable("screen.tacticalpvphud.settings"));
        this.parent = parent;
    }

    // ===================== построение экрана =====================
    @Override
    protected void init() {
        TacticalConfig c = TacticalConfig.get();
        icons.clear();

        int y = 40;
        for (Category cat : Category.values()) {
            addDrawableChild(ButtonWidget.builder(Text.literal((cat == current ? "> " : "") + cat.label), btn -> {
                current = cat;
                page = 0;
                picking = false;
                pickingRadial = false;
                capturingRadial = false;
                capturingPhrase = -1;
                capturingFreelook = false;
                capturingWpKey = false;
                capturing = -1;
                clearAndInit();
            }).dimensions(10, y, 105, 20).build());
            y += 24;
        }

        List<IntConsumer> rows = switch (current) {
            case VISUAL -> visualRows(c);
            case INDICATORS -> indicatorRows(c);
            case PVP -> pvpRows(c);
            case BINDS -> bindRows(c);
            case RADIAL -> radialRows(c);
            case WAYPOINTS -> waypointRows(c);
            case MISC -> miscRows(c);
        };

        int top = 40;
        int perPage = Math.max(2, (height - top - 34) / 24);
        int pages = Math.max(1, (rows.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * perPage;
        int to = Math.min(rows.size(), from + perPage);
        for (int i = from; i < to; i++) rows.get(i).accept(top + (i - from) * 24);

        int by = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page--; clearAndInit(); })
                .dimensions(width / 2 - 104, by, 48, 20).build()).active = page > 0;
        addDrawableChild(ButtonWidget.builder(Text.literal("Готово " + (page + 1) + "/" + pages), b -> close())
                .dimensions(width / 2 - 50, by, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { page++; clearAndInit(); })
                .dimensions(width / 2 + 56, by, 48, 20).build()).active = page < pages - 1;
    }

    // ===================== категории =====================
    private List<IntConsumer> visualRows(TacticalConfig c) {
        List<IntConsumer> r = new ArrayList<>();
        r.add(button("Пресет: МИНИМУМ", () -> { c.applyPreset(0); clearAndInit(); }));
        r.add(button("Пресет: PvP", () -> { c.applyPreset(1); clearAndInit(); }));
        r.add(button("Пресет: ВСЁ", () -> { c.applyPreset(2); clearAndInit(); }));
        r.add(toggle("Азимут", () -> c.azimuthEnabled, v -> c.azimuthEnabled = v));
        r.add(toggle("Свой прицел", () -> c.customCrosshair, v -> c.customCrosshair = v));
        r.add(toggle("Динамический прицел", () -> c.dynamicCrosshair, v -> c.dynamicCrosshair = v));
        r.add(slider("Толщина", 1, 5, () -> c.crosshairThickness, v -> c.crosshairThickness = (float) v));
        r.add(slider("Зазор", 0, 10, () -> c.crosshairGap, v -> c.crosshairGap = (float) v));
        r.add(slider("Длина линий", 2, 15, () -> c.crosshairLength, v -> c.crosshairLength = (float) v));
        r.add(slider("Цвет (оттенок)", 0, 1, () -> c.crosshairHue, v -> c.crosshairHue = (float) v));
        r.add(toggle("Попиксельный прицел", () -> c.crosshairPixelMode, v -> c.crosshairPixelMode = v));
        r.add(slider("Размер пикселя", 1, 4, () -> c.crosshairPixelScale, v -> c.crosshairPixelScale = (float) v));
        r.add(button("Рисовать прицел (пиксели)", () -> client.setScreen(new PixelEditorScreen(this))));
        r.add(button("Расставить HUD (перетаскивание)", () -> client.setScreen(new HudEditorScreen(this))));
        r.add(toggle("HUD брони", () -> c.armorHudEnabled, v -> c.armorHudEnabled = v));
        r.add(toggle("Алерт сломанной брони", () -> c.brokenArmorAlert, v -> c.brokenArmorAlert = v));
        r.add(toggle("Цветной хотбар", () -> c.coloredHotbar, v -> c.coloredHotbar = v));
        r.add(toggle("Скрыть значки эффектов", () -> c.hideEffectsOverlay, v -> c.hideEffectsOverlay = v));
        r.add(slider("Прозрачность HUD", 0.1, 1, () -> c.hudOpacity, v -> c.hudOpacity = (float) v));
        return r;
    }

    private List<IntConsumer> indicatorRows(TacticalConfig c) {
        List<IntConsumer> r = new ArrayList<>();
        r.add(toggle("Числа урона", () -> c.damageIndicators, v -> c.damageIndicators = v));
        r.add(slider("Время чисел (сек)", 0.5, 3, () -> c.indicatorDuration, v -> c.indicatorDuration = (float) v));
        r.add(toggle("Дистанция до цели", () -> c.reachInfo, v -> c.reachInfo = v));
        r.add(toggle("Трекер замаха врага", () -> c.swingTracker, v -> c.swingTracker = v));
        r.add(toggle("Таймер щита врага", () -> c.shieldTimer, v -> c.shieldTimer = v));
        r.add(toggle("Крит / кулдаун", () -> c.critHelper, v -> c.critHelper = v));
        r.add(toggle("Счётчик предметов", () -> c.itemCounter, v -> c.itemCounter = v));
        r.add(toggle("Еда / насыщение", () -> c.foodInfo, v -> c.foodInfo = v));
        r.add(toggle("Предмет в левой руке", () -> c.offhandHud, v -> c.offhandHud = v));
        r.add(toggle("Алерт пустой руки", () -> c.emptyHandAlert, v -> c.emptyHandAlert = v));
        r.add(toggle("Кольца таймеров эффектов", () -> c.potionRings, v -> c.potionRings = v));
        r.add(toggle("Список дебаффов", () -> c.debuffTracker, v -> c.debuffTracker = v));
        r.add(toggle("Пинг + график", () -> c.pingHud, v -> c.pingHud = v));
        r.add(toggle("Ближайший игрок", () -> c.nearestEnemy, v -> c.nearestEnemy = v));
        r.add(toggle("Моё HP цифрой", () -> c.ownHp, v -> c.ownHp = v));
        r.add(toggle("Тревога низкого HP", () -> c.lowHpAlert, v -> c.lowHpAlert = v));
        r.add(toggle("Прочность предмета в руке", () -> c.toolDurability, v -> c.toolDurability = v));
        r.add(toggle("FPS", () -> c.infoFps, v -> c.infoFps = v));
        r.add(toggle("Скорость", () -> c.infoSpeed, v -> c.infoSpeed = v));
        r.add(toggle("Координаты", () -> c.infoCoords, v -> c.infoCoords = v));
        r.add(toggle("Часы", () -> c.infoClock, v -> c.infoClock = v));
        r.add(toggle("Биом", () -> c.biomeInfo, v -> c.biomeInfo = v));
        r.add(toggle("Инвентарь на экране", () -> c.inventoryHud, v -> c.inventoryHud = v));
        r.add(toggle("Вспышка прицела при попадании", () -> c.hitFlash, v -> c.hitFlash = v));
        r.add(toggle("Цель: броня и предмет", () -> c.targetGear, v -> c.targetGear = v));
        r.add(toggle("Кольцо кулдауна у прицела", () -> c.cooldownRing, v -> c.cooldownRing = v));
        r.add(toggle("Тревоги (тотем/голод/добивай)", () -> c.alerts, v -> c.alerts = v));
        r.add(toggle("Прогноз урона от падения", () -> c.fallInfo, v -> c.fallInfo = v));
        r.add(toggle("Полоса еды / лука", () -> c.useBar, v -> c.useBar = v));
        r.add(toggle("Состояние (спринт/присед)", () -> c.stateInfo, v -> c.stateInfo = v));
        r.add(toggle("Игровое время", () -> c.gameTime, v -> c.gameTime = v));
        r.add(toggle("Игроков рядом", () -> c.playersNear, v -> c.playersNear = v));
        r.add(toggle("Все эффекты списком", () -> c.effectsAll, v -> c.effectsAll = v));
        r.add(toggle("Адрес сервера", () -> c.infoServer, v -> c.infoServer = v));
        r.add(toggle("Память (ОЗУ)", () -> c.infoRam, v -> c.infoRam = v));
        r.add(toggle("MiniHUD: уровень света", () -> c.miniLight, v -> c.miniLight = v));
        r.add(toggle("MiniHUD: чанк и регион", () -> c.miniChunkInfo, v -> c.miniChunkInfo = v));
        r.add(toggle("MiniHUD: куда смотрю", () -> c.miniFacingInfo, v -> c.miniFacingInfo = v));
        r.add(toggle("MiniHUD: блок под прицелом", () -> c.miniBlockInfo, v -> c.miniBlockInfo = v));
        r.add(toggle("MiniHUD: измерение", () -> c.miniDimInfo, v -> c.miniDimInfo = v));
        r.add(toggle("MiniHUD: число сущностей", () -> c.miniEntInfo, v -> c.miniEntInfo = v));
        r.add(toggle("Авто-спринт (toggle sprint)", () -> c.toggleSprint, v -> c.toggleSprint = v));
        return r;
    }

    private List<IntConsumer> pvpRows(TacticalConfig c) {
        List<IntConsumer> r = new ArrayList<>();
        r.add(toggle("Счётчик CPS", () -> c.cpsEnabled, v -> c.cpsEnabled = v));
        r.add(toggle("Режим боя (таймер)", () -> c.combatMode, v -> c.combatMode = v));
        r.add(toggle("Рамка боя", () -> c.combatFrame, v -> c.combatFrame = v));
        r.add(toggle("Обмен уроном", () -> c.tradeLogger, v -> c.tradeLogger = v));
        r.add(toggle("Таймер сессии", () -> c.sessionTimer, v -> c.sessionTimer = v));
        r.add(toggle("Подсказки комбо", () -> c.comboTutorial, v -> c.comboTutorial = v));
        r.add(toggle("Счётчик комбо", () -> c.comboCounter, v -> c.comboCounter = v));
        r.add(toggle("Счётчик убийств", () -> c.killCounter, v -> c.killCounter = v));
        r.add(toggle("Клавиши WASD/мышь", () -> c.keystrokes, v -> c.keystrokes = v));
        r.add(toggle("DPS", () -> c.dpsMeter, v -> c.dpsMeter = v));
        r.add(toggle("Статистика (точность, К/Д)", () -> c.statsPanel, v -> c.statsPanel = v));
        r.add(toggle("Лента убийств", () -> c.killFeed, v -> c.killFeed = v));
        r.add(toggle("Красный квадрат: куда пойдёт враг", () -> c.enemyPredict, v -> c.enemyPredict = v));
        r.add(slider("Предсказание врага (сек)", 0.2, 1.5, () -> c.enemyPredictSec, v -> c.enemyPredictSec = (float) v));
        r.add(toggle("Красный экран при уроне", () -> c.damageTint, v -> c.damageTint = v));
        r.add(toggle("Звук при своей смерти", () -> c.oofSound, v -> c.oofSound = v));
        r.add(toggle("Таймер TNT", () -> c.tntCountdown, v -> c.tntCountdown = v));
        r.add(toggle("Ловушки: предупреждения", () -> c.trapHelper, v -> c.trapHelper = v));
        r.add(toggle("Ловушки: запасы предметов", () -> c.trapItems, v -> c.trapItems = v));
        r.add(toggle("Ловушки: враг у моей ловушки", () -> c.trapPointAlert, v -> c.trapPointAlert = v));
        r.add(slider("Радиус тревоги ловушки (бл.)", 3, 20, () -> c.trapRadius, v -> c.trapRadius = (float) v));
        r.add(toggle("Звук при попадании", () -> c.hitSound, v -> c.hitSound = v));
        r.add(toggle("Звук при убийстве", () -> c.killSound, v -> c.killSound = v));
        r.add(toggle("Вспышка при убийстве", () -> c.killFlash, v -> c.killFlash = v));
        r.add(toggle("Вспышка при тотеме", () -> c.totemFlash, v -> c.totemFlash = v));
        r.add(toggle("Крестик при попадании", () -> c.hitCross, v -> c.hitCross = v));
        r.add(toggle("Помощник режима PvP", () -> c.modePanel, v -> c.modePanel = v));
        r.add(button("Режим: " + HudRenderer.MODE_NAMES[Math.floorMod(c.pvpMode, HudRenderer.MODE_NAMES.length)] + " (след.)", () -> {
            c.pvpMode = (c.pvpMode + 1) % HudRenderer.MODE_NAMES.length;
            clearAndInit();
        }));
        return r;
    }

    private List<IntConsumer> miscRows(TacticalConfig c) {
        List<IntConsumer> r = new ArrayList<>();
        r.add(toggle("FreeLook (свободный обзор)", () -> c.freelookEnabled, v -> c.freelookEnabled = v));
        r.add(button("Клавиша FreeLook: " + (capturingFreelook ? "нажми клавишу..." : keyName(c.freelookKey)), () -> {
            capturingFreelook = true;
            clearAndInit();
        }));
        r.add(toggle("Без покачивания камеры (no bob)", () -> c.noBob, v -> c.noBob = v));
        r.add(toggle("Всегда ясная погода (у себя)", () -> c.weatherClear, v -> c.weatherClear = v));
        r.add(toggle("Без размытия меню", () -> c.menuBlurOff, v -> c.menuBlurOff = v));
        r.add(toggle("Кулдауны предметов", () -> c.cooldownsHud, v -> c.cooldownsHud = v));
        r.add(slider("Громкость музыки", 0, 1, () -> client.options.getSoundVolumeOption(SoundCategory.MUSIC).getValue(),
                v -> client.options.getSoundVolumeOption(SoundCategory.MUSIC).setValue(v)));
        r.add(slider("Громкость погоды", 0, 1, () -> client.options.getSoundVolumeOption(SoundCategory.WEATHER).getValue(),
                v -> client.options.getSoundVolumeOption(SoundCategory.WEATHER).setValue(v)));
        r.add(slider("Громкость окружения", 0, 1, () -> client.options.getSoundVolumeOption(SoundCategory.AMBIENT).getValue(),
                v -> client.options.getSoundVolumeOption(SoundCategory.AMBIENT).setValue(v)));
        r.add(toggle("Авто-приседание (toggle sneak)", () -> c.toggleSneak, v -> c.toggleSneak = v));
        r.add(toggle("Меньше частиц", () -> c.lowParticles, v -> c.lowParticles = v));
        r.add(toggle("Скрыть босс-бар", () -> c.hideBossBar, v -> c.hideBossBar = v));
        r.add(toggle("Скрыть таблицу очков", () -> c.hideScoreboard, v -> c.hideScoreboard = v));
        r.add(toggle("Время в чате", () -> c.chatTime, v -> c.chatTime = v));
        r.add(toggle("Скрывать мой ник (стример)", () -> c.nickHider, v -> c.nickHider = v));
        r.add(toggle("Защита команд", () -> c.cmdProtect, v -> c.cmdProtect = v));
        r.add(y -> {
            TextFieldWidget f = new TextFieldWidget(textRenderer, 130, y, 218, 20, Text.literal("Команды"));
            f.setMaxLength(300);
            f.setText(c.cmdProtectWords);
            f.setChangedListener(s -> c.cmdProtectWords = s);
            addDrawableChild(f);
        });
        r.add(toggle("Секундомер на экране", () -> c.stopwatchHud, v -> c.stopwatchHud = v));
        r.add(button("Секундомер: старт / стоп", HudRenderer::swToggle));
        r.add(button("Секундомер: сброс", HudRenderer::swReset));
        r.add(toggle("Время в игре (наиграно)", () -> c.playtimeHud, v -> c.playtimeHud = v));
        r.add(toggle("Быстрые фразы (клавиша -> чат)", () -> c.quickPhrases, v -> c.quickPhrases = v));
        for (int i = 0; i < c.phrases.size(); i++) {
            final int pidx = i;
            TacticalConfig.Phrase ph = c.phrases.get(i);
            r.add(y -> {
                TextFieldWidget f = new TextFieldWidget(textRenderer, 130, y, 120, 20, Text.literal("Фраза"));
                f.setMaxLength(200);
                f.setText(ph.text == null ? "" : ph.text);
                f.setChangedListener(s -> ph.text = s);
                addDrawableChild(f);
                addDrawableChild(ButtonWidget.builder(Text.literal(capturingPhrase == pidx ? "..." : keyName(ph.key)), b -> {
                    capturingPhrase = pidx;
                    clearAndInit();
                }).dimensions(254, y, 70, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> {
                    c.phrases.remove(pidx);
                    clearAndInit();
                }).dimensions(328, y, 20, 20).build());
            });
        }
        r.add(button("+ Добавить фразу", () -> { c.phrases.add(new TacticalConfig.Phrase("", -1)); clearAndInit(); }));
        r.add(toggle("Мини-карта", () -> c.minimapEnabled, v -> c.minimapEnabled = v));
        r.add(toggle("Карта вращается", () -> c.minimapRotate, v -> c.minimapRotate = v));
        r.add(toggle("Круглая карта", () -> c.minimapCircle, v -> c.minimapCircle = v));
        r.add(toggle("Мобы на карте", () -> c.minimapMobs, v -> c.minimapMobs = v));
        r.add(toggle("Буква С (север)", () -> c.minimapNorth, v -> c.minimapNorth = v));
        r.add(toggle("Игроки на карте", () -> c.minimapPlayers, v -> c.minimapPlayers = v));
        r.add(toggle("Метки на карте", () -> c.minimapWaypoints, v -> c.minimapWaypoints = v));
        r.add(toggle("Предсказание движения", () -> c.minimapPredict, v -> c.minimapPredict = v));
        r.add(slider("Секунд вперёд (1-5)", 1, 5, () -> c.predictSeconds, v -> c.predictSeconds = (float) v));
        r.add(slider("Размер клетки (px)", 1, 4, () -> c.minimapScale, v -> c.minimapScale = (float) v));
        r.add(slider("Радиус карты (блоки)", 12, 64, () -> c.minimapRange, v -> c.minimapRange = (float) v));
        r.add(toggle("Зум (клавиша C)", () -> c.zoomEnabled, v -> c.zoomEnabled = v));
        r.add(slider("Сила зума (FOV)", 30, 90, () -> c.zoomFov, v -> c.zoomFov = (float) v));
        r.add(toggle("Фильтр чата", () -> c.chatFilter, v -> c.chatFilter = v));
        r.add(y -> {
            TextFieldWidget f = new TextFieldWidget(textRenderer, 130, y, 218, 20, Text.literal("Слова"));
            f.setMaxLength(300);
            f.setText(c.chatFilterWords);
            f.setChangedListener(s -> c.chatFilterWords = s);
            addDrawableChild(f);
        });
        return r;
    }

    /** Вкладка «Метки»: поставить на своей позиции, по координатам, по клавише; удалить. */
    private List<IntConsumer> waypointRows(TacticalConfig c) {
        List<IntConsumer> r = new ArrayList<>();
        r.add(toggle("Метки на экране", () -> c.waypointsHud, v -> c.waypointsHud = v));
        r.add(toggle("Метки на мини-карте", () -> c.minimapWaypoints, v -> c.minimapWaypoints = v));
        r.add(button("+ Метка на моей позиции", () -> {
            if (client.player == null) return;
            c.waypoints.add(new TacticalConfig.Waypoint("Метка " + (c.waypoints.size() + 1),
                    (int) Math.floor(client.player.getX()), (int) Math.floor(client.player.getY()),
                    (int) Math.floor(client.player.getZ())));
            clearAndInit();
        }));
        r.add(button("+ Ловушка на моей позиции", () -> {
            if (client.player == null) return;
            c.waypoints.add(new TacticalConfig.Waypoint("Ловушка " + (c.waypoints.size() + 1),
                    (int) Math.floor(client.player.getX()), (int) Math.floor(client.player.getY()),
                    (int) Math.floor(client.player.getZ())));
            clearAndInit();
        }));
        r.add(button("Клавиша «метка здесь»: " + (capturingWpKey ? "нажми клавишу..." : keyName(c.waypointKey)), () -> {
            capturingWpKey = true;
            clearAndInit();
        }));
        // Ввод координат: имя, X, Y, Z. В поле X можно вставить сразу «100 64 -200» или «100, -200».
        r.add(y -> {
            TextFieldWidget nf = new TextFieldWidget(textRenderer, 130, y, 70, 20, Text.literal("Имя"));
            nf.setMaxLength(24);
            nf.setText(wpName);
            nf.setChangedListener(s -> wpName = s);
            addDrawableChild(nf);
            TextFieldWidget xf = new TextFieldWidget(textRenderer, 204, y, 46, 20, Text.literal("X"));
            xf.setMaxLength(40);
            xf.setText(wpX);
            xf.setChangedListener(s -> wpX = s);
            addDrawableChild(xf);
            TextFieldWidget yf = new TextFieldWidget(textRenderer, 254, y, 46, 20, Text.literal("Y"));
            yf.setMaxLength(8);
            yf.setText(wpY);
            yf.setChangedListener(s -> wpY = s);
            addDrawableChild(yf);
            TextFieldWidget zf = new TextFieldWidget(textRenderer, 304, y, 44, 20, Text.literal("Z"));
            zf.setMaxLength(12);
            zf.setText(wpZ);
            zf.setChangedListener(s -> wpZ = s);
            addDrawableChild(zf);
        });
        r.add(button("Добавить по координатам (имя, X, Y, Z)", () -> {
            try {
                String[] parts = wpX.trim().split("[ ,;]+");
                int x, yv, z;
                if (parts.length >= 3) {
                    x = Integer.parseInt(parts[0]);
                    yv = Integer.parseInt(parts[1]);
                    z = Integer.parseInt(parts[2]);
                } else if (parts.length == 2) {
                    x = Integer.parseInt(parts[0]);
                    z = Integer.parseInt(parts[1]);
                    yv = wpY.trim().isEmpty() ? (client.player != null ? (int) client.player.getY() : 64) : Integer.parseInt(wpY.trim());
                } else {
                    x = Integer.parseInt(wpX.trim());
                    z = Integer.parseInt(wpZ.trim());
                    yv = wpY.trim().isEmpty() ? (client.player != null ? (int) client.player.getY() : 64) : Integer.parseInt(wpY.trim());
                }
                String nm = wpName.trim().isEmpty() ? "Метка " + (c.waypoints.size() + 1) : wpName.trim();
                c.waypoints.add(new TacticalConfig.Waypoint(nm, x, yv, z));
                wpName = "";
                wpX = "";
                wpY = "";
                wpZ = "";
                clearAndInit();
            } catch (NumberFormatException e) {
                // нужны целые числа
            }
        }));
        for (int i = 0; i < c.waypoints.size(); i++) {
            final int idx = i;
            TacticalConfig.Waypoint wp = c.waypoints.get(i);
            r.add(button("Удалить: " + wp.name + " (" + wp.x + " " + wp.y + " " + wp.z + ")", () -> {
                c.waypoints.remove(idx);
                clearAndInit();
            }));
        }
        return r;
    }

    private List<IntConsumer> bindRows(TacticalConfig c) {
        List<IntConsumer> r = new ArrayList<>();

        if (picking) {
            return pickerRows(c, () -> { picking = false; clearAndInit(); }, stack -> {
                c.binds.add(new TacticalConfig.Bind(Registries.ITEM.getId(stack.getItem()).toString(), -1));
                capturing = c.binds.size() - 1;
                picking = false;
                clearAndInit();
            });
        }

        r.add(toggle("Бинды предметов", () -> c.bindsEnabled, v -> c.bindsEnabled = v));
        for (int i = 0; i < c.binds.size(); i++) {
            final int idx = i;
            TacticalConfig.Bind b = c.binds.get(i);
            Item item = Registries.ITEM.get(Identifier.of(b.item));
            r.add(y -> {
                icons.add(new IconDraw(new ItemStack(item), 130, y + 2));
                String keyTxt = capturing == idx ? "нажми клавишу..." : keyName(b.key);
                addDrawableChild(ButtonWidget.builder(Text.literal(item.getName().getString() + ": " + keyTxt), btn -> {
                    capturing = idx;
                    clearAndInit();
                }).dimensions(152, y, 172, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("X"), btn -> {
                    c.binds.remove(idx);
                    capturing = -1;
                    clearAndInit();
                }).dimensions(328, y, 20, 20).build());
            });
        }
        r.add(button("+ Добавить бинд", () -> { picking = true; capturing = -1; clearAndInit(); }));
        return r;
    }

    /** Сетка предметов из инвентаря игрока: клик по предмету вызывает onPick. */
    private List<IntConsumer> pickerRows(TacticalConfig c, Runnable onCancel, Consumer<ItemStack> onPick) {
        List<IntConsumer> r = new ArrayList<>();
        r.add(button("Отмена", onCancel));
        for (int row = 0; row < 4; row++) {
            final int rr = row;
            r.add(y -> {
                if (client == null || client.player == null) return;
                for (int col = 0; col < 9; col++) {
                    int n = rr * 9 + col;
                    int slot = n < 27 ? 9 + n : n - 27;
                    ItemStack stack = client.player.getInventory().getStack(slot);
                    if (stack.isEmpty()) continue;
                    int x = 130 + col * 24;
                    icons.add(new IconDraw(stack, x + 2, y + 2));
                    addDrawableChild(ButtonWidget.builder(Text.empty(), btn -> onPick.accept(stack))
                            .dimensions(x, y, 20, 20).build());
                }
            });
        }
        return r;
    }

    /** Вкладка «Круг»: настройка колеса предметов (до 8 сегментов). */
    private List<IntConsumer> radialRows(TacticalConfig c) {
        if (pickingRadial) {
            return pickerRows(c, () -> { pickingRadial = false; clearAndInit(); }, stack -> {
                if (c.radialItems.size() < 8) c.radialItems.add(Registries.ITEM.getId(stack.getItem()).toString());
                pickingRadial = false;
                clearAndInit();
            });
        }
        List<IntConsumer> r = new ArrayList<>();
        r.add(toggle("Круг предметов", () -> c.radialEnabled, v -> c.radialEnabled = v));
        r.add(button("Клавиша круга: " + (capturingRadial ? "нажми клавишу..." : keyName(c.radialKey)), () -> {
            capturingRadial = true;
            clearAndInit();
        }));
        for (int i = 0; i < c.radialItems.size(); i++) {
            final int idx = i;
            Item item = Registries.ITEM.get(Identifier.of(c.radialItems.get(i)));
            r.add(y -> {
                icons.add(new IconDraw(new ItemStack(item), 130, y + 2));
                addDrawableChild(ButtonWidget.builder(Text.literal((idx + 1) + ". " + item.getName().getString()), btn -> {})
                        .dimensions(152, y, 172, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("X"), btn -> {
                    c.radialItems.remove(idx);
                    clearAndInit();
                }).dimensions(328, y, 20, 20).build());
            });
        }
        if (c.radialItems.size() < 8) {
            r.add(button("+ Добавить предмет в круг", () -> { pickingRadial = true; clearAndInit(); }));
        }
        return r;
    }

    // ===================== помощники строк =====================
    private IntConsumer toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
        return y -> addDrawableChild(ButtonWidget.builder(toggleText(label, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(toggleText(label, get.getAsBoolean()));
        }).dimensions(130, y, 218, 20).build());
    }

    private IntConsumer slider(String label, double min, double max, DoubleSupplier get, DoubleConsumer set) {
        return y -> addDrawableChild(new ConfigSlider(130, y, 218, 20, label, min, max, get.getAsDouble(), set));
    }

    private IntConsumer button(String label, Runnable action) {
        return y -> addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> action.run())
                .dimensions(130, y, 218, 20).build());
    }

    private static Text toggleText(String label, boolean on) {
        return Text.literal(label + ": " + (on ? "§aВКЛ" : "§cВЫКЛ"));
    }

    private static String keyName(int key) {
        if (key <= 0) return "не задана";
        return InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
    }

    // ===================== отрисовка =====================
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 15, 0xFFFFFF);
        for (IconDraw ic : icons) ctx.drawItem(ic.stack(), ic.x(), ic.y());

        // Ожидание клавиши для бинда (опрос GLFW). Backspace очищает бинд.
        TacticalConfig c = TacticalConfig.get();
        if (capturingPhrase >= 0 && capturingPhrase < c.phrases.size() && client != null) {
            long pw = client.getWindow().getHandle();
            for (int k = 32; k <= 348; k++) {
                if (k == GLFW.GLFW_KEY_ESCAPE) continue;
                if (GLFW.glfwGetKey(pw, k) == GLFW.GLFW_PRESS) {
                    c.phrases.get(capturingPhrase).key = (k == GLFW.GLFW_KEY_BACKSPACE) ? -1 : k;
                    capturingPhrase = -1;
                    clearAndInit();
                    break;
                }
            }
        }
        if (capturingWpKey && client != null) {
            long ww = client.getWindow().getHandle();
            for (int k = 32; k <= 348; k++) {
                if (k == GLFW.GLFW_KEY_ESCAPE) continue;
                if (GLFW.glfwGetKey(ww, k) == GLFW.GLFW_PRESS) {
                    c.waypointKey = (k == GLFW.GLFW_KEY_BACKSPACE) ? -1 : k;
                    capturingWpKey = false;
                    clearAndInit();
                    break;
                }
            }
        }
        if (capturingFreelook && client != null) {
            long fw = client.getWindow().getHandle();
            for (int k = 32; k <= 348; k++) {
                if (k == GLFW.GLFW_KEY_ESCAPE) continue;
                if (GLFW.glfwGetKey(fw, k) == GLFW.GLFW_PRESS) {
                    c.freelookKey = (k == GLFW.GLFW_KEY_BACKSPACE) ? -1 : k;
                    capturingFreelook = false;
                    clearAndInit();
                    break;
                }
            }
        }
        if (capturingRadial && client != null) {
            long win = client.getWindow().getHandle();
            for (int k = 32; k <= 348; k++) {
                if (k == GLFW.GLFW_KEY_ESCAPE) continue;
                if (GLFW.glfwGetKey(win, k) == GLFW.GLFW_PRESS) {
                    c.radialKey = (k == GLFW.GLFW_KEY_BACKSPACE) ? -1 : k;
                    capturingRadial = false;
                    clearAndInit();
                    break;
                }
            }
        }
        if (capturing >= 0 && capturing < c.binds.size() && client != null) {
            long win = client.getWindow().getHandle();
            for (int k = 32; k <= 348; k++) {
                if (k == GLFW.GLFW_KEY_ESCAPE) continue;
                if (GLFW.glfwGetKey(win, k) == GLFW.GLFW_PRESS) {
                    c.binds.get(capturing).key = (k == GLFW.GLFW_KEY_BACKSPACE) ? -1 : k;
                    capturing = -1;
                    clearAndInit();
                    break;
                }
            }
        }
    }

    @Override
    public void close() {
        TacticalConfig.save();
        client.setScreen(parent);
    }

    private static class ConfigSlider extends SliderWidget {
        private final String label;
        private final double min, max;
        private final DoubleConsumer setter;

        ConfigSlider(int x, int y, int w, int h, String label, double min, double max, double value, DoubleConsumer setter) {
            super(x, y, w, h, Text.empty(), (value - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        private double real() { return min + (max - min) * value; }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + String.format("%.2f", real())));
        }

        @Override
        protected void applyValue() { setter.accept(real()); }
    }
}
