package com.tacticalpvphud.client.gui;

import com.tacticalpvphud.config.TacticalConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.Arrays;

/** Попиксельный редактор прицела: сетка 11x11, клик по клетке включает/выключает пиксель. */
public class PixelEditorScreen extends Screen {

    private static final int N = TacticalConfig.PIX;
    private static final int CELL = 14;
    private final Screen parent;
    private int gx, gy;

    public PixelEditorScreen(Screen parent) {
        super(Text.literal("Рисование прицела"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        TacticalConfig c = TacticalConfig.get();
        gx = width / 2 - (N * CELL) / 2 - 50;
        gy = 34;

        for (int r = 0; r < N; r++) {
            for (int col = 0; col < N; col++) {
                final int idx = r * N + col;
                addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                    c.pixels[idx] = !c.pixels[idx];
                    c.crosshairPixelMode = true;
                }).dimensions(gx + col * CELL, gy + r * CELL, CELL, CELL).build());
            }
        }

        int sx = gx + N * CELL + 10;
        int sy = gy;
        addDrawableChild(ButtonWidget.builder(Text.literal("Очистить"), b -> {
            Arrays.fill(c.pixels, false);
        }).dimensions(sx, sy, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Крест"), b -> {
            c.pixels = TacticalConfig.defaultPixels();
            c.crosshairPixelMode = true;
        }).dimensions(sx, sy + 24, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Точка"), b -> {
            Arrays.fill(c.pixels, false);
            c.pixels[(N / 2) * N + N / 2] = true;
            c.crosshairPixelMode = true;
        }).dimensions(sx, sy + 48, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), b -> close())
                .dimensions(sx, sy + 96, 100, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFF);
        TacticalConfig c = TacticalConfig.get();
        for (int i = 0; i < N * N; i++) {
            if (!c.pixels[i]) continue;
            int x = gx + (i % N) * CELL, y = gy + (i / N) * CELL;
            ctx.fill(x + 2, y + 2, x + CELL - 2, y + CELL - 2, 0xFFFFFFFF);
        }
    }

    @Override
    public void close() {
        TacticalConfig.save();
        client.setScreen(parent);
    }
}
