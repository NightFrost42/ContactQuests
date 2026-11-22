package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.client.gui.BaseValidTaskScreen;
import com.creeperyang.contactquests.client.util.ParcelAutoFiller;
import com.creeperyang.contactquests.client.util.PostcardAutoFiller;
import com.creeperyang.contactquests.client.util.RedPacketAutoFiller;
import com.creeperyang.contactquests.compat.kubejs.KubeJSNpcSavedData;
import com.creeperyang.contactquests.config.ContactConfig;
import com.creeperyang.contactquests.config.NpcConfigManager;
import com.creeperyang.contactquests.data.DataManager;
import com.creeperyang.contactquests.mixin.PostcardEditScreenAccessor;
import com.creeperyang.contactquests.mixin.RedPacketEnvelopeScreenAccessor;
import com.creeperyang.contactquests.quest.task.ContactTask;
import com.creeperyang.contactquests.quest.task.ItemMatchingTask;
import com.creeperyang.contactquests.quest.task.ParcelTask;
import com.creeperyang.contactquests.quest.task.PostcardTask;
import com.creeperyang.contactquests.quest.task.RedPacketTask;
import com.creeperyang.contactquests.utils.ContactMailNbt;
import com.flechazo.contact.client.gui.screen.EnvelopeScreen;
import com.flechazo.contact.client.gui.screen.PostboxScreen;
import com.flechazo.contact.client.gui.screen.PostcardEditScreen;
import com.flechazo.contact.client.gui.screen.RedPacketEnvelopeScreen;
import com.flechazo.contact.client.gui.screen.WrappingPaperScreen;
import com.flechazo.contact.common.item.ParcelItem;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.common.item.RedPacketItem;
import com.flechazo.contact.common.screenhandler.EnvelopeScreenHandler;
import com.flechazo.contact.common.screenhandler.PostboxScreenHandler;
import com.flechazo.contact.common.screenhandler.RedPacketEnvelopeScreenHandler;
import com.flechazo.contact.common.screenhandler.WrappingPaperScreenHandler;
import dev.ftb.mods.ftblibrary.ui.IScreenWrapper;
import dev.ftb.mods.ftblibrary.ui.WidgetType;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Real Windows client scenarios, after install_synthetic_tasks in the integrated test world.
 * Dispatch run(step.get("phase").getAsString()); on false rewind the step and retry in 1-5 ticks.
 * Each PHASES entry is one complete scenario and records client.autofill.lifecycle.<phase> once,
 * only after verification and restoration. No separate seed/snapshot/cleanup actions are needed.
 *
 * The controller's ordinary TaskButton entry is reused; Submit/Seal/Done are actual screen clicks.
 * Filler fields are read for assertions only: no schedule(), tick(), finishTask() or private filler
 * invocation. Per-tick observers retain the 12 -> cursor -> 4/4/1 -> remainder 3 trajectory even
 * with a five-tick controller retry. Server listeners observe real packet-applied slot changes.
 * All inventory/game-mode mutations and server snapshots run in integrated-server futures.
 *
 * Reopen is ESC followed by selecting the second stationery slot and useItem in the SAME client
 * tick, without another Submit. Require a different Screen/container within ten ticks, cancellation
 * within two ticks of that new screen, and 24 ticks with no stale changes (below the old timeout).
 * Close-only cases use the same native ESC, require cancellation within two ticks of closing,
 * and verify the exact returned client/server inventory after 24 ticks without reopening.
 * The current old Base is expected to fail these cases; never reset its fields to pass them.
 *
 * parcel_shared_prefilled uses genuine four-slot wrapping paper: two client synthetic parcel
 * tasks for one isolated recipient, counts 3+5, and native pickup/right-click prefill of 2 from 12.
 * It verifies actual final input total 8, remainder 4, not delivery or per-task credited progress.
 * red_packet_empty configures EMPTY through the public task setter before the real TaskButton;
 * a blocked entry is a failure, never bypassed by constructing its submission screen.
 */
@EventBusSubscriber(modid = ContactQuestsTestMod.MOD_ID, value = Dist.CLIENT)
public final class ContactQuestsAutofillLifecycleAssertions {
    public static final List<String> PHASES = List.of(
            "parcel_multitick", "red_packet_multitick", "wrapping_multitick",
            "parcel_close_pickup", "red_packet_close_deposit",
            "parcel_reopen_pickup", "parcel_reopen_deposit",
            "red_packet_reopen_pickup", "red_packet_reopen_deposit", "postcard_reopen",
            "parcel_shared_prefilled", "postcard_intervention", "red_packet_empty",
            "parcel_shared_send", "parcel_midflight_mutation");
    private static final String PREFIX = "client.autofill.lifecycle.";
    private static final String TEXT = "Lifecycle postcard: keep this draft on its own card.";
    private static final String BLESSING = "Lifecycle blessing without lost items";
    private static final long EXTRA_TASK_ID = 0x7f00000000000171L;
    private static final Set<String> completed = new java.util.HashSet<>();
    private static volatile Session active;
    private static int ticks;

    private ContactQuestsAutofillLifecycleAssertions() {}

    private enum Stage { SEED, OPEN, SUBMIT, FILL, PACKED, POSTBOX_OPEN, POSTBOX_QUERY, POSTBOX_SEND, CLOSED, REOPEN, INTERVENTION, SAVED, VERIFY, CLOSE, RESTORE }

    /** false means asynchronous work/real ticks remain; true means this phase is recorded. */
    public static boolean run(String phase) {
        if (completed.contains(phase)) return true;
        try {
            require(PHASES.contains(phase), "Unknown lifecycle phase: " + phase);
            if (active == null) {
                active = new Session(phase);
                configure(active);
                Session s = active;
                s.pending = s.server.submit(() -> seed(s));
                return false;
            }
            Session s = active;
            require(s.phase.equals(phase), "Finish " + s.phase + " before starting " + phase);
            if (s.stage == Stage.CLOSE || s.stage == Stage.RESTORE) return restore(s);
            if (s.failure != null) { beginClose(s); return false; }
            require(ticks - s.started < 400 && System.nanoTime() - s.startedNanos < 45_000_000_000L,
                    "Lifecycle timeout at " + s.stage + "; trace=" + s.clientTrace);
            advance(s);
            return false;
        } catch (Exception | LinkageError error) {
            Session s = active;
            if (s == null) {
                ContactQuestsClientTestController.assertCase(PREFIX + phase, false, error.toString());
                completed.add(phase);
                return true;
            }
            if (s.failure == null) s.failure = error;
            else if (s.failure != error) s.failure.addSuppressed(error);
            if (s.stage == Stage.CLOSE || s.stage == Stage.RESTORE) return record(s, "Restoration failed: " + error);
            beginClose(s);
            return false;
        }
    }

