package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.quest.reward.*;
import com.creeperyang.contactquests.quest.task.*;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;

import java.util.List;

/** Expected persisted values are independent of the editor automation. */
public final class ContactQuestsEditorAssertions {
    public static final String NPC = "CQEditedTaskNpc";
    public static final String SENDER = "CQEditedRewardNpc";
    public static final String STYLE = "contact:creeper";
    public static final String CARD_TEXT = "编辑器明信片 <player_name>\\n第二行";
    public static final String BLESSING = "编辑器红包 <player_name>";
    public static final String TAG = "CQEditorRewardTag";
    public static final long PARCEL_COUNT = 3_000_000_005L;

    private ContactQuestsEditorAssertions() {}

    public static boolean taskFields(Quest quest) {
        if (quest == null || quest.getTasks().size() != 6) return false;
        var parcel = quest.getTasks().stream().filter(t -> t instanceof ParcelTask).map(t -> (ParcelTask) t).findFirst().orElseThrow();
        var red = quest.getTasks().stream().filter(t -> t instanceof RedPacketTask).map(t -> (RedPacketTask) t).findFirst().orElseThrow();
        var card = quest.getTasks().stream().filter(t -> t instanceof PostcardTask).map(t -> (PostcardTask) t).findFirst().orElseThrow();
        return parcel.getCount() == PARCEL_COUNT && parcel.getItemStack().is(Items.IRON_INGOT)
                && parcel.getMatchNBT() == dev.ftb.mods.ftblibrary.config.Tristate.TRUE && !parcel.getWeakNBTmatch()
                && parcel.getTargetAddressee().equals(NPC)
                && red.getCount() == 7 && red.getItemStack().is(Items.GOLD_INGOT)
                && red.getMatchNBT() == dev.ftb.mods.ftblibrary.config.Tristate.TRUE && red.getWeakNBTmatch()
                && red.getBlessing().equals(BLESSING) && red.getTargetAddressee().equals(NPC)
                && card.getCount() == 3 && card.getPostcardStyle().equals(STYLE)
                && card.getPostcardText().equals(CARD_TEXT) && card.getTargetAddressee().equals(NPC);
    }

    public static boolean rewardFields(Quest quest, long editedTableId) {
        if (quest == null || quest.getRewards().size() != 5) return false;
        var parcel = quest.getRewards().stream().filter(r -> r instanceof ParcelReward).map(r -> (ParcelReward) r).findFirst().orElseThrow();
        var red = quest.getRewards().stream().filter(r -> r instanceof RedPacketReward).map(r -> (RedPacketReward) r).findFirst().orElseThrow();
        var card = quest.getRewards().stream().filter(r -> r instanceof PostcardReward).map(r -> (PostcardReward) r).findFirst().orElseThrow();
        boolean common = quest.getRewards().stream().allMatch(r -> {
            var contact = (ParcelRewardBase) r;
            return contact.getTargetAddressee().equals(SENDER) && contact.getUnlockTags().equals(List.of(TAG))
                    && !contact.getEnableTeamMultiplier() && contact.getTeamRewardMultiplier() == 1.5f
                    && contact.isEnder() == (r instanceof PostcardReward);
        });
        boolean tables = quest.getRewards().stream().filter(r -> r instanceof ParcelRandomReward).allMatch(r -> {
            var table = ((ParcelRandomReward) r).getTable();
            return table != null && table.getId() == editedTableId && table.getWeightedRewards().size() == 1
                    && table.getWeightedRewards().get(0).getReward() instanceof dev.ftb.mods.ftbquests.quest.reward.ItemReward item
                    && item.getItem().is(Items.DIAMOND) && item.getCount() == 6;
        });
        return common && tables && parcel.getItem().is(Items.APPLE) && parcel.getCount() == 3 && parcel.getRandomBonus() == 0
                && red.getItem().is(Items.EMERALD) && red.getCount() == 2 && red.getRandomBonus() == 0 && red.getBlessing().equals(BLESSING)
                && card.getPostcardStyle().equals(STYLE) && card.getPostcardText().equals(CARD_TEXT);
    }

    public static boolean questRules(Quest quest, long mutexId, RegistryAccess provider) {
        if (quest == null) return false;
        var nbt = new CompoundTag(); quest.writeData(nbt);
        return nbt.getList("cq_req_tags", 8).size() == 1 && nbt.getList("cq_req_tags", 8).getString(0).equals("CQEditorRequiredTag")
                && nbt.getString("cq_tag_mode").equals("ANY") && nbt.getBoolean("cq_hide_no_tags")
                && nbt.getList("cq_mutex_ids", 8).size() == 1
                && Long.parseUnsignedLong(nbt.getList("cq_mutex_ids", 8).getString(0), 16) == mutexId
                && nbt.getString("cq_mutex_mode").equals("NUMBER") && nbt.getInt("cq_mutex_count") == 1
                && nbt.getBoolean("cq_hide_locked");
    }
}
