package com.creeperyang.contactquests.test.mixin;

import com.creeperyang.contactquests.test.ContactQuestsTestWindowHints;
import com.mojang.blaze3d.platform.Window;
import net.minecraftforge.fml.loading.ImmediateWindowHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Creates the test client window unfocused without referencing LWJGL types inside Mixin bytecode. */
@Mixin(value = Window.class, remap = false)
public abstract class TestWindowMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J"
            ),
            remap = false
    )
    private long contactQuestsTest$createUnfocusedWindow(
            IntSupplier width,
            IntSupplier height,
            Supplier<String> title,
            LongSupplier monitor
    ) {
        ContactQuestsTestWindowHints.applyUnfocusedHints();
        return ImmediateWindowHandler.setupMinecraftWindow(width, height, title, monitor);
    }
}
