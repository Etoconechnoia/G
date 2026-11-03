package com.tacticalpvphud.client.gui;

import com.tacticalpvphud.config.TacticalConfig;
import com.tacticalpvphud.hud.HudRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Круговое меню предметов (как колесо в Counter-Strike).
 * Зажми клавишу -> появляется круг -> наведи мышь (или тапни) на сегмент -> отпусти клавишу:
 * предмет берётся в руку (из хотбара ИЛИ из инвентаря).
 */
public class RadialScreen extends Screen {

    private static final int RADIUS = 64;
    private final int keyCode;
    private final List<String> ids = new ArrayList<>();
    private final List<ItemStack> stacks = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private int hovered = -1;
    private int lastMx, lastMy;

    public RadialScreen(int keyCode) {
        super(Text.literal("Круг"));
        this.keyCode = keyCode;
        for (String id : TacticalConfig.get().radialItems) {
            Item item = Registries.ITEM.get(Identifier.of(id));
            ids.add(id);
            stacks.add(new ItemStack(item));
            names.add(item.getName().getString());
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    private int hoveredAt(int mx, int my) {
        int n = ids.size();
        if (n == 0) return -1;
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        if (Math.sqrt(dx * dx + dy * dy) < 22) return -1; // мёртвая зона в центре
        double ang = Math.toDegrees(Math.atan2(dx, -dy)); // 0 = вверх, по часовой
        if (ang < 0) ang += 360;
        double seg = 360.0 / n;
        return (int) ((ang + seg / 2) / seg) % n;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        lastMx = mouseX;
        lastMy = mouseY;
        hovered = hoveredAt(mouseX, mouseY);
        int cx = width / 2, cy = height / 2;
        ctx.fill(0, 0, width, height, 0x66000000);

        // кольцо точек
        for (int k = 0; k < 72; k++) {
            double a = k * Math.PI * 2 / 72;
            int x = cx + (int) (Math.sin(a) * (RADIUS + 18));
            int y = cy - (int) (Math.cos(a) * (RADIUS + 18));
            ctx.fill(x, y, x + 2, y + 2, 0x88FFFFFF);
        }

        int n = ids.size();
        if (n == 0) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Круг пуст: M -> Круг -> добавь предметы", cx, cy, 0xFFFFFFFF);
            return;
        }
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            int x = cx + (int) (Math.sin(a) * RADIUS);
            int y = cy - (int) (Math.cos(a) * RADIUS);
            boolean hot = i == hovered;
            int r = hot ? 15 : 12;
            ctx.fill(x - r, y - r, x + r, y + r, hot ? 0xCC4488FF : 0x77000000);
            ctx.drawItem(stacks.get(i), x - 8, y - 8);
        }
        if (hovered >= 0) {
            ctx.drawCenteredTextWithShadow(textRenderer, names.get(hovered), cx, cy - 4, 0xFFFFFFFF);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer, "Выбери", cx, cy - 4, 0xFFAAAAAA);
        }
    }

    @Override
    public void tick() {
        if (client == null) return;
        long win = client.getWindow().getHandle();
        boolean keyDown = GLFW.glfwGetKey(win, keyCode) == GLFW.GLFW_PRESS;
        boolean tap = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        int sel = hoveredAt(lastMx, lastMy);
        if ((!keyDown || tap) && sel >= 0 && sel < ids.size()) {
            HudRenderer.giveItem(client, ids.get(sel));
            client.setScreen(null);
        } else if (!keyDown) {
            client.setScreen(null);
        }
    }
}
