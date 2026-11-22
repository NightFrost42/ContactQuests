package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.data.*;
import com.creeperyang.contactquests.llm.*;
import com.creeperyang.contactquests.quest.*;
import com.creeperyang.contactquests.quest.reward.*;
import com.creeperyang.contactquests.quest.task.PostcardTask;
import com.creeperyang.contactquests.bridge.ITeamDataExtension;
import com.creeperyang.contactquests.utils.ContactMailNbt;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.platform.PlatformHelper;
import com.google.gson.*;
import dev.ftb.mods.ftbquests.quest.*;
import dev.ftb.mods.ftbquests.quest.loot.*;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** New assertions use the same native Windows UI, real network and persistent worlds as the original suite. */
public final class ContactQuestsNewFeatureFixtures {
    public static final String NPC = "CQFeatureLLM";
    private static final String ESCROW_NPC = NPC + "_EscrowPersistence";
    private static final String ESCROW_MARKER = "cq_test_escrow_restart";
    private static long questId, choiceId, pendingQuestId, pendingChoiceId, visibleChoiceId;
    private static UUID teamId;
    private static String selectedText = "", selectedRecipient = "";
    private static ChoiceRewardSavedData.Request selectedRequest;
    private static ChoiceRewardSavedData.Request visibleRequest;
    private static final List<QuestStateEvents.Change> stateEvents = new ArrayList<>();
    private static final Consumer<QuestStateEvents.Change> listener = c -> { if (c.getTeamId().equals(teamId)) stateEvents.add(c); };
    private static int legacyMailCallbacks;
    private static long llmReplyBaseline;
    private static List<ItemStack> llmBaselineCards = List.of();
    private static String savedFailureConversation = "";
    private static long savedFailureRetry = Long.MIN_VALUE;
    private static net.minecraft.world.SimpleContainer previousMailbox;
    private static ItemStack receivedLetter = ItemStack.EMPTY;
    private static final List<JsonObject> scriptEvents = new ArrayList<>();
    private static Quest eventBlocker, eventGated;
    private static dev.ftb.mods.ftbquests.quest.task.Task eventTask;
    private static int eventStart;

    private static Path path() { return Path.of(System.getProperty("contactquests.test.snapshot") + ".features.json"); }
    private static Path escrowClaimedPath() { return Path.of(System.getProperty("contactquests.test.snapshot") + ".escrow-claimed.json"); }
    public static void recordLegacyMail(String recipient) {
        if (NPC.equals(recipient)) legacyMailCallbacks++;
        ContactQuestsLiveLlmFixtures.recordLegacy(recipient);
    }
    public static void recordScriptEvent(String json) { scriptEvents.add(JsonParser.parseString(json).getAsJsonObject()); }
    public static boolean isRestartJournalReply(ServerPlayer player, UUID team, ItemStack card) {
        var conversation = NpcLlmSavedData.Companion.get(player.serverLevel()).all().stream()
                .filter(c -> NPC.equals(c.getNpc()) && team.equals(c.getTeamId()) && c.getOwner() == null)
                .findFirst().orElse(null);
        var published = conversation == null ? null : conversation.getPublished().get("restart");
        return published != null && published.matches("[0-9A-Fa-f]{16}") && card.getItem() instanceof PostcardItem
                && NPC.equals(ContactMailNbt.INSTANCE.getSender(card))
                && new ResourceLocation("contact:default").equals(ContactMailNbt.INSTANCE.getPostcardStyleId(card))
                && PostcardItem.getText(card).equals("Resumed durable tools\ncompleted-journal-result\n" + published);
    }
    public static void verifyPhysicalEvents(ServerPlayer player, String phase) {
        if (Boolean.getBoolean("contactquests.test.noKubejs")) return;
        long expected = phase.equals("first") ? 3 : 6;
        var events = scriptEvents.stream().filter(e -> e.get("cause").getAsString().equals("physical_item") &&
                e.get("tag").getAsString().startsWith("CQUiUnlock")).toList();
        boolean good = events.size() == expected && events.stream().allMatch(e ->
                e.get("teamId").getAsString().equals(FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow().getId().toString())
                        && e.get("playerId").getAsString().equals(player.getUUID().toString())
                        && e.get("questId").getAsString().matches("[0-9A-Fa-f]{16}"));
        ContactQuestsTestServerController.recordCase("kubejs.events.physical." + phase, good,
                "Real Contact postcard right-click produces exactly " + expected + " matching quest Tag events, with actor/team strings and no replay");
    }
    public static void addResultFields(JsonObject result) {
        if (questId != 0) result.addProperty("featureQuest", Long.toUnsignedString(questId, 16));
        if (choiceId != 0) result.addProperty("featureChoice", Long.toUnsignedString(choiceId, 16));
        if (visibleChoiceId != 0) result.addProperty("featureVisibleChoice", Long.toUnsignedString(visibleChoiceId, 16));
        result.addProperty("featureRecipient", selectedRecipient); result.addProperty("featureText", selectedText);
        result.addProperty("creationQuest", ContactQuestsCreationAssertions.questId());
    }

    public static void run(ServerPlayer player, String phase) {
        try {
            switch (phase) {
                case "prepare" -> prepare(player);
                case "edit_mode" -> {
                    var cleared = new CompoundTag(); cleared.put("icon", new CompoundTag());
                    ServerQuestFile.INSTANCE.getQuest(questId).getRewards().stream()
                            .filter(r -> r instanceof ParcelRandomReward && !(r instanceof ParcelChoiceReward))
                            .forEach(r -> QuestGraphService.INSTANCE.updateReward(player.serverLevel(), r.getCodeString(), cleared));
                    ServerQuestFile.INSTANCE.getOrCreateTeamData(FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow()).setCanEdit(player, true);
                }
                case "player_mode" -> ServerQuestFile.INSTANCE.getOrCreateTeamData(FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow()).setCanEdit(player, false);
                case "selected" -> selected(player);
                case "verify_choice" -> verifyChoice(player);
                case "verify_repeat" -> verifyRepeat(player);
                case "prepare_visible_choice" -> prepareVisibleChoice(player);
                case "visible_selected" -> visibleRequest = ChoiceRewardSavedData.Companion.get(player.serverLevel())
                        .request(player, (ParcelChoiceReward) ServerQuestFile.INSTANCE.getReward(visibleChoiceId), 0);
                case "verify_visible_choice" -> verifyVisibleChoice(player);
                case "llm_first" -> {
                    llmBaselineCards = mailboxPostcards().stream().filter(s -> NPC.equals(ContactMailNbt.INSTANCE.getSender(s)))
                            .map(ItemStack::copy).toList();
                    llmReplyBaseline = llmBaselineCards.size();
                }
                case "llm_second", "llm_fail" -> { }
                case "claim_hidden" -> claimHidden(player);
                case "verify_llm" -> verifyLlm(player);
                case "verify_keep" -> verifyKeep(player);
                case "verify_discard" -> verifyDiscard(player);
                case "verify_with_rewards" -> verifyWithRewards(player);
                case "verify_fail" -> verifyFail(player);
                case "verify_events" -> {
                    if (Boolean.getBoolean("contactquests.test.noKubejs")) {
                        prepareStateEvents(player);
                        var ext = (ITeamDataExtension) ServerQuestFile.INSTANCE.getNullableTeamData(teamId);
                        QuestStateEvents.withActor(player, "script_test", () -> ext.contactQuests$unlockTag("CQFeatureEvent"));
                        ext.contactQuests$unlockTag("CQFeatureEvent");
                        ext.contactQuests$blockQuest(eventGated.id); ext.contactQuests$blockQuest(eventGated.id); ext.contactQuests$unblockQuest(eventGated.id);
                        mutateStateAfterRhinoGrant(player);
                        verifyStateEvents(player);
                    } else require(ContactQuestsTestServerController.isCasePassed("kubejs.events.native_and_script"), "Rhino event assertions have not passed");
                }
                case "snapshot" -> snapshot(player);
                case "rejoin" -> rejoin(player);
                case "escrow_audit" -> auditEscrow(player);
                case "verify_failure_rejoin" -> verifyFailureRejoin(player);
                default -> throw new IllegalArgumentException("Unknown feature phase " + phase);
            }
        } catch (Exception error) { ContactQuestsTestServerController.recordCase("server.features." + phase, false, error.toString()); }
    }

