package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.compat.kubejs.QuestOverrideSavedData;
import com.creeperyang.contactquests.config.NpcConfigManager;
import com.creeperyang.contactquests.data.DataManager;
import com.creeperyang.contactquests.data.TaskDeliverySavedData;
import com.creeperyang.contactquests.quest.QuestStateEvents;
import com.creeperyang.contactquests.quest.reward.ParcelReward;
import com.creeperyang.contactquests.quest.reward.PostcardReward;
import com.creeperyang.contactquests.quest.reward.RedPacketReward;
import com.creeperyang.contactquests.quest.task.ParcelTask;
import com.creeperyang.contactquests.quest.task.PostcardTask;
import com.creeperyang.contactquests.quest.task.RedPacketTask;
import com.creeperyang.contactquests.bridge.IQuestExtension;
import com.creeperyang.contactquests.bridge.ITeamDataExtension;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Native /reload acceptance against the existing fresh-process rejoin objects.
 * Register one test command which calls prepare(source.getPlayerOrException()).
 * After server.reload.prepared passes, the CLIENT must send the literal "reload"
 * through its normal connection, then wait for server.reload.complete (120 s).
 * Finally allow client packets to settle and run the existing assert_rejoin_client.
 * Insert after its original rejoin checks and before features/rejoin, which advances
 * delivery state. Keep the GUI closed until completion so the integrated server ticks.
 * No direct loader calls, synthetic events, replacement reload command or new graph.
 *
 * The native 1.20.1 command announces success before reloading. That message is not
 * a completion barrier. We observe its real CommandEvent, SERVER_DATA_LOAD tags and
 * all-player datapack sync, then verify on a later server tick with new resource and
 * Commands instances. The native server-thread reloadResources uses managedBlock
 * until its final reload future completes, including the post-tag player sync.
 *
 * Requires the existing KubeJS-enabled client-rejoin phase, its unchanged snapshot,
 * an op player and no concurrent graph/team/inventory mutations. Never call prepare
 * in smoke or no-KubeJS: this fixture intentionally relies on the prior JVM's data.
 * SavedData/config files are observed, never rewritten. Cleanup only restores the
 * disturbed in-memory fields on failure; failures remain failures.
 */
@EventBusSubscriber(modid = ContactQuestsTestMod.MOD_ID)
public final class ContactQuestsNativeReloadAssertions {
    private static final String PREFIX = "server.reload.";
    private static final String DISTURBED = "CQNativeReloadDisturbed";
    private static final Gson GSON = new Gson();
    private static volatile Session active;

    private ContactQuestsNativeReloadAssertions() {}

    private record ObjectState(long id, String type, CompoundTag nbt) {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class ContainerState {
        final Object live;
        final Object before;
        final boolean functions;

        ContainerState(Object live, boolean functions) {
            this.live = live;
            this.functions = functions;
            if (live instanceof Map map) {
                Map copy = new LinkedHashMap();
                map.forEach((key, value) -> copy.put(key, value instanceof Set set ? new HashSet(set) : value));
                before = copy;
            } else if (live instanceof Collection list) {
                before = new ArrayList(list);
            } else throw new IllegalArgumentException("Unsupported fixture container: " + live);
        }

        boolean sameIndex() {
            Map now = (Map) live;
            Map saved = (Map) before;
            return functions ? now.keySet().equals(saved.keySet()) : now.equals(saved);
        }

        void restore() {
            if (live instanceof Map map) {
                map.clear();
                ((Map) before).forEach((key, value) -> map.put(key, value instanceof Set set ? new HashSet(set) : value));
            } else {
                Collection list = (Collection) live;
                list.clear();
                list.addAll((Collection) before);
            }
        }
    }

    private static final class Session {
        final MinecraftServer server;
        final UUID playerId;
        final ServerQuestFile file;
        final Object resources;
        final Object commands;
        final List<ObjectState> objects = new ArrayList<>();
        final List<ContainerState> indices = new ArrayList<>();
        final List<ContainerState> replacers = new ArrayList<>();
        final List<QuestStateEvents.Change> changes = new ArrayList<>();
        final Consumer<QuestStateEvents.Change> listener = changes::add;
        final Map<String, Object> titles = new HashMap<>();
        final Map<String, Object> subtitles = new HashMap<>();
        final Map<String, Object> descriptions = new HashMap<>();
        final Path npcPath = FMLPaths.CONFIGDIR.get().resolve("contactquests/npc_config.json");
        TeamData team;
        Set<String> tags;
        Set<Long> forced, blocked;
        Quest quest;
        ParcelTask task;
        long progress, pending;
        CompoundTag overrides;
        byte[] npcBytes;
        ContainerState npcMap;
        boolean dirty;
        boolean disturbed, prepared, observing, finished;
        int commandCount, tagsCount, syncCount, syncTick = -1;
        long deadline;
        String commandError;

