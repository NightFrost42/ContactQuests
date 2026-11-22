package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.config.*;
import com.creeperyang.contactquests.data.*;
import com.creeperyang.contactquests.item.TeamBindingItem;
import com.creeperyang.contactquests.quest.reward.*;
import com.creeperyang.contactquests.quest.task.*;
import com.creeperyang.contactquests.bridge.IMailboxTeamAccessor;
import com.creeperyang.contactquests.utils.ContactMailNbt;
import com.creeperyang.contactquests.utils.RedPacketUtils;
import com.flechazo.contact.common.block.MailboxBlock;
import com.flechazo.contact.common.item.ParcelItem;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.common.registry.BlockRegistry;
import com.flechazo.contact.helper.NetworkHelper;
import com.flechazo.contact.platform.PlatformHelper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbquests.quest.*;
import dev.ftb.mods.ftbquests.quest.loot.RewardTable;
import dev.ftb.mods.ftbquests.quest.loot.WeightedReward;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.server.ServerStartedEvent;

import java.nio.file.*;
import java.util.*;

/** No script classes or data are needed to prepare or verify this environment. */
public final class ContactQuestsNoKubejsFixtures {
    public static final String NPC = "CQNoKubeTaskNpc";
    private static long questId = -1;
    private static BlockPos mailboxUpper;