    public static String prepareStateEvents(ServerPlayer player) {
        var graph = QuestGraphService.INSTANCE; var file = ServerQuestFile.INSTANCE; var level = player.serverLevel();
        var chapter = file.getQuest(questId).getChapter();
        eventBlocker = graph.createQuest(level, chapter.getCodeString(), "cqtest:features/events_blocker", "Event blocker");
        eventTask = graph.addTask(level, eventBlocker.getCodeString(), "cqtest:features/events_blocker/task", "ftbquests:checkmark", new CompoundTag());
        eventGated = graph.createQuest(level, chapter.getCodeString(), "cqtest:features/events_gated", "Event gated quest");
        graph.addTask(level, eventGated.getCodeString(), "cqtest:features/events_gated/task", "ftbquests:checkmark", new CompoundTag());
        graph.updateQuest(level, eventGated.getCodeString(), Map.of("requiredTags", List.of("CQFeatureEvent"), "mutex", List.of(eventBlocker.getCodeString())));
        eventStart = scriptEvents.size();
        return eventGated.getCodeString();
    }
    public static void mutateStateAfterRhinoGrant(ServerPlayer player) {
        var file = ServerQuestFile.INSTANCE; var team = file.getNullableTeamData(teamId);
        QuestStateEvents.withActor(player, "script_test", () -> team.setProgress(eventTask, 1));
        eventBlocker.forceProgressRaw(team, new dev.ftb.mods.ftbquests.util.ProgressChange(file, eventBlocker, player.getUUID()).setReset(true));
    }
    public static void verifyStateEvents(ServerPlayer player) {
        var file = ServerQuestFile.INSTANCE; var team = file.getNullableTeamData(teamId);
        for (int i=0;i<3;i++) { team.canStartTasks(eventGated); eventGated.isVisible(team); team.clearCachedProgress(); }
        var copy = new TeamData(teamId, file); copy.deserializeNBT(team.serializeNBT());
        var nativeTransitions = stateEvents.stream().filter(e -> e.getQuestId().equals(eventGated.getCodeString())).toList();
        require(nativeTransitions.size() == 5 && nativeTransitions.stream().filter(e -> e.getKind().equals("tagUnlocked")).count() == 1
                && nativeTransitions.stream().filter(e -> e.getKind().equals("mutexChanged")).count() == 2
                && nativeTransitions.stream().filter(e -> e.getKind().equals("scriptBlockChanged")).count() == 2,
                "Native state listeners replayed reads or confused mutex/block transitions");
        if (!Boolean.getBoolean("contactquests.test.noKubejs")) {
            var transitions = scriptEvents.subList(eventStart, scriptEvents.size()).stream().filter(e -> e.get("questId").getAsString().equals(eventGated.getCodeString())).toList();
            var tag = transitions.stream().filter(e -> e.get("kind").getAsString().equals("tagUnlocked")).toList();
            var mutex = transitions.stream().filter(e -> e.get("kind").getAsString().equals("mutexChanged")).toList();
            var blocks = transitions.stream().filter(e -> e.get("kind").getAsString().equals("scriptBlockChanged")).toList();
            require(tag.size() == 1 && mutex.size() == 2 && mutex.get(0).get("mutexAfter").getAsBoolean()
                    && !mutex.get(1).get("mutexAfter").getAsBoolean() && blocks.size() == 2
                    && blocks.get(0).get("blockAfter").getAsBoolean() && !blocks.get(1).get("blockAfter").getAsBoolean()
                    && transitions.size() == 5, "Events confuse true mutex and script block: " + transitions);
            require(transitions.stream().allMatch(e -> e.get("teamId").getAsString().equals(teamId.toString())), "Rhino event crossed teams");
            ContactQuestsTestServerController.recordCase("kubejs.events.native_and_script", true, "Real Rhino subscribers observe grant, native FTB completion/reset and separate script block exactly once; reads/deserialize do not replay");
        }
        ContactQuestsTestServerController.recordCase("server.features.events", true, "Native state mutation and optional script event path complete without getter/load replays");
    }

    private static void prepare(ServerPlayer player) {
        var level = player.serverLevel(); var file = ServerQuestFile.INSTANCE;
        var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow(); teamId = team.getId();
        var chapter = QuestGraphService.INSTANCE.createChapter(level, "cqtest:features/ui_chapter", "New features");
        var quest = QuestGraphService.INSTANCE.createQuest(level, chapter.getCodeString(), "cqtest:features/ui_quest", "New mail rewards");
        questId = quest.id;
        quest.addTask(new CheckmarkTask(file.newID(), quest));
        var arbitrary = new PostcardTask(file.newID(), quest); arbitrary.setTargetAddressee(NPC); arbitrary.setCount(100);
        arbitrary.setPostcardText("Unrelated ordinary postcard task");
        quest.addTask(arbitrary);
        var table = new RewardTable(file.newID(), file); table.setRawTitle("Secret weighted table");
        table.addReward(new WeightedReward(new ItemReward(file.newID(), table.getFakeQuest(), new ItemStack(Items.APPLE), 3), 1));
        table.addReward(new WeightedReward(new ItemReward(file.newID(), table.getFakeQuest(), new ItemStack(Items.DIAMOND), 7), 3));
        file.addRewardTable(table);
        var parcel = new ParcelReward(file.newID(), quest); parcel.setItem(new ItemStack(Items.APPLE)); parcel.setCount(3);
        var red = new RedPacketReward(file.newID(), quest); red.setItem(new ItemStack(Items.EMERALD)); red.setCount(2); red.setBlessing("Secret blessing 91%");
        var card = new PostcardReward(file.newID(), quest); card.setPostcardText("Secret postcard contents");
        var random = new ParcelRandomReward(file.newID(), quest); random.setTable(table);
        var all = new ParcelAllTableReward(file.newID(), quest); all.setTable(table);
        var choice = new ParcelChoiceReward(file.newID(), quest); choice.setTable(table); choice.setOptionLabels(new ArrayList<>(List.of("A", "B")));
        choiceId = choice.id;
        for (var reward : List.of(parcel, red, card, random, all, choice)) {
            reward.setTargetAddressee(NPC); reward.setHideContents(true); reward.setRawTitle("Secret reward 777 x diamond");
            reward.setRawIcon(new ItemStack(Items.DIAMOND)); quest.addReward(reward);
        }
        var data = file.getOrCreateTeamData(team); data.setLocked(false);
        ContactQuestsTestUtil.refreshIndex(file);
        data.setCompleted(quest.id, new Date()); data.setCanEdit(player, false);
        configureMock(chapter.getCodeString());
        QuestGraphService.INSTANCE.updateQuest(level, quest.getCodeString(), Map.of("description", List.of("Six sealed mail rewards")));
        QuestStateEvents.removeListener(listener); stateEvents.clear(); QuestStateEvents.addListener(listener); legacyMailCallbacks = 0;
        require(PlatformHelper.getMailboxPos(teamId) != null, "Existing test team mailbox is not bound");
        var contents = PlatformHelper.getMailboxContents(teamId);
        previousMailbox = new net.minecraft.world.SimpleContainer(contents.getContainerSize());
        for (int i = 0; i < contents.getContainerSize(); i++) previousMailbox.setItem(i, contents.getItem(i).copy());
        PlatformHelper.resetMailboxContents(teamId);
        player.getInventory().clearContent();
        receivedLetter = PostcardItem.setText(PostcardItem.getPostcard(new ResourceLocation("contact:creeper"), false), "A letter worth keeping");
        ContactMailNbt.INSTANCE.setSender(receivedLetter, "An earlier correspondent");
        player.getInventory().setItem(0, receivedLetter.copy());
        player.getInventory().setItem(9, PostcardItem.getPostcard(new ResourceLocation("contact:creeper"), false));
        player.getInventory().selected = 0; player.inventoryMenu.broadcastFullState();
        ContactQuestsTestServerController.recordCase("server.features.prepare", true,
                "Six hidden real mail rewards, native weighted table, real Contact postcard, existing postcard task and LLM NPC ready");
    }

