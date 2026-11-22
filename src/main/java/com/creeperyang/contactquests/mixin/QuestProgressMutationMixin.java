package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.quest.QuestStateEvents;
import com.creeperyang.contactquests.bridge.IQuestExtension;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.util.ProgressChange;
import dev.ftb.mods.ftbquests.util.TextUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

@Mixin(QuestObjectBase.class)
public class QuestProgressMutationMixin {
    @Unique
    private static final ThreadLocal<Deque<Runnable>> contactQuests$mutations = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "forceProgressRaw", at = @At("HEAD"), remap = false)
    private void progressMutationHead(TeamData team, ProgressChange change, CallbackInfo ci) {
        UUID playerId = change.getPlayerId();
        ServerPlayer actor = team.getFile() instanceof ServerQuestFile file && playerId != null
                ? file.server.getPlayerList().getPlayer(playerId) : null;
        String reason = change.shouldReset() ? "native_reset" : "native_complete";
        // withIdentity restores the caller identity even if event callbacks fail.
        QuestStateEvents.withIdentity(playerId, actor, reason, () -> QuestStateEvents.begin(team, reason));
        contactQuests$mutations.get().push(() ->
                QuestStateEvents.withIdentity(playerId, actor, reason, () -> QuestStateEvents.end(team)));
    }

    @Inject(method = "forceProgressRaw", at = @At("RETURN"), remap = false)
    private void progressMutationReturn(TeamData team, ProgressChange change, CallbackInfo ci) {
        Deque<Runnable> stack = contactQuests$mutations.get();
        try {
            if (!stack.isEmpty()) stack.pop().run();
        } finally {
            if (stack.isEmpty()) contactQuests$mutations.remove();
        }
    }

    // These title methods are final on QuestObjectBase in 2001.4.22.
    @Inject(method = "getRawTitle", at = @At("HEAD"), cancellable = true, remap = false)
    private void contactQuests$rawTitle(CallbackInfoReturnable<String> cir) {
        if ((Object) this instanceof IQuestExtension ext) {
            String title = ext.contactQuests$getTitleOverride();
            if (title != null) cir.setReturnValue(title);
        }
    }

    @Inject(method = "getTitle", at = @At("HEAD"), cancellable = true, remap = false)
    private void contactQuests$title(CallbackInfoReturnable<Component> cir) {
        if ((Object) this instanceof IQuestExtension ext) {
            String title = ext.contactQuests$getTitleOverride();
            if (title != null) cir.setReturnValue(TextUtils.parseRawText(title));
        }
    }

    @Inject(method = "getMutableTitle", at = @At("HEAD"), cancellable = true, remap = false)
    private void contactQuests$mutableTitle(CallbackInfoReturnable<MutableComponent> cir) {
        if ((Object) this instanceof IQuestExtension ext) {
            String title = ext.contactQuests$getTitleOverride();
            if (title != null) cir.setReturnValue(TextUtils.parseRawText(title).copy());
        }
    }
}
