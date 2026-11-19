package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.EventHandler;
import com.creeperyang.contactquests.compat.kubejs.ContactKubeJSPlugin;
import com.creeperyang.contactquests.compat.kubejs.QuestGraphApi;
import com.creeperyang.contactquests.config.*;
import com.creeperyang.contactquests.data.*;
import com.creeperyang.contactquests.item.TeamBindingItem;
import com.creeperyang.contactquests.quest.task.ParcelTask;
import com.creeperyang.contactquests.bridge.IMailboxTeamAccessor;
import com.creeperyang.contactquests.bridge.ITeamDataExtension;
import com.flechazo.contact.common.block.MailboxBlock;
import com.flechazo.contact.common.item.ParcelItem;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.common.registry.BlockRegistry;
import com.flechazo.contact.common.storage.PlayerMailboxData;
import com.flechazo.contact.platform.PlatformHelper;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbquests.quest.*;
import dev.ftb.mods.ftbquests.util.ProgressChange;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.*;

@GameTestHolder(ContactQuestsTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContactQuestsBoundaryTests {
    private ContactQuestsBoundaryTests() {}

    private static ServerPlayer player(ServerLevel level) {
        ServerPlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "CQ" + UUID.randomUUID().toString().substring(0, 8)));
        ((TeamManagerImpl) FTBTeamsAPI.api().getManager()).playerLoggedIn(null, player.getUUID(), player.getGameProfile().getName());
        return player;
    }

    private static PartyTeam party(ServerPlayer owner) throws Exception {
        return (PartyTeam) FTBTeamsAPI.api().getManager().createPartyTeam(owner,
                "CQ" + owner.getUUID().toString().substring(0, 8), "isolated regression team", Color4I.WHITE);
    }

    private static void join(PartyTeam party, ServerPlayer owner, ServerPlayer member) throws Exception {
        party.invite(owner, List.of(member.getGameProfile()));
        party.join(member);
    }

    private static BlockPos mailbox(GameTestHelper helper, int x) {
        ServerLevel level = helper.getLevel();
        BlockPos lower = helper.absolutePos(new BlockPos(x, 2, 2));
        var base = BlockRegistry.GREEN_MAILBOX.get().defaultBlockState();
        level.setBlock(lower, base.setValue(MailboxBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(lower.above(), base.setValue(MailboxBlock.HALF, DoubleBlockHalf.UPPER), 3);
        return lower.above();
    }

    private static InteractionResult bind(ServerPlayer player, BlockPos upper) {
        var binder = (TeamBindingItem) com.creeperyang.contactquests.registry.ModItems.INSTANCE.getTEAM_BINDING_CARD().get();
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(binder));
        return binder.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(upper), Direction.UP, upper, false)));
    }

    private static InteractionResult use(ServerLevel level, BlockPos upper, ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return level.getBlockState(upper).useWithoutItem(level, player,
                new BlockHitResult(Vec3.atCenterOf(upper), Direction.UP, upper, false));
    }

    private static Quest quest(ServerLevel level, String suffix) {
        var api = QuestGraphApi.INSTANCE;
        Chapter chapter = api.createChapter(level, "cqtest:" + suffix + "/chapter", "Boundary chapter");
        return api.createQuest(level, chapter.getCodeString(), "cqtest:" + suffix + "/quest", "Boundary quest");
    }

    private static ParcelTask task(ServerLevel level, Quest quest, String suffix, int count) {
        ParcelTask task = new ParcelTask(ServerQuestFile.INSTANCE.newID(), quest)
                .setStackAndCount(new ItemStack(Items.DIAMOND), count);
        task.setTargetAddressee("CQBoundary_" + suffix);
        quest.addTask(task);
        ContactQuestsTestUtil.refreshIndex(ServerQuestFile.INSTANCE);
        ServerQuestFile.INSTANCE.markDirty();
        DataManager.INSTANCE.ensureTargetsFresh();
        NpcConfigManager.INSTANCE.getNpcData(task.getTargetAddressee()).setDeliveryTime(0);
        return task;
    }

    private static void send(ServerPlayer player, ParcelTask task, int amount) {
        SimpleContainer contents = new SimpleContainer(4);
        contents.setItem(0, new ItemStack(Items.DIAMOND, amount));
        ItemStack mail = ParcelItem.getParcel(contents, false, player.getGameProfile().getName());
        DataManager.INSTANCE.matchParcelTaskItem(player, mail,
                mail.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY), task.getTargetAddressee());
    }

    private static int items(UUID owner, Item item) {
        var contents = PlatformHelper.getMailboxContents(owner);
        int total = 0;
        for (int slot = 0; slot < contents.getContainerSize(); slot++) {
            ItemStack mail = contents.getItem(slot);
            if (mail.is(item)) total += mail.getCount();
            total += mail.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).stream()
                    .filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
        }
        return total;
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void mailboxRejectsPersonalAndOtherTeamRebinding(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(level), outsider = player(level);
        PartyTeam team = party(owner), other = party(outsider);
        BlockPos upper = mailbox(helper, 1);
        helper.assertTrue(bind(owner, upper).consumesAction(), "Initial team bind failed");
        helper.assertTrue(team.getId().equals(PlatformHelper.getMailboxOwner(level.dimension(), upper)), "Wrong actual owner");
        helper.assertTrue(((IMailboxTeamAccessor) level.getBlockEntity(upper)).contactQuests$getTeamId().equals(team.getId()), "Missing party marker");
        helper.assertTrue(bind(outsider, upper) == InteractionResult.FAIL
                && outsider.getMainHandItem().getCount() == 1, "Other party replaced team owner or consumed binder");
        PlatformHelper.setMailboxData(owner.getUUID(), level.dimension(), upper);
        helper.assertTrue(PlatformHelper.getMailboxPos(owner.getUUID()) == null, "Party owner gained a personal alias");
        PlatformHelper.setMailboxData(other.getId(), level.dimension(), upper);
        helper.assertTrue(PlatformHelper.getMailboxPos(other.getId()) == null
                && team.getId().equals(PlatformHelper.getMailboxOwner(level.dimension(), upper)), "Contact API produced dual owners");
        owner.setShiftKeyDown(true);
        use(level, upper, owner);
        helper.assertTrue(PlatformHelper.getMailboxPos(owner.getUUID()) == null, "Native sneak-use created a personal binding");

        ServerPlayer personal = player(level);
        BlockPos personalBox = mailbox(helper, 4);
        helper.assertTrue(bind(personal, personalBox).consumesAction(), "Personal team card bind failed");
        helper.assertTrue(personal.getUUID().equals(PlatformHelper.getMailboxOwner(level.dimension(), personalBox))
                && personal.getUUID().equals(((IMailboxTeamAccessor) level.getBlockEntity(personalBox)).contactQuests$getTeamId()),
                "Personal TeamBindingCard bind did not record player's teamId marker");

        ServerPlayer vanillaPersonal = player(level);
        BlockPos vanillaBox = mailbox(helper, 7);
        vanillaPersonal.setShiftKeyDown(true);
        use(level, vanillaBox, vanillaPersonal);
        helper.assertTrue(vanillaPersonal.getUUID().equals(PlatformHelper.getMailboxOwner(level.dimension(), vanillaBox))
                && ((IMailboxTeamAccessor) level.getBlockEntity(vanillaBox)).contactQuests$getTeamId() == null,
                "Vanilla empty-hand sneak bind incorrectly wrote a team marker");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void mailboxLegacyAliasesRepairWithoutDeletingContents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID oldPlayer = UUID.randomUUID(), actual = UUID.randomUUID();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 2));
        PlayerMailboxData seed = new PlayerMailboxData();
        seed.setMailboxData(actual, level.dimension(), pos);
        SimpleContainer contents = new SimpleContainer(24);
        contents.setItem(0, new ItemStack(Items.DIAMOND, 3));
        seed.setMailboxContents(actual, contents);
        CompoundTag nbt = seed.writeToNBT(new CompoundTag(), level.registryAccess());
        String entryKey = nbt.getAllKeys().stream().filter(k -> nbt.get(k) instanceof CompoundTag c
                && c.getString("UUID").equals(actual.toString())).findFirst().orElseThrow();
        String prefix = entryKey.substring(0, entryKey.length() - 1);
        CompoundTag old = nbt.getCompound(entryKey).copy();
        old.putString("UUID", oldPlayer.toString());
        nbt.put(prefix + "0", old);
        nbt.put(prefix + "1", seed.writeToNBT(new CompoundTag(), level.registryAccess()).getCompound(entryKey).copy());
        nbt.putInt("MapDataSize", 2);
        PlayerMailboxData loaded = new PlayerMailboxData();
        loaded.readFromNBT(nbt, level.registryAccess());
        helper.assertTrue(loaded.getMailboxPos(oldPlayer) == null && loaded.getMailboxPos(actual) != null
                && actual.equals(loaded.getMailboxOwner(level.dimension(), pos)), "Legacy alias remained active");
        helper.assertTrue(loaded.getMailboxContents(oldPlayer).getItem(0).getCount() == 3
                && loaded.getMailboxContents(actual).getItem(0).getCount() == 3, "Alias repair deleted mail contents");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void contactOwnerChangesAndTeamDisbandRefreshMarkers(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(level);
        PartyTeam team = party(owner);
        BlockPos first = mailbox(helper, 1), second = mailbox(helper, 4);
        bind(owner, first);
        PlatformHelper.setMailboxData(team.getId(), level.dimension(), second);
        helper.assertTrue(((IMailboxTeamAccessor) level.getBlockEntity(first)).contactQuests$getTeamId() == null
                && team.getId().equals(((IMailboxTeamAccessor) level.getBlockEntity(second)).contactQuests$getTeamId()),
                "Native Contact move left stale markers");
        PlatformHelper.removeMailboxData(GlobalPos.of(level.dimension(), second));
        helper.assertTrue(((IMailboxTeamAccessor) level.getBlockEntity(second)).contactQuests$getTeamId() == null,
                "Native Contact removal left team marker");
        PlatformHelper.setMailboxData(team.getId(), level.dimension(), second);
        PlatformHelper.addMailboxContents(team.getId(), new ItemStack(Items.DIAMOND, 2));
        team.forceDisband(owner.createCommandSourceStack());
        helper.assertTrue(FTBTeamsAPI.api().getManager().getTeamByID(team.getId()).isEmpty()
                && PlatformHelper.getMailboxPos(team.getId()) == null
                && PlatformHelper.getMailboxOwner(level.dimension(), second) == null
                && ((IMailboxTeamAccessor) level.getBlockEntity(second)).contactQuests$getTeamId() == null,
                "Real FTB disband left a binding or marker");
        helper.assertTrue(items(team.getId(), Items.DIAMOND) == 2, "Disband destroyed retained mail");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void teammatesSubmitTogetherAndOutsidersStayIsolated(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(level), member = player(level), outsider = player(level);
        PartyTeam party = party(owner);
        join(party, owner, member);
        Quest quest = quest(level, "team" + owner.getUUID());
        ParcelTask task = task(level, quest, owner.getUUID().toString(), 5);
        TeamData teamData = ServerQuestFile.INSTANCE.getOrCreateTeamData(party);
        TeamData otherData = ServerQuestFile.INSTANCE.getOrCreateTeamData(outsider);
        send(owner, task, 1); send(member, task, 2); send(outsider, task, 1);
        helper.assertTrue(teamData.getProgress(task) == 3 && otherData.getProgress(task) == 1,
                "Actual join/submit shared or leaked the wrong quantities");
        party.leave(member.getUUID());
        send(member, task, 1);
        TeamData personal = ServerQuestFile.INSTANCE.getOrCreateTeamData(member);
        helper.assertTrue(teamData.getProgress(task) == 3 && personal.getProgress(task) >= 1,
                "Actual leave still submitted into the old team");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void mailboxAccessUsesCurrentTeamAndRealOwner(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(level), member = player(level), outsider = player(level);
        PartyTeam party = party(owner);
        join(party, owner, member);
        BlockPos upper = mailbox(helper, 1);
        bind(owner, upper);
        ItemStack parcel = ParcelItem.getParcel(new SimpleContainer(new ItemStack(Items.DIAMOND, 2)), false, "Boundary");
        PlatformHelper.addMailboxContents(party.getId(), parcel.copy());
        outsider.setShiftKeyDown(false);
        helper.assertTrue(use(level, upper, outsider) == InteractionResult.FAIL && items(party.getId(), Items.DIAMOND) == 2,
                "Outsider collected or altered team mail");
        member.setShiftKeyDown(false);
        helper.assertTrue(use(level, upper, member).consumesAction() && items(party.getId(), Items.DIAMOND) == 0,
                "Teammate could not collect actual owner mail");
        boolean delivered = member.getInventory().items.stream().anyMatch(s -> s.getOrDefault(DataComponents.CONTAINER,
                ItemContainerContents.EMPTY).stream().anyMatch(c -> c.is(Items.DIAMOND) && c.getCount() == 2));
        helper.assertTrue(delivered && items(member.getUUID(), Items.DIAMOND) == 0, "Collected the personal mailbox instead");
        party.leave(member.getUUID());
        helper.assertTrue(!FTBTeamsAPI.api().getManager().getTeamForPlayerID(member.getUUID()).orElseThrow().getId().equals(party.getId()),
                "Leave fixture did not change the current team");
        PlatformHelper.addMailboxContents(party.getId(), parcel.copy());
        helper.assertTrue(items(party.getId(), Items.DIAMOND) == 2 && party.getId().equals(
                PlatformHelper.getMailboxOwner(level.dimension(), upper)), "Leave fixture lost actual owner or parcel contents");
        InteractionResult afterLeave = use(level, upper, member);
        helper.assertTrue(afterLeave == InteractionResult.FAIL && items(party.getId(), Items.DIAMOND) == 2,
                "Former teammate access result=" + afterLeave + ", remaining=" + items(party.getId(), Items.DIAMOND));
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void rewardUsesClaimingTeamAcrossMoveAndDimension(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(level), member = player(level);
        PartyTeam team = party(owner);
        join(team, owner, member);
        BlockPos upper = mailbox(helper, 1);
        bind(owner, upper);
        RewardDistributionManager.INSTANCE.distribute(member, new ItemStack(Items.EMERALD, 3), "CQ|ClaimTeam", false);
        partyLeaveAndBindPersonal(helper, team, member);
        RewardDistributionManager.INSTANCE.onServerTick(level);
        for (int i = 0; i < 3; i++) RewardDeliverySavedData.Companion.get(level).tick(level);
        helper.assertTrue(items(team.getId(), Items.EMERALD) == 3 && items(member.getUUID(), Items.EMERALD) == 0,
                "Switch before buffer flush sent reward to new team");
        RewardDeliverySavedData queue = new RewardDeliverySavedData();
        queue.addPendingReward(member.getUUID(), new ItemStack(Items.DIAMOND, 2), 2, "CQClaimDelay", team.getId());
        queue = ContactQuestsTestUtil.roundTrip(queue, level.registryAccess());
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        helper.assertTrue(RewardDeliverySavedData.Companion.get(nether) == RewardDeliverySavedData.Companion.get(level),
                "Rewards are split across dimensions");
        queue.tick(nether); queue.tick(nether);
        helper.assertTrue(items(team.getId(), Items.DIAMOND) == 2 && items(member.getUUID(), Items.DIAMOND) == 0,
                "Reload/different tick dimension changed reward recipient");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void legacyDimensionRewardsMigrateOnceAndPersistCountdown(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var nether = level.getServer().getLevel(Level.NETHER);
        var owner = player(level); var team = party(owner); bind(owner, mailbox(helper, 1));
        var global = RewardDeliverySavedData.Companion.get(level);
        CompoundTag saved = global.save(new CompoundTag(), level.registryAccess());
        saved.getList("MigratedDimensions", Tag.TAG_STRING).removeIf(t -> t.getAsString().equals("minecraft:the_nether"));
        var companion = RewardDeliverySavedData.Companion;
        var load = companion.getClass().getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        global = (RewardDeliverySavedData) load.invoke(companion, saved, level.registryAccess());
        level.getDataStorage().set("contactquests_pending_rewards", global);
        var legacy = new RewardDeliverySavedData();
        legacy.addPendingReward(owner.getUUID(), ParcelItem.getParcel(new SimpleContainer(new ItemStack(Items.DIAMOND, 3)),
                false, "CQMigration"), 4, "CQMigration", team.getId());
        nether.getDataStorage().set("contactquests_pending_rewards", legacy);
        var merged = RewardDeliverySavedData.Companion.get(nether);
        helper.assertTrue(merged == global, "Dimension rewards did not use global queue");
        merged.tick(level);
        var restored = ContactQuestsTestUtil.roundTrip(merged, level.registryAccess());
        restored.setDirty(false); restored.tick(level);
        helper.assertTrue(restored.isDirty(), "Countdown changes were not marked for saving");
        level.getDataStorage().set("contactquests_pending_rewards", restored);
        var again = RewardDeliverySavedData.Companion.get(nether);
        again.tick(level); helper.assertTrue(items(team.getId(), Items.DIAMOND) == 0, "Countdown changed after load");
        again.tick(level); again.tick(level);
        helper.assertTrue(items(team.getId(), Items.DIAMOND) == 3, "Migration duplicated or lost reward contents");
        helper.succeed();
    }

    private static void partyLeaveAndBindPersonal(GameTestHelper helper, PartyTeam team, ServerPlayer member) throws Exception {
        team.leave(member.getUUID());
        bind(member, mailbox(helper, 4));
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void breakingEitherMailboxHalfClearsBindingBeforeReplacement(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        for (boolean party : new boolean[]{false, true}) {
            for (boolean upperHalf : new boolean[]{false, true}) {
                ServerPlayer owner = player(level);
                Team team = party ? party(owner) : FTBTeamsAPI.api().getManager().getTeamForPlayer(owner).orElseThrow();
                BlockPos upper = mailbox(helper, party ? 5 : 1);
                helper.assertTrue(bind(owner, upper).consumesAction(), "Could not prepare removal binding");
                PlatformHelper.addMailboxContents(team.getId(), new ItemStack(Items.DIAMOND, 2));
                var state = level.getBlockState(upper);
                // A flag/blockstate update must not count as physical removal.
                level.setBlock(upper, state, 3);
                helper.assertTrue(PlatformHelper.getMailboxPos(team.getId()) != null, "State update cleared live binding");
                level.destroyBlock(upperHalf ? upper : upper.below(), false);
                helper.assertTrue(PlatformHelper.getMailboxPos(team.getId()) == null
                        && PlatformHelper.getMailboxOwner(level.dimension(), upper) == null,
                        "Physical removal retained " + (party ? "party" : "personal") + " location");
                helper.assertTrue(items(team.getId(), Items.DIAMOND) == 2, "Breaking mailbox deleted its stored mail");
                BlockPos replaced = mailbox(helper, party ? 5 : 1);
                helper.assertTrue(PlatformHelper.getMailboxOwner(level.dimension(), replaced) == null
                        && ((IMailboxTeamAccessor) level.getBlockEntity(replaced)).contactQuests$getTeamId() == null,
                        "Replacement inherited old ownership");
                owner.setShiftKeyDown(false);
                helper.assertTrue(use(level, replaced, owner) == InteractionResult.FAIL, "Replacement received before explicit binding");
                helper.assertTrue(bind(owner, replaced).consumesAction(), "Fresh bind after replacement failed");
                level.setBlock(replaced, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                helper.assertTrue(PlatformHelper.getMailboxPos(team.getId()) == null, "Script replacement retained binding");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void questTagsMutexDependenciesAndFlexibleModeEnforceRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(level);
        String key = "locks" + player.getUUID();
        Quest quest = quest(level, key);
        ParcelTask task = task(level, quest, key, 3);
        Quest a = quest(level, key + "a"), b = quest(level, key + "b");
        ParcelTask ta = task(level, a, key + "a", 1), tb = task(level, b, key + "b", 1);
        TeamData data = ServerQuestFile.INSTANCE.getOrCreateTeamData(player);
        ITeamDataExtension ext = (ITeamDataExtension) data;
        QuestGraphApi api = QuestGraphApi.INSTANCE;
        api.updateQuest(level, quest.getCodeString(), Map.of("requiredTags", List.of("CQA", "CQB"),
                "tagMode", "ALL", "hideWithoutTags", true, "hideIfLocked", true, "dependencies", List.of(a.getCodeString())));
        helper.assertTrue(!data.canStartTasks(quest) && !quest.isVisible(data), "Missing tags did not hide and lock quest");
        ext.contactQuests$unlockTag("CQA");
        helper.assertTrue(!data.canStartTasks(quest) && data.getCannotStartReason(quest).getString().contains("CQB")
                && !data.getCannotStartReason(quest).getString().contains("CQA"), "ALL or missing tag reason is incorrect");
        ext.contactQuests$unlockTag("CQB");
        helper.assertTrue(!data.canStartTasks(quest), "Tags bypassed native FTB dependency");
        data.setProgress(ta, 1);
        helper.assertTrue(data.canStartTasks(quest) && quest.isVisible(data), "Native completion was not recognized");
        for (String mode : List.of("ANY", "ALL", "NUMBER")) {
            // Native resetProgress(task) only drops the progress counter. Reset
            // the complete quest so its cached completion date is cleared too.
            a.forceProgressRaw(data, new ProgressChange(a, player.getUUID()).setReset(true));
            b.forceProgressRaw(data, new ProgressChange(b, player.getUUID()).setReset(true));
            helper.assertTrue(!data.isCompleted(a) && !data.isCompleted(b)
                    && data.getProgress(ta) == 0 && data.getProgress(tb) == 0, "Native reset fixture retained completion");
            api.updateQuest(level, quest.getCodeString(), Map.of("dependencies", List.of(),
                    "mutex", List.of(a.getCodeString(), b.getCodeString()), "mutexMode", mode, "mutexCount", 2));
            helper.assertTrue(data.canStartTasks(quest), mode + " locked before any completion");
            data.setProgress(ta, 1);
            helper.assertTrue(data.canStartTasks(quest) == !mode.equals("ANY"), mode + " wrong one-completion threshold");
            data.setProgress(tb, 1);
            helper.assertTrue(!data.canStartTasks(quest) && !quest.isVisible(data), mode + " ignored mutex threshold");
        }
        api.updateChapter(level, quest.getChapter().getCodeString(), Map.of("progressionMode", "FLEXIBLE"));
        helper.assertTrue(!data.canStartTasks(quest), "FLEXIBLE bypassed mutex");
        send(player, task, 1);
        helper.assertTrue(data.getProgress(task) == 0, "Locked task consumed progress");
        ext.contactQuests$forceQuest(quest.id);
        helper.assertTrue(data.canStartTasks(quest) && quest.isVisible(data), "Force did not permit hidden locked quest");
        ext.contactQuests$blockQuest(quest.id);
        helper.assertTrue(!data.canStartTasks(quest) && !quest.isVisible(data), "Block did not supersede force");
        helper.assertTrue(data.getCannotStartReason(quest).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents c
                && c.getKey().equals("contactquests.quest.locked.script"), "Wrong script-lock reason");
        ContactKubeJSPlugin.resetQuestState(data, quest.getCodeString());
        helper.assertTrue(!data.canStartTasks(quest), "Reset discarded actual mutex rules");
        api.updateQuest(level, quest.getCodeString(), Map.of("mutex", List.of(), "requiredTags", List.of()));
        helper.assertTrue(data.canStartTasks(quest) && quest.isVisible(data), "Clearing rules did not unlock visible quest");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void unlockItemsAffectOnlyCurrentTeamAndPersist(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(level), member = player(level), outsider = player(level);
        PartyTeam team = party(owner); join(team, owner, member);
        TeamData data = ServerQuestFile.INSTANCE.getOrCreateTeamData(team);
        Quest quest = quest(level, "itemtags" + owner.getUUID());
        task(level, quest, owner.getUUID().toString(), 1);
        QuestGraphApi.INSTANCE.updateQuest(level, quest.getCodeString(), Map.of("requiredTags", List.of("CQItemA", "CQItemB"),
                "hideWithoutTags", true));
        ItemStack card = new ItemStack(Items.PAPER);
        ListTag tags = new ListTag(); tags.add(StringTag.valueOf("CQItemA"));
        CompoundTag custom = new CompoundTag(); custom.put("ContactQuestsUnlockTags", tags);
        card.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        member.setItemInHand(InteractionHand.MAIN_HAND, card);
        EventHandler.INSTANCE.onItemRightClick(new PlayerInteractEvent.RightClickItem(member, InteractionHand.MAIN_HAND));
        helper.assertTrue(!data.canStartTasks(quest), "Single item tag satisfied ALL");
        tags.add(StringTag.valueOf("CQItemB")); custom.put("ContactQuestsUnlockTags", tags);
        card.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        EventHandler.INSTANCE.onItemRightClick(new PlayerInteractEvent.RightClickItem(member, InteractionHand.MAIN_HAND));
        helper.assertTrue(data.canStartTasks(quest) && quest.isVisible(data)
                && !((ITeamDataExtension) ServerQuestFile.INSTANCE.getOrCreateTeamData(outsider)).contactQuests$hasTag("CQItemA"),
                "Tag unlock did not affect teammates or leaked to outsider");
        TeamData restored = new TeamData(team.getId(), ServerQuestFile.INSTANCE, "reload");
        restored.deserializeNBT(data.serializeNBT());
        helper.assertTrue(data.canStartTasks(quest) && ((ITeamDataExtension) restored).contactQuests$hasTag("CQItemB"),
                "Team tags were not serialized");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void allNpcRemainderPoliciesKeepExactQuantities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (ErrorSolveType policy : ErrorSolveType.values()) {
            ServerPlayer player = player(level);
            String suffix = "policy" + policy.name().toLowerCase(Locale.ROOT) + player.getUUID();
            Quest quest = quest(level, suffix);
            ParcelTask task = task(level, quest, suffix, 2);
            NpcData npc = NpcConfigManager.INSTANCE.getNpcData(task.getTargetAddressee());
            ErrorSolveData rule = new ErrorSolveData();
            rule.setReturnType(policy); rule.setStyleType(StyleType.SAME); rule.setStyle("contact:default");
            rule.setMessage(new ArrayList<>(List.of(new MessageData("count <total_count>", "contact:default", false, 1))));
            npc.getErrorSolve().clear(); npc.getErrorSolve().add(rule);
            BlockPos upper = mailbox(helper, policy.ordinal() * 3 + 1);
            bind(player, upper);
            CollectionSavedData collection = CollectionSavedData.Companion.get(level);
            collection.setDirty(false);
            SimpleContainer contents = new SimpleContainer(4);
            contents.setItem(0, new ItemStack(Items.DIAMOND, 3)); contents.setItem(1, new ItemStack(Items.DIRT, 2));
            ItemStack mail = ParcelItem.getParcel(contents, false, "CQPolicy");
            DataManager.INSTANCE.matchParcelTaskItem(player, mail, mail.getOrDefault(DataComponents.CONTAINER,
                    ItemContainerContents.EMPTY), task.getTargetAddressee());
            TeamData data = ServerQuestFile.INSTANCE.getOrCreateTeamData(player);
            helper.assertTrue(data.getProgress(task) == 2, policy + " lost task progress");
            helper.assertTrue(collection.getTriggerCount(player.getUUID(), task.getTargetAddressee()) == 1,
                    policy + " counted item quantities or slots instead of one mixed mail");
            helper.assertTrue(collection.isDirty(), policy + " changed the trigger count without marking it for saving");
            helper.assertTrue(ContactQuestsTestUtil.roundTrip(collection, level.registryAccess())
                    .getTriggerCount(player.getUUID(), task.getTargetAddressee()) == 1,
                    policy + " did not serialize the trigger count");
            int stashed = collection.getStacks(player.getUUID(), task.getTargetAddressee()).stream().mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(stashed == (policy == ErrorSolveType.SAVE || policy == ErrorSolveType.WITHREWARDS ? 3 : 0),
                    policy + " wrong stash quantity " + stashed);
            if (policy == ErrorSolveType.WITHREWARDS) collection.returnReward(player, task.getTargetAddressee());
            RewardDistributionManager.INSTANCE.onServerTick(level);
            for (int i = 0; i < 3; i++) RewardDeliverySavedData.Companion.get(level).tick(level);
            int expected = policy == ErrorSolveType.NOW || policy == ErrorSolveType.WITHREWARDS ? 1 : 0;
            helper.assertTrue(items(player.getUUID(), Items.DIAMOND) == expected
                    && items(player.getUUID(), Items.DIRT) == expected * 2, policy + " wrong returned quantities");
        }
        ContactQuestsTestServerController.recordCase("server.collection.error_solve", true,
                "NOW/SAVE/WITHREWARDS/DISCARD: task diamond x2; remainder diamond x1/dirt x2 verified in stash and mailbox");
        ContactQuestsTestServerController.recordCase("server.collection.count_all_policies", true,
                "All four policies increment once per mixed mail, mark counts dirty and serialize count=1");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void npcTriggerCountIsolatesPlayersAndRecipients(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = player(level), member = player(level), outsider = player(level);
        PartyTeam team = party(owner);
        join(team, owner, member);
        String suffix = "trigger" + owner.getUUID();
        ParcelTask task = task(level, quest(level, suffix), suffix, 2);
        String npc = task.getTargetAddressee();
        NpcData config = NpcConfigManager.INSTANCE.getNpcData(npc);
        config.getErrorSolve().clear();
        ErrorSolveData first = new ErrorSolveData();
        first.setReturnType(ErrorSolveType.SAVE);
        ErrorSolveData second = new ErrorSolveData();
        second.setCount(2);
        second.setReturnType(ErrorSolveType.DISCARD);
        config.getErrorSolve().add(first);
        config.getErrorSolve().add(second);
        CollectionSavedData collection = CollectionSavedData.Companion.get(level);

        send(owner, task, 2);
        helper.assertTrue(ServerQuestFile.INSTANCE.getOrCreateTeamData(team).getProgress(task) == 2
                && collection.getTriggerCount(owner.getUUID(), npc) == 0,
                "A fully matched task mail was counted as a mistake");
        collection.addItems(owner, npc, List.of());
        collection.addItems(owner, npc, List.of(ItemStack.EMPTY));
        helper.assertTrue(collection.getTriggerCount(owner.getUUID(), npc) == 0, "Empty mail increased the count");
        collection.addItemsSilent(owner, npc, List.of(new ItemStack(Items.APPLE, 3)));
        helper.assertTrue(collection.getTriggerCount(owner.getUUID(), npc) == 0,
                "Silent script stash changes increased the count");

        // Both slots are now ordinary mail because this party has completed the task.
        SimpleContainer contents = new SimpleContainer(new ItemStack(Items.DIRT, 12), new ItemStack(Items.PAPER, 4));
        ItemStack mail = ParcelItem.getParcel(contents, false, "CQTrigger");
        DataManager.INSTANCE.matchParcelTaskItem(owner, mail, mail.getOrDefault(DataComponents.CONTAINER,
                ItemContainerContents.EMPTY), npc);
        helper.assertTrue(collection.getTriggerCount(owner.getUUID(), npc) == 1,
                "Sixteen leftover items in two slots must count as one mail");
        collection.addItems(owner, npc, List.of(new ItemStack(Items.STONE, 64)));
        helper.assertTrue(collection.getTriggerCount(owner.getUUID(), npc) == 2
                && NpcConfigManager.INSTANCE.getErrorSolve(npc, 2) == second
                && collection.getStacks(owner.getUUID(), npc).stream().noneMatch(s -> s.is(Items.STONE)),
                "The second mistake did not select the count=2 DISCARD threshold");
        collection.addItems(member, npc, List.of(new ItemStack(Items.DIRT, 2)));
        collection.addItems(outsider, npc, List.of(new ItemStack(Items.DIRT, 3)));
        String otherNpc = npc + "Other";
        collection.addItems(owner, otherNpc, List.of(new ItemStack(Items.PAPER)));
        helper.assertTrue(collection.getTriggerCount(owner.getUUID(), npc) == 2
                && collection.getTriggerCount(member.getUUID(), npc) == 1
                && collection.getTriggerCount(outsider.getUUID(), npc) == 1
                && collection.getTriggerCount(owner.getUUID(), otherNpc) == 1,
                "Counts leaked between NPCs, teammates or other parties");
        helper.assertTrue(collection.getTriggerCount(UUID.randomUUID(), npc) == 0,
                "An unrelated player inherited another player's count");
        var restored = ContactQuestsTestUtil.roundTrip(collection, level.registryAccess());
        helper.assertTrue(restored.getTriggerCount(owner.getUUID(), npc) == 2
                && restored.getTriggerCount(member.getUUID(), npc) == 1
                && restored.getTriggerCount(outsider.getUUID(), npc) == 1
                && restored.getTriggerCount(owner.getUUID(), otherNpc) == 1,
                "Saving changed the player/NPC keys or counts");
        var replace = CollectionSavedData.class.getDeclaredMethod("applyReplacements", String.class,
                CollectionSavedData.ReplacerContext.class);
        replace.setAccessible(true);
        String text = (String) replace.invoke(restored, "<trigger_count>/<triggerCount>",
                new CollectionSavedData.ReplacerContext(owner, npc, restored.getTriggerCount(owner.getUUID(), npc), List.of()));
        helper.assertTrue("2/2|npc_reply".equals(text), "NPC count text placeholders changed: " + text);
        helper.succeed();
    }
}
