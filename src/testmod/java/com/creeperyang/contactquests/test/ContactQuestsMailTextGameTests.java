package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.config.*;
import com.creeperyang.contactquests.data.*;
import com.creeperyang.contactquests.quest.task.*;
import com.creeperyang.contactquests.bridge.ITeamDataExtension;
import com.flechazo.contact.common.component.ContactDataComponents;
import com.flechazo.contact.common.item.PostcardItem;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbquests.quest.*;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Normal mail/text paths observed independently from their replacement implementation. */
@GameTestHolder(ContactQuestsTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContactQuestsMailTextGameTests {
    private static ServerPlayer player(ServerLevel level) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CQText"));
        ((dev.ftb.mods.ftbteams.data.TeamManagerImpl) FTBTeamsAPI.api().getManager())
                .playerLoggedIn(null, player.getUUID(), player.getScoreboardName());
        return player;
    }
    private static List<ItemStack> buffered(ServerPlayer player) throws Exception {
        var field = RewardDistributionManager.class.getDeclaredField("buffer"); field.setAccessible(true);
        List<ItemStack> contents = new ArrayList<>();
        for (var entry : ((Map<?, ?>) field.get(RewardDistributionManager.INSTANCE)).entrySet()) {
            var owner = entry.getKey().getClass().getDeclaredMethod("getPlayerId"); owner.setAccessible(true);
            if (!player.getUUID().equals(owner.invoke(entry.getKey()))) continue;
            for (var list : ((Map<?, ?>) entry.getValue()).values()) contents.addAll((List<ItemStack>) list);
        }
        return contents;
    }
    private static ErrorSolveData rule(StyleType style, String template) {
        var rule = new ErrorSolveData(); rule.setReturnType(ErrorSolveType.NOW); rule.setStyleType(style);
        rule.setStyle("contact:creeper");
        rule.setMessage(new ArrayList<>(List.of(new MessageData(template, "contact:creeper", false, 1))));
        return rule;
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void collectionReplyResolvesRealItemsAndRejectsOutOfRangeTemplates(GameTestHelper helper) throws Exception {
        var level = helper.getLevel().getServer().overworld(); var player = player(level);
        var store = CollectionSavedData.Companion.get(level); String npc = "CQText_" + player.getUUID();
        NpcConfigManager.INSTANCE.syncWithQuests(Set.of(npc), Set.of(), Set.of(), Set.of());
        var config = NpcConfigManager.INSTANCE.getNpcData(npc);
        config.getErrorSolve().clear(); config.getErrorSolve().add(rule(StyleType.SPECIFIC,
                "<item_1_name>=<item_1_count>; <item_2_name>=<item_2_count>; total=<total_count>/<total_types>; visit=<trigger_count>"));
        var apples = new ItemStack(Items.APPLE, 3); var dirt = new ItemStack(Items.DIRT, 2);
        apples.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Travel $1 \\ apples"));
        store.addItems(player, npc, List.of(apples, dirt));
        var result = buffered(player);
        var cards = result.stream().filter(s -> s.getItem() instanceof PostcardItem).toList();
        // The existing real Rhino fixture appends this independently fixed suffix to every NPC reply.
        String expected = apples.getHoverName().getString() + "=3; " + dirt.getHoverName().getString() + "=2; total=5/2; visit=1|npc_reply";
        helper.assertTrue(cards.size() == 1 && PostcardItem.getText(cards.getFirst()).equals(expected)
                        && npc.equals(cards.getFirst().get(ContactDataComponents.POSTCARD_SENDER.get()))
                        && ResourceLocation.parse("contact:creeper").equals(cards.getFirst().get(ContactDataComponents.POSTCARD_STYLE_ID.get()))
                        && result.stream().filter(s -> s.is(Items.APPLE)).mapToInt(ItemStack::getCount).sum() == 3
                        && result.stream().filter(s -> s.is(Items.DIRT)).mapToInt(ItemStack::getCount).sum() == 2,
                "Real NOW reply lost item name/count/total/visit, SPECIFIC style, sender or exact returned attachments: "
                        + result.stream().map(s -> s.save(level.registryAccess()).toString()).toList());
        for (String invalid : List.of("<item_0_name>", "<item_3_count>", "<item_999999999999999999_name>")) {
            var other = player(level); String invalidNpc = "CQBadText_" + other.getUUID();
            NpcConfigManager.INSTANCE.syncWithQuests(Set.of(invalidNpc), Set.of(), Set.of(), Set.of());
            var invalidConfig = NpcConfigManager.INSTANCE.getNpcData(invalidNpc);
            invalidConfig.getErrorSolve().clear(); invalidConfig.getErrorSolve().add(rule(StyleType.SPECIFIC, invalid));
            store.addItems(other, invalidNpc, List.of(new ItemStack(Items.APPLE, 2)));
            var invalidCards = buffered(other).stream().filter(s -> s.getItem() instanceof PostcardItem).toList();
            helper.assertTrue(invalidCards.size() == 1 && PostcardItem.getText(invalidCards.getFirst()).equals("Error: No valid message"),
                    "Out-of-range or overflowing item placeholder did not choose its fixed error reply: " + invalid);
        }
        var blank = new MessageData("line one\\n第二行", "", true, 1);
        var defaultCard = store.generatePostcard(npc, rule(StyleType.SPECIFIC, "unused"), blank);
        helper.assertTrue(ResourceLocation.parse("contact:default").equals(defaultCard.get(ContactDataComponents.POSTCARD_STYLE_ID.get()))
                        && PostcardItem.getText(defaultCard).equals("line one\n第二行")
                        && defaultCard.getItem() != cards.getFirst().getItem(),
                "Blank SPECIFIC style did not use Contact's default, preserve newlines or use the real ender postcard");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void collectionLegacyDataCreatesIndependentPlayerCopies(GameTestHelper helper) throws Exception {
        var level = helper.getLevel().getServer().overworld();
        var entry = new CompoundTag(); entry.putString("name", "CQLegacyText"); entry.putInt("count", 6);
        var items = new ListTag(); items.add(new ItemStack(Items.DIAMOND, 4).save(level.registryAccess())); entry.put("itemStacks", items);
        var legacyList = new ListTag(); legacyList.add(entry); var root = new CompoundTag(); root.put("collectionDataList", legacyList);
        var load = CollectionSavedData.Companion.getClass().getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
        load.setAccessible(true); var store = (CollectionSavedData) load.invoke(CollectionSavedData.Companion, root, level.registryAccess());
        var a = UUID.randomUUID(); var b = UUID.randomUUID();
        var beforeLegacyQuery = store.save(new CompoundTag(), level.registryAccess());
        boolean dirtyBeforeQuery = store.isDirty();
        helper.assertTrue(store.getTriggerCountByName("CQLegacyText") == 6 && store.getTriggerCountByName("missing") == 0
                        && store.isDirty() == dirtyBeforeQuery
                        && beforeLegacyQuery.equals(store.save(new CompoundTag(), level.registryAccess())),
                "Public legacy name-only count query changed data or did not return exact present/absent values");
        helper.assertTrue(store.getTriggerCount(a, "CQLegacyText") == 6 && store.getStacks(a, "CQLegacyText").getFirst().getCount() == 4,
                "Legacy data was not available to its first player");
        store.getStacks(a, "CQLegacyText").getFirst().setCount(99);
        helper.assertTrue(store.getStacks(b, "CQLegacyText").getFirst().getCount() == 4,
                "Legacy getter leaked a mutable shared stack");
        store.setTriggerCount(a, "CQLegacyText", 7); store.getStacks(a, "CQLegacyText").getFirst().setCount(1);
        store.setTriggerCount(b, "CQLegacyText", 8);
        var restored = ContactQuestsTestUtil.roundTrip(store, level.registryAccess());
        helper.assertTrue(restored.getTriggerCount(a, "CQLegacyText") == 7 && restored.getStacks(a, "CQLegacyText").getFirst().getCount() == 1
                        && restored.getTriggerCount(b, "CQLegacyText") == 8 && restored.getStacks(b, "CQLegacyText").getFirst().getCount() == 4
                        && restored.getTriggerCountByName("CQLegacyText") == 6 && restored.getTriggerCountByName("missing") == 0
                        && restored.getStacks(UUID.randomUUID(), "CQLegacyText").getFirst().getCount() == 4,
                "Player migration or save/reload changed another player's copy or the untouched legacy fallback");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void taskTextUsesTeamSizeAndPersistentCacheBeforeTemplates(GameTestHelper helper) {
        var level = helper.getLevel().getServer().overworld(); var player = player(level); var file = ServerQuestFile.INSTANCE;
        var chapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(), "cq_text_" + player.getUUID());
        file.getDefaultChapterGroup().addChapter(chapter); var quest = new Quest(file.newID(), chapter); chapter.addQuest(quest);
        var card = new PostcardTask(file.newID(), quest); card.setPostcardText("<player_name>|<team_name>|<team_size>"); quest.addTask(card);
        var red = new RedPacketTask(file.newID(), quest); red.setBlessing("<player_name>|<team_name>|<team_size>"); quest.addTask(red);
        ContactQuestsTestUtil.refreshIndex(file);
        var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow(); var data = file.getOrCreateTeamData(team);
        String expected = player.getName().getString() + "|" + data.getName() + "|" + team.getMembers().size();
        helper.assertTrue(card.getResolvedText(data, player).equals(expected) && red.getResolvedText(data, player).equals(expected),
                "Normal server text failed to resolve player/team names and team size");
        var ext = (ITeamDataExtension) data; ext.contactQuests$setPostcardText(card.getId(), "cached postcard\n原样");
        ext.contactQuests$setRedPacketBlessing(red.getId(), "cached blessing\n原样");
        helper.assertTrue(card.getResolvedText(data, player).equals("cached postcard\n原样")
                        && red.getResolvedText(data, player).equals("cached blessing\n原样"), "Team cache did not take precedence");
        var restored = new TeamData(team.getId(), file, data.getName()); restored.deserializeNBT(data.serializeNBT());
        helper.assertTrue(card.getResolvedText(restored, player).equals("cached postcard\n原样")
                        && red.getResolvedText(restored, player).equals("cached blessing\n原样")
                        && card.getResolvedText(null, null).equals(card.getPostcardText())
                        && red.getResolvedText(null, null).equals(red.getBlessing()), "Persistent cache or absent-context fallback changed text");
        helper.succeed();
    }
}
