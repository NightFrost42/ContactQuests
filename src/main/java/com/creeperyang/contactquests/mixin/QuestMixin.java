package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.config.TagConfig;
import com.creeperyang.contactquests.utils.DependencyMode;
import com.creeperyang.contactquests.utils.IQuestExtension;
import com.creeperyang.contactquests.utils.ITeamDataExtension;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(Quest.class)
public abstract class QuestMixin extends QuestObject implements IQuestExtension {

    @Unique
    private final List<String> contactQuests$requiredTags = new ArrayList<>();
    @Unique
    private DependencyMode contactQuests$tagMode = DependencyMode.ALL;
    @Unique
    private boolean contactQuests$hideWithoutTags = false;

    protected QuestMixin(long id) {
        super(id);
    }

    @Override
    @Unique
    public List<String> contactQuests$getRequiredTags() {
        return contactQuests$requiredTags;
    }

    @Override
    @Unique
    public boolean contactQuests$areTagsMet(TeamData data) {
        return contactQuests$checkTags(data);
    }

    @Inject(method = "fillConfigGroup", at = @At("TAIL"))
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
    }

    @Inject(method = "isVisible", at = @At("RETURN"), cancellable = true)
    private void injectIsVisible(TeamData data, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && contactQuests$hideWithoutTags && contactQuests$checkTags(data)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "areDependenciesComplete", at = @At("RETURN"), cancellable = true)
    private void injectDependenciesCheck(TeamData data, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && contactQuests$checkTags(data)) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private boolean contactQuests$checkTags(TeamData data) {
        if (contactQuests$requiredTags.isEmpty()) return false;
        if (!(data instanceof ITeamDataExtension ext)) return false;

        if (contactQuests$tagMode == DependencyMode.ANY) {
            return contactQuests$requiredTags.stream()
                    .noneMatch(ext::contactQuests$hasTag);
        } else {
            return !contactQuests$requiredTags.stream()
                    .allMatch(ext::contactQuests$hasTag);
        }
    }

    @Inject(method = "writeData", at = @At("TAIL"))
    private void writeTagsNBT(CompoundTag nbt, HolderLookup.Provider provider, CallbackInfo ci) {
        if (!contactQuests$requiredTags.isEmpty()) {
            ListTag list = new ListTag();
            contactQuests$requiredTags.forEach(t -> list.add(StringTag.valueOf(t)));
            nbt.put("cq_req_tags", list);
            nbt.putString("cq_tag_mode", contactQuests$tagMode.name());
        }
        if (contactQuests$hideWithoutTags) {
            nbt.putBoolean("cq_hide_no_tags", true);
        }
    }

    @Inject(method = "readData", at = @At("TAIL"))
    private void readTagsNBT(CompoundTag nbt, HolderLookup.Provider provider, CallbackInfo ci) {
        contactQuests$requiredTags.clear();
        if (nbt.contains("cq_req_tags")) {
            ListTag list = nbt.getList("cq_req_tags", Tag.TAG_STRING);
            list.forEach(t -> contactQuests$requiredTags.add(t.getAsString()));
            if (nbt.contains("cq_tag_mode")) {
                try {
                    contactQuests$tagMode = DependencyMode.valueOf(nbt.getString("cq_tag_mode"));
                } catch (IllegalArgumentException e) {
                    contactQuests$tagMode = DependencyMode.ALL;
                }
            }
        }
        contactQuests$hideWithoutTags = nbt.getBoolean("cq_hide_no_tags");
    }

    @Inject(method = "writeNetData", at = @At("TAIL"))
    private void writeTagsNet(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
        buffer.writeVarInt(contactQuests$requiredTags.size());
        contactQuests$requiredTags.forEach(buffer::writeUtf);
        buffer.writeEnum(contactQuests$tagMode);
        buffer.writeBoolean(contactQuests$hideWithoutTags);
    }

    @Inject(method = "readNetData", at = @At("TAIL"))
    private void readTagsNet(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
        int size = buffer.readVarInt();
        contactQuests$requiredTags.clear();
        for (int i = 0; i < size; i++) contactQuests$requiredTags.add(buffer.readUtf());
        contactQuests$tagMode = buffer.readEnum(DependencyMode.class);
        contactQuests$hideWithoutTags = buffer.readBoolean();
    }
}