package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.config.ChoiceTemplateConfig;
import com.creeperyang.contactquests.config.ContactConfig;
import com.creeperyang.contactquests.data.*;
import com.creeperyang.contactquests.llm.*;
import com.creeperyang.contactquests.quest.*;
import com.creeperyang.contactquests.quest.reward.*;
import com.creeperyang.contactquests.bridge.ITeamDataExtension;
import com.creeperyang.contactquests.utils.ContactMailNbt;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.common.item.ParcelItem;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbquests.quest.*;
import dev.ftb.mods.ftbquests.quest.loot.*;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder(ContactQuestsTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ContactQuestsFeatureGameTests {
    private record Fixture(ServerLevel level, ServerPlayer player, TeamData data, Chapter chapter, Quest quest) {}
    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel().getServer().overworld();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "CQNewFeatures"));
        var manager = (dev.ftb.mods.ftbteams.data.TeamManagerImpl) FTBTeamsAPI.api().getManager();
        manager.playerLoggedIn(null, player.getUUID(), player.getScoreboardName());
        var file = ServerQuestFile.INSTANCE;
        var chapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(), "cq_features_" + UUID.randomUUID());
        file.getDefaultChapterGroup().addChapter(chapter);
        var quest = new Quest(file.newID(), chapter); chapter.addQuest(quest);
        var data = file.getOrCreateTeamData(manager.getTeamForPlayer(player).orElseThrow()); data.setLocked(false);
        ContactQuestsTestUtil.refreshIndex(file);
        return new Fixture(level, player, data, chapter, quest);
    }
    private static ParcelChoiceReward choice(Fixture f) {
        var file = ServerQuestFile.INSTANCE;
        var reward = new ParcelChoiceReward(file.newID(), f.quest);
        var table = new RewardTable(file.newID(), file);
        table.addReward(new WeightedReward(new ItemReward(file.newID(), table.getFakeQuest(), new ItemStack(Items.APPLE), 3), 1));
        table.addReward(new WeightedReward(new ItemReward(file.newID(), table.getFakeQuest(), new ItemStack(Items.DIAMOND), 7), 9));
        file.addRewardTable(table); reward.setTable(table); reward.setTargetAddressee("CQChoice_" + f.quest.getCodeString());
        f.quest.addReward(reward); f.quest.addTask(new CheckmarkTask(file.newID(), f.quest));
        ContactQuestsTestUtil.refreshIndex(file); f.data.setCompleted(f.quest.getId(), new Date());
        return reward;
    }
    private static ItemStack card(String text) {
        return PostcardItem.setText(PostcardItem.getPostcard(new ResourceLocation("contact:creeper"), false), text);
    }
    private static ItemStack card(ChoiceRewardSavedData.Request request) {
        var card = card(request.getText()); ChoiceRewardSavedData.bindCard(card, request); return card;
    }
    private static int bufferCount(UUID player, Item item) throws Exception {
        var field = RewardDistributionManager.class.getDeclaredField("buffer"); field.setAccessible(true);
        int count = 0;
        for (var entry : ((Map<?, ?>) field.get(RewardDistributionManager.INSTANCE)).entrySet()) {
            var accessor = entry.getKey().getClass().getDeclaredMethod("getPlayerId"); accessor.setAccessible(true);
            if (!player.equals(accessor.invoke(entry.getKey()))) continue;
            for (var list : ((Map<?, ?>) entry.getValue()).values()) for (var stack : (List<ItemStack>) list) if (stack.is(item)) count += stack.getCount();
        }
        var queueField = RewardDeliverySavedData.class.getDeclaredField("pendingRewards"); queueField.setAccessible(true);
        var level = ServerQuestFile.INSTANCE.server.overworld();
        for (var value : (List<RewardDeliverySavedData.PendingReward>) queueField.get(RewardDeliverySavedData.Companion.get(level))) {
            if (!player.equals(value.getPlayerUUID())) continue;
            var stack = value.getParcelStack();
            if (stack.is(item)) count += stack.getCount();
            for (var inside : ContactMailNbt.INSTANCE.readContents(stack)) if (inside.is(item)) count += inside.getCount();
        }
        return count;
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void choiceRequiresRealPostcardAndSelectedDelivery(GameTestHelper helper) throws Exception {
        var f = fixture(helper); var reward = choice(f); var store = ChoiceRewardSavedData.Companion.get(f.level);
        int diamondsBefore = bufferCount(f.player.getUUID(), Items.DIAMOND), applesBefore = bufferCount(f.player.getUUID(), Items.APPLE);
        reward.claim(f.player, false);
        helper.assertTrue(!f.data.isRewardClaimed(f.player.getUUID(), reward)
                && bufferCount(f.player.getUUID(), Items.DIAMOND) == diamondsBefore
                && bufferCount(f.player.getUUID(), Items.APPLE) == applesBefore, "Direct choice claim bypassed the postcard");
        var request = store.request(f.player, reward, 1);
        helper.assertTrue(request == store.request(f.player, reward, 1), "Repeated same option changed token/template");
        helper.assertTrue(!request.getText().contains("CQ:") && !request.getText().contains(request.getToken().toString())
                && !request.getText().contains("{request}") && request.getRecipient().equals(reward.getTargetAddressee()),
                "Technical request identity leaked into the natural letter or NPC address");
        var forged = new ItemStack(Items.PAPER); forged.getOrCreateTag().putString("Text", request.getText());
        helper.assertTrue(store.receive(f.player, reward.getClaimRecipient(), List.of(forged), 0).size() == 1, "Paper accepted as a Contact postcard");
        helper.assertTrue(store.receive(f.player, "wrong recipient", List.of(card(request)), 0).size() == 1, "Wrong recipient accepted");
        helper.assertTrue(store.receive(f.player, reward.getClaimRecipient(), List.of(card(request.getText())), 0).size() == 1,
                "Copied prose without a hidden server selection was accepted");
        var wrongSender = card(request); ContactMailNbt.INSTANCE.setSender(wrongSender, "SomebodyElse");
        helper.assertTrue(store.receive(f.player, reward.getClaimRecipient(), List.of(wrongSender), 0).size() == 1, "Forged Contact sender accepted");
        var valid = PostcardItem.setText(card(request), "你好！这次我想要钻石，多谢啦。\n旅行者留"); valid.setCount(2);
        var remainder = store.receive(f.player, reward.getClaimRecipient(), List.of(valid), 0);
        helper.assertTrue(remainder.size() == 1 && remainder.get(0).getCount() == 1, "Choice consumed more than one postcard");
        helper.assertTrue(f.data.isRewardClaimed(f.player.getUUID(), reward), "Verified card failed to mark actual FTB claim");
        helper.assertTrue(bufferCount(f.player.getUUID(), Items.DIAMOND) - diamondsBefore == 7 && bufferCount(f.player.getUUID(), Items.APPLE) == applesBefore,
                "Choice did not deliver precisely the selected seven diamonds");
        store.receive(f.player, reward.getClaimRecipient(), List.of(card(request)), 0);
        helper.assertTrue(bufferCount(f.player.getUUID(), Items.DIAMOND) - diamondsBefore == 7, "Repeated postcard duplicated reward");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void choiceInventoryBindingRejectsInvalidAndReplacedSelections(GameTestHelper helper) throws Exception {
        var f = fixture(helper); var reward = choice(f); var store = ChoiceRewardSavedData.Companion.get(f.level);
        f.player.getInventory().clearContent();
        f.player.getInventory().setItem(0, new ItemStack(Items.PAPER));
        var first = store.request(f.player, reward, 0);
        for (int slot : List.of(-1, 36, 0)) {
            boolean rejected = false;
            try { store.prepareCard(f.player, reward.getId(), slot); } catch (RuntimeException expected) { rejected = true; }
            helper.assertTrue(rejected && !ChoiceRewardSavedData.hasChoiceMarker(f.player.getInventory().getItem(0)),
                    "Invalid inventory slot or plain paper acquired a claim marker");
        }
        for (String sender : List.of("A previous correspondent", "")) {
            var received = card("Keep this received letter untouched");
            // An empty Sender tag still marks received stationery; setSender skips empty strings.
            received.getOrCreateTag().putString("Sender", sender);
            var before = received.copy(); f.player.getInventory().setItem(0, received);
            boolean rejected = false;
            try { store.prepareCard(f.player, reward.getId(), 0); } catch (RuntimeException expected) { rejected = true; }
            helper.assertTrue(rejected && ItemStack.matches(before, received)
                            && !ChoiceRewardSavedData.isEditablePostcard(received),
                    "Received/read-only postcard was changed or accepted as writable stationery");
        }
        var original = card("自由手写的信，不必复述任务或谢礼");
        original.getOrCreateTag().putString("unrelated", "preserve me");
        original.setHoverName(net.minecraft.network.chat.Component.literal("旅行明信片"));
        f.player.getInventory().setItem(0, original);
        helper.assertTrue(store.prepareCard(f.player, reward.getId(), 0) == first
                        && ChoiceRewardSavedData.cardToken(original).equals(first.getToken())
                        && original.getOrCreateTag().getString("unrelated").equals("preserve me")
                        && PostcardItem.getText(original).equals("自由手写的信，不必复述任务或谢礼")
                        && original.getHoverName().getString().equals("旅行明信片"),
                "Preparing a real inventory card changed its prose, name or unrelated components");
        var oldCard = original.copy();
        var replacement = store.request(f.player, reward, 1);
        helper.assertTrue(!replacement.getToken().equals(first.getToken())
                        && store.receive(f.player, reward.getTargetAddressee(), List.of(oldCard), 0).size() == 1,
                "Changing the selection left the old hidden marker authorized");
        var forged = original.copy(); var forgedData = forged.getOrCreateTag();
        forgedData.getCompound("ContactQuestsChoice").putUUID("request", UUID.randomUUID());
        helper.assertTrue(store.receive(f.player, reward.getTargetAddressee(), List.of(forged), 0).size() == 1,
                "A random hidden UUID authorized a claim");
        store.prepareCard(f.player, reward.getId(), 0);
        var authorized = original.copy();
        int before = bufferCount(f.player.getUUID(), Items.DIAMOND);
        helper.assertTrue(store.receive(f.player, reward.getTargetAddressee(), List.of(authorized), 0).isEmpty()
                        && bufferCount(f.player.getUUID(), Items.DIAMOND) == before + 7,
                "A handwritten inventory card failed to claim the server's replacement selection");
        boolean rejected = false;
        try { store.prepareCard(f.player, reward.getId(), 0); } catch (RuntimeException expected) { rejected = true; }
        helper.assertTrue(rejected && store.receive(f.player, reward.getTargetAddressee(), List.of(authorized), 0).size() == 1
                        && bufferCount(f.player.getUUID(), Items.DIAMOND) == before + 7,
                "Claimed selection could be prepared or replayed again");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void choiceLegacyPendingAddressRefreshesWithoutChangingProseRules(GameTestHelper helper) throws Exception {
        var f = fixture(helper); var reward = choice(f); var store = new ChoiceRewardSavedData();
        var previous = store.request(f.player, reward, 1);
        var saved = store.save(new CompoundTag());
        saved.getList("Requests", Tag.TAG_COMPOUND).getCompound(0).putString("recipient", "CQ-Choice-" + reward.getCodeString());
        saved.getList("Requests", Tag.TAG_COMPOUND).getCompound(0).putString("text", "Old visible request identity");
        var restored = ChoiceRewardSavedData.Companion.load(saved);
        var current = restored.request(f.player, reward, 1);
        helper.assertTrue(!current.getToken().equals(previous.getToken())
                        && current.getRecipient().equals(reward.getTargetAddressee())
                        && !current.getText().contains("CQ-Choice-")
                        && current == restored.request(f.player, reward, 1),
                "Old unsubmitted system-address request was reused or its refreshed identity was unstable");
        f.player.getInventory().setItem(0, card("Completely different handwritten prose"));
        restored.prepareCard(f.player, reward.getId(), 0);
        int before = bufferCount(f.player.getUUID(), Items.DIAMOND);
        helper.assertTrue(restored.receive(f.player, reward.getTargetAddressee(), List.of(f.player.getInventory().getItem(0).copy()), 0).isEmpty()
                        && bufferCount(f.player.getUUID(), Items.DIAMOND) == before + 7,
                "Refreshed legacy choice could not complete a real hidden-card claim");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void choiceSameNpcUsesHiddenIdentityWithoutProseMatching(GameTestHelper helper) throws Exception {
        var f = fixture(helper); var first = choice(f); var second = choice(f);
        second.setTargetAddressee(first.getTargetAddressee());
        var store = ChoiceRewardSavedData.Companion.get(f.level);
        var a = store.request(f.player, first, 0); var b = store.request(f.player, second, 1);
        helper.assertTrue(a.getRecipient().equals(b.getRecipient()) && !a.getToken().equals(b.getToken()),
                "Two selections for one NPC shared hidden identity or exposed different system addresses");
        var bCard = PostcardItem.setText(card(b), "");
        helper.assertTrue(store.receive(f.player, b.getRecipient(), List.of(bCard), 0).isEmpty()
                        && f.data.isRewardClaimed(f.player.getUUID(), second)
                        && !f.data.isRewardClaimed(f.player.getUUID(), first)
                        && bufferCount(f.player.getUUID(), Items.DIAMOND) == 7
                        && bufferCount(f.player.getUUID(), Items.APPLE) == 0,
                "Empty prose or a shared NPC confused which selection was claimed");
        helper.assertTrue(store.receive(f.player, a.getRecipient(), List.of(PostcardItem.setText(card(a), "改天见！")), 0).isEmpty()
                        && f.data.isRewardClaimed(f.player.getUUID(), first)
                        && bufferCount(f.player.getUUID(), Items.APPLE) == 3,
                "Second independent selection was lost after claiming its same-NPC neighbour");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void choiceInvalidRequestsDoNotPersistAndOfflineCardsWait(GameTestHelper helper) throws Exception {
        var f = fixture(helper); var reward = choice(f); var store = ChoiceRewardSavedData.Companion.get(f.level);
        var before = store.save(new CompoundTag()).copy();
        for (int option : List.of(-1, 2)) {
            boolean rejected = false;
            try { store.request(f.player, reward, option); } catch (RuntimeException expected) { rejected = true; }
            helper.assertTrue(rejected && before.equals(store.save(new CompoundTag())), "Invalid option persisted a half request");
        }
        var table = reward.getTable(); reward.setTable(null);
        boolean rejected = false;
        try { store.request(f.player, reward, 0); } catch (RuntimeException expected) { rejected = true; }
        reward.setTable(table); f.data.setCompleted(f.quest.getId(), null);
        helper.assertTrue(rejected && before.equals(store.save(new CompoundTag())), "Missing table persisted a request");
        rejected = false;
        try { store.request(f.player, reward, 0); } catch (RuntimeException expected) { rejected = true; }
        helper.assertTrue(rejected && before.equals(store.save(new CompoundTag())), "Incomplete quest persisted a request");
        f.data.setCompleted(f.quest.getId(), new Date());
        var request = store.request(f.player, reward, 0); var original = card(request);
        ContactMailNbt.INSTANCE.setSender(original, f.player.getScoreboardName());
        helper.assertTrue(store.receive(f.player, reward.getClaimRecipient(), List.of(original), 2).isEmpty(), "Delayed actual card was not accepted");
        for (int i = 0; i < 4; i++) store.tick(f.level);
        helper.assertTrue(f.level.getServer().getPlayerList().getPlayer(f.player.getUUID()) == null
                && request.getAccepted() && request.getTicks() == 0 && !request.getDelivered() && !request.getGranted() && !request.getCancelled()
                && ItemStack.matches(original, request.getSubmittedCard()) && !f.data.isRewardClaimed(f.player.getUUID(), reward)
                && bufferCount(f.player.getUUID(), Items.APPLE) == 0, "Offline choice did not wait with the exact original card and identity");
        // Retain this world's evidence without leaving a live delayed test request.
        reward.setTargetAddressee("ChangedOfflineSource"); store.tick(f.level); store.tick(f.level);
        helper.assertTrue(request.getCancelled() && bufferCount(f.player.getUUID(), original.getItem()) == 1,
                "Offline invalidated source did not refund its Contact card exactly once");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void stateEventReentrancyPreservesActorAndTeam(GameTestHelper helper) {
        var a = fixture(helper); var b = fixture(helper);
        var tagA = "NoQuestA_" + a.quest.getCodeString(); var tagB = "NoQuestB_" + b.quest.getCodeString();
        var tagAfter = "NoQuestAfter_" + a.quest.getCodeString();
        var changes = new ArrayList<QuestStateEvents.Change>();
        Consumer<QuestStateEvents.Change> throwing = change -> { if (change.getTag().equals(tagA)) throw new IllegalStateException("fixed listener isolation failure"); };
        Consumer<QuestStateEvents.Change> listener = change -> {
            if (!Set.of(tagA, tagB, tagAfter).contains(change.getTag())) return;
            changes.add(change);
            if (change.getTag().equals(tagA)) QuestStateEvents.withActor(b.player, "nested_b", () ->
                    ((ITeamDataExtension) b.data).contactQuests$unlockTag(tagB));
        };
        QuestStateEvents.addListener(throwing); QuestStateEvents.addListener(listener);
        try {
            QuestStateEvents.withActor(a.player, "outer_a", () -> {
                try { QuestStateEvents.withIdentity(b.player.getUUID(), b.player, "must_restore", () -> { throw new IllegalStateException("identity fixture"); }); }
                catch (IllegalStateException expected) { }
                ((ITeamDataExtension) a.data).contactQuests$unlockTag(tagA);
                ((ITeamDataExtension) a.data).contactQuests$unlockTag(tagAfter);
            });
            helper.assertTrue(changes.size() == 3 && changes.get(0).getTag().equals(tagA) && changes.get(1).getTag().equals(tagB)
                    && changes.get(2).getTag().equals(tagAfter), "Nested/throwing event listeners dropped or duplicated mutations");
            for (int i = 0; i < 3; i++) {
                var change = changes.get(i); var f = i == 1 ? b : a;
                helper.assertTrue(change.getQuestId().isEmpty() && change.getTeamId().equals(f.data.getTeamId())
                        && f.player.getUUID().equals(change.getPlayerId()) && change.getPlayer() == f.player
                        && change.getMembers().contains(f.player.getUUID()) && change.getCause().equals(i == 1 ? "nested_b" : "outer_a")
                        && !change.getBefore().getTags().contains(change.getTag()) && change.getAfter().getTags().contains(change.getTag())
                        && (i == 0 || change.getSequence() > changes.get(i - 1).getSequence()), "Nested event crossed actor/team/state or sequence");
                var js = new com.creeperyang.contactquests.compat.kubejs.QuestStateEventJS(change);
                helper.assertTrue(js.getPlayer() == f.player && js.getMembers().contains(f.player.getUUID().toString())
                        && js.getSequence().equals(Long.toString(change.getSequence())) && js.getBefore().equals(change.getBefore())
                        && js.getAfter().equals(change.getAfter()), "KubeJS event lost native actor/members/state/sequence");
            }
            ((ITeamDataExtension) a.data).contactQuests$unlockTag(tagA); a.data.serializeNBT(); a.data.canStartTasks(a.quest);
            helper.assertTrue(changes.size() == 3, "Repeat grant/getter/serialization replayed nested events");
        } finally { QuestStateEvents.removeListener(listener); QuestStateEvents.removeListener(throwing); }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void graphChildKeysAndNativeInheritanceStayAtomic(GameTestHelper helper) {
        var f = fixture(helper); var graph = QuestGraphService.INSTANCE;
        String prefix = "cqtest:strict/" + f.quest.getCodeString().toLowerCase();
        var other = graph.createQuest(f.level, f.chapter.getCodeString(), prefix + "/other", "Other");
        graph.updateChapter(f.level, f.chapter.getCodeString(), Map.of("consumeItems", true));
        var data = new CompoundTag(); data.putString("item", "minecraft:apple"); data.putBoolean("consume_items", false); data.putBoolean("only_from_crafting", true);
        var task = (dev.ftb.mods.ftbquests.quest.task.ItemTask) graph.addTask(f.level, f.quest.getCodeString(), prefix + "/task", "ftbquests:item", data);
        helper.assertTrue(!task.consumesResources() && task.isOnlyFromCrafting(), "Explicit false/true creation lost native item policy");
        var title = new CompoundTag(); title.putString("title", "Keep policy"); graph.updateTask(f.level, task.getCodeString(), title);
        helper.assertTrue(!task.consumesResources() && task.isOnlyFromCrafting(), "Omitted update reset native item policy");
        var reset = new CompoundTag(); reset.putString("consume_items", "DEFAULT"); reset.putString("only_from_crafting", "DEFAULT");
        graph.updateTask(f.level, task.getCodeString(), reset);
        helper.assertTrue(task.consumesResources() && !task.isOnlyFromCrafting(), "DEFAULT failed to restore chapter inheritance");
        var reward = graph.addReward(f.level, f.quest.getCodeString(), prefix + "/reward", "contactquests:parcel", new CompoundTag());
        var taskBefore = new CompoundTag(); task.writeData(taskBefore);
        var rewardBefore = new CompoundTag(); reward.writeData(rewardBefore);
        for (int kind = 0; kind < 4; kind++) {
            boolean rejected = false;
            try {
                if (kind < 2) graph.addTask(f.level, kind == 0 ? other.getCodeString() : f.quest.getCodeString(), prefix + "/task",
                        kind == 0 ? "ftbquests:item" : "ftbquests:checkmark", new CompoundTag());
                else graph.addReward(f.level, kind == 2 ? other.getCodeString() : f.quest.getCodeString(), prefix + "/reward",
                        kind == 2 ? "contactquests:parcel" : "contactquests:postcard", new CompoundTag());
            } catch (RuntimeException expected) { rejected = true; }
            var actualTask = new CompoundTag(); task.writeData(actualTask);
            var actualReward = new CompoundTag(); reward.writeData(actualReward);
            helper.assertTrue(rejected && actualTask.equals(taskBefore) && actualReward.equals(rewardBefore)
                    && f.quest.getTasks().size() == 1 && f.quest.getRewards().size() == 1
                    && other.getTasks().isEmpty() && other.getRewards().isEmpty(), "Conflicting stable key mutated native graph");
        }
        for (var invalid : List.of(Map.of("consume_items", (Object) 2), Map.of("only_from_crafting", (Object) "yes"))) {
            boolean rejected = false;
            try { graph.updateTask(f.level, task.getCodeString(), QuestGraphSettings.INSTANCE.creationData(invalid)); }
            catch (RuntimeException expected) { rejected = true; }
            var actual = new CompoundTag(); task.writeData(actual);
            helper.assertTrue(rejected && actual.equals(taskBefore), "Invalid native tristate mutated the existing task");
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void choicePersistsIdentityAndRejectsStaleRequest(GameTestHelper helper) {
        var f = fixture(helper); var reward = choice(f); var store = ChoiceRewardSavedData.Companion.get(f.level);
        var request = store.request(f.player, reward, 0);
        var restored = ContactQuestsTestUtil.roundTrip(store);
        helper.assertTrue(restored.request(f.player, reward, 0).getToken().equals(request.getToken()), "Restart replaced pending selection");
        var other = FakePlayerFactory.get(f.level, new GameProfile(UUID.randomUUID(), "CQChoiceOutsider"));
        ((dev.ftb.mods.ftbteams.data.TeamManagerImpl) FTBTeamsAPI.api().getManager()).playerLoggedIn(null, other.getUUID(), other.getScoreboardName());
        helper.assertTrue(restored.receive(other, reward.getClaimRecipient(), List.of(card(request)), 0).size() == 1, "Other team/player could use request");
        f.data.setCompleted(f.quest.getId(), null); f.data.setCompleted(f.quest.getId(), new Date(request.getCompletedAt() + 1000));
        helper.assertTrue(restored.receive(f.player, reward.getClaimRecipient(), List.of(card(request)), 0).size() == 1, "Old completion card accepted after reset");
        var fresh = restored.request(f.player, reward, 0);
        reward.setTargetAddressee("CQChangedChoiceTarget");
        helper.assertTrue(restored.receive(f.player, request.getRecipient(), List.of(card(fresh)), 0).size() == 1, "Edited reward accepted stale request");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void choiceTemplatesUseValidatedWeights(GameTestHelper helper) {
        var templates = List.of(new ChoiceTemplateConfig.Template("disabled", 0, "never"), new ChoiceTemplateConfig.Template("only", 5, "{task_goal}: {reward}"));
        var original = ChoiceTemplateConfig.INSTANCE.current();
        var updated = new LinkedHashMap<>(original); updated.put("cqtest_templates", templates);
        try {
            ChoiceTemplateConfig.INSTANCE.configure(updated);
            for (int i = 0; i < 20; i++) helper.assertTrue(ChoiceTemplateConfig.INSTANCE.select("cqtest_templates", net.minecraft.util.RandomSource.create(i)).getId().equals("only"), "Zero weight template selected");
            var text = ChoiceTemplateConfig.INSTANCE.fill(templates.get(1), Map.of("task_goal", "diamond x2", "reward", "apple x3"));
            helper.assertTrue(text.equals("diamond x2: apple x3"), "Optional prose placeholder replacement failed");
            var appended = ChoiceTemplateConfig.INSTANCE.fill(new ChoiceTemplateConfig.Template("no_token", 1, "Choose {reward}"),
                    Map.of("reward", "apple x3"));
            helper.assertTrue(appended.equals("Choose apple x3"), "Template appended a technical identifier");
            boolean rejected = false;
            try { ChoiceTemplateConfig.INSTANCE.configure(Map.of("default", List.of(new ChoiceTemplateConfig.Template("bad", -1, "x")))); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected && ChoiceTemplateConfig.INSTANCE.select("cqtest_templates", net.minecraft.util.RandomSource.create()).getId().equals("only"), "Invalid replacement damaged active templates");
            for (var invalid : List.of(
                    List.of(new ChoiceTemplateConfig.Template("duplicate", 1, "a"), new ChoiceTemplateConfig.Template("duplicate", 1, "b")),
                    List.of(new ChoiceTemplateConfig.Template("empty", 0, "a")),
                    List.of(new ChoiceTemplateConfig.Template("long", 1, "x".repeat(1501))))) {
                rejected = false;
                try { ChoiceTemplateConfig.INSTANCE.configure(Map.of("cqtest_templates", invalid)); }
                catch (IllegalArgumentException expected) { rejected = true; }
                helper.assertTrue(rejected && ChoiceTemplateConfig.INSTANCE.select("cqtest_templates", net.minecraft.util.RandomSource.create()).getId().equals("only"),
                        "Invalid duplicate/zero/oversized template replaced the active configuration");
            }
            rejected = false;
            try { ChoiceTemplateConfig.INSTANCE.fill(new ChoiceTemplateConfig.Template("oversized_fill", 1, "{reward}"),
                    Map.of("reward", "x".repeat(2001))); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "Overlong filled Contact postcard was accepted");
        } finally { ChoiceTemplateConfig.INSTANCE.configure(original); }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void nativeStateEventsOnlyFollowMutations(GameTestHelper helper) {
        var f = fixture(helper); var file = ServerQuestFile.INSTANCE;
        var dependency = new Quest(file.newID(), f.chapter); f.chapter.addQuest(dependency);
        var task = new CheckmarkTask(file.newID(), dependency); dependency.addTask(task);
        ContactQuestsTestUtil.refreshIndex(file);
        QuestGraphService.INSTANCE.updateQuest(f.level, f.quest.getCodeString(), Map.of("requiredTags", List.of("CQEventTag"), "mutex", List.of(dependency.getCodeString())));
        var changes = new ArrayList<QuestStateEvents.Change>(); Consumer<QuestStateEvents.Change> listener = c -> { if (c.getTeamId().equals(f.data.getTeamId())) changes.add(c); };
        QuestStateEvents.addListener(listener);
        try {
            var ext = (ITeamDataExtension) f.data;
            QuestStateEvents.withActor(f.player, "script_test", () -> ext.contactQuests$unlockTag("CQEventTag"));
            ext.contactQuests$unlockTag("CQEventTag");
            for (int i = 0; i < 5; i++) { f.data.canStartTasks(f.quest); f.quest.isVisible(f.data); f.data.clearCachedProgress(); }
            helper.assertTrue(changes.size() == 1 && changes.get(0).getKind().equals("tagUnlocked")
                    && changes.get(0).getQuestId().equals(f.quest.getCodeString()) && f.player.getUUID().equals(changes.get(0).getPlayerId()), "Tag event missing/duplicated or wrong identity");
            file.withPlayerContext(f.player, () -> f.data.setProgress(task, 1));
            helper.assertTrue(changes.size() == 2 && changes.get(changes.size() - 1).getKind().equals("mutexChanged") && changes.get(changes.size() - 1).getAfter().getMutex(), "Native completion did not lock mutex exactly once");
            f.data.setProgress(task, 0);
            helper.assertTrue(changes.size() == 2 && ((com.creeperyang.contactquests.bridge.IQuestExtension) (Object) f.quest).contactQuests$isLockedByMutex(f.data),
                    "Single task progress reset changed a still-completed quest's mutex state");
            dependency.forceProgressRaw(f.data, new dev.ftb.mods.ftbquests.util.ProgressChange(file, dependency, f.player.getUUID()).setReset(true));
        helper.assertTrue(changes.size() == 3 && !changes.get(changes.size() - 1).getAfter().getMutex()
                && f.player.getUUID().equals(changes.get(changes.size() - 1).getPlayerId()), "Native quest reset did not unlock mutex or preserve offline actor UUID");
            ext.contactQuests$blockQuest(f.quest.getId()); ext.contactQuests$blockQuest(f.quest.getId()); ext.contactQuests$unblockQuest(f.quest.getId());
            helper.assertTrue(changes.size() == 5 && changes.get(3).getKind().equals("scriptBlockChanged")
                    && !changes.get(3).getAfter().getMutex() && changes.get(3).getAfter().getScriptBlocked(), "Script block confused with true mutex");
            var nbt = f.data.serializeNBT();
            var copy = new TeamData(f.data.getTeamId(), file); copy.deserializeNBT(nbt);
            helper.assertTrue(changes.size() == 5, "Reading SavedData replayed state events");
        } finally { QuestStateEvents.removeListener(listener); }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void graphCreationDefaultsAndBundleValidation(GameTestHelper helper) {
        var f = fixture(helper); var graph = QuestGraphService.INSTANCE;
        var blanks = new LinkedHashMap<String, Object>(); blanks.put("x", null); blanks.put("shape", "  "); blanks.put("title", "");
        var quest = graph.createQuest(f.level, f.chapter.getCodeString(), "cqtest:features/defaults/" + f.quest.getCodeString().toLowerCase(), "", blanks);
        helper.assertTrue(quest.getX() == 0 && quest.getY() == 0 && quest.getRawTitle().isEmpty(), "Blank creation options replaced native defaults");
        var parcel = graph.addReward(f.level, quest.getCodeString(), "cqtest:features/default_reward/" + f.quest.getCodeString().toLowerCase(), "contactquests:parcel", new CompoundTag());
        helper.assertTrue(parcel instanceof ParcelReward p && p.getItem().is(Items.APPLE) && p.getCount() == 1 && !p.getHideContents(), "Reward creation lost default item/count/visibility");
        var nbt = new CompoundTag(); nbt.putBoolean("team_reward", true); graph.updateReward(f.level, parcel.getCodeString(), nbt);
        nbt.putString("team_reward", "DEFAULT"); graph.updateReward(f.level, parcel.getCodeString(), nbt);
        var saved = new CompoundTag(); parcel.writeData(saved);
        helper.assertTrue(!saved.contains("team_reward"), "DEFAULT failed to restore native team inheritance");
        int countBefore = f.chapter.getQuests().size(); boolean rejected = false;
        try { graph.publishQuest(f.level, f.chapter.getCodeString(), "cqtest:features/invalid/" + f.quest.getCodeString().toLowerCase(), Map.of("title", "must not appear"),
                List.of(Map.of("type", "contactquests:parcel_task", "data", Map.of("count", -1))), List.of(Map.of("type", "contactquests:parcel"))); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected && f.chapter.getQuests().size() == countBefore, "Invalid bundle left a half-created quest");
        rejected = false;
        var unknown = new CompoundTag(); unknown.putString("unknown_empty_field", "");
        try { graph.addReward(f.level, quest.getCodeString(), "cqtest:features/unknown/" + f.quest.getCodeString().toLowerCase(), "contactquests:parcel", unknown); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected && quest.getRewards().size() == 1, "Unknown empty creation field escaped validation or created an object");
        var item = new CompoundTag(); item.putString("item", "minecraft:apple"); item.putInt("count", 2);
        var nativeReward = graph.addReward(f.level, quest.getCodeString(), "cqtest:features/native_overflow/" + f.quest.getCodeString().toLowerCase(), "ftbquests:item", item);
        var bonus = new CompoundTag(); bonus.putInt("random_bonus", Integer.MAX_VALUE - 1); rejected = false;
        try { graph.updateReward(f.level, nativeReward.getCodeString(), bonus); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected && ((ItemReward) nativeReward).getCount() == 2, "Merged native count/bonus overflow modified the reward");
        rejected = false;
        try { com.creeperyang.contactquests.quest.QuestGraphSettings.INSTANCE.creationData(Map.of("tags", List.of("valid", 1))); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "Mixed NBT list silently discarded a supplied creation value");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void everyPinnedTypeCreatesWithFactoryDefaults(GameTestHelper helper) throws Exception {
        var f = fixture(helper); var graph = QuestGraphService.INSTANCE; var file = ServerQuestFile.INSTANCE;
        var catalog = new com.google.gson.JsonObject();
        int tasks = 0, rewards = 0;
        for (var entry : dev.ftb.mods.ftbquests.quest.task.TaskTypes.TYPES.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (!Set.of("ftbquests", "contactquests").contains(entry.getKey().getNamespace())) continue;
            var original = entry.getValue().createTask(file.newID(), f.quest);
            var defaults = new CompoundTag(); original.writeData(defaults);
            var created = graph.addTask(f.level, f.quest.getCodeString(), "cqtest:defaults/" + f.quest.getCodeString().toLowerCase() + "/" + entry.getKey(),
                    entry.getKey().toString(), new CompoundTag());
            var actual = new CompoundTag(); created.writeData(actual);
            helper.assertTrue(actual.equals(defaults), "Empty creation differs from " + entry.getKey() + " factory defaults: " + defaults + " / " + actual);
            catalog.addProperty(entry.getKey() + ":task", actual.toString()); tasks++;
        }
        for (var entry : dev.ftb.mods.ftbquests.quest.reward.RewardTypes.TYPES.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (!Set.of("ftbquests", "contactquests").contains(entry.getKey().getNamespace())) continue;
            var original = entry.getValue().createReward(file.newID(), f.quest);
            var defaults = new CompoundTag(); original.writeData(defaults);
            var created = graph.addReward(f.level, f.quest.getCodeString(), "cqtest:defaults/" + f.quest.getCodeString().toLowerCase() + "/" + entry.getKey(),
                    entry.getKey().toString(), new CompoundTag());
            var actual = new CompoundTag(); created.writeData(actual);
            helper.assertTrue(actual.equals(defaults), "Empty creation differs from " + entry.getKey() + " factory defaults: " + defaults + " / " + actual);
            catalog.addProperty(entry.getKey() + ":reward", actual.toString()); rewards++;
        }
        var report = System.getProperty("contactquests.test.gameTestResult");
        if (report != null) java.nio.file.Files.writeString(java.nio.file.Path.of(report).resolveSibling("creation-defaults.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(catalog));
        helper.assertTrue(tasks == 18 && rewards == 18, "Pinned native/Contact type catalog is incomplete: tasks=" + tasks + ", rewards=" + rewards + "; types=" + catalog.keySet());
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void inlineTitlesPersistWithoutOrphanTranslations(GameTestHelper helper) throws Exception {
        var f = fixture(helper); var file = ServerQuestFile.INSTANCE; var graph = QuestGraphService.INSTANCE;
        // 1.20.1 stores titles in each object's NBT, without a separate translation manager.
        var before = new CompoundTag(); f.quest.writeData(before);
        var chapterBefore = new CompoundTag(); f.chapter.writeData(chapterBefore);
        int countBefore = f.chapter.getQuests().size(); boolean rejected = false;
        try {
            graph.publishQuest(f.level, f.chapter.getCodeString(), "cqtest:features/orphan/" + f.quest.getCodeString().toLowerCase(),
                    Map.of("title", "Never committed"), List.of(), List.of(
                            Map.of("type", "contactquests:parcel_random", "data", Map.of("table_data", Map.of("title", "Detached table",
                                    "rewards", List.of(Map.of("item", "minecraft:apple", "title", "Detached option", "locale", "en_us"))))),
                            Map.of("type", "contactquests:parcel", "data", Map.of("count", -1))));
        } catch (IllegalArgumentException expected) { rejected = true; }
        var after = new CompoundTag(); f.quest.writeData(after);
        var chapterAfter = new CompoundTag(); f.chapter.writeData(chapterAfter);
        helper.assertTrue(rejected && countBefore == f.chapter.getQuests().size()
                && after.equals(before) && chapterAfter.equals(chapterBefore), "Invalid bundle changed native graph objects or inline titles");
        var created = (ParcelRandomReward) graph.addReward(f.level, f.quest.getCodeString(), "cqtest:features/local_title/" + f.quest.getCodeString().toLowerCase(),
                "contactquests:parcel_random", QuestGraphSettings.INSTANCE.creationData(Map.of("table_data", Map.of("title", "Own table", "filename", "own_table",
                        "rewards", List.of(Map.of("item", "minecraft:apple", "title", "Own option"))))));
        var other = (ParcelRandomReward) graph.addReward(f.level, f.quest.getCodeString(), "cqtest:features/other_title/" + f.quest.getCodeString().toLowerCase(),
                "contactquests:parcel_random", QuestGraphSettings.INSTANCE.creationData(Map.of("table_data", Map.of("title", "Other table",
                        "rewards", List.of(Map.of("item", "minecraft:diamond", "title", "Other option"))))));
        var saved = new CompoundTag(); created.writeData(saved);
        var restored = new ParcelRandomReward(file.newID(), f.quest); restored.readData(saved);
        var copied = new CompoundTag(); restored.writeData(copied);
        helper.assertTrue(saved.equals(copied) && restored.getTable().getRawTitle().equals("Own table")
                && restored.getTable().getWeightedRewards().get(0).getReward().getRawTitle().equals("Own option")
                && other.getTable().getRawTitle().equals("Other table") && saved.getCompound("table_data").getString("filename").equals("own_table"),
                "Inline table/option title or filename was ignored, lost on disk, or shared with another fake ID");
        var nativeReward = (dev.ftb.mods.ftbquests.quest.reward.RandomReward) graph.addReward(f.level, f.quest.getCodeString(),
                "cqtest:features/native_inline_title/" + f.quest.getCodeString().toLowerCase(), "ftbquests:random",
                QuestGraphSettings.INSTANCE.creationData(Map.of("table_data", Map.of("title", "Native inline title",
                        "rewards", List.of(Map.of("item", "minecraft:apple", "title", "Native inline option"))))));
        helper.assertTrue(nativeReward.getTable().getRawTitle().equals("Native inline title"), "Native RandomReward replaced the requested table title with Internal");
        var standalone = new RewardTable(file.newID(), file);
        var globalEntry = new ItemReward(file.newID(), standalone.getFakeQuest(), new ItemStack(Items.APPLE));
        globalEntry.setRawTitle("Standalone global title");
        var standaloneSaved = new CompoundTag(); globalEntry.writeData(standaloneSaved);
        var standaloneCopy = new ItemReward(file.newID(), standalone.getFakeQuest(), new ItemStack(Items.APPLE));
        standaloneCopy.readData(standaloneSaved);
        helper.assertTrue(standaloneSaved.getString("title").equals("Standalone global title")
                        && standaloneCopy.getRawTitle().equals("Standalone global title"), "Standalone reward title was lost from native NBT");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void inlineNestedBlanksUseNativeDefaults(GameTestHelper helper) {
        var f = fixture(helper); var graph = QuestGraphService.INSTANCE;
        int index = 0;
        for (Object blank : Arrays.asList(null, "", " \n ")) {
            var drops = new LinkedHashMap<String, Object>();
            for (String key : List.of("passive", "monster", "boss")) drops.put(key, blank);
            var crate = new LinkedHashMap<String, Object>();
            for (String key : List.of("string_id", "item_name", "color", "glow")) crate.put(key, blank);
            crate.put("drops", drops);
            var entry = new LinkedHashMap<String, Object>();
            entry.put("item", "minecraft:apple");
            for (String key : List.of("id", "type", "weight", "count", "title", "translate")) entry.put(key, blank);
            var table = new LinkedHashMap<String, Object>();
            table.put("loot_crate", crate); table.put("rewards", List.of(entry));
            for (String key : List.of("title", "translate", "loot_size")) table.put(key, blank);
            var reward = (ParcelRandomReward) graph.addReward(f.level, f.quest.getCodeString(),
                    "cqtest:features/nested_blanks/" + f.quest.getCodeString().toLowerCase() + "/" + index++, "contactquests:parcel_random",
                    QuestGraphSettings.INSTANCE.creationData(Map.of("table_data", table)));
            var expected = new CompoundTag(); new LootCrate(reward.getTable(), false).writeData(expected);
            var actual = new CompoundTag(); reward.getTable().getLootCrate().writeData(actual);
            helper.assertTrue(actual.equals(expected) && ((ItemReward) reward.getTable().getWeightedRewards().get(0).getReward()).getCount() == 1
                    && reward.getTable().getWeightedRewards().get(0).getWeight() == 1,
                    "Nested null/blank input failed to use native crate, item or weight defaults: " + expected + " / " + actual);
            graph.addReward(f.level, f.quest.getCodeString(), "cqtest:features/nested_blanks/" + f.quest.getCodeString().toLowerCase() + "/" + (index - 1),
                    "contactquests:parcel_random", new CompoundTag());
            graph.updateReward(f.level, reward.getCodeString(), QuestGraphSettings.INSTANCE.creationData(Map.of("table_data",
                    Map.of("loot_crate", Map.of("drops", " "), "rewards", List.of(Map.of("item", "minecraft:apple"))))));
            actual = new CompoundTag(); reward.getTable().getLootCrate().writeData(actual);
            helper.assertTrue(actual.equals(expected), "Missing/blank drops or an upsert lost native crate defaults");
        }
        var explicit = (ParcelRandomReward) graph.addReward(f.level, f.quest.getCodeString(),
                "cqtest:features/nested_explicit/" + f.quest.getCodeString().toLowerCase(), "contactquests:parcel_random",
                QuestGraphSettings.INSTANCE.creationData(Map.of("table_data", Map.of("loot_crate",
                        Map.of("color", 0, "glow", false, "drops", Map.of("passive", 0, "monster", 0, "boss", 5))))));
        helper.assertTrue(explicit.getTable().getLootCrate().getColor().rgb() == 0 && !explicit.getTable().getLootCrate().isGlow()
                && explicit.getTable().getLootCrate().getDrops().boss == 5, "Explicit zero/false/drop weight became a default");
        int before = f.quest.getRewards().size();
        for (var bad : List.of(Map.of("unknown_blank", " "), Map.of("drops", Map.of("unknown_blank", "")),
                Map.of("color", (long) Integer.MAX_VALUE + 1), Map.of("drops", Map.of("boss", -1)))) {
            boolean rejected = false;
            try { graph.addReward(f.level, f.quest.getCodeString(), "cqtest:features/nested_invalid/" + f.quest.getCodeString().toLowerCase(),
                    "contactquests:parcel_random", QuestGraphSettings.INSTANCE.creationData(Map.of("table_data", Map.of("loot_crate", bad)))); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected && f.quest.getRewards().size() == before, "Invalid nested config left an object or ignored unknown blank fields");
        }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void inlineIdsRejectConflictsBeforeGraphMutation(GameTestHelper helper) {
        var f = fixture(helper); var graph = QuestGraphService.INSTANCE; var file = ServerQuestFile.INSTANCE;
        String key = "cqtest:features/inline_ids/" + f.quest.getCodeString().toLowerCase();
        var data = QuestGraphSettings.INSTANCE.creationData(Map.of("table_data", Map.of("rewards", List.of(Map.of("item", "minecraft:apple")))));
        var owned = (ParcelRandomReward) graph.addReward(f.level, f.quest.getCodeString(), key, "contactquests:parcel_random", data);
        long ownId = owned.getTable().getWeightedRewards().get(0).getReward().getId();
        graph.addReward(f.level, f.quest.getCodeString(), key, "contactquests:parcel_random", data);
        helper.assertTrue(owned.getTable().getWeightedRewards().get(0).getReward().getId() == ownId, "Repeated addReward changed generated inline identity");
        var snapshot = new CompoundTag(); owned.writeData(snapshot);
        graph.updateReward(f.level, owned.getCodeString(), snapshot);
        helper.assertTrue(owned.getTable().getWeightedRewards().get(0).getReward().getId() == ownId, "Own saved inline ID rejected on update");
        int before = f.quest.getRewards().size();
        var standalone = new RewardTable(file.newID(), file);
        var standaloneEntry = new ItemReward(file.newID(), standalone.getFakeQuest(), new ItemStack(Items.DIAMOND));
        standalone.addReward(new WeightedReward(standaloneEntry, 1)); file.addRewardTable(standalone); ContactQuestsTestUtil.refreshIndex(file);
        for (long conflict : List.of(f.quest.getId(), owned.getId(), ownId, standalone.getId(), standaloneEntry.getId(), -1L)) {
            boolean rejected = false;
            try { graph.addReward(f.level, f.quest.getCodeString(), key + "/foreign", "contactquests:parcel_random",
                    QuestGraphSettings.INSTANCE.creationData(Map.of("table_data", Map.of("rewards",
                            List.of(Map.of("id", QuestObjectBase.getCodeString(conflict), "item", "minecraft:apple")))))); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected && f.quest.getRewards().size() == before, "Conflicting inline ID created an object: " + QuestObjectBase.getCodeString(conflict));
        }
        boolean rejected = false;
        var duplicate = Map.of("id", QuestObjectBase.getCodeString(file.newID()), "item", "minecraft:apple");
        try { graph.updateReward(f.level, owned.getCodeString(), QuestGraphSettings.INSTANCE.creationData(Map.of("table_data",
                Map.of("rewards", List.of(duplicate, duplicate))))); } catch (IllegalArgumentException expected) { rejected = true; }
        var after = new CompoundTag(); owned.writeData(after);
        helper.assertTrue(rejected && after.equals(snapshot), "Duplicate inline IDs modified the existing table");
        int questsBefore = f.chapter.getQuests().size(); rejected = false;
        try { graph.publishQuest(f.level, f.chapter.getCodeString(), key + "/bundle", Map.of("title", "Never committed"), List.of(),
                List.of(Map.of("type", "contactquests:parcel_random", "data", Map.of("table_data", Map.of("rewards", List.of(duplicate)))),
                        Map.of("type", "contactquests:parcel_random", "data", Map.of("table_data", Map.of("rewards", List.of(duplicate)))))); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected && f.chapter.getQuests().size() == questsBefore, "Cross-table collision left a partly published quest");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void choiceAcceptedCardPersistsAndRefundsChangedSource(GameTestHelper helper) throws Exception {
        var f = fixture(helper); var reward = choice(f); var store = ChoiceRewardSavedData.Companion.get(f.level);
        var request = store.request(f.player, reward, 0);
        var submitted = card(request);
        ContactMailNbt.INSTANCE.setSender(submitted, f.player.getScoreboardName());
        helper.assertTrue(store.receive(f.player, reward.getClaimRecipient(), List.of(submitted), 3).isEmpty()
                && !f.data.isRewardClaimed(f.player.getUUID(), reward), "Delayed card did not remain pending");
        var copy = ContactQuestsTestUtil.roundTrip(store);
        var saved = copy.save(new CompoundTag()).getList("Requests", Tag.TAG_COMPOUND).stream()
                .map(t -> (CompoundTag)t).filter(t -> t.getUUID("token").equals(request.getToken())).findFirst().orElseThrow();
        helper.assertTrue(saved.getBoolean("accepted") && saved.getInt("ticks") == 3
                && ItemStack.matches(ItemStack.of(saved.getCompound("card")), submitted),
                "Accepted delay, sender or actual Contact card components were lost on restart");
        int cardsBefore = bufferCount(f.player.getUUID(), submitted.getItem()), applesBefore = bufferCount(f.player.getUUID(), Items.APPLE);
        var changedEntry = reward.getTable().getWeightedRewards().get(0).getReward();
        var changedData = new CompoundTag(); changedEntry.writeData(changedData);
        changedData.putInt("count", 4); changedEntry.readData(changedData);
        for (int i = 0; i < 3; i++) copy.tick(f.level);
        var refunded = copy.save(new CompoundTag()).getList("Requests", Tag.TAG_COMPOUND).stream()
                .map(t -> (CompoundTag)t).filter(t -> t.getUUID("token").equals(request.getToken())).findFirst().orElseThrow();
        helper.assertTrue(refunded.getBoolean("cancelled") && !refunded.getBoolean("delivered")
                && bufferCount(f.player.getUUID(), submitted.getItem()) == cardsBefore + 1
                && bufferCount(f.player.getUUID(), Items.APPLE) == applesBefore && !f.data.isRewardClaimed(f.player.getUUID(), reward),
                "Offline changed table did not refund exactly the original postcard without granting items");
        copy.tick(f.level);
        helper.assertTrue(bufferCount(f.player.getUUID(), submitted.getItem()) == cardsBefore + 1, "Cancelled card refunded twice");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void choicePreCallbackCannotDuplicateOrGrantChangedSource(GameTestHelper helper) throws Exception {
        var f = fixture(helper); var reward = choice(f); var store = ChoiceRewardSavedData.Companion.get(f.level);
        var request = store.request(f.player, reward, 1); int before = bufferCount(f.player.getUUID(), Items.DIAMOND);
        f.data.claimReward(f.player, reward, false);
        helper.assertTrue(bufferCount(f.player.getUUID(), Items.DIAMOND) == before
                && !f.data.isRewardClaimed(f.player.getUUID(), reward),
                "Unauthorized claimReward marked reward claimed or granted items before postcard submission");
        store.receive(f.player, reward.getClaimRecipient(), List.of(card(request)), 0);
        reward.claim(f.player, false);
        f.data.claimReward(f.player, reward, false);
        helper.assertTrue(bufferCount(f.player.getUUID(), Items.DIAMOND) == before + 7
                && f.data.isRewardClaimed(f.player.getUUID(), reward) && request.getDelivered(),
                "Duplicate claim call duplicated reward or lost native claim marker");
        var f2 = fixture(helper); var reward2 = choice(f2); var request2 = store.request(f2.player, reward2, 0);
        int applesBefore = bufferCount(f2.player.getUUID(), Items.APPLE);
        String originalRecipient = reward2.getClaimRecipient();
        reward2.setTargetAddressee("ChangedBeforeDelivery");
        var untouched = store.receive(f2.player, originalRecipient, List.of(card(request2)), 0);
        reward2.claim(f2.player, false);
        helper.assertTrue(untouched.size() == 1 && bufferCount(f2.player.getUUID(), Items.APPLE) == applesBefore
                && !request2.getAccepted() && !request2.getGranted()
                && !f2.data.isRewardClaimed(f2.player.getUUID(), reward2),
                "Source mutation bypassed source verification");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void nativeCreationFieldsKeepFluidQuantityAndOnlyOne(GameTestHelper helper) {
        var f = fixture(helper); var graph = QuestGraphService.INSTANCE;
        var fluidData = new CompoundTag(); fluidData.putString("fluid", "minecraft:water"); fluidData.putLong("amount", 2500);
        var task = graph.addTask(f.level, f.quest.getCodeString(), "cqtest:features/fluid/" + f.quest.getCodeString().toLowerCase(), "ftbquests:fluid", fluidData);
        var copy = new dev.ftb.mods.ftbquests.quest.task.FluidTask(ServerQuestFile.INSTANCE.newID(), f.quest);
        var saved = new CompoundTag(); task.writeData(saved); copy.readData(saved);
        helper.assertTrue(task.getMaxProgress() == 2500 && copy.getMaxProgress() == 2500
                        && saved.getString("fluid").equals("minecraft:water") && saved.getLong("amount") == 2500,
                "Creation amount was not normalized into persistent native fluid NBT");
        var amount = new CompoundTag(); amount.putLong("amount", 3000); graph.updateTask(f.level, task.getCodeString(), amount);
        helper.assertTrue(task.getMaxProgress() == 3000, "Amount-only update reset fluid or silently ignored quantity");
        var item = new CompoundTag(); item.putString("item", "minecraft:apple"); item.putBoolean("only_one", true);
        var reward = graph.addReward(f.level, f.quest.getCodeString(), "cqtest:features/one/" + f.quest.getCodeString().toLowerCase(), "ftbquests:item", item);
        helper.assertTrue(((com.creeperyang.contactquests.mixin.ItemRewardAccessor) reward).contactQuests$isOnlyOne(), "Native editor only_one field was not supported at creation");
        var icon = new CompoundTag(); icon.putString("icon", "minecraft:diamond"); graph.updateReward(f.level, reward.getCodeString(), icon);
        icon.put("icon", new CompoundTag()); graph.updateReward(f.level, reward.getCodeString(), icon);
        saved = new CompoundTag(); reward.writeData(saved);
        helper.assertTrue(!saved.contains("icon"), "Explicit empty icon merged instead of clearing");
        graph.updateQuest(f.level, f.quest.getCodeString(), Map.of("minRequiredDependencies", 300));
        saved = new CompoundTag(); f.quest.writeData(saved);
        var questCopy = new Quest(ServerQuestFile.INSTANCE.newID(), f.chapter); questCopy.readData(saved);
        var copied = new CompoundTag(); questCopy.writeData(copied);
        helper.assertTrue(saved.getInt("min_required_dependencies") == 300 && copied.getInt("min_required_dependencies") == 300,
                "Native editor dependency integer was truncated while saving or loading");
        var redData = new CompoundTag(); redData.putString("item", "minecraft:gold_ingot"); redData.putString("match_components", "STRICT");
        var redTask = (com.creeperyang.contactquests.quest.task.RedPacketTask) graph.addTask(f.level, f.quest.getCodeString(),
                "cqtest:features/red_fields/" + f.quest.getCodeString().toLowerCase(), "contactquests:red_packet_task", redData);
        var redSaved = new CompoundTag(); redTask.writeData(redSaved);
        helper.assertTrue(redTask.getItemStack().is(Items.GOLD_INGOT)
                && redSaved.getBoolean("match_nbt") && !redSaved.getBoolean("weak_nbt_match")
                && !redSaved.contains("match_components"),
                "Editor item alias or strict matching did not reach native red packet NBT");
        var nativeKey = new CompoundTag(); nativeKey.putString("inner_item", "minecraft:emerald"); graph.updateTask(f.level, redTask.getCodeString(), nativeKey);
        helper.assertTrue(redTask.getItemStack().is(Items.EMERALD), "Existing native inner_item call was not preserved");
        var observation = graph.addTask(f.level, f.quest.getCodeString(), "cqtest:features/observation_defaults/" + f.quest.getCodeString().toLowerCase(),
                "ftbquests:observation", new CompoundTag());
        var observationSaved = new CompoundTag(); observation.writeData(observationSaved);
        helper.assertTrue(observationSaved.getLong("timer") == 0, "Empty observation creation lost the native zero timer default");
        var invalidTimer = new CompoundTag(); invalidTimer.putLong("timer", 1201);
        boolean timerRejected = false;
        try { graph.updateTask(f.level, observation.getCodeString(), invalidTimer); } catch (IllegalArgumentException expected) { timerRejected = true; }
        helper.assertTrue(timerRejected, "Observation timer exceeded the native editor's 1200 tick bound");
        var energyData = new CompoundTag(); energyData.putLong("value", 5000); energyData.putInt("max_input", 0);
        var energy = (dev.ftb.mods.ftbquests.quest.task.EnergyTask) graph.addTask(f.level, f.quest.getCodeString(),
                "cqtest:features/energy_fields/" + f.quest.getCodeString().toLowerCase(), "ftbquests:forge_energy", energyData);
        helper.assertTrue(energy.getMaxProgress() == 5000 && energy.getMaxInput() == 0, "Native Forge energy fields were ignored");
        energyData.putLong("max_input", (long) Integer.MAX_VALUE + 1); boolean energyRejected = false;
        try { graph.updateTask(f.level, energy.getCodeString(), energyData); } catch (IllegalArgumentException expected) { energyRejected = true; }
        helper.assertTrue(energyRejected && energy.getMaxInput() == 0, "Invalid native energy input range changed the task");
        var inline = com.creeperyang.contactquests.quest.QuestGraphSettings.INSTANCE.creationData(Map.of("table_data", Map.of(
                "hide_tooltip", false, "use_title", true, "loot_size", 1, "icon", "minecraft:book", "tags", List.of("NativeTable"),
                "loot_crate", Map.of("string_id", "native_fields", "item_name", "Native fields", "color", 16711680, "glow", true, "drops", Map.of("passive", 2, "monster", 3, "boss", 4)),
                "rewards", List.of(Map.of("item", "minecraft:apple", "count", 3, "weight", 1))))) ;
        var tableReward = (ParcelRandomReward) graph.addReward(f.level, f.quest.getCodeString(),
                "cqtest:features/table_fields/" + f.quest.getCodeString().toLowerCase(), "contactquests:parcel_random", inline);
        saved = new CompoundTag(); tableReward.writeData(saved);
        var tableCopy = new ParcelRandomReward(ServerQuestFile.INSTANCE.newID(), f.quest); tableCopy.readData(saved);
        copied = new CompoundTag(); tableCopy.writeData(copied);
        helper.assertTrue(copied.toString().equals(saved.toString()) && tableCopy.getTable().getWeightedRewards().size() == 1
                && tableCopy.getTable().getLootCrate().getStringID().equals("native_fields"), "Native inline table metadata or omitted ItemReward type failed to persist");
        var invalidTable = com.creeperyang.contactquests.quest.QuestGraphSettings.INSTANCE.creationData(Map.of("table_data", Map.of("hide_tooltip", "yes")));
        boolean rejected = false;
        try { graph.updateReward(f.level, tableReward.getCodeString(), invalidTable); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected && tableReward.getTable().getLootCrate().getStringID().equals("native_fields"), "Invalid inline metadata partly modified the table");
        var defaultInline = com.creeperyang.contactquests.quest.QuestGraphSettings.INSTANCE.creationData(Map.of("table_data", Map.of("loot_size", " ")));
        var defaultTable = (ParcelRandomReward) graph.addReward(f.level, f.quest.getCodeString(),
                "cqtest:features/table_defaults/" + f.quest.getCodeString().toLowerCase(), "contactquests:parcel_random", defaultInline);
        var defaultTableSaved = new CompoundTag(); defaultTable.getTable().writeData(defaultTableSaved);
        helper.assertTrue(defaultTableSaved.getInt("loot_size") == 1, "Blank inline loot_size did not use the native one-roll constructor default");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void returnedPostcardsKeepComponentsInPersistentDelivery(GameTestHelper helper) {
        var f = fixture(helper); var submitted = card("Original return text");
        ContactMailNbt.INSTANCE.setSender(submitted, "OriginalPlayer");
        submitted.setHoverName(net.minecraft.network.chat.Component.literal("Original name"));
        var delivery = new RewardDeliverySavedData();
        delivery.addPendingReward(f.player.getUUID(), submitted, 5, "ReturningNpc", f.data.getTeamId(), true);
        var copy = ContactQuestsTestUtil.roundTrip(delivery);
        var saved = copy.save(new CompoundTag()).getList("PendingRewards", Tag.TAG_COMPOUND).getCompound(0);
        helper.assertTrue(saved.getBoolean("preserve_sender") && saved.getInt("ticks") == 5
                && ItemStack.matches(ItemStack.of(saved.getCompound("parcel")), submitted),
                "Persistent return delivery lost original postcard components or preservation flag");
        var normal = new RewardDeliverySavedData(); normal.addPendingReward(f.player.getUUID(), submitted, 5, "NormalNpc", f.data.getTeamId());
        helper.assertTrue(!normal.save(new CompoundTag()).getList("PendingRewards", Tag.TAG_COMPOUND).getCompound(0).contains("preserve_sender"),
                "Normal delivery unexpectedly adopted returned-mail semantics");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void npcLlmMockContextAndToolAuthorization(GameTestHelper helper) {
        var f = fixture(helper); var store = new NpcLlmSavedData();
        var a = store.conversation("NPC-A", f.data.getTeamId(), f.player.getUUID(), "player");
        a.getTurns().add(new NpcLlmSavedData.Turn("user", "private history"));
        var b = store.conversation("NPC-A", f.data.getTeamId(), UUID.randomUUID(), "player");
        var c = store.conversation("NPC-B", f.data.getTeamId(), f.player.getUUID(), "player");
        helper.assertTrue(b.getTurns().isEmpty() && c.getTurns().isEmpty(), "NPC or player histories crossed identities");
        var teamA = store.conversation("NPC-A", f.data.getTeamId(), f.player.getUUID(), "team");
        var teamB = store.conversation("NPC-A", f.data.getTeamId(), UUID.randomUUID(), "team");
        helper.assertTrue(teamA == teamB, "Explicit team context failed to share");
        var restored = ContactQuestsTestUtil.roundTrip(store);
        helper.assertTrue(restored.history("NPC-A", f.data.getTeamId(), f.player.getUUID(), "player").get(0).getText().equals("private history"), "Persistent NPC context lost history");
        var provider = new NpcLlmConfig.Provider("mock", "", "", "fixed-mock", "low", 30, 1024, List.of("publish_quest"));
        var input = new NpcLlmTransport.Input("NPC-A", "stay in character", "test-model", "high", a.getTurns(), "CQ_MOCK_PUBLISH", Set.of("publish_quest"));
        var response = NpcLlmTransport.INSTANCE.call(provider, input);
        helper.assertTrue(response.getText().contains("history=1, model=test-model, reasoning=high") && response.getTools().size() == 1, "Fixed mock ignored model/thinking/history");
        var npc = new NpcLlmConfig.Npc(true, "mock", "persona", "player", 12, "", "", List.of("publish_quest"), f.chapter.getCodeString(), "test", 16, 0);
        var tool = response.getTools().get(0);
        String id = NpcLlmTools.INSTANCE.execute(f.level, f.player, a, npc, provider, tool);
        var quest = QuestGraphService.INSTANCE.getQuest(f.level, id);
        helper.assertTrue(quest != null && quest.getTasks().size() == 1 && quest.getRewards().size() == 1
                && ((com.creeperyang.contactquests.quest.task.ContactTask) quest.getTasks().iterator().next()).getCount() == 2, "Mock tool did not publish validated task bundle");
        helper.assertTrue(NpcLlmTools.INSTANCE.execute(f.level, f.player, a, npc, provider, tool).equals(id), "Repeated structured tool duplicated quest");
        int before = f.chapter.getQuests().size(); boolean rejected = false;
        var denied = new NpcLlmConfig.Provider("mock", "", "", "fixed-mock", "low", 30, 1024, List.of());
        try { NpcLlmTools.INSTANCE.execute(f.level, f.player, a, npc, denied, tool); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected && f.chapter.getQuests().size() == before, "Provider without tool grant published a quest");
        var nestedArgs = com.google.gson.JsonParser.parseString("""
                {"key":"nested","tasks":[{"type":"ftbquests:checkmark"}],"rewards":[{"type":"contactquests:parcel_all_table","data":{
                "table_data":{"rewards":[{"type":"contactquests:parcel","target_addressee":"ForeignNpc","item":"minecraft:apple"}]}}}]}
                """).getAsJsonObject();
        var nestedTool = new NpcLlmTransport.Tool("nested", "publish_quest", nestedArgs);
        String nestedId = NpcLlmTools.INSTANCE.execute(f.level, f.player, a, npc, provider, nestedTool);
        var nestedQuest = QuestGraphService.INSTANCE.getQuest(f.level, nestedId);
        var nestedReward = (ParcelRandomReward) nestedQuest.getRewards().iterator().next();
        helper.assertTrue(nestedReward.getTargetAddressee().equals(a.getNpc())
                && ((ParcelRewardBase) nestedReward.getTable().getWeightedRewards().get(0).getReward()).getTargetAddressee().equals(a.getNpc()),
                "LLM nested delivery escaped its own NPC identity");
        var deniedArgs = nestedArgs.deepCopy(); deniedArgs.addProperty("key", "foreign_id");
        deniedArgs.getAsJsonArray("rewards").get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("table_data")
                .getAsJsonArray("rewards").get(0).getAsJsonObject().addProperty("id", f.quest.getCodeString());
        before = f.chapter.getQuests().size(); rejected = false;
        try { NpcLlmTools.INSTANCE.execute(f.level, f.player, a, npc, provider, new NpcLlmTransport.Tool("denied_id", "publish_quest", deniedArgs)); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected && before == f.chapter.getQuests().size() && !a.getPublished().containsKey("foreign_id"),
                "LLM-supplied inline ID bypassed authorization or created a quest");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void llmToolPlanSurvivesRestartWithoutReplay(GameTestHelper helper) {
        var f = fixture(helper); var c = new NpcLlmSavedData().conversation("NPC-A", f.data.getTeamId(), f.player.getUUID(), "player");
        var args = com.google.gson.JsonParser.parseString("{\"key\":\"stable\",\"tasks\":[],\"rewards\":[]}").getAsJsonObject();
        var store = new NpcLlmSavedData(); var persistent = store.conversation("NPC-A", f.data.getTeamId(), f.player.getUUID(), "player");
        persistent.setRetryAfterTick(12345L);
        persistent.getPendingTools().add(new NpcLlmSavedData.ToolState("same", "publish_quest", args.toString(), "stable-quest-id"));
        persistent.setToolReplyText("plan persisted");
        var copy = ContactQuestsTestUtil.roundTrip(store);
        var restoredConversation = copy.all().get(0);
        helper.assertTrue(restoredConversation.getPendingTools().size() == 1
                && restoredConversation.getPendingTools().get(0).getResult().equals("stable-quest-id")
                && restoredConversation.getToolReplyText().equals("plan persisted") && restoredConversation.getRetryAfterTick() == 12345L,
                "Tool result/retry wait was not persisted as the restart replay barrier");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void llmLookupAndInvalidConfigStayConversationScoped(GameTestHelper helper) {
        var f = fixture(helper);
        var original = NpcLlmConfig.INSTANCE.current();
        var invalid = new NpcLlmConfig.Config(true,
                Map.of("mock", new NpcLlmConfig.Provider("mock", "", "", "", "low", 30, 1024, List.of())), Map.of());
        boolean rejected = false;
        try { NpcLlmConfig.INSTANCE.configure(invalid); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected && NpcLlmConfig.INSTANCE.current().equals(original),
                "Invalid LLM replacement damaged the active server configuration");

        var owner = new NpcLlmSavedData().conversation("NPC-A", f.data.getTeamId(), f.player.getUUID(), "player");
        owner.getPublished().put("intro", f.quest.getCodeString());
        var provider = new NpcLlmConfig.Provider("mock", "", "", "fixed-mock", "low", 30, 1024, List.of("get_quest"));
        var npc = new NpcLlmConfig.Npc(true, "mock", "persona", "player", 12, "", "", List.of("get_quest"),
                f.chapter.getCodeString(), "test", 16, 0);
        var lookup = new NpcLlmTransport.Tool("lookup", "get_quest",
                com.google.gson.JsonParser.parseString("{\"key\":\"intro\"}").getAsJsonObject());
        var result = NpcLlmTools.INSTANCE.execute(f.level, f.player, owner, npc, provider, lookup);
        helper.assertTrue(result.contains(f.quest.getCodeString()), "Owner could not read their published quest");

        var stranger = new NpcLlmSavedData().conversation("NPC-A", f.data.getTeamId(), UUID.randomUUID(), "player");
        rejected = false;
        try { NpcLlmTools.INSTANCE.execute(f.level, f.player, stranger, npc, provider, lookup); }
        catch (RuntimeException expected) { rejected = true; }
        helper.assertTrue(rejected, "Player read a quest through another player's conversation");

        var outsider = FakePlayerFactory.get(f.level, new GameProfile(UUID.randomUUID(), "CQLookupOutsider"));
        ((dev.ftb.mods.ftbteams.data.TeamManagerImpl) FTBTeamsAPI.api().getManager())
                .playerLoggedIn(null, outsider.getUUID(), outsider.getScoreboardName());
        rejected = false;
        try { NpcLlmTools.INSTANCE.execute(f.level, outsider, owner, npc, provider, lookup); }
        catch (RuntimeException expected) { rejected = true; }
        helper.assertTrue(rejected, "Another team read the owner's published quest");

        var missing = new NpcLlmTransport.Tool("missing", "get_quest",
                com.google.gson.JsonParser.parseString("{\"key\":\"unknown\"}").getAsJsonObject());
        rejected = false;
        try { NpcLlmTools.INSTANCE.execute(f.level, f.player, owner, npc, provider, missing); }
        catch (RuntimeException expected) { rejected = true; }
        helper.assertTrue(rejected, "Unknown conversation key returned a quest");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void llmProviderConfigRejectsInvalidSettingsAtomically(GameTestHelper helper) {
        var original = NpcLlmConfig.INSTANCE.current();
        var validProvider = new NpcLlmConfig.Provider("openai_compatible", "https://llm.example/v1", "", "model", "low", 60, 1024,
                List.of("get_quest"));
        var validNpc = new NpcLlmConfig.Npc(true, "remote", "Persona", "team", 12, "", "", List.of("get_quest"), "", "npc", 4, 0);
        try {
            NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, Map.of("remote", validProvider), Map.of("Courier", validNpc)));
            var baseline = NpcLlmConfig.INSTANCE.current();
            var badProviders = List.of(
                    new NpcLlmConfig.Provider("openai_compatible", "http://example.com/v1", "", "model", "low", 30, 1024, List.of()),
                    new NpcLlmConfig.Provider("openai_compatible", "http://user:secret@127.0.0.1/v1", "", "model", "low", 30, 1024, List.of()),
                    new NpcLlmConfig.Provider("openai_compatible", "https://llm.example/v1", "", "model", "impossible", 30, 1024, List.of()),
                    new NpcLlmConfig.Provider("openai_compatible", "https://llm.example/v1", "", "model", "low", 181, 1024, List.of()),
                    new NpcLlmConfig.Provider("openai_compatible", "https://llm.example/v1", "", "model", "low", 30, 63, List.of()),
                    new NpcLlmConfig.Provider("openai_compatible", "https://llm.example/v1", "", "model", "low", 30, 1024, List.of("delete_everything")));
            for (var bad : badProviders) {
                boolean rejected = false;
                try { NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, Map.of("remote", bad), Map.of("Courier", validNpc))); }
                catch (IllegalArgumentException expected) { rejected = true; }
                helper.assertTrue(rejected && NpcLlmConfig.INSTANCE.current().equals(baseline),
                        "Invalid provider endpoint/limits/tool set replaced the active LLM config");
            }
            var badNpcs = List.of(
                    new NpcLlmConfig.Npc(true, "remote", " ", "team", 12, "", "", List.of("get_quest"), "", "npc", 4, 0),
                    new NpcLlmConfig.Npc(true, "remote", "Persona", "shared", 12, "", "", List.of("get_quest"), "", "npc", 4, 0),
                    new NpcLlmConfig.Npc(true, "remote", "Persona", "team", 65, "", "", List.of("get_quest"), "", "npc", 4, 0),
                    new NpcLlmConfig.Npc(true, "remote", "Persona", "team", 12, "", "", List.of("publish_quest"), "", "bad prefix", 4, 0));
            for (var bad : badNpcs) {
                boolean rejected = false;
                try { NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, Map.of("remote", validProvider), Map.of("Courier", bad))); }
                catch (IllegalArgumentException expected) { rejected = true; }
                helper.assertTrue(rejected && NpcLlmConfig.INSTANCE.current().equals(baseline),
                        "Invalid NPC scope/persona/history/tool authorization replaced the active LLM config");
            }
            var loopback = new NpcLlmConfig.Provider("openai_compatible", "http://127.0.0.1:3425/v1", "", "model", "low", 180, 2048,
                    List.of("get_quest"));
            NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, Map.of("local", loopback),
                    Map.of("Courier", new NpcLlmConfig.Npc(true, "local", "Persona", "team", 12, "override", "low",
                            List.of("get_quest"), "", "npc", 4, 0))));
            helper.assertTrue(NpcLlmConfig.INSTANCE.current().getProviders().get("local").getEndpoint().equals("http://127.0.0.1:3425/v1")
                    && NpcLlmConfig.INSTANCE.current().getNpcs().get("Courier").getModel().equals("override"),
                    "Explicit loopback provider or per-NPC model override was rejected");
        } finally { NpcLlmConfig.INSTANCE.configure(original); }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void llmTaskReceiptsUseActualAcceptedAndRemainingMail(GameTestHelper helper) throws Exception {
        var f = fixture(helper); var original = NpcLlmConfig.INSTANCE.current();
        var store = NpcLlmSavedData.Companion.get(f.level); var npcName = "CQReceipt_" + f.quest.getCodeString();
        var npc = new NpcLlmConfig.Npc(true, "mock", "receipt persona", "player", 12, "", "", List.of(), "", "test", 0, 12000);
        var c = store.conversation(npcName, f.data.getTeamId(), f.player.getUUID(), "player");
        c.setLastTick(f.level.getGameTime());
        try {
            NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, Map.of("mock", new NpcLlmConfig.Provider()), Map.of(npcName, npc)));
            var graph = QuestGraphService.INSTANCE; String key = "cqtest:receipts/" + f.quest.getCodeString().toLowerCase();
            var parcelData = new CompoundTag(); parcelData.putString("item", "minecraft:diamond"); parcelData.putLong("count", 2); parcelData.putString("TargetAddressee", npcName);
            var parcelTask = graph.addTask(f.level, f.quest.getCodeString(), key + "/parcel", "contactquests:parcel_task", parcelData);
            var redData = new CompoundTag(); redData.putString("item", "minecraft:gold_ingot"); redData.putLong("count", 2); redData.putString("TargetAddressee", npcName);
            var redTask = graph.addTask(f.level, f.quest.getCodeString(), key + "/red", "contactquests:red_packet_task", redData);
            var cardData = new CompoundTag(); cardData.putString("postcard_text", "Receipt postcard"); cardData.putLong("count", 1); cardData.putString("TargetAddressee", npcName);
            var cardTask = graph.addTask(f.level, f.quest.getCodeString(), key + "/card", "contactquests:postcard_task", cardData);
            var items = new net.minecraft.world.SimpleContainer(2); items.setItem(0, new ItemStack(Items.DIAMOND, 5)); items.setItem(1, new ItemStack(Items.DIRT, 3));
            var parcel = ParcelItem.getParcel(items, false, f.player.getScoreboardName());
            helper.assertTrue(DataManager.INSTANCE.matchParcelTaskItem(f.player, parcel, npcName), "LLM parcel was not accepted");
            var first = c.getInbox().get(c.getInbox().size() - 1);
            helper.assertTrue(f.data.getProgress(parcelTask) == 2 && first.getText().startsWith("[Task submission: 2 units accepted]")
                    && first.getText().contains("minecraft:diamond x3") && first.getText().contains("minecraft:dirt x3")
                    && first.getItems().stream().filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum() == 3
                    && first.getItems().stream().filter(s -> s.is(Items.DIRT)).mapToInt(ItemStack::getCount).sum() == 3,
                    "LLM parcel facts or retained attachments do not match actual task allocation");
            var gold = new net.minecraft.world.SimpleContainer(1); gold.setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
            var red = com.creeperyang.contactquests.utils.RedPacketUtils.INSTANCE.getRedPacket(gold, "Receipt blessing", f.player.getScoreboardName());
            helper.assertTrue(DataManager.INSTANCE.matchRedPacketTaskItem(f.player, red, npcName), "LLM red packet was not accepted");
            var second = c.getInbox().get(c.getInbox().size() - 1);
            helper.assertTrue(f.data.getProgress(redTask) == 2 && second.getText().startsWith("[Task submission: 2 units accepted]")
                    && second.getText().contains("minecraft:gold_ingot x3") && second.getText().contains("Receipt blessing")
                    && ContactMailNbt.INSTANCE.readContents(second.getItems().get(0)).get(0).getCount() == 3,
                    "LLM red packet facts counted envelopes rather than accepted inner items");
            helper.assertTrue(DataManager.INSTANCE.matchPostcardTaskItem(f.player, card("Receipt postcard"), npcName), "LLM postcard was not accepted");
            var third = c.getInbox().get(c.getInbox().size() - 1);
            helper.assertTrue(f.data.getProgress(cardTask) == 1 && third.getText().equals("[Task submission: 1 units accepted]\nReceipt postcard")
                    && third.getItems().isEmpty(), "Fully consumed task postcard did not produce an exact factual LLM receipt");
            helper.assertTrue(!NpcLlmService.INSTANCE.submit(f.player, "DisabledNPC", "text") && !NpcLlmService.INSTANCE.submit(f.player, npcName, " ")
                    && !NpcLlmService.INSTANCE.submit(f.player, npcName, "x".repeat(2001)) && NpcLlmService.INSTANCE.submit(f.player, npcName, "Direct postcard submission")
                    && c.getInbox().size() == 4 && PostcardItem.getText(c.getInbox().get(c.getInbox().size() - 1).getItems().get(0)).equals("Direct postcard submission"),
                    "Text submission bypassed validation or failed to enqueue a genuine Contact card");
            helper.assertTrue(CollectionSavedData.Companion.get(f.level).getTriggerCount(f.player.getUUID(), npcName) == 0
                    && com.creeperyang.contactquests.config.NpcConfigManager.INSTANCE.getDeliveryTime(npcName, f.level) == 0,
                    "LLM task mail ran the legacy response or delay");
        } finally { c.getInbox().clear(); NpcLlmConfig.INSTANCE.configure(original); }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void llmBackpressureReturnsExactUnacceptedEnvelope(GameTestHelper helper) throws Exception {
        var f = fixture(helper); var original = NpcLlmConfig.INSTANCE.current(); var store = NpcLlmSavedData.Companion.get(f.level);
        var name = "CQCapacity_" + f.quest.getCodeString();
        var npc = new NpcLlmConfig.Npc(true, "mock", "capacity persona", "player", 12, "", "", List.of(), "", "test", 0, 12000);
        var c = store.conversation(name, f.data.getTeamId(), f.player.getUUID(), "player"); c.setLastTick(f.level.getGameTime());
        var full = store.conversation(name + "Global", UUID.randomUUID(), f.player.getUUID(), "team");
        try {
            NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, Map.of("mock", new NpcLlmConfig.Provider()), Map.of(name, npc)));
            var mail = new NpcLlmSavedData.Mail(UUID.randomUUID(), f.player.getUUID(), "fixed capacity fixture", List.of());
            for (int i = 0; i < 64; i++) c.getInbox().add(mail);
            var incoming = card("Overflow original card"); ContactMailNbt.INSTANCE.setSender(incoming, "OriginalPlayer");
            int before = bufferCount(f.player.getUUID(), incoming.getItem());
            helper.assertTrue(NpcLlmService.INSTANCE.receive(f.player, name, List.of(incoming, new ItemStack(Items.EMERALD, 4))).isEmpty()
                    && c.getInbox().size() == 64 && c.getTurns().isEmpty()
                    && bufferCount(f.player.getUUID(), incoming.getItem()) == before + 1 && bufferCount(f.player.getUUID(), Items.EMERALD) == 4,
                    "Per-conversation backpressure lost/accepted mail or invented a reaction");
            c.getInbox().clear();
            for (int i = 0; i < 2048; i++) full.getInbox().add(mail);
            NpcLlmService.INSTANCE.receive(f.player, name, List.of(incoming));
            helper.assertTrue(c.getInbox().isEmpty() && bufferCount(f.player.getUUID(), incoming.getItem()) == before + 2,
                    "Global capacity did not return exactly the unaccepted envelope");
            var queued = RewardDeliverySavedData.Companion.get(f.level).save(new CompoundTag())
                    .getList("PendingRewards", Tag.TAG_COMPOUND).stream().map(t -> (CompoundTag)t)
                    .filter(t -> t.getUUID("uuid").equals(f.player.getUUID())).toList();
            helper.assertTrue(queued.stream().filter(t -> ItemStack.of(t.getCompound("parcel")).getItem() == incoming.getItem())
                    .allMatch(t -> t.getBoolean("preserve_sender") && ItemStack.matches(ItemStack.of(t.getCompound("parcel")), incoming)),
                    "Overflow return changed original card components or sender preservation");
        } finally { c.getInbox().clear(); full.getInbox().clear(); NpcLlmConfig.INSTANCE.configure(original); }
        helper.succeed();
    }

    private static ItemStack keepCapacityStack(Item item, int count, String marker) {
        var stack = new ItemStack(item, count);
        stack.getOrCreateTag().putString("keep_capacity_marker", marker);
        stack.setHoverName(net.minecraft.network.chat.Component.literal("Keep capacity fixture"));
        return stack;
    }

    private static List<CompoundTag> keepDeliverySnapshot(Fixture f, RewardDeliverySavedData deliveries) {
        return deliveries.save(new CompoundTag()).getList("PendingRewards", Tag.TAG_COMPOUND)
                .stream().map(tag -> (CompoundTag) tag).filter(tag -> tag.getUUID("uuid").equals(f.player.getUUID()))
                .map(CompoundTag::copy).toList();
    }

    private static void processMockKeep(GameTestHelper helper, Fixture f, NpcLlmSavedData.Conversation conversation,
                                       NpcLlmConfig.Npc npc, NpcLlmConfig.Provider provider, List<ItemStack> incoming,
                                       boolean expectedCompleted) throws Exception {
        var originals = incoming.stream().map(ItemStack::copy).toList();
        // Hold the real inbox on cooldown, then complete the fixed mock synchronously:
        // other GameTests must not replace the shared NPC config while a worker is pending.
        conversation.setLastTick(f.level.getGameTime());
        helper.assertTrue(NpcLlmService.INSTANCE.receive(f.player, conversation.getNpc(), incoming).isEmpty()
                        && conversation.getInbox().size() == 1 && conversation.getPendingId() == null,
                "Keep fixture did not accept exactly one envelope into the cooldown-protected inbox");
        var mail = conversation.getInbox().get(0);
        helper.assertTrue(mail.getItems().size() == originals.size()
                        && java.util.stream.IntStream.range(0, originals.size()).allMatch(i -> ItemStack.matches(mail.getItems().get(i), originals.get(i))),
                "Keep intake changed the original counts or components");
        var response = NpcLlmTransport.INSTANCE.call(provider, new NpcLlmTransport.Input(conversation.getNpc(), npc.getPersona(),
                provider.getModel(), provider.getReasoning(), conversation.getTurns(), mail.getText(), Set.of()));
        helper.assertTrue("keep".equals(response.getMailPolicy()) && response.getTools().isEmpty(),
                "Fixed mock did not choose keep without tools");
        conversation.setPendingId(mail.getId()); conversation.setPendingPlayer(mail.getPlayerId());
        conversation.setPendingText(mail.getText());
        var complete = NpcLlmService.class.getDeclaredMethod("complete", ServerLevel.class, NpcLlmSavedData.Conversation.class,
                NpcLlmConfig.Npc.class, NpcLlmConfig.Provider.class, NpcLlmTransport.Response.class, String.class, UUID.class);
        complete.setAccessible(true);
        complete.invoke(NpcLlmService.INSTANCE, f.level, conversation, npc, provider, response,
                System.identityHashCode(f.level.getServer()) + ":" + conversation.getKey(), mail.getId());
        helper.assertTrue("keep".equals(conversation.getMailPolicy()) && conversation.getRewardItems().isEmpty()
                        && conversation.getTurns().size() == 2 && conversation.getTurns().get(0).getRole().equals("user")
                        && conversation.getTurns().get(0).getText().equals(mail.getText())
                        && conversation.getTurns().get(conversation.getTurns().size() - 1).getRole().equals("assistant")
                        && conversation.getTurns().get(conversation.getTurns().size() - 1).getText().equals(response.getText()),
                "Production completion changed the model's keep decision, duplicated history or created reward escrow");
        if (expectedCompleted) {
            helper.assertTrue(conversation.getInbox().isEmpty() && conversation.getPendingId() == null
                            && conversation.getPendingPlayer() == null && !conversation.getResolved()
                            && conversation.getReplyText().isEmpty() && conversation.getRetryAfterTick() == Long.MIN_VALUE,
                    "Production completion did not finish the accepted keep turn");
        } else {
            helper.assertTrue(conversation.getInbox().size() == 1 && conversation.getInbox().get(0).getId().equals(mail.getId())
                            && mail.getId().equals(conversation.getPendingId()) && mail.getPlayerId().equals(conversation.getPendingPlayer())
                            && conversation.getPendingText().equals(mail.getText()) && conversation.getResolved()
                            && conversation.getReplyText().equals(response.getText())
                            && conversation.getRetryAfterTick() == f.level.getGameTime() + 20
                            && conversation.getInbox().get(0).getItems().size() == originals.size()
                            && java.util.stream.IntStream.range(0, originals.size()).allMatch(i ->
                                ItemStack.matches(conversation.getInbox().get(0).getItems().get(i), originals.get(i))),
                    "Full keep must preserve the exact accepted envelope, resolved decision and reply with a 20-tick retry");
        }
        helper.assertTrue(java.util.stream.IntStream.range(0, originals.size()).allMatch(i -> ItemStack.matches(incoming.get(i), originals.get(i))),
                "Keep processing mutated caller-owned input counts or components");
    }

    @FunctionalInterface
    private interface KeepCheck { void run() throws Exception; }

    private static void withKeepState(Fixture f, NpcLlmSavedData state, NpcLlmConfig.Config config, KeepCheck check) throws Exception {
        var previousState = NpcLlmSavedData.Companion.get(f.level);
        var previousConfig = NpcLlmConfig.INSTANCE.current();
        try {
            f.level.getDataStorage().set("contactquests_npc_llm", state);
            NpcLlmConfig.INSTANCE.configure(config);
            check.run();
        } finally {
            f.level.getDataStorage().set("contactquests_npc_llm", previousState);
            NpcLlmConfig.INSTANCE.configure(previousConfig);
        }
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void llmKeepMergesOnlyMatchingComponents(GameTestHelper helper) throws Exception {
        var f = fixture(helper); var original = NpcLlmConfig.INSTANCE.current();
        var name = "CQKeepMerge_" + f.quest.getCodeString();
        var provider = new NpcLlmConfig.Provider("mock", "", "", "fixed-mock", "low", 30, 512, List.of());
        var npc = new NpcLlmConfig.Npc(true, "mock", "keep courier", "player", 12, "", "", List.of(), "", "test", 0, 12000);
        var store = NpcLlmSavedData.Companion.get(f.level);
        var c = store.conversation(name, f.data.getTeamId(), f.player.getUUID(), "player");
        var matching = keepCapacityStack(Items.DIAMOND, 60, "matching");
        var different = keepCapacityStack(Items.DIAMOND, 5, "different");
        c.getKeptItems().add(matching.copy());
        try {
            NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, Map.of("mock", provider), Map.of(name, npc)));
            processMockKeep(helper, f, c, npc, provider, List.of(matching.copyWithCount(9), different, matching.copyWithCount(2)), true);
            helper.assertTrue(c.getKeptItems().size() == 3
                            && ItemStack.matches(c.getKeptItems().get(0), matching.copyWithCount(64))
                            && ItemStack.matches(c.getKeptItems().get(1), matching.copyWithCount(7))
                            && ItemStack.matches(c.getKeptItems().get(2), different)
                            && c.getKeptItems().stream().mapToInt(ItemStack::getCount).sum() == 76,
                    "Keep must merge 60+9+2 into matching stacks 64+7 while preserving the distinct-component stack x5");
            var deliveries = RewardDeliverySavedData.Companion.get(f.level);
            var queued = keepDeliverySnapshot(f, deliveries);
            helper.assertTrue(bufferCount(f.player.getUUID(), Items.DIAMOND) == 0 && !queued.isEmpty()
                            && queued.stream().allMatch(tag -> !tag.getBoolean("preserve_sender") && tag.getString("sender").equals(name)
                                && tag.getUUID("team").equals(f.data.getTeamId())
                                && ItemStack.of(tag.getCompound("parcel")).getItem() instanceof PostcardItem),
                    "Keep returned stored diamonds or failed to queue only the actual mock reply for its owner/team");
            var completed = store.save(new CompoundTag());
            for (int i = 0; i < 3; i++) NpcLlmService.INSTANCE.tick(f.level);
            helper.assertTrue(completed.equals(store.save(new CompoundTag()))
                            && queued.equals(keepDeliverySnapshot(f, deliveries)),
                    "Subsequent NPC pumps replayed the completed keep turn or changed its exact stored items");
        } finally { c.getInbox().clear(); NpcLlmConfig.INSTANCE.configure(original); }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void llmKeepFullStorageWaitsWithoutChangingDecision(GameTestHelper helper) throws Exception {
        var f = fixture(helper);
        var name = "CQKeepFull_" + f.quest.getCodeString();
        var provider = new NpcLlmConfig.Provider("mock", "", "", "fixed-mock", "low", 30, 512, List.of());
        var npc = new NpcLlmConfig.Npc(true, "mock", "keep courier", "player", 12, "", "", List.of(), "", "test", 0, 12000);
        var config = new NpcLlmConfig.Config(true, Map.of("mock", provider), Map.of(name, npc));
        var store = new NpcLlmSavedData();
        var c = store.conversation(name, f.data.getTeamId(), f.player.getUUID(), "player");
        var matching = keepCapacityStack(Items.DIAMOND, 60, "matching");
        var different = keepCapacityStack(Items.DIAMOND, 7, "different");
        c.getKeptItems().add(matching.copy());
        for (int i = 1; i < 256; i++) c.getKeptItems().add(keepCapacityStack(Items.STONE, 1, "occupied_" + i));
        var occupied = c.getKeptItems().stream().map(ItemStack::copy).toList();
        var incomingCard = card("Keep capacity original\nPreserve this letter");
        ContactMailNbt.INSTANCE.setSender(incomingCard, "OriginalCorrespondent");
        incomingCard.setHoverName(net.minecraft.network.chat.Component.literal("Original keep letter"));
        incomingCard.getOrCreateTag().putString("unrelated", "retain original card NBT");
        withKeepState(f, store, config, () -> {
            processMockKeep(helper, f, c, npc, provider, List.of(matching.copyWithCount(9), different, incomingCard), false);
            helper.assertTrue(c.getKeptItems().size() == 256
                            && java.util.stream.IntStream.range(0, 256).allMatch(i -> ItemStack.matches(c.getKeptItems().get(i), occupied.get(i)))
                            && c.getKeptItems().stream().mapToInt(ItemStack::getCount).sum()
                                + c.getInbox().get(0).getItems().stream().mapToInt(ItemStack::getCount).sum() == 332,
                    "An envelope that cannot fit completely must not even merge its four fitting diamonds into existing storage");
            var deliveries = RewardDeliverySavedData.Companion.get(f.level);
            helper.assertTrue(keepDeliverySnapshot(f, deliveries).isEmpty()
                            && bufferCount(f.player.getUUID(), Items.DIAMOND) == 0 && bufferCount(f.player.getUUID(), incomingCard.getItem()) == 0,
                    "Capacity changed AI keep into return or sent an NPC reply before accepting the whole envelope");
            var waiting = store.save(new CompoundTag());
            var restored = ContactQuestsTestUtil.roundTrip(store);
            helper.assertTrue(waiting.equals(restored.save(new CompoundTag())),
                    "Reload changed waiting keep items, pending identity, resolved policy, reply, history or retry deadline");
            var resumed = restored.conversation(name, f.data.getTeamId(), f.player.getUUID(), "player");
            long due = resumed.getRetryAfterTick();
            var history = List.copyOf(resumed.getTurns());
            String reply = resumed.getReplyText();
            var released = new ArrayList<ItemStack>();
            // The remaining matching stack, different-component stack and original card each need one slot.
            for (int i = 0; i < 3; i++) released.add(resumed.getKeptItems().remove(resumed.getKeptItems().size() - 1));
            restored.setDirty();
            var beforeDue = restored.save(new CompoundTag());
            helper.runAfterDelay(19, () -> {
                try {
                    withKeepState(f, restored, config, () -> {
                        helper.assertTrue(f.level.getGameTime() == due - 1, "Pre-retry observation did not occur on the nineteenth real tick");
                        NpcLlmService.INSTANCE.tick(f.level);
                        helper.assertTrue(beforeDue.equals(restored.save(new CompoundTag()))
                                        && keepDeliverySnapshot(f, RewardDeliverySavedData.Companion.get(f.level)).isEmpty()
                                        && bufferCount(f.player.getUUID(), Items.DIAMOND) == 0
                                        && bufferCount(f.player.getUUID(), incomingCard.getItem()) == 0,
                                "Restored keep committed, returned mail, replied or changed history before its 20-tick retry");
                    });
                    helper.runAfterDelay(1, () -> {
                        try {
                            withKeepState(f, restored, config, () -> {
                                helper.assertTrue(f.level.getGameTime() == due, "Keep completion did not occur on the twentieth real tick");
                                NpcLlmService.INSTANCE.tick(f.level);
                                helper.assertTrue(resumed.getInbox().isEmpty() && resumed.getPendingId() == null
                                                && resumed.getPendingPlayer() == null && !resumed.getResolved()
                                                && resumed.getReplyText().isEmpty() && resumed.getPendingText().isEmpty()
                                                && resumed.getRetryAfterTick() == Long.MIN_VALUE && "keep".equals(resumed.getMailPolicy())
                                                && resumed.getTurns().equals(history) && resumed.getRewardItems().isEmpty(),
                                        "Real pump did not finish the restored keep decision exactly once with its original history");
                                helper.assertTrue(resumed.getKeptItems().size() == 256
                                                && ItemStack.matches(resumed.getKeptItems().get(0), matching.copyWithCount(64))
                                                && java.util.stream.IntStream.range(1, 253).allMatch(i -> ItemStack.matches(resumed.getKeptItems().get(i), occupied.get(i)))
                                                && ItemStack.matches(resumed.getKeptItems().get(253), matching.copyWithCount(5))
                                                && ItemStack.matches(resumed.getKeptItems().get(254), different)
                                                && ItemStack.matches(resumed.getKeptItems().get(255), incomingCard)
                                                && java.util.stream.IntStream.range(0, 3).allMatch(i -> ItemStack.matches(released.get(i), occupied.get(255 - i)))
                                                && resumed.getKeptItems().stream().mapToInt(ItemStack::getCount).sum() == 329
                                                && released.stream().mapToInt(ItemStack::getCount).sum() == 3,
                                        "Whole-envelope keep must conserve 332 units: stored diamonds 64+5+7, 252 unchanged stones, original card and three released stones");
                                var queued = keepDeliverySnapshot(f, RewardDeliverySavedData.Companion.get(f.level));
                                helper.assertTrue(!queued.isEmpty() && queued.stream().allMatch(tag -> !tag.getBoolean("preserve_sender")
                                                && tag.getString("sender").equals(name) && tag.getUUID("team").equals(f.data.getTeamId()))
                                                && bufferCount(f.player.getUUID(), Items.DIAMOND) == 0 && bufferCount(f.player.getUUID(), Items.STONE) == 0,
                                        "Resolved keep returned original items instead of queuing only its original NPC reply");
                                var cards = queued.stream().map(tag -> ItemStack.of(tag.getCompound("parcel"))).toList();
                                var group = NpcPostcardLetters.group(cards.get(0));
                                helper.assertTrue(group != null && java.util.stream.IntStream.range(0, cards.size()).allMatch(i ->
                                                cards.get(i).getItem() instanceof PostcardItem && cards.get(i).getCount() == 1
                                                && group.equals(NpcPostcardLetters.group(cards.get(i))) && NpcPostcardLetters.index(cards.get(i)) == i
                                                && NpcPostcardLetters.count(cards.get(i)) == cards.size()
                                                && name.equals(ContactMailNbt.INSTANCE.getSender(cards.get(i))))
                                                && cards.stream().map(PostcardItem::getText).collect(java.util.stream.Collectors.joining()).equals(reply),
                                        "Retry must send one complete fragment group containing exactly the saved original reply");
                                var completed = restored.save(new CompoundTag());
                                // Delivery countdowns naturally change while the isolated NPC state is detached.
                                queued.forEach(tag -> tag.remove("ticks"));
                                helper.runAfterDelay(21, () -> {
                                    try {
                                        withKeepState(f, restored, config, () -> {
                                            for (int i = 0; i < 3; i++) NpcLlmService.INSTANCE.tick(f.level);
                                            var after = keepDeliverySnapshot(f, RewardDeliverySavedData.Companion.get(f.level));
                                            after.forEach(tag -> tag.remove("ticks"));
                                            helper.assertTrue(f.level.getGameTime() > due + 20
                                                            && completed.equals(restored.save(new CompoundTag())) && queued.equals(after),
                                                    "Later real-time pumps replayed the keep merge, model history, original mail return or saved reply");
                                            helper.succeed();
                                        });
                                    } catch (Exception error) { helper.fail("Keep no-replay check failed: " + error); }
                                });
                            });
                        } catch (Exception error) { helper.fail("Keep retry completion failed: " + error); }
                    });
                } catch (Exception error) { helper.fail("Keep pre-retry check failed: " + error); }
            });
        });
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void llmWithRewardsEscrowReturnsOnMatchingNativeClaimOnce(GameTestHelper helper) throws Exception {
        var f = fixture(helper); var original = NpcLlmConfig.INSTANCE.current();
        var name = "CQWithRewards_" + f.quest.getCodeString();
        var provider = new NpcLlmConfig.Provider("mock", "", "", "fixed-mock", "low", 30, 512, List.of());
        var npc = new NpcLlmConfig.Npc(true, "mock", "escrow courier", "player", 12, "", "", List.of(), "", "test", 0, 12000);
        try {
            NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, Map.of("mock", provider), Map.of(name, npc)));
            var c = NpcLlmSavedData.Companion.get(f.level).conversation(name, f.data.getTeamId(), f.player.getUUID(), "player");
            var realCard = PostcardItem.setText(PostcardItem.getPostcard(new ResourceLocation("contact:creeper"), false), "CQ_MOCK_WITH_REWARDS");
            var incoming = new NpcLlmSavedData.Mail(UUID.randomUUID(), f.player.getUUID(), "CQ_MOCK_WITH_REWARDS", List.of(realCard));
            c.getInbox().add(incoming);
            var response = NpcLlmTransport.INSTANCE.call(provider, new NpcLlmTransport.Input(name, npc.getPersona(), "fixed-mock", "low",
                    List.of(), incoming.getText(), Set.of()));
            c.setPendingId(incoming.getId()); c.setPendingPlayer(f.player.getUUID());
            c.getTurns().add(new NpcLlmSavedData.Turn("user", incoming.getText()));
            c.getTurns().add(new NpcLlmSavedData.Turn("assistant", response.getText()));
            c.setMailPolicy(response.getMailPolicy()); c.setResolved(true);
            var deliver = NpcLlmService.class.getDeclaredMethod("deliver", net.minecraft.server.level.ServerLevel.class,
                    NpcLlmSavedData.Conversation.class, NpcLlmSavedData.class);
            deliver.setAccessible(true); deliver.invoke(NpcLlmService.INSTANCE, f.level, c, NpcLlmSavedData.Companion.get(f.level));
            helper.assertTrue(c.getRewardItems().size() == 1 && c.getKeptItems().isEmpty()
                    && bufferCount(f.player.getUUID(), realCard.getItem()) == 0, "AI-selected with_rewards did not stay escrowed until claim");
            var persisted = ContactQuestsTestUtil.roundTrip(NpcLlmSavedData.Companion.get(f.level));
            var restoredConversation = persisted.conversation(name, f.data.getTeamId(), f.player.getUUID(), "player");
            helper.assertTrue(restoredConversation.getRewardItems().size() == 1
                    && ItemStack.matches(restoredConversation.getRewardItems().get(0).getItems().get(0), realCard),
                    "NPC reward escrow did not preserve the exact original Contact card through a save/reload");
            var reward = new ParcelReward(ServerQuestFile.INSTANCE.newID(), f.quest); reward.setTargetAddressee(name);
            reward.setItem(new ItemStack(Items.EMERALD)); reward.setCount(4); f.quest.addReward(reward);
            f.data.setCompleted(f.quest.getId(), new Date()); ContactQuestsTestUtil.refreshIndex(ServerQuestFile.INSTANCE);
            f.data.claimReward(f.player, reward, false);
            f.data.claimReward(f.player, reward, false);
            helper.assertTrue(bufferCount(f.player.getUUID(), Items.EMERALD) == 4 && bufferCount(f.player.getUUID(), realCard.getItem()) == 1
                    && c.getRewardItems().isEmpty(), "Claim did not return exact escrow and reward once");
            var other = NpcLlmSavedData.Companion.get(f.level).conversation(name, UUID.randomUUID(), f.player.getUUID(), "team");
            other.getRewardItems().add(new NpcLlmSavedData.RewardEscrow(f.player.getUUID(), new ArrayList<>(List.of(new ItemStack(Items.GOLD_INGOT, 2)))));
            var otherTeam = fixture(helper);
            var outsiderConversation = NpcLlmSavedData.Companion.get(f.level).conversation(name, otherTeam.data.getTeamId(), otherTeam.player.getUUID(), "team");
            outsiderConversation.getRewardItems().add(new NpcLlmSavedData.RewardEscrow(otherTeam.player.getUUID(), new ArrayList<>(List.of(new ItemStack(Items.GOLD_INGOT, 2)))));
            var unrelated = new ParcelReward(ServerQuestFile.INSTANCE.newID(), otherTeam.quest); unrelated.setTargetAddressee(name);
            unrelated.setItem(new ItemStack(Items.IRON_INGOT)); unrelated.setCount(1); otherTeam.quest.addReward(unrelated);
            otherTeam.data.setCompleted(otherTeam.quest.getId(), new Date()); otherTeam.data.claimReward(otherTeam.player, unrelated, false);
            helper.assertTrue(other.getRewardItems().size() == 1 && bufferCount(otherTeam.player.getUUID(), Items.GOLD_INGOT) == 2
                    && outsiderConversation.getRewardItems().isEmpty() && bufferCount(f.player.getUUID(), Items.GOLD_INGOT) == 0,
                    "Another team claim did not return only its own player-bound NPC escrow");
        } finally { NpcLlmConfig.INSTANCE.configure(original); }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void llmEscrowReturnsOnceForEveryContactReward(GameTestHelper helper) throws Exception {
        var original = NpcLlmConfig.INSTANCE.current();
        try {
            for (int kind = 0; kind < 6; kind++) {
                var f = fixture(helper); var file = ServerQuestFile.INSTANCE;
                var name = "CQEscrowType_" + f.quest.getCodeString();
                var provider = new NpcLlmConfig.Provider("mock", "", "", "fixed", "low", 30, 512, List.of());
                var npc = new NpcLlmConfig.Npc(true, "mock", "courier", "team", 12, "", "", List.of(), "", "test", 0, 12000);
                NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, Map.of("mock", provider), Map.of(name, npc)));
                var c = NpcLlmSavedData.Companion.get(f.level).conversation(name, f.data.getTeamId(), f.player.getUUID(), "team");
                c.getRewardItems().add(new NpcLlmSavedData.RewardEscrow(f.player.getUUID(), new ArrayList<>(List.of(new ItemStack(Items.GOLD_INGOT, 5)))));
                var otherPlayer = UUID.randomUUID();
                c.getRewardItems().add(new NpcLlmSavedData.RewardEscrow(otherPlayer, new ArrayList<>(List.of(new ItemStack(Items.GOLD_INGOT, 7)))));
                var foreign = NpcLlmSavedData.Companion.get(f.level).conversation(name + "Other", f.data.getTeamId(), f.player.getUUID(), "team");
                foreign.getRewardItems().add(new NpcLlmSavedData.RewardEscrow(f.player.getUUID(), new ArrayList<>(List.of(new ItemStack(Items.GOLD_INGOT, 9)))));
                ParcelRewardBase reward;
                if (kind == 0) {
                    var parcel = new ParcelReward(file.newID(), f.quest); parcel.setItem(new ItemStack(Items.APPLE)); parcel.setCount(3); reward = parcel;
                } else if (kind == 1) {
                    var red = new RedPacketReward(file.newID(), f.quest); red.setItem(new ItemStack(Items.APPLE)); red.setCount(3); reward = red;
                } else if (kind == 2) {
                    var postcard = new PostcardReward(file.newID(), f.quest); postcard.setPostcardText("Escrow reward"); reward = postcard;
                } else if (kind == 5) {
                    reward = choice(f);
                } else {
                    var table = new RewardTable(file.newID(), file);
                    table.addReward(new WeightedReward(new ItemReward(file.newID(), table.getFakeQuest(), new ItemStack(Items.APPLE), 3), 1));
                    file.addRewardTable(table);
                    var tableReward = kind == 3 ? new ParcelRandomReward(file.newID(), f.quest) : new ParcelAllTableReward(file.newID(), f.quest);
                    tableReward.setTable(table); reward = tableReward;
                }
                reward.setTargetAddressee(name);
                if (kind != 5) { f.quest.addTask(new CheckmarkTask(file.newID(), f.quest)); f.quest.addReward(reward); }
                ContactQuestsTestUtil.refreshIndex(file); f.data.setCompleted(f.quest.getId(), new Date());
                helper.assertTrue(bufferCount(f.player.getUUID(), Items.GOLD_INGOT) == 0 && c.getRewardItems().size() == 2,
                        "Escrow returned before reward type " + kind + " was claimed");
                if (reward instanceof ParcelChoiceReward selected) {
                    var store = ChoiceRewardSavedData.Companion.get(f.level); var request = store.request(f.player, selected, 0);
                    selected.claim(f.player, false);
                    helper.assertTrue(!f.data.isRewardClaimed(f.player.getUUID(), selected)
                            && bufferCount(f.player.getUUID(), Items.GOLD_INGOT) == 0,
                            "Choice bypass returned escrow before a real selected postcard");
                    store.receive(f.player, selected.getClaimRecipient(), List.of(card(request)), 0);
                    store.receive(f.player, selected.getClaimRecipient(), List.of(card(request)), 0);
                } else {
                    f.data.claimReward(f.player, reward, false); f.data.claimReward(f.player, reward, false);
                }
                helper.assertTrue(f.data.isRewardClaimed(f.player.getUUID(), reward) && bufferCount(f.player.getUUID(), Items.GOLD_INGOT) == 5
                        && c.getRewardItems().size() == 1 && c.getRewardItems().get(0).getPlayerId().equals(otherPlayer)
                        && foreign.getRewardItems().size() == 1, "Reward type " + kind + " duplicated/crossed NPC or player escrow");
                if (kind != 2) helper.assertTrue(bufferCount(f.player.getUUID(), Items.APPLE) == 3,
                        "Reward type " + kind + " changed reward contents while returning escrow");
                else helper.assertTrue(bufferCount(f.player.getUUID(), card("").getItem()) == 1, "Postcard reward was lost during escrow return");
            }
        } finally { NpcLlmConfig.INSTANCE.configure(original); }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void tableDelegatedRewardReturnsOuterNpcEscrowOnce(GameTestHelper helper) throws Exception {
        var f = fixture(helper); var file = ServerQuestFile.INSTANCE; var original = NpcLlmConfig.INSTANCE.current();
        var name = "CQDelegated_" + f.quest.getCodeString();
        try {
            var npc = new NpcLlmConfig.Npc(true, "mock", "courier", "team", 12, "", "", List.of(), "", "test", 0, 12000);
            NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, Map.of("mock", new NpcLlmConfig.Provider()), Map.of(name, npc)));
            var c = NpcLlmSavedData.Companion.get(f.level).conversation(name, f.data.getTeamId(), f.player.getUUID(), "team");
            c.getRewardItems().add(new NpcLlmSavedData.RewardEscrow(f.player.getUUID(), new ArrayList<>(List.of(new ItemStack(Items.GOLD_INGOT, 5)))));
            var table = new RewardTable(file.newID(), file);
            var child = new PostcardReward(file.newID(), table.getFakeQuest()); child.setTargetAddressee("DifferentInnerNpc"); child.setPostcardText("Delegated card");
            table.addReward(new WeightedReward(child, 1)); file.addRewardTable(table);
            var outer = new ParcelRandomReward(file.newID(), f.quest); outer.setTargetAddressee(name); outer.setTable(table);
            f.quest.addTask(new CheckmarkTask(file.newID(), f.quest)); f.quest.addReward(outer);
            ContactQuestsTestUtil.refreshIndex(file); f.data.setCompleted(f.quest.getId(), new Date());
            f.data.claimReward(f.player, outer, false); f.data.claimReward(f.player, outer, false);
            helper.assertTrue(f.data.isRewardClaimed(f.player.getUUID(), outer) && c.getRewardItems().isEmpty()
                    && bufferCount(f.player.getUUID(), Items.GOLD_INGOT) == 5 && bufferCount(f.player.getUUID(), card("").getItem()) == 1,
                    "Delegated reward did not return its outer NPC escrow exactly once with the real child reward");
        } finally { NpcLlmConfig.INSTANCE.configure(original); }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void fullWithRewardStorageWaitsForClaimWithoutEarlyReturn(GameTestHelper helper) throws Exception {
        var f = fixture(helper); var original = NpcLlmConfig.INSTANCE.current(); var file = ServerQuestFile.INSTANCE;
        var name = "CQFullEscrow_" + f.quest.getCodeString();
        try {
            var npc = new NpcLlmConfig.Npc(true, "mock", "courier", "team", 12, "", "", List.of(), "", "test", 0, 12000);
            NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, Map.of("mock", new NpcLlmConfig.Provider()), Map.of(name, npc)));
            var store = NpcLlmSavedData.Companion.get(f.level);
            var c = store.conversation(name, f.data.getTeamId(), f.player.getUUID(), "team");
            var full = new ArrayList<ItemStack>();
            for (int i = 0; i < 256; i++) full.add(new ItemStack(Items.GOLD_INGOT));
            c.getRewardItems().add(new NpcLlmSavedData.RewardEscrow(f.player.getUUID(), full));
            var incoming = new NpcLlmSavedData.Mail(UUID.randomUUID(), f.player.getUUID(), "Capacity request",
                    List.of(card("Capacity request"), new ItemStack(Items.EMERALD, 4)));
            c.getInbox().add(incoming); c.setPendingId(incoming.getId()); c.setPendingPlayer(f.player.getUUID());
            c.setPendingText(incoming.getText()); c.setReplyText("Wait for a reward"); c.setMailPolicy("withReward"); c.setResolved(true);
            var deliver = NpcLlmService.class.getDeclaredMethod("deliver", ServerLevel.class, NpcLlmSavedData.Conversation.class, NpcLlmSavedData.class);
            deliver.setAccessible(true);
            deliver.invoke(NpcLlmService.INSTANCE, f.level, c, store); deliver.invoke(NpcLlmService.INSTANCE, f.level, c, store);
            helper.assertTrue(c.getPendingId().equals(incoming.getId()) && c.getResolved() && c.getInbox().size() == 1
                    && c.getRewardItems().get(0).getItems().size() == 256 && c.getReplyText().equals("Wait for a reward")
                    && bufferCount(f.player.getUUID(), Items.EMERALD) == 0 && bufferCount(f.player.getUUID(), card("").getItem()) == 0
                    && bufferCount(f.player.getUUID(), Items.GOLD_INGOT) == 0,
                    "Full withReward storage returned accepted mail or a reply before capacity was released by claim");
            var persisted = ContactQuestsTestUtil.roundTrip(store)
                    .conversation(name, f.data.getTeamId(), f.player.getUUID(), "team");
            helper.assertTrue(persisted.getPendingId().equals(incoming.getId()) && persisted.getResolved()
                    && ItemStack.matches(persisted.getInbox().get(0).getItems().get(0), incoming.getItems().get(0)),
                    "Capacity-waiting withReward decision lost exact mail across save/reload");
            f.quest.addTask(new CheckmarkTask(file.newID(), f.quest));
            var first = new ParcelReward(file.newID(), f.quest); first.setTargetAddressee(name); first.setItem(new ItemStack(Items.APPLE));
            f.quest.addReward(first); ContactQuestsTestUtil.refreshIndex(file); f.data.setCompleted(f.quest.getId(), new Date());
            f.data.claimReward(f.player, first, false); f.data.claimReward(f.player, first, false);
            helper.assertTrue(c.getRewardItems().isEmpty() && bufferCount(f.player.getUUID(), Items.GOLD_INGOT) == 256
                    && bufferCount(f.player.getUUID(), Items.EMERALD) == 0, "Native claim did not free only the previously escrowed items once");
            deliver.invoke(NpcLlmService.INSTANCE, f.level, c, store); deliver.invoke(NpcLlmService.INSTANCE, f.level, c, store);
            helper.assertTrue(c.getPendingId() == null && c.getInbox().isEmpty() && c.getRewardItems().size() == 1
                    && c.getRewardItems().get(0).getItems().size() == 2
                    && bufferCount(f.player.getUUID(), Items.EMERALD) == 0 && bufferCount(f.player.getUUID(), card("").getItem()) == 1,
                    "Resolved waiting mail did not resume once or returned its new escrow before another claim");
            var second = new ParcelReward(file.newID(), f.quest); second.setTargetAddressee(name); second.setItem(new ItemStack(Items.APPLE));
            f.quest.addReward(second); ContactQuestsTestUtil.refreshIndex(file);
            f.data.claimReward(f.player, second, false); f.data.claimReward(f.player, second, false);
            helper.assertTrue(c.getRewardItems().isEmpty() && bufferCount(f.player.getUUID(), Items.EMERALD) == 4
                    && bufferCount(f.player.getUUID(), card("").getItem()) == 2 && bufferCount(f.player.getUUID(), Items.GOLD_INGOT) == 256,
                    "Newly escrowed mail was not returned exactly once on its later native reward claim");
        } finally { NpcLlmConfig.INSTANCE.configure(original); }
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void llmWithRewardsEscrowSurvivesSavedDataRoundTrip(GameTestHelper helper) throws Exception {
        var f = fixture(helper);
        var npc = "CQEscrowPersistence_" + f.quest.getCodeString();
        var firstTeamPlayer = UUID.randomUUID(); var secondTeamPlayer = UUID.randomUUID();
        var store = NpcLlmSavedData.Companion.get(f.level);
        var c = store.conversation(npc, f.data.getTeamId(), f.player.getUUID(), "team");
        c.getRewardItems().add(new NpcLlmSavedData.RewardEscrow(firstTeamPlayer,
                new ArrayList<>(List.of(new ItemStack(Items.DIAMOND, 3)))));
        c.getRewardItems().add(new NpcLlmSavedData.RewardEscrow(secondTeamPlayer,
                new ArrayList<>(List.of(new ItemStack(Items.EMERALD, 5)))));
        var saved = ContactQuestsTestUtil.roundTrip(store);
        var loaded = saved.conversation(npc, f.data.getTeamId(), f.player.getUUID(), "team");
        helper.assertTrue(loaded.getRewardItems().size() == 2
                && loaded.getRewardItems().get(0).getPlayerId().equals(firstTeamPlayer)
                && loaded.getRewardItems().get(0).getItems().get(0).is(Items.DIAMOND)
                && loaded.getRewardItems().get(0).getItems().get(0).getCount() == 3
                && loaded.getRewardItems().get(1).getPlayerId().equals(secondTeamPlayer)
                && loaded.getRewardItems().get(1).getItems().get(0).is(Items.EMERALD)
                && loaded.getRewardItems().get(1).getItems().get(0).getCount() == 5,
                "Saved NPC escrow merged separate players or changed real Contact item stacks");
        helper.succeed();
    }

    @GameTest(template = "contactquestsgametests.empty", timeoutTicks = 200)
    public static void graphRejectedUpdatesLeaveNativeStateUnchanged(GameTestHelper helper) {
        var f = fixture(helper); var graph = QuestGraphService.INSTANCE;
        String prefix = "cqtest:atomic/" + f.quest.getCodeString().toLowerCase();
        var other = graph.createQuest(f.level, f.chapter.getCodeString(), prefix + "/other", "Dependency");
        graph.updateQuest(f.level, other.getCodeString(), Map.of("dependencies", List.of(f.quest.getCodeString())));
        var before = new CompoundTag(); f.quest.writeData(before);
        for (var invalid : List.<Map<String, Object>>of(
                Map.of("dependencies", List.of(f.quest.getCodeString())), Map.of("dependencies", List.of(other.getCodeString())),
                Map.of("dependencies", List.of("0000000000000001")), Map.of("shape", "missing-shape"),
                Map.of("x", Double.NaN), Map.of("iconScale", 0), Map.of("requiredTags", List.of("ok", 2)),
                Map.of("dependencyControlPoints", Map.of(other.getCodeString(), List.of(List.of(1, 2), List.of(3, 4)))))) {
            boolean rejected = false;
            try { graph.updateQuest(f.level, f.quest.getCodeString(), invalid); } catch (RuntimeException expected) { rejected = true; }
            var after = new CompoundTag(); f.quest.writeData(after);
            helper.assertTrue(rejected && before.equals(after), "Rejected dependency/display update changed native quest state: " + invalid);
        }
        var chapterBefore = new CompoundTag(); f.chapter.writeData(chapterBefore);
        var foreign = graph.createChapter(f.level, prefix + "/foreign", "Foreign chapter");
        var foreignQuest = graph.createQuest(f.level, foreign.getCodeString(), prefix + "/foreign_quest", "Foreign quest");
        boolean rejected = false;
        try { graph.updateChapter(f.level, f.chapter.getCodeString(), Map.of("autofocusId", foreignQuest.getCodeString(), "title", "Must not change")); }
        catch (RuntimeException expected) { rejected = true; }
        var chapterAfter = new CompoundTag(); f.chapter.writeData(chapterAfter);
        helper.assertTrue(rejected && chapterBefore.equals(chapterAfter), "Rejected chapter update changed title/native state");
        var location = graph.addTask(f.level, f.quest.getCodeString(), prefix + "/location", "ftbquests:location",
                QuestGraphSettings.INSTANCE.creationData(Map.of("position", List.of(-10, 64, 20), "size", List.of(2, 3, 4), "dimension", "minecraft:overworld")));
        var saved = new CompoundTag(); location.writeData(saved);
        helper.assertTrue(Arrays.equals(saved.getIntArray("position"), new int[]{-10,64,20}) && Arrays.equals(saved.getIntArray("size"), new int[]{2,3,4}),
                "Validated native location coordinates did not reach real task NBT");
        for (var invalid : List.of(Map.of("position", List.of(1.5, 2, 3)), Map.of("size", List.of(-1, 2, 3)), Map.of("dimension", "missing:dimension"))) {
            rejected = false;
            try { graph.updateTask(f.level, location.getCodeString(), QuestGraphSettings.INSTANCE.creationData(invalid)); }
            catch (RuntimeException expected) { rejected = true; }
            var after = new CompoundTag(); location.writeData(after);
            helper.assertTrue(rejected && saved.equals(after), "Invalid native location update left a partial change");
        }
        helper.succeed();
    }
}
