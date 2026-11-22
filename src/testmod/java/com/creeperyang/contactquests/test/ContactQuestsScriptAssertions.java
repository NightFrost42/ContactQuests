package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.compat.kubejs.ContactKubeJSPlugin;
import com.creeperyang.contactquests.compat.kubejs.KubeJSNpcSavedData;
import com.creeperyang.contactquests.compat.kubejs.MailReceivedEventJS;
import com.creeperyang.contactquests.compat.kubejs.QuestOverrideSavedData;
import com.creeperyang.contactquests.data.CollectionSavedData;
import com.creeperyang.contactquests.data.RewardDistributionManager;
import com.creeperyang.contactquests.quest.reward.ParcelReward;
import com.creeperyang.contactquests.quest.reward.PostcardReward;
import com.creeperyang.contactquests.quest.reward.RedPacketReward;
import com.creeperyang.contactquests.quest.task.ParcelTask;
import com.creeperyang.contactquests.quest.task.PostcardTask;
import com.creeperyang.contactquests.quest.task.RedPacketTask;
import com.creeperyang.contactquests.bridge.IQuestExtension;
import com.creeperyang.contactquests.bridge.ITeamDataExtension;
import com.creeperyang.contactquests.utils.ContactMailNbt;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.platform.PlatformHelper;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/** Observes the actual objects after calls made from Rhino/KubeJS scripts. */
public final class ContactQuestsScriptAssertions {
    private ContactQuestsScriptAssertions() {}

    public static boolean liveQuestText(ServerLevel level, String phase) {
        var file = ServerQuestFile.INSTANCE;
        var quest = file.getQuest(ContactQuestsTestServerController.getRuntimeQuestId());
        if (quest == null) return false;
        String prefix = phase.equals("saved") ? "脚本存档覆盖"
                : phase.equals("global_after_saved") || phase.equals("reset") ? "脚本再次全局" : "脚本实时全局";
        String second = phase.equals("saved") ? "覆盖第二行"
                : phase.equals("global_after_saved") || phase.equals("reset") ? "再次第二行" : "全局第二行";
        var saved = QuestOverrideSavedData.Companion.get(level).getOverride(quest.id);
        var raw = new CompoundTag();
        quest.writeData(raw);
        for (String locale : List.of("en_us", "zh_cn")) {
            if (phase.equals("saved")) {
                if (saved == null || !saved.getCompound("title").getString(locale).equals(prefix + "标题")
                        || !saved.getCompound("subtitle").getString(locale).equals(prefix + "副标题")
                        || !saved.getCompound("description").getList(locale, 8).getString(0).equals(prefix + "描述")
                        || !saved.getCompound("description").getList(locale, 8).getString(1).equals(second)) return false;
                if (!overrideMap(quest, "contactQuests$titleOverrides").get(locale).equals(prefix + "标题")
                        || !overrideMap(quest, "contactQuests$subtitleOverrides").get(locale).equals(prefix + "副标题")
                        || !overrideMap(quest, "contactQuests$descOverrides").get(locale).equals(List.of(prefix + "描述", second))) return false;
            } else {
                if (!raw.getString("title").equals(prefix + "标题")
                        || !raw.getString("subtitle").equals(prefix + "副标题")
                        || !raw.getList("description", Tag.TAG_STRING).stream().map(Tag::getAsString)
                        .toList().equals(List.of(prefix + "描述", second))) return false;
                if (overrideMap(quest, "contactQuests$titleOverrides").containsKey(locale)
                        || overrideMap(quest, "contactQuests$subtitleOverrides").containsKey(locale)
                        || overrideMap(quest, "contactQuests$descOverrides").containsKey(locale)
                        || saved != null && (saved.getCompound("title").contains(locale)
                        || saved.getCompound("subtitle").contains(locale) || saved.getCompound("description").contains(locale))) return false;
            }
        }
        return true;
    }

