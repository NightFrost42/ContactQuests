package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.config.NpcConfigManager;
import com.creeperyang.contactquests.data.*;
import com.creeperyang.contactquests.llm.*;
import com.creeperyang.contactquests.quest.*;
import com.creeperyang.contactquests.quest.reward.ParcelReward;
import com.creeperyang.contactquests.quest.task.ParcelTask;
import com.creeperyang.contactquests.utils.ContactMailNbt;
import com.flechazo.contact.common.item.*;
import com.flechazo.contact.platform.PlatformHelper;
import com.google.gson.*;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;

import java.nio.file.*;
import java.util.*;

/** Opt-in real provider acceptance. No assertion is decided by interpreting model prose. */
public final class ContactQuestsLiveLlmFixtures {
    public static final String NPC = "CQLiveMagpie";
    private static final String PERSONA = """
            You are a precise Minecraft quest courier. This is a protocol acceptance conversation.
            Every final response must be a JSON object with exactly two string fields: reply and mail_policy.
            Write the player-facing reply as a Contact postcard. mail_policy is mandatory: keep, return, withReward, or discard;
            this field is your decision about the physical incoming items. Never omit it or return plain prose.
            Always respond using this JSON contract and the authorized tools.
            For a user message containing CQ_LIVE_PUBLISH, call publish_quest exactly once, key intro,
            options {"title":"CQ Magpie quest","x":6,"y":7},
            tasks [{"type":"contactquests:parcel_task","data":{"item":"minecraft:diamond","count":2}}],
            rewards [{"type":"contactquests:parcel","data":{"item":"minecraft:apple","count":3}}].
            After the tool result, reply exactly CQ_LIVE_PUBLISHED:<id>, substituting the real returned quest ID,
            and mail_policy withReward. Store this envelope until the just-published quest reward is claimed; return the stored attachments in the same Contact reward delivery.
            Remember the SECRET= value from that user message privately in context.
            For CQ_LIVE_ESCROW_RETURN, call get_quest with key intro once, then reply exactly
            CQ_LIVE_RECALLED:<id>:<secret> using the real quest ID and the earlier SECRET= value; mail_policy return.
            For CQ_LIVE_RESTART, call get_quest with key intro once, then reply exactly
            CQ_LIVE_RESTARTED:<id>:<secret> from the persisted conversation; mail_policy discard.
            Never fabricate an ID or a tool result. No prose, spaces, code fences or extra fields in the reaction.
            """;
    private static NpcLlmConfig.Config original;
    private static JsonObject diskConfig;
    private static SimpleContainer previousMailbox;
    private static UUID team, playerId;
    private static String secret, published, waiting;
    private static String escrowReturnText;
    private static long deadline, firstPid;
    private static int legacyCallbacks;
    private static boolean restored;

