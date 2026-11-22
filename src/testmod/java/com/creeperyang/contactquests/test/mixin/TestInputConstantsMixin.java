package com.creeperyang.contactquests.test.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents automated client tests from grabbing/warping the OS cursor or toggling raw mouse input. */
@Mixin(value = InputConstants.class, remap = false)
public abstract class TestInputConstantsMixin {
    @Inject(method = "grabOrReleaseMouse", at = @At("HEAD"), cancellable = true, remap = false)
    private static void contactQuestsTest$skipGrabOrReleaseMouse(
            long window,
            int cursorMode,
            double xpos,
            double ypos,
            CallbackInfo ci
    ) {
        if (Boolean.getBoolean("contactquests.test.client")) {
            ci.cancel();
        }
    }

    @Inject(method = "updateRawMouseInput", at = @At("HEAD"), cancellable = true, remap = false)
    private static void contactQuestsTest$skipUpdateRawMouseInput(
            long window,
            boolean enableRawMouseMotion,
            CallbackInfo ci
    ) {
        if (Boolean.getBoolean("contactquests.test.client")) {
            ci.cancel();
        }
    }
}
