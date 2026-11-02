package com.tacticalpvphud.client.gui;

import com.tacticalpvphud.config.TacticalConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
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
        VISUAL("Визуал"), INDICATORS("Индикаторы"), PVP("PvP"), BINDS("Бинды"), MISC("Прочее");
        final String label;
        Category(String l) { label = l; }
    }

    private record IconDraw(ItemStack stack, int x, int y) {}

    private final Screen parent;
    private Category current = Category.VISUAL;
    private int page = 0;
    private boolean picking = false;
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
        return r;
    }

    private List<IntConsumer> miscRows(TacticalConfig c) {
        List<IntConsumer> r = new ArrayList<>();
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
        r.add(toggle("Метки на экране", () -> c.waypointsHud, v -> c.waypointsHud = v));
        r.add(button("+ Метка на моей позиции", () -> {
            if (client.player == null) return;
            c.waypoints.add(new TacticalConfig.Waypoint("Метка " + (c.waypoints.size() + 1),
                    (int) client.player.getX(), (int) client.player.getY(), (int) client.player.getZ()));
            clearAndInit();
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
            r.add(button("Отмена", () -> { picking = false; clearAndInit(); }));
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
                        addDrawableChild(ButtonWidget.builder(Text.empty(), btn -> {
                            c.binds.add(new TacticalConfig.Bind(Registries.ITEM.getId(stack.getItem()).toString(), -1));
                            capturing = c.binds.size() - 1;
                            picking = false;
                            clearAndInit();
                        }).dimensions(x, y, 20, 20).build());
                    }
                });
            }
            return r;
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