    public static boolean enabled() { return Boolean.getBoolean("contactquests.test.realLlm"); }
    public static String secretForUi() { return secret == null ? "" : secret; }
    private static Path path() { return Path.of(System.getProperty("contactquests.test.snapshot") + ".live-llm.json"); }
    private static Path configPath() { return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("contactquests/npc_llm.json"); }
    public static void recordLegacy(String recipient) { if (NPC.equals(recipient)) legacyCallbacks++; }
    public static void addResultFields(JsonObject out) {
        out.addProperty("realLlmEnabled", enabled());
        if (enabled()) {
            out.addProperty("realLlmEndpoint", System.getProperty("contactquests.test.llmEndpoint"));
            out.addProperty("realLlmModel", System.getProperty("contactquests.test.llmModel"));
            if (published != null) out.addProperty("realLlmQuest", published);
        }
    }
    public static void run(ServerPlayer player, String phase) {
        if (!enabled()) return;
        try {
            switch (phase) {
                case "prepare" -> prepare(player);
                case "publish", "return", "restart" -> seed(player, phase);
                case "collect" -> collect(player);
                case "finish" -> finish(player);
                case "rejoin" -> rejoin(player);
                default -> throw new IllegalArgumentException("Unknown real-provider phase " + phase);
            }
        } catch (Exception error) { fail(player, phase, error); }
    }
    private static void prepare(ServerPlayer player) throws Exception {
        original = NpcLlmConfig.INSTANCE.current();
        diskConfig = JsonParser.parseString(Files.readString(configPath())).getAsJsonObject();
        var sourceTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
        team = sourceTeam.getId(); playerId = player.getUUID(); firstPid = ProcessHandle.current().pid();
        secret = UUID.randomUUID().toString().replace("-", "");
        require(PlatformHelper.getMailboxPos(team) != null, "Real-provider test requires the existing real bound mailbox");
        previousMailbox = copy(PlatformHelper.getMailboxContents(team)); PlatformHelper.resetMailboxContents(team);
        var graph = QuestGraphService.INSTANCE;
        var chapter = graph.createChapter(player.serverLevel(), "cqtest:live_llm/chapter", "Live provider acceptance");
        var providers = new LinkedHashMap<>(original.getProviders());
        providers.put("live", new NpcLlmConfig.Provider("openai_compatible", System.getProperty("contactquests.test.llmEndpoint"), "",
                "unused-provider-default", "default", 180, 2048, List.of("publish_quest", "get_quest")));
        var npcs = new LinkedHashMap<String, NpcLlmConfig.Npc>();
        // The old fixture deliberately left a mock journal for the next JVM. Keep it paused during this acceptance.
        original.getNpcs().forEach((name, npc) -> npcs.put(name, new NpcLlmConfig.Npc(false, npc.getProvider(), npc.getPersona(), npc.getScope(),
                npc.getHistoryTurns(), npc.getModel(), npc.getReasoning(), npc.getTools(), npc.getChapterId(), npc.getQuestPrefix(), npc.getMaxPublishedQuests(), npc.getCooldownTicks())));
        npcs.put(NPC, new NpcLlmConfig.Npc(true, "live", PERSONA, "team", 12,
                System.getProperty("contactquests.test.llmModel"), "low", List.of("publish_quest", "get_quest"), chapter.getCodeString(), "live", 1, 0, true));
        NpcLlmConfig.INSTANCE.configure(new NpcLlmConfig.Config(true, providers, npcs));
        require(NpcConfigManager.INSTANCE.getDeliveryTime(NPC, player.serverLevel()) == 0 && NpcConfigManager.INSTANCE.getErrorSolve(NPC, 999) == null,
                "Real LLM NPC did not suppress old NPC delay/reaction configuration");
        restored = false; legacyCallbacks = 0;
        record("prepare", "Real Magpie transport configured in an isolated world with per-NPC model/thinking overrides, scoped tools and original mailbox preserved");
    }
    private static void seed(ServerPlayer player, String phase) {
        require(waiting == null, "Previous real provider request has not completed");
        String text = switch (phase) {
            case "publish" -> "CQ_LIVE_PUBLISH SECRET=" + secret;
            case "return" -> "CQ_LIVE_ESCROW_RETURN";
            default -> "CQ_LIVE_RESTART";
        };
        var card = PostcardItem.getPostcard(new ResourceLocation("contact:creeper"), false);
        PostcardItem.setText(card, text);
        ContactMailNbt.INSTANCE.setSender(card, player.getScoreboardName());
        ItemStack mail = card;
        if (phase.equals("publish")) {
            var contents = new SimpleContainer(2); contents.setItem(0, card); contents.setItem(1, new ItemStack(Items.EMERALD, 4));
            mail = ParcelItem.getParcel(contents, false, player.getScoreboardName());
        }
        player.getInventory().clearContent();
        player.getInventory().setItem(0, mail); player.getInventory().selected = 0;
        // The command is normally sent while the real Contact postbox is still
        // open. Refresh both menus so the Windows client can see the seeded
        // card before it moves it through that postbox.
        player.getInventory().setChanged();
        player.containerMenu.broadcastFullState();
        if (player.containerMenu != player.inventoryMenu) player.inventoryMenu.broadcastFullState();
        waiting = phase;
        // A turn can contain the first model request plus a tool follow-up,
        // each with the configured 180 second provider limit.
        deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(390);
    }
    public static void seedWithRewardsMail(ServerPlayer player) {
        require(secret != null, "Live provider acceptance has not initialized its session secret");
        seed(player, "return");
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (waiting == null || !enabled() || playerId == null) return;
        var player = event.getServer().getPlayerList().getPlayer(playerId);
        if (player == null) return;
        var c = conversation(player);
        int count = waiting.equals("publish") ? 2 : waiting.equals("escrow_return") || waiting.equals("return") ? 4 : 6;
        try {
            if (c.getTurns().size() >= count && c.getPendingId() == null && c.getInbox().isEmpty()) {
                String phase = waiting; waiting = null; verify(player, c, phase);
            } else if (System.nanoTime() >= deadline) {
                String phase = waiting; waiting = null;
                throw new IllegalStateException("Real Magpie " + phase + " timed out; turns=" + c.getTurns().size() + ", pending=" + c.getPendingId());
            }
        } catch (Exception error) { fail(player, "response", error); }
    }
    private static void verify(ServerPlayer player, NpcLlmSavedData.Conversation c, String phase) throws Exception {
        if (phase.equals("publish")) {
            require(c.getPublished().size() == 1 && c.getPublished().containsKey("intro"),
                    "Provider did not invoke authorized publication exactly once; actual reply=" + c.getTurns().get(c.getTurns().size() - 1).getText());
            published = c.getPublished().get("intro");
            var quest = QuestGraphService.INSTANCE.getQuest(player.serverLevel(), published);
            var firstTask = quest == null ? null : quest.getTasks().stream().findFirst().orElse(null);
            var firstReward = quest == null ? null : quest.getRewards().stream().findFirst().orElse(null);
            require(quest != null && quest.getRawTitle().equals("CQ Magpie quest") && quest.getX() == 6 && quest.getY() == 7
                    && quest.getTasks().size() == 1 && quest.getRewards().size() == 1
                    && firstTask instanceof ParcelTask task && task.getItemStack().is(Items.DIAMOND) && task.getCount() == 2 && task.getTargetAddressee().equals(NPC)
                    && firstReward instanceof ParcelReward reward && reward.getItem().is(Items.APPLE) && reward.getCount() == 3 && reward.getTargetAddressee().equals(NPC),
                    "Live model publication differs from actual native quest/task/reward fields");
            require(c.getTurns().size() == 2 && c.getTurns().get(c.getTurns().size() - 1).getText().equals("CQ_LIVE_PUBLISHED:" + published)
                    && c.getKeptItems().isEmpty() && c.getRewardItems().stream().flatMap(e -> e.getItems().stream())
                        .filter(s -> s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum() == 4
                    && c.getRewardItems().stream().flatMap(e -> e.getItems().stream())
                        .anyMatch(s -> PostcardItem.getText(s).equals("CQ_LIVE_PUBLISH SECRET=" + secret)),
                    "Live reply did not use the real tool result or escrow the exact mixed-envelope contents for reward claim");
            assertReplies(player, List.of("CQ_LIVE_PUBLISHED:" + published));
            require(count(Items.EMERALD) == 0, "withReward attachments were returned before an actual FTB claim");
            TeamData teamData = ServerQuestFile.INSTANCE.getOrCreateTeamData(player);
            teamData.setCompleted(quest.getId(), new Date());
            teamData.claimReward(player, firstReward, false);
            teamData.claimReward(player, firstReward, false);
            RewardDistributionManager.INSTANCE.onServerTick(player.serverLevel());
            long returnedOriginal = cards().stream().filter(s -> PostcardItem.getText(s).equals("CQ_LIVE_PUBLISH SECRET=" + secret))
                    .mapToLong(ItemStack::getCount).sum();
            require(teamData.isRewardClaimed(player.getUUID(), firstReward) && count(Items.EMERALD) == 4 && count(Items.APPLE) == 3
                    && returnedOriginal == 1 && c.getRewardItems().isEmpty(),
                    "Real Magpie withReward decision did not return escrow with the actual native quest reward exactly once");
            record("reward_claimed", "Native FTB claim returned the exact AI-selected escrow alongside its quest reward once");
            escrowReturnText = "CQ_LIVE_ESCROW_RETURN";
        } else {
            boolean returnRound = phase.equals("return");
            String reply = (returnRound ? "CQ_LIVE_RECALLED:" : "CQ_LIVE_RESTARTED:") + published + ":" + secret;
            require(c.getTurns().size() == (returnRound ? 4 : 6) && c.getTurns().get(c.getTurns().size() - 1).getText().equals(reply)
                    && c.getPublished().size() == 1 && c.getKeptItems().isEmpty() && c.getRewardItems().isEmpty(),
                    "Live model lost prior secret/tool results, replayed publication or ignored its mail policy");
            if (returnRound)
                assertReplies(player, List.of("CQ_LIVE_PUBLISHED:" + published, "CQ_LIVE_RECALLED:" + published + ":" + secret));
            if (phase.equals("return")) {
                require(escrowReturnText != null && count(Items.EMERALD) == 4
                        && countOriginalCard("CQ_LIVE_PUBLISH SECRET=" + secret, player) == 1
                        && countOriginalCard(escrowReturnText, player) == 1,
                        "Real Magpie return policy changed already-claimed reward escrow or the actual temporary card");
            } else {
                require(cards().stream().filter(s -> NPC.equals(ContactMailNbt.INSTANCE.getSender(s)))
                    .filter(s -> PostcardItem.getText(s).equals(reply)).mapToInt(ItemStack::getCount).sum() == 1
                    && cards().stream().noneMatch(s -> PostcardItem.getText(s).equals("CQ_LIVE_RESTART")), "Restart discard/reply was duplicated or incorrect");
            }
        }
        var store = NpcLlmSavedData.Companion.get(player.serverLevel());
        require(store.conversation(NPC, UUID.randomUUID(), player.getUUID(), "team").getTurns().isEmpty()
                && store.conversation(NPC + "Other", team, player.getUUID(), "team").getTurns().isEmpty()
                && legacyCallbacks == 0 && CollectionSavedData.Companion.get(player.serverLevel()).getTriggerCount(player.getUUID(), NPC) == 0,
                "Real NPC context or old reactions crossed an identity boundary");
        record(phase, "Actual " + System.getProperty("contactquests.test.llmModel") + " request completed: exact Contact reply/tool ID, native graph, AI item disposition, isolated context and no legacy reaction");
    }
    private static void collect(ServerPlayer player) {
        String expected = "CQ_LIVE_RECALLED:" + published + ":" + secret;
        var card = cards().stream().filter(s -> NPC.equals(ContactMailNbt.INSTANCE.getSender(s)) && PostcardItem.getText(s).equals(expected)).findFirst().orElseThrow();
        player.getInventory().setItem(0, card.copy()); player.getInventory().selected = 0;
        player.getInventory().setChanged();
        player.containerMenu.broadcastFullState();
        if (player.containerMenu != player.inventoryMenu) player.inventoryMenu.broadcastFullState();
        record("collected", "Exact live NPC reply from the real team mailbox is available for the native Windows Contact reader");
    }
    private static void finish(ServerPlayer player) throws Exception {
        require(ContactQuestsTestServerController.isCasePassed("server.llm_live.return"), "Real two-round acceptance did not finish");
        var c = conversation(player); var saved = new JsonObject();
        saved.addProperty("pid", firstPid); saved.addProperty("team", team.toString()); saved.addProperty("player", playerId.toString());
        saved.addProperty("secret", secret); saved.addProperty("quest", published);
        saved.addProperty("turns", c.getTurns().size()); saved.addProperty("history", c.getTurns().toString());
        Files.writeString(path(), new GsonBuilder().setPrettyPrinting().create().toJson(saved));
        var active = new Gson().toJsonTree(NpcLlmConfig.INSTANCE.current()).getAsJsonObject();
        var persisted = diskConfig.deepCopy(); persisted.addProperty("enabled", true);
        persisted.getAsJsonObject("providers").add("live", active.getAsJsonObject("providers").get("live"));
        persisted.getAsJsonObject("npcs").add(NPC, active.getAsJsonObject("npcs").get(NPC));
        Files.writeString(configPath(), new GsonBuilder().setPrettyPrinting().create().toJson(persisted));
        restore(player); NpcLlmConfig.INSTANCE.configure(original); player.server.overworld().getDataStorage().save();
        record("snapshot", "Real history and scoped publication saved for another JVM; original test mailbox restored exactly");
    }
    private static void rejoin(ServerPlayer player) throws Exception {
        var saved = JsonParser.parseString(Files.readString(path())).getAsJsonObject();
        require(saved.get("pid").getAsLong() != ProcessHandle.current().pid(), "Live rejoin did not start another JVM");
        team = UUID.fromString(saved.get("team").getAsString()); playerId = UUID.fromString(saved.get("player").getAsString());
        secret = saved.get("secret").getAsString(); published = saved.get("quest").getAsString();
        require(player.getUUID().equals(playerId) && FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow().getId().equals(team), "Live player/team changed through restart");
        var c = conversation(player);
        require(c.getTurns().size() == saved.get("turns").getAsInt() && c.getTurns().toString().equals(saved.get("history").getAsString())
                && c.getPublished().size() == 1 && published.equals(c.getPublished().get("intro")) && c.getPendingId() == null && c.getInbox().isEmpty(),
                "Live conversation/tool state did not persist without replay");
        original = NpcLlmConfig.INSTANCE.current(); previousMailbox = copy(PlatformHelper.getMailboxContents(team)); restored = false;
        PlatformHelper.resetMailboxContents(team); legacyCallbacks = 0;
        record("rejoin", "New JVM restored exact live-provider transcript, player/team and native published graph without replay");
    }
    public static void restoreAfterRestart(ServerPlayer player) {
        if (!enabled()) return;
        try {
            require(ContactQuestsTestServerController.isCasePassed("server.llm_live.restart"), "No real restart response evidence");
            restore(player); record("restored", "Restart conversation finished and the pre-existing team mailbox was restored");
        } catch (Exception error) { fail(player, "restored", error); }
    }
    private static void restore(ServerPlayer player) {
        if (!restored && previousMailbox != null) { PlatformHelper.setMailboxContents(team, previousMailbox); restored = true; }
    }
    private static void fail(ServerPlayer player, String phase, Exception error) {
        waiting = null; restore(player);
        if (original != null) NpcLlmConfig.INSTANCE.configure(original);
        ContactQuestsTestServerController.recordCase("server.llm_live." + phase, false, error.toString());
    }
    private static NpcLlmSavedData.Conversation conversation(ServerPlayer player) {
        return NpcLlmSavedData.Companion.get(player.serverLevel()).conversation(NPC, team, player.getUUID(), "team");
    }
    private static void record(String phase, String text) { ContactQuestsTestServerController.recordCase("server.llm_live." + phase, true, text); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static SimpleContainer copy(SimpleContainer box) {
        var copy = new SimpleContainer(box.getContainerSize()); for (int i = 0; i < box.getContainerSize(); i++) copy.setItem(i, box.getItem(i).copy()); return copy;
    }
    private static List<ItemStack> cards() { return mail().stream().filter(s -> s.getItem() instanceof PostcardItem).toList(); }
    private static List<ItemStack> mail() {
        var result = new ArrayList<ItemStack>(); var box = PlatformHelper.getMailboxContents(team);
        for (int i = 0; i < box.getContainerSize(); i++) {
            var stack = box.getItem(i); if (!stack.isEmpty()) result.add(stack);
            ContactMailNbt.INSTANCE.readContents(stack).stream().filter(s -> !s.isEmpty()).forEach(result::add);
        }
        return result;
    }
    private static int count(Item item) { return mail().stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum(); }
    private static int countOriginalCard(String text, ServerPlayer player) {
        return cards().stream().filter(s -> PostcardItem.getText(s).equals(text) && player.getScoreboardName().equals(ContactMailNbt.INSTANCE.getSender(s))
                && new ResourceLocation("contact:creeper").equals(ContactMailNbt.INSTANCE.getPostcardStyleId(s))).mapToInt(ItemStack::getCount).sum();
    }
    private static void assertReplies(ServerPlayer player, List<String> expected) {
        if (cRestart()) return;
        var replies = cards().stream().filter(s -> NPC.equals(ContactMailNbt.INSTANCE.getSender(s))).toList();
        require(replies.size() == expected.size() && expected.stream().allMatch(text -> replies.stream().filter(s -> PostcardItem.getText(s).equals(text)).mapToInt(ItemStack::getCount).sum() == 1)
                && replies.stream().allMatch(s -> new ResourceLocation("contact:default").equals(ContactMailNbt.INSTANCE.getPostcardStyleId(s))),
                "Actual Contact reply card text/style/sender/count differs from fixed live-provider contract");
    }
    private static boolean cRestart() { return firstPid != ProcessHandle.current().pid(); }
}
