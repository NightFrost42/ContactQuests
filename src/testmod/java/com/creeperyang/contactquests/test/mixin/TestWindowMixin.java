package com.creeperyang.contactquests.test.mixin;

import com.creeperyang.contactquests.test.ContactQuestsTestWindowHints;
import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Creates the test client window unfocused without referencing LWJGL types inside Mixin bytecode. */
@Mixin(value = Window.class, remap = false)
public abstract class TestWindowMixin {
    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J"
            ),
            remap = false
    )
    private void contactQuestsTest$createUnfocusedWindow(
            WindowEventHandler eventHandler,
            ScreenManager screenManager,
            DisplayData displayData,
            String fullscreenVideoModeString,
            String title,
            CallbackInfo ci
    ) {
        ContactQuestsTestWindowHints.applyUnfocusedHints();
    }
}