        Session(ServerPlayer player) {
            server = player.server;
            playerId = player.getUUID();
            file = ServerQuestFile.INSTANCE;
            resources = server.getResourceManager();
            commands = server.getCommands();
        }
    }

    public static void prepare(ServerPlayer player) {
        Session s = null;
        try {
            require(player.server.isSameThread(), "Prepare must run on the server thread");
            require(active == null, "Do not repeat native reload in the same fixture JVM");
            require(Boolean.getBoolean("contactquests.test.rejoin") && ModList.get().isLoaded("kubejs"),
                    "Native reload requires the KubeJS-enabled fresh-process rejoin phase");
            require(player.createCommandSourceStack().hasPermission(2), "Real client needs reload permission");
            s = new Session(player);
            active = s;
            capture(s, player);
            s.disturbed = true;
            disturb(s, player);
            QuestStateEvents.addListener(s.listener);
            s.prepared = true;
            s.observing = true;
            s.deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
            ContactQuestsTestServerController.recordCase(PREFIX + "prepared", true,
                    "Existing rejoin/public-API objects and disk overrides captured; six native objects, text, index, NPC cache and replacer disturbed only in memory");
        } catch (Exception error) {
            if (s != null) cleanup(s, error);
            ContactQuestsTestServerController.recordCase(PREFIX + "prepared", false, error.toString());
            ContactQuestsTestServerController.recordCase(PREFIX + "complete", false, "Preparation failed: " + error);
        }
    }

    private static void capture(Session s, ServerPlayer player) throws Exception {
        var main = JsonParser.parseString(Files.readString(ContactQuestsRejoinFixtures.snapshotPath())).getAsJsonObject();
        var api = JsonParser.parseString(Files.readString(Path.of(ContactQuestsRejoinFixtures.snapshotPath()
                + ".public-api.json"))).getAsJsonObject();
        require(main.get("seedPid").getAsLong() != ProcessHandle.current().pid()
                        && api.get("pid").getAsLong() != ProcessHandle.current().pid()
                        && main.get("player").getAsString().equals(s.playerId.toString())
                        && api.get("player").getAsString().equals(s.playerId.toString()),
                "Rejoin inputs are not from the previous JVM/same player");
        var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
        require(team.getId().toString().equals(main.get("team").getAsString()), "Rejoin team changed");
        s.team = s.file.getOrCreateTeamData(team);
        var ext = (ITeamDataExtension) s.team;
        s.tags = new HashSet<>(ext.contactQuests$getTags());
        s.forced = new HashSet<>(ext.contactQuests$getForcedQuests());
        s.blocked = new HashSet<>(ext.contactQuests$getBlockedQuests());
        s.quest = s.file.getQuest(id(main, "quest"));
        s.task = (ParcelTask) s.file.getTask(id(main, "task"));
        require(s.quest != null && s.task != null && s.task.getCount() == 10
                        && s.task.getItemStack().is(Items.DIAMOND) && s.team.getProgress(s.task) == 3
                        && s.tags.contains("CQPersistTag") && s.team.canStartTasks(s.quest),
                "Existing rejoin graph/tag/progress precondition failed");
        s.progress = s.team.getProgress(s.task);
        s.pending = TaskDeliverySavedData.Companion.get(player.serverLevel())
                .getPendingAmount(team.getId(), s.task.getId());
        var overrides = QuestOverrideSavedData.Companion.get(player.serverLevel());
        s.overrides = overrides.save(new CompoundTag()).copy();
        for (var element : api.getAsJsonArray("objects")) {
            var expected = element.getAsJsonObject();
            var object = s.file.getBase(id(expected, "id"));
            require(object != null && object.getClass().getName().equals(expected.get("type").getAsString())
                            && nativeTag(object, player).toString().equals(expected.get("nbt").getAsString()),
                    "Existing public API native snapshot mismatch before reload: " + expected.get("id"));
            var saved = overrides.getOverride(object.getId());
            require((saved == null ? "" : saved.toString()).equals(expected.get("override").getAsString()),
                    "Existing public API override changed before reload");
            s.objects.add(new ObjectState(object.getId(), object.getClass().getName(), nativeTag(object, player)));
        }
        for (String key : List.of("quest", "task", "mutex", "editorQuest")) {
            var object = s.file.getBase(id(main, key));
            require(object != null, "Existing rejoin object missing: " + key);
            s.objects.add(new ObjectState(object.getId(), object.getClass().getName(), nativeTag(object, player)));
        }
        copyText(s.quest, "contactQuests$titleOverrides", s.titles);
        copyText(s.quest, "contactQuests$subtitleOverrides", s.subtitles);
        copyText(s.quest, "contactQuests$descOverrides", s.descriptions);
        require(s.titles.containsKey("zh_cn") && s.subtitles.containsKey("zh_cn")
                        && s.descriptions.containsKey("zh_cn"), "Saved rejoin text overrides are missing");
        s.indices.add(new ContainerState(DataManager.parcelReceiver, false));
        s.indices.add(new ContainerState(DataManager.redPacketReceiver, false));
        s.indices.add(new ContainerState(DataManager.postcardReceiver, false));
        s.indices.add(new ContainerState(DataManager.INSTANCE.getParcelTasks(), false));
        s.indices.add(new ContainerState(DataManager.INSTANCE.getRedPacketTasks(), false));
        s.indices.add(new ContainerState(DataManager.INSTANCE.getPostcardTasks(), false));
        s.indices.add(new ContainerState(DataManager.INSTANCE.getItemTestFunc(), true));
        s.indices.add(new ContainerState(DataManager.INSTANCE.getRedPacketItemTestFunc(), true));
        s.indices.add(new ContainerState(DataManager.INSTANCE.getPostcardItemTestFunc(), true));
        s.dirty = DataManager.targetsDirty;
        require(!s.dirty && DataManager.INSTANCE.getParcelTasks().get(s.task.getId()) == s.task
                        && DataManager.parcelReceiver.get(s.task.getTargetAddressee()).contains(s.task.getId()),
                "Rejoin receiver index must be fresh before deliberate disturbance");
        s.npcBytes = Files.readAllBytes(s.npcPath);
        s.npcMap = new ContainerState(staticField(NpcConfigManager.class, "npcMap"), false);
        require(GSON.toJsonTree(s.npcMap.live).equals(JsonParser.parseString(new String(s.npcBytes,
                        java.nio.charset.StandardCharsets.UTF_8))), "NPC memory/config differs before reload");
        s.replacers.add(new ContainerState(PostcardTask.PostcardPlaceholderSupport.INSTANCE.getReplacers(), false));
        s.replacers.add(new ContainerState(RedPacketTask.RedPacketPlaceholderSupport.INSTANCE.getReplacers(), false));
        s.replacers.add(new ContainerState(staticField(PostcardReward.class, "replacers"), false));
        s.replacers.add(new ContainerState(staticField(RedPacketReward.class, "replacers"), false));
        s.replacers.add(new ContainerState(staticField(com.creeperyang.contactquests.data.CollectionSavedData.class, "replacers"), false));
        require(ContactQuestsScriptAssertions.replacers(player), "Exact existing five replacer assertions fail before reload");
    }