    public static boolean graphRules(ServerLevel level, String id, String dependency) {
        var file = ServerQuestFile.INSTANCE;
        var quest = file.getQuest(Long.parseUnsignedLong(id, 16));
        var tag = new CompoundTag();
        quest.writeData(tag);
        var chapter = new CompoundTag();
        quest.getChapter().writeData(chapter);
        return tag.getString("title").equals("Edited quest") && tag.getString("subtitle").equals("Edited subtitle")
                && tag.getList("description", Tag.TAG_STRING).stream().map(Tag::getAsString).toList().equals(List.of("Edited description"))
                && chapter.getString("title").equals("Edited chapter")
                && chapter.getList("subtitle", Tag.TAG_STRING).stream().map(Tag::getAsString).toList().equals(List.of("Chapter subtitle"))
                && tag.getList("dependencies", Tag.TAG_STRING).stream().anyMatch(o -> o.getAsString().equals(dependency))
                && tag.getList("cq_req_tags", 8).size() == 2 && tag.getString("cq_tag_mode").equals("ALL")
                && tag.getBoolean("cq_hide_no_tags") && tag.getBoolean("cq_hide_locked")
                && tag.getString("cq_mutex_mode").equals("NUMBER") && tag.getInt("cq_mutex_count") == 1
                && tag.getList("cq_mutex_ids", 8).getString(0).equals(dependency)
                && tag.getDouble("x") == 2 && tag.getDouble("y") == 3;
    }

    public static boolean graphTaskReward(ServerLevel level, String taskId, String rewardId) {
        var task = (ParcelTask) ServerQuestFile.INSTANCE.getTask(Long.parseUnsignedLong(taskId, 16));
        var reward = (ParcelReward) ServerQuestFile.INSTANCE.getReward(Long.parseUnsignedLong(rewardId, 16));
        return task.getItemStack().is(Items.DIAMOND) && task.getCount() == 6
                && task.getTargetAddressee().equals("CQGraphEdited")
                && com.creeperyang.contactquests.data.DataManager.INSTANCE.getParcelTasks().get(task.id) == task
                && reward.getItem().is(Items.APPLE) && reward.getCount() == 3
                && reward.getUnlockTags().equals(List.of("GraphRewardTag"));
    }

    public static boolean graphReferencesCleared(ServerLevel level, String id) {
        var quest = ServerQuestFile.INSTANCE.getQuest(Long.parseUnsignedLong(id, 16));
        var tag = new CompoundTag(); quest.writeData(tag);
        return tag.getList("dependencies", Tag.TAG_STRING).isEmpty() && tag.getList("cq_mutex_ids", 8).isEmpty();
    }

    public static boolean graphOverridesReload(ServerLevel level, String id, String taskId, String rewardId) {
        var saved = QuestOverrideSavedData.Companion.get(level);
        var restored = ContactQuestsTestUtil.roundTrip(saved);
        level.getServer().overworld().getDataStorage().set("contactquests_quest_overrides", restored);
        ContactKubeJSPlugin.loadPersistentData(level.getServer());
        var quest = ServerQuestFile.INSTANCE.getQuest(Long.parseUnsignedLong(id, 16));
        var text = restored.getOverride(quest.id);
        var raw = new CompoundTag(); quest.writeData(raw);
        return graphTaskReward(level, taskId, rewardId) && raw.getString("title").equals("Edited quest")
                && (text == null || !text.getCompound("title").contains("en_us"));
    }

    public static boolean npcConfig(ServerLevel level, String name, int delay, boolean autoFill) {
        var config = KubeJSNpcSavedData.Companion.get(level).getNpcData(name);
        return config != null && config.getDeliveryTime() == delay && config.getAutoFill() == autoFill;
    }

    public static boolean npcRemoved(ServerLevel level, String name) {
        return KubeJSNpcSavedData.Companion.get(level).getNpcData(name) == null;
    }

    public static boolean npcConfigRoundTrip(ServerLevel level, String name, int delay, boolean autoFill) {
        var restored = ContactQuestsTestUtil.roundTrip(KubeJSNpcSavedData.Companion.get(level));
        var config = restored.getNpcData(name);
        return config != null && config.getDeliveryTime() == delay && config.getAutoFill() == autoFill;
    }

    public static boolean npcConfigsEmpty(ServerLevel level) {
        return KubeJSNpcSavedData.Companion.get(level).getAllNpcs().isEmpty();
    }

    public static boolean stashRoundTrip(ServerPlayer player, String name) {
        var restored = ContactQuestsTestUtil.roundTrip(CollectionSavedData.Companion.get(player.serverLevel()));
        var stacks = restored.getStacks(player.getUUID(), name);
        return stacks.size() == 2 && stacks.stream().filter(s -> s.is(Items.APPLE)).mapToInt(ItemStack::getCount).sum() == 3
                && stacks.stream().filter(s -> s.is(Items.PAPER)).mapToInt(ItemStack::getCount).sum() == 2
                && restored.getTriggerCount(player.getUUID(), name) == 2;
    }

