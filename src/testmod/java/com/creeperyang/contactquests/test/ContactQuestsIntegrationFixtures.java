package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.data.DataManager;
import com.creeperyang.contactquests.item.TeamBindingItem;
import com.creeperyang.contactquests.registry.ModItems;
import com.creeperyang.contactquests.utils.RedPacketUtils;
import com.flechazo.contact.common.item.ParcelItem;
import com.flechazo.contact.common.item.PostcardItem;
import com.creeperyang.contactquests.data.RewardDeliverySavedData;
import com.creeperyang.contactquests.data.TaskDeliverySavedData;
import com.flechazo.contact.common.block.MailboxBlock;
import com.flechazo.contact.common.registry.BlockRegistry;
import com.flechazo.contact.common.storage.PlayerMailboxData;
import com.flechazo.contact.platform.PlatformHelper;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import com.creeperyang.contactquests.quest.task.ParcelTask;
import com.creeperyang.contactquests.quest.task.PostcardTask;
import com.creeperyang.contactquests.quest.task.RedPacketTask;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.Optional;

public final class ContactQuestsIntegrationFixtures {
    private ContactQuestsIntegrationFixtures() {
    }

    public static void runCommandSmoke(ServerPlayer player) {
        ContactQuestsCommandAssertions.run(player);
        try {
            var dispatcher = player.server.getCommands().getDispatcher();
            var source = player.createCommandSourceStack();
            int binderResult = dispatcher.execute("contactquests get_binder_internal", source.withPermission(0));
            int openResult = dispatcher.execute("contactquests open 999999999999", source.withPermission(0));
            int setSelf = dispatcher.execute("contactquests admin setcount CQCommandSelf 7", source.withPermission(2));
            int setTarget = dispatcher.execute("contactquests admin setcount CQCommandTarget 9 " + player.getGameProfile().getName(), source.withPermission(2));
            var data = com.creeperyang.contactquests.data.CollectionSavedData.Companion.get(player.serverLevel());
            var beforeQuery = data.save(new CompoundTag(), player.registryAccess());
            boolean dirtyBefore = data.isDirty();
            int getSelf = dispatcher.execute("contactquests admin getcount CQCommandSelf", source.withPermission(2));
            int getTarget = dispatcher.execute("contactquests admin getcount CQCommandTarget " + player.getGameProfile().getName(), source.withPermission(2));
            int getMissing = dispatcher.execute("contactquests admin getcount CQCommandMissing", source.withPermission(2));
            boolean readOnly = dirtyBefore == data.isDirty()
                    && beforeQuery.equals(data.save(new CompoundTag(), player.registryAccess()));
            boolean denied = false;
            try { dispatcher.execute("contactquests admin setcount CQCommandSelf 99", source.withPermission(0)); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
            boolean queryDenied = false;
            try { dispatcher.execute("contactquests admin getcount CQCommandSelf", source.withPermission(0)); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { queryDenied = true; }
            ContactQuestsTestServerController.recordCase("server.commands.getcount_actual", setSelf == 1 && getSelf == 1
                    && setTarget == 1 && getTarget == 1 && getMissing == 1 && readOnly
                    && data.getTriggerCount(player.getUUID(), "CQCommandSelf") == 7
                    && data.getTriggerCount(player.getUUID(), "CQCommandTarget") == 9 && denied && queryDenied,
                    "Real getcount: self=7, target=9, absent=0; queries preserve serialized data and dirty state; permission-zero admin set/query denied");
            ContactQuestsTestServerController.recordCase("server.commands.player_permissions",
                    binderResult == 1 && openResult == 1 && denied && queryDenied,
                    "Permission-zero players can get a binder and open quest links; admin count controls still require OP");
            ContactQuestsTestServerController.recordCase(
                    "server.commands.fixed_results",
                    binderResult > 0 && openResult > 0,
                    "get_binder_internal=" + binderResult + ", open=" + openResult
            );
        } catch (Exception exception) {
            ContactQuestsTestServerController.recordCase("server.commands.getcount_actual", false, exception.toString());
            ContactQuestsTestServerController.recordCase(
                    "server.commands.fixed_results", false, exception.toString()
            );
        }
    }

    public static void prepareEditorGraph(ServerPlayer player) {
        try {
            ServerQuestFile file = ServerQuestFile.INSTANCE;
            Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
            Chapter chapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(), "cq_editor_test");
            file.getDefaultChapterGroup().addChapter(chapter);
            Quest quest = new Quest(file.newID(), chapter);
            quest.setRawTitle("ContactQuests editor regression");
            chapter.addQuest(quest);
            ParcelTask parcel = new ParcelTask(file.newID(), quest).setStackAndCount(new ItemStack(Items.DIAMOND), 2);
            parcel.setTargetAddressee("CQEditorNpc");
            quest.addTask(parcel);
            RedPacketTask red = new RedPacketTask(file.newID(), quest).setStackAndCount(new ItemStack(Items.GOLD_INGOT), 2);
            red.setTargetAddressee("CQEditorNpc");
            quest.addTask(red);
            PostcardTask card = new PostcardTask(file.newID(), quest);
            card.setTargetAddressee("CQEditorNpc");
            quest.addTask(card);
            var table = new dev.ftb.mods.ftbquests.quest.loot.RewardTable(file.newID(), file);
            table.setRawTitle("CQ UI deterministic rewards");
            table.addReward(new dev.ftb.mods.ftbquests.quest.loot.WeightedReward(
                    new dev.ftb.mods.ftbquests.quest.reward.ItemReward(file.newID(), table.getFakeQuest(),
                            new ItemStack(Items.DIAMOND), 4), 1));
            file.addRewardTable(table);
            ContactQuestsTestServerController.setEditorRewardTableId(table.getId());
            var editedTable = new dev.ftb.mods.ftbquests.quest.loot.RewardTable(file.newID(), file);
            editedTable.setRawTitle("CQ UI edited rewards");
            editedTable.addReward(new dev.ftb.mods.ftbquests.quest.loot.WeightedReward(
                    new dev.ftb.mods.ftbquests.quest.reward.ItemReward(file.newID(), editedTable.getFakeQuest(),
                            new ItemStack(Items.DIAMOND), 6), 1));
            file.addRewardTable(editedTable);
            ContactQuestsTestServerController.setEditorEditedTableId(editedTable.getId());
            var mutex = new Quest(file.newID(), chapter); mutex.setRawTitle("CQ editor unfinished mutex");
            chapter.addQuest(mutex);
            mutex.addTask(new dev.ftb.mods.ftbquests.quest.task.CheckmarkTask(file.newID(), mutex));
            ContactQuestsTestServerController.setEditorMutexId(mutex.getId());
            ContactQuestsTestUtil.refreshIndex(file);
            file.getOrCreateTeamData(team).setPlayerEditMode(player, true);
            file.markDirty();
            file.saveNow();
            ContactQuestsTestServerController.setEditorQuestId(quest.getId());
            dev.ftb.mods.ftblibrary.util.NetworkHelper.sendTo(player,
                    new dev.ftb.mods.ftbquests.net.SyncQuestsMessage(file));
            file.getTranslationManager().sendTranslationsToPlayer(player);
            ContactQuestsTestServerController.recordCase("server.editor.prepared", true,
                    "Registered server quest " + Long.toUnsignedString(quest.getId(), 16));
        } catch (Exception error) {
            error.printStackTrace();
            ContactQuestsTestServerController.recordCase("server.editor.prepared", false, error.toString());
        }
    }

    public static void verifyEditorGraph(ServerPlayer player) {
        var quest = ServerQuestFile.INSTANCE.getQuest(ContactQuestsTestServerController.getEditorQuestId());
        boolean valid = quest != null && quest.getTasks().size() == 6 && quest.getRewards().size() == 5;
        if (valid) {
            var types = quest.getRewards().stream().map(r -> r.getType().getTypeForNBT().toString()).toList();
            valid = types.stream().distinct().count() == 5 && quest.getRewards().stream()
                    .filter(r -> r instanceof com.creeperyang.contactquests.quest.reward.ParcelRandomReward)
                    .allMatch(r -> {
                        var table = ((com.creeperyang.contactquests.quest.reward.ParcelRandomReward) r).getTable();
                        return table != null && table.getRawTitle().equals("CQ UI deterministic rewards")
                                && table.getWeightedRewards().size() == 1;
                    });
        }
        ContactQuestsTestServerController.recordCase("server.editor.all_five_saved", valid,
                "Server objects created by real editor packets: tasks=" + (quest == null ? -1 : quest.getTasks().size())
                        + ", rewards=" + (quest == null ? -1 : quest.getRewards().size()));
    }

    public static void verifyEditorCancel(ServerPlayer player, long taskId) {
        var task = (ParcelTask) ServerQuestFile.INSTANCE.getTask(taskId);
        ContactQuestsTestServerController.recordCase("server.editor.cancel_preserves_fields", task != null
                        && task.getCount() == 2 && task.getItemStack().is(Items.DIAMOND)
                        && task.getTargetAddressee().equals("CQEditorNpc"),
                "Native Cancel preserved original diamond count=2 and NPC=CQEditorNpc on the server");
    }

    public static void prepareEditorRewardClaims(ServerPlayer player) {
        try {
            var file = ServerQuestFile.INSTANCE;
            var quest = file.getQuest(ContactQuestsTestServerController.getEditorQuestId());
            var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
            if (quest == null || quest.getRewards().size() != 5 || PlatformHelper.getMailboxPos(team.getId()) == null) {
                throw new IllegalStateException("Actual editor graph and bound party mailbox are required");
            }
            var data = file.getOrCreateTeamData(team);
            data.setLocked(false);
            ((com.creeperyang.contactquests.bridge.ITeamDataExtension) data).contactQuests$unlockTag("CQEditorRequiredTag");
            for (var task : quest.getTasks()) {
                var contact = (com.creeperyang.contactquests.quest.task.ContactTask) task;
                data.setProgress(task, contact.getCount());
                if (!data.isCompleted(task) || data.getProgress(task) != contact.getCount()) {
                    throw new IllegalStateException("Task completion fixture did not reach its exact count");
                }
            }
            if (!data.isCompleted(quest)) throw new IllegalStateException("Editor quest is not complete");
            for (var reward : quest.getRewards()) {
                var contact = (com.creeperyang.contactquests.quest.reward.ParcelRewardBase) reward;
                com.creeperyang.contactquests.compat.kubejs.ContactKubeJSPlugin.setNpcConfig(
                        player.serverLevel(), contact.getTargetAddressee(), 0, true);
                if (!data.getClaimType(player.getUUID(), reward).canClaim()) {
                    throw new IllegalStateException("Actual reward is not claimable: " + reward.getCodeString());
                }
            }
            com.creeperyang.contactquests.data.RewardDistributionManager.INSTANCE.onServerTick(player.serverLevel());
            PlatformHelper.resetMailboxContents(team.getId());
            player.setShiftKeyDown(false);
            data.setPlayerEditMode(player, false);
            ContactQuestsTestServerController.recordCase("server.editor.claims_prepared", true,
                    "Actual six editor tasks completed with exact counts; five unclaimed rewards and bound party mailbox ready");
        } catch (Exception error) {
            ContactQuestsTestServerController.recordCase("server.editor.claims_prepared", false, error.toString());
        }
    }

    public static void verifyEditorRewardClaims(ServerPlayer player, boolean repeated) {
        try {
            var quest = ServerQuestFile.INSTANCE.getQuest(ContactQuestsTestServerController.getEditorQuestId());
            var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
            var data = ServerQuestFile.INSTANCE.getOrCreateTeamData(team);
            boolean claimed = quest != null && quest.getRewards().size() == 5
                    && quest.getRewards().stream().allMatch(r -> data.isRewardClaimed(player.getUUID(), r));
            var mailbox = PlatformHelper.getMailboxContents(team.getId());
            int apples = 0, emeralds = 0, diamonds = 0, cards = 0, unexpected = 0;
            boolean cardData = true, redData = true;
            for (int i = 0; i < mailbox.getContainerSize(); i++) {
                var mail = mailbox.getItem(i);
                if (mail.getItem() instanceof PostcardItem) {
                    cards += mail.getCount();
                    cardData &= ContactQuestsEditorAssertions.STYLE.equals(new com.creeperyang.contactquests.compat.kubejs.MailReceivedEventJS(
                            player, "test", java.util.List.of(mail), false).getPostcardStyle(mail))
                            && PostcardItem.getText(mail).equals(com.creeperyang.contactquests.quest.reward.PostcardReward.Companion.processText(
                            ContactQuestsEditorAssertions.CARD_TEXT.replace("\\n", "\n"), player));
                }
                else if (!(mail.getItem() instanceof ParcelItem)
                        && !(mail.getItem() instanceof com.flechazo.contact.common.item.RedPacketItem)
                        && !mail.isEmpty()) unexpected += mail.getCount();
                for (var stack : mail.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,
                        net.minecraft.world.item.component.ItemContainerContents.EMPTY).stream().toList()) {
                    if (stack.is(Items.APPLE)) apples += stack.getCount();
                    else if (stack.is(Items.EMERALD)) emeralds += stack.getCount();
                    else if (stack.is(Items.DIAMOND)) diamonds += stack.getCount();
                    else unexpected += stack.getCount();
                }
                if (mail.getItem() instanceof com.flechazo.contact.common.item.RedPacketItem) {
                    var helper = new com.creeperyang.contactquests.compat.kubejs.MailReceivedEventJS(player, "test", java.util.List.of(mail), false);
                    redData &= helper.getRedPacketBlessing(mail).equals(com.creeperyang.contactquests.quest.reward.RedPacketReward.Companion.processText(
                            ContactQuestsEditorAssertions.BLESSING, player));
                }
            }
            ContactQuestsTestServerController.recordCase(repeated ? "server.editor.repeated_claim_no_duplicate"
                            : "server.editor.button_claim_delivery",
                    claimed && apples == 3 && emeralds == 2 && diamonds == 12 && cards == 1 && unexpected == 0 && cardData && redData,
                    "Actual button packets: five claimed=" + claimed + ", apple=" + apples + ", emerald=" + emeralds
                            + ", diamond=" + diamonds + ", postcard=" + cards + ", unexpected=" + unexpected
                            + ", postcard style/text=" + cardData + ", red packet text=" + redData);
        } catch (Exception error) {
            ContactQuestsTestServerController.recordCase(repeated ? "server.editor.repeated_claim_no_duplicate"
                    : "server.editor.button_claim_delivery", false, error.toString());
        }
    }