    private static void advance(Session s) throws Exception {
        var mc = Minecraft.getInstance();
        switch (s.stage) {
            case SEED -> {
                if (!s.pending.isDone()) return;
                s.seeded = s.pending.join();
                select(0);
                if (!sameStacks(s.seeded.inventory, inventory(mc.player)) || mc.player.getAbilities().instabuild) return;
                require(s.seeded.cursor.isEmpty(), "Seed cursor is not empty");
                s.clientProgress = progress(ClientQuestFile.INSTANCE.selfTeamData, s);
                s.stage = Stage.OPEN;
            }
            case OPEN -> {
                invokeOpen(s.type);
                require(mc.screen instanceof IScreenWrapper wrapper && wrapper.getGui() instanceof BaseValidTaskScreen<?>,
                        "Actual TaskButton did not open submission; empty-item tasks must use this same entry");
                s.submission = mc.screen;
                s.stage = Stage.SUBMIT;
            }
            case SUBMIT -> {
                require(mc.screen == s.submission, "Submission screen changed");
                var valid = (BaseValidTaskScreen<?>) ((IScreenWrapper) mc.screen).getGui();
                require(valid.getTask() == s.task && !ClientQuestFile.INSTANCE.canEdit()
                                && valid.getSubmitButton().getWidgetType() != WidgetType.DISABLED
                                && valid.getSubmitButton().getPosX() > valid.getBackButton().getPosX(),
                        "Actual right-hand Submit is not ready for this task");
                var button = valid.getSubmitButton();
                click(mc.screen, button.getX(), button.getY(), button.getWidth(), button.getHeight());
                require(scheduled(s), "Real Submit did not schedule autofill");
                s.stage = Stage.FILL;
            }
            case FILL -> {
                if (s.firstScreen == null || scheduled(s)) return;
                require(!s.closeOnly && !s.reopen && !s.intervention, "Filler finished before the required interruption");
                require(read(s.filler, "taskData") == null, "Finished filler retained task data");
                Snapshot client = snapshot(mc.player, s);
                if (s.empty) {
                    verifyTransfer(s, client, 0, 0, s.seedCount);
                    require(BLESSING.equals(((RedPacketEnvelopeScreenAccessor) mc.screen)
                            .contactQuests$getBlessingsBox().getValue()), "Empty-item blessing was not typed");
                } else if (s.shared) {
                    require(s.prefilled, "Fixture preparation failed: native prefill was never performed");
                    verifySharedTasks(s, "after autofill");
                    verifyTransfer(s, client, 8, 0, 4);
                } else if (s.sharedSend) {
                    verifySharedTasks(s, "after autofill");
                    verifyTransfer(s, client, 6, 0, 0);
                    clickButton(mc.screen, "buttonPack");
                    s.stage = Stage.PACKED;
                    return;
                } else if (s.midflightMutation) {
                    require(s.mutated, "Mid-flight mutation never triggered");
                    verifyTransfer(s, client, 4, 0, 8);
                } else verifyMultitick(s);
                s.pending = serverSnapshot(s);
                s.stage = Stage.VERIFY;
            }
            case PACKED -> {
                if (mc.screen != null || mc.player.containerMenu != mc.player.inventoryMenu) return;
                boolean hasPacked = false;
                for (ItemStack stack : mc.player.getInventory().items) {
                    if (stack.getItem() instanceof ParcelItem) {
                        int diamonds = ContactMailNbt.INSTANCE.readContents(stack)
                                .stream().filter(i -> i.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();
                        if (diamonds == 6) {
                            hasPacked = true;
                            break;
                        }
                    }
                }
                if (!hasPacked) return;
                mc.player.connection.sendCommand("contactquests_test open_postbox");
                s.stage = Stage.POSTBOX_OPEN;
            }
            case POSTBOX_OPEN -> {
                if (!(mc.screen instanceof PostboxScreen postbox)) return;
                PostboxScreenHandler handler = postbox.getMenu();
                if (handler == null || mc.player.containerMenu != handler) return;
                if (!s.postboxMoved) {
                    for (int slot = 1; slot < handler.slots.size(); slot++) {
                        if (handler.getSlot(slot).getItem().getItem() instanceof ParcelItem) {
                            mc.gameMode.handleInventoryMouseClick(handler.containerId, slot, 0, ClickType.QUICK_MOVE, mc.player);
                            s.postboxMoved = true;
                            return;
                        }
                    }
                    return;
                }
                if (handler.parcel.getItem(0).isEmpty()) return;
                if (handler.status == 0) handler.status = 1;
                ((EditBox) read(postbox, "nameField")).setValue(s.recipient);
                Method whileTyping = PostboxScreen.class.getDeclaredMethod("whileTyping", String.class);
                whileTyping.setAccessible(true);
                whileTyping.invoke(postbox, s.recipient);
                mc.player.connection.sendCommand("contactquests_test query_recipient " + s.recipient);
                s.stage = Stage.POSTBOX_QUERY;
            }
            case POSTBOX_QUERY -> {
                if (!(mc.screen instanceof PostboxScreen postbox)) return;
                PostboxScreenHandler handler = postbox.getMenu();
                if (handler == null || handler.names.isEmpty()) return;
                require(s.recipient.equals(handler.names.get(0)), "Unexpected recipient suggestion: " + handler.names);
                ((EditBox) read(postbox, "nameField")).setValue(handler.names.get(0));
                Method send = PostboxScreen.class.getDeclaredMethod("send");
                send.setAccessible(true);
                send.invoke(postbox);
                s.stage = Stage.POSTBOX_SEND;
            }
            case POSTBOX_SEND -> {
                if (!(mc.screen instanceof PostboxScreen postbox)) return;
                PostboxScreenHandler handler = postbox.getMenu();
                if (handler == null || !handler.parcel.getItem(0).isEmpty()) return;
                mc.player.closeContainer();
                mc.setScreen(null);
                s.pending = serverSnapshot(s);
                s.stage = Stage.VERIFY;
            }
            case CLOSED -> {
                if (ticks - s.interruptedAt < 24) return;
                requireIdle(s);
                s.pending = serverSnapshot(s);
                s.stage = Stage.VERIFY;
            }
            case REOPEN -> {
                if (s.reopenedAt < 0 || ticks - s.reopenedAt < 24) return;
                requireIdle(s);
                s.pending = serverSnapshot(s);
                s.stage = Stage.VERIFY;
            }
            case INTERVENTION -> {
                if (ticks - s.interruptedAt < 24) return;
                requireIdle(s);
                require(s.editedText.equals(page()), "Old autofill changed the manually edited draft");
                clickButton(mc.screen, "buttonDone");
                require(mc.screen == null, "Native Done did not close the postcard");
                s.stage = Stage.SAVED;
            }
            case SAVED -> {
                // Contact's Seal closes the native menu only after the server processes
                // ActionMessage. Never click twice while that packet is in flight.
                if (s.empty && (mc.screen != null || mc.player.containerMenu != mc.player.inventoryMenu)) return;
                s.pending = serverSnapshot(s);
                s.stage = Stage.VERIFY;
            }
            case VERIFY -> {
                if (!s.pending.isDone()) return;
                Snapshot server = s.pending.join(), client = snapshot(mc.player, s);
                // An executor task may precede a native click packet. Poll for synchronization;
                // retained server traces still reject transient bad input even if later restored.
                if (!sameState(client, server) || !savedResultArrived(s, server)) {
                    s.pending = serverSnapshot(s);
                    return;
                }
                require(s.seeded.progress.equals(server.progress) && s.clientProgress.equals(client.progress),
                        "Filling changed task/quest progress without sending mail");
                require(sameDrops(s.seeded.drops, server.drops), "Native operation dropped/lost items");
                requireIdle(s);
                if (s.closeOnly) verifyClosed(s, client, server);
                else if (s.reopen) verifyReopen(s, client, server);
                else if (s.intervention) {
                    List<ItemStack> expected = copy(s.seeded.inventory);
                    expected.set(0, PostcardItem.setText(expected.get(0).copy(), s.editedText));
                    require(sameStacks(expected, client.inventory) && client.cursor.isEmpty(),
                            "Done did not save only the exact manual draft on both cards' owning inventories");
                } else if (s.empty && mc.screen == null) {
                    verifySealedEmpty(s, client);
                } else if (s.sharedSend) {
                    require(List.of(3L, 3L).equals(server.sharedProgress())
                                    && count(server.inventory, s.good) == 0
                                    && count(server.inventory, Items.DIRT) == 3
                                    && count(server.inventory, Items.STICK) == 7,
                            "Shared parcel send did not settle 3/3 and 3/5 cleanly: " + server.sharedProgress());
                    verifyServerTrace(s);
                } else if (s.midflightMutation) {
                    verifyTransfer(s, server, 4, 0, 8);
                    verifyServerTrace(s);
                } else {
                    verifyTransfer(s, server, s.empty ? 0 : s.shared ? 8 : 9, 0, s.empty ? 12 : s.shared ? 4 : 3);
                    verifyServerTrace(s);
                    if (s.empty) {
                        require(((RedPacketEnvelopeScreenHandler) mc.player.containerMenu).blessings.equals(BLESSING),
                                "Empty-item client menu blessing differs");
                        clickButton(mc.screen, "buttonPack");
                        s.stage = Stage.SAVED;
                        return;
                    }
                }
                s.verified = true;
                beginClose(s);
            }
            default -> throw new IllegalStateException("Unexpected stage " + s.stage);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeFill(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        Session s = active;
        if (s == null || s.failure != null || s.stage != Stage.FILL || !nativeScreen(s)) return;
        try {
            var mc = Minecraft.getInstance();
            if (s.firstScreen == null) {
                s.firstScreen = mc.screen;
                s.firstMenuId = mc.player.containerMenu.containerId;
                require(s.postcard || s.firstMenuId != 0, "Native container ID must be nonzero");
            }
            if (s.shared && !s.prefilled) {
                verifySharedPreparation(s, snapshot(mc.player, s));
                // Contact's first menu packet can precede its survival paper-consumption update.
                if (!mc.player.getInventory().getItem(0).isEmpty()) return;
                var menu = mc.player.containerMenu;
                require(menu instanceof WrappingPaperScreenHandler, "Fixture preparation failed: wrong shared-task menu");
                int source = -1;
                for (int i = 4; i < menu.slots.size(); i++)
                    if (menu.getSlot(i).container == mc.player.getInventory() && menu.getSlot(i).getContainerSlot() == 9) source = i;
                require(source >= 0, "Fixture preparation failed: native inventory source slot missing");
                mc.gameMode.handleInventoryMouseClick(menu.containerId, source, 0, ClickType.PICKUP, mc.player);
                mc.gameMode.handleInventoryMouseClick(menu.containerId, 0, 1, ClickType.PICKUP, mc.player);
                mc.gameMode.handleInventoryMouseClick(menu.containerId, 0, 1, ClickType.PICKUP, mc.player);
                mc.gameMode.handleInventoryMouseClick(menu.containerId, source, 0, ClickType.PICKUP, mc.player);
                try { verifyTransfer(s, snapshot(mc.player, s), 2, 0, 10); }
                catch (IllegalStateException error) {
                    throw new IllegalStateException("Fixture preparation failed: native prefill 2 from 12; " + error, error);
                }
                verifySharedTasks(s, "after native prefill");
                s.prefilled = true;
            }
        } catch (Exception | LinkageError error) { s.failure = error; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterFill(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Session s = active;
        if (s == null || s.failure != null) return;
        try {
            var mc = Minecraft.getInstance();
            if (s.stage == Stage.FILL && s.firstScreen != null) {
                require(mc.screen == s.firstScreen, "Original native screen changed before interruption");
                if (s.postcard) {
                    String text = page();
                    if (text.length() >= 2) {
                        require(text.equals(TEXT.substring(0, 2)) && scheduled(s), "Expected an active two-character draft, actual=" + text);
                        s.partialText = text;
                        if (s.reopen) interrupt(s);
                        else {
                            require(mc.screen.charTyped('!', 0), "Native postcard input rejected manual character");
                            s.editedText = text + "!";
                            require(page().equals(s.editedText), "Manual character did not enter native draft at its cursor");
                            s.interruptedAt = ticks;
                            s.stage = Stage.INTERVENTION;
                        }
                    }
                } else {
                    Snapshot state = snapshot(mc.player, s);
                    if (s.shared && !s.prefilled) {
                        verifySharedPreparation(s, state);
                        return;
                    }
                    int input = count(state.inputs, s.good), cursor = state.cursor.getCount();
                    require(count(state.inventory, s.good) + input + (state.cursor.is(s.good) ? cursor : 0) == s.seedCount,
                            "Client transfer conservation failed: " + brief(state));
                    if (!s.shared && !s.sharedSend && !s.empty) {
                        int inv = count(state.inventory, s.good);
                        if (s.clientTrace.isEmpty() || !sameMaterial(s.clientTrace.get(s.clientTrace.size() - 1).state, state, s.good))
                            s.clientTrace.add(new Frame(ticks, state));
                        if (cursor > 0) {
                            require(scheduled(s), "Filler became idle while holding items");
                            require(List.of(0, 4, 8, 9).contains(input), "Unexpected per-tick deposit count " + input);
                            require(inv == 0 && cursor == s.seedCount - input, "Wrong active cursor/remainder");
                        }
                        if (s.midflightMutation && !s.mutated && input == 4 && cursor == s.seedCount - 4) {
                            ((ParcelTask) s.task).setItemStack(new ItemStack(Items.EMERALD));
                            s.mutated = true;
                        }
                        if ((s.closeOnly || s.reopen) && input == (s.phase.endsWith("pickup") ? 0 : 4) && cursor == s.seedCount - input) {
                            verifyTransfer(s, state, input, cursor, 0);
                            interrupt(s);
                        }
                    }
                }
            } else if (s.closeOnly && (s.stage == Stage.CLOSED || s.stage == Stage.VERIFY)) {
                require(mc.screen == null && mc.player.containerMenu == mc.player.inventoryMenu
                                && mc.player.containerMenu.getCarried().isEmpty(),
                        "Close-only interruption reopened a screen/menu or changed the inventory cursor");
                require(s.clientProgress.equals(progress(ClientQuestFile.INSTANCE.selfTeamData, s)),
                        "Close-only interruption changed client task/quest progress");
                if (ticks - s.interruptedAt >= 2) requireIdle(s);
            } else if (s.stage == Stage.REOPEN) {
                if (!nativeScreen(s)) {
                    require(ticks - s.interruptedAt <= 10, "Immediate reopen did not reach a native screen within ten ticks");
                    return;
                }
                require(mc.screen != s.firstScreen && (s.postcard || mc.player.containerMenu.containerId != s.firstMenuId),
                        "Reopen reused the original screen/container");
                if (s.reopenedAt < 0) {
                    require(ticks - s.interruptedAt <= 10, "Reopen occurred after the immediate-reopen bound");
                    s.reopenedAt = ticks;
                    s.secondScreen = mc.screen;
                }
                require(mc.screen == s.secondScreen, "Second native screen changed");
                if (s.postcard) require(page().isEmpty(), "Old typing resumed on the other postcard: " + page());
                else {
                    Snapshot state = snapshot(mc.player, s);
                    require(state.inputs.stream().allMatch(ItemStack::isEmpty) && state.cursor.isEmpty(),
                            "Old transfer touched new container/cursor: " + brief(state));
                }
                if (ticks - s.reopenedAt >= 2) requireIdle(s);
            } else if (s.stage == Stage.INTERVENTION) {
                require(mc.screen == s.firstScreen && s.editedText.equals(page()),
                        "Old typing appended/overwrote a character after native input intervention: " + page());
                if (ticks - s.interruptedAt >= 4) requireIdle(s);
            }
        } catch (Exception | LinkageError error) { s.failure = error; }
    }

    /** Read-only listener attached on the real server open, before any autofill click packet. */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        Session s = active;
        if (s == null || s.postcard || !(event.getEntity() instanceof ServerPlayer player)
                || !player.getUUID().equals(s.owner) || !nativeMenu(s, event.getContainer())) return;
        AbstractContainerMenu menu = event.getContainer();
        ContainerListener listener = new ContainerListener() {
            @Override public void slotChanged(AbstractContainerMenu changed, int slot, ItemStack stack) {
                // No fabricated snapshots: this runs after a native server-side slot transaction.
                if (active == s && s.stage != Stage.CLOSE && s.stage != Stage.RESTORE) {
                    try { s.serverTrace.add(snapshot(player, s)); }
                    catch (Exception error) { s.failure = error; }
                }
            }
            @Override public void dataChanged(AbstractContainerMenu changed, int slot, int value) {}
        };
        s.listeners.add(new ObservedMenu(menu, listener));
        try {
            menu.addSlotListener(listener);
            s.serverTrace.add(snapshot(player, s));
        } catch (Exception error) { s.failure = error; }
    }

    private static void interrupt(Session s) throws Exception {
        var mc = Minecraft.getInstance();
        require(scheduled(s) && mc.screen == s.firstScreen, "Interruption requires an active original fill");
        s.interruptedAt = ticks;
        s.stage = s.closeOnly ? Stage.CLOSED : Stage.REOPEN;
        require(mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0), "Native ESC was not consumed");
        require(mc.screen == null && mc.player.containerMenu == mc.player.inventoryMenu, "Native ESC did not close");
        if (s.closeOnly) return;
        require(ItemStack.matches(mc.player.getInventory().getItem(1), s.seeded.inventory.get(1)),
                "Second real stationery was unavailable before reopen");
        select(1);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        require(ticks == s.interruptedAt, "Reopen must be requested in the same client tick as close");
    }

    private static void verifyMultitick(Session s) {
        int previousTick = -1, index = 0;
        for (int[] point : List.of(new int[]{0, 12, 0}, new int[]{4, 8, 0}, new int[]{8, 4, 0},
                new int[]{9, 3, 0}, new int[]{9, 0, 3})) {
            while (index < s.clientTrace.size() && !at(s.clientTrace.get(index).state, s, point)) index++;
            require(index < s.clientTrace.size(), "Missing real multi-tick checkpoint " + java.util.Arrays.toString(point) + ": " + s.clientTrace);
            Frame frame = s.clientTrace.get(index++);
            require(frame.tick > previousTick, "Pickup/4/4/1/return did not span distinct real client ticks");
            previousTick = frame.tick;
            verifyTransfer(s, frame.state, point[0], point[1], point[2]);
        }
    }

    private static void verifyServerTrace(Session s) {
        List<Snapshot> trace = new ArrayList<>(s.serverTrace);
        int index = 0;
        List<int[]> points = s.empty ? List.of(new int[]{0, 0, s.seedCount})
                : s.shared ? List.of(new int[]{2, 0, 10}, new int[]{8, 0, 4})
                : s.sharedSend ? List.of(new int[]{0, 6, 0}, new int[]{3, 3, 0}, new int[]{3, 0, 3}, new int[]{6, 0, 0})
                : s.midflightMutation ? List.of(new int[]{0, 12, 0}, new int[]{4, 8, 0}, new int[]{4, 0, 8})
                : List.of(new int[]{0, 12, 0}, new int[]{4, 8, 0}, new int[]{8, 4, 0}, new int[]{9, 3, 0}, new int[]{9, 0, 3});
        for (int[] point : points) {
            while (index < trace.size() && !(trace.get(index).menuId == s.firstMenuId && at(trace.get(index), s, point))) index++;
            require(index < trace.size(), "Native server never applied checkpoint " + java.util.Arrays.toString(point));
            Snapshot state = trace.get(index++);
            // Empty-item initial open precedes paper consumption; all transfer checkpoints
            // otherwise include exact full inventory, input slots, components and cursor.
            if (!s.empty) verifyTransfer(s, state, point[0], point[1], point[2]);
            else require(count(state.inventory, Items.DIRT) == 3 && count(state.inventory, Items.STICK) == 7,
                    "Server transaction moved unrelated inventory");
        }
        if (s.midflightMutation) {
            require(trace.stream().noneMatch(st -> st.menuId == s.firstMenuId && count(st.inputs, s.good) > 4),
                    "Filler deposited extra items after mid-flight task mutation");
        }
    }

    private static void verifyTransfer(Session s, Snapshot state, int input, int cursor, int remaining) {
        require(at(state, s, new int[]{input, cursor, remaining}), "Wrong material split: " + brief(state));
        require(state.inputs.size() == (s.wrapping ? 4 : 1)
                        && state.inputs.stream().allMatch(stack -> stack.isEmpty() || stack.is(s.good)),
                "Unexpected native input slots/items");
        if (!s.shared && !s.sharedSend) {
            require(state.inputs.get(0).getCount() == input && state.inputs.stream().skip(1).allMatch(ItemStack::isEmpty),
                    "Transfer spread into unexpected wrapping slots");
        }
        List<ItemStack> expected = copy(s.seeded.inventory);
        expected.set(0, ItemStack.EMPTY); // survival use consumed the first stationery into the native menu
        expected.set(9, remaining == 0 ? ItemStack.EMPTY : new ItemStack(s.good, remaining));
        require(sameStacks(expected, state.inventory) && state.selected == 0,
                "Inventory slot/component mismatch at material checkpoint: " + brief(state));
        require(input + cursor + remaining == s.seedCount && (cursor == 0 || state.cursor.is(s.good)), "Conservation/cursor item mismatch");
    }

    private static void verifyReopen(Session s, Snapshot client, Snapshot server) {
        require(s.secondScreen != null && ticks - s.reopenedAt >= 24, "No stable immediate reopened screen");
        List<ItemStack> expected = copy(s.seeded.inventory);
        if (!s.postcard) {
            expected.set(0, new ItemStack(s.good, 12)); // native close returns contents/cursor to first free slot
            expected.set(9, ItemStack.EMPTY);
            // Returned first paper stacks with slot 1; using the second leaves exactly one paper.
            require(client.menuId != s.firstMenuId && client.inputs.stream().allMatch(ItemStack::isEmpty), "New input changed");
            int[] interrupted = s.phase.endsWith("pickup") ? new int[]{0, 12, 0} : new int[]{4, 8, 0};
            require(s.serverTrace.stream().anyMatch(state -> state.menuId == s.firstMenuId && at(state, s, interrupted)),
                    "Server never received the actual pre-close pickup/deposit");
            for (Snapshot state : s.serverTrace) if (state.menuId == client.menuId) {
                require(state.inputs.stream().allMatch(ItemStack::isEmpty) && state.cursor.isEmpty(),
                        "A stale packet touched the server's new menu: " + brief(state));
            }
            require(s.serverTrace.stream().anyMatch(state -> state.menuId == client.menuId),
                    "No server observation of the second native menu");
            if (s.type.equals("red_packet")) require(((RedPacketEnvelopeScreenAccessor) Minecraft.getInstance().screen)
                            .contactQuests$getBlessingsBox().getValue().isEmpty(),
                    "Old blessing resumed on the second red packet");
        } else require(s.partialText.equals(TEXT.substring(0, 2)) && pageUnchecked().isEmpty(), "Missing interrupted draft/new blank page");
        require(client.selected == 1 && client.cursor.isEmpty() && sameStacks(expected, client.inventory)
                        && sameStacks(expected, server.inventory),
                "Immediate close/reopen lost material, changed stationery or moved unrelated inventory: " + brief(client));
    }

    private static void verifyClosed(Session s, Snapshot client, Snapshot server) {
        var mc = Minecraft.getInstance();
        require(ticks - s.interruptedAt >= 24 && mc.screen == null && mc.player.containerMenu == mc.player.inventoryMenu,
                "Close-only case did not remain closed for 24 ticks");
        int[] interrupted = s.phase.endsWith("pickup") ? new int[]{0, 12, 0} : new int[]{4, 8, 0};
        Snapshot applied = s.serverTrace.stream().filter(state -> state.menuId == s.firstMenuId && at(state, s, interrupted))
                .findFirst().orElseThrow(() -> new IllegalStateException("Server never applied the close-only pickup/deposit"));
        verifyTransfer(s, applied, interrupted[0], interrupted[1], interrupted[2]);
        List<ItemStack> expected = copy(s.seeded.inventory);
        expected.set(0, new ItemStack(s.good, 12));
        expected.get(1).grow(s.seeded.inventory.get(0).getCount()); // return the first paper; do not use the second
        expected.set(9, ItemStack.EMPTY);
        require(client.menuId == 0 && server.menuId == 0 && client.inputs.isEmpty() && server.inputs.isEmpty()
                        && client.cursor.isEmpty() && server.cursor.isEmpty() && client.selected == 0 && server.selected == 0
                        && sameStacks(expected, client.inventory) && sameStacks(expected, server.inventory),
                "Native ESC lost material/paper or changed unrelated inventory: client=" + brief(client) + "; server=" + brief(server));
        require(sameDrops(s.seeded.drops, client.drops), "Close-only interruption left unexpected client drops");
    }

    private static void verifySealedEmpty(Session s, Snapshot state) {
        List<ItemStack> expected = copy(s.seeded.inventory);
        ItemStack packed = state.inventory.get(0);
        require(packed.getItem() instanceof RedPacketItem && packed.getCount() == 1
                        && BLESSING.equals(ContactMailNbt.INSTANCE.getBlessing(packed))
                        && ContactMailNbt.INSTANCE.readContents(packed).stream().allMatch(ItemStack::isEmpty),
                "Native Seal did not persist one empty red packet with exact blessing");
        expected.set(0, packed.copy());
        require(sameStacks(expected, state.inventory) && state.cursor.isEmpty() && state.menuId == 0,
                "Sealing empty red packet moved material/unrelated items");
    }

    private static boolean savedResultArrived(Session s, Snapshot state) {
        if (s.intervention) return s.editedText.equals(PostcardItem.getText(state.inventory.get(0)));
        if (s.empty && Minecraft.getInstance().screen == null)
            return state.menuId == 0 && state.inventory.get(0).getItem() instanceof RedPacketItem;
        if (s.sharedSend) return state.menuId == 0 && List.of(3L, 3L).equals(state.sharedProgress);
        return true;
    }

    private static void configure(Session s) {
        if (s.task instanceof ItemMatchingTask itemTask) {
            itemTask.setItemStack(s.empty ? ItemStack.EMPTY : new ItemStack(s.good));
            s.task.setCount((s.shared || s.sharedSend) ? 3 : s.empty ? 1 : 9);
        }
        s.task.setTargetAddressee(s.recipient);
        if (s.task instanceof RedPacketTask red) red.setBlessing(BLESSING);
        if (s.task instanceof PostcardTask card) {
            card.setPostcardStyle("contact:default");
            card.setPostcardText(TEXT);
            ContactConfig.INSTANCE.getAutoFillSpeed().set(2);
        }
        if (s.shared || s.sharedSend) {
            require(ClientQuestFile.INSTANCE.getTask(EXTRA_TASK_ID) == null, "Fixture preparation failed: shared task ID already exists");
            s.extra = new ParcelTask(EXTRA_TASK_ID, s.task.getQuest()).setStackAndCount(new ItemStack(Items.DIAMOND), 5);
            s.extra.setTargetAddressee(s.task.getTargetAddressee());
            s.task.getQuest().addTask(s.extra);
        }
        ContactQuestsTestUtil.refreshIndex(ClientQuestFile.INSTANCE);
        if (s.shared || s.sharedSend) verifySharedTasks(s, "after index refresh");
        require(ClientQuestFile.INSTANCE.selfTeamData.getProgress(s.task) == 0
                        && ClientQuestFile.INSTANCE.selfTeamData.canStartTasks(s.task.getQuest()), "Task must start at zero and be startable");
    }

    private static void verifySharedTasks(Session s, String checkpoint) {
        var file = ClientQuestFile.INSTANCE;
        var data = file.selfTeamData;
        String prefix = "Fixture preparation failed (" + checkpoint + "): ";
        require(s.task instanceof ParcelTask && s.extra != null && s.task != s.extra && s.task.id != s.extra.id
                        && s.extra.id == EXTRA_TASK_ID && file.getTask(s.task.id) == s.task && file.getTask(EXTRA_TASK_ID) == s.extra
                        && s.task.getQuest() == s.extra.getQuest()
                        && s.task.getQuest().getTasks().stream().filter(task -> task == s.task).count() == 1
                        && s.task.getQuest().getTasks().stream().filter(task -> task == s.extra).count() == 1,
                prefix + "main/extra task identity, ownership or index changed");
        require(s.task.getCount() == 3 && s.extra.getCount() == 5
                        && s.task.getAmountNeeded(data) == 3 && s.extra.getAmountNeeded(data) == 5
                        && data.getProgress(s.task) == 0 && data.getProgress(s.extra) == 0
                        && !data.isCompleted(s.task) && !data.isCompleted(s.extra) && data.canStartTasks(s.task.getQuest())
                        && s.recipient.equals(s.task.getTargetAddressee())
                        && s.task.getTargetAddressee().equals(s.extra.getTargetAddressee())
                        && ItemStack.matches(((ParcelTask) s.task).getItemStack(), new ItemStack(Items.DIAMOND))
                        && ItemStack.matches(s.extra.getItemStack(), new ItemStack(Items.DIAMOND)),
                prefix + "expected independent same-recipient diamond demands main=3, extra=5 at zero progress; actual="
                        + s.task.getCount() + "/" + s.extra.getCount());
    }

    private static void verifySharedPreparation(Session s, Snapshot state) throws Exception {
        verifySharedTasks(s, "before native prefill");
        List<ItemStack> expected = copy(s.seeded.inventory);
        if (state.inventory.get(0).isEmpty()) expected.set(0, ItemStack.EMPTY);
        String fillerState = String.valueOf(read(s.filler, "currentState"));
        require(state.inputs.size() == 4 && state.inputs.stream().allMatch(ItemStack::isEmpty) && state.cursor.isEmpty()
                        && state.selected == 0 && sameStacks(expected, state.inventory) && "IDLE".equals(fillerState),
                "Fixture preparation failed: autofill advanced or inventory changed before native prefill 2; fillerState="
                        + fillerState + "; " + brief(state));
    }

    private static Snapshot seed(Session s) {
        ServerPlayer player = serverPlayer(s);
        require(player.containerMenu == player.inventoryMenu && player.containerMenu.getCarried().isEmpty(), "Close previous native menus before this phase");
        s.savedServer = snapshot(player, s);
        s.savedMode = player.gameMode.getGameModeForPlayer();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        ItemStack paper = s.postcard ? PostcardItem.getPostcard(new ResourceLocation("contact:default"), false)
                : new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("contact:" + (s.wrapping ? "wrapping_paper"
                        : s.type.equals("parcel") ? "envelope" : "red_packet_envelope"))));
        require(!paper.isEmpty(), "Native stationery item is not registered");
        player.getInventory().setItem(0, paper.copy());
        player.getInventory().setItem(1, paper.copy());
        if (s.postcard) {
            player.getInventory().getItem(0).setHoverName(Component.literal("Lifecycle original card"));
            player.getInventory().getItem(1).setHoverName(Component.literal("Lifecycle second card"));
        } else player.getInventory().setItem(9, new ItemStack(s.good, s.seedCount));
        player.getInventory().setItem(10, new ItemStack(Items.DIRT, 3));
        player.getInventory().setItem(11, new ItemStack(Items.STICK, 7));
        player.getInventory().selected = 0;
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastFullState();
        if (s.sharedSend) {
            ServerQuestFile file = ServerQuestFile.INSTANCE;
            s.serverChapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(),
                    "cq_lifecycle_shared_send_" + player.getUUID());
            file.getDefaultChapterGroup().addChapter(s.serverChapter);
            Quest serverQuest = new Quest(file.newID(), s.serverChapter);
            s.serverChapter.addQuest(serverQuest);
            long id1 = file.newID();
            long id2 = file.newID();
            while (id2 == id1) id2 = file.newID();
            long smallId = Long.compareUnsigned(id1, id2) < 0 ? id1 : id2;
            long largeId = smallId == id1 ? id2 : id1;
            s.serverTask1 = new ParcelTask(smallId, serverQuest).setStackAndCount(new ItemStack(Items.DIAMOND), 3);
            s.serverTask1.setTargetAddressee(s.recipient);
            s.serverTask2 = new ParcelTask(largeId, serverQuest).setStackAndCount(new ItemStack(Items.DIAMOND), 5);
            s.serverTask2.setTargetAddressee(s.recipient);
            serverQuest.addTask(s.serverTask1);
            serverQuest.addTask(s.serverTask2);
            serverQuest.addTask(new CheckmarkTask(file.newID(), serverQuest));
            ContactQuestsTestUtil.refreshIndex(file);
            file.clearCachedProgress();
            DataManager.targetsDirty = true;
            DataManager.INSTANCE.ensureTargetsFresh();
            KubeJSNpcSavedData.Companion.get(player.serverLevel()).setNpcData(s.recipient, 0, true);
            NpcConfigManager.INSTANCE.getNpcData(s.recipient).setDeliveryTime(0);
        }
        return snapshot(player, s);
    }

