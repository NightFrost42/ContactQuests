package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.quest.QuestStateEvents;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.util.ProgressChange;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将原生完成/重置操作连带触发的子对象进度变更收敛为单次“变更前/变更后”状态迁移上下文。 */
@Mixin(QuestObjectBase.class)
public class QuestProgressMutationMixin {
    @WrapMethod(method = "forceProgressRaw", remap = false)
    private void progressMutation(TeamData team, ProgressChange change, Operation<Void> original) {
        var actor = team.getFile() instanceof ServerQuestFile file && change.getPlayerId() != null
                ? file.server.getPlayerList().getPlayer(change.getPlayerId()) : null;
        QuestStateEvents.withIdentity(change.getPlayerId(), actor, change.shouldReset() ? "native_reset" : "native_complete", () -> {
            QuestStateEvents.begin(team, change.shouldReset() ? "native_reset" : "native_complete");
            try { original.call(team, change); }
            finally { QuestStateEvents.end(team); }
        });
    }
}
