package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.config.ContactConfig;
import com.creeperyang.contactquests.compat.kubejs.KubeJSNpcSavedData;
import com.creeperyang.contactquests.compat.kubejs.KubeJSMailQueueSavedData;
import com.creeperyang.contactquests.config.NpcConfigManager;
import com.creeperyang.contactquests.data.CollectionSavedData;
import com.creeperyang.contactquests.data.DataManager;
import com.creeperyang.contactquests.data.RewardDeliverySavedData;
import com.creeperyang.contactquests.data.RewardDistributionManager;
import com.creeperyang.contactquests.data.TaskDeliverySavedData;
import com.creeperyang.contactquests.quest.reward.*;
import com.creeperyang.contactquests.quest.task.ContactTask;
import com.creeperyang.contactquests.quest.task.ParcelTask;
import com.creeperyang.contactquests.quest.task.PostcardTask;
import com.creeperyang.contactquests.quest.task.RedPacketTask;
import com.creeperyang.contactquests.utils.RedPacketUtils;
import com.creeperyang.contactquests.bridge.IQuestExtension;
import com.creeperyang.contactquests.bridge.ITeamDataExtension;
import com.flechazo.contact.common.item.ParcelItem;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.platform.PlatformHelper;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftblibrary.config.Tristate;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.loot.RewardTable;
import dev.ftb.mods.ftbquests.quest.loot.WeightedReward;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

