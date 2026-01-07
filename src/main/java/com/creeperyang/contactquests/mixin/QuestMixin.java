package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.ContactQuests;
import com.creeperyang.contactquests.config.TagConfig;
import com.creeperyang.contactquests.utils.DependencyMode;
import com.creeperyang.contactquests.utils.IQuestExtension;
import com.creeperyang.contactquests.utils.ITeamDataExtension;
import com.creeperyang.contactquests.utils.MutexMode;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.*;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.util.ConfigQuestObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

@Mixin(Quest.class)
public abstract class QuestMixin extends QuestObject implements IQuestExtension {

    @Unique
    private final List<String> contactQuests$mutexGuids = new ArrayList<>();
    @Shadow(remap = false)
    @Final
    private List<Task> tasks;

    @Unique
    private final List<String> contactQuests$requiredTags = new ArrayList<>();
    @Unique
    private DependencyMode contactQuests$tagMode = DependencyMode.ALL;
    @Unique
    private boolean contactQuests$hideWithoutTags = false;
    @Shadow(remap = false)
    private Chapter chapter;
    @Unique
    private MutexMode contactQuests$mutexMode = MutexMode.ANY;
    @Unique
    private int contactQuests$mutexNum = 1;
    @Unique
    private boolean contactQuests$hideIfLocked = false;

    protected QuestMixin(long id) {
        super(id);
    }

    @Override
    @Unique
    public List<String> contactQuests$getRequiredTags() {
        return contactQuests$requiredTags;
    }

    @Override
    public List<String> contactQuests$getMutexTasks() {
        List<String> uiList = new ArrayList<>();
        for (String id : contactQuests$mutexGuids) {
            try {
                long longId = getQuestFile().getID(id);
                QuestObjectBase obj = getQuestFile().get(longId);
                if (obj instanceof QuestObject qo) {
                    uiList.add(qo.getTitle().getString());
                }
            } catch (Exception ignored) {
            }
        }
        return uiList;
    }

    @Override
    public boolean contactQuests$areTagsMet(TeamData data) {
        return contactQuests$internal$checkTagsMet(data);
    }

    @Override
    public boolean contactQuests$isLockedByMutex(TeamData data) {
        return contactQuests$internal$checkMutexLocked(data);
    }

    @Unique
    private boolean contactQuests$internal$checkTagsMet(TeamData data) {
        if (contactQuests$requiredTags.isEmpty()) {
            return false;
        }

        if (!(data instanceof ITeamDataExtension ext)) {
            return false;
        }

        boolean met;
        if (contactQuests$tagMode == DependencyMode.ANY) {
            met = contactQuests$requiredTags.stream().anyMatch(ext::contactQuests$hasTag);
        } else {
            met = !contactQuests$requiredTags.stream().allMatch(ext::contactQuests$hasTag);
        }

        return met;
    }

