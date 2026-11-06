package com.tacticalpvphud.mixin;

import com.tacticalpvphud.hud.FreeLook;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** FreeLook: подменяет углы камеры, пока режим активен. */
@Mixin(Camera.class)
public class CameraRotationMixin {
    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float tacticalpvphud$yaw(float yaw) {
        return FreeLook.yaw(yaw);
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private float tacticalpvphud$pitch(float pitch) {
        return FreeLook.pitch(pitch);
    }
}