    private static void configureMock(String chapterId) {
        var provider = new NpcLlmConfig.Provider("mock", "", "", "fixed-mock", "low", 30, 1024, List.of("publish_quest", "get_quest"));
        var npc = new NpcLlmConfig.Npc(true, "mock", "Mock courier persona", "team", 12, "mock-override", "high", List.of("publish_quest", "get_quest"), chapterId, "fixture", 8, 0);
        NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, Map.of("mock", provider), Map.of(NPC, npc)));
    }

    private static void selected(ServerPlayer player) {
        var reward = (ParcelChoiceReward) ServerQuestFile.INSTANCE.getReward(choiceId);
        var request = ChoiceRewardSavedData.Companion.get(player.serverLevel()).request(player, reward, 1);
        selectedRequest = request;
        selectedText = request.getText(); selectedRecipient = request.getRecipient();
        require(selectedText.contains("B") && !selectedText.toLowerCase().contains("diamond"), "Hidden choice leaked contents into the postcard");
        require(request.getOption() == 1 && selectedRecipient.equals(reward.getClaimRecipient()), "Wrong selected option or recipient");
        ContactQuestsTestServerController.recordCase("server.features.selected", true, "Native button selected B; natural letter names the actual NPC and hidden data identifies the server-owned selection");
    }

    private static void verifyChoice(ServerPlayer player) {
        var reward = (ParcelChoiceReward) ServerQuestFile.INSTANCE.getReward(choiceId);
        var data = ServerQuestFile.INSTANCE.getNullableTeamData(teamId);
        require(data.isRewardClaimed(player.getUUID(), reward), "Actual postcard did not mark FTB claim");
        require(selectedRequest != null && selectedRequest.getAccepted()
                        && selectedRequest.getToken().equals(ChoiceRewardSavedData.cardToken(selectedRequest.getSubmittedCard()))
                        && PostcardItem.getText(selectedRequest.getSubmittedCard()).equals("你好，前些日子的事办妥啦。多谢关照，回头见！"),
                "Real Contact Done/send lost the hidden marker or did not preserve the completely rewritten letter");
        ContactQuestsTestServerController.recordCase("server.features.choice_card", true,
                "Real Contact Done and postbox packets preserved the hidden selection and fully rewritten prose; server never required a reward name or fixed text");
        require(player.getInventory().items.stream().filter(s -> ItemStack.matches(s, receivedLetter)).count() == 1,
                "Preparing and posting the blank card changed or consumed the received letter");
        ContactQuestsTestServerController.recordCase("server.features.choice_stationery", true,
                "Real inventory selection skipped the read-only letter in hotbar 0 and used blank stationery from slot 9; the received item is byte-for-byte unchanged");
        require(mailCount(Items.DIAMOND) == 7 && mailCount(Items.APPLE) == 0, "Choice delivered wrong items/count");
        var quest = ServerQuestFile.INSTANCE.getQuest(questId);
        require(data.getProgress(quest.getTasks().stream().filter(t -> t instanceof PostcardTask).findFirst().orElseThrow()) == 0,
                "Normal postcard task consumed a claim card");
        ContactQuestsTestServerController.recordCase("server.features.verify_choice", true, "Real Contact card sent through postbox: precisely seven diamonds, no apples, native claimed=true; arbitrary postcard task unchanged");
    }
    private static void verifyRepeat(ServerPlayer player) {
        var card = PostcardItem.setText(PostcardItem.getPostcard(new ResourceLocation("contact:creeper"), false), selectedText);
        ChoiceRewardSavedData.bindCard(card, selectedRequest);
        var kept = DataManager.INSTANCE.receiveChoiceCard(player, card, selectedRecipient);
        require(kept.getCount() == 1 && mailCount(Items.DIAMOND) == 7, "Repeat request duplicated delivery or consumed another card");
        ContactQuestsTestServerController.recordCase("server.features.verify_repeat", true, "Duplicate card remains intact and native claimed reward cannot deliver twice");
    }
    private static int visibleApplesBefore, visibleDiamondsBefore;
    private static void prepareVisibleChoice(ServerPlayer player) {
        var file = ServerQuestFile.INSTANCE;
        var main = file.getQuest(questId);
        var quest = QuestGraphService.INSTANCE.createQuest(player.serverLevel(), main.getChapter().getCodeString(),
                "cqtest:features/visible_choice", "A gift of your choosing");
        quest.addTask(new CheckmarkTask(file.newID(), quest));
        var choice = new ParcelChoiceReward(file.newID(), quest);
        choice.setTable(((ParcelChoiceReward) file.getReward(choiceId)).getTable());
        choice.setTargetAddressee(NPC); choice.setHideContents(false); choice.setOptionLabels(new ArrayList<>());
        quest.addReward(choice); visibleChoiceId = choice.id;
        ContactQuestsTestUtil.refreshIndex(file);
        var data = file.getNullableTeamData(teamId);
        data.setCompleted(quest.id, new Date()); data.setCanEdit(player, false);
        visibleApplesBefore = mailCount(Items.APPLE); visibleDiamondsBefore = mailCount(Items.DIAMOND);
        player.getInventory().setItem(0, PostcardItem.getPostcard(new ResourceLocation("contact:creeper"), false));
        player.getInventory().selected = 0; player.inventoryMenu.broadcastFullState();
        QuestGraphService.INSTANCE.updateQuest(player.serverLevel(), quest.getCodeString(), Map.of("description", List.of("Choose one actual item")));
        ContactQuestsTestServerController.recordCase("server.features.visible_choice_prepared", true,
                "Separate completed non-hidden choice has empty labels, native apple x3/diamond x7 entries and real editable Contact stationery");
    }
    private static void verifyVisibleChoice(ServerPlayer player) {
        var reward = (ParcelChoiceReward) ServerQuestFile.INSTANCE.getReward(visibleChoiceId);
        var request = visibleRequest;
        require(request != null && request.getAccepted() && request.getDelivered() && request.getGranted()
                        && ServerQuestFile.INSTANCE.getNullableTeamData(teamId).isRewardClaimed(player.getUUID(), reward)
                        && mailCount(Items.APPLE) == visibleApplesBefore + 3 && mailCount(Items.DIAMOND) == visibleDiamondsBefore,
                "Non-hidden real postcard choice did not deliver exactly the selected apple x3");
        var repeated = DataManager.INSTANCE.receiveChoiceCard(player, request.getSubmittedCard().copy(), request.getRecipient());
        require(!repeated.isEmpty() && mailCount(Items.APPLE) == visibleApplesBefore + 3 && mailCount(Items.DIAMOND) == visibleDiamondsBefore,
                "Visible choice duplicate was consumed or delivered again");
        ContactQuestsTestServerController.recordCase("server.features.visible_choice_delivery", true,
                "Actual non-hidden RewardButton, native postcard Done and postbox send claim precisely apple x3, no diamond; replay never duplicates");
    }
    private static int mailCount(Item item) {
        int n = 0; var box = PlatformHelper.getMailboxContents(teamId);
        for (int i = 0; i < box.getContainerSize(); i++) {
            var stack = box.getItem(i); if (stack.is(item)) n += stack.getCount();
            for (var inside : ContactMailNbt.INSTANCE.readContents(stack)) if (inside.is(item)) n += inside.getCount();
        }
        return n;
    }
    private static void claimHidden(ServerPlayer player) {
        var quest = ServerQuestFile.INSTANCE.getQuest(questId); var data = ServerQuestFile.INSTANCE.getNullableTeamData(teamId);
        var table = ((ParcelChoiceReward) ServerQuestFile.INSTANCE.getReward(choiceId)).getTable();
        long seed = 431117L;
        var selected = (ItemReward) table.generateWeightedRandomRewards(net.minecraft.util.RandomSource.create(seed), 1, false).iterator().next().getReward();
        int applesBefore = mailCount(Items.APPLE), diamondsBefore = mailCount(Items.DIAMOND), emeraldsBefore = mailCount(Items.EMERALD);
        int cardsBefore = mailboxPostcards().size();
        for (var reward : quest.getRewards()) if (!(reward instanceof ParcelChoiceReward)) {
            if (reward instanceof ParcelRandomReward && !(reward instanceof ParcelAllTableReward)) player.getRandom().setSeed(seed);
            data.claimReward(player, reward, false);
            data.claimReward(player, reward, false);
        }
        for (int i = 0; i < 10; i++) RewardDistributionManager.INSTANCE.onServerTick(player.serverLevel());
        int apples = 6 + (selected.getItem().is(Items.APPLE) ? selected.getCount() : 0);
        int diamonds = 7 + (selected.getItem().is(Items.DIAMOND) ? selected.getCount() : 0);
        require(mailCount(Items.APPLE) - applesBefore == apples && mailCount(Items.DIAMOND) - diamondsBefore == diamonds
                && mailCount(Items.EMERALD) - emeraldsBefore == 2 && mailboxPostcards().size() == cardsBefore + 1
                && quest.getRewards().stream().allMatch(r -> data.isRewardClaimed(player.getUUID(), r)),
                "Hiding changed actual five reward quantities, native weighted selection, All contents or repeated claims");
        ContactQuestsTestServerController.recordCase("server.features.hidden_delivery", true,
                "All hidden reward types deliver exact native quantities; Random selected by FTB helper, All sends both entries, two claims never duplicate");
    }
    private static void llmMail(ServerPlayer player, String text) {
        var card = PostcardItem.setText(PostcardItem.getPostcard(new ResourceLocation("contact:default"), false), text);
        player.getInventory().setItem(0, card); player.getInventory().selected = 0;
        player.getInventory().setChanged();
        player.containerMenu.broadcastFullState();
        if (player.containerMenu != player.inventoryMenu) player.inventoryMenu.broadcastFullState();
    }
    public static void seedLlmMail(ServerPlayer player, String text) { llmMail(player, text); }
    private static void verifyKeep(ServerPlayer player) {
        var c = NpcLlmSavedData.Companion.get(player.serverLevel()).conversation(NPC, teamId, player.getUUID(), "team");
        require(c.getPendingId() == null && c.getInbox().isEmpty() && c.getTurns().size() == 2
                && c.getKeptItems().size() == 1 && PostcardItem.getText(c.getKeptItems().get(0)).equals("CQ_MOCK_PUBLISH")
                && c.getRewardItems().isEmpty() && c.getPublished().size() == 1,
                "Keep decision did not retain the exact input postcard outside reward escrow");
        var cards = mailboxPostcards();
        require(cards.stream().noneMatch(s -> PostcardItem.getText(s).equals("CQ_MOCK_PUBLISH"))
                && legacyMailCallbacks == 0, "Keep returned the original card, lost its actual reply or ran an old NPC reaction");
        verifyReplySeries(c, 1);
        ContactQuestsTestServerController.recordCase("server.features.verify_keep", true,
                "Real Contact postbox input stays in NPC keep storage; exactly one complete Contact reply across bounded cards, no premature return or legacy reaction");
    }
    private static void verifyDiscard(ServerPlayer player) {
        var c = NpcLlmSavedData.Companion.get(player.serverLevel()).conversation(NPC, teamId, player.getUUID(), "team");
        var cards = mailboxPostcards();
        require(c.getPendingId() == null && c.getInbox().isEmpty() && c.getTurns().size() == 8
                && c.getKeptItems().isEmpty() && c.getRewardItems().isEmpty()
                && cards.stream().noneMatch(s -> PostcardItem.getText(s).equals("CQ_MOCK_DISCARD"))
                && cards.stream().filter(s -> NPC.equals(ContactMailNbt.INSTANCE.getSender(s)))
                        .anyMatch(s -> PostcardItem.getText(s).contains("CQ_MOCK_DISCARD"))
                && legacyMailCallbacks == 0, "Discard retained/returned the input, lost its real Contact reply or ran a legacy reaction");
        verifyReplySeries(c, 4);
        ContactQuestsTestServerController.recordCase("server.features.verify_discard", true,
                "Real Contact postbox discard consumes only the current input; one additional complete NPC letter, no kept or reward escrow items");
    }
    private static void verifyLlm(ServerPlayer player) {
        var store = NpcLlmSavedData.Companion.get(player.serverLevel());
        var c = store.conversation(NPC, teamId, player.getUUID(), "team");
        require(c.getPendingId() == null && c.getInbox().isEmpty() && c.getTurns().size() == 4, "Mock did not finish two persistent rounds");
        require(c.getTurns().get(1).getText().contains("model=mock-override, reasoning=high")
                && c.getTurns().get(3).getText().contains("history=2"), "LLM lost NPC model/reasoning/persona or context");
        require(c.getPublished().size() == 1 && c.getKeptItems().isEmpty() && legacyMailCallbacks == 0, "Old script reaction fired, task publication failed or RETURN kept attachments");
        var postcards = mailboxPostcards();
        require(postcards.stream().filter(s -> "CQ_MOCK_PUBLISH".equals(PostcardItem.getText(s))).count() == 1
                && postcards.stream().filter(s -> "CQ_MOCK_RETURN".equals(PostcardItem.getText(s))).count() == 1,
                "LLM did not mail exactly two replies and return the two real input postcards: " + postcards.stream()
                        .map(s -> s.save(new CompoundTag()).toString()).toList());
        verifyReplySeries(c, 2);
        require(postcards.stream().filter(s -> Set.of("CQ_MOCK_PUBLISH", "CQ_MOCK_RETURN").contains(PostcardItem.getText(s)))
                        .allMatch(s -> ContactMailNbt.INSTANCE.getSender(s) == null),
                "Returned LLM postcards changed their original player sender");
        require(CollectionSavedData.Companion.get(player.serverLevel()).getTriggerCount(player.getUUID(), NPC) == 0,
                "Legacy error policy reacted to LLM mail");
        var other = store.conversation(NPC, UUID.randomUUID(), player.getUUID(), "team");
        require(other.getTurns().isEmpty(), "LLM team context crossed into another team");
        ContactQuestsTestServerController.recordCase("server.features.verify_llm", true, "Two mock rounds retain context/model/persona; one authorized idempotent quest; old script and NOW/SAVE policies never run");
    }
    private static void verifyFail(ServerPlayer player) {
        var c = NpcLlmSavedData.Companion.get(player.serverLevel()).conversation(NPC, teamId, player.getUUID(), "team");
        var pending = c.getInbox().stream().filter(mail -> mail.getId().equals(c.getPendingId())).findFirst().orElse(null);
        require(c.getPendingId() != null && pending != null && pending.getPlayerId().equals(player.getUUID())
                && c.getPendingText().equals("CQ_MOCK_FAIL") && !c.getResolved() && c.getReplyText().isBlank()
                && c.getRetryAfterTick() >= player.serverLevel().getGameTime(),
                "Failure discarded or completed the pending provider request instead of scheduling a retry");
        require(pending.getItems().size() == 1 && "CQ_MOCK_FAIL".equals(PostcardItem.getText(pending.getItems().get(0)))
                && legacyMailCallbacks == 0 && com.creeperyang.contactquests.config.NpcConfigManager.INSTANCE.getErrorSolve(NPC, 999) == null,
                "Failure invoked a legacy reaction or lost the real input postcard");
        verifyReplySeries(c, 4);
        ContactQuestsTestServerController.recordCase("server.features.verify_fail", true, "Fixed provider failure retained the real postcard and emitted no old response/fake assistant turn");
    }
    private static void verifyWithRewards(ServerPlayer player) {
        var store = NpcLlmSavedData.Companion.get(player.serverLevel());
        var c = store.conversation(NPC, teamId, player.getUUID(), "team");
        require(c.getPendingId() == null && c.getInbox().isEmpty() && c.getTurns().size() == 6
                && c.getKeptItems().isEmpty() && c.getRewardItems().size() == 1
                && c.getRewardItems().get(0).getPlayerId().equals(player.getUUID())
                && "CQ_MOCK_WITH_REWARDS".equals(PostcardItem.getText(c.getRewardItems().get(0).getItems().get(0))),
                "Mock withReward decision was not stored as isolated NPC reward escrow");
        var allNpcPostcards = mailboxPostcards().stream()
                .filter(s -> NPC.equals(ContactMailNbt.INSTANCE.getSender(s)))
                .filter(s -> s.getItem() instanceof PostcardItem).toList();
        var npcReplies = allNpcPostcards.stream()
                .filter(s -> new ResourceLocation("contact:default")
                        .equals(ContactMailNbt.INSTANCE.getPostcardStyleId(s)))
                .toList();
        verifyReplySeries(c, 3);
        require(npcReplies.stream().anyMatch(s -> PostcardItem.getText(s).contains("CQ_MOCK_WITH_REWARDS"))
                        && npcReplies.stream().allMatch(s -> new ResourceLocation("contact:default")
                                .equals(ContactMailNbt.INSTANCE.getPostcardStyleId(s))),
                "AI withReward response was not delivered as a real Contact postcard: baseline=" + llmReplyBaseline
                        + ", actual=" + npcReplies.stream().map(s -> List.of(s.getCount(),
                                ContactMailNbt.INSTANCE.getSender(s),
                                ContactMailNbt.INSTANCE.getPostcardStyleId(s), PostcardItem.getText(s))).toList());
        var otherTeam = UUID.randomUUID();
        var other = store.conversation(NPC, otherTeam, player.getUUID(), "team");
        other.getRewardItems().add(new NpcLlmSavedData.RewardEscrow(player.getUUID(), new ArrayList<>(List.of(new ItemStack(Items.GOLD_INGOT, 2)))));
        var rewardQuest = QuestGraphService.INSTANCE.createQuest(player.serverLevel(),
                ServerQuestFile.INSTANCE.getQuest(questId).getChapter().getCodeString(), "cqtest:features/with_rewards", "Escrow claim");
        rewardQuest.addTask(new CheckmarkTask(ServerQuestFile.INSTANCE.newID(), rewardQuest));
        var reward = new ParcelReward(ServerQuestFile.INSTANCE.newID(), rewardQuest);
        reward.setTargetAddressee(NPC); reward.setItem(new ItemStack(Items.EMERALD)); reward.setCount(5); rewardQuest.addReward(reward);
        ContactQuestsTestUtil.refreshIndex(ServerQuestFile.INSTANCE);
        var teamData = ServerQuestFile.INSTANCE.getNullableTeamData(teamId); teamData.setCompleted(rewardQuest.getId(), new Date());
        int emeraldBefore = mailCount(Items.EMERALD), escrowCardCount = c.getRewardItems().get(0).getItems().stream()
                .filter(card -> "CQ_MOCK_WITH_REWARDS".equals(PostcardItem.getText(card))).mapToInt(ItemStack::getCount).sum();
        require(escrowCardCount == 1 && c.getRewardItems().size() == 1, "AI escrow was returned before a native reward claim");
        teamData.claimReward(player, reward, false);
        teamData.claimReward(player, reward, false);
        for (int i = 0; i < 10; i++) RewardDistributionManager.INSTANCE.onServerTick(player.serverLevel());
        int returnedCards = mailboxPostcards().stream().filter(card -> "CQ_MOCK_WITH_REWARDS".equals(PostcardItem.getText(card)))
                .mapToInt(ItemStack::getCount).sum();
        require(teamData.isRewardClaimed(player.getUUID(), reward) && mailCount(Items.EMERALD) - emeraldBefore == 5
                && returnedCards == 1 && c.getRewardItems().isEmpty() && other.getRewardItems().size() == 1
                && other.getRewardItems().get(0).getItems().get(0).is(Items.GOLD_INGOT),
                "Claim did not return NPC escrow with the real reward exactly once and only to its original team");
        ContactQuestsTestServerController.recordCase("server.features.verify_with_rewards", true,
                "AI selected withReward; real Contact attachment stayed escrowed until native FTB reward claim, then returned once without crossing teams");
    }

    /** Count complete provider turns, preserving strict exact-body checks when one turn spans several cards. */
    private static void verifyReplySeries(NpcLlmSavedData.Conversation conversation, int expectedReplies) {
        var expected = conversation.getTurns().stream().filter(t -> t.getRole().equals("assistant"))
                .map(NpcLlmSavedData.Turn::getText).toList();
        require(expected.size() == expectedReplies, "Unexpected number of completed model turns");
        var groups = new LinkedHashMap<UUID, List<ItemStack>>();
        var otherNpcCards = new ArrayList<ItemStack>();
        for (var card : mailboxPostcards()) {
            if (!NPC.equals(ContactMailNbt.INSTANCE.getSender(card))) continue;
            UUID group = NpcPostcardLetters.group(card);
            if (group == null) { otherNpcCards.add(card); continue; }
            require(card.getCount() == 1 && new ResourceLocation("contact:default").equals(ContactMailNbt.INSTANCE.getPostcardStyleId(card)),
                    "NPC reply lacks a distinct real default-style fragment");
            groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(card);
        }
        require(groups.size() == expectedReplies && otherNpcCards.size() == llmBaselineCards.size()
                        && java.util.stream.IntStream.range(0, otherNpcCards.size()).allMatch(i -> ItemStack.matches(otherNpcCards.get(i), llmBaselineCards.get(i))),
                "NPC fabricated/lost a reply group or altered pre-existing reward mail");
        int turn = 0;
        for (var cards : groups.values()) {
            var text = new StringBuilder();
            for (int i = 0; i < cards.size(); i++) {
                var card = cards.get(i);
                require(NpcPostcardLetters.index(card) == i && NpcPostcardLetters.count(card) == cards.size(),
                        "Reply card order/count differs from its exact hidden sequence");
                text.append(PostcardItem.getText(card));
            }
            require(text.toString().equals(expected.get(turn++)), "Actual Contact reply does not preserve the entire provider turn");
        }
    }
    private static void snapshot(ServerPlayer player) throws Exception {
        require(!Files.exists(escrowClaimedPath()) && NpcLlmSavedData.Companion.get(player.serverLevel()).all().stream()
                        .noneMatch(c -> ESCROW_NPC.equals(c.getNpc())), "Feature snapshot must not reseed existing restart escrow evidence");
        var level = player.serverLevel(); var graph = QuestGraphService.INSTANCE;
        var main = ServerQuestFile.INSTANCE.getQuest(questId);
        var quest = graph.createQuest(level, main.getChapter().getCodeString(), "cqtest:features/pending", "Pending choice"); pendingQuestId = quest.id;
        var task = new CheckmarkTask(ServerQuestFile.INSTANCE.newID(), quest); quest.addTask(task);
        var reward = new ParcelChoiceReward(ServerQuestFile.INSTANCE.newID(), quest); reward.setTable(((ParcelChoiceReward) ServerQuestFile.INSTANCE.getReward(choiceId)).getTable());
        reward.setTargetAddressee(NPC); reward.setHideContents(true); quest.addReward(reward); pendingChoiceId = reward.id;
        ContactQuestsTestUtil.refreshIndex(ServerQuestFile.INSTANCE); var data = ServerQuestFile.INSTANCE.getNullableTeamData(teamId); data.setCompleted(quest.id, new Date());
        graph.updateQuest(level, quest.getCodeString(), Map.of("description", List.of("Pending postcard survives process restart")));
        var request = ChoiceRewardSavedData.Companion.get(level).request(player, reward, 0);
        var acceptedQuest = graph.createQuest(level, main.getChapter().getCodeString(), "cqtest:features/accepted", "Accepted choice");
        acceptedQuest.addTask(new CheckmarkTask(ServerQuestFile.INSTANCE.newID(), acceptedQuest));
        var acceptedReward = new ParcelChoiceReward(ServerQuestFile.INSTANCE.newID(), acceptedQuest);
        acceptedReward.setTable(reward.getTable()); acceptedReward.setTargetAddressee(NPC); acceptedReward.setHideContents(true);
        acceptedQuest.addReward(acceptedReward); ContactQuestsTestUtil.refreshIndex(ServerQuestFile.INSTANCE);
        data.setCompleted(acceptedQuest.id, new Date());
        graph.updateQuest(level, acceptedQuest.getCodeString(), Map.of("description", List.of("Accepted real card and delivery delay survive restart")));
        var accepted = ChoiceRewardSavedData.Companion.get(level).request(player, acceptedReward, 1);
        var acceptedCard = PostcardItem.setText(PostcardItem.getPostcard(new ResourceLocation("contact:creeper"), false), accepted.getText());
        ChoiceRewardSavedData.bindCard(acceptedCard, accepted);
        ContactMailNbt.INSTANCE.setSender(acceptedCard, player.getScoreboardName());
        require(ChoiceRewardSavedData.Companion.get(level).receive(player, acceptedReward.getClaimRecipient(), List.of(acceptedCard), 12000).isEmpty()
                && accepted.getAccepted() && !accepted.getDelivered(), "Restart fixture did not accept the actual Contact card into a delayed request");
        var llmStore = NpcLlmSavedData.Companion.get(level);
        var llm = llmStore.conversation(NPC, teamId, player.getUUID(), "team");
        var failed = llm.getInbox().stream().filter(mail -> mail.getText().equals("CQ_MOCK_FAIL")
                && mail.getPlayerId().equals(player.getUUID())).findFirst().orElse(null);
        if (failed != null) {
            String failureNpc = NPC + "_FailurePersistence";
            var failure = llmStore.conversation(failureNpc, teamId, player.getUUID(), "player");
            failure.getInbox().add(failed); failure.setPendingId(failed.getId()); failure.setPendingPlayer(player.getUUID());
            failure.setPendingText(failed.getText()); failure.setRetryAfterTick(llm.getRetryAfterTick());
            savedFailureConversation = failureNpc;
            savedFailureRetry = failure.getRetryAfterTick();
            llm.getInbox().remove(failed);
        }
        if (llm.getPendingId() != null && llm.getPendingText().equals("CQ_MOCK_FAIL")) {
            llm.setPendingId(null); llm.setPendingPlayer(null); llm.setPendingText("");
            llm.setResolved(false); llm.setRetryAfterTick(Long.MIN_VALUE);
        }
        var saved = new JsonObject(); saved.addProperty("pid", ProcessHandle.current().pid()); saved.addProperty("quest", main.getCodeString());
        saved.addProperty("choice", Long.toUnsignedString(choiceId, 16)); saved.addProperty("pendingChoice", reward.getCodeString());
        saved.addProperty("team", teamId.toString()); saved.addProperty("token", request.getToken().toString());
        saved.addProperty("text", request.getText()); saved.addProperty("turns", llm.getTurns().size()); saved.addProperty("kept", llm.getKeptItems().size());
        saved.addProperty("published", llm.getPublished().get("intro"));
        saved.addProperty("acceptedChoice", acceptedReward.getCodeString()); saved.addProperty("acceptedToken", accepted.getToken().toString());
        saved.addProperty("acceptedTicks", accepted.getTicks()); saved.addProperty("acceptedCard", acceptedCard.save(new CompoundTag()).toString());
        saved.add("escrow", snapshotEscrow(player, main));
        var activeConfig = NpcLlmConfig.INSTANCE.current();
        NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(false, activeConfig.getProviders(), activeConfig.getNpcs()));
        var mailId = UUID.randomUUID();
        var journalCard = PostcardItem.setText(PostcardItem.getPostcard(new ResourceLocation("contact:creeper"), false), "CQ_RESTART_TOOL_PLAN");
        ContactMailNbt.INSTANCE.setSender(journalCard, player.getScoreboardName());
        llm.getInbox().add(new NpcLlmSavedData.Mail(mailId, player.getUUID(), "CQ_RESTART_TOOL_PLAN", List.of(journalCard)));
        llm.setPendingId(mailId); llm.setPendingPlayer(player.getUUID()); llm.setPendingText("CQ_RESTART_TOOL_PLAN");
        llm.setResolved(false); llm.setToolReplyText("Resumed durable tools"); llm.setToolMailPolicy("keep");
        llm.getPendingTools().add(new NpcLlmSavedData.ToolState("completed", "get_quest", "{\"key\":\"must_not_execute\"}", "completed-journal-result"));
        llm.getPendingTools().add(new NpcLlmSavedData.ToolState("publish_after_restart", "publish_quest",
                "{\"key\":\"restart\",\"options\":{\"title\":\"Restart journal quest\",\"x\":8,\"y\":9},\"tasks\":[{\"type\":\"contactquests:parcel_task\",\"data\":{\"item\":\"minecraft:diamond\",\"count\":2}}],\"rewards\":[{\"type\":\"contactquests:parcel\",\"data\":{\"item\":\"minecraft:apple\",\"count\":1}}]}", null));
        NpcLlmSavedData.Companion.get(level).setDirty();
        saved.addProperty("events", scriptEvents.size());
        saved.addProperty("failureConversation", savedFailureConversation);
        saved.addProperty("failureRetry", savedFailureRetry);
        Files.writeString(path(), new GsonBuilder().setPrettyPrinting().create().toJson(saved));
        if (!Boolean.getBoolean("contactquests.test.noKubejs")) ContactQuestsCreationAssertions.save();
        // Test configuration persists in the private per-world config; no credentials are used.
        Path config = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("contactquests/npc_llm.json");
        Files.writeString(config, new GsonBuilder().setPrettyPrinting().create().toJson(activeConfig));
        require(previousMailbox != null, "Original mailbox snapshot is missing");
        PlatformHelper.setMailboxContents(teamId, previousMailbox);
        ServerQuestFile.INSTANCE.saveNow(); player.server.saveEverything(false, true, true);
        ContactQuestsTestServerController.recordCase("server.features.escrow_snapshot", true,
                "Fixed mock withReward passed through the production disposition path; original marked Contact card and copper x3 remain escrowed, native amethyst x5 reward unclaimed, saved for another JVM");
        ContactQuestsTestServerController.recordCase("server.features.snapshot", true, "Pending and accepted real choice cards, delivery delay, hidden table and unfinished NPC tool plan/results saved for a new JVM");
    }
    private static void rejoin(ServerPlayer player) throws Exception {
        require(!Files.exists(escrowClaimedPath()), "Feature rejoin already claimed escrow; use escrow_audit in the next JVM without reinitializing fixtures");
        var saved = JsonParser.parseString(Files.readString(path())).getAsJsonObject();
        if (!Boolean.getBoolean("contactquests.test.noKubejs")) ContactQuestsCreationAssertions.rejoin();
        require(saved.get("pid").getAsLong() != ProcessHandle.current().pid(), "Features rejoin uses same process");
        teamId = UUID.fromString(saved.get("team").getAsString()); questId = Long.parseUnsignedLong(saved.get("quest").getAsString(),16);
        choiceId = Long.parseUnsignedLong(saved.get("choice").getAsString(),16);
        var quest = ServerQuestFile.INSTANCE.getQuest(questId);
        require(quest != null && quest.getRewards().size() == 6 && quest.getRewards().stream().allMatch(r -> ((ParcelRewardBase)r).getHideContents()), "Hidden rewards or table were not restored");
        var reward = (ParcelChoiceReward) ServerQuestFile.INSTANCE.getReward(Long.parseUnsignedLong(saved.get("pendingChoice").getAsString(),16));
        var request = ChoiceRewardSavedData.Companion.get(player.serverLevel()).request(player, reward, 0);
        require(request.getToken().toString().equals(saved.get("token").getAsString()) && request.getText().equals(saved.get("text").getAsString()), "Pending selection/template changed after restart");
        var c = NpcLlmSavedData.Companion.get(player.serverLevel()).conversation(NPC, teamId, player.getUUID(), "team");
        require(c.getTurns().size() == saved.get("turns").getAsInt() + 2 && c.getKeptItems().size() == saved.get("kept").getAsInt() + 1
                && c.getPublished().get("intro").equals(saved.get("published").getAsString()), "Mock NPC history/stash/tools did not survive restart");
        require(c.getPendingId() == null && c.getPendingTools().isEmpty()
                && c.getInbox().stream().noneMatch(mail -> mail.getText().equals("CQ_MOCK_FAIL"))
                && c.getPublished().size() == 2 && c.getPublished().containsKey("restart")
                && c.getTurns().get(c.getTurns().size() - 1).getText().equals("Resumed durable tools\ncompleted-journal-result\n" + c.getPublished().get("restart"))
                && c.getKeptItems().stream().anyMatch(s -> "CQ_RESTART_TOOL_PLAN".equals(PostcardItem.getText(s))
                    && player.getScoreboardName().equals(ContactMailNbt.INSTANCE.getSender(s))),
                "Restart replayed a completed tool, mixed the isolated failure mail into the team conversation, lost pending mail or skipped an unfinished publication");
        var resumedQuest = QuestGraphService.INSTANCE.getQuest(player.serverLevel(), c.getPublished().get("restart"));
        require(resumedQuest != null && resumedQuest.getRawTitle().equals("Restart journal quest") && resumedQuest.getX() == 8 && resumedQuest.getY() == 9
                && resumedQuest.getTasks().size() == 1 && resumedQuest.getRewards().size() == 1
                && QuestGraphService.INSTANCE.listQuests(player.serverLevel(), resumedQuest.getChapter().getCodeString()).stream()
                    .filter(q -> q.getRawTitle().equals("Restart journal quest")).count() == 1, "Resumed journal duplicated or corrupted the native graph");
        assertRestartJournalReply(player);
        ContactQuestsTestServerController.recordCase("server.features.tool_journal_rejoin", true, "New JVM preserves completed tool result and real mail; unfinished authorized tool publishes exactly one native quest, finishes one NPC turn and delivers one exact Contact reply");
        var acceptedReward = (ParcelChoiceReward) ServerQuestFile.INSTANCE.getReward(Long.parseUnsignedLong(saved.get("acceptedChoice").getAsString(), 16));
        var store = ChoiceRewardSavedData.Companion.get(player.serverLevel()); var accepted = store.request(player, acceptedReward, 1);
        require(accepted.getToken().toString().equals(saved.get("acceptedToken").getAsString()) && accepted.getAccepted() && !accepted.getDelivered()
                && accepted.getTicks() > 0 && accepted.getTicks() <= saved.get("acceptedTicks").getAsInt()
                && accepted.getSubmittedCard().save(new CompoundTag()).toString().equals(saved.get("acceptedCard").getAsString()),
                "Restart changed an accepted real card, token or remaining delivery delay");
        int diamondsBefore = mailCount(Items.DIAMOND), applesBefore = mailCount(Items.APPLE);
        for (int i = accepted.getTicks(); i > 0; i--) store.tick(player.serverLevel());
        for (int i = 0; i < 10; i++) RewardDistributionManager.INSTANCE.onServerTick(player.serverLevel());
        require(accepted.getDelivered() && ServerQuestFile.INSTANCE.getNullableTeamData(teamId).isRewardClaimed(player.getUUID(), acceptedReward)
                && mailCount(Items.DIAMOND) == diamondsBefore + 7 && mailCount(Items.APPLE) == applesBefore,
                "Accepted card did not deliver precisely its selected native reward after restart");
        var repeat = store.receive(player, acceptedReward.getClaimRecipient(), List.of(accepted.getSubmittedCard()), 0);
        require(repeat.size() == 1 && mailCount(Items.DIAMOND) == diamondsBefore + 7, "Resumed accepted request delivered twice");
        assertRestartJournalReply(player);
        ContactQuestsTestServerController.recordCase("server.features.accepted_rejoin", true, "Accepted Contact card/components/token/delay survive a new JVM and deliver exactly seven diamonds once to the original team");
        require(legacyMailCallbacks == 0 && stateEvents.isEmpty() && scriptEvents.stream().noneMatch(e ->
                e.get("questId").getAsString().equals(quest.getCodeString()) || e.get("tag").getAsString().equals("CQFeatureEvent")
                        || e.get("tag").getAsString().startsWith("CQUiUnlock")), "Read/load/rejoin replayed feature state or reaction events");
        claimEscrowAfterRestart(player, saved);
        ContactQuestsTestServerController.recordCase("server.features.rejoin", true, "New JVM restored sealed rewards, native table, pending postcard token/text and isolated NPC context without event replay");
    }

    private static ItemStack markEscrow(ItemStack stack, String role) {
        stack.getOrCreateTag().putString(ESCROW_MARKER, role);
        return stack;
    }

    private static JsonObject snapshotEscrow(ServerPlayer player, Quest main) throws Exception {
        var level = player.serverLevel(); var file = ServerQuestFile.INSTANCE;
        var store = NpcLlmSavedData.Companion.get(level);
        require(!Files.exists(escrowClaimedPath()) && store.all().stream().noneMatch(c -> ESCROW_NPC.equals(c.getNpc()))
                        && escrowMail(player).size() == 0 && escrowPending(player) == 0,
                "Restart escrow fixture already exists; never reseed claimed evidence");
        var original = NpcLlmConfig.INSTANCE.current();
        require(!original.getNpcs().containsKey(ESCROW_NPC) && !original.getProviders().containsKey("escrow_mock"),
                "Isolated restart NPC/provider already configured");
        var provider = new NpcLlmConfig.Provider("mock", "", "", "fixed-mock", "low", 30, 512, List.of());
        var npc = new NpcLlmConfig.Npc(true, "escrow_mock", "Restart escrow courier", "team", 12, "", "",
                List.of(), "", "escrow_restart", 0, 0);
        var providers = new LinkedHashMap<>(original.getProviders()); providers.put("escrow_mock", provider);
        var npcs = new LinkedHashMap<>(original.getNpcs()); npcs.put(ESCROW_NPC, npc);
        var active = new NpcLlmConfig.Config(original.getEnabled(), providers, npcs);
        var quest = QuestGraphService.INSTANCE.createQuest(level, main.getChapter().getCodeString(),
                "cqtest:features/escrow_restart", "Unclaimed restart escrow");
        quest.addTask(new CheckmarkTask(file.newID(), quest));
        var reward = new ParcelReward(file.newID(), quest);
        reward.setTargetAddressee(ESCROW_NPC); reward.setItem(markEscrow(new ItemStack(Items.AMETHYST_SHARD), "reward"));
        reward.setCount(5); quest.addReward(reward); ContactQuestsTestUtil.refreshIndex(file); file.markDirty();
        var team = file.getNullableTeamData(teamId); team.setCompleted(quest.id, new Date());
        require(reward.getAutoClaimType() == dev.ftb.mods.ftbquests.quest.reward.RewardAutoClaim.DISABLED
                        && !team.isRewardClaimed(player.getUUID(), reward), "Restart reward must remain manually claimable");
        var card = markEscrow(PostcardItem.setText(PostcardItem.getPostcard(new ResourceLocation("contact:creeper"), false),
                "CQ_MOCK_WITH_REWARDS\nCQ_ESCROW_RESTART"), "card");
        ContactMailNbt.INSTANCE.setSender(card, player.getScoreboardName());
        var copper = markEscrow(new ItemStack(Items.COPPER_INGOT, 3), "attachment");
        var c = store.conversation(ESCROW_NPC, teamId, player.getUUID(), "team");
        var mail = new NpcLlmSavedData.Mail(UUID.randomUUID(), player.getUUID(), PostcardItem.getText(card), List.of(card, copper));
        var response = NpcLlmTransport.INSTANCE.call(provider, new NpcLlmTransport.Input(ESCROW_NPC, npc.getPersona(),
                provider.getModel(), provider.getReasoning(), List.of(), mail.getText(), Set.of()));
        require("withReward".equals(response.getMailPolicy()) && response.getTools().isEmpty(), "Fixed mock must choose withReward");
        // Seed a resolved model decision, then let the public production pump store the real attachments.
        // Restart evidence below comes from the runner's saved world in a different JVM, never a load(save(...)) copy.
        c.getInbox().add(mail); c.setPendingId(mail.getId()); c.setPendingPlayer(player.getUUID()); c.setPendingText(mail.getText());
        c.getTurns().add(new NpcLlmSavedData.Turn("user", mail.getText()));
        c.getTurns().add(new NpcLlmSavedData.Turn("assistant", response.getText()));
        c.setReplyText(response.getText()); c.setMailPolicy(response.getMailPolicy()); c.setResolved(true);
        try {
            NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, providers, Map.of(ESCROW_NPC, npc)));
            NpcLlmService.INSTANCE.tick(level);
            RewardDistributionManager.INSTANCE.onServerTick(level);
        } finally { NpcLlmConfig.INSTANCE.configure(active); }
        require(c.getPendingId() == null && c.getInbox().isEmpty() && c.getKeptItems().isEmpty()
                        && c.getRewardItems().size() == 1 && c.getRewardItems().get(0).getPlayerId().equals(player.getUUID())
                        && c.getRewardItems().get(0).getItems().size() == 2
                        && ItemStack.matches(c.getRewardItems().get(0).getItems().get(0), card)
                        && ItemStack.matches(c.getRewardItems().get(0).getItems().get(1), copper)
                        && escrowMail(player).size() == 0 && escrowPending(player) == 0,
                "Production withReward must retain the exact nonempty original mail without prematurely returning attachments");
        store.setDirty();
        var saved = new JsonObject(); saved.addProperty("pid", ProcessHandle.current().pid());
        saved.addProperty("player", player.getUUID().toString()); saved.addProperty("team", teamId.toString());
        saved.addProperty("npc", ESCROW_NPC); saved.addProperty("key", c.getKey());
        saved.addProperty("quest", quest.getCodeString()); saved.addProperty("reward", reward.getCodeString());
        var rewardStack = reward.getItem().copy(); rewardStack.setCount(reward.getCount());
        saved.addProperty("rewardStack", rewardStack.save(new CompoundTag()).toString());
        saved.addProperty("context", escrowContext(player, c).toString()); saved.addProperty("claimed", false);
        saved.addProperty("cardCount", 1); saved.addProperty("attachmentCount", 3); saved.addProperty("rewardCount", 5);
        saved.add("delivered", escrowStacks(player, List.of(card, copper, rewardStack)));
        return saved;
    }

    private static NpcLlmSavedData.Conversation escrowConversation(ServerPlayer player, JsonObject saved) {
        require(player.getUUID().toString().equals(saved.get("player").getAsString())
                        && teamId.toString().equals(saved.get("team").getAsString())
                        && teamId.equals(FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow().getId())
                        && ESCROW_NPC.equals(saved.get("npc").getAsString()) && NpcLlmConfig.INSTANCE.npc(ESCROW_NPC) != null,
                "Restart escrow player, team or configured NPC changed");
        // Lookup only: a missing persisted conversation must not be silently recreated.
        var c = NpcLlmSavedData.Companion.get(player.serverLevel()).all().stream()
                .filter(entry -> entry.getKey().equals(saved.get("key").getAsString())).findFirst().orElseThrow();
        require(ESCROW_NPC.equals(c.getNpc()) && teamId.equals(c.getTeamId()) && c.getOwner() == null
                        && c.getPendingId() == null && c.getInbox().isEmpty() && c.getKeptItems().isEmpty()
                        && c.getPendingTools().isEmpty() && "withReward".equals(c.getMailPolicy()),
                "Restart escrow context changed or restarted its completed model turn");
        return c;
    }

    private static ParcelReward escrowReward(ServerPlayer player, JsonObject saved) throws Exception {
        var file = ServerQuestFile.INSTANCE;
        var quest = file.getQuest(Long.parseUnsignedLong(saved.get("quest").getAsString(), 16));
        var candidate = file.getReward(Long.parseUnsignedLong(saved.get("reward").getAsString(), 16));
        require(quest != null && candidate instanceof ParcelReward && quest.getRewards().size() == 1
                        && quest.getRewards().iterator().next() == candidate && quest.getTasks().size() == 1,
                "Persisted restart escrow quest or native reward is missing");
        var reward = (ParcelReward) candidate;
        var expected = ItemStack.of(TagParser.parseTag(saved.get("rewardStack").getAsString()));
        var actual = reward.getItem().copy(); actual.setCount(reward.getCount());
        require(ESCROW_NPC.equals(reward.getTargetAddressee()) && ItemStack.matches(actual, expected)
                        && reward.getCount() == 5 && reward.getRandomBonus() == 0 && !reward.getEnableTeamMultiplier()
                        && reward.getAutoClaimType() == dev.ftb.mods.ftbquests.quest.reward.RewardAutoClaim.DISABLED
                        && file.getNullableTeamData(teamId).isCompleted(quest), "Native escrow reward changed through restart");
        return reward;
    }

    private static void claimEscrowAfterRestart(ServerPlayer player, JsonObject snapshot) throws Exception {
        var saved = snapshot.getAsJsonObject("escrow");
        require(saved.get("pid").getAsLong() == snapshot.get("pid").getAsLong()
                        && saved.get("pid").getAsLong() != ProcessHandle.current().pid() && !Files.exists(escrowClaimedPath()),
                "Escrow claim requires the first new JVM and must not overwrite prior claimed evidence");
        var c = escrowConversation(player, saved); var reward = escrowReward(player, saved);
        var team = ServerQuestFile.INSTANCE.getNullableTeamData(teamId);
        require(!saved.get("claimed").getAsBoolean() && !team.isRewardClaimed(player.getUUID(), reward)
                        && c.getRewardItems().size() == 1 && c.getRewardItems().get(0).getPlayerId().equals(player.getUUID())
                        && c.getRewardItems().get(0).getItems().size() == 2
                        && escrowContext(player, c).equals(TagParser.parseTag(saved.get("context").getAsString()))
                        && escrowMail(player).size() == 0 && escrowPending(player) == 0,
                "New JVM lost/returned nonempty escrow or claimed the reward before the original player");
        ContactQuestsTestServerController.recordCase("server.features.escrow_rejoin", true,
                "Different JVM restored exact nonempty withReward escrow, original player/team/NPC and an unclaimed native reward; no premature mailbox or queued return");
        team.claimReward(player, reward, false);
        for (int i = 0; i < 10; i++) RewardDistributionManager.INSTANCE.onServerTick(player.serverLevel());
        require(team.isRewardClaimed(player.getUUID(), reward) && c.getRewardItems().isEmpty()
                        && escrowMail(player).equals(saved.getAsJsonArray("delivered")) && escrowPending(player) == 0,
                "Native FTB claim after restart must deliver the exact original card, copper x3 and reward amethyst x5 once");
        ContactQuestsTestServerController.recordCase("server.features.escrow_claim", true,
                "Original player claimed the restored same-NPC FTB reward: exact original card/copper x3 plus amethyst x5 reached the real team mailbox and escrow emptied");
        var before = mailboxState(player); var context = escrowContext(player, c);
        team.claimReward(player, reward, false);
        for (int i = 0; i < 10; i++) RewardDistributionManager.INSTANCE.onServerTick(player.serverLevel());
        require(team.isRewardClaimed(player.getUUID(), reward) && before.equals(mailboxState(player))
                        && context.equals(escrowContext(player, c)) && escrowPending(player) == 0,
                "Repeated native claim changed the real mailbox, context or delivery queue");
        var claimed = saved.deepCopy(); claimed.addProperty("snapshotPid", saved.get("pid").getAsLong());
        claimed.addProperty("pid", ProcessHandle.current().pid()); claimed.addProperty("claimed", true);
        claimed.addProperty("context", context.toString()); claimed.addProperty("pending", escrowPending(player));
        claimed.add("delivered", escrowMail(player));
        ServerQuestFile.INSTANCE.saveNow(); player.server.saveEverything(false, true, true);
        Files.writeString(escrowClaimedPath(), new GsonBuilder().setPrettyPrinting().create().toJson(claimed));
        ContactQuestsTestServerController.recordCase("server.features.escrow_repeat", true,
                "Repeated native claim changed zero mailbox slots/context/queued deliveries; claimed state and exact marked items saved for the optional third JVM audit");
    }

    private static void auditEscrow(ServerPlayer player) throws Exception {
        var snapshot = JsonParser.parseString(Files.readString(path())).getAsJsonObject();
        var saved = snapshot.getAsJsonObject("escrow");
        var claimed = JsonParser.parseString(Files.readString(escrowClaimedPath())).getAsJsonObject();
        long now = ProcessHandle.current().pid(), first = snapshot.get("pid").getAsLong(), second = claimed.get("pid").getAsLong();
        require(now != first && now != second && first != second && saved.get("pid").getAsLong() == first
                        && claimed.get("snapshotPid").getAsLong() == first, "Escrow audit requires a third distinct JVM");
        for (String key : List.of("player", "team", "npc", "key", "quest", "reward", "rewardStack", "delivered",
                "cardCount", "attachmentCount", "rewardCount"))
            require(saved.get(key).equals(claimed.get(key)), "Claim evidence changed original escrow identity/items: " + key);
        teamId = UUID.fromString(saved.get("team").getAsString());
        var c = escrowConversation(player, saved); var reward = escrowReward(player, saved);
        var team = ServerQuestFile.INSTANCE.getNullableTeamData(teamId);
        require(claimed.get("claimed").getAsBoolean() && team.isRewardClaimed(player.getUUID(), reward)
                        && c.getRewardItems().isEmpty() && escrowContext(player, c).equals(TagParser.parseTag(claimed.get("context").getAsString()))
                        && escrowMail(player).equals(claimed.getAsJsonArray("delivered"))
                        && claimed.get("pending").getAsInt() == 0 && escrowPending(player) == 0,
                "Third JVM replayed/lost claimed escrow, its exact marked mailbox items, context or queue state");
        var before = mailboxState(player); var context = escrowContext(player, c);
        team.claimReward(player, reward, false);
        for (int i = 0; i < 10; i++) RewardDistributionManager.INSTANCE.onServerTick(player.serverLevel());
        require(team.isRewardClaimed(player.getUUID(), reward) && before.equals(mailboxState(player))
                        && context.equals(escrowContext(player, c)) && escrowPending(player) == 0,
                "Third-JVM native duplicate claim replayed mail or changed persisted context");
        ContactQuestsTestServerController.recordCase("server.features.escrow_audit", true,
                "Third distinct JVM retained claimed=true, empty escrow/queue and exact original card/copper x3/amethyst x5; native duplicate claim changed zero mailbox slots or context");
    }

    private static CompoundTag escrowContext(ServerPlayer player, NpcLlmSavedData.Conversation c) {
        var all = NpcLlmSavedData.Companion.get(player.serverLevel()).save(new CompoundTag())
                .getList("Conversations", 10);
        for (int i = 0; i < all.size(); i++) if (all.getCompound(i).getString("key").equals(c.getKey())) return all.getCompound(i).copy();
        throw new IllegalStateException("Persisted escrow context is missing");
    }

    private static int escrowPending(ServerPlayer player) {
        var pending = RewardDeliverySavedData.Companion.get(player.serverLevel()).save(new CompoundTag())
                .getList("PendingRewards", 10);
        int count = 0;
        for (int i = 0; i < pending.size(); i++) if (ESCROW_NPC.equals(pending.getCompound(i).getString("sender"))) count++;
        return count;
    }

    private static JsonArray escrowStacks(ServerPlayer player, List<ItemStack> stacks) {
        var json = new JsonArray();
        stacks.stream().map(s -> s.save(new CompoundTag()).toString()).sorted().forEach(json::add);
        return json;
    }

    private static JsonArray escrowMail(ServerPlayer player) {
        var marked = new ArrayList<ItemStack>(); var box = PlatformHelper.getMailboxContents(teamId);
        for (int i = 0; i < box.getContainerSize(); i++) {
            var stack = box.getItem(i);
            if (stack.getTag() != null && stack.getTag().contains(ESCROW_MARKER)) marked.add(stack);
            ContactMailNbt.INSTANCE.readContents(stack).forEach(s -> {
                if (s.getTag() != null && s.getTag().contains(ESCROW_MARKER)) marked.add(s);
            });
        }
        return escrowStacks(player, marked);
    }

    private static List<String> mailboxState(ServerPlayer player) {
        var state = new ArrayList<String>(); var box = PlatformHelper.getMailboxContents(teamId);
        for (int i = 0; i < box.getContainerSize(); i++) {
            var stack = box.getItem(i); state.add(stack.isEmpty() ? "" : stack.save(new CompoundTag()).toString());
        }
        return state;
    }

    private static void verifyFailureRejoin(ServerPlayer player) throws Exception {
        var saved = JsonParser.parseString(Files.readString(path())).getAsJsonObject();
        require(saved.get("pid").getAsLong() != ProcessHandle.current().pid(), "Failure queue was checked in the same JVM");
        teamId = UUID.fromString(saved.get("team").getAsString());
        var c = NpcLlmSavedData.Companion.get(player.serverLevel()).conversation(saved.get("failureConversation").getAsString(), teamId, player.getUUID(), "player");
        var mail = c.getInbox().stream().filter(item -> item.getText().equals("CQ_MOCK_FAIL") && item.getPlayerId().equals(player.getUUID())).findFirst().orElse(null);
        require(mail != null && mail.getId().equals(c.getPendingId()) && c.getPendingText().equals("CQ_MOCK_FAIL")
                && c.getPendingPlayer().equals(player.getUUID()) && !c.getResolved() && c.getReplyText().isBlank()
                && c.getRetryAfterTick() == saved.get("failureRetry").getAsLong()
                && c.getTurns().stream().noneMatch(turn -> turn.getRole().equals("assistant") && turn.getText().contains("CQ_MOCK_FAIL")),
                "Failed provider envelope/retry or no-fabricated-reply state did not survive JVM restart");
        ContactQuestsTestServerController.recordCase("server.features.pending_failure_rejoin", true,
                "New JVM restored the same unanswered real Contact card, player-bound pending request and retry delay without a legacy reaction");
    }
    private static void assertRestartJournalReply(ServerPlayer player) {
        int exact = 0, unexpected = 0;
        for (var card : mailboxPostcards()) {
            if (!NPC.equals(ContactMailNbt.INSTANCE.getSender(card))) continue;
            if (isRestartJournalReply(player, teamId, card)) exact += card.getCount();
            else unexpected += card.getCount();
        }
        require(exact == 1 && unexpected == 0, "Restart journal reply is missing, duplicated or incorrect: " + List.of(exact, unexpected));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static List<ItemStack> mailboxPostcards() {
        var cards = new ArrayList<ItemStack>(); var box = PlatformHelper.getMailboxContents(teamId);
        for (int i = 0; i < box.getContainerSize(); i++) {
            var stack = box.getItem(i);
            if (stack.getItem() instanceof PostcardItem) cards.add(stack);
            ContactMailNbt.INSTANCE.readContents(stack).forEach(s -> {
                if (s.getItem() instanceof PostcardItem) cards.add(s);
            });
        }
        return cards;
    }
}