    private static void beginClose(Session s) {
        s.outcomeStage = s.stage;
        try { s.beforeClose = snapshot(Minecraft.getInstance().player, s); }
        catch (Exception error) { if (s.failure == null) s.failure = error; }
        s.stage = Stage.CLOSE;
        s.cleanupAt = ticks;
        s.cleanupNanos = System.nanoTime();
        var mc = Minecraft.getInstance();
        if (mc.screen != null) mc.screen.onClose();
        if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
        s.pending = serverSnapshot(s);
    }

    private static boolean restore(Session s) throws Exception {
        var mc = Minecraft.getInstance();
        require(ticks - s.cleanupAt < 180 && System.nanoTime() - s.cleanupNanos < 25_000_000_000L,
                "Native cleanup/restore exceeded its bound");
        if (s.stage == Stage.CLOSE) {
            // Let the old production timeout run after capturing failure; never call its private reset.
            if (scheduled(s) || !s.pending.isDone()) return false;
            Snapshot closed = s.pending.join();
            if (closed.menuId != 0 || !sameState(snapshot(mc.player, s), closed)) {
                s.pending = serverSnapshot(s);
                return false;
            }
            s.closedServer = closed;
            require(closed.cursor.isEmpty(), "Native close did not return the server cursor");
            if (s.failure == null && s.seeded != null) {
                require(sameDrops(s.seeded.drops, closed.drops), "Native close left unexpected drops");
                require(s.seeded.progress.equals(closed.progress), "Native close changed task/quest progress");
                if (!s.postcard) require(count(closed.inventory, s.good) == (s.sharedSend ? 0 : s.seedCount)
                                && count(closed.inventory, Items.DIRT) == 3 && count(closed.inventory, Items.STICK) == 7,
                        "Native close failed material conservation before fixture restoration: " + brief(closed));
            }
            s.task.setCount(s.oldCount);
            s.task.setTargetAddressee(s.oldRecipient);
            if (s.task instanceof ItemMatchingTask item) item.setItemStack(s.oldItem.copy());
            if (s.task instanceof RedPacketTask red) red.setBlessing(s.oldText);
            if (s.task instanceof PostcardTask card) { card.setPostcardText(s.oldText); card.setPostcardStyle(s.oldStyle); }
            if (s.extra != null) s.task.getQuest().removeTask(s.extra);
            ContactConfig.INSTANCE.getAutoFillSpeed().set(s.oldSpeed);
            ClientQuestFile.INSTANCE.setEditorPermission(s.oldEditor);
            ContactQuestsTestUtil.refreshIndex(ClientQuestFile.INSTANCE);
            s.pending = s.server.submit(() -> {
                ServerPlayer player = serverPlayer(s);
                for (ObservedMenu observed : s.listeners) observed.menu.removeSlotListener(observed.listener);
                if (s.serverChapter != null && ServerQuestFile.INSTANCE != null) {
                    if (s.serverChapter.getGroup() != null) {
                        s.serverChapter.getGroup().removeChapter(s.serverChapter);
                    }
                    ContactQuestsTestUtil.refreshIndex(ServerQuestFile.INSTANCE);
                    ServerQuestFile.INSTANCE.clearCachedProgress();
                    DataManager.targetsDirty = true;
                    KubeJSNpcSavedData.Companion.get(player.serverLevel()).removeNpcData(s.recipient);
                }
                if (s.savedServer != null) {
                    require(player.containerMenu == player.inventoryMenu, "Server native menu did not close before restoration");
                    for (int i = 0; i < s.savedServer.inventory.size(); i++) player.getInventory().setItem(i, s.savedServer.inventory.get(i).copy());
                    player.getInventory().selected = s.savedServer.selected;
                    player.setGameMode(s.savedMode);
                    player.getInventory().setChanged();
                    player.inventoryMenu.broadcastFullState();
                }
                return snapshot(player, s);
            });
            s.stage = Stage.RESTORE;
            return false;
        }
        if (!s.pending.isDone()) return false;
        Snapshot restored = s.pending.join();
        if (s.savedServer != null) {
            select(s.savedServer.selected);
            require(sameStacks(s.savedServer.inventory, restored.inventory) && restored.cursor.isEmpty(), "Server restoration differs");
            if (!sameStacks(restored.inventory, inventory(mc.player))) return false;
            if (mc.gameMode.getPlayerMode() != s.savedMode) return false;
        }
        return record(s, "Native state/config/inventory restored");
    }

