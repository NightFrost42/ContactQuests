package com.creeperyang.contactquests.test.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips registering physical GLFW mouse callbacks during automated client tests. */
@Mixin(value = MouseHandler.class, remap = false)
public abstract class TestMouseHandlerMixin {
    @Inject(method = "setup", at = @At("HEAD"), cancellable = true, remap = false)
    private void contactQuestsTest$skipPhysicalMouseSetup(long window, CallbackInfo ci) {
        if (Boolean.getBoolean("contactquests.test.client")) {
            ci.cancel();
        }
    }
}