    @Unique
    private boolean contactQuests$internal$checkMutexLocked(TeamData data) {
        if (contactQuests$mutexGuids.isEmpty()) return false;

        long completedCount = contactQuests$mutexGuids.stream()
                .map(id -> {
                    try {
                        long qId = getQuestFile().getID(id);
                        return getQuestFile().get(qId);
                    } catch (Exception e) {
                        ContactQuests.debug("Error parsing Mutex ID: " + id);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(data::isCompleted)
                .count();

        ContactQuests.debug(String.valueOf(completedCount));

        return switch (contactQuests$mutexMode) {
            case ALL -> completedCount >= contactQuests$mutexGuids.size();
            case ANY -> completedCount > 0;
            case NUMBER -> completedCount >= contactQuests$mutexNum;
        };
    }

    @OnlyIn(Dist.CLIENT)
    @Inject(method = "fillConfigGroup", at = @At("TAIL"), remap = false)
    private void injectConfig(ConfigGroup config, CallbackInfo ci) {
        ConfigGroup group = config.getOrCreateSubgroup("contact_quests_tags");
        group.setNameKey("contactquests.config.group");

        TagConfig tagConfig = new TagConfig(getQuestFile(), contactQuests$requiredTags);

        group.addList("required_tags", contactQuests$requiredTags, tagConfig, v -> {
            contactQuests$requiredTags.clear();
            contactQuests$requiredTags.addAll(v);
        }, "").setNameKey("contactquests.config.required_tags");

        group.addEnum("mode", contactQuests$tagMode, v -> contactQuests$tagMode = v, DependencyMode.NAME_MAP)
                .setNameKey("contactquests.config.tag_mode");

        group.addBool("hide_without_tags", contactQuests$hideWithoutTags, v -> contactQuests$hideWithoutTags = v, false)
                .setNameKey("contactquests.config.hide_without_tags");

        ConfigGroup mutexGroup = config.getOrCreateSubgroup("contact_quests_mutex");
        mutexGroup.setNameKey("contactquests.config.mutex_group");

        Predicate<QuestObjectBase> mutexFilter = object -> object instanceof QuestObject && object != this && !tasks.contains(object) && object != chapter.file && object != chapter;

        List<QuestObject> uiList = new ArrayList<>();
        for (String id : contactQuests$mutexGuids) {
            try {
                long longId = getQuestFile().getID(id);
                QuestObjectBase obj = getQuestFile().get(longId);
                if (obj instanceof QuestObject qo) {
                    uiList.add(qo);
                }
            } catch (Exception ignored) {
            }
        }

        mutexGroup.addList("mutex_refs", uiList, new ConfigQuestObject<>(mutexFilter), v -> {
            contactQuests$mutexGuids.clear();
            for (QuestObject obj : v) {
                if (obj != null) {
                    contactQuests$mutexGuids.add(obj.getCodeString());
                }
            }
        }, null).setNameKey("contactquests.config.mutex_refs");

        mutexGroup.addEnum("mutex_mode", contactQuests$mutexMode, v -> contactQuests$mutexMode = v, MutexMode.NAME_MAP)
                .setNameKey("contactquests.config.mutex_mode");

        mutexGroup.addInt("mutex_count", contactQuests$mutexNum, v -> contactQuests$mutexNum = v, 1, 1, Integer.MAX_VALUE)
                .setNameKey("contactquests.config.mutex_count");

        mutexGroup.addBool("hide_if_locked", contactQuests$hideIfLocked, v -> contactQuests$hideIfLocked = v, false)
                .setNameKey("contactquests.config.hide_if_locked");
    }

    @Inject(method = "isVisible", at = @At("RETURN"), cancellable = true, remap = false)
    private void injectIsVisible(TeamData data, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.FALSE.equals(cir.getReturnValue())) return;

        if (contactQuests$hideIfLocked && contactQuests$internal$checkMutexLocked(data)) {
            cir.setReturnValue(false);
            return;
        }

        if (contactQuests$hideWithoutTags && contactQuests$internal$checkTagsMet(data)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "areDependenciesComplete", at = @At("RETURN"), cancellable = true, remap = false)
    private void injectDependenciesCheck(TeamData data, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.FALSE.equals(cir.getReturnValue())) return;

        if (contactQuests$internal$checkMutexLocked(data)) {
            cir.setReturnValue(false);
            return;
        }

        if (contactQuests$internal$checkTagsMet(data)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "writeData", at = @At("TAIL"), remap = false)
    private void writeTagsNBT(CompoundTag nbt, CallbackInfo ci) {
        if (!contactQuests$requiredTags.isEmpty()) {
            ListTag list = new ListTag();
            contactQuests$requiredTags.forEach(t -> list.add(StringTag.valueOf(t)));
            nbt.put("cq_req_tags", list);
            nbt.putString("cq_tag_mode", contactQuests$tagMode.name());
        }
        if (contactQuests$hideWithoutTags) {
            nbt.putBoolean("cq_hide_no_tags", true);
        }

        if (!contactQuests$mutexGuids.isEmpty()) {
            ListTag list = new ListTag();
            contactQuests$mutexGuids.forEach(id -> list.add(StringTag.valueOf(id)));
            nbt.put("cq_mutex_ids", list);
            nbt.putString("cq_mutex_mode", contactQuests$mutexMode.name());
            nbt.putInt("cq_mutex_count", contactQuests$mutexNum);
        }
        if (contactQuests$hideIfLocked) nbt.putBoolean("cq_hide_locked", true);
    }

    @Inject(method = "readData", at = @At("TAIL"), remap = false)
    private void readTagsNBT(CompoundTag nbt, CallbackInfo ci) {
        contactQuests$requiredTags.clear();
        if (nbt.contains("cq_req_tags")) {
            ListTag list = nbt.getList("cq_req_tags", Tag.TAG_STRING);
            list.forEach(t -> contactQuests$requiredTags.add(t.getAsString()));
            if (nbt.contains("cq_tag_mode")) {
                try {
                    contactQuests$tagMode = DependencyMode.valueOf(nbt.getString("cq_tag_mode"));
                } catch (Exception e) {
                    contactQuests$tagMode = DependencyMode.ALL;
                }
            }
        }
        contactQuests$hideWithoutTags = nbt.getBoolean("cq_hide_no_tags");


        contactQuests$mutexGuids.clear();
        if (nbt.contains("cq_mutex_ids")) {
            ListTag list = nbt.getList("cq_mutex_ids", Tag.TAG_STRING);
            list.forEach(t -> contactQuests$mutexGuids.add(t.getAsString()));
            if (nbt.contains("cq_mutex_mode")) {
                try {
                    contactQuests$mutexMode = MutexMode.valueOf(nbt.getString("cq_mutex_mode"));
                } catch (Exception e) {
                    contactQuests$mutexMode = MutexMode.ANY;
                }
            }
            if (nbt.contains("cq_mutex_count")) contactQuests$mutexNum = nbt.getInt("cq_mutex_count");
        }
        contactQuests$hideIfLocked = nbt.getBoolean("cq_hide_locked");
    }

    @Inject(method = "writeNetData", at = @At("TAIL"), remap = false)
    private void writeTagsNet(FriendlyByteBuf buffer, CallbackInfo ci) {
        buffer.writeVarInt(contactQuests$requiredTags.size());
        contactQuests$requiredTags.forEach(buffer::writeUtf);
        buffer.writeEnum(contactQuests$tagMode);
        buffer.writeBoolean(contactQuests$hideWithoutTags);

        buffer.writeVarInt(contactQuests$mutexGuids.size());
        contactQuests$mutexGuids.forEach(buffer::writeUtf);
        buffer.writeEnum(contactQuests$mutexMode);
        buffer.writeVarInt(contactQuests$mutexNum);
        buffer.writeBoolean(contactQuests$hideIfLocked);
    }

    @Inject(method = "readNetData", at = @At("TAIL"), remap = false)
    private void readTagsNet(FriendlyByteBuf buffer, CallbackInfo ci) {
        int size = buffer.readVarInt();
        contactQuests$requiredTags.clear();
        for (int i = 0; i < size; i++) contactQuests$requiredTags.add(buffer.readUtf());
        contactQuests$tagMode = buffer.readEnum(DependencyMode.class);
        contactQuests$hideWithoutTags = buffer.readBoolean();

        int mutexSize = buffer.readVarInt();
        contactQuests$mutexGuids.clear();
        for (int i = 0; i < mutexSize; i++) contactQuests$mutexGuids.add(buffer.readUtf());
        contactQuests$mutexMode = buffer.readEnum(MutexMode.class);
        contactQuests$mutexNum = buffer.readVarInt();
        contactQuests$hideIfLocked = buffer.readBoolean();
    }
}