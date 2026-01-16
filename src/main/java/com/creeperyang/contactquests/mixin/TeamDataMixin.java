package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.network.SyncTeamExtensionMessage;
import com.creeperyang.contactquests.network.SyncTeamTagsMessage;
import com.creeperyang.contactquests.utils.IQuestExtension;
import com.creeperyang.contactquests.utils.ITeamDataExtension;
import dev.ftb.mods.ftblibrary.snbt.SNBTCompoundTag;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.*;
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

import java.util.*;

@Mixin(TeamData.class)
public class TeamDataMixin implements ITeamDataExtension {

    @Unique
    private final Set<String> contactQuests$unlockedTags = new HashSet<>();

    @Unique
    private final Set<Long> contactQuests$forcedQuests = new HashSet<>();

    @Unique
    private final Set<Long> contactQuests$blockedQuests = new HashSet<>();

    @Unique
    private final Map<Long, String> contactQuests$postcardCache = new HashMap<>();

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
    public boolean contactQuests$forceQuest(long questId) {
        if (contactQuests$forcedQuests.add(questId)) {
            contactQuests$blockedQuests.remove(questId);
            return contactQuests$operationSync();
        }
        return false;
    }

    @Override
    @Unique
    public boolean contactQuests$unforceQuest(long questId) {
        if (contactQuests$forcedQuests.remove(questId)) {
            return contactQuests$operationSync();
        }
        return false;
    }

    @Override
    @Unique
    public boolean contactQuests$isQuestForced(long questId) {
        return contactQuests$forcedQuests.contains(questId);
    }

    @Override
    @Unique
    public boolean contactQuests$blockQuest(long questId) {
        if (contactQuests$blockedQuests.add(questId)) {
            contactQuests$forcedQuests.remove(questId);
            return contactQuests$operationSync();
        }
        return false;
    }

    @Override
    @Unique
    public boolean contactQuests$unblockQuest(long questId) {
        if (contactQuests$blockedQuests.remove(questId)) {
            return contactQuests$operationSync();
        }
        return false;
    }

    @Override
    @Unique
    public boolean contactQuests$isQuestBlocked(long questId) {
        return contactQuests$blockedQuests.contains(questId);
    }

    @Override
    @Unique
    public Collection<Long> contactQuests$getForcedQuests() {
        return Collections.unmodifiableSet(contactQuests$forcedQuests);
    }

    @Override
    @Unique
    public Collection<Long> contactQuests$getBlockedQuests() {
        return Collections.unmodifiableSet(contactQuests$blockedQuests);
    }

    @Override
    @Unique
    public void contactQuests$setForcedQuests(Collection<Long> ids) {
        contactQuests$forcedQuests.clear();
        contactQuests$forcedQuests.addAll(ids);
    }

    @Override
    @Unique
    public void contactQuests$setBlockedQuests(Collection<Long> ids) {
        contactQuests$blockedQuests.clear();
        contactQuests$blockedQuests.addAll(ids);
    }

    @Override
    @Unique
    public String contactQuests$getPostcardText(long taskId) {
        return contactQuests$postcardCache.get(taskId);
    }

    @Override
    @Unique
    public void contactQuests$setPostcardText(long taskId, String text) {
        if (!Objects.equals(contactQuests$postcardCache.get(taskId), text)) {
            contactQuests$postcardCache.put(taskId, text);
            contactQuests$operationSync();
        }
    }

    @Override
    @Unique
    public Map<Long, String> contactQuests$getAllPostcardTexts() {
        return Collections.unmodifiableMap(contactQuests$postcardCache);
    }

    @Override
    @Unique
    public void contactQuests$setAllPostcardTexts(Map<Long, String> texts) {
        contactQuests$postcardCache.clear();
        contactQuests$postcardCache.putAll(texts);
    }

    @Unique
    private boolean contactQuests$operationSync() {
        TeamData self = (TeamData) (Object) this;
        self.markDirty();
        self.clearCachedProgress();

        if (self.getFile().isServerSide()) {
            SyncTeamExtensionMessage msg = new SyncTeamExtensionMessage(
                    self.getTeamId(),
                    contactQuests$unlockedTags,
                    contactQuests$forcedQuests,
                    contactQuests$blockedQuests,
                    contactQuests$postcardCache
            );

            for (ServerPlayer player : self.getOnlineMembers()) {
                PacketDistributor.sendToPlayer(player, msg);
            }
        }
        return true;
    }

    @Override
    @Unique
    public java.util.Collection<String> contactQuests$getTags() {
        return java.util.Collections.unmodifiableSet(contactQuests$unlockedTags);
    }

