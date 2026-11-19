package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.compat.kubejs.KubeJSMailQueueSavedData;
import com.creeperyang.contactquests.compat.kubejs.KubeJSNpcSavedData;
import com.creeperyang.contactquests.config.ContactConfig;
import com.creeperyang.contactquests.config.NpcConfigManager;
import com.creeperyang.contactquests.data.CollectionSavedData;
import com.creeperyang.contactquests.data.DataManager;
import com.creeperyang.contactquests.data.TaskDeliverySavedData;
import com.creeperyang.contactquests.llm.NpcLlmConfig;
import com.creeperyang.contactquests.quest.QuestGraphService;
import com.creeperyang.contactquests.quest.task.ContactTask;
import com.creeperyang.contactquests.quest.task.ParcelTask;
import com.creeperyang.contactquests.quest.task.PostcardTask;
import com.flechazo.contact.common.component.ContactDataComponents;
import com.flechazo.contact.common.item.ParcelItem;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.common.screenhandler.PostboxScreenHandler;
import dev.ftb.mods.ftblibrary.util.NetworkHelper;
import dev.ftb.mods.ftbquests.net.SyncQuestsMessage;
import dev.ftb.mods.ftbquests.net.SyncTeamDataMessage;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Online delivery boundaries, driven exclusively by a real Contact postbox send.
 *
 * <p>Wire a server command {@code delivery_boundary <type> <phase>} to
 * {@link #run(ServerPlayer, String, String)}. Types are parcel, ender_parcel,
 * postcard, ender_postcard, same_tick, deleted_task. Run cases sequentially.
 * Server phases are prepare, delete (deleted_task only), verify, finish.
 * Both tick listeners register automatically and bracket ContactQuests' NORMAL
 * ServerTickEvent.Post handler. They never invoke matching, sendMail or queue.tick.
 *
 * <p>After prepare, wait for inventory synchronization, arm the companion client
 * assertion, open_real_postbox, use its move/staged phases, then use the existing
 * recipient enquiry/selection and click_send actions. Wait for the server accepted
 * case, check the client's early phase, and close the screen. For deleted_task,
 * issue delete immediately after accepted, before the 200-tick deadline. Wait for
 * due, client due; wait for no_repeat, client no_repeat; then verify, finish and
 * client finish. Close using player.closeContainer(), so the server also receives
 * the close packet. Wait at least 10 client ticks after each server notification
 * barrier and after finish. The existing 10-tick composite needs no extra phase;
 * 40-second barriers cover these windows at normal tick rate. There is no
 * integrated-server dependency.
 *
 * <p>Fixed server IDs: server.delivery_boundary.TYPE.{prepare,staged,accepted,
 * due,no_repeat,verify,finish}; deleted_task also requires .delete. An absent
 * milestone is not a successful compatibility fallback. Normal acceptance must
 * expose the exact configured countdown and zero progress; ender acceptance must
 * already expose the exact submitted progress with no queue. same_tick uses one
 * normal envelope with diamond x2 and apple x3: the normal allocator creates two
 * entries for one NPC in the same handler invocation without editing queue ticks.
 * If acceptedTick is A, the first natural decrement is in A's NORMAL handler;
 * normal due is A+199, and no_repeat is due+220. Ender due is A and its
 * no_repeat is A+220. The deleted task must be removed while ticksLeft > 1.
 */
@EventBusSubscriber(modid = ContactQuestsTestMod.MOD_ID)
public final class ContactQuestsDeliveryBoundaryFixtures {
    public static final int DELAY_TICKS = 200;
    public static final int REPEAT_WINDOW_TICKS = DELAY_TICKS + 20;
    public static final List<String> TYPES = List.of(
            "parcel", "ender_parcel", "postcard", "ender_postcard", "same_tick", "deleted_task");
    private static final int ACCEPT_TIMEOUT_TICKS = 1200;
    private static final String POSTCARD_TEXT = "沿河的花开了，愿这封信平安抵达。";
    private static State active;

    private ContactQuestsDeliveryBoundaryFixtures() {}

    public static String recipient(String type) {
        return switch (type) {
            case "parcel" -> "CQDeliveryBoundaryParcel";
            case "ender_parcel" -> "CQDeliveryBoundaryEnderParcel";
            case "postcard" -> "CQDeliveryBoundaryPostcard";
            case "ender_postcard" -> "CQDeliveryBoundaryEnderPostcard";
            case "same_tick" -> "CQDeliveryBoundarySameTick";
            case "deleted_task" -> "CQDeliveryBoundaryDeletedTask";
            default -> throw new IllegalArgumentException("Unknown delivery case: " + type);
        };
    }

    public static String caseId(String type, String phase) {
        recipient(type);
        return "server.delivery_boundary." + type + "." + phase;
    }

    public static int expectedNotifications(String type) {
        recipient(type);
        return type.startsWith("ender_") || type.equals("deleted_task") ? 0 : 1;
    }

    // Shared deterministic item construction lets the companion move the exact
    // envelope/card, rather than use the controller's mutable featureMailText.
    static ItemStack mail(String type) {
        recipient(type);
        boolean ender = type.startsWith("ender_");
        if (type.equals("postcard") || type.equals("ender_postcard")) {
            var card = PostcardItem.setText(
                    PostcardItem.getPostcard(ResourceLocation.parse("contact:default"), ender), POSTCARD_TEXT);
            require(card.getItem() instanceof PostcardItem item && item.isEnderType() == ender,
                    "Native postcard variant differs from the requested case");
            requireUnsent(card);
            return card;
        }
        var contents = new SimpleContainer(4);
        contents.setItem(0, new ItemStack(Items.DIAMOND, 2));
        if (type.equals("same_tick")) contents.setItem(1, new ItemStack(Items.APPLE, 3));
        // This Contact version calls sender.isEmpty(): use "", never null.
        // Both native factories leave POSTCARD_SENDER absent on outgoing mail.
        var parcel = ParcelItem.getParcel(contents, ender, "");
        require(parcel.getItem() instanceof ParcelItem item && item.isEnderType() == ender,
                "Native parcel variant differs from the requested case");
        requireUnsent(parcel);
        return parcel;
    }

    static void requireUnsent(ItemStack mail) {
        require(mail.getCount() == 1 && mail.get(ContactDataComponents.POSTCARD_SENDER.get()) == null,
                "Outgoing native mail must be a single unsent item without POSTCARD_SENDER");
    }

    public static void run(ServerPlayer player, String type, String phase) {
        String id = "server.delivery_boundary.invalid_step";
        try {
            id = caseId(type, phase);
            switch (phase) {
                case "prepare" -> prepare(player, type);
                case "delete" -> delete(player, state(player, type));
                case "verify" -> verify(player, state(player, type));
                case "finish" -> finish(player, state(player, type));
                default -> throw new IllegalArgumentException("Unknown delivery phase: " + phase);
            }
        } catch (Exception | LinkageError error) {
            if (active != null && active.type.equals(type)) active.failed = true;
            ContactQuestsTestServerController.recordCase(id, false, error.toString());
        }
    }

    private static void prepare(ServerPlayer player, String type) {
        require(active == null, "Finish the previous delivery case before preparing another");
        require(player.containerMenu == player.inventoryMenu && player.containerMenu.getCarried().isEmpty(),
                "Start with the normal player inventory and an empty cursor");
        var file = ServerQuestFile.INSTANCE;
        require(file != null, "Real server quest file is unavailable");
        var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
        var data = file.getNullableTeamData(team.getId());
        require(data != null, "Use an existing online team without creating or replacing its state");
        require(!data.isLocked(), "Do not change a locked team's state to make delivery pass");
        DataManager.INSTANCE.ensureTargetsFresh();
        var names = List.of(recipient(type), recipient(type) + "Guard");
        var overrides = KubeJSNpcSavedData.Companion.get(player.serverLevel());
        for (String name : names) {
            require(!DataManager.parcelReceiver.containsKey(name) && !DataManager.redPacketReceiver.containsKey(name)
                            && !DataManager.postcardReceiver.containsKey(name) && !DataManager.rewardSenders.contains(name)
                            && NpcLlmConfig.INSTANCE.npc(name) == null && overrides.getNpcData(name) == null,
                    "Fixture NPC is already owned by another graph/script/LLM: " + name);
        }
        var s = new State(type, player, data);
        for (String name : names) if (!NpcConfigManager.INSTANCE.getAllNpcNames().contains(name)) s.createdNpcNames.add(name);
        active = s;

        s.chapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(),
                "cq_delivery_boundary_" + UUID.randomUUID().toString().replace("-", ""));
        s.chapter.setRawTitle("Contact delivery boundary");
        file.getDefaultChapterGroup().addChapter(s.chapter);
        var quest = new Quest(file.newID(), s.chapter);
        quest.setRawTitle("Delivery: " + type);
        s.chapter.addQuest(quest);
        if (s.postcard) {
            var task = new PostcardTask(file.newID(), quest);
            task.setCount(4L);
            task.setPostcardStyle("contact:default");
            task.setPostcardText(POSTCARD_TEXT);
            add(s, quest, task, 1L);
        } else {
            add(s, quest, new ParcelTask(file.newID(), quest).setStackAndCount(new ItemStack(Items.DIAMOND), 7), 2L);
            if (type.equals("same_tick")) {
                add(s, quest, new ParcelTask(file.newID(), quest).setStackAndCount(new ItemStack(Items.APPLE), 9), 3L);
            }
        }
        s.guard = new ParcelTask(file.newID(), quest).setStackAndCount(new ItemStack(Items.COPPER_INGOT), 11);
        s.guard.setTargetAddressee(s.npc + "Guard");
        quest.addTask(s.guard);
        ContactQuestsTestUtil.refreshIndex(file);
        file.clearCachedProgress();
        file.markDirty();
        DataManager.targetsDirty = true;
        DataManager.INSTANCE.ensureTargetsFresh();
        // This SavedData has no runtime Rhino dependency. It holds only our NPC's
        // positive configuration across native graph/index refreshes and deletion.
        overrides.setNpcData(s.npc, DELAY_TICKS, false);
        ContactConfig.INSTANCE.getEnableDeliveryTime().set(true);
        require(data.canStartTasks(quest) && !data.isCompleted(quest), "Fixture quest is not normally startable");
        for (ContactTask task : s.tasks.values()) {
            require(data.getProgress(task) == 0 && !data.isCompleted(task) && task.getAmountNeeded(data) > s.amounts.get(task.id),
                    "Require fresh unfinished tasks with capacity above the actual sent quantity");
        }
        require(data.getProgress(s.guard) == 0 && entries(player, s).isEmpty(), "Fixture inherited progress or pending deliveries");
        require(NpcConfigManager.INSTANCE.getDeliveryTime(s.npc, player.serverLevel()) == DELAY_TICKS
                        && DataManager.INSTANCE.getAvailableTargets(player).get(s.npc) == DELAY_TICKS,
                "NPC must be a real task receiver with a positive non-LLM delivery delay");
        s.mail = mail(type);
        var collection = CollectionSavedData.Companion.get(player.serverLevel());
        s.collectionCount = collection.getTriggerCount(player.getUUID(), s.npc);
        s.collectionStacks = collection.getStacks(player.getUUID(), s.npc).stream().map(ItemStack::copy).toList();
        requireNoWrongMail(player, s);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.STICK, 7));
        player.getInventory().setItem(18, s.mail.copy());
        player.getInventory().selected = 0;
        player.getInventory().setChanged();
        player.connection.send(new ClientboundSetCarriedItemPacket(0));
        player.inventoryMenu.broadcastFullState();
        file.saveNow();
        NetworkHelper.sendTo(player, new SyncQuestsMessage(file));
        NetworkHelper.sendTo(player, new SyncTeamDataMessage(data));
        file.getTranslationManager().sendTranslationsToPlayer(player);
        record(s, "prepare", "Real unfinished tasks and exact native " + type + " in inventory slot 18; NPC="
                + s.npc + ", configured delay=" + DELAY_TICKS + ", quantities=" + s.amounts.values());
    }

    private static void add(State s, Quest quest, ContactTask task, long amount) {
        task.setTargetAddressee(s.npc);
        quest.addTask(task);
        s.tasks.put(task.id, task);
        s.amounts.put(task.id, amount);
    }

    /** Before the production delivery handler, after real client packets in this tick. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeDelivery(ServerTickEvent.Post event) {
        var s = active;
        if (s == null || s.failed || s.server != event.getServer()) return;
        try {
            var player = online(s);
            s.tick = event.getServer().getTickCount();
            requireContext(player, s);
            if (!s.staged && player.containerMenu instanceof PostboxScreenHandler menu && !menu.parcel.getItem(0).isEmpty()) {
                require(ItemStack.matches(menu.parcel.getItem(0), s.mail), "Real postbox holds a different envelope/card");
                requireUnsent(menu.parcel.getItem(0));
                require(menu.status == 1, "Native server slot listener must make unsent mail sendable without a status override");
                requireConsumedInventory(player);
                s.staged = true;
                s.menuId = menu.containerId;
                record(s, "staged", "Actual server Contact postbox holds the exact unsent seed at native status 1; inventory move was consumed once");
            }
            s.before = entries(player, s);
            if (!s.accepted) {
                boolean changedProgress = s.tasks.keySet().stream().anyMatch(id -> s.data.getProgress(id) != 0);
                if (!s.before.isEmpty() || changedProgress) {
                    require(s.staged && player.containerMenu instanceof PostboxScreenHandler menu
                                    && menu.containerId == s.menuId && menu.parcel.getItem(0).isEmpty(),
                            "Acceptance did not consume the previously observed real postbox envelope/card");
                    requireConsumedInventory(player);
                    requireNoWrongMail(player, s);
                    if (s.ender) {
                        require(s.before.isEmpty(), "Ender mail created a delayed queue despite positive configuration");
                        requireProgress(s, true);
                    } else {
                        requireEntries(s, s.before, DELAY_TICKS);
                        requireProgress(s, false);
                    }
                    s.accepted = true;
                    s.acceptedTick = s.tick;
                    record(s, "accepted", s.ender
                            ? "Real ender send immediately credited exact quantities with zero queued deliveries; configuration remains positive"
                            : "Real normal send consumed one envelope/card, left progress zero and queued exact player/team/task/amount at " + DELAY_TICKS + " ticks");
                } else {
                    requireProgress(s, false);
                    require(s.tick - s.preparedTick < ACCEPT_TIMEOUT_TICKS,
                            "Real postbox send was not observed before the fixture timeout");
                }
            }
            if (s.accepted) {
                requireConsumedInventory(player);
                requireNoWrongMail(player, s);
                if (s.delivered || s.ender) {
                    require(s.before.isEmpty(), "A completed/immediate delivery was requeued");
                    requireProgress(s, true);
                } else {
                    int remaining = DELAY_TICKS - (int) (s.tick - s.acceptedTick);
                    require(remaining > 0, "Natural countdown passed its exact due tick");
                    requireEntries(s, s.before, remaining);
                    requireProgress(s, false);
                }
            }
        } catch (Exception | LinkageError error) {
            failObservation(s, error);
        }
    }

    /** After ContactQuests' real tick: assert every countdown transition and final amount. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterDelivery(ServerTickEvent.Post event) {
        var s = active;
        if (s == null || s.failed || !s.accepted || s.server != event.getServer()) return;
        try {
            var player = online(s);
            require(s.tick == event.getServer().getTickCount(), "Missing before-delivery observation for this real server tick");
            var after = entries(player, s);
            if (!s.delivered) {
                if (s.ender || s.before.getFirst().ticks == 1) {
                    require(!s.type.equals("deleted_task") || s.deleted, "Delete the accepted task before it becomes due");
                    require(after.isEmpty(), "Due/immediate mail retained or retried its accepted queue entries");
                    requireProgress(s, true);
                    require(s.ender || s.tick - s.acceptedTick == DELAY_TICKS - 1,
                            "Normal mail did not complete on exactly its configured countdown tick");
                    s.delivered = true;
                    s.dueTick = s.tick;
                    record(s, "due", s.deleted
                            ? "Real deleted task remained absent; accepted delivery drained naturally on its original due tick without phantom progress"
                            : "Exact quantities applied once; all " + s.amounts.size() + " entries drained in this real tick=" + s.tick
                                + (s.ender ? "; immediate, zero queue" : "; elapsed delivery ticks=" + (s.tick - s.acceptedTick + 1)));
                } else {
                    requireEntries(s, after, s.before.getFirst().ticks - 1);
                    requireProgress(s, false);
                }
            } else {
                require(after.isEmpty(), "A settled delivery appeared again in the queue");
                requireProgress(s, true);
            }
            requireNoWrongMail(player, s);
            if (s.delivered && !s.noRepeat && s.tick - s.dueTick >= REPEAT_WINDOW_TICKS) {
                s.noRepeat = true;
                record(s, "no_repeat", "Queue remained empty and exact progress/guard unchanged for "
                        + REPEAT_WINDOW_TICKS + " natural ticks after completion, beyond the configured delivery delay");
            }
        } catch (Exception | LinkageError error) {
            failObservation(s, error);
        }
    }

    private static void delete(ServerPlayer player, State s) {
        require(s.type.equals("deleted_task") && s.accepted && !s.delivered && !s.deleted && !s.failed,
                "Delete only this accepted in-flight normal delivery before its due tick");
        var pending = entries(player, s);
        require(pending.size() == 1 && pending.getFirst().ticks > 1, "Accepted delivery is no longer in flight");
        requireProgress(s, false);
        long taskId = s.tasks.keySet().iterator().next();
        require(QuestGraphService.INSTANCE.deleteTask(player.serverLevel(), Long.toUnsignedString(taskId, 16)),
                "Native quest graph did not delete the real task");
        s.deleted = true;
        require(ServerQuestFile.INSTANCE.getTask(taskId) == null && pending.equals(entries(player, s)),
                "Deletion changed accepted queue data or retained the actual task");
        requireProgress(s, false);
        record(s, "delete", "Actual native task deleted while " + pending.getFirst().ticks
                + " ticks remained; accepted player/team/quantity/countdown were preserved for normal due processing");
    }

    private static void verify(ServerPlayer player, State s) {
        require(!s.failed && s.staged && s.accepted && s.delivered && s.noRepeat,
                "All actual postbox/countdown/no-repeat milestones are required");
        require(entries(player, s).isEmpty(), "Owned deliveries remain queued after verification");
        requireProgress(s, true);
        requireConsumedInventory(player);
        requireNoWrongMail(player, s);
        s.verified = true;
        record(s, "verify", "Exact final progress, zero queue, preserved guard/inventory and no wrong-mail side effects verified; notification count is independently checked by the online client");
    }

    private static void finish(ServerPlayer player, State s) {
        require(player.containerMenu == player.inventoryMenu && player.containerMenu.getCarried().isEmpty(),
                "Close the real postbox before restoring the original inventory");
        require(entries(player, s).isEmpty(), "Never discard an accepted delivery during fixture cleanup");
        boolean verified = s.verified && !s.failed;
        if (s.chapter != null && ServerQuestFile.INSTANCE.getChapter(s.chapter.id) != null) {
            require(QuestGraphService.INSTANCE.deleteChapter(player.serverLevel(), s.chapter.getCodeString()),
                    "Could not remove the fixture's own native graph");
        }
        KubeJSNpcSavedData.Companion.get(player.serverLevel()).removeNpcData(s.npc);
        // Remove only names introduced by this case; keep every other current
        // native configuration, including work created by other fixtures.
        var retained = new LinkedHashSet<>(NpcConfigManager.INSTANCE.getAllNpcNames());
        retained.removeAll(s.createdNpcNames);
        NpcConfigManager.INSTANCE.removeUnusedNpcs(retained);
        ContactConfig.INSTANCE.getEnableDeliveryTime().set(s.originalDelay);
        player.getInventory().clearContent();
        for (int i = 0; i < s.originalInventory.size(); i++) player.getInventory().setItem(i, s.originalInventory.get(i).copy());
        player.getInventory().selected = s.originalSelected;
        player.getInventory().setChanged();
        player.connection.send(new ClientboundSetCarriedItemPacket(s.originalSelected));
        player.inventoryMenu.broadcastFullState();
        require(ContactConfig.INSTANCE.getEnableDeliveryTime().get() == s.originalDelay
                        && player.getInventory().selected == s.originalSelected, "Original configuration/selected slot was not restored");
        for (int i = 0; i < s.originalInventory.size(); i++) {
            require(ItemStack.matches(s.originalInventory.get(i), player.getInventory().getItem(i)),
                    "Original inventory components/counts were not restored at slot " + i);
        }
        require((s.chapter == null || ServerQuestFile.INSTANCE.getChapter(s.chapter.id) == null)
                        && KubeJSNpcSavedData.Companion.get(player.serverLevel()).getNpcData(s.npc) == null,
                "Fixture graph or positive NPC override survived cleanup");
        for (long id : s.tasks.keySet()) {
            require(ServerQuestFile.INSTANCE.getTask(id) == null && s.data.getProgress(id) == 0,
                    "Fixture task identity/progress survived native graph cleanup");
        }
        if (s.guard != null) {
            require(ServerQuestFile.INSTANCE.getTask(s.guard.id) == null && s.data.getProgress(s.guard.id) == 0,
                    "Fixture guard identity/progress survived native graph cleanup");
        }
        active = null;
        require(verified, "Original state restored, but the delivery boundary itself was not successfully verified");
        record(s, "finish", "Only this fixture graph/NPC override was removed; original full inventory and delivery flag restored");
    }

    private static State state(ServerPlayer player, String type) {
        var s = active;
        require(s != null && s.type.equals(type) && s.player.equals(player.getUUID()) && s.server == player.server,
                "Prepare this exact case/player/server first");
        return s;
    }

    private static ServerPlayer online(State s) {
        var player = s.server.getPlayerList().getPlayer(s.player);
        require(player != null, "Delivery notification boundary requires the original player to remain online");
        return player;
    }

    private static void requireContext(ServerPlayer player, State s) {
        var team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElseThrow();
        require(team.getId().equals(s.team) && !s.data.isLocked()
                        && ServerQuestFile.INSTANCE != null && ServerQuestFile.INSTANCE.getNullableTeamData(s.team) == s.data,
                "Delivery changed owning team, replaced team data or locked its progress");
        require(ContactConfig.INSTANCE.getEnableDeliveryTime().get()
                        && NpcConfigManager.INSTANCE.getDeliveryTime(s.npc, player.serverLevel()) == DELAY_TICKS
                        && NpcLlmConfig.INSTANCE.npc(s.npc) == null,
                "Positive configured delay/non-LLM receiver was changed during this boundary");
    }

    private static void requireProgress(State s, boolean settled) {
        var file = ServerQuestFile.INSTANCE;
        for (Map.Entry<Long, ContactTask> entry : s.tasks.entrySet()) {
            long id = entry.getKey();
            long expected = settled && !s.deleted ? s.amounts.get(id) : 0L;
            require(s.data.getProgress(id) == expected,
                    "Exact progress differs for " + Long.toUnsignedString(id, 16) + ": expected=" + expected + ", actual=" + s.data.getProgress(id));
            require(s.deleted ? file.getTask(id) == null : file.getTask(id) == entry.getValue() && !s.data.isCompleted(entry.getValue()),
                    "Task identity/completion changed during an incomplete delivery boundary");
        }
        require(file.getTask(s.guard.id) == s.guard && s.data.getProgress(s.guard) == 0,
                "An unrelated surviving task was credited or removed");
    }

    private static void requireConsumedInventory(ServerPlayer player) {
        require(player.getInventory().selected == 0 && player.containerMenu.getCarried().isEmpty(),
                "Selected slot/cursor changed during normal sending");
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var expected = i == 0 ? new ItemStack(Items.STICK, 7) : ItemStack.EMPTY;
            require(ItemStack.matches(player.getInventory().getItem(i), expected),
                    "Real send did not consume exactly one seed or changed the guard inventory at slot " + i);
        }
    }

    private static void requireNoWrongMail(ServerPlayer player, State s) {
        var collection = CollectionSavedData.Companion.get(player.serverLevel());
        require(collection.getTriggerCount(s.player, s.npc) == s.collectionCount,
                "Fully matched mail was incorrectly handed to wrong-mail collection/response handling");
        var stacks = collection.getStacks(s.player, s.npc);
        require(stacks.size() == s.collectionStacks.size(), "Fully matched mail changed the NPC's kept-item collection");
        for (int i = 0; i < stacks.size(); i++) {
            require(ItemStack.matches(stacks.get(i), s.collectionStacks.get(i)),
                    "Fully matched mail changed a kept item's count/components at slot " + i);
        }
        var queued = KubeJSMailQueueSavedData.Companion.get(player.serverLevel())
                .save(new CompoundTag(), player.registryAccess()).getList("PendingMails", Tag.TAG_COMPOUND);
        for (int i = 0; i < queued.size(); i++) {
            require(!s.npc.equals(queued.getCompound(i).getString("Recipient")),
                    "Fully matched mail leaked into the delayed unmatched-mail queue");
        }
    }

    private record Pending(UUID player, UUID team, long task, String target, long amount, int ticks) {}

    private static List<Pending> entries(ServerPlayer player, State s) {
        var tag = TaskDeliverySavedData.Companion.get(player.serverLevel()).save(new CompoundTag(), player.registryAccess());
        var list = tag.getList("PendingParcels", Tag.TAG_COMPOUND);
        var result = new ArrayList<Pending>();
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i);
            if (!s.npc.equals(entry.getString("target")) && !s.tasks.containsKey(entry.getLong("taskId"))) continue;
            require(entry.hasUUID("uuid") && entry.hasUUID("team"), "Real accepted delivery omitted its player/team identity");
            result.add(new Pending(entry.getUUID("uuid"), entry.getUUID("team"), entry.getLong("taskId"),
                    entry.getString("target"), entry.getLong("amount"), entry.getInt("ticks")));
        }
        return List.copyOf(result);
    }

    private static void requireEntries(State s, List<Pending> actual, int ticks) {
        require(actual.size() == s.amounts.size(), "Wrong pending entry count: expected=" + s.amounts.size() + ", actual=" + actual);
        var seen = new LinkedHashSet<Long>();
        for (Pending entry : actual) {
            require(seen.add(entry.task) && s.amounts.containsKey(entry.task) && entry.amount == s.amounts.get(entry.task)
                            && entry.player.equals(s.player) && entry.team.equals(s.team) && entry.target.equals(s.npc)
                            && entry.ticks == ticks,
                    "Pending quantity/identity/exact natural countdown differs: expected ticks=" + ticks + ", actual=" + entry);
        }
        var queue = TaskDeliverySavedData.Companion.get(s.server.overworld());
        for (long id : s.amounts.keySet()) {
            require(queue.getPendingAmount(s.team, id) == s.amounts.get(id), "Pending capacity accounting differs from serialized accepted quantity");
        }
    }

    private static void failObservation(State s, Throwable error) {
        String phase = !s.accepted ? "accepted" : !s.delivered ? "due" : "no_repeat";
        s.failed = true;
        ContactQuestsTestServerController.recordCase(caseId(s.type, phase), false, error.toString());
    }

    private static void record(State s, String phase, String detail) {
        ContactQuestsTestServerController.recordCase(caseId(s.type, phase), true, detail);
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }

    private static final class State {
        final String type;
        final String npc;
        final UUID player;
        final UUID team;
        final MinecraftServer server;
        final TeamData data;
        final boolean ender;
        final boolean postcard;
        final boolean originalDelay;
        final int originalSelected;
        final List<ItemStack> originalInventory = new ArrayList<>();
        final LinkedHashMap<Long, ContactTask> tasks = new LinkedHashMap<>();
        final LinkedHashMap<Long, Long> amounts = new LinkedHashMap<>();
        final LinkedHashSet<String> createdNpcNames = new LinkedHashSet<>();
        final long preparedTick;
        Chapter chapter;
        ParcelTask guard;
        ItemStack mail;
        int collectionCount;
        List<ItemStack> collectionStacks = List.of();
        int menuId;
        long tick;
        long acceptedTick;
        long dueTick;
        List<Pending> before = List.of();
        boolean staged;
        boolean accepted;
        boolean deleted;
        boolean delivered;
        boolean noRepeat;
        boolean verified;
        boolean failed;

        State(String type, ServerPlayer player, TeamData data) {
            this.type = type;
            this.npc = recipient(type);
            this.player = player.getUUID();
            this.team = data.getTeamId();
            this.server = player.server;
            this.data = data;
            this.ender = type.startsWith("ender_");
            this.postcard = type.equals("postcard") || type.equals("ender_postcard");
            this.originalDelay = ContactConfig.INSTANCE.getEnableDeliveryTime().get();
            this.originalSelected = player.getInventory().selected;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) originalInventory.add(player.getInventory().getItem(i).copy());
            this.preparedTick = server.getTickCount();
        }
    }
}