    @SuppressWarnings("rawtypes")
    private static void disturb(Session s, ServerPlayer player) {
        int changed = 0;
        for (ObjectState state : s.objects) {
            var object = s.file.getBase(state.id());
            boolean modified = true;
            if (object instanceof ParcelTask task && object != s.task) {
                task.setItemStack(new ItemStack(Items.DIRT)); task.setCount(105); changed++;
            } else if (object instanceof RedPacketTask task) {
                task.setItemStack(new ItemStack(Items.DIRT)); task.setCount(106); task.setBlessing(DISTURBED); changed++;
            } else if (object instanceof PostcardTask task) {
                task.setPostcardStyle("contact:default"); task.setPostcardText(DISTURBED); changed++;
            } else if (object instanceof ParcelReward reward) {
                reward.setItem(new ItemStack(Items.DIRT)); reward.setCount(107); reward.setRandomBonus(12); changed++;
            } else if (object instanceof RedPacketReward reward) {
                reward.setItem(new ItemStack(Items.DIRT)); reward.setCount(108);
                reward.setRandomBonus(13); reward.setBlessing(DISTURBED); changed++;
            } else if (object instanceof PostcardReward reward) {
                reward.setPostcardStyle("contact:default"); reward.setPostcardText(DISTURBED); changed++;
            } else modified = false;
            if (modified) require(!nativeTag(object, player).equals(state.nbt()),
                    "Native task/reward was not observably disturbed: " + state.id());
        }
        require(changed == 6, "Disturb exactly the six existing public API task/reward objects");
        var text = (IQuestExtension) (Object) s.quest;
        s.titles.keySet().forEach(locale -> text.contactQuests$setTitleOverride(locale, DISTURBED));
        s.subtitles.keySet().forEach(locale -> text.contactQuests$setSubtitleOverride(locale, DISTURBED));
        s.descriptions.keySet().forEach(locale -> text.contactQuests$setDescriptionOverride(locale, List.of(DISTURBED)));
        s.quest.clearCachedData();
        DataManager.INSTANCE.getParcelTasks().remove(s.task.getId());
        DataManager.INSTANCE.getItemTestFunc().remove(s.task.getId());
        DataManager.parcelReceiver.get(s.task.getTargetAddressee()).remove(s.task.getId());
        ((Map) s.npcMap.live).clear();
        // Keep recipient names unchanged: DataManager's rebuild must not discover
        // artificial NPCs and persist them while the native objects are disturbed.
        PostcardTask.PostcardPlaceholderSupport.INSTANCE.register("contactquests_test_postcard", (p, t) -> DISTURBED);
        require(!DataManager.INSTANCE.getParcelTasks().containsKey(s.task.getId())
                        && !DataManager.INSTANCE.getItemTestFunc().containsKey(s.task.getId())
                        && !DataManager.parcelReceiver.get(s.task.getTargetAddressee()).contains(s.task.getId())
                        && ((Map) s.npcMap.live).isEmpty()
                        && DISTURBED.equals(ContactQuestsScriptAssertions.overrideMap(s.quest, "contactQuests$titleOverrides").get("zh_cn"))
                        && DISTURBED.equals(PostcardTask.PostcardPlaceholderSupport.INSTANCE
                        .replace("contactquests_test_postcard", player, s.team)),
                "In-memory disturbance did not reach all target caches");
        require(s.overrides.equals(QuestOverrideSavedData.Companion.get(player.serverLevel())
                        .save(new CompoundTag())), "Disturbance wrote to SavedData");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onCommand(CommandEvent event) {
        Session s = active;
        if (s == null || !s.observing || s.finished) return;
        var parsed = event.getParseResults();
        if (parsed.getContext().getSource().getServer() != s.server
                || !parsed.getReader().getString().equals("reload")) return;
        s.commandCount++;
        if (!s.playerId.equals(parsed.getContext().getSource().getEntity() == null ? null
                : parsed.getContext().getSource().getEntity().getUUID()) || event.isCanceled()
                || event.getException() != null || parsed.getContext().getNodes().size() != 1
                || parsed.getContext().getNodes().get(0).getNode() != s.server.getCommands().getDispatcher()
                        .getRoot().getChild("reload")) {
            s.commandError = "Reload was cancelled, substituted, or did not come from the prepared real player";
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        Session s = active;
        if (s != null && s.observing && !s.finished && s.commandCount > 0 && s.server.isSameThread()
                && event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) s.tagsCount++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        Session s = active;
        if (s != null && s.observing && !s.finished && s.commandCount > 0 && s.server.isSameThread()
                && event.getPlayerList() == s.server.getPlayerList() && event.getPlayer() == null) {
            s.syncCount++;
            s.syncTick = s.server.getTickCount();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Session s = active;
        if (s == null || !s.observing || s.finished || event.getServer() != s.server) return;
        if (s.syncCount == 0 || s.server.getTickCount() <= s.syncTick) {
            if (System.nanoTime() > s.deadline) fail(s, new IllegalStateException("Native reload completion barrier timed out"));
            return;
        }
        try {
            var player = s.server.getPlayerList().getPlayer(s.playerId);
            require(player != null, "Prepared player disconnected during native reload");
            check("barrier", s.prepared && s.commandCount == 1 && s.commandError == null
                            && s.tagsCount == 1 && s.syncCount == 1 && s.file == ServerQuestFile.INSTANCE
                            && s.resources != s.server.getResourceManager() && s.commands != s.server.getCommands(),
                    "Real client /reload command=1, server tags=1, global datapack sync=1 and a later tick with replaced resources/commands; "
                            + "observed=" + s.commandCount + "/" + s.tagsCount + "/" + s.syncCount + "; " + s.commandError);
            boolean objects = true;
            for (ObjectState expected : s.objects) {
                var object = s.file.getBase(expected.id());
                objects &= object != null && object.getClass().getName().equals(expected.type())
                        && nativeTag(object, player).equals(expected.nbt());
            }
            check("overrides_reapplied", objects && textEquals(s.quest, "contactQuests$titleOverrides", s.titles)
                            && textEquals(s.quest, "contactQuests$subtitleOverrides", s.subtitles)
                            && textEquals(s.quest, "contactQuests$descOverrides", s.descriptions)
                            && s.overrides.equals(QuestOverrideSavedData.Companion.get(player.serverLevel())
                            .save(new CompoundTag())),
                    "Six disturbed public API tasks/rewards and all rejoin text restored exactly; native NBT/override SavedData unchanged");
            check("indices_and_npc", s.indices.stream().allMatch(ContainerState::sameIndex) && !DataManager.targetsDirty
                            && DataManager.INSTANCE.getParcelTasks().get(s.task.getId()) == s.task
                            && DataManager.INSTANCE.getItemTestFunc().get(s.task.getId()).invoke(new ItemStack(Items.DIAMOND))
                            && !DataManager.INSTANCE.getItemTestFunc().get(s.task.getId()).invoke(new ItemStack(Items.DIRT))
                            && GSON.toJsonTree(s.npcMap.live).equals(JsonParser.parseString(new String(s.npcBytes,
                            java.nio.charset.StandardCharsets.UTF_8)))
                            && Arrays.equals(s.npcBytes, Files.readAllBytes(s.npcPath)),
                    "Removed receiver/task/item-test entries and cleared NPC cache rebuilt from the native reload; config file bytes unchanged");
            var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
            var ext = (ITeamDataExtension) s.team;
            check("state_unchanged", team.getId().equals(s.team.getTeamId())
                            && new HashSet<>(ext.contactQuests$getTags()).equals(s.tags)
                            && new HashSet<>(ext.contactQuests$getForcedQuests()).equals(s.forced)
                            && new HashSet<>(ext.contactQuests$getBlockedQuests()).equals(s.blocked)
                            && s.team.getProgress(s.task) == s.progress && s.team.canStartTasks(s.quest)
                            && TaskDeliverySavedData.Companion.get(player.serverLevel())
                            .getPendingAmount(team.getId(), s.task.getId()) == s.pending && s.changes.isEmpty(),
                    "Reload preserves exact team/tag/forced/blocked/progress/pending state and emits zero story state events");
            check("replacers_once", ContactQuestsScriptAssertions.replacers(player),
                    "Native reload re-registers five real Rhino replacers exactly once, including base|red_packet without duplicate suffix");
            QuestStateEvents.removeListener(s.listener);
            s.finished = true;
            s.observing = false;
            ContactQuestsTestServerController.recordCase(PREFIX + "complete", true,
                    "Native command reached its post-reload barrier and all exact restoration/isolation assertions passed");
        } catch (Exception error) { fail(s, error); }
    }

    private static void fail(Session s, Exception error) {
        cleanup(s, error);
        ContactQuestsTestServerController.recordCase(PREFIX + "complete", false, error.toString());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void cleanup(Session s, Exception error) {
        s.finished = true;
        s.observing = false;
        QuestStateEvents.removeListener(s.listener);
        if (!s.disturbed) return;
        try {
            var player = s.server.getPlayerList().getPlayer(s.playerId);
            if (player != null) for (ObjectState state : s.objects) {
                var object = s.file.getBase(state.id());
                if (object != null) { object.readData(state.nbt().copy()); object.clearCachedData(); }
            }
            if (s.quest != null) {
                var ext = (IQuestExtension) (Object) s.quest;
                s.titles.forEach((locale, value) -> ext.contactQuests$setTitleOverride(locale, (String) value));
                s.subtitles.forEach((locale, value) -> ext.contactQuests$setSubtitleOverride(locale, (String) value));
                s.descriptions.forEach((locale, value) -> ext.contactQuests$setDescriptionOverride(locale, (List<String>) value));
                s.quest.clearCachedData();
            }
            s.indices.forEach(ContainerState::restore);
            s.replacers.forEach(ContainerState::restore);
            if (s.npcMap != null) s.npcMap.restore();
            DataManager.targetsDirty = s.dirty;
        } catch (Exception cleanup) { error.addSuppressed(cleanup); }
    }

    private static void copyText(Quest quest, String field, Map<String, Object> destination) {
        ContactQuestsScriptAssertions.overrideMap(quest, field).forEach((key, value) ->
                destination.put((String) key, value instanceof List<?> list ? new ArrayList<>(list) : value));
    }

    private static boolean textEquals(Quest quest, String field, Map<String, Object> expected) {
        return ContactQuestsScriptAssertions.overrideMap(quest, field).equals(expected);
    }

    private static Object staticField(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static CompoundTag nativeTag(QuestObjectBase object, ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        object.writeData(tag);
        return tag;
    }

    private static long id(JsonObject json, String key) { return Long.parseUnsignedLong(json.get(key).getAsString(), 16); }
    private static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    private static void check(String name, boolean ok, String message) {
        ContactQuestsTestServerController.recordCase(PREFIX + name, ok, message);
        require(ok, name + ": " + message);
    }
}