@GameTestHolder(ContactQuestsTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContactQuestsRegressionTests {
    private ContactQuestsRegressionTests() {
    }

    private record Fixture(ServerLevel level, ServerPlayer player, Team team, TeamData data,
                           ServerQuestFile file, Quest quest) {
    }

    private static Fixture fixture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().overworld();
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CQRegression"));
        var manager = FTBTeamsAPI.api().getManager();
        var impl = (dev.ftb.mods.ftbteams.data.TeamManagerImpl) manager;
        // Initialize an offline personal team; FakePlayer has no client handshake.
        impl.playerLoggedIn(null, player.getUUID(), player.getGameProfile().getName());
        Team team = manager.getTeamForPlayer(player).orElseThrow();
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        Chapter chapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(), "cq_regression");
        file.getDefaultChapterGroup().addChapter(chapter);
        Quest quest = new Quest(file.newID(), chapter);
        chapter.addQuest(quest);
        TeamData data = file.getOrCreateTeamData(team);
        data.setLocked(false);
        ContactConfig.INSTANCE.getEnableDeliveryTime().set(true);
        return new Fixture(level, player, team, data, file, quest);
    }

    private static ParcelTask parcelTask(Fixture fixture, int count, String target, int delay) {
        ParcelTask task = new ParcelTask(fixture.file.newID(), fixture.quest)
                .setStackAndCount(new ItemStack(Items.DIAMOND), count);
        task.setTargetAddressee(target);
        fixture.quest.addTask(task);
        index(fixture, target, delay);
        return task;
    }

    private static void index(Fixture fixture, String target, int delay) {
        ContactQuestsTestUtil.refreshIndex(fixture.file);
        fixture.file.markDirty();
        DataManager.INSTANCE.ensureTargetsFresh();
        NpcConfigManager.INSTANCE.getNpcData(target).setDeliveryTime(delay);
        fixture.data.clearCachedProgress();
    }

    private static boolean sendParcel(Fixture fixture, String target, int count) {
        SimpleContainer contents = new SimpleContainer(4);
        contents.setItem(0, new ItemStack(Items.DIAMOND, count));
        ItemStack mail = ParcelItem.getParcel(contents, false, "CQRegression");
        return DataManager.INSTANCE.matchParcelTaskItem(fixture.player, mail,
                mail.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY), target);
    }

    private static void tick(Fixture fixture) {
        TaskDeliverySavedData queue = TaskDeliverySavedData.Companion.get(fixture.level);
        queue.tick(fixture.level);
        queue.tick(fixture.level);
    }

    private static void progress(GameTestHelper helper, Fixture fixture, ContactTask task, long expected) {
        helper.assertTrue(fixture.data.getProgress(task) == expected,
                "Task " + task.getId() + " expected " + expected + ", actual " + fixture.data.getProgress(task));
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void delayedParcelCreditsSubmittedQuantity(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        String target = "CQQuantity_" + fixture.quest.getId();
        ParcelTask task = parcelTask(fixture, 5, target, 2);
        helper.assertTrue(sendParcel(fixture, target, 1), "One-item parcel was rejected");
        progress(helper, fixture, task, 0);
        helper.assertTrue(TaskDeliverySavedData.Companion.get(fixture.level)
                .getPendingAmount(fixture.team.getId(), task.getId()) == 1, "Reserved quantity is not one");
        tick(fixture);
        progress(helper, fixture, task, 1);
        helper.assertTrue(!fixture.data.isCompleted(task), "One of five items completed the task");
        helper.assertTrue(sendParcel(fixture, target, 2), "First partial batch was rejected");
        helper.assertTrue(sendParcel(fixture, target, 2), "Second partial batch was rejected");
        helper.assertTrue(sendParcel(fixture, target, 1), "NPC rejected non-task mail after reservation filled");
        helper.assertTrue(TaskDeliverySavedData.Companion.get(fixture.level)
                .getPendingAmount(fixture.team.getId(), task.getId()) == 4, "Task over-reserved a full queue");
        tick(fixture);
        progress(helper, fixture, task, 5);
        helper.assertTrue(fixture.data.isCompleted(task), "Five items did not complete the task");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void immediateAndDelayedParcelRespectExistingProgress(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        String target = "CQExisting_" + fixture.quest.getId();
        ParcelTask task = parcelTask(fixture, 5, target, 0);
        fixture.data.setProgress(task, 2);
        helper.assertTrue(sendParcel(fixture, target, 1), "Immediate partial delivery was rejected");
        progress(helper, fixture, task, 3);
        NpcConfigManager.INSTANCE.getNpcData(target).setDeliveryTime(2);
        helper.assertTrue(sendParcel(fixture, target, 5), "Delayed remaining delivery was rejected");
        helper.assertTrue(TaskDeliverySavedData.Companion.get(fixture.level)
                .getPendingAmount(fixture.team.getId(), task.getId()) == 2, "Queue ignored existing progress");
        tick(fixture);
        progress(helper, fixture, task, 5);
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void delayedRedPacketCountsContentsAndPostcardsCountOne(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        String redTarget = "CQRedQuantity_" + fixture.quest.getId();
        RedPacketTask red = new RedPacketTask(fixture.file.newID(), fixture.quest)
                .setStackAndCount(new ItemStack(Items.GOLD_INGOT), 5);
        red.setTargetAddressee(redTarget);
        fixture.quest.addTask(red);
        index(fixture, redTarget, 2);
        SimpleContainer contents = new SimpleContainer(1);
        contents.setItem(0, new ItemStack(Items.GOLD_INGOT, 2));
        ItemStack mail = RedPacketUtils.INSTANCE.getRedPacket(contents, "", "CQRegression");
        helper.assertTrue(DataManager.INSTANCE.matchRedPacketTaskItem(fixture.player, mail, redTarget),
                "Red packet was rejected");
        tick(fixture);
        progress(helper, fixture, red, 2);

        String cardTarget = "CQCardQuantity_" + fixture.quest.getId();
        PostcardTask card = new PostcardTask(fixture.file.newID(), fixture.quest);
        card.setCount(3);
        card.setTargetAddressee(cardTarget);
        fixture.quest.addTask(card);
        index(fixture, cardTarget, 2);
        ItemStack postcard = PostcardItem.getPostcard(ResourceLocation.parse("contact:default"), false);
        helper.assertTrue(DataManager.INSTANCE.matchPostcardTaskItem(fixture.player, postcard, cardTarget),
                "Postcard was rejected");
        tick(fixture);
        progress(helper, fixture, card, 1);
        helper.assertTrue(!fixture.data.isCompleted(card), "One postcard completed a three-card task");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void delayedQuantitySurvivesSaveAndLegacyIsConservative(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        String target = "CQSavedQuantity_" + fixture.quest.getId();
        ParcelTask task = parcelTask(fixture, 5, target, 2);
        TaskDeliverySavedData original = new TaskDeliverySavedData();
        original.addParcel(fixture.player, task.getId(), 2, target, 2, fixture.team.getId());
        CompoundTag nbt = original.save(new CompoundTag(), fixture.level.registryAccess());
        TaskDeliverySavedData restored = TaskDeliverySavedData.Companion.load(nbt, fixture.level.registryAccess());
        helper.assertTrue(restored.getPendingAmount(fixture.team.getId(), task.getId()) == 2,
                "Save round-trip changed the submitted quantity/team");
        restored.tick(fixture.level);
        restored.tick(fixture.level);
        progress(helper, fixture, task, 2);

        fixture.data.setProgress(task, 0);
        CompoundTag entry = nbt.getList("PendingParcels", Tag.TAG_COMPOUND).getCompound(0);
        entry.remove("amount");
        entry.remove("team");
        TaskDeliverySavedData legacy = TaskDeliverySavedData.Companion.load(nbt, fixture.level.registryAccess());
        legacy.tick(fixture.level);
        legacy.tick(fixture.level);
        progress(helper, fixture, task, 1);
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void delayedTaskKeepsSendingTeamAndRetriesLockedProgress(GameTestHelper helper) {
        Fixture sending = fixture(helper);
        Fixture current = fixture(helper);
        String target = "CQTeamAtSend_" + sending.quest.getId();
        ParcelTask task = parcelTask(sending, 5, target, 0);
        TaskDeliverySavedData queue = new TaskDeliverySavedData();
        // Model a post-send team switch: the player in this queue now belongs
        // to a different real team. Progress still belongs to the recorded team.
        queue.addParcel(current.player, task.getId(), 1, target, 2, sending.team.getId());
        sending.data.setLocked(true);
        queue.tick(sending.level);
        progress(helper, sending, task, 0);
        helper.assertTrue(queue.getPendingAmount(sending.team.getId(), task.getId()) == 2,
                "Lock dropped the accepted submitted quantity");
        queue = ContactQuestsTestUtil.roundTrip(queue, sending.level.registryAccess());
        sending.data.setLocked(false);
        for (int i = 0; i <= 200; i++) queue.tick(sending.level);
        progress(helper, sending, task, 2);
        progress(helper, current, task, 0);
        helper.assertTrue(queue.getPendingAmount(sending.team.getId(), task.getId()) == 0,
                "Completed delivery remained pending");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void offlineScriptMailSurvivesDueTimeAndReload(GameTestHelper helper) {
        Fixture fixture = fixture(helper); // Its FakePlayer is deliberately offline.
        KubeJSMailQueueSavedData queue = new KubeJSMailQueueSavedData();
        ItemStack content = new ItemStack(Items.DIAMOND, 2);
        queue.addMail(fixture.player, "CQOfflineScript", List.of(content), 1, false, false);
        content.setCount(9);
        for (int i = 0; i < 3; i++) queue.tick(fixture.level);
        queue = ContactQuestsTestUtil.roundTrip(queue, fixture.level.registryAccess());
        CompoundTag saved = queue.save(new CompoundTag(), fixture.level.registryAccess());
        ListTag mails = saved.getList("PendingMails", Tag.TAG_COMPOUND);
        helper.assertTrue(mails.size() == 1, "Offline due mail was lost");
        CompoundTag mail = mails.getCompound(0);
        helper.assertTrue(mail.getInt("TicksLeft") == 0 && mail.getUUID("PlayerUUID").equals(fixture.player.getUUID()),
                "Due time or sender changed on reload");
        ItemStack restored = ItemStack.parseOptional(fixture.level.registryAccess(),
                mail.getList("Items", Tag.TAG_COMPOUND).getCompound(0));
        helper.assertTrue(restored.is(Items.DIAMOND) && restored.getCount() == 2,
                "Queued copy changed with the caller's mutable stack");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void legacyDimensionStashMigratesOnlyOnce(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ServerLevel nether = fixture.level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        helper.assertTrue(nether != null, "Missing Nether level");
        CollectionSavedData global = CollectionSavedData.Companion.get(fixture.level);
        CompoundTag marker = global.save(new CompoundTag(), fixture.level.registryAccess());
        ListTag dimensions = marker.getList("MigratedDimensions", Tag.TAG_STRING);
        dimensions.removeIf(tag -> tag.getAsString().equals("minecraft:the_nether"));
        marker.put("MigratedDimensions", dimensions);
        try {
            var companion = CollectionSavedData.Companion;
            var load = companion.getClass().getDeclaredMethod("load", CompoundTag.class,
                    net.minecraft.core.HolderLookup.Provider.class);
            load.setAccessible(true);
            global = (CollectionSavedData) load.invoke(companion, marker, fixture.level.registryAccess());
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
        String npc = "CQDimension_" + fixture.quest.getId();
        CollectionSavedData old = new CollectionSavedData();
        old.setStacks(fixture.player.getUUID(), npc, List.of(new ItemStack(Items.APPLE, 3)));
        old.setTriggerCount(fixture.player.getUUID(), npc, 2);
        fixture.level.getDataStorage().set("contactquests_collection_data", global);
        nether.getDataStorage().set("contactquests_collection_data", old);
        CollectionSavedData merged = CollectionSavedData.Companion.get(nether);
        helper.assertTrue(merged == global && merged.getStacks(fixture.player.getUUID(), npc).getFirst().getCount() == 3
                && merged.getTriggerCount(fixture.player.getUUID(), npc) == 2, "Old dimensional stash was not migrated");
        CollectionSavedData restored = ContactQuestsTestUtil.roundTrip(merged, fixture.level.registryAccess());
        fixture.level.getDataStorage().set("contactquests_collection_data", restored);
        CollectionSavedData again = CollectionSavedData.Companion.get(nether);
        helper.assertTrue(again.getStacks(fixture.player.getUUID(), npc).getFirst().getCount() == 3
                && again.getTriggerCount(fixture.player.getUUID(), npc) == 2,
                "Dimension migration duplicated quantities after reload");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void allFiveRewardsClaimOnceAndDeliverExactContents(GameTestHelper helper) throws Exception {
        Fixture fixture = fixture(helper);
        String sender = "CQReward_" + fixture.quest.getId();
        NpcConfigManager.INSTANCE.getNpcData(sender).setDeliveryTime(0);
        ParcelTask task = parcelTask(fixture, 1, "CQRewardTask_" + fixture.quest.getId(), 0);

        ParcelReward parcel = new ParcelReward(fixture.file.newID(), fixture.quest);
        parcel.setItem(new ItemStack(Items.APPLE));
        parcel.setCount(2);
        RedPacketReward red = new RedPacketReward(fixture.file.newID(), fixture.quest);
        red.setCount(3);
        red.setItem(new ItemStack(Items.EMERALD));
        PostcardReward postcard = new PostcardReward(fixture.file.newID(), fixture.quest);
        postcard.setPostcardStyle("contact:default");
        postcard.setPostcardText("reward quantity test");
        RewardTable table = new RewardTable(-1L, fixture.file);
        table.addReward(new WeightedReward(
                new ItemReward(fixture.file.newID(), table.getFakeQuest(), new ItemStack(Items.DIAMOND), 4), 1));
        ParcelRandomReward random = new ParcelRandomReward(fixture.file.newID(), fixture.quest);
        random.setTable(table);
        ParcelAllTableReward all = new ParcelAllTableReward(fixture.file.newID(), fixture.quest);
        all.setTable(table);
        List<ParcelRewardBase> rewards = List.of(parcel, red, postcard, random, all);
        for (ParcelRewardBase reward : rewards) {
            reward.setTargetAddressee(sender);
            fixture.quest.addReward(reward);
        }
        ContactQuestsTestUtil.refreshIndex(fixture.file);
        fixture.data.setProgress(task, 1);
        for (Reward reward : rewards) {
            fixture.data.claimReward(fixture.player, reward, false);
            helper.assertTrue(fixture.data.isRewardClaimed(fixture.player.getUUID(), reward),
                    reward.getClass().getSimpleName() + " did not return a successful claim");
            fixture.data.claimReward(fixture.player, reward, false);
        }
        helper.assertTrue(!new ParcelRandomReward(fixture.file.newID(), fixture.quest).claim(fixture.player, false),
                "Missing reward table returned success");
        helper.assertTrue(!new ParcelAllTableReward(fixture.file.newID(), fixture.quest).claim(fixture.player, false),
                "Missing all-reward table returned success");
        ParcelReward empty = new ParcelReward(fixture.file.newID(), fixture.quest);
        empty.setItem(ItemStack.EMPTY);
        helper.assertTrue(!empty.claim(fixture.player, false), "Empty reward returned success");

        // Delivery uses the real Contact mailbox data with an isolated team UUID.
        PlatformHelper.setMailboxData(fixture.team.getId(), fixture.level.dimension(), BlockPos.ZERO);
        RewardDistributionManager.INSTANCE.onServerTick(fixture.level);
        for (int i = 0; i < 4; i++) RewardDeliverySavedData.Companion.get(fixture.level).tick(fixture.level);
        var mailbox = PlatformHelper.getMailboxContents(fixture.team.getId());
        int apples = 0, diamonds = 0, emeralds = 0, cards = 0;
        for (int i = 0; i < mailbox.getContainerSize(); i++) {
            ItemStack mail = mailbox.getItem(i);
            if (mail.getItem() instanceof com.flechazo.contact.common.item.PostcardItem) cards += mail.getCount();
            for (ItemStack content : mail.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).stream().toList()) {
                if (content.is(Items.APPLE)) apples += content.getCount();
                if (content.is(Items.DIAMOND)) diamonds += content.getCount();
                if (content.is(Items.EMERALD)) emeralds += content.getCount();
            }
        }
        helper.assertTrue(apples == 2 && diamonds == 8 && emeralds == 3 && cards == 1,
                "Claim twice changed delivered quantities: apples=" + apples + ", diamonds=" + diamonds
                        + ", emeralds=" + emeralds + ", postcards=" + cards);
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void editedNpcRefreshesWithoutRestart(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        String target = "CQNewNpc_" + fixture.quest.getId();
        ParcelTask task = parcelTask(fixture, 1, target, 0);
        helper.assertTrue(DataManager.INSTANCE.getAvailableTargets(fixture.player).containsKey(target),
                "New NPC did not become available");
        task.setTargetAddressee(target + "Edited");
        fixture.file.markDirty();
        var available = DataManager.INSTANCE.getAvailableTargets(fixture.player);
        helper.assertTrue(available.containsKey(target + "Edited") && !available.containsKey(target),
                "Edited NPC index is stale");
        helper.succeed();
    }

    private static ItemStack mixedParcel() {
        SimpleContainer contents = new SimpleContainer(4);
        contents.setItem(0, new ItemStack(Items.DIAMOND, 3));
        contents.setItem(1, new ItemStack(Items.DIRT, 2));
        return ParcelItem.getParcel(contents, false, "CQRegression");
    }

    private static void send(Fixture fixture, String target, ItemStack parcel) {
        DataManager.INSTANCE.matchParcelTaskItem(fixture.player, parcel,
                parcel.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY), target);
    }

    private static void assertStash(GameTestHelper helper, Fixture fixture, String target, int diamonds, int dirt) {
        var items = CollectionSavedData.Companion.get(fixture.level).getStacks(fixture.player.getUUID(), target);
        int actualDiamonds = items.stream().filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();
        int actualDirt = items.stream().filter(s -> s.is(Items.DIRT)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(actualDiamonds == diamonds && actualDirt == dirt,
                "Remainder differs: diamonds=" + actualDiamonds + ", dirt=" + actualDirt);
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void mixedParcelPreservesTaskAndNonTaskQuantities(GameTestHelper helper) {
        for (int delay : new int[]{0, 2}) {
            Fixture fixture = fixture(helper);
            String target = "CQMixed_" + fixture.quest.getId();
            ParcelTask task = parcelTask(fixture, 2, target, delay);
            // With no matching reply rule the existing policy stashes the remainder.
            NpcConfigManager.INSTANCE.getNpcData(target).getErrorSolve().clear();
            ItemStack mail = mixedParcel();
            send(fixture, target, mail);
            progress(helper, fixture, task, delay == 0 ? 2 : 0);
            assertStash(helper, fixture, target, 1, 2);
            helper.assertTrue(mail.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                    .stream().filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum() == 3,
                    "Matching mutated the original mail component");
            tick(fixture);
            progress(helper, fixture, task, 2);
            helper.assertTrue(DataManager.INSTANCE.acceptsNpcMail(fixture.player, target),
                    "Completed task NPC stopped accepting ordinary mail");
            SimpleContainer ordinary = new SimpleContainer(4);
            ordinary.setItem(0, new ItemStack(Items.DIRT, 1));
            send(fixture, target, ParcelItem.getParcel(ordinary, false, "CQRegression"));
            progress(helper, fixture, task, 2);
            assertStash(helper, fixture, target, 1, 3);
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void multipleTasksPreferLargestPercentageIncrease(GameTestHelper helper) {
        for (int delay : new int[]{0, 2}) {
            Fixture fixture = fixture(helper);
            String target = "CQPriority_" + fixture.quest.getId();
            ParcelTask large = new ParcelTask(fixture.file.newID(), fixture.quest)
                    .setStackAndCount(new ItemStack(Items.DIAMOND), 10);
            ParcelTask small = new ParcelTask(fixture.file.newID(), fixture.quest)
                    .setStackAndCount(new ItemStack(Items.DIAMOND), 2);
            large.setTargetAddressee(target);
            small.setTargetAddressee(target);
            fixture.quest.addTask(large); // Intentionally register the lower-priority task first.
            fixture.quest.addTask(small);
            index(fixture, target, delay);
            NpcConfigManager.INSTANCE.getNpcData(target).getErrorSolve().clear();
            // Split the same item over two input stacks: score the entire mail.
            SimpleContainer contents = new SimpleContainer(4);
            contents.setItem(0, new ItemStack(Items.DIAMOND, 1));
            contents.setItem(1, new ItemStack(Items.DIAMOND, 2));
            contents.setItem(2, new ItemStack(Items.DIRT, 2));
            send(fixture, target, ParcelItem.getParcel(contents, false, "CQRegression"));
            if (delay != 0) {
                helper.assertTrue(TaskDeliverySavedData.Companion.get(fixture.level)
                        .getPendingAmount(fixture.team.getId(), small.getId()) == 2,
                        "Higher percentage task did not reserve two items");
            }
            tick(fixture);
            progress(helper, fixture, small, 2);
            progress(helper, fixture, large, 1);
            assertStash(helper, fixture, target, 0, 2);
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void percentageIncreaseUsesTotalCountAndStableTies(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        String target = "CQIncrease_" + fixture.quest.getId();
        ParcelTask nearlyDone = new ParcelTask(fixture.file.newID(), fixture.quest)
                .setStackAndCount(new ItemStack(Items.DIAMOND), 100);
        ParcelTask empty = new ParcelTask(fixture.file.newID(), fixture.quest)
                .setStackAndCount(new ItemStack(Items.DIAMOND), 5);
        nearlyDone.setTargetAddressee(target);
        empty.setTargetAddressee(target);
        fixture.quest.addTask(nearlyDone);
        fixture.quest.addTask(empty);
        index(fixture, target, 0);
        fixture.data.setProgress(nearlyDone, 90);
        helper.assertTrue(sendParcel(fixture, target, 2), "Percentage priority parcel was rejected");
        progress(helper, fixture, nearlyDone, 90);
        progress(helper, fixture, empty, 2);

        String tieTarget = target + "Tie";
        ParcelTask first = parcelTask(fixture, 2, tieTarget, 0);
        ParcelTask second = parcelTask(fixture, 2, tieTarget, 0);
        helper.assertTrue(sendParcel(fixture, tieTarget, 1), "Tie parcel was rejected");
        ParcelTask lowerId = Long.compareUnsigned(first.getId(), second.getId()) < 0 ? first : second;
        ParcelTask higherId = lowerId == first ? second : first;
        progress(helper, fixture, lowerId, 1);
        progress(helper, fixture, higherId, 0);
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void taskNpcAndScriptShareMixedMail(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        String target = "CQScriptMixed_" + fixture.quest.getId();
        ParcelTask task = parcelTask(fixture, 2, target, 0);
        // A script NPC entry must not bypass its FTB task.
        KubeJSNpcSavedData.Companion.get(fixture.level).setNpcData(target, 0, true);
        send(fixture, target, mixedParcel());
        progress(helper, fixture, task, 2);
        helper.assertTrue(ContactQuestsTestServerController.isCasePassed("kubejs.mixed." + target),
                "KubeJS did not see exactly diamond x1 and dirt x2 after task allocation");
        assertStash(helper, fixture, target, 0, 0); // Script intercept owns only the remainder.
        helper.assertTrue(CollectionSavedData.Companion.get(fixture.level)
                .getTriggerCount(fixture.player.getUUID(), target) == 0,
                "Ordinary mail accepted by a script was counted as a mistake");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void tagDependencyAnyAndAllTruthTable(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        IQuestExtension quest = (IQuestExtension) (Object) fixture.quest;
        ITeamDataExtension team = (ITeamDataExtension) fixture.data;
        for (String mode : new String[]{"ANY", "ALL"}) {
            CompoundTag nbt = new CompoundTag();
            fixture.quest.writeData(nbt, fixture.level.registryAccess());
            ListTag tags = new ListTag();
            tags.add(StringTag.valueOf("CQTruthA"));
            tags.add(StringTag.valueOf("CQTruthB"));
            nbt.put("cq_req_tags", tags);
            nbt.putString("cq_tag_mode", mode);
            fixture.quest.readData(nbt, fixture.level.registryAccess());
            team.contactQuests$removeTag("CQTruthA");
            team.contactQuests$removeTag("CQTruthB");
            helper.assertTrue(quest.contactQuests$areTagsMet(fixture.data), mode + " allowed no tags");
            team.contactQuests$unlockTag("CQTruthA");
            helper.assertTrue(quest.contactQuests$areTagsMet(fixture.data) == mode.equals("ALL"),
                    mode + " returned the wrong block flag for one tag");
            team.contactQuests$unlockTag("CQTruthB");
            helper.assertTrue(!quest.contactQuests$areTagsMet(fixture.data), mode + " blocked both tags");
        }
        helper.succeed();
    }
}
