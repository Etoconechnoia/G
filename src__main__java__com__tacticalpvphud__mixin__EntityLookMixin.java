package com.tacticalpvphud.mixin;

import com.tacticalpvphud.hud.FreeLook;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FreeLook: пока клавиша зажата, движение мыши вращает камеру, а не игрока. */
@Mixin(Entity.class)
public class EntityLookMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
    private void tacticalpvphud$freelook(double dx, double dy, CallbackInfo ci) {
        if (FreeLook.active && FreeLook.hookOk && (Object) this instanceof ClientPlayerEntity) {
            FreeLook.camYaw += (float) dx * 0.15f;
            FreeLook.camPitch = Math.max(-90f, Math.min(90f, FreeLook.camPitch + (float) dy * 0.15f));
            ci.cancel();
        }
    }
}
