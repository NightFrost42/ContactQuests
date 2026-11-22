package com.creeperyang.contactquests.test.mixin;

import com.flechazo.contact.forge.ContactForge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

/**
 * Contact 1.2.3 Forge passes {@code () -> ContactClient::onInitializeClient} to
 * {@code DistExecutor.safeRunWhenOn}. In userdev ({@code FMLEnvironment.production == false}),
 * {@code DistExecutor.validateSafeReferent} evaluates the outer supplier even on DEDICATED_SERVER
 * and fails with {@code BootstrapMethodError} instead of {@code Exception}. Guard the dev-only
 * client bootstrap here in {@code contactquests_test} without modifying production code or the jar.
 */
@Mixin(value = ContactForge.class, remap = false)
public abstract class ContactForgeDevServerMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/DistExecutor;safeRunWhenOn(Lnet/minecraftforge/api/distmarker/Dist;Ljava/util/function/Supplier;)V"
            ),
            remap = false
    )
    private void contactQuestsTest$guardClientOnlyInit(
            Dist dist,
            Supplier<DistExecutor.SafeRunnable> toRun
    ) {
        if (FMLEnvironment.dist == dist) {
            DistExecutor.safeRunWhenOn(dist, toRun);
        }
    }
}