    public static int npcTriggerCount(ServerPlayer player, String name) {
        return CollectionSavedData.Companion.get(player.serverLevel()).getTriggerCount(player.getUUID(), name);
    }

    public static boolean triggerCountRoundTrip(ServerPlayer player, String name, int expected) {
        return ContactQuestsTestUtil.roundTrip(CollectionSavedData.Companion.get(player.serverLevel())).getTriggerCount(player.getUUID(), name) == expected;
    }

    public static boolean questForced(TeamData data, long id) {
        return ((ITeamDataExtension) data).contactQuests$isQuestForced(id);
    }

    public static boolean questBlocked(TeamData data, long id) {
        return ((ITeamDataExtension) data).contactQuests$isQuestBlocked(id);
    }

    public static boolean factories(ItemStack card, ItemStack red, ServerPlayer player) {
        var helpers = new MailReceivedEventJS(player, "factory", List.of(card, red), false);
        CompoundTag custom = card.getTag();
        var redContents = helpers.getContainerContents(red);
        return card.getItem() instanceof PostcardItem && helpers.getPostcardText(card).equals("fixed test postcard")
                && helpers.getPostcardStyle(card).equals("contact:default")
                && custom != null && custom.getList("ContactQuestsUnlockTags", Tag.TAG_STRING).getString(0).equals("ContactQuestsTestTag")
                && helpers.getRedPacketBlessing(red).equals("fixed test blessing")
                && redContents.size() == 1 && redContents.get(0).is(Items.DIAMOND)
                && redContents.get(0).getCount() == 2;
    }