    private ContactQuestsNoKubejsFixtures() {}

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        ContactQuestsTestServerController.reset();
        ContactQuestsTestServerController.recordCase("server.no_kubejs.dependencies_absent", dependenciesAbsent(),
                "KubeJS and Rhino mods and representative classes must both be absent");
        ContactQuestsTestServerController.recordCase("server.no_jei.dependencies_absent", recipeViewerAbsent(),
                "JEI mod and representative API class must be absent in the real server");
        ContactQuestsTestServerController.markLoaded();
    }

    public static boolean dependenciesAbsent() {
        if (ModList.get().isLoaded("kubejs") || ModList.get().isLoaded("rhino")) return false;
        for (String name : List.of("dev.latvian.mods.kubejs.event.KubeEvent", "dev.latvian.mods.rhino.Context")) {
            try { Class.forName(name, false, ContactQuestsNoKubejsFixtures.class.getClassLoader()); return false; }
            catch (ClassNotFoundException expected) { /* Verify the jar is absent, not just disabled. */ }
        }
        return true;
    }

    public static boolean recipeViewerAbsent() {
        if (ModList.get().isLoaded("jei")) return false;
        try {
            Class.forName("mezz.jei.api.IModPlugin", false, ContactQuestsNoKubejsFixtures.class.getClassLoader());
            return false;
        } catch (ClassNotFoundException expected) { return true; }
    }

    public static void addResultFields(JsonObject result) {
        if (questId >= 0) result.addProperty("noKubejsQuestId", Long.toUnsignedString(questId, 16));
    }

    public static void run(ServerPlayer player, String phase) {
        try {
            switch (phase) {
                case "prepare" -> prepare(player);
                case "mail" -> mail(player);
                case "claim" -> claim(player);
                case "rejoin" -> rejoin(player);
                default -> throw new IllegalArgumentException("Unknown no-KubeJS phase: " + phase);
            }
        } catch (Exception | LinkageError error) {
            error.printStackTrace();
            ContactQuestsTestServerController.recordCase("server.no_kubejs." + phase, false, error.toString());
        }
    }

    private static void prepare(ServerPlayer player) throws Exception {
        require(dependenciesAbsent(), "Optional script libraries are still on the runtime classpath");
        var level = player.serverLevel();
        var file = ServerQuestFile.INSTANCE;
        var manager = FTBTeamsAPI.api().getManager();
        var team = manager.getTeamForPlayer(player).orElseThrow();
        if (team.isPlayerTeam()) team = manager.createPartyTeam(player, "CQ no-KubeJS party", "isolated optional-dependency regression", Color4I.WHITE);
        var data = file.getOrCreateTeamData(team);
        data.setLocked(false);
        ContactConfig.INSTANCE.getEnableDeliveryTime().set(false);
        Chapter chapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(), "cq_no_kubejs");
        chapter.setRawTitle("CQ without KubeJS");
        file.getDefaultChapterGroup().addChapter(chapter);
        Quest quest = new Quest(file.newID(), chapter); quest.setRawTitle("No KubeJS: real mail and five rewards"); chapter.addQuest(quest);
        questId = quest.getId();
        var parcel = new ParcelTask(file.newID(), quest).setStackAndCount(new ItemStack(Items.DIAMOND), 2);
        parcel.setTargetAddressee(NPC); quest.addTask(parcel);
        var red = new RedPacketTask(file.newID(), quest).setStackAndCount(new ItemStack(Items.GOLD_INGOT), 2);
        red.setTargetAddressee(NPC); red.setBlessing("without scripts"); quest.addTask(red);
        var card = new PostcardTask(file.newID(), quest); card.setTargetAddressee(NPC);
        card.setPostcardStyle("contact:creeper"); card.setPostcardText("without scripts"); quest.addTask(card);
        var parcelReward = new ParcelReward(file.newID(), quest); parcelReward.setItem(new ItemStack(Items.APPLE)); parcelReward.setCount(2);
        var redReward = new RedPacketReward(file.newID(), quest); redReward.setItem(new ItemStack(Items.EMERALD)); redReward.setCount(3);
        var cardReward = new PostcardReward(file.newID(), quest); cardReward.setPostcardStyle("contact:creeper"); cardReward.setPostcardText("without scripts <player_name>");
        var table = new RewardTable(file.newID(), file);
        table.addReward(new WeightedReward(new ItemReward(file.newID(), table.getFakeQuest(), new ItemStack(Items.DIAMOND), 4), 1));
        file.addRewardTable(table);
        var random = new ParcelRandomReward(file.newID(), quest); random.setTable(table);
        var all = new ParcelAllTableReward(file.newID(), quest); all.setTable(table);
        for (var reward : List.of(parcelReward, redReward, cardReward, random, all)) {
            reward.setTargetAddressee("CQNoKubeRewardNpc"); reward.setEnableTeamMultiplier(false); quest.addReward(reward);
        }

        // Separate graph for the actual client Tag/mutex editor. This graph
        // never depends on a script adapter or script-defined NPC configuration.
        Quest editor = new Quest(file.newID(), chapter); editor.setRawTitle("Native Tag and mutex editor"); chapter.addQuest(editor);
        editor.addTask(new CheckmarkTask(file.newID(), editor));
        Quest mutex = new Quest(file.newID(), chapter); mutex.setRawTitle("CQ editor unfinished mutex"); chapter.addQuest(mutex);
        mutex.addTask(new CheckmarkTask(file.newID(), mutex));
        ContactQuestsTestServerController.setEditorQuestId(editor.getId());
        ContactQuestsTestServerController.setEditorMutexId(mutex.getId());
        ContactQuestsTestUtil.refreshIndex(file); file.markDirty(); DataManager.INSTANCE.init();
        NpcConfigManager.INSTANCE.getNpcData(NPC).setDeliveryTime(0);
        var rule = new ErrorSolveData(); rule.setReturnType(ErrorSolveType.SAVE);
        NpcConfigManager.INSTANCE.getNpcData(NPC).getErrorSolve().clear();
        NpcConfigManager.INSTANCE.getNpcData(NPC).getErrorSolve().add(rule);
        var saveNpcConfig = NpcConfigManager.class.getDeclaredMethod("save");
        saveNpcConfig.setAccessible(true); saveNpcConfig.invoke(NpcConfigManager.INSTANCE);
        var lower = player.blockPosition().offset(3, 0, 0);
        mailboxUpper = lower.above();
        var state = BlockRegistry.GREEN_MAILBOX.get().defaultBlockState();
        level.setBlock(lower, state.setValue(MailboxBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(mailboxUpper, state.setValue(MailboxBlock.HALF, DoubleBlockHalf.UPPER), 3);
        var binder = (TeamBindingItem) com.creeperyang.contactquests.registry.ModItems.INSTANCE.getTEAM_BINDING_CARD().get();
        player.setShiftKeyDown(true); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(binder));
        var binding = binder.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(mailboxUpper), Direction.UP, mailboxUpper, false)));
        player.setShiftKeyDown(false); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        require(binding.consumesAction() && PlatformHelper.getMailboxPos(team.getId()) != null
                && PlatformHelper.getMailboxPos(player.getUUID()) == null, "Non-personal party mailbox binding is invalid");
        PlatformHelper.resetMailboxContents(team.getId());
        data.setCanEdit(player, true);
        file.markDirty(); file.saveNow(); sync(player);
        require(DataManager.INSTANCE.getAvailableTargets(player).containsKey(NPC)
                && DataManager.INSTANCE.getHiddenTargets(player).isEmpty(), "Task NPC lookup without scripts failed");
        ContactQuestsCommandAssertions.runNoTeamBoundary(player);
        ContactQuestsTestServerController.recordCase("server.no_kubejs.prepare", true,
                "Three native tasks, five rewards, native editor graph and exclusive non-personal party mailbox prepared");
    }

    private static Quest currentQuest() { return ServerQuestFile.INSTANCE.getQuest(questId); }

    private static void mail(ServerPlayer player) {
        var quest = currentQuest(); var data = ServerQuestFile.INSTANCE.getOrCreateTeamData(player);
        var collection = CollectionSavedData.Companion.get(player.serverLevel());
        int countBefore = collection.getTriggerCount(player.getUUID(), NPC);
        var container = new SimpleContainer(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.DIRT, 3));
        sendThroughContact(player, ParcelItem.getParcel(container, false, "NoKubeJS"));
        var parcelTask = quest.getTasks().stream().filter(t -> t instanceof ParcelTask).findFirst().orElseThrow();
        require(data.getProgress(parcelTask) == 2, "Mixed parcel did not credit exactly diamond x2");
        CompoundTag saved = collection.save(new CompoundTag());
        var players = saved.getList("playerDataMap", 10);
        int dirt = 0, unexpected = 0;
        for (int i = 0; i < players.size(); i++) {
            var entry = players.getCompound(i);
            if (!entry.getUUID("uuid").equals(player.getUUID())) continue;
            var npcs = entry.getList("npcData", 10);
            for (int j = 0; j < npcs.size(); j++) {
                var npc = npcs.getCompound(j); if (!npc.getString("name").equals(NPC)) continue;
                var items = npc.getList("itemStacks", 10);
                for (int k = 0; k < items.size(); k++) {
                    var stack = ItemStack.of(items.getCompound(k));
                    if (stack.is(Items.DIRT)) dirt += stack.getCount(); else if (!stack.isEmpty()) unexpected += stack.getCount();
                }
            }
        }
        require(dirt == 3 && unexpected == 0 && collection.getTriggerCount(player.getUUID(), NPC) == countBefore + 1,
                "Mixed-mail remainder or wrong-mail batch count is incorrect: dirt=" + dirt + ", unexpected=" + unexpected);
        ContactQuestsTestServerController.recordCase("server.no_kubejs.mixed_mail", true,
                "Real Contact send consumed envelope, credited diamond=2, retained dirt=3 once, wrong-mail batches=1");
        sendThroughContact(player, RedPacketUtils.INSTANCE.getRedPacket(new SimpleContainer(new ItemStack(Items.GOLD_INGOT, 2)), "without scripts", "NoKubeJS"));
        var card = PostcardItem.getPostcard(new ResourceLocation("contact:creeper"), false);
        PostcardItem.setText(card, "without scripts");
        sendThroughContact(player, card);
        var redTask = quest.getTasks().stream().filter(t -> t instanceof RedPacketTask).findFirst().orElseThrow();
        var postcardTask = quest.getTasks().stream().filter(t -> t instanceof PostcardTask).findFirst().orElseThrow();
        require(data.getProgress(redTask) == 2 && data.getProgress(postcardTask) == 1 && data.isCompleted(quest),
                "Three native mail task quantities did not complete exactly");
        RewardDistributionManager.INSTANCE.onServerTick(player.serverLevel());
        var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
        var mailbox = PlatformHelper.getMailboxContents(team.getId());
        int replies = 0, unexpectedReplies = 0;
        for (int i = 0; i < mailbox.getContainerSize(); i++) {
            var reply = mailbox.getItem(i);
            if (reply.isEmpty()) continue;
            if (reply.getItem() instanceof PostcardItem && PostcardItem.getText(reply).equals("Hello World!")) replies += reply.getCount();
            else unexpectedReplies += reply.getCount();
        }
        require(replies == 1 && unexpectedReplies == 0, "NPC SAVE acknowledgement quantity mismatch: replies=" + replies + ", unexpected=" + unexpectedReplies);
        ContactQuestsTestServerController.recordCase("server.no_kubejs.npc_response", true,
                "Native SAVE policy delivered exactly one Contact postcard acknowledgement for one mixed-mail remainder batch");
        PlatformHelper.resetMailboxContents(team.getId());
        ContactQuestsTestServerController.recordCase("server.no_kubejs.mail", true,
                "Real Contact parcel/red packet/postcard routes completed exact task progress [2,2,1]");
        sync(player);
    }

    private static void sendThroughContact(ServerPlayer player, ItemStack mail) {
        var original = player.containerMenu;
        try {
            var postbox = new com.flechazo.contact.common.screenhandler.PostboxScreenHandler(90, player.getInventory(), false);
            player.containerMenu = postbox;
            postbox.parcel.setItem(0, mail);
            NetworkHelper.handleSendMail(player, NPC, 0);
            require(postbox.parcel.getItem(0).isEmpty(), "Contact did not consume the accepted envelope");
        } finally { player.containerMenu = original; }
    }

    private static void claim(ServerPlayer player) throws Exception {
        var quest = currentQuest(); var data = ServerQuestFile.INSTANCE.getOrCreateTeamData(player);
        require(quest.getRewards().size() == 5 && data.isCompleted(quest), "Quest or actual five reward objects are missing");
        for (var reward : quest.getRewards()) {
            data.claimReward(player, reward, false);
            require(data.isRewardClaimed(player.getUUID(), reward), "Native claim was rejected: " + reward.getClass().getSimpleName());
            data.claimReward(player, reward, false);
        }
        RewardDistributionManager.INSTANCE.onServerTick(player.serverLevel());
        var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
        assertRewardContents(player, team.getId(), false);
        ContactQuestsTestServerController.recordCase("server.no_kubejs.claim", true,
                "Five real reward objects claimed twice: exact mailbox apple=2, emerald=3, diamond=8, Contact postcard=1");
        var snapshot = new JsonObject(); snapshot.addProperty("questId", quest.getCodeString());
        snapshot.addProperty("teamId", team.getId().toString()); snapshot.addProperty("playerId", player.getUUID().toString());
        snapshot.addProperty("mailboxX", mailboxUpper.getX()); snapshot.addProperty("mailboxY", mailboxUpper.getY()); snapshot.addProperty("mailboxZ", mailboxUpper.getZ());
        snapshot.addProperty("editorQuestId", Long.toUnsignedString(ContactQuestsTestServerController.getEditorQuestId(), 16));
        snapshot.addProperty("editorMutexId", Long.toUnsignedString(ContactQuestsTestServerController.getEditorMutexId(), 16));
        Path path = Path.of(System.getProperty("contactquests.test.snapshot")); Files.writeString(path, snapshot.toString());
        data.markDirty(); data.saveIfChanged(); ServerQuestFile.INSTANCE.saveNow();
        player.server.saveEverything(false, true, true); sync(player);
    }

    private static void assertRewardContents(ServerPlayer player, UUID team, boolean restartedJournal) {
        int apples = 0, emeralds = 0, diamonds = 0, cards = 0, journalCards = 0, unexpected = 0;
        var mailbox = PlatformHelper.getMailboxContents(team);
        for (int i = 0; i < mailbox.getContainerSize(); i++) {
            var mail = mailbox.getItem(i);
            if (mail.getItem() instanceof PostcardItem) {
                if (PostcardItem.getText(mail).equals("without scripts " + player.getGameProfile().getName())) {
                    cards += mail.getCount();
                } else if (restartedJournal && ContactQuestsNewFeatureFixtures.isRestartJournalReply(player, team, mail)) {
                    journalCards += mail.getCount();
                } else {
                    throw new IllegalStateException("Unexpected native postcard: " + mail.save(new CompoundTag()));
                }
            }
            for (var stack : ContactMailNbt.INSTANCE.readContents(mail)) {
                if (stack.is(Items.APPLE)) apples += stack.getCount();
                else if (stack.is(Items.EMERALD)) emeralds += stack.getCount();
                else if (stack.is(Items.DIAMOND)) diamonds += stack.getCount();
                else if (!stack.isEmpty()) unexpected += stack.getCount();
            }
        }
        require(apples == 2 && emeralds == 3 && diamonds == 8 && cards == 1 && unexpected == 0,
                "Exact reward quantities mismatch: " + List.of(apples, emeralds, diamonds, cards, unexpected));
        require(journalCards == (restartedJournal ? 1 : 0), "Restart journal reply quantity mismatch: " + journalCards);
    }

    private static void rejoin(ServerPlayer player) throws Exception {
        var snapshot = JsonParser.parseString(Files.readString(Path.of(System.getProperty("contactquests.test.snapshot")))).getAsJsonObject();
        require(dependenciesAbsent() && snapshot.get("playerId").getAsString().equals(player.getUUID().toString()), "Restart has wrong dependencies or player");
        questId = Long.parseUnsignedLong(snapshot.get("questId").getAsString(), 16);
        var teamId = UUID.fromString(snapshot.get("teamId").getAsString());
        var actualTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
        require(actualTeam.getId().equals(teamId) && actualTeam.isPartyTeam(), "Persisted party changed");
        var data = ServerQuestFile.INSTANCE.getOrCreateTeamData(actualTeam); var quest = currentQuest();
        require(quest != null && data.isCompleted(quest) && List.of(2L, 2L, 1L).equals(quest.getTasks().stream().map(data::getProgress).toList()),
                "Persisted native task progress is not [2,2,1]");
        require(quest.getRewards().size() == 5 && quest.getRewards().stream().allMatch(r -> data.isRewardClaimed(player.getUUID(), r)), "Claim state lost on restart");
        mailboxUpper = new BlockPos(snapshot.get("mailboxX").getAsInt(), snapshot.get("mailboxY").getAsInt(), snapshot.get("mailboxZ").getAsInt());
        var pos = PlatformHelper.getMailboxPos(teamId);
        require(pos != null && pos.pos().equals(mailboxUpper) && PlatformHelper.getMailboxPos(player.getUUID()) == null
                && player.serverLevel().getBlockEntity(mailboxUpper) instanceof IMailboxTeamAccessor access
                && teamId.equals(access.contactQuests$getTeamId()), "Exclusive party mailbox binding lost on restart");
        assertRewardContents(player, teamId, true);
        require(CollectionSavedData.Companion.get(player.serverLevel()).getTriggerCount(player.getUUID(), NPC) == 1, "Wrong-mail count did not persist");
        ContactQuestsTestServerController.setEditorQuestId(Long.parseUnsignedLong(snapshot.get("editorQuestId").getAsString(), 16));
        ContactQuestsTestServerController.setEditorMutexId(Long.parseUnsignedLong(snapshot.get("editorMutexId").getAsString(), 16));
        require(ContactQuestsEditorAssertions.questRules(ServerQuestFile.INSTANCE.getQuest(ContactQuestsTestServerController.getEditorQuestId()),
                ContactQuestsTestServerController.getEditorMutexId(), player.level().registryAccess()), "Native Tag and mutex editor rules lost on restart");
        ContactQuestsTestServerController.recordCase("server.no_kubejs.rejoin", true,
                "New JVM read exact [2,2,1] progress, five claim states, quantities, exclusive party mailbox, wrong-mail count and edited rules without reseeding");
        sync(player);
    }

    private static void sync(ServerPlayer player) {
        var file = ServerQuestFile.INSTANCE;
        new dev.ftb.mods.ftbquests.net.SyncQuestsMessage(file).sendTo(player);
        new dev.ftb.mods.ftbquests.net.SyncTeamDataMessage(file.getOrCreateTeamData(player), true).sendTo(player);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
