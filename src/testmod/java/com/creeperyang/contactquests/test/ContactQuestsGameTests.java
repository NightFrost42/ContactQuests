package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.compat.kubejs.KubeJSNpcSavedData;
import com.creeperyang.contactquests.compat.kubejs.KubeJSMailQueueSavedData;
import com.creeperyang.contactquests.compat.kubejs.QuestOverrideSavedData;
import com.creeperyang.contactquests.data.CollectionSavedData;
import com.creeperyang.contactquests.data.DataManager;
import com.creeperyang.contactquests.config.NpcConfigManager;
import com.creeperyang.contactquests.quest.reward.ParcelAllTableReward;
import com.creeperyang.contactquests.quest.reward.ParcelRandomReward;
import com.creeperyang.contactquests.quest.reward.ParcelReward;
import com.creeperyang.contactquests.quest.reward.PostcardReward;
import com.creeperyang.contactquests.quest.reward.RedPacketReward;
import com.creeperyang.contactquests.quest.task.ParcelTask;
import com.creeperyang.contactquests.quest.task.PostcardTask;
import com.creeperyang.contactquests.quest.task.RedPacketTask;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import com.flechazo.contact.common.item.ParcelItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.UUID;

@GameTestHolder(ContactQuestsTestMod.MOD_ID)
public final class ContactQuestsGameTests {
    private ContactQuestsGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void npcConfigurationRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        KubeJSNpcSavedData data = KubeJSNpcSavedData.Companion.get(level);
        data.setNpcData("ContactQuestsTestNpc", 20, false);
        data = ContactQuestsTestUtil.roundTrip(data, level.registryAccess());

        helper.assertTrue(data.getNpcData("ContactQuestsTestNpc") != null,
                "NPC configuration was not created");
        helper.assertTrue(data.getNpcData("ContactQuestsTestNpc").getDeliveryTime() == 20,
                "NPC delivery time was not stored");
        helper.assertTrue(!data.getNpcData("ContactQuestsTestNpc").getAutoFill(),
                "NPC autoFill flag was not stored");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void noTaskNpcAcceptsDelayedParcel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        KubeJSNpcSavedData.Companion.get(level).setNpcData("ContactQuestsNoTask", 20, false);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SimpleContainer contents = new SimpleContainer(4);
        contents.setItem(0, new ItemStack(Items.DIAMOND));
        ItemStack parcel = ParcelItem.getParcel(contents, false, "ContactQuestsTest");

        KubeJSMailQueueSavedData queue = KubeJSMailQueueSavedData.Companion.get(level);
        queue.addMail(player, "ContactQuestsNoTask", java.util.List.of(parcel), 20, false, false);
        CompoundTag saved = new CompoundTag();
        queue.save(saved, level.registryAccess());
        helper.assertTrue(
                saved.getList("PendingMails", Tag.TAG_COMPOUND).size() > 0,
                "No-task delayed parcel was not queued"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void noTaskNpcAcceptsDelayedMailVariants(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        KubeJSNpcSavedData.Companion.get(level).setNpcData("ContactQuestsNoTaskVariants", 20, false);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        KubeJSMailQueueSavedData queue = KubeJSMailQueueSavedData.Companion.get(level);

        queue.addMail(player, "ContactQuestsNoTaskVariants",
                java.util.List.of(new ItemStack(Items.GOLD_INGOT)), 20, false, true);
        queue.addMail(player, "ContactQuestsNoTaskVariants",
                java.util.List.of(new ItemStack(Items.PAPER)), 20, true, false);

        CompoundTag saved = new CompoundTag();
        queue.save(saved, level.registryAccess());
        helper.assertTrue(
                saved.getList("PendingMails", Tag.TAG_COMPOUND).size() >= 2,
                "No-task mail variants were not queued"
        );
        helper.succeed();
    }


    @GameTest(template = "empty", timeoutTicks = 100)
    public static void collectionStashRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CollectionSavedData data = CollectionSavedData.Companion.get(level);
        UUID playerId = UUID.randomUUID();
        data.setStacks(playerId, "ContactQuestsTestNpc", java.util.List.of(new ItemStack(Items.APPLE, 3)));
        data.setTriggerCount(playerId, "ContactQuestsTestNpc", 7);
        data = ContactQuestsTestUtil.roundTrip(data, level.registryAccess());

        var stacks = data.getStacks(playerId, "ContactQuestsTestNpc");
        helper.assertTrue(!stacks.isEmpty(), "Collection stash was not written");
        helper.assertTrue(stacks.get(0).is(Items.APPLE), "Collection stash item type changed");
        helper.assertTrue(stacks.get(0).getCount() == 3, "Collection stash item count changed");
        helper.assertTrue(data.getTriggerCount(playerId, "ContactQuestsTestNpc") == 7,
                "Collection trigger count did not survive NBT reload");
        helper.succeed();
    }

