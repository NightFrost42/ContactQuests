package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.compat.kubejs.ContactKubeJSPlugin;
import com.creeperyang.contactquests.compat.kubejs.KubeJSMailQueueSavedData;
import com.creeperyang.contactquests.compat.kubejs.KubeJSNpcSavedData;
import com.creeperyang.contactquests.config.*;
import com.creeperyang.contactquests.data.*;
import com.creeperyang.contactquests.item.TeamBindingItem;
import com.creeperyang.contactquests.llm.NpcLlmConfig;
import com.creeperyang.contactquests.llm.NpcLlmSavedData;
import com.creeperyang.contactquests.llm.NpcLlmService;
import com.creeperyang.contactquests.mixin.ItemRewardAccessor;
import com.creeperyang.contactquests.quest.reward.*;
import com.creeperyang.contactquests.quest.task.PostcardTask;
import com.creeperyang.contactquests.quest.task.RedPacketTask;
import com.creeperyang.contactquests.registry.ModItems;
import com.creeperyang.contactquests.bridge.ITeamDataExtension;
import com.flechazo.contact.common.block.MailboxBlock;
import com.flechazo.contact.common.component.ContactDataComponents;
import com.flechazo.contact.common.item.ParcelItem;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.common.item.RedPacketItem;
import com.flechazo.contact.common.registry.BlockRegistry;
import com.flechazo.contact.data.PostcardDataManager;
import com.flechazo.contact.platform.PlatformHelper;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbquests.quest.*;
import dev.ftb.mods.ftbquests.quest.loot.*;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@GameTestHolder(ContactQuestsTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContactQuestsExtendedGameTests {
    private ContactQuestsExtendedGameTests() {}

    private static ServerPlayer player(ServerLevel level, String prefix) {
        String tag = prefix + UUID.randomUUID().toString().replace("-", "");
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), tag.substring(0, Math.min(16, tag.length()))));
        ((TeamManagerImpl) FTBTeamsAPI.api().getManager()).playerLoggedIn(null, player.getUUID(), player.getGameProfile().getName());
        return player;
    }

    private static PartyTeam party(ServerPlayer owner, ServerPlayer... members) throws Exception {
        TeamManagerImpl manager = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        PartyTeam party = (PartyTeam) manager.createPartyTeam(owner,
                "CQ" + owner.getUUID().toString().substring(0, 8), "extended feature team", Color4I.WHITE);
        for (ServerPlayer member : members) {
            party.invite(owner, List.of(member.getGameProfile()));
            party.join(member);
        }
        return party;
    }

    private static BlockPos bindMailbox(GameTestHelper helper, ServerPlayer player, int xOffset) {
        ServerLevel level = helper.getLevel();
        BlockPos lower = helper.absolutePos(new BlockPos(xOffset, 2, 2));
        level.setBlock(lower.below(), Blocks.STONE.defaultBlockState(), 3);
        var state = BlockRegistry.GREEN_MAILBOX.get().defaultBlockState();
        level.setBlock(lower, state.setValue(MailboxBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(lower.above(), state.setValue(MailboxBlock.HALF, DoubleBlockHalf.UPPER), 3);
        BlockPos upper = lower.above();
        var binder = (TeamBindingItem) ModItems.INSTANCE.getTEAM_BINDING_CARD().get();
        boolean wasSneaking = player.isShiftKeyDown();
        ItemStack previous = player.getMainHandItem().copy();
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(binder));
        InteractionResult result = binder.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(upper), Direction.UP, upper, false)));
        player.setShiftKeyDown(wasSneaking);
        player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        if (!result.consumesAction()) {
            throw new IllegalStateException("Failed to bind mailbox at " + upper);
        }
        return upper;
    }

    private static void flushAndDeliver(ServerLevel level, int ticks) {
        RewardDistributionManager.INSTANCE.flush(level);
        for (int i = 0; i < ticks; i++) {
            RewardDistributionManager.INSTANCE.onServerTick(level);
        }
    }

    private static List<ItemStack> rawMail(UUID owner) {
        var box = PlatformHelper.getMailboxContents(owner);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack s = box.getItem(i);
            if (!s.isEmpty()) items.add(s.copy());
        }
        return items;
    }

    private static int mailItemCount(UUID owner, Item item) {
        int sum = 0;
        for (ItemStack mail : rawMail(owner)) {
            if (mail.is(item)) sum += mail.getCount();
            for (ItemStack inside : mail.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems()) {
                if (inside.is(item)) sum += inside.getCount();
            }
        }
        return sum;
    }

    private static List<ItemStack> innerStacks(UUID owner) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack mail : rawMail(owner)) {
            var contents = mail.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            for (ItemStack inside : contents.nonEmptyItems()) result.add(inside.copy());
        }
        return result;
    }

    private static void cleanupMailbox(UUID ownerId) {
        PlatformHelper.resetMailboxContents(ownerId);
        var pos = PlatformHelper.getMailboxPos(ownerId);
        if (pos != null) PlatformHelper.removeMailboxData(pos);
    }

    private static NpcLlmConfig.Npc mockNpc(boolean autoFill) {
        return new NpcLlmConfig.Npc(true, "mock", "courier persona", "player", 12,
                "", "", List.of(), "", "ext", 4, 12000, autoFill);
    }

    private static void removeChapter(Chapter chapter) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        if (file == null || chapter == null) return;
        if (chapter.getGroup() != null) chapter.getGroup().removeChapter(chapter);
        ContactQuestsTestUtil.refreshIndex(file);
        file.clearCachedProgress();
        DataManager.targetsDirty = true;
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void teamRewardMultiplierAndOversizedStackSplitting(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(level, "MultA");
        ServerPlayer m2 = player(level, "MultB");
        ServerPlayer m3 = player(level, "MultC");
        PartyTeam team = party(owner, m2, m3);
        bindMailbox(helper, owner, 2);
        UUID ownerId = team.getId();
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        Chapter chapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(), "cq_ext_mult_" + owner.getUUID());
        file.getDefaultChapterGroup().addChapter(chapter);
        Quest quest = new Quest(file.newID(), chapter);
        chapter.addQuest(quest);
        quest.addTask(new CheckmarkTask(file.newID(), quest));
        RewardTable singleDiamond = new RewardTable(file.newID(), file);
        singleDiamond.addReward(new WeightedReward(new ItemReward(file.newID(), singleDiamond.getFakeQuest(), new ItemStack(Items.DIAMOND), 4), 1));
        file.addRewardTable(singleDiamond);
        RewardTable allItems = new RewardTable(file.newID(), file);
        allItems.addReward(new WeightedReward(new ItemReward(file.newID(), allItems.getFakeQuest(), new ItemStack(Items.EMERALD), 2), 1));
        allItems.addReward(new WeightedReward(new ItemReward(file.newID(), allItems.getFakeQuest(), new ItemStack(Items.COPPER_INGOT), 5), 1));
        file.addRewardTable(allItems);

        try {
            TeamData data = file.getOrCreateTeamData(team);
            data.setLocked(false);

            ParcelReward diamondParcel = new ParcelReward(file.newID(), quest);
            diamondParcel.setItem(new ItemStack(Items.DIAMOND));
            diamondParcel.setCount(50);
            diamondParcel.setEnableTeamMultiplier(true);
            diamondParcel.setTeamRewardMultiplier(1.0f);
            diamondParcel.setTargetAddressee("CQMultNpc");
            quest.addReward(diamondParcel);

            ParcelReward fracParcel = new ParcelReward(file.newID(), quest);
            fracParcel.setItem(new ItemStack(Items.EMERALD));
            fracParcel.setCount(3);
            fracParcel.setEnableTeamMultiplier(true);
            fracParcel.setTeamRewardMultiplier(1.5f);
            fracParcel.setTargetAddressee("CQMultNpc");
            quest.addReward(fracParcel);

            ParcelReward pearlParcel = new ParcelReward(file.newID(), quest);
            pearlParcel.setItem(new ItemStack(Items.ENDER_PEARL));
            pearlParcel.setCount(10);
            pearlParcel.setEnableTeamMultiplier(true);
            pearlParcel.setTeamRewardMultiplier(1.0f);
            pearlParcel.setTargetAddressee("CQMultNpc");
            quest.addReward(pearlParcel);

            RedPacketReward redPacket = new RedPacketReward(file.newID(), quest);
            redPacket.setItem(new ItemStack(Items.GOLD_INGOT));
            redPacket.setCount(40);
            redPacket.setEnableTeamMultiplier(true);
            redPacket.setTeamRewardMultiplier(1.0f);
            redPacket.setTargetAddressee("CQMultNpc");
            quest.addReward(redPacket);

            ParcelRandomReward randomReward = new ParcelRandomReward(file.newID(), quest);
            randomReward.setTable(singleDiamond);
            randomReward.setEnableTeamMultiplier(true);
            randomReward.setTeamRewardMultiplier(1.0f);
            randomReward.setTargetAddressee("CQMultNpc");
            quest.addReward(randomReward);

            ParcelAllTableReward allReward = new ParcelAllTableReward(file.newID(), quest);
            allReward.setTable(allItems);
            allReward.setEnableTeamMultiplier(true);
            allReward.setTeamRewardMultiplier(1.0f);
            allReward.setTargetAddressee("CQMultNpc");
            quest.addReward(allReward);

            ParcelChoiceReward choiceReward = new ParcelChoiceReward(file.newID(), quest);
            choiceReward.setTable(singleDiamond);
            choiceReward.setEnableTeamMultiplier(true);
            choiceReward.setTeamRewardMultiplier(1.0f);
            choiceReward.setTargetAddressee("CQMultNpc");
            quest.addReward(choiceReward);

            ContactQuestsTestUtil.refreshIndex(file);
            data.setCompleted(quest.id, new Date());

            // 1. Parcel oversized (50 * 3 = 150 -> 64+64+22), fractional (int(3*3*1.5) = 13), max16 (10*3 = 30 -> 16+14)
            PlatformHelper.resetMailboxContents(ownerId);
            helper.assertTrue(diamondParcel.claim(owner, false) && fracParcel.claim(owner, false) && pearlParcel.claim(owner, false),
                    "Multiplied parcel rewards failed to claim");
            RewardDistributionManager.INSTANCE.flush(level);
            RewardDeliverySavedData.Companion.get(level).save(new CompoundTag(), level.registryAccess());
            flushAndDeliver(level, 3);
            helper.assertTrue(mailItemCount(ownerId, Items.DIAMOND) == 150
                            && mailItemCount(ownerId, Items.EMERALD) == 13
                            && mailItemCount(ownerId, Items.ENDER_PEARL) == 30,
                    "Parcel multiplier/fractional counts wrong: diamonds=" + mailItemCount(ownerId, Items.DIAMOND)
                            + ", emeralds=" + mailItemCount(ownerId, Items.EMERALD)
                            + ", pearls=" + mailItemCount(ownerId, Items.ENDER_PEARL));
            helper.assertTrue(innerStacks(ownerId).stream().allMatch(s -> s.getCount() <= s.getMaxStackSize()),
                    "Split stack inside delivered parcel exceeded maxStackSize");

            // 2. RedPacket oversized (40 * 3 = 120 -> 2 packets: 64 + 56)
            PlatformHelper.resetMailboxContents(ownerId);
            helper.assertTrue(redPacket.claim(owner, false), "Multiplied RedPacket claim failed");
            RewardDistributionManager.INSTANCE.flush(level);
            RewardDeliverySavedData.Companion.get(level).save(new CompoundTag(), level.registryAccess());
            flushAndDeliver(level, 3);
            List<ItemStack> packets = rawMail(ownerId).stream().filter(s -> s.getItem() instanceof RedPacketItem).toList();
            helper.assertTrue(packets.size() == 2 && mailItemCount(ownerId, Items.GOLD_INGOT) == 120
                            && innerStacks(ownerId).stream().allMatch(s -> s.getCount() <= 64),
                    "Multiplied RedPacket did not split 120 gold into 2 valid packets: packets=" + packets.size());

            // 3. ParcelRandomReward (4 * 3 = 12) + ParcelAllTableReward (emerald 6, copper 15)
            PlatformHelper.resetMailboxContents(ownerId);
            helper.assertTrue(randomReward.claim(owner, false) && allReward.claim(owner, false),
                    "Random/AllTable multiplied rewards failed to claim");
            flushAndDeliver(level, 3);
            helper.assertTrue(mailItemCount(ownerId, Items.DIAMOND) == 12
                            && mailItemCount(ownerId, Items.EMERALD) == 6
                            && mailItemCount(ownerId, Items.COPPER_INGOT) == 15,
                    "Random/AllTable team multiplier counts incorrect");

            // 4. ParcelChoiceReward (4 * 3 = 12) via real ChoiceRewardSavedData flow
            PlatformHelper.resetMailboxContents(ownerId);
            var choiceStore = ChoiceRewardSavedData.Companion.get(level);
            var req = choiceStore.request(owner, choiceReward, 0);
            owner.getInventory().setItem(0, PostcardItem.getPostcard(ResourceLocation.parse("contact:default"), false));
            choiceStore.prepareCard(owner, choiceReward.id, 0);
            ItemStack kept = DataManager.INSTANCE.receiveChoiceCard(owner, owner.getInventory().getItem(0).copy(), choiceReward.getClaimRecipient());
            flushAndDeliver(level, 3);
            helper.assertTrue(kept.isEmpty() && data.isRewardClaimed(owner.getUUID(), choiceReward)
                            && mailItemCount(ownerId, Items.DIAMOND) == 12,
                    "Multiplied ParcelChoiceReward did not deliver 12 diamonds");
        } finally {
            cleanupMailbox(ownerId);
            file.removeRewardTable(singleDiamond);
            file.removeRewardTable(allItems);
            removeChapter(chapter);
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void rewardPlaceholdersResolveTeamSizeTeamNameAndPlayer(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(level, "RewTxtA");
        ServerPlayer member = player(level, "RewTxtB");
        PartyTeam team = party(owner, member);
        bindMailbox(helper, owner, 3);
        UUID ownerId = team.getId();
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        Chapter chapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(), "cq_ext_rewtxt_" + owner.getUUID());
        file.getDefaultChapterGroup().addChapter(chapter);
        Quest quest = new Quest(file.newID(), chapter);
        chapter.addQuest(quest);

        try {
            PostcardReward postcardReward = new PostcardReward(file.newID(), quest);
            postcardReward.setPostcardStyle("contact:default");
            postcardReward.setPostcardText("<player_name>|<team_name>|<team_size>");
            postcardReward.setTargetAddressee("CQRewardTxtNpc");
            quest.addReward(postcardReward);

            RedPacketReward redPacketReward = new RedPacketReward(file.newID(), quest);
            redPacketReward.setItem(new ItemStack(Items.GOLD_NUGGET));
            redPacketReward.setCount(1);
            redPacketReward.setBlessing("<player_name>|<team_name>|<team_size>");
            redPacketReward.setTargetAddressee("CQRewardTxtNpc");
            quest.addReward(redPacketReward);

            PlatformHelper.resetMailboxContents(ownerId);
            helper.assertTrue(postcardReward.claim(owner, false) && redPacketReward.claim(owner, false),
                    "Placeholder rewards failed to claim");
            flushAndDeliver(level, 3);

            String expected = owner.getGameProfile().getName() + "|" + team.getName().getString() + "|2";
            List<ItemStack> cards = rawMail(ownerId).stream().filter(s -> s.getItem() instanceof PostcardItem).toList();
            List<ItemStack> packets = rawMail(ownerId).stream().filter(s -> s.getItem() instanceof RedPacketItem).toList();
            helper.assertTrue(cards.size() == 1 && PostcardItem.getText(cards.getFirst()).equals(expected + "|postcard"),
                    "PostcardReward placeholder text mismatch: " + (cards.isEmpty() ? "missing" : PostcardItem.getText(cards.getFirst())));
            helper.assertTrue(packets.size() == 1
                            && (expected + "|red_packet").equals(packets.getFirst().get(ContactDataComponents.RED_PACKET_BLESSING.get())),
                    "RedPacketReward placeholder blessing mismatch: "
                            + (packets.isEmpty() ? "missing" : packets.getFirst().get(ContactDataComponents.RED_PACKET_BLESSING.get())));
        } finally {
            cleanupMailbox(ownerId);
            removeChapter(chapter);
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void wrongMailDiscardWithRewardsThresholdsAndStyles(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(level, "WrMail");
        bindMailbox(helper, player, 4);
        CollectionSavedData collection = CollectionSavedData.Companion.get(level);
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        Chapter chapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(), "cq_ext_wrong_" + player.getUUID());
        file.getDefaultChapterGroup().addChapter(chapter);
        Quest quest = new Quest(file.newID(), chapter);
        chapter.addQuest(quest);

        String npcDiscard = "CQDisc_" + player.getUUID();
        String npcWr = "CQWithRew_" + player.getUUID();
        String npcFallback = "CQSameFb_" + player.getUUID();

        try {
            NpcConfigManager.INSTANCE.syncWithQuests(Set.of(npcDiscard, npcWr, npcFallback), Set.of(), Set.of(), Set.of());
            // Part A: DISCARD uses incoming items rather than dirty pre-stashed apples
            NpcData dData = NpcConfigManager.INSTANCE.getNpcData(npcDiscard);
            ErrorSolveData dRule = new ErrorSolveData();
            dRule.setCount(1);
            dRule.setReturnType(ErrorSolveType.DISCARD);
            dRule.setStyleType(StyleType.SPECIFIC);
            dRule.setStyle("contact:creeper");
            dRule.setMessage(new ArrayList<>(List.of(new MessageData(
                    "disc:<item_1_name>:<item_1_count>/<total_count>#<trigger_count>", "contact:creeper", false, 1))));
            dData.getErrorSolve().clear();
            dData.getErrorSolve().add(dRule);

            collection.addItemsSilent(player, npcDiscard, List.of(new ItemStack(Items.APPLE, 9)));
            PlatformHelper.resetMailboxContents(player.getUUID());
            collection.addItems(player, npcDiscard, List.of(new ItemStack(Items.DIRT, 4)));
            flushAndDeliver(level, 3);
            List<ItemStack> discCards = rawMail(player.getUUID()).stream().filter(s -> s.getItem() instanceof PostcardItem).toList();
            String expectedDisc = "disc:" + new ItemStack(Items.DIRT).getHoverName().getString() + ":4/4#1|npc_reply";
            helper.assertTrue(collection.getTriggerCount(player.getUUID(), npcDiscard) == 1
                            && mailItemCount(player.getUUID(), Items.DIRT) == 0
                            && mailItemCount(player.getUUID(), Items.APPLE) == 0
                            && discCards.size() == 1 && PostcardItem.getText(discCards.getFirst()).equals(expectedDisc)
                            && ResourceLocation.parse("contact:creeper").equals(discCards.getFirst().get(ContactDataComponents.POSTCARD_STYLE_ID.get())),
                    "DISCARD reply read wrong context or leaked items");

            // Part B: WITHREWARDS quiet stash -> return + postcard -> no duplicate -> tier2 threshold & RANDOM
            NpcData wrData = NpcConfigManager.INSTANCE.getNpcData(npcWr);
            ErrorSolveData tier1 = new ErrorSolveData();
            tier1.setCount(1);
            tier1.setReturnType(ErrorSolveType.WITHREWARDS);
            tier1.setStyleType(StyleType.SAME);
            tier1.setStyle("contact:stripes");
            tier1.setMessage(new ArrayList<>(List.of(new MessageData("tier1:<total_count>#<trigger_count>", "contact:default", false, 1))));
            ErrorSolveData tier2 = new ErrorSolveData();
            tier2.setCount(2);
            tier2.setReturnType(ErrorSolveType.WITHREWARDS);
            tier2.setStyleType(StyleType.RANDOM);
            tier2.setStyle("contact:default");
            tier2.setMessage(new ArrayList<>(List.of(new MessageData("tier2:<total_count>#<trigger_count>", "contact:default", false, 1))));
            wrData.getErrorSolve().clear();
            wrData.getErrorSolve().add(tier1);
            wrData.getErrorSolve().add(tier2);

            ParcelReward wrReward1 = new ParcelReward(file.newID(), quest);
            wrReward1.setItem(new ItemStack(Items.DIAMOND)); wrReward1.setCount(1); wrReward1.setTargetAddressee(npcWr);
            ParcelReward wrReward2 = new ParcelReward(file.newID(), quest);
            wrReward2.setItem(new ItemStack(Items.GOLD_INGOT)); wrReward2.setCount(1); wrReward2.setTargetAddressee(npcWr);
            ParcelReward wrReward3 = new ParcelReward(file.newID(), quest);
            wrReward3.setItem(new ItemStack(Items.APPLE)); wrReward3.setCount(1); wrReward3.setTargetAddressee(npcWr);
            quest.addReward(wrReward1); quest.addReward(wrReward2); quest.addReward(wrReward3);
            ContactQuestsTestUtil.refreshIndex(file);

            PlatformHelper.resetMailboxContents(player.getUUID());
            ItemStack creeperIn = PostcardItem.setText(PostcardItem.getPostcard(ResourceLocation.parse("contact:creeper"), false), "wrong card");
            collection.addItems(player, npcWr, List.of(creeperIn, new ItemStack(Items.EMERALD, 3)));
            flushAndDeliver(level, 3);
            helper.assertTrue(rawMail(player.getUUID()).isEmpty() && collection.getTriggerCount(player.getUUID(), npcWr) == 1,
                    "WITHREWARDS must stash quietly without immediate reply");

            // Claim reward 1 -> returns stashed card + 3 emeralds + accompanying tier1 SAME reply postcard
            helper.assertTrue(wrReward1.claim(player, false), "wrReward1 claim failed");
            flushAndDeliver(level, 3);
            List<ItemStack> claim1Cards = rawMail(player.getUUID()).stream().filter(s -> s.getItem() instanceof PostcardItem).toList();
            Optional<ItemStack> tier1Reply = claim1Cards.stream().filter(c -> PostcardItem.getText(c).equals("tier1:4#1|npc_reply")).findFirst();
            helper.assertTrue(mailItemCount(player.getUUID(), Items.DIAMOND) == 1
                            && mailItemCount(player.getUUID(), Items.EMERALD) == 3
                            && tier1Reply.isPresent()
                            && ResourceLocation.parse("contact:creeper").equals(tier1Reply.get().get(ContactDataComponents.POSTCARD_STYLE_ID.get()))
                            && collection.getStacks(player.getUUID(), npcWr).isEmpty(),
                    "WITHREWARDS claim did not return stashed items with SAME-style accompanying postcard");

            // Claim reward 2 with empty stash -> no duplicate wrong-mail postcard
            PlatformHelper.resetMailboxContents(player.getUUID());
            helper.assertTrue(wrReward2.claim(player, false), "wrReward2 claim failed");
            flushAndDeliver(level, 3);
            helper.assertTrue(mailItemCount(player.getUUID(), Items.GOLD_INGOT) == 1
                            && rawMail(player.getUUID()).stream().noneMatch(s -> s.getItem() instanceof PostcardItem),
                    "Claiming second reward with empty stash duplicated wrong-mail postcard");

            // Second wrong mail (parcel items only) -> quiet stash -> claim reward 3 hits count=2 tier2
            PlatformHelper.resetMailboxContents(player.getUUID());
            collection.addItems(player, npcWr, List.of(new ItemStack(Items.IRON_INGOT, 5)));
            flushAndDeliver(level, 3);
            helper.assertTrue(rawMail(player.getUUID()).isEmpty() && collection.getTriggerCount(player.getUUID(), npcWr) == 2,
                    "Second WITHREWARDS mistake should remain quietly stashed");
            helper.assertTrue(wrReward3.claim(player, false), "wrReward3 claim failed");
            flushAndDeliver(level, 3);
            List<ItemStack> claim3Cards = rawMail(player.getUUID()).stream().filter(s -> s.getItem() instanceof PostcardItem).toList();
            helper.assertTrue(mailItemCount(player.getUUID(), Items.APPLE) == 1
                            && mailItemCount(player.getUUID(), Items.IRON_INGOT) == 5
                            && claim3Cards.size() == 1
                            && PostcardItem.getText(claim3Cards.getFirst()).equals("tier2:5#2|npc_reply")
                            && PostcardDataManager.getPostcards().containsKey(claim3Cards.getFirst().get(ContactDataComponents.POSTCARD_STYLE_ID.get())),
                    "Second WITHREWARDS claim hit wrong count threshold (+1 off-by-one) or invalid RANDOM style");

            // Part C: StyleType.SAME fallback to npcData.style when incoming parcel has no postcard
            PlatformHelper.resetMailboxContents(player.getUUID());
            NpcData fbData = NpcConfigManager.INSTANCE.getNpcData(npcFallback);
            ErrorSolveData fbRule = new ErrorSolveData();
            fbRule.setCount(1); fbRule.setReturnType(ErrorSolveType.NOW);
            fbRule.setStyleType(StyleType.SAME); fbRule.setStyle("contact:stripes");
            fbRule.setMessage(new ArrayList<>(List.of(new MessageData("same_fb", "contact:default", false, 1))));
            fbData.getErrorSolve().clear(); fbData.getErrorSolve().add(fbRule);
            collection.addItems(player, npcFallback, List.of(new ItemStack(Items.COAL, 2)));
            flushAndDeliver(level, 3);
            List<ItemStack> fbCards = rawMail(player.getUUID()).stream().filter(s -> s.getItem() instanceof PostcardItem).toList();
            helper.assertTrue(fbCards.size() == 1
                            && ResourceLocation.parse("contact:stripes").equals(fbCards.getFirst().get(ContactDataComponents.POSTCARD_STYLE_ID.get())),
                    "StyleType.SAME did not fall back to npcData.style for pure parcel");
        } finally {
            cleanupMailbox(player.getUUID());
            collection.removeData(player.getUUID(), npcDiscard);
            collection.removeData(player.getUUID(), npcWr);
            collection.removeData(player.getUUID(), npcFallback);
            removeChapter(chapter);
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void llmNpcDeliveryDelayAndNonSpoilerVisibility(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(level, "LlmVis");
        PartyTeam team = party(player);
        bindMailbox(helper, player, 5);
        NpcLlmConfig.Config origLlm = NpcLlmConfig.INSTANCE.current();
        boolean origDelivery = ContactConfig.INSTANCE.getEnableDeliveryTime().get();
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        Chapter chapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(), "cq_ext_llm_" + player.getUUID());
        file.getDefaultChapterGroup().addChapter(chapter);
        Quest quest = new Quest(file.newID(), chapter);
        chapter.addQuest(quest);

        String hiddenNpc = "CQLlmHidden_" + player.getUUID();
        String publicNpc = "CQLlmPublic_" + player.getUUID();
        KubeJSNpcSavedData npcOverrides = KubeJSNpcSavedData.Companion.get(level);
        NpcLlmSavedData llmStore = NpcLlmSavedData.Companion.get(level);
        var playersByUuidField = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
        playersByUuidField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, ServerPlayer> onlineByUuid = (Map<UUID, ServerPlayer>) playersByUuidField.get(level.getServer().getPlayerList());
        onlineByUuid.put(player.getUUID(), player);

        try {
            Map<String, NpcLlmConfig.Provider> providers = new LinkedHashMap<>(origLlm.getProviders());
            providers.putIfAbsent("mock", new NpcLlmConfig.Provider());
            Map<String, NpcLlmConfig.Npc> npcs = new LinkedHashMap<>(origLlm.getNpcs());
            npcs.put(hiddenNpc, mockNpc(false));
            npcs.put(publicNpc, mockNpc(true));
            NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, providers, npcs));
            npcOverrides.setNpcData(hiddenNpc, 5, false);
            ContactConfig.INSTANCE.getEnableDeliveryTime().set(true);

            // 1. Before quests or conversations: publicNpc visible, hiddenNpc hidden with delay=5
            Map<String, Integer> avail0 = DataManager.INSTANCE.getAvailableTargets(player);
            Map<String, Integer> hidden0 = DataManager.INSTANCE.getHiddenTargets(player);
            helper.assertTrue(avail0.containsKey(publicNpc) && !avail0.containsKey(hiddenNpc)
                            && Integer.valueOf(5).equals(hidden0.get(hiddenNpc))
                            && !llmStore.hasActiveConversation(hiddenNpc, team.getId(), player.getUUID(), "player"),
                    "Unsolicited non-autoFill LLM NPC spoiled availability or created empty conversation");

            // 2. Locked quest still keeps hiddenNpc out of available; unlocking reveals it
            PostcardTask task = new PostcardTask(file.newID(), quest);
            task.setTargetAddressee(hiddenNpc);
            task.setPostcardText("hi");
            quest.addTask(task);
            ContactQuestsTestUtil.refreshIndex(file);
            boolean updated = com.creeperyang.contactquests.quest.QuestGraphService.INSTANCE.updateQuest(level, quest.getCodeString(),
                    Map.of("requiredTags", List.of("CQLlmUnlock_" + player.getUUID()), "hideWithoutTags", true));
            helper.assertTrue(updated, "QuestGraphService.updateQuest failed to apply requiredTags");
            DataManager.targetsDirty = true;

            TeamData data = file.getOrCreateTeamData(team);
            helper.assertTrue(!DataManager.INSTANCE.getAvailableTargets(player).containsKey(hiddenNpc),
                    "Locked quest spoiled hidden LLM NPC in getAvailableTargets");
            ((ITeamDataExtension) data).contactQuests$unlockTag("CQLlmUnlock_" + player.getUUID());
            helper.assertTrue(Integer.valueOf(5).equals(DataManager.INSTANCE.getAvailableTargets(player).get(hiddenNpc)),
                    "Unlocked quest did not reveal LLM NPC with delay=5");

            // 3. Global enableDeliveryTime=false overrides hidden/available delay to 0
            ContactConfig.INSTANCE.getEnableDeliveryTime().set(false);
            helper.assertTrue(Integer.valueOf(0).equals(DataManager.INSTANCE.getAvailableTargets(player).get(hiddenNpc)),
                    "Disabling enableDeliveryTime did not force LLM delay to 0");
            ContactConfig.INSTANCE.getEnableDeliveryTime().set(true);

            // 4. Ender mail arrives immediately (0 ticks) while normal mail respects 5 ticks
            PlatformHelper.resetMailboxContents(team.getId());
            ItemStack normalCard = PostcardItem.setText(PostcardItem.getPostcard(ResourceLocation.parse("contact:default"), false), "hello normal");
            normalCard.set(ContactDataComponents.POSTCARD_SENDER.get(), player.getScoreboardName());
            helper.assertTrue(DataManager.INSTANCE.matchPostcardTaskItem(player, normalCard, hiddenNpc),
                    "matchPostcardTaskItem rejected normal LLM card");
            // Pending in KubeJSMailQueueSavedData; not yet arrived in conversation until 5 ticks
            ((ITeamDataExtension) data).contactQuests$removeTag("CQLlmUnlock_" + player.getUUID());
            helper.assertTrue(!llmStore.hasActiveConversation(hiddenNpc, team.getId(), player.getUUID(), "player"),
                    "Normal LLM mail arrived immediately without configured delivery delay");

            NpcLlmSavedData.Conversation conv = llmStore.conversation(hiddenNpc, team.getId(), player.getUUID(), "player");
            conv.setLastTick(level.getGameTime());
            ItemStack enderCard = PostcardItem.setText(PostcardItem.getPostcard(ResourceLocation.parse("contact:default"), true), "hello ender");
            enderCard.set(ContactDataComponents.POSTCARD_SENDER.get(), player.getScoreboardName());
            DataManager.INSTANCE.matchPostcardTaskItem(player, enderCard, hiddenNpc);
            helper.assertTrue(conv.getInbox().stream().anyMatch(m -> m.getText().contains("hello ender"))
                            && conv.getInbox().stream().noneMatch(m -> m.getText().contains("hello normal")),
                    "Ender mail did not bypass delay or normal mail bypassed delay");
            for (int t = 0; t < 4; t++) {
                KubeJSMailQueueSavedData.Companion.get(level).tick(level);
                helper.assertTrue(conv.getInbox().stream().noneMatch(m -> m.getText().contains("hello normal")),
                        "Normal LLM mail arrived early at tick " + (t + 1));
            }
            KubeJSMailQueueSavedData.Companion.get(level).tick(level);
            helper.assertTrue(conv.getInbox().stream().anyMatch(m -> m.getText().contains("hello normal")),
                    "Normal LLM mail failed to arrive on tick 5");

            // 5. Outbound LLM reply uses delay=5 in RewardDeliverySavedData; conversation visibility outlives removed quest
            conv.getTurns().add(new NpcLlmSavedData.Turn("user", "hello ender"));
            conv.setPendingId(conv.getInbox().getFirst().getId());
            conv.setPendingPlayer(player.getUUID());
            conv.setPendingText("hello");
            conv.setReplyText("Delayed courier reply");
            conv.setMailPolicy("discard");
            conv.setResolved(true);
            conv.setRetryAfterTick(Long.MIN_VALUE);
            NpcLlmService.INSTANCE.tick(level);
            RewardDistributionManager.INSTANCE.flush(level);
            CompoundTag deliveryNbt = RewardDeliverySavedData.Companion.get(level).save(new CompoundTag(), level.registryAccess());
            boolean foundDelay5 = deliveryNbt.getList("PendingRewards", 10).stream()
                    .map(t -> (CompoundTag) t)
                    .anyMatch(t -> hiddenNpc.equals(t.getString("sender")) && t.getInt("ticks") == 5);
            helper.assertTrue(foundDelay5, "Outbound LLM reply did not receive configured delay=5 ticks");
            flushAndDeliver(level, 4);
            helper.assertTrue(rawMail(team.getId()).isEmpty(), "Outbound LLM reply delivered before 5 ticks");
            flushAndDeliver(level, 1);
            helper.assertTrue(rawMail(team.getId()).stream().anyMatch(s -> s.getItem() instanceof PostcardItem
                    && "Delayed courier reply".equals(PostcardItem.getText(s))),
                    "Outbound LLM reply did not arrive after 5 ticks");

            com.creeperyang.contactquests.quest.QuestGraphService.INSTANCE.deleteChapter(level, chapter.getCodeString());
            chapter = null;
            DataManager.targetsDirty = true;
            // Quest is gone and tag removed, yet active conversation keeps hiddenNpc visible
            helper.assertTrue(DataManager.INSTANCE.getAvailableTargets(player).containsKey(hiddenNpc),
                    "Existing LLM conversation did not keep NPC visible after quest removal");
        } finally {
            onlineByUuid.remove(player.getUUID(), player);
            cleanupMailbox(team.getId());
            npcOverrides.removeNpcData(hiddenNpc);
            ContactConfig.INSTANCE.getEnableDeliveryTime().set(origDelivery);
            NpcLlmConfig.INSTANCE.configure(origLlm);
            if (chapter != null) {
                com.creeperyang.contactquests.quest.QuestGraphService.INSTANCE.deleteChapter(level, chapter.getCodeString());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void postcardExactStyleAndRedPacketEmptyOrTrimmedBlessing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(level, "Exact");
        TeamData data = ServerQuestFile.INSTANCE.getOrCreateTeamData(player);
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        Chapter chapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(), "cq_ext_exact_" + player.getUUID());
        file.getDefaultChapterGroup().addChapter(chapter);
        Quest quest = new Quest(file.newID(), chapter);
        chapter.addQuest(quest);
        String npc = "CQExactNpc_" + player.getUUID();

        try {
            PostcardTask cardTask = new PostcardTask(file.newID(), quest);
            cardTask.setPostcardStyle("default");
            cardTask.setPostcardText("secret message");
            cardTask.setTargetAddressee(npc);
            quest.addTask(cardTask);

            RedPacketTask redTask = new RedPacketTask(file.newID(), quest);
            redTask.setItemStack(ItemStack.EMPTY);
            redTask.setBlessing("Happy\\nYear");
            redTask.setTargetAddressee(npc);
            quest.addTask(redTask);
            ContactQuestsTestUtil.refreshIndex(file);
            DataManager.targetsDirty = true;

            ItemStack defaultCard = PostcardItem.setText(PostcardItem.getPostcard(ResourceLocation.parse("contact:default"), false),
                    "  prefix secret message suffix  ");
            ItemStack stripesCard = PostcardItem.setText(PostcardItem.getPostcard(ResourceLocation.parse("contact:stripes"), false),
                    "secret message");
            cardTask.setContext(player, data);
            boolean matchDefault = cardTask.test(defaultCard);
            boolean matchStripes = cardTask.test(stripesCard);
            cardTask.setPostcardStyle("def");
            boolean matchPrefixOnly = cardTask.test(defaultCard);
            cardTask.clearContext();
            helper.assertTrue(matchDefault && !matchStripes && !matchPrefixOnly,
                    "Postcard style matching allowed substring or failed exact normalized match");

            // RedPacket empty contents (itemStack == EMPTY task still requires an item inside)
            ItemStack emptyRed = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("contact:red_packet")));
            emptyRed.set(ContactDataComponents.RED_PACKET_BLESSING.get(), "  Happy\nYear  ");
            redTask.setContext(player, data);
            boolean emptyMatched = redTask.test(emptyRed);
            redTask.clearContext();
            helper.assertTrue(!emptyMatched, "Empty blessing-only red packet wrongly matched RedPacketTask");

            CollectionSavedData collection = CollectionSavedData.Companion.get(level);
            int beforeTriggers = collection.getTriggerCount(player.getUUID(), npc);
            DataManager.INSTANCE.matchRedPacketTaskItem(player, emptyRed, npc);
            helper.assertTrue(data.getProgress(redTask) == 0 && collection.getTriggerCount(player.getUUID(), npc) == beforeTriggers + 1,
                    "Empty red packet incremented task progress or failed to route to NPC reaction");

            SimpleContainer inner = new SimpleContainer(1);
            inner.setItem(0, new ItemStack(Items.GOLD_NUGGET, 1));
            ItemStack filledTrimRed = com.creeperyang.contactquests.utils.RedPacketUtils.INSTANCE.getRedPacket(
                    inner, "  Happy\nYear  ", npc);
            // Calling test with null tempContext must not throw NPE
            helper.assertTrue(redTask.test(filledTrimRed), "Filled trimmed red packet failed null-context test");
            helper.assertTrue(DataManager.INSTANCE.matchRedPacketTaskItem(player, filledTrimRed, npc)
                            && data.getProgress(redTask) == 1,
                    "Filled red packet with trimmed blessing failed task submission");
        } finally {
            CollectionSavedData.Companion.get(level).removeData(player.getUUID(), npc);
            removeChapter(chapter);
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void localeChoiceTemplatesReloadAndPartyPlayerNameIsolation(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = player(level, "CQAlice");
        ServerPlayer bob = player(level, "CQBob");
        PartyTeam team = party(alice, bob);
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        Chapter chapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(), "cq_ext_loc_" + alice.getUUID());
        file.getDefaultChapterGroup().addChapter(chapter);
        Quest quest = new Quest(file.newID(), chapter);
        chapter.addQuest(quest);
        quest.addTask(new CheckmarkTask(file.newID(), quest));
        RewardTable table = new RewardTable(file.newID(), file);
        table.addReward(new WeightedReward(new ItemReward(file.newID(), table.getFakeQuest(), new ItemStack(Items.APPLE), 2), 1));
        file.addRewardTable(table);

        Path tplPath = FMLPaths.CONFIGDIR.get().resolve("contactquests/choice_templates.json");
        Path llmPath = FMLPaths.CONFIGDIR.get().resolve("contactquests/npc_llm.json");
        byte[] origTplBytes = Files.exists(tplPath) ? Files.readAllBytes(tplPath) : null;
        byte[] origLlmBytes = Files.exists(llmPath) ? Files.readAllBytes(llmPath) : null;
        Map<String, List<ChoiceTemplateConfig.Template>> origTemplates = ChoiceTemplateConfig.INSTANCE.current();
        NpcLlmConfig.Config origLlm = NpcLlmConfig.INSTANCE.current();
        String npc = "CQPartyLoc_" + alice.getUUID();

        try {
            // A. Locale selection on ChoiceTemplateConfig & request()
            Set<String> enIds = new HashSet<>();
            origTemplates.getOrDefault("en_us", List.of()).forEach(t -> enIds.add(t.getId()));
            Set<String> defIds = new HashSet<>();
            origTemplates.getOrDefault("default", List.of()).forEach(t -> defIds.add(t.getId()));
            RandomSource rnd = RandomSource.create(7L);
            helper.assertTrue(enIds.contains(ChoiceTemplateConfig.INSTANCE.selectForLocale("default", "en_us", rnd).getId())
                            && enIds.contains(ChoiceTemplateConfig.INSTANCE.selectForLocale("default", "en_gb", rnd).getId())
                            && defIds.contains(ChoiceTemplateConfig.INSTANCE.selectForLocale("default", "zh_cn", rnd).getId())
                            && defIds.contains(ChoiceTemplateConfig.INSTANCE.selectForLocale("default", "fr_fr", rnd).getId()),
                    "ChoiceTemplateConfig locale resolution picked wrong group");

            alice.updateOptions(new ClientInformation("en_us", 8, ChatVisiblity.FULL, true, 0, HumanoidArm.RIGHT, false, true));
            ParcelChoiceReward enChoice = new ParcelChoiceReward(file.newID(), quest);
            enChoice.setTable(table);
            enChoice.setTargetAddressee(npc);
            enChoice.setHideContents(false);
            quest.addReward(enChoice);
            TeamData teamData = file.getOrCreateTeamData(team);
            teamData.setLocked(false);
            teamData.setCompleted(quest.id, new Date());
            ContactQuestsTestUtil.refreshIndex(file);
            ChoiceRewardSavedData.Request enReq = ChoiceRewardSavedData.Companion.get(level).request(alice, enChoice, 0);
            helper.assertTrue(enIds.contains(enReq.getTemplateId()) && enReq.getText().contains(alice.getScoreboardName()),
                    "English player choice request did not select en_us template: id=" + enReq.getTemplateId());

            // B. Reload via TagsUpdatedEvent(SERVER_DATA_LOAD) refreshes ChoiceTemplateConfig & NpcLlmConfig and preserves KubeJS replacers
            Files.createDirectories(tplPath.getParent());
            var tplJson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            var reloadedTemplates = new LinkedHashMap<>(origTemplates);
            reloadedTemplates.put("cq_reload_group", List.of(new ChoiceTemplateConfig.Template("custom", 1, "Reloaded {recipient}: {reward}")));
            Files.writeString(tplPath, tplJson.toJson(reloadedTemplates));

            var providers = new LinkedHashMap<>(origLlm.getProviders());
            providers.putIfAbsent("mock", new NpcLlmConfig.Provider());
            var npcs = new LinkedHashMap<>(origLlm.getNpcs());
            npcs.put("CQReloadNpc", mockNpc(false));
            Files.writeString(llmPath, tplJson.toJson(new NpcLlmConfig.Config(true, providers, npcs)));

            TagsUpdatedEvent reloadEvent = new TagsUpdatedEvent(level.registryAccess(), false, false);
            helper.assertTrue(reloadEvent.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD,
                    "Unexpected TagsUpdatedEvent cause");
            com.creeperyang.contactquests.ContactQuests.INSTANCE.onTagsUpdated(reloadEvent);
            helper.assertTrue(ChoiceTemplateConfig.INSTANCE.hasGroup("cq_reload_group")
                            && NpcLlmConfig.INSTANCE.npc("CQReloadNpc") != null,
                    "SERVER_DATA_LOAD reload did not reload ChoiceTemplateConfig or NpcLlmConfig");

            // C. Party <player_name> isolation across teammates
            PostcardTask cardTask = new PostcardTask(file.newID(), quest);
            cardTask.setPostcardText("<player_name>|<team_name>|<team_size>");
            cardTask.setTargetAddressee(npc);
            RedPacketTask redTask = new RedPacketTask(file.newID(), quest);
            redTask.setBlessing("<player_name>|<team_size>");
            redTask.setTargetAddressee(npc);
            quest.addTask(cardTask);
            quest.addTask(redTask);
            ContactQuestsTestUtil.refreshIndex(file);
            DataManager.INSTANCE.init();

            ContactKubeJSPlugin.INSTANCE.updatePlayerTaskCache(alice);
            String cachedCard = ((ITeamDataExtension) teamData).contactQuests$getPostcardText(cardTask.id);
            String cachedRed = ((ITeamDataExtension) teamData).contactQuests$getRedPacketBlessing(redTask.id);
            helper.assertTrue(cachedCard != null && cachedCard.contains("<player_name>")
                            && cachedRed != null && cachedRed.contains("<player_name>"),
                    "Party cache baked one player's name: card=" + cachedCard + ", red=" + cachedRed);

            String expectedAlice = alice.getName().getString() + "|" + teamData.getName() + "|2";
            String expectedBob = bob.getName().getString() + "|" + teamData.getName() + "|2";
            helper.assertTrue(cardTask.getResolvedText(teamData, alice).equals(expectedAlice)
                            && cardTask.getResolvedText(teamData, bob).equals(expectedBob)
                            && redTask.getResolvedText(teamData, alice).equals(alice.getName().getString() + "|2")
                            && redTask.getResolvedText(teamData, bob).equals(bob.getName().getString() + "|2"),
                    "Party <player_name> resolved incorrectly per player");

            ItemStack aliceCard = PostcardItem.setText(PostcardItem.getPostcard(ResourceLocation.parse("contact:default"), false), expectedAlice);
            ItemStack bobCard = PostcardItem.setText(PostcardItem.getPostcard(ResourceLocation.parse("contact:default"), false), expectedBob);
            cardTask.setContext(bob, teamData);
            boolean bobAcceptedAlice = cardTask.test(aliceCard);
            cardTask.clearContext();
            helper.assertTrue(!bobAcceptedAlice, "Bob's matcher accepted Alice's name card");
            helper.assertTrue(DataManager.INSTANCE.matchPostcardTaskItem(bob, bobCard, npc)
                            && teamData.getProgress(cardTask) == 1,
                    "Bob's own postcard failed to advance shared party task");
        } finally {
            if (origTplBytes != null) Files.write(tplPath, origTplBytes);
            else Files.deleteIfExists(tplPath);
            if (origLlmBytes != null) Files.write(llmPath, origLlmBytes);
            else Files.deleteIfExists(llmPath);
            ChoiceTemplateConfig.INSTANCE.configure(origTemplates);
            NpcLlmConfig.INSTANCE.configure(origLlm);
            file.removeRewardTable(table);
            removeChapter(chapter);
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void parcelRandomOnlyOneAndOccupiedMailboxTeleportLink(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerQuestFile file = ServerQuestFile.INSTANCE;

        class RecordingFakePlayer extends FakePlayer {
            final List<Component> messages = new ArrayList<>();
            final boolean op;
            RecordingFakePlayer(ServerLevel lvl, String name, boolean op) {
                super(lvl, new GameProfile(UUID.randomUUID(), name));
                this.op = op;
            }
            @Override public void sendSystemMessage(Component component) { messages.add(component); }
            @Override public boolean hasPermissions(int required) { return op && required <= 2; }
        }

        RecordingFakePlayer nonOp = new RecordingFakePlayer(level, "CQNoOp_" + UUID.randomUUID().toString().substring(0, 6), false);
        RecordingFakePlayer opUser = new RecordingFakePlayer(level, "CQOp_" + UUID.randomUUID().toString().substring(0, 6), true);
        ((TeamManagerImpl) FTBTeamsAPI.api().getManager()).playerLoggedIn(null, nonOp.getUUID(), nonOp.getGameProfile().getName());
        ((TeamManagerImpl) FTBTeamsAPI.api().getManager()).playerLoggedIn(null, opUser.getUUID(), opUser.getGameProfile().getName());
        PartyTeam team = party(opUser, nonOp);

        Chapter chapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(), "cq_ext_only1_" + opUser.getUUID());
        file.getDefaultChapterGroup().addChapter(chapter);
        Quest quest = new Quest(file.newID(), chapter);
        chapter.addQuest(quest);
        RewardTable onlyOneTable = new RewardTable(file.newID(), file);
        ItemReward pickReward = new ItemReward(file.newID(), onlyOneTable.getFakeQuest(), new ItemStack(Items.IRON_PICKAXE), 1);
        CompoundTag pickNbt = new CompoundTag();
        pickReward.writeData(pickNbt, level.registryAccess());
        pickNbt.putBoolean("only_one", true);
        pickReward.readData(pickNbt, level.registryAccess());
        onlyOneTable.addReward(new WeightedReward(pickReward, 1));
        file.addRewardTable(onlyOneTable);

        try {
            helper.assertTrue(((ItemRewardAccessor) pickReward).contactQuests$isOnlyOne(), "Failed to set only_one via NBT");
            BlockPos boxA = bindMailbox(helper, opUser, 2);

            // Prepare second unbound mailbox B
            BlockPos lowerB = helper.absolutePos(new BlockPos(5, 2, 2));
            level.setBlock(lowerB.below(), Blocks.STONE.defaultBlockState(), 3);
            var baseState = BlockRegistry.GREEN_MAILBOX.get().defaultBlockState();
            level.setBlock(lowerB, baseState.setValue(MailboxBlock.HALF, DoubleBlockHalf.LOWER), 3);
            level.setBlock(lowerB.above(), baseState.setValue(MailboxBlock.HALF, DoubleBlockHalf.UPPER), 3);
            BlockPos boxB = lowerB.above();

            // Non-OP trying to bind boxB gets error without /tp link
            var binder = (TeamBindingItem) ModItems.INSTANCE.getTEAM_BINDING_CARD().get();
            nonOp.setShiftKeyDown(true);
            nonOp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(binder));
            InteractionResult nonOpRes = binder.useOn(new UseOnContext(nonOp, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(boxB), Direction.UP, boxB, false)));
            boolean nonOpHasTp = nonOp.messages.stream().anyMatch(m -> m.getStyle().getClickEvent() != null
                    && m.getStyle().getClickEvent().getValue().contains(" run tp "));
            helper.assertTrue(nonOpRes == InteractionResult.FAIL && !nonOp.messages.isEmpty() && !nonOpHasTp,
                    "Non-OP player received /tp link or succeeded second bind");

            // OP trying to bind boxB gets error WITH exact /execute in ... run tp <boxA> link
            opUser.messages.clear();
            opUser.setShiftKeyDown(true);
            opUser.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(binder));
            InteractionResult opRes = binder.useOn(new UseOnContext(opUser, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(boxB), Direction.UP, boxB, false)));
            String expectedTp = "/execute in " + level.dimension().location() + " run tp "
                    + boxA.getX() + " " + boxA.getY() + " " + boxA.getZ();
            boolean opHasExactTp = opUser.messages.stream().anyMatch(m -> {
                ClickEvent click = m.getStyle().getClickEvent();
                return click != null && click.getAction() == ClickEvent.Action.RUN_COMMAND && expectedTp.equals(click.getValue());
            });
            helper.assertTrue(opRes == InteractionResult.FAIL && opHasExactTp,
                    "OP player did not receive exact existing-mailbox teleport link");

            // only_one filter on ParcelRandomReward
            ParcelRandomReward randomReward = new ParcelRandomReward(file.newID(), quest);
            randomReward.setTable(onlyOneTable);
            randomReward.setTargetAddressee("CQOnlyOneNpc");
            quest.addReward(randomReward);

            PlatformHelper.resetMailboxContents(team.getId());
            opUser.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            boolean claimedWhileHeld = randomReward.claim(opUser, false);
            flushAndDeliver(level, 3);
            helper.assertTrue(!claimedWhileHeld && mailItemCount(team.getId(), Items.IRON_PICKAXE) == 0,
                    "only_one ParcelRandomReward granted duplicate while player already held item");

            opUser.getInventory().clearContent();
            boolean claimedWhenAbsent = randomReward.claim(opUser, false);
            flushAndDeliver(level, 3);
            helper.assertTrue(claimedWhenAbsent && mailItemCount(team.getId(), Items.IRON_PICKAXE) == 1,
                    "only_one ParcelRandomReward failed to grant item when absent");
        } finally {
            cleanupMailbox(team.getId());
            file.removeRewardTable(onlyOneTable);
            removeChapter(chapter);
        }
        helper.succeed();
    }
}