    public static int[] mailboxAmounts(ServerPlayer player) {
        var data = ContactKubeJSPlugin.getData(player);
        var container = PlatformHelper.getMailboxContents(data.getTeamId());
        int apples = 0, paper = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack mail = container.getItem(slot);
            for (ItemStack item : ContactMailNbt.INSTANCE.readContents(mail)) {
                if (item.is(Items.APPLE)) apples += item.getCount();
                if (item.is(Items.PAPER)) paper += item.getCount();
            }
        }
        return new int[]{apples, paper};
    }

    public static void verifyScriptDelivery(ServerPlayer player, int previousApples, int previousPaper) {
        for (int tick = 0; tick < 100; tick++) RewardDistributionManager.INSTANCE.onServerTick(player.serverLevel());
        int[] amounts = mailboxAmounts(player);
        record("kubejs.item_factory", amounts[0] == previousApples + 3 && amounts[1] == previousPaper + 2
                && ContactQuestsTestServerController.isCasePassed("kubejs.item_factory.components"),
                "Factories inspected; real mailbox delta apple=" + (amounts[0] - previousApples)
                        + ", paper=" + (amounts[1] - previousPaper));
    }

    public static boolean replacers(ServerPlayer player) throws ReflectiveOperationException {
        TeamData data = ContactKubeJSPlugin.getData(player);
        String cardTask = PostcardTask.PostcardPlaceholderSupport.INSTANCE.replace("contactquests_test_postcard", player, data);
        String redTask = RedPacketTask.RedPacketPlaceholderSupport.INSTANCE.replace("contactquests_test_red_packet", player, data);
        String cardReward = PostcardReward.Companion.processText("base", player);
        String redReward = RedPacketReward.Companion.processText("base", player);
        var collection = CollectionSavedData.Companion.get(player.serverLevel());
        var replace = CollectionSavedData.class.getDeclaredMethod("applyReplacements", String.class,
                CollectionSavedData.ReplacerContext.class);
        replace.setAccessible(true);
        String npc = (String) replace.invoke(collection, "base",
                new CollectionSavedData.ReplacerContext(player, "CQScript", 0, List.of()));
        return cardTask.equals("fixed postcard replacement") && redTask.equals("fixed red packet replacement")
                && cardReward.equals("base|postcard") && redReward.equals("base|red_packet")
                && npc.equals("base|npc_reply");
    }

    public static void verifyEditedGraph(ServerLevel level) {
        var file = ServerQuestFile.INSTANCE;
        var quest = file.getQuest(ContactQuestsTestServerController.getRuntimeQuestId());
        var parcel = (ParcelTask) file.getTask(ContactQuestsTestServerController.getRuntimeParcelTaskId());
        var red = (RedPacketTask) file.getTask(ContactQuestsTestServerController.getRuntimeRedPacketTaskId());
        var card = (PostcardTask) file.getTask(ContactQuestsTestServerController.getRuntimePostcardTaskId());
        var parcelReward = (ParcelReward) file.getReward(ContactQuestsTestServerController.getRuntimeParcelRewardId());
        var redReward = (RedPacketReward) file.getReward(ContactQuestsTestServerController.getRuntimeRedPacketRewardId());
        var cardReward = (PostcardReward) file.getReward(ContactQuestsTestServerController.getRuntimePostcardRewardId());
        boolean tasks = parcel.getItemStack().is(Items.EMERALD) && parcel.getCount() == 3
                && parcel.getTargetAddressee().equals("CQTest_SavedParcel")
                && red.getItemStack().is(Items.COAL) && red.getCount() == 3
                && red.getBlessing().equals("saved graph blessing") && red.getTargetAddressee().equals("CQTest_SavedRedPacket")
                && card.getPostcardStyle().equals("contact:default") && card.getPostcardText().equals("saved graph postcard")
                && card.getTargetAddressee().equals("CQTest_SavedPostcard");
        boolean rewards = parcelReward.getItem().is(Items.BREAD) && parcelReward.getCount() == 3
                && parcelReward.getRandomBonus() == 2 && parcelReward.getTargetAddressee().equals("CQTest_SavedParcel")
                && redReward.getItem().is(Items.LAPIS_LAZULI) && redReward.getCount() == 3 && redReward.getRandomBonus() == 2
                && redReward.getBlessing().equals("saved graph reward") && redReward.getTargetAddressee().equals("CQTest_SavedRedPacket")
                && cardReward.getPostcardStyle().equals("contact:default")
                && cardReward.getPostcardText().equals("saved graph reward postcard")
                && cardReward.getTargetAddressee().equals("CQTest_SavedPostcard");
        var overrides = QuestOverrideSavedData.Companion.get(level);
        var restored = ContactQuestsTestUtil.roundTrip(overrides);
        CompoundTag text = restored.getOverride(quest.getId());
        boolean textSaved = text != null && text.getCompound("title").getString("zh_cn").equals("valid graph title")
                && text.getCompound("subtitle").getString("zh_cn").equals("valid graph subtitle")
                && text.getCompound("description").getList("zh_cn", Tag.TAG_STRING).getString(0).equals("valid graph description");
        var raw = new CompoundTag(); quest.writeData(raw);
        boolean globals = raw.getString("title").equals("valid graph title")
                && raw.getString("subtitle").equals("valid graph subtitle")
                && raw.getList("description", Tag.TAG_STRING).stream().map(Tag::getAsString)
                .toList().equals(List.of("valid graph description"));
        record("kubejs.task_editing.valid", tasks, "All three task items/counts/recipients/text/style after script SavedData edits");
        record("kubejs.reward_editing.valid", rewards, "All three reward items/counts/bonus/sender/text/style after script SavedData edits");
        record("kubejs.quest_text_editing.valid", textSaved && globals, "Global quest text and all text NBT override fields");
        if (!tasks || !rewards || !textSaved || !globals) throw new IllegalStateException("Script edit fields differ from requested values");

        // Disturb real runtime fields, replace SavedData with its loaded copy, then
        // invoke the same loading path used when a saved world starts.
        parcel.setCount(99);
        redReward.setBlessing("disturbed");
        ((IQuestExtension) (Object) quest).contactQuests$setDescriptionOverride("zh_cn", null);
        level.getServer().overworld().getDataStorage().set("contactquests_quest_overrides", restored);
        ContactKubeJSPlugin.loadPersistentData(level.getServer());
        boolean reloaded = parcel.getCount() == 3 && redReward.getBlessing().equals("saved graph reward")
                && overrideMap(quest, "contactQuests$descOverrides").get("zh_cn").equals(List.of("valid graph description"));
        record("kubejs.override_reload", reloaded, "NBT reload reapplied task count, reward blessing and description override");
        if (!reloaded) throw new IllegalStateException("Persistent script overrides were not reapplied");
    }

    public static Map<?, ?> overrideMap(Object quest, String fieldName) {
        try {
            Field field = quest.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (Map<?, ?>) field.get(quest);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot inspect quest override", error);
        }
    }

    private static void record(String id, boolean success, String message) {
        ContactQuestsTestServerController.recordCase(id, success, message);
    }
}
