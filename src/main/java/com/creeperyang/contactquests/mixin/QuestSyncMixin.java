package com.creeperyang.contactquests.mixin;

import dev.ftb.mods.ftbquests.quest.BaseQuestFile;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.locks.ReentrantLock;

/** Serializes full graph reads and writes across client and integrated server. */
@Mixin(BaseQuestFile.class)
public abstract class QuestSyncMixin {
    @Unique private static final ReentrantLock contactQuests$syncLock = new ReentrantLock();

    @Inject(method = {"writeNetDataFull", "readNetDataFull"}, at = @At("HEAD"), remap = false)
    private void contactQuests$lock(FriendlyByteBuf buffer, CallbackInfo ci) {
        contactQuests$syncLock.lock();
    }

    @Inject(method = {"writeNetDataFull", "readNetDataFull"}, at = @At("RETURN"), remap = false)
    private void contactQuests$unlock(FriendlyByteBuf buffer, CallbackInfo ci) {
        if (contactQuests$syncLock.isHeldByCurrentThread()) contactQuests$syncLock.unlock();
    }
}