    private static boolean record(Session s, String cleanup) {
        boolean success = s.failure == null && s.verified;
        String detail = success ? "Real TaskButton/Submit; exact client/server slots, cursor, inventory and unchanged progress; "
                + (s.closeOnly ? "native ESC without reopening; idle within 2 ticks; exact returned inventory/cursor after 24 ticks"
                : s.reopen ? "same-tick second-stationery reopen stayed idle for 24 ticks"
                : s.shared ? "two same-recipient demands 3+5 with real prefill 2 produced total 8, remainder 4"
                : s.sharedSend ? "two same-recipient tasks 3+5 settled 3/3 and 3/5 after real WrappingPaper pack + Postbox send"
                : s.midflightMutation ? "mid-flight task change stopped at 4, returned 8 diamonds to slot 9 and left cursor empty"
                : s.intervention ? "native manual input stopped old typing; Done saved the exact draft"
                : s.empty ? "configured EMPTY task filled blessing and native Seal saved empty contents"
                : "need 9 from 12: pickup, deposits 4/4/1, return 3 on distinct ticks; " + s.clientTrace)
                : "stage=" + s.outcomeStage + "; " + s.failure + "; beforeClose=" + s.beforeClose
                        + "; closedServer=" + s.closedServer + "; clientTrace=" + s.clientTrace
                        + "; nativeServerTrace=" + s.serverTrace;
        active = null;
        completed.add(s.phase);
        ContactQuestsClientTestController.assertCase(PREFIX + s.phase, success, detail + "; " + cleanup);
        return true;
    }

