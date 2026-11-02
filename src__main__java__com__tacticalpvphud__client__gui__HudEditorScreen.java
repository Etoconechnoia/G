package com.tacticalpvphud.client.gui;

import com.tacticalpvphud.config.TacticalConfig;
import com.tacticalpvphud.hud.HudLayout;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Редактор позиций: каждый элемент HUD — рамка. Зажми палец/мышь на рамке и перетащи.
 * Перетаскивание читается напрямую из GLFW (состояние левой кнопки мыши).
 */
public class HudEditorScreen extends Screen {

    private final Screen parent;
    private int dragging = -1;
    private int grabDX, grabDY;
    private boolean wasDown = false;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("Расстановка HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Сбросить"), b -> HudLayout.reset(TacticalConfig.get()))
                .dimensions(width / 2 - 104, height - 28, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), b -> close())
                .dimensions(width / 2 + 4, height - 28, 100, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TacticalConfig c = TacticalConfig.get();
        ctx.drawCenteredTextWithShadow(textRenderer, "Перетаскивай рамки. Готово — сохранить.", width / 2, 8, 0xFFFFFF);

        // --- перетаскивание ---
        boolean down = client != null
                && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && !wasDown && mouseY < height - 34) {
            for (int i = HudLayout.COUNT - 1; i >= 0; i--) {
                int px = (int) (c.posX[i] * width), py = (int) (c.posY[i] * height);
                int l = HudLayout.left(i, px), t = HudLayout.top(i, py);
                if (mouseX >= l && mouseX <= l + HudLayout.W[i] && mouseY >= t && mouseY <= t + HudLayout.H[i]) {
                    dragging = i;
                    grabDX = mouseX - px;
                    grabDY = mouseY - py;
                    break;
                }
            }
        }
        if (down && dragging >= 0) {
            c.posX[dragging] = Math.max(0f, Math.min(1f, (mouseX - grabDX) / (float) width));
            c.posY[dragging] = Math.max(0f, Math.min(1f, (mouseY - grabDY) / (float) height));
        }
        if (!down) dragging = -1;
        wasDown = down;

        // --- рамки ---
        for (int i = 0; i < HudLayout.COUNT; i++) {
            int px = (int) (c.posX[i] * width), py = (int) (c.posY[i] * height);
            int l = HudLayout.left(i, px), t = HudLayout.top(i, py);
            int r = l + HudLayout.W[i], b = t + HudLayout.H[i];
            int col = (i == dragging) ? 0xFFFFFF55 : 0xFF55FFFF;
            ctx.fill(l, t, r, t + 1, col);
            ctx.fill(l, b - 1, r, b, col);
            ctx.fill(l, t, l + 1, b, col);
            ctx.fill(r - 1, t, r, b, col);
            ctx.fill(l + 1, t + 1, r - 1, b - 1, i == dragging ? 0x40FFFF55 : 0x2055FFFF);
            ctx.drawTextWithShadow(textRenderer, HudLayout.NAMES[i], l + 2, t + 2, 0xFFFFFFFF);
        }
    }

    @Override
    public void close() {
        TacticalConfig.save();
        client.setScreen(parent);
    }
}