    @Inject(method = "areDependenciesComplete", at = @At("RETURN"), cancellable = true, remap = false)
    private void injectAreDependenciesComplete(Quest quest, CallbackInfoReturnable<Boolean> cir) {
        long id = quest.id;
        if (contactQuests$isQuestForced(id)) {
            cir.setReturnValue(true);
            return;
        }
        if (contactQuests$isQuestBlocked(id)) {
            cir.setReturnValue(false);
            return;
        }

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

    @Inject(method = "getCannotStartReason", at = @At("HEAD"), cancellable = true, remap = false)
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

    @Inject(method = "serializeNBT", at = @At("RETURN"), remap = false)
    private void injectSerializeNBT(CallbackInfoReturnable<SNBTCompoundTag> cir) {
        SNBTCompoundTag tag = cir.getReturnValue();
        if (!contactQuests$unlockedTags.isEmpty()) {
            ListTag list = new ListTag();
            contactQuests$unlockedTags.forEach(t -> list.add(StringTag.valueOf(t)));
            tag.put("ContactQuestsUnlockedTags", list);
        }
        if (!contactQuests$forcedQuests.isEmpty()) {
            ListTag list = new ListTag();
            contactQuests$forcedQuests.forEach(id -> list.add(LongTag.valueOf(id)));
            tag.put("ContactQuestsForcedQuests", list);
        }
        if (!contactQuests$blockedQuests.isEmpty()) {
            ListTag list = new ListTag();
            contactQuests$blockedQuests.forEach(id -> list.add(LongTag.valueOf(id)));
            tag.put("ContactQuestsBlockedQuests", list);
        }
        if (!contactQuests$postcardCache.isEmpty()) {
            CompoundTag pcTag = new CompoundTag();
            contactQuests$postcardCache.forEach((k, v) -> pcTag.putString(String.valueOf(k), v));
            tag.put("ContactQuestsPostcardCache", pcTag);
        }
    }

    @Inject(method = "deserializeNBT", at = @At("TAIL"), remap = false)
    private void injectDeserializeNBT(SNBTCompoundTag nbt, CallbackInfo ci) {
        contactQuests$unlockedTags.clear();
        if (nbt.contains("ContactQuestsUnlockedTags")) {
            ListTag list = nbt.getList("ContactQuestsUnlockedTags", Tag.TAG_STRING);
            list.forEach(t -> contactQuests$unlockedTags.add(t.getAsString()));
        }

        contactQuests$forcedQuests.clear();
        if (nbt.contains("ContactQuestsForcedQuests")) {
            ListTag list = nbt.getList("ContactQuestsForcedQuests", Tag.TAG_LONG);
            list.forEach(t -> contactQuests$forcedQuests.add(((LongTag) t).getAsLong()));
        }

        contactQuests$blockedQuests.clear();
        if (nbt.contains("ContactQuestsBlockedQuests")) {
            ListTag list = nbt.getList("ContactQuestsBlockedQuests", Tag.TAG_LONG);
            list.forEach(t -> contactQuests$blockedQuests.add(((LongTag) t).getAsLong()));
        }

        contactQuests$postcardCache.clear();
        if (nbt.contains("ContactQuestsPostcardCache")) {
            CompoundTag pcTag = nbt.getCompound("ContactQuestsPostcardCache");
            for (String key : pcTag.getAllKeys()) {
                try {
                    long id = Long.parseLong(key);
                    contactQuests$postcardCache.put(id, pcTag.getString(key));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    @Inject(method = "writeNetData", at = @At("TAIL"), remap = false)
    private void injectWrite(FriendlyByteBuf buffer, CallbackInfo ci) {
        buffer.writeVarInt(contactQuests$unlockedTags.size());
        contactQuests$unlockedTags.forEach(buffer::writeUtf);

        buffer.writeVarInt(contactQuests$forcedQuests.size());
        contactQuests$forcedQuests.forEach(buffer::writeLong);

        buffer.writeVarInt(contactQuests$blockedQuests.size());
        contactQuests$blockedQuests.forEach(buffer::writeLong);

        buffer.writeVarInt(contactQuests$postcardCache.size());
        contactQuests$postcardCache.forEach((k, v) -> {
            buffer.writeLong(k);
            buffer.writeUtf(v);
        });
    }

    @Inject(method = "readNetData", at = @At("TAIL"), remap = false)
    private void injectReadNetData(FriendlyByteBuf buffer, CallbackInfo ci) {
        int size = buffer.readVarInt();
        contactQuests$unlockedTags.clear();
        for (int i = 0; i < size; i++) {
            contactQuests$unlockedTags.add(buffer.readUtf());
        }

        int forcedSize = buffer.readVarInt();
        contactQuests$forcedQuests.clear();
        for (int i = 0; i < forcedSize; i++) {
            contactQuests$forcedQuests.add(buffer.readLong());
        }

        int blockedSize = buffer.readVarInt();
        contactQuests$blockedQuests.clear();
        for (int i = 0; i < blockedSize; i++) {
            contactQuests$blockedQuests.add(buffer.readLong());
        }

        int pcSize = buffer.readVarInt();
        contactQuests$postcardCache.clear();
        for (int i = 0; i < pcSize; i++) {
            contactQuests$postcardCache.put(buffer.readLong(), buffer.readUtf());
        }
    }
}