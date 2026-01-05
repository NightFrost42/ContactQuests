package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.network.SyncTeamTagsMessage;
import com.creeperyang.contactquests.utils.IQuestExtension;
import com.creeperyang.contactquests.utils.ITeamDataExtension;
import dev.ftb.mods.ftblibrary.snbt.SNBTCompoundTag;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(TeamData.class)
public class TeamDataMixin implements ITeamDataExtension {

    @Unique
    private final Set<String> contactQuests$unlockedTags = new HashSet<>();

    @Override
    @Unique
    public boolean contactQuests$unlockTag(String tag) {
        if (contactQuests$unlockedTags.add(tag)) {
            return contactQuests$operationTag();
        }
        return false;
    }

    @Unique
    private boolean contactQuests$operationTag() {
        TeamData self = (TeamData) (Object) this;
        self.markDirty();
        self.clearCachedProgress();

        if (self.getFile().isServerSide()) {
            SyncTeamTagsMessage msg = new SyncTeamTagsMessage(self.getTeamId(), contactQuests$unlockedTags);

            for (ServerPlayer player : self.getOnlineMembers()) {
                PacketDistributor.sendToPlayer(player, msg);
            }
        }

        return true;
    }

    @Override
    @Unique
    public boolean contactQuests$removeTag(String tag) {
        if (contactQuests$unlockedTags.remove(tag)) {
            return contactQuests$operationTag();
        }
        return false;
    }

    @Override
    @Unique
    public boolean contactQuests$hasTag(String tag) {
        return contactQuests$unlockedTags.contains(tag);
    }

    @Override
    @Unique
    public java.util.Collection<String> contactQuests$getTags() {
        return java.util.Collections.unmodifiableSet(contactQuests$unlockedTags);
    }

    @Inject(method = "areDependenciesComplete", at = @At("RETURN"), cancellable = true)
    private void injectAreDependenciesComplete(Quest quest, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            Object questObj = quest;
            if (questObj instanceof IQuestExtension) {
                if (((IQuestExtension) questObj).contactQuests$isLockedByMutex((TeamData) (Object) this)) {
                    cir.setReturnValue(false);
                }

                if (((IQuestExtension) questObj).contactQuests$areTagsMet((TeamData) (Object) this)) {
                    cir.setReturnValue(false);
                }
            }
        }
    }

    @Inject(method = "getCannotStartReason", at = @At("HEAD"), cancellable = true)
    private void injectGetCannotStartReason(Quest quest, CallbackInfoReturnable<Component> cir) {
        Object questObj = quest;
        if (questObj instanceof IQuestExtension ext) {
            if (ext.contactQuests$isLockedByMutex((TeamData) (Object) this)) {
                List<String> mutexTasks = ext.contactQuests$getMutexTasks();
                String tagsStr = String.join(", ", mutexTasks);
                cir.setReturnValue(Component.translatable("contactquests.quest.locked.mutex_tasks", tagsStr)
                        .withStyle(ChatFormatting.RED));
            }

            if (ext.contactQuests$areTagsMet((TeamData) (Object) this)) {
                List<String> missingTags = ext.contactQuests$getRequiredTags();
                String tagsStr = String.join(", ", missingTags);
                cir.setReturnValue(Component.translatable("contactquests.quest.locked.missing_tags", tagsStr)
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    @Inject(method = "serializeNBT", at = @At("RETURN"))
    private void injectSerializeNBT(CallbackInfoReturnable<SNBTCompoundTag> cir) {
        if (!contactQuests$unlockedTags.isEmpty()) {
            ListTag list = new ListTag();
            contactQuests$unlockedTags.forEach(t -> list.add(StringTag.valueOf(t)));
            cir.getReturnValue().put("ContactQuestsUnlockedTags", list);
        }
    }

    @Inject(method = "deserializeNBT", at = @At("TAIL"))
    private void injectDeserializeNBT(SNBTCompoundTag nbt, CallbackInfo ci) {
        contactQuests$unlockedTags.clear();
        if (nbt.contains("ContactQuestsUnlockedTags")) {
            ListTag list = nbt.getList("ContactQuestsUnlockedTags", Tag.TAG_STRING);
            list.forEach(t -> contactQuests$unlockedTags.add(t.getAsString()));
        }
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void injectWrite(FriendlyByteBuf buffer, CallbackInfo ci) {
        buffer.writeVarInt(contactQuests$unlockedTags.size());
        contactQuests$unlockedTags.forEach(buffer::writeUtf);
    }

    @Inject(method = "readNetData", at = @At("TAIL"))
    private void injectReadNetData(FriendlyByteBuf buffer, CallbackInfo ci) {
        int size = buffer.readVarInt();
        contactQuests$unlockedTags.clear();
        for (int i = 0; i < size; i++) {
            contactQuests$unlockedTags.add(buffer.readUtf());
        }
    }
}