    public static void prepareScriptGraph(ServerLevel graphLevel) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        Chapter chapter = new Chapter(
                file.newID(),
                file,
                file.getDefaultChapterGroup(),
                "contactquests_test_runtime"
        );
        file.getDefaultChapterGroup().addChapter(chapter);

        Quest quest = new Quest(file.newID(), chapter);
        quest.setRawTitle("ContactQuests deterministic runtime graph");
        chapter.addQuest(quest);

        ParcelTask parcelTask = new ParcelTask(file.newID(), quest)
                .setStackAndCount(new ItemStack(Items.DIAMOND), 1);
        parcelTask.setTargetAddressee("CQTest_AutoParcel");
        quest.addTask(parcelTask);

        RedPacketTask redPacketTask = new RedPacketTask(file.newID(), quest)
                .setStackAndCount(new ItemStack(Items.GOLD_INGOT), 1);
        redPacketTask.setBlessing("fixed graph blessing");
        redPacketTask.setTargetAddressee("CQTest_AutoRedPacket");
        quest.addTask(redPacketTask);

        PostcardTask postcardTask = new PostcardTask(file.newID(), quest);
        postcardTask.setPostcardStyle("contact:default");
        postcardTask.setPostcardText("fixed graph postcard");
        postcardTask.setTargetAddressee("CQTest_AutoPostcard");
        quest.addTask(postcardTask);

        ParcelReward parcelReward = new ParcelReward(file.newID(), quest);
        parcelReward.setItem(new ItemStack(Items.APPLE));
        parcelReward.setCount(1);
        parcelReward.setTargetAddressee("CQTest_AutoParcel");
        quest.addReward(parcelReward);

        RedPacketReward redPacketReward = new RedPacketReward(file.newID(), quest);
        redPacketReward.setItem(new ItemStack(Items.EMERALD));
        redPacketReward.setCount(1);
        redPacketReward.setBlessing("fixed graph reward");
        redPacketReward.setTargetAddressee("CQTest_AutoRedPacket");
        quest.addReward(redPacketReward);

        PostcardReward postcardReward = new PostcardReward(file.newID(), quest);
        postcardReward.setPostcardStyle("contact:default");
        postcardReward.setPostcardText("fixed graph reward postcard");
        postcardReward.setTargetAddressee("CQTest_AutoPostcard");
        quest.addReward(postcardReward);

        ParcelRandomReward randomReward = new ParcelRandomReward(file.newID(), quest);
        randomReward.setTargetAddressee("CQTest_AutoParcel");
        quest.addReward(randomReward);

        ParcelAllTableReward allTableReward = new ParcelAllTableReward(file.newID(), quest);
        allTableReward.setTargetAddressee("CQTest_AutoParcel");
        quest.addReward(allTableReward);

        ContactQuestsTestUtil.refreshIndex(file);
        ContactQuestsTestServerController.setRuntimeGraphIds(
                quest.getId(),
                parcelTask.getId(),
                redPacketTask.getId(),
                postcardTask.getId(),
                parcelReward.getId(),
                redPacketReward.getId(),
                postcardReward.getId()
        );
        DataManager.INSTANCE.init();

    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void temporaryQuestGraphRegistersAllContactTypes(GameTestHelper helper) {
        ServerLevel graphLevel = helper.getLevel();
        prepareScriptGraph(graphLevel);
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        Quest quest = file.getQuest(ContactQuestsTestServerController.getRuntimeQuestId());
        ParcelTask parcelTask = (ParcelTask) file.getTask(ContactQuestsTestServerController.getRuntimeParcelTaskId());
        RedPacketTask redPacketTask = (RedPacketTask) file.getTask(ContactQuestsTestServerController.getRuntimeRedPacketTaskId());
        PostcardTask postcardTask = (PostcardTask) file.getTask(ContactQuestsTestServerController.getRuntimePostcardTaskId());
        ParcelReward parcelReward = (ParcelReward) file.getReward(ContactQuestsTestServerController.getRuntimeParcelRewardId());
        RedPacketReward redPacketReward = (RedPacketReward) file.getReward(ContactQuestsTestServerController.getRuntimeRedPacketRewardId());
        PostcardReward postcardReward = (PostcardReward) file.getReward(ContactQuestsTestServerController.getRuntimePostcardRewardId());
        helper.assertTrue(DataManager.INSTANCE.getParcelTasks().containsKey(parcelTask.getId()),
                "Parcel task was not indexed");
        helper.assertTrue(DataManager.INSTANCE.getRedPacketTasks().containsKey(redPacketTask.getId()),
                "Red packet task was not indexed");
        helper.assertTrue(DataManager.INSTANCE.getPostcardTasks().containsKey(postcardTask.getId()),
                "Postcard task was not indexed");
        helper.assertTrue(NpcConfigManager.INSTANCE.getAllNpcNames().contains("CQTest_AutoParcel"),
                "Parcel NPC was not auto-generated in npc_config");
        helper.assertTrue(NpcConfigManager.INSTANCE.getAllNpcNames().contains("CQTest_AutoRedPacket"),
                "Red packet NPC was not auto-generated in npc_config");
        helper.assertTrue(NpcConfigManager.INSTANCE.getAllNpcNames().contains("CQTest_AutoPostcard"),
                "Postcard NPC was not auto-generated in npc_config");
        ContactQuestsTestServerController.recordCase("server.npc_config.auto_generation", true,
                "All three task NPC names exist in the generated config");
        helper.succeedWhen(() -> {
            helper.assertTrue(parcelTask.getTargetAddressee().equals("CQTest_SavedParcel"),
                    "Valid parcel task edit was not applied");
            helper.assertTrue(redPacketTask.getBlessing().equals("saved graph blessing"),
                    "Valid red packet task edit was not applied");
            helper.assertTrue(postcardTask.getPostcardText().equals("saved graph postcard"),
                    "Valid postcard task edit was not applied");
            helper.assertTrue(parcelReward.getTargetAddressee().equals("CQTest_SavedParcel"),
                    "Valid parcel reward edit was not applied");
            helper.assertTrue(redPacketReward.getBlessing().equals("saved graph reward"),
                    "Valid red packet reward edit was not applied");
            helper.assertTrue(postcardReward.getPostcardText().equals("saved graph reward postcard"),
                    "Valid postcard reward edit was not applied");
            CompoundTag questOverride = QuestOverrideSavedData.Companion.get(graphLevel).getOverride(quest.getId());
            helper.assertTrue(questOverride != null
                            && questOverride.getCompound("title").getString("zh_cn").equals("valid graph title"),
                    "Valid quest title override was not persisted");
            ContactQuestsScriptAssertions.verifyEditedGraph(graphLevel);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void savedDataUsesServerOverworld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        KubeJSNpcSavedData fromCurrentLevel = KubeJSNpcSavedData.Companion.get(level);
        KubeJSNpcSavedData fromOverworld = KubeJSNpcSavedData.Companion.get(level.getServer().overworld());

        helper.assertTrue(fromCurrentLevel == fromOverworld,
                "KubeJS NPC SavedData is not server-global");
        ServerLevel nether = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        helper.assertTrue(nether != null, "Nether test level is missing");
        helper.assertTrue(KubeJSNpcSavedData.Companion.get(nether) == fromOverworld,
                "NPC config splits across dimensions");
        helper.assertTrue(KubeJSMailQueueSavedData.Companion.get(nether)
                == KubeJSMailQueueSavedData.Companion.get(level), "Script mail queue splits across dimensions");
        helper.assertTrue(com.creeperyang.contactquests.data.TaskDeliverySavedData.Companion.get(nether)
                == com.creeperyang.contactquests.data.TaskDeliverySavedData.Companion.get(level),
                "Task delivery queue splits across dimensions");
        helper.assertTrue(CollectionSavedData.Companion.get(nether)
                == CollectionSavedData.Companion.get(level), "NPC stash splits across dimensions");
        helper.succeed();
    }
}