    private static Snapshot snapshot(Player player, Session s) {
        AbstractContainerMenu menu = player.containerMenu;
        int size = menu instanceof WrappingPaperScreenHandler ? 4 : menu instanceof EnvelopeScreenHandler
                || menu instanceof RedPacketEnvelopeScreenHandler ? 1 : 0;
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < size; i++) inputs.add(menu.getSlot(i).getItem().copy());
        Map<UUID, ItemStack> drops = new LinkedHashMap<>();
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(16)))
            drops.put(item.getUUID(), item.getItem().copy());
        TeamData team = player instanceof ServerPlayer server ? TeamData.get(server) : ClientQuestFile.INSTANCE.selfTeamData;
        List<Long> sharedProg = (player instanceof ServerPlayer && s.serverTask1 != null && s.serverTask2 != null)
                ? List.of(team.getProgress(s.serverTask1), team.getProgress(s.serverTask2))
                : List.of();
        return new Snapshot(menu.getClass().getName(), menu.containerId, List.copyOf(inputs), inventory(player),
                menu.getCarried().copy(), player.getInventory().selected, Map.copyOf(drops), progress(team, s), sharedProg);
    }

    private static List<Long> progress(TeamData team, Session s) {
        List<Long> values = new ArrayList<>();
        for (long id : new long[]{s.task.id, s.task.getQuest().id, EXTRA_TASK_ID}) {
            values.add(team.getProgress(id));
            values.add(team.getCompletedTime(id).map(java.util.Date::getTime).orElse(-1L));
        }
        return List.copyOf(values);
    }
    private static CompletableFuture<Snapshot> serverSnapshot(Session s) { return s.server.submit(() -> snapshot(serverPlayer(s), s)); }
    private static ServerPlayer serverPlayer(Session s) {
        ServerPlayer player = s.server.getPlayerList().getPlayer(s.owner);
        require(player != null, "Owning integrated-server player disappeared");
        return player;
    }
    private static List<ItemStack> inventory(Player player) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) items.add(player.getInventory().getItem(i).copy());
        return List.copyOf(items);
    }
    private static List<ItemStack> copy(List<ItemStack> stacks) { return new ArrayList<>(stacks.stream().map(ItemStack::copy).toList()); }
    private static int count(List<ItemStack> stacks, Item item) { return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum(); }
    private static boolean at(Snapshot state, Session s, int[] point) {
        return count(state.inputs, s.good) == point[0] && state.cursor.getCount() == point[1]
                && (point[1] == 0 || state.cursor.is(s.good)) && count(state.inventory, s.good) == point[2];
    }
    private static boolean sameMaterial(Snapshot a, Snapshot b, Item good) {
        return count(a.inputs, good) == count(b.inputs, good) && ItemStack.matches(a.cursor, b.cursor)
                && count(a.inventory, good) == count(b.inventory, good);
    }
    private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        return true;
    }
    private static boolean sameDrops(Map<UUID, ItemStack> a, Map<UUID, ItemStack> b) {
        return a.keySet().equals(b.keySet()) && a.keySet().stream().allMatch(id -> ItemStack.matches(a.get(id), b.get(id)));
    }
    private static boolean sameState(Snapshot a, Snapshot b) {
        return a.menuId == b.menuId && a.menu.equals(b.menu) && a.selected == b.selected
                && ItemStack.matches(a.cursor, b.cursor) && sameStacks(a.inputs, b.inputs) && sameStacks(a.inventory, b.inventory);
    }
    private static String brief(Snapshot state) { return "menu=" + state.menuId + ", input=" + state.inputs + ", cursor=" + state.cursor + ", inventory=" + state.inventory; }
    private static boolean nativeMenu(Session s, AbstractContainerMenu menu) {
        return s.wrapping ? menu instanceof WrappingPaperScreenHandler : s.type.equals("parcel")
                ? menu instanceof EnvelopeScreenHandler : menu instanceof RedPacketEnvelopeScreenHandler;
    }
    private static boolean nativeScreen(Session s) {
        Screen screen = Minecraft.getInstance().screen;
        return s.postcard ? screen instanceof PostcardEditScreen : s.wrapping ? screen instanceof WrappingPaperScreen
                : s.type.equals("parcel") ? screen instanceof EnvelopeScreen : screen instanceof RedPacketEnvelopeScreen;
    }
    private static void select(int slot) {
        var mc = Minecraft.getInstance();
        if (mc.player.getInventory().selected != slot) {
            mc.player.getInventory().selected = slot;
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }
    }
    private static boolean scheduled(Session s) throws Exception { return Boolean.TRUE.equals(read(s.filler, "isTaskScheduled")); }
    private static void requireIdle(Session s) throws Exception { require(!scheduled(s) && read(s.filler, "taskData") == null, "Old autofill remained scheduled after interruption/finish"); }
    private static String page() throws Exception {
        require(Minecraft.getInstance().screen instanceof PostcardEditScreen, "Expected actual postcard editor");
        return (String) read(((PostcardEditScreenAccessor) Minecraft.getInstance().screen).contactQuests$getTextBox(), "page");
    }
    private static String pageUnchecked() {
        try { return page(); } catch (Exception error) { throw new IllegalStateException(error); }
    }
    private static void clickButton(Screen screen, String field) throws Exception {
        Object found = read(screen, field);
        require(found instanceof Button button && button.active && button.visible && screen.children().contains(button), "Native button is unavailable: " + field);
        Button button = (Button) found;
        click(screen, button.getX(), button.getY(), button.getWidth(), button.getHeight());
    }
    private static void click(Screen screen, int x, int y, int width, int height) {
        require(Minecraft.getInstance().screen == screen && width > 0 && height > 0, "Button is not laid out on the current screen");
        require(screen.mouseClicked(x + width / 2.0, y + height / 2.0, GLFW.GLFW_MOUSE_BUTTON_LEFT), "Native screen did not consume click");
        screen.mouseReleased(x + width / 2.0, y + height / 2.0, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private static void invokeOpen(String type) throws Exception {
        var method = ContactQuestsClientTestController.class.getDeclaredMethod("openTaskDisplay", String.class);
        method.setAccessible(true);
        try { method.invoke(null, type); }
        catch (InvocationTargetException error) { throw new IllegalStateException("Native TaskButton entry", error.getCause()); }
        require(!Boolean.TRUE.equals(read(null, "finished")), "Controller failed opening the native task");
    }
    private static Object read(Object owner, String name) throws Exception {
        for (Class<?> type = owner == null ? ContactQuestsClientTestController.class : owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private record Snapshot(String menu, int menuId, List<ItemStack> inputs, List<ItemStack> inventory,
                            ItemStack cursor, int selected, Map<UUID, ItemStack> drops,
                            List<Long> progress, List<Long> sharedProgress) {}
    private record Frame(int tick, Snapshot state) {
        @Override public String toString() { return tick + ":" + brief(state); }
    }
    private record ObservedMenu(AbstractContainerMenu menu, ContainerListener listener) {}

    private static final class Session {
        final String phase, type, recipient;
        final boolean postcard, wrapping, shared, sharedSend, midflightMutation, empty, closeOnly, reopen, intervention;
        final MinecraftServer server;
        final UUID owner;
        final ContactTask task;
        final Object filler;
        final Item good;
        final long oldCount;
        final String oldRecipient, oldText, oldStyle;
        final ItemStack oldItem;
        final int oldSpeed, seedCount, started = ticks;
        final boolean oldEditor;
        final long startedNanos = System.nanoTime();
        final List<Frame> clientTrace = new ArrayList<>();
        final ConcurrentLinkedQueue<Snapshot> serverTrace = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<ObservedMenu> listeners = new ConcurrentLinkedQueue<>();
        volatile Stage stage = Stage.SEED;
        volatile Throwable failure;
        Snapshot savedServer, seeded, beforeClose, closedServer;
        Stage outcomeStage;
        GameType savedMode;
        List<Long> clientProgress;
        CompletableFuture<Snapshot> pending;
        ParcelTask extra, serverTask1, serverTask2;
        Chapter serverChapter;
        Screen submission, firstScreen, secondScreen;
        int firstMenuId, interruptedAt, reopenedAt = -1, cleanupAt;
        long cleanupNanos;
        boolean prefilled, verified, mutated, postboxMoved;
        String partialText, editedText;

        Session(String phase) throws Exception {
            var mc = Minecraft.getInstance();
            require(mc.player != null && mc.level != null && mc.gameMode != null && mc.getSingleplayerServer() != null,
                    "Lifecycle phases require the live integrated-server Windows client");
            require(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("windows"), "Run these UI scenarios with the real Windows client");
            require(mc.screen == null && mc.player.containerMenu == mc.player.inventoryMenu && mc.player.containerMenu.getCarried().isEmpty(),
                    "Close previous screens before the lifecycle action");
            this.phase = phase;
            postcard = phase.startsWith("postcard_"); shared = phase.equals("parcel_shared_prefilled");
            sharedSend = phase.equals("parcel_shared_send"); midflightMutation = phase.equals("parcel_midflight_mutation");
            wrapping = phase.startsWith("wrapping_") || shared || sharedSend; empty = phase.equals("red_packet_empty");
            closeOnly = phase.contains("_close_"); reopen = phase.contains("reopen"); intervention = phase.equals("postcard_intervention");
            seedCount = sharedSend ? 6 : 12;
            recipient = sharedSend ? "CQ_lifecycle_shared_send" : "CQ lifecycle " + phase;
            type = postcard ? "postcard" : phase.startsWith("red_packet_") ? "red_packet" : "parcel";
            task = (ContactTask) read(null, postcard ? "postcardTask" : type.equals("parcel") ? "parcelTask" : "redPacketTask");
            require(task != null && ClientQuestFile.INSTANCE != null, "Run install_synthetic_tasks first");
            filler = postcard ? PostcardAutoFiller.INSTANCE : type.equals("parcel") ? ParcelAutoFiller.INSTANCE : RedPacketAutoFiller.INSTANCE;
            for (Object other : List.of(ParcelAutoFiller.INSTANCE, RedPacketAutoFiller.INSTANCE, PostcardAutoFiller.INSTANCE))
                require(Boolean.FALSE.equals(read(other, "isTaskScheduled")) && read(other, "taskData") == null, "Previous autofill is still active");
            server = mc.getSingleplayerServer(); owner = mc.player.getUUID();
            good = type.equals("red_packet") ? Items.GOLD_INGOT : Items.DIAMOND;
            oldCount = task.getCount(); oldRecipient = task.getTargetAddressee();
            oldItem = task instanceof ItemMatchingTask item ? item.getItemStack().copy() : ItemStack.EMPTY;
            oldText = task instanceof PostcardTask card ? card.getPostcardText() : task instanceof RedPacketTask red ? red.getBlessing() : "";
            oldStyle = task instanceof PostcardTask card ? card.getPostcardStyle() : "";
            oldSpeed = ContactConfig.INSTANCE.getAutoFillSpeed().get(); oldEditor = ClientQuestFile.INSTANCE.canEdit();
        }
    }
}
