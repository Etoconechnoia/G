package com.tacticalpvphud.client.gui;

import com.tacticalpvphud.config.TacticalConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
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

/**
 * Меню настроек (клавиша M): слева категории, справа тумблеры, ползунки и бинды.
 */
public class TacticalSettingsScreen extends Screen {

    private enum Category {
        INTERFACE("Интерфейс"), PVP("PvP"), BINDS("Бинды");
        final String label;
        Category(String l) { label = l; }
    }

    /** Иконка предмета, которую рисуем поверх кнопок. */
    private record IconDraw(ItemStack stack, int x, int y) {}

    private final Screen parent;
    private Category current = Category.INTERFACE;
    private int rowY;

    private boolean picking = false;  // открыт выбор предмета из инвентаря
    private int capturing = -1;       // индекс бинда, для которого ждём нажатие клавиши
    private final List<IconDraw> icons = new ArrayList<>();

    public TacticalSettingsScreen(Screen parent) {
        super(Text.translatable("screen.tacticalpvphud.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        TacticalConfig c = TacticalConfig.get();
        icons.clear();

        int y = 40;
        for (Category cat : Category.values()) {
            addDrawableChild(ButtonWidget.builder(Text.literal((cat == current ? "> " : "") + cat.label), btn -> {
                current = cat;
                picking = false;
                capturing = -1;
                clearAndInit();
            }).dimensions(10, y, 100, 20).build());
            y += 24;
        }

        rowY = 40;
        switch (current) {
            case INTERFACE -> {
                addToggle("Азимут (градусы)", () -> c.azimuthEnabled, v -> c.azimuthEnabled = v);
                addToggle("Свой прицел", () -> c.customCrosshair, v -> c.customCrosshair = v);
                addToggle("Динамический прицел", () -> c.dynamicCrosshair, v -> c.dynamicCrosshair = v);
                addSlider("Толщина", 1, 5, () -> c.crosshairThickness, v -> c.crosshairThickness = (float) v);
                addSlider("Зазор", 0, 10, () -> c.crosshairGap, v -> c.crosshairGap = (float) v);
                addSlider("Цвет (оттенок)", 0, 1, () -> c.crosshairHue, v -> c.crosshairHue = (float) v);
                addToggle("HUD брони", () -> c.armorHudEnabled, v -> c.armorHudEnabled = v);
                addToggle("Алерт сломанной брони", () -> c.brokenArmorAlert, v -> c.brokenArmorAlert = v);
                addSlider("Прозрачность HUD", 0.1, 1, () -> c.hudOpacity, v -> c.hudOpacity = (float) v);
            }
            case PVP -> {
                addToggle("Счётчик CPS", () -> c.cpsEnabled, v -> c.cpsEnabled = v);
                addToggle("Режим боя (таймер)", () -> c.combatMode, v -> c.combatMode = v);
                addToggle("Рамка боя", () -> c.combatFrame, v -> c.combatFrame = v);
            }
            case BINDS -> initBinds(c);
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), b -> close())
                .dimensions(width / 2 - 50, height - 28, 100, 20).build());
    }

    /** Вкладка «Бинды»: любой предмет на любую клавишу. */
    private void initBinds(TacticalConfig c) {
        addToggle("Бинды предметов", () -> c.bindsEnabled, v -> c.bindsEnabled = v);

        for (int i = 0; i < c.binds.size(); i++) {
            final int idx = i;
            TacticalConfig.Bind b = c.binds.get(i);
            Item item = Registries.ITEM.get(Identifier.of(b.item));
            icons.add(new IconDraw(new ItemStack(item), 130, rowY + 2));

            String keyTxt = capturing == idx ? "нажми клавишу..." : keyName(b.key);
            addDrawableChild(ButtonWidget.builder(Text.literal(item.getName().getString() + ": " + keyTxt), btn -> {
                capturing = idx;
                picking = false;
                clearAndInit();
            }).dimensions(152, rowY, 172, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("X"), btn -> {
                c.binds.remove(idx);
                capturing = -1;
                clearAndInit();
            }).dimensions(328, rowY, 20, 20).build());
            rowY += 24;
        }

        addDrawableChild(ButtonWidget.builder(
                Text.literal(picking ? "Выбери предмет ниже" : "+ Добавить бинд"), btn -> {
                    picking = !picking;
                    capturing = -1;
                    clearAndInit();
                }).dimensions(130, rowY, 218, 20).build());
        rowY += 24;

        // Сетка инвентаря игрока: клик по предмету создаёт бинд
        if (picking && client != null && client.player != null) {
            for (int n = 0; n < 36; n++) {
                int slot = n < 27 ? 9 + n : n - 27; // сначала основной инвентарь, потом хотбар
                ItemStack stack = client.player.getInventory().getStack(slot);
                if (stack.isEmpty()) continue;
                int x = 130 + (n % 9) * 24;
                int y = rowY + (n / 9) * 22;
                icons.add(new IconDraw(stack, x + 2, y + 2));
                addDrawableChild(ButtonWidget.builder(Text.empty(), btn -> {
                    c.binds.add(new TacticalConfig.Bind(Registries.ITEM.getId(stack.getItem()).toString(), -1));
                    capturing = c.binds.size() - 1; // сразу ждём клавишу
                    picking = false;
                    clearAndInit();
                }).dimensions(x, y, 20, 20).build());
            }
        }
    }

    private static String keyName(int key) {
        if (key <= 0) return "не задана";
        return InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
    }

    private void addToggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
        addDrawableChild(ButtonWidget.builder(toggleText(label, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(toggleText(label, get.getAsBoolean()));
        }).dimensions(130, rowY, 218, 20).build());
        rowY += 24;
    }

    private static Text toggleText(String label, boolean on) {
        return Text.literal(label + ": " + (on ? "§aВКЛ" : "§cВЫКЛ"));
    }

    private void addSlider(String label, double min, double max, DoubleSupplier get, DoubleConsumer set) {
        addDrawableChild(new ConfigSlider(130, rowY, 218, 20, label, min, max, get.getAsDouble(), set));
        rowY += 24;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 15, 0xFFFFFF);
        for (IconDraw ic : icons) ctx.drawItem(ic.stack(), ic.x(), ic.y());

        // Ожидание клавиши для бинда: опрашиваем GLFW напрямую.
        // Backspace — очистить бинд. Esc закрывает меню как обычно.
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

    /** Ползунок со значением в диапазоне [min; max]. */
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