    public static void verifyEditorFields(ServerPlayer player) {
        try {
            var file = ServerQuestFile.INSTANCE;
            var quest = file.getQuest(ContactQuestsTestServerController.getEditorQuestId());
            ContactQuestsTestServerController.recordCase("server.editor.task_fields_saved",
                    ContactQuestsEditorAssertions.taskFields(quest), "Three real task edits include Long count=3000000005, components, NPC, text and style");
            ContactQuestsTestServerController.recordCase("server.editor.reward_fields_saved",
                    ContactQuestsEditorAssertions.rewardFields(quest, ContactQuestsTestServerController.getEditorEditedTableId()),
                    "Five real reward edits include quantities, table, sender, tags, multiplier and Contact card");
            ContactQuestsTestServerController.recordCase("server.editor.quest_rules_saved",
                    ContactQuestsEditorAssertions.questRules(quest, ContactQuestsTestServerController.getEditorMutexId(), player.registryAccess()),
                    "Native quest editor saved ANY tag dependency and NUMBER mutex");
            file.saveNow();
            var card = quest.getRewards().stream().filter(r -> r instanceof com.creeperyang.contactquests.quest.reward.PostcardReward)
                    .map(r -> (com.creeperyang.contactquests.quest.reward.PostcardReward) r).findFirst().orElseThrow();
            ContactQuestsTestServerController.setEditorPostcardText(com.creeperyang.contactquests.quest.reward.PostcardReward.Companion.processText(
                    card.getPostcardText().replace("\\n", "\n"), player));
        } catch (Exception error) {
            ContactQuestsTestServerController.recordCase("server.editor.fields_failure", false, error.toString());
        }
    }

    public static void collectEditorPostcard(ServerPlayer player) {
        var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
        var pos = PlatformHelper.getMailboxPos(team.getId());
        player.setShiftKeyDown(false); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var result = player.serverLevel().getBlockState(pos.pos()).useWithoutItem(player.serverLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(pos.pos()), Direction.UP, pos.pos(), false));
        boolean received = player.getInventory().items.stream().anyMatch(s -> s.getItem() instanceof PostcardItem
                && PostcardItem.getText(s).equals(ContactQuestsTestServerController.getEditorPostcardText()));
        if (received) {
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                var card = player.getInventory().getItem(i);
                if (!(card.getItem() instanceof PostcardItem)) continue;
                var original = player.getInventory().getItem(0);
                player.getInventory().setItem(0, card);
                player.getInventory().setItem(i, original);
                player.getInventory().selected = 0;
                player.inventoryMenu.broadcastChanges();
                break;
            }
        }
        ContactQuestsTestServerController.recordCase("server.editor.postcard_collected", result.consumesAction() && received,
                "Real bound party mailbox collected Contact postcard with edited, replaced text; interaction=" + result);
    }

    public static void verifyMailboxAndRejectedSend(ServerPlayer player) {
        var originalMenu = player.containerMenu;
        try {
            var postbox = new com.flechazo.contact.common.screenhandler.PostboxScreenHandler(88, player.getInventory(), false);
            player.containerMenu = postbox;
            int originalPending = PlatformHelper.getMailList().size();
            com.flechazo.contact.helper.NetworkHelper.handleSendMail(player, "CQTest_AliasCandidate", 0);
            SimpleContainer empty = new SimpleContainer(4);
            ItemStack emptyParcel = ParcelItem.getParcel(empty, false, "TestRejected");
            postbox.parcel.setItem(0, emptyParcel);
            com.flechazo.contact.helper.NetworkHelper.handleSendMail(player, "CQTest_AliasCandidate", 0);
            boolean emptyPreserved = ItemStack.matches(postbox.parcel.getItem(0), emptyParcel);
            SimpleContainer contents = new SimpleContainer(4);
            contents.setItem(0, new ItemStack(Items.DIAMOND, 2));
            ItemStack unknown = ParcelItem.getParcel(contents, false, "TestRejected");
            postbox.parcel.setItem(0, unknown);
            com.flechazo.contact.helper.NetworkHelper.handleSendMail(player, "CQUnknownRecipient", 0);
            boolean unknownPreserved = ItemStack.matches(postbox.parcel.getItem(0), unknown);
            boolean queueUnchanged = PlatformHelper.getMailList().size() == originalPending
                    && !PlatformHelper.getUuidToContents().containsKey(null);
            ContactQuestsTestServerController.recordCase("server.postbox.rejected_mail_preserved",
                    emptyPreserved && unknownPreserved && queueUnchanged,
                    "empty parcel=" + emptyPreserved + ", unknown recipient=" + unknownPreserved + ", queue unchanged=" + queueUnchanged);

            Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
            var data = PlatformHelper.data();
            var reloaded = PlayerMailboxData.deserializeForAttachment(data.serializeForAttachment(player.registryAccess()), player.registryAccess());
            var pos = PlatformHelper.getMailboxPos(team.getId());
            boolean position = pos != null && pos.equals(reloaded.getMailboxPos(team.getId()));
            boolean contentsEqual = true;
            var before = data.getMailboxContents(team.getId());
            var after = reloaded.getMailboxContents(team.getId());
            for (int i = 0; i < before.getContainerSize(); i++) {
                contentsEqual &= ItemStack.matches(before.getItem(i), after.getItem(i));
            }
            var block = pos == null ? null : player.serverLevel().getBlockEntity(pos.pos());
            boolean blockReloaded = false;
            if (block != null) {
                CompoundTag nbt = block.saveWithFullMetadata(player.registryAccess());
                var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos.pos(), block.getBlockState(), nbt,
                        player.registryAccess());
                blockReloaded = restored instanceof com.creeperyang.contactquests.bridge.IMailboxTeamAccessor accessor
                        && team.getId().equals(accessor.contactQuests$getTeamId());
            }
            ContactQuestsTestServerController.recordCase("server.mailbox.attachment.persistence",
                    position && contentsEqual && blockReloaded,
                    "actual attachment location=" + position + ", exact contents=" + contentsEqual + ", block team reload=" + blockReloaded);
        } catch (Exception error) {
            ContactQuestsTestServerController.recordCase("server.mailbox.attachment.persistence", false, error.toString());
        } finally {
            player.containerMenu = originalMenu;
        }
    }

    public static void runTaskRuntimeFlow(ServerPlayer player) {
        try {
            Team team = ensurePartyTeam(player);
            ServerQuestFile file = ServerQuestFile.INSTANCE;
            Chapter chapter = new Chapter(
                    file.newID(), file, file.getDefaultChapterGroup(), "contactquests_runtime_flow"
            );
            file.getDefaultChapterGroup().addChapter(chapter);
            Quest quest = new Quest(file.newID(), chapter);
            chapter.addQuest(quest);

            ParcelTask parcelTask = new ParcelTask(file.newID(), quest)
                    .setStackAndCount(new ItemStack(Items.DIAMOND), 1);
            parcelTask.setTargetAddressee("CQTest_RuntimeParcel");
            quest.addTask(parcelTask);

            RedPacketTask redPacketTask = new RedPacketTask(file.newID(), quest)
                    .setStackAndCount(new ItemStack(Items.GOLD_INGOT), 1);
            redPacketTask.setBlessing("runtime blessing");
            redPacketTask.setTargetAddressee("CQTest_RuntimeRedPacket");
            quest.addTask(redPacketTask);

            PostcardTask postcardTask = new PostcardTask(file.newID(), quest);
            postcardTask.setPostcardStyle("contact:default");
            postcardTask.setPostcardText("runtime postcard");
            postcardTask.setTargetAddressee("CQTest_RuntimePostcard");
            quest.addTask(postcardTask);

            ContactQuestsTestUtil.refreshIndex(file);
            ContactQuestsTestServerController.setRuntimeStateQuestId(quest.getId());
            DataManager.INSTANCE.init();
            TeamData teamData = file.getOrCreateTeamData(team);

            SimpleContainer parcelContents = new SimpleContainer(4);
            parcelContents.setItem(0, new ItemStack(Items.DIAMOND));
            ItemStack parcel = ParcelItem.getParcel(parcelContents, false, "RuntimeTest");
            boolean parcelAccepted = DataManager.INSTANCE.matchParcelTaskItem(
                    player, parcel, parcel.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,
                            net.minecraft.world.item.component.ItemContainerContents.EMPTY),
                    "CQTest_RuntimeParcel"
            );

            SimpleContainer redContents = new SimpleContainer(1);
            redContents.setItem(0, new ItemStack(Items.GOLD_INGOT));
            ItemStack redPacket = RedPacketUtils.INSTANCE.getRedPacket(
                    redContents, "runtime blessing", "RuntimeTest"
            );
            boolean redPacketAccepted = DataManager.INSTANCE.matchRedPacketTaskItem(
                    player, redPacket, "CQTest_RuntimeRedPacket"
            );

            ItemStack postcard = PostcardItem.getPostcard(
                    ResourceLocation.fromNamespaceAndPath("contact", "default"), false
            );
            postcard = PostcardItem.setText(postcard, "runtime postcard");
            boolean postcardAccepted = DataManager.INSTANCE.matchPostcardTaskItem(
                    player, postcard, "CQTest_RuntimePostcard"
            );

            for (int tick = 0; tick < 200; tick++) {
                TaskDeliverySavedData.Companion.get(player.serverLevel()).tick(player.serverLevel());
            }

            ContactQuestsTestServerController.recordCase(
                    "server.task.parcel_runtime",
                    parcelAccepted && teamData.getProgress(parcelTask) == parcelTask.getCount()
                            && teamData.isCompleted(parcelTask),
                    "accepted=" + parcelAccepted + ", progress=" + teamData.getProgress(parcelTask)
            );
            ContactQuestsTestServerController.recordCase(
                    "server.task.red_packet_runtime",
                    redPacketAccepted && teamData.getProgress(redPacketTask) == redPacketTask.getCount()
                            && teamData.isCompleted(redPacketTask),
                    "accepted=" + redPacketAccepted + ", progress=" + teamData.getProgress(redPacketTask)
            );
            ContactQuestsTestServerController.recordCase(
                    "server.task.postcard_runtime",
                    postcardAccepted && teamData.getProgress(postcardTask) == postcardTask.getCount()
                            && teamData.isCompleted(postcardTask),
                    "accepted=" + postcardAccepted + ", progress=" + teamData.getProgress(postcardTask)
            );
        } catch (Exception exception) {
            String message = exception.toString();
            ContactQuestsTestServerController.recordCase("server.task.parcel_runtime", false, message);
            ContactQuestsTestServerController.recordCase("server.task.red_packet_runtime", false, message);
            ContactQuestsTestServerController.recordCase("server.task.postcard_runtime", false, message);
        }
    }

    public static void runTeamBindingAndRewardRetry(ServerPlayer player) {
        try {
            ServerLevel level = player.serverLevel();
            Team team = ensurePartyTeam(player);
            BlockPos lower = player.blockPosition().offset(2, 0, 0);
            BlockPos conflictLower = lower.offset(4, 0, 0);
            placeMailbox(level, lower);
            placeMailbox(level, conflictLower);

            TeamBindingItem binder = (TeamBindingItem) ModItems.INSTANCE.getTEAM_BINDING_CARD().get();
            player.setShiftKeyDown(true);

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(binder, 1));
            InteractionResult first = useBinder(player, binder, lower);
            BlockPos expectedUpper = lower.above();
            boolean firstBound = first.consumesAction()
                    && expectedUpper.equals(PlatformHelper.getMailboxPos(team.getId()).pos())
                    && player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty();

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(binder, 1));
            InteractionResult repeated = useBinder(player, binder, lower);
            boolean repeatedBound = repeated.consumesAction()
                    && player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty();

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(binder, 1));
            InteractionResult conflict = useBinder(player, binder, conflictLower);
            boolean conflictRejected = conflict == InteractionResult.FAIL
                    && player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1;

            ContactQuestsTestServerController.recordCase(
                    "server.team_binding.full_flow",
                    firstBound && repeatedBound && conflictRejected,
                    "first=" + first + ", repeated=" + repeated + ", conflict=" + conflict
            );

            recordMailboxPersistence(level, team, expectedUpper);
            runMailboxFullRetry(level, player, team);
        } catch (Exception exception) {
            ContactQuestsTestServerController.recordCase(
                    "server.team_binding.full_flow",
                    false,
                    exception.toString()
            );
        }
    }

    private static Team ensurePartyTeam(ServerPlayer player) throws Exception {
        var manager = FTBTeamsAPI.api().getManager();
        Optional<Team> existing = manager.getTeamForPlayer(player);
        if (existing.isPresent() && existing.get().isPartyTeam()) {
            return existing.get();
        }
        return manager.createPartyTeam(
                player,
                "ContactQuestsTestTeam",
                "ContactQuests deterministic test team",
                Color4I.WHITE
        );
    }

    private static void placeMailbox(ServerLevel level, BlockPos lower) {
        BlockState base = BlockRegistry.GREEN_MAILBOX.get().defaultBlockState();
        BlockState lowerState = base.setValue(MailboxBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upperState = base.setValue(MailboxBlock.HALF, DoubleBlockHalf.UPPER);
        level.setBlock(lower, lowerState, 3);
        level.setBlock(lower.above(), upperState, 3);
    }

    private static InteractionResult useBinder(ServerPlayer player, TeamBindingItem binder, BlockPos lower) {
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(lower),
                Direction.UP,
                lower,
                false
        );
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        return binder.useOn(context);
    }

    private static void recordMailboxPersistence(ServerLevel level, Team team, BlockPos upper) {
        try {
            PlayerMailboxData original = new PlayerMailboxData();
            original.setMailboxData(team.getId(), level.dimension(), upper);
            SimpleContainer contents = new SimpleContainer(1);
            contents.setItem(0, new ItemStack(Items.DIAMOND, 2));
            original.setMailboxContents(team.getId(), contents);

            CompoundTag tag = original.writeToNBT(new CompoundTag(), level.registryAccess());
            PlayerMailboxData reloaded = new PlayerMailboxData();
            reloaded.readFromNBT(tag, level.registryAccess());
            boolean position = upper.equals(reloaded.getMailboxPos(team.getId()).pos());
            boolean content = reloaded.getMailboxContents(team.getId()).getItem(0).is(Items.DIAMOND)
                    && reloaded.getMailboxContents(team.getId()).getItem(0).getCount() == 2;
            ContactQuestsTestServerController.recordCase(
                    "server.team_binding.persistence",
                    position && content,
                    "position=" + position + ", content=" + content
            );
        } catch (Exception exception) {
            ContactQuestsTestServerController.recordCase(
                    "server.team_binding.persistence", false, exception.toString()
            );
        }
    }

    private static void runMailboxFullRetry(ServerLevel level, ServerPlayer player, Team team) throws Exception {
        int retryInterval = readRetryInterval();
        SimpleContainer contents = PlatformHelper.getMailboxContents(team.getId());
        for (int slot = 0; slot < contents.getContainerSize(); slot++) {
            contents.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        PlatformHelper.setMailboxContents(team.getId(), contents);
        boolean wasFull = PlatformHelper.isMailboxFull(team.getId());

        RewardDeliverySavedData data = RewardDeliverySavedData.Companion.get(level);
        data.addPendingReward(player.getUUID(), new ItemStack(Items.DIAMOND), 0, "ContactQuestsTest");
        data.tick(level);

        CompoundTag queuedTag = new CompoundTag();
        data.save(queuedTag, level.registryAccess());
        ListTag queued = queuedTag.getList("PendingRewards", 10);
        boolean queuedForRetry = false;
        for (int index = 0; index < queued.size(); index++) {
            CompoundTag entry = queued.getCompound(index);
            if (entry.getString("sender").equals("ContactQuestsTest")
                    && entry.getInt("ticks") == retryInterval) {
                queuedForRetry = true;
                break;
            }
        }

        PlatformHelper.resetMailboxContents(team.getId());
        for (int tick = 0; tick <= retryInterval; tick++) {
            data.tick(level);
        }
        CompoundTag deliveredTag = new CompoundTag();
        data.save(deliveredTag, level.registryAccess());
        ListTag remaining = deliveredTag.getList("PendingRewards", 10);
        boolean testEntryRemoved = true;
        for (int index = 0; index < remaining.size(); index++) {
            if (remaining.getCompound(index).getString("sender").equals("ContactQuestsTest")) {
                testEntryRemoved = false;
                break;
            }
        }
        boolean delivered = testEntryRemoved && !PlatformHelper.isMailboxEmpty(team.getId());

        ContactQuestsTestServerController.recordCase(
                "server.reward.delivery_retry",
                queuedForRetry && delivered,
                "wasFull=" + wasFull + ", queuedForRetry=" + queuedForRetry + ", delivered=" + delivered
        );
    }

    private static int readRetryInterval() throws Exception {
        Class<?> configClass = Class.forName("com.creeperyang.contactquests.config.ContactConfig");
        Field field = configClass.getDeclaredField("retryInterval");
        field.setAccessible(true);
        Object value = field.get(null);
        return (int) value.getClass().getMethod("get").invoke(value);
    }
}
