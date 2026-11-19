package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.client.gui.ContactConfigScreen;
import com.creeperyang.contactquests.client.gui.GetBinderButton;
import com.creeperyang.contactquests.client.gui.TagSelectionScreen;
import com.creeperyang.contactquests.client.gui.ValidParcelItemsScreen;
import com.creeperyang.contactquests.client.gui.ValidPostcardItemsScreen;
import com.creeperyang.contactquests.client.gui.ValidRedPacketItemsScreen;
import com.creeperyang.contactquests.client.renderer.MailboxGlobalRenderer;
import com.creeperyang.contactquests.client.util.ParcelAutoFiller;
import com.creeperyang.contactquests.client.util.PostcardAutoFiller;
import com.creeperyang.contactquests.client.util.RedPacketAutoFiller;
import com.creeperyang.contactquests.config.ContactConfig;
import com.creeperyang.contactquests.config.TagConfig;
import com.creeperyang.contactquests.compat.jei.ContactQuestsJeiPlugin;
import com.creeperyang.contactquests.quest.reward.ParcelRandomReward;
import com.creeperyang.contactquests.quest.reward.ParcelReward;
import com.creeperyang.contactquests.quest.reward.ParcelRewardBase;
import com.creeperyang.contactquests.quest.reward.PostcardReward;
import com.creeperyang.contactquests.quest.reward.RedPacketReward;
import com.creeperyang.contactquests.quest.task.ParcelTask;
import com.creeperyang.contactquests.quest.task.PostcardTask;
import com.creeperyang.contactquests.quest.task.RedPacketTask;
import com.creeperyang.contactquests.bridge.IMailboxTeamAccessor;
import com.creeperyang.contactquests.bridge.IQuestExtension;
import com.creeperyang.contactquests.mailbox.TeamMailboxBinding;
import com.creeperyang.contactquests.registry.ModItems;
import com.creeperyang.contactquests.utils.TagUtils;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.flechazo.contact.common.block.MailboxBlock;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.common.registry.BlockRegistry;
import com.flechazo.contact.common.tileentity.MailboxBlockEntity;
import com.google.gson.JsonObject;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.config.ListConfig;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigListScreen;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.IScreenWrapper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.WidgetType;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.loot.RewardTable;
import dev.ftb.mods.ftbquests.quest.loot.WeightedReward;
import dev.ftb.mods.ftbquests.quest.reward.ItemReward;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforge.client.gui.widget.ModListWidget;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Independent controller action: {@code additional_ui -> run(step)}.
 * Every step supplies type and phase; every successful phase records
 * {@code client.additional_ui.TYPE.PHASE}. A failed phase records that ID as FAIL.
 * No private production click/save/cancel callback is called by this fixture.
 *
 * type=config, in order:
 * pause, mods, search, select, open, seed_done, seed_reopen, empty_done,
 * empty_reopen, overflow_seed_done, overflow_seed_reopen, overflow_done,
 * overflow_reopen, toggle_done, toggle_reopen, cancel, cancel_reopen,
 * restore_done, restore_verify, back, resume.
 * Use {@link #phases(String)} and {@link #waitTicksAfter(String, String)} for
 * controller sequencing; wait 3 ticks after every config phase. There is no long wait
 * or controller retry inside this class. Begin in-game with no screen, an empty
 * carried stack, idle autofillers and live configuration matching its disk file.
 * Actual Mods list selection and its registered Config factory are mandatory.
 * The full inventory, unrelated TOML settings and default addressee are preserved.
 * Original tested values are restored through the same native Done button.
 *
 * type=required_tags or unlock_tags, separately and in order:
 * prepare, open, expand, focus, row, add, focus_tag, select, save_list,
 * save_object, snapshot_saved, verify_saved, reopen, reopen_expand,
 * reopen_focus, reopen_row, tooltip, remove, restore_list, restore_object,
 * snapshot_restored, verify_restored.
 * Wait 5 ticks after prepare and each snapshot; 20 after save_object and
 * restore_object; 3 after every other UI transition/expand/focus/remove.
 * A pending snapshot fails its verification instead of blocking the client thread.
 * Requires the integrated server and the existing fully edited editorQuest fixture,
 * after its original field/rule checks and before persistence/rejoin snapshots.
 * required_tags is a Quest-owned native config row (not a ContactTask field).
 * unlock_tags uses the existing ParcelReward. Both append a distinct existing
 * graph Tag, accept the list and object editor, observe the owning server, reopen
 * and inspect the native list-element tooltip, then delete only the appended row
 * and save/independently verify restoration. Original session Tags are restored.
 * Do not interleave graph editing, delivery, reload or another Tag selector.
 *
 * Teardown on failure may restore config memory/disk or the prior screen, but
 * cannot turn a failed case into PASS. Tag graph restoration uses the explicit
 * normal UI phases; failure leaves those objects available for diagnosis.
 */
public final class ContactQuestsAdditionalUiAssertions {
    private static final String PREFIX = "client.additional_ui.";
    private static final List<String> CONFIG_PHASES = List.of(
            "pause", "mods", "search", "select", "open", "seed_done", "seed_reopen", "empty_done", "empty_reopen",
            "overflow_seed_done", "overflow_seed_reopen",
            "overflow_done", "overflow_reopen", "toggle_done", "toggle_reopen", "cancel",
            "cancel_reopen", "restore_done", "restore_verify", "back", "resume");
    private static final List<String> TAG_PHASES = List.of(
            "prepare", "open", "expand", "focus", "row", "add", "focus_tag", "select",
            "save_list", "save_object", "snapshot_saved", "verify_saved", "reopen",
            "reopen_expand", "reopen_focus", "reopen_row", "tooltip", "remove",
            "restore_list", "restore_object", "snapshot_restored", "verify_restored");
    private static final List<String> MAILBOX_RENDERER_PHASES = List.of(
            "prepare", "unmarked", "set_own_team", "verify_own_team", "set_solo_marker", "verify_solo_marker",
            "set_foreign", "verify_foreign", "move_far", "verify_far", "move_near", "verify_near_and_destroy", "verify_removed");
    private static final List<String> UI_COMPLETENESS_PHASES = List.of(
            "empty_task_toast", "binder_open", "binder_click", "binder_verify", "binder_restored", "submit_tooltips_and_jei");
    private static ConfigSession configSession;
    private static TagSession tagSession;
    private static RendererSession rendererSession;
    private static CompletenessSession completenessSession;

    private ContactQuestsAdditionalUiAssertions() {}

    /** Immutable phase order shared by the composite action and its required case IDs. */
    public static List<String> phases(String type) {
        return switch (type) {
            case "config" -> CONFIG_PHASES;
            case "required_tags", "unlock_tags" -> TAG_PHASES;
            case "mailbox_renderer" -> MAILBOX_RENDERER_PHASES;
            case "ui_completeness" -> UI_COMPLETENESS_PHASES;
            default -> throw new IllegalArgumentException("Unknown additional UI type: " + type);
        };
    }

    /** Scheduling advice only: never sleeps, polls, advances a phase or records a case. */
    public static int waitTicksAfter(String type, String phase) {
        require(phases(type).contains(phase), "Unknown additional UI phase: " + type + "/" + phase);
        if (type.equals("config")) return 3;
        if (type.equals("mailbox_renderer")) {
            return switch (phase) {
                case "prepare", "set_own_team", "set_solo_marker", "set_foreign", "move_far", "move_near", "verify_near_and_destroy" -> 8;
                default -> 2;
            };
        }
        if (type.equals("ui_completeness")) {
            return switch (phase) {
                case "binder_click" -> 15;
                case "binder_verify" -> 6;
                default -> 3;
            };
        }
        return switch (phase) {
            case "prepare", "snapshot_saved", "snapshot_restored" -> 5;
            case "save_object", "restore_object" -> 20;
            default -> 3;
        };
    }

    private record ConfigState(int speed, int retry, boolean delivery, String addressee) {}
    private record InventoryState(UUID player, int selected, List<ItemStack> stacks, ItemStack carried) {}

    private static final class ConfigSession {
        final ConfigState original = configState();
        final Path path = FMLPaths.CONFIGDIR.get().resolve("contactquests/contactquests-common.toml");
        final Map<String, Object> originalDisk;
        final InventoryState inventory = inventory();
        Screen pause;
        ModListScreen mods;
        ContactConfigScreen page;
        byte[] beforeCancel;
        int next;

        ConfigSession() throws Exception {
            originalDisk = disk(path);
            requireDiskValues(this, original);
        }
    }

    private static final class TagSession {
        final String type;
        final UUID player;
        final long questId, objectId;
        final Screen parent;
        final boolean editPermission;
        final Set<String> sessionTags;
        final List<String> original, selected;
        final String choice;
        final CompoundTag clientBefore;
        final CompletableFuture<CompoundTag> serverBefore;
        CompletableFuture<CompoundTag> serverAfter;
        EditConfigScreen editor;
        EditConfigListScreen<?, ?> listEditor;
        ListConfig<?, ?> listConfig;
        int next;

        TagSession(String type, Quest quest, QuestObjectBase object) {
            var mc = Minecraft.getInstance();
            this.type = type;
            player = mc.player.getUUID();
            questId = quest.id;
            objectId = object.id;
            parent = mc.screen;
            editPermission = ClientQuestFile.INSTANCE.hasEditorPermission();
            sessionTags = new HashSet<>(TagConfig.Companion.getSessionTags());
            original = objectTags(object, type);
            String baselineTag = type.equals("required_tags") ? "CQEditorRequiredTag" : ContactQuestsEditorAssertions.TAG;
            require(original.contains(baselineTag), "Finish the existing editor field/rule checks first: missing " + baselineTag);
            choice = type.equals("required_tags") ? ContactQuestsEditorAssertions.TAG : "CQEditorRequiredTag";
            require(!original.contains(choice) && TagUtils.getAllExistingTags(ClientQuestFile.INSTANCE).contains(choice),
                    "The other existing editor Tag must exist in the graph and be absent from this list: " + choice);
            var added = new ArrayList<>(original);
            added.add(choice);
            selected = List.copyOf(added);
            clientBefore = nativeTag(object);
            serverBefore = serverTag(this);
        }

        String configId() { return type.equals("required_tags") ? "required_tags" : "unlock_tags"; }
        String nbtKey() { return type.equals("required_tags") ? "cq_req_tags" : "unlock_tags"; }
    }

    private static final class RendererSession {
        BlockPos below, lower, upper;
        BlockState prevBelow, prevLower, prevUpper;
        double origX, origY, origZ;
        float origYaw, origPitch;
        UUID playerUuid, viewerTeamId;
        MailboxBlockEntity observedBe;
        int next;
    }

    private static final class CompletenessSession {
        InventoryState savedInv;
        int binderBefore;
        Screen questScreen;
        int next;
    }

    public static void run(JsonObject step) {
        String type = step.has("type") ? step.get("type").getAsString() : "invalid";
        String phase = step.has("phase") ? step.get("phase").getAsString() : "invalid";
        String id = PREFIX + type + "." + phase;
        try {
            requirePlayer();
            requireIdleAutofillers();
            String detail = switch (type) {
                case "config" -> config(phase);
                case "mailbox_renderer" -> mailboxRenderer(phase);
                case "ui_completeness" -> uiCompleteness(phase);
                default -> tags(type, phase);
            };
            requireIdleAutofillers();
            ContactQuestsClientTestController.assertCase(id, true, detail);
        } catch (Exception error) {
            ContactQuestsClientTestController.assertCase(id, false, error.toString());
            if (type.equals("config") && configSession != null) {
                // Failure-only cleanup, never used to satisfy a UI assertion.
                ConfigState old = configSession.original;
                var config = ContactConfig.INSTANCE;
                config.getAutoFillSpeed().set(old.speed);
                config.getRetryInterval().set(old.retry);
                config.getEnableDeliveryTime().set(old.delivery);
                try { config.getSPEC().save(); }
                catch (Exception cleanup) {
                    ContactQuestsClientTestController.assertCase(PREFIX + "config.cleanup", false, cleanup.toString());
                }
            } else if (type.equals("mailbox_renderer") && rendererSession != null) {
                restoreRendererWorld(rendererSession);
                rendererSession = null;
            } else if (type.equals("ui_completeness")) {
                Minecraft.getInstance().setScreen(null);
                if (completenessSession != null && completenessSession.savedInv != null) {
                    restoreServerInventory(completenessSession.savedInv);
                }
                completenessSession = null;
            }
        }
    }

    private static String mailboxRenderer(String phase) throws Exception {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        require(server != null && mc.screen == null, "Mailbox renderer test requires live integrated server and closed screens");
        if (phase.equals("prepare")) {
            require(rendererSession == null, "Renderer session already active");
            rendererSession = new RendererSession();
            RendererSession s = rendererSession;
            s.playerUuid = mc.player.getUUID();
            s.viewerTeamId = MailboxGlobalRenderer.INSTANCE.resolveViewerTeamId();
            require(s.viewerTeamId != null, "Client viewer team ID unavailable");
            s.origX = mc.player.getX(); s.origY = mc.player.getY(); s.origZ = mc.player.getZ();
            s.origYaw = mc.player.getYRot(); s.origPitch = mc.player.getXRot();
            BlockPos lower = mc.player.blockPosition().offset(3, 0, 0);
            s.lower = lower; s.below = lower.below(); s.upper = lower.above();
            server.submit(() -> {
                var sp = server.getPlayerList().getPlayer(s.playerUuid);
                ServerLevel lvl = sp.serverLevel();
                s.prevBelow = lvl.getBlockState(s.below);
                s.prevLower = lvl.getBlockState(s.lower);
                s.prevUpper = lvl.getBlockState(s.upper);
                lvl.setBlock(s.below, Blocks.STONE.defaultBlockState(), 3);
                var boxState = BlockRegistry.GREEN_MAILBOX.get().defaultBlockState();
                lvl.setBlock(s.lower, boxState.setValue(MailboxBlock.HALF, DoubleBlockHalf.LOWER), 3);
                lvl.setBlock(s.upper, boxState.setValue(MailboxBlock.HALF, DoubleBlockHalf.UPPER), 3);
                TeamMailboxBinding.INSTANCE.updateMarker(lvl, s.upper, null);
                return null;
            }).join();
            s.next++;
            return "Placed unbound mailbox at " + s.upper;
        }
        RendererSession s = rendererSession;
        require(s != null, "Call prepare first");
        expectPhase(MAILBOX_RENDERER_PHASES, s.next, phase);
        String result;
        switch (phase) {
            case "unmarked" -> {
                BlockEntity be = mc.level.getBlockEntity(s.upper);
                require(be instanceof MailboxBlockEntity mbe && MailboxGlobalRenderer.INSTANCE.trackedMailboxes().contains(mbe),
                        "Client MailboxBlockEntity was not tracked by MailboxGlobalRenderer");
                s.observedBe = (MailboxBlockEntity) be;
                require(((IMailboxTeamAccessor) be).contactQuests$getTeamId() == null
                                && !MailboxGlobalRenderer.INSTANCE.shouldRenderMailbox(s.observedBe, mc.player, s.viewerTeamId),
                        "Unbound mailbox rendered floating binding icon");
                result = "Unbound mailbox tracked but hidden from 3D icon renderer";
            }
            case "set_own_team" -> {
                server.submit(() -> {
                    ServerLevel lvl = server.getPlayerList().getPlayer(s.playerUuid).serverLevel();
                    TeamMailboxBinding.INSTANCE.updateMarker(lvl, s.upper, s.viewerTeamId);
                    return null;
                }).join();
                result = "Server synced viewer team marker";
            }
            case "verify_own_team" -> {
                MailboxBlockEntity mbe = (MailboxBlockEntity) mc.level.getBlockEntity(s.upper);
                require(mbe != null && MailboxGlobalRenderer.INSTANCE.trackedMailboxes().contains(mbe)
                                && s.viewerTeamId.equals(((IMailboxTeamAccessor) mbe).contactQuests$getTeamId())
                                && MailboxGlobalRenderer.INSTANCE.shouldRenderMailbox(mbe, mc.player, s.viewerTeamId),
                        "Own-team mailbox did not become visible in MailboxGlobalRenderer");
                result = "Own-team mailbox shows 3D floating card";
            }
            case "set_solo_marker" -> {
                server.submit(() -> {
                    ServerLevel lvl = server.getPlayerList().getPlayer(s.playerUuid).serverLevel();
                    TeamMailboxBinding.INSTANCE.updateMarker(lvl, s.upper, s.playerUuid);
                    return null;
                }).join();
                result = "Server synced solo-player card marker";
            }
            case "verify_solo_marker" -> {
                MailboxBlockEntity mbe = (MailboxBlockEntity) mc.level.getBlockEntity(s.upper);
                require(mbe != null && s.playerUuid.equals(((IMailboxTeamAccessor) mbe).contactQuests$getTeamId())
                                && MailboxGlobalRenderer.INSTANCE.shouldRenderMailbox(mbe, mc.player, s.playerUuid),
                        "Solo player card-bound mailbox failed viewer visibility check");
                if (!s.viewerTeamId.equals(s.playerUuid)) {
                    require(!MailboxGlobalRenderer.INSTANCE.shouldRenderMailbox(mbe, mc.player, s.viewerTeamId),
                            "Solo player mailbox leaked to non-matching team viewer");
                }
                result = "Solo card-bound mailbox visible only to matching viewer";
            }
            case "set_foreign" -> {
                UUID foreign = UUID.randomUUID();
                server.submit(() -> {
                    ServerLevel lvl = server.getPlayerList().getPlayer(s.playerUuid).serverLevel();
                    TeamMailboxBinding.INSTANCE.updateMarker(lvl, s.upper, foreign);
                    return null;
                }).join();
                result = "Server synced foreign team marker";
            }
            case "verify_foreign" -> {
                MailboxBlockEntity mbe = (MailboxBlockEntity) mc.level.getBlockEntity(s.upper);
                require(mbe != null && !MailboxGlobalRenderer.INSTANCE.shouldRenderMailbox(mbe, mc.player, s.viewerTeamId),
                        "Foreign team mailbox leaked floating 3D card to viewer");
                result = "Foreign team mailbox hidden from viewer";
            }
            case "move_far" -> {
                server.submit(() -> {
                    var sp = server.getPlayerList().getPlayer(s.playerUuid);
                    TeamMailboxBinding.INSTANCE.updateMarker(sp.serverLevel(), s.upper, s.viewerTeamId);
                    sp.teleportTo(s.origX + 70.0, s.origY, s.origZ);
                    return null;
                }).join();
                result = "Moved viewer beyond 64-block render distance";
            }
            case "verify_far" -> {
                require(s.upper.distToCenterSqr(mc.player.position()) > 64 * 64
                                && !MailboxGlobalRenderer.INSTANCE.shouldRenderMailbox(s.observedBe, mc.player, s.viewerTeamId),
                        "Mailbox 3D icon rendered beyond 64 blocks");
                result = "Mailbox 3D icon culled beyond 64 blocks";
            }
            case "move_near" -> {
                server.submit(() -> {
                    var sp = server.getPlayerList().getPlayer(s.playerUuid);
                    sp.teleportTo(sp.serverLevel(), s.origX, s.origY, s.origZ, s.origYaw, s.origPitch);
                    return null;
                }).join();
                result = "Returned viewer within 64 blocks";
            }
            case "verify_near_and_destroy" -> {
                MailboxBlockEntity mbe = (MailboxBlockEntity) mc.level.getBlockEntity(s.upper);
                require(mbe != null && MailboxGlobalRenderer.INSTANCE.shouldRenderMailbox(mbe, mc.player, s.viewerTeamId),
                        "Mailbox 3D icon did not reappear when back within 64 blocks");
                restoreRendererWorld(s);
                result = "Mailbox icon restored in range and block removed cleanly";
            }
            case "verify_removed" -> {
                require(mc.level.getBlockEntity(s.upper) != s.observedBe
                                && !MailboxGlobalRenderer.INSTANCE.trackedMailboxes().contains(s.observedBe),
                        "Destroyed mailbox stayed in MailboxGlobalRenderer tracking set");
                rendererSession = null;
                return "Destroyed mailbox removed from global tracker and world restored";
            }
            default -> throw new IllegalArgumentException("Unknown renderer phase: " + phase);
        }
        s.next++;
        return result;
    }

    private static void restoreRendererWorld(RendererSession s) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || s.lower == null) return;
        server.submit(() -> {
            var sp = server.getPlayerList().getPlayer(s.playerUuid);
            if (sp != null) {
                ServerLevel lvl = sp.serverLevel();
                sp.teleportTo(lvl, s.origX, s.origY, s.origZ, s.origYaw, s.origPitch);
                if (s.prevUpper != null) lvl.setBlock(s.upper, Blocks.AIR.defaultBlockState(), 3);
                if (s.prevLower != null) lvl.setBlock(s.lower, s.prevLower, 3);
                if (s.prevUpper != null) lvl.setBlock(s.upper, s.prevUpper, 3);
                if (s.prevBelow != null) lvl.setBlock(s.below, s.prevBelow, 3);
            }
            return null;
        }).join();
    }

    @SuppressWarnings("unchecked")
    private static String uiCompleteness(String phase) throws Exception {
        var mc = Minecraft.getInstance();
        if (phase.equals("empty_task_toast")) {
            require(completenessSession == null && mc.screen == null, "Close screens before ui_completeness");
            completenessSession = new CompletenessSession();
            CompletenessSession s = completenessSession;
            s.savedInv = inventory();
            var toastsList = (List<Map<String, Object>>) readController("toasts");
            int beforeToasts = toastsList.size();
            ClientQuestFile file = ClientQuestFile.INSTANCE;
            Chapter tempChapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(), "cq_empty_toast_tmp");
            file.getDefaultChapterGroup().addChapter(tempChapter);
            try {
                Quest tempQuest = new Quest(file.newID(), tempChapter);
                tempChapter.addQuest(tempQuest);
                ParcelTask emptyTask = new ParcelTask(file.newID(), tempQuest);
                emptyTask.setItemStack(ItemStack.EMPTY);
                emptyTask.setTargetAddressee("CQToastNpc");
                tempQuest.addTask(emptyTask);
                Button dummyButton = new Button(new Panel(new BaseScreen() {
                    @Override public void addWidgets() {}
                }) { @Override public void addWidgets() {} @Override public void alignWidgets() {} }) {
                    @Override public void onClicked(dev.ftb.mods.ftblibrary.ui.input.MouseButton b) {}
                };
                emptyTask.onButtonClicked(dummyButton, true);
                require(toastsList.size() > beforeToasts, "Empty-valid-items task click did not emit CustomToast");
                Map<String, Object> lastToast = toastsList.getLast();
                require(Component.translatable("contactquest.task.no_valid_items").getString().equals(lastToast.get("title")),
                        "CustomToast title mismatch: " + lastToast);
                s.next++;
                return "Empty valid-items ContactTask emitted CustomToast(" + lastToast.get("title") + ")";
            } finally {
                file.getDefaultChapterGroup().removeChapter(tempChapter);
                file.clearCachedData();
            }
        }
        CompletenessSession s = completenessSession;
        require(s != null, "Run empty_task_toast first");
        expectPhase(UI_COMPLETENESS_PHASES, s.next, phase);
        switch (phase) {
            case "binder_open" -> {
                s.binderBefore = (int) mc.player.getInventory().items.stream()
                        .filter(i -> i.is(ModItems.INSTANCE.getTEAM_BINDING_CARD().get()))
                        .mapToInt(ItemStack::getCount).sum();
                var gui = ClientQuestFile.openGui(null, false);
                require(gui != null && Minecraft.getInstance().screen != null, "QuestScreen did not open for GetBinderButton check");
                s.questScreen = Minecraft.getInstance().screen;
                s.next++;
                return "Opened QuestScreen for screen-coordinate GetBinderButton click";
            }
            case "binder_click" -> {
                var gui = (BaseScreen) gui();
                GetBinderButton binderBtn = flatten(gui).stream()
                        .filter(w -> w instanceof GetBinderButton)
                        .map(w -> (GetBinderButton) w).findFirst()
                        .orElseThrow(() -> new IllegalStateException("GetBinderButton missing on QuestScreen"));
                TooltipList tip = new TooltipList();
                binderBtn.addMouseOverText(tip);
                require(!tip.getLines().isEmpty(), "GetBinderButton produced empty tooltip");
                clickFtb(binderBtn, 0.5);
                s.next++;
                return "Clicked GetBinderButton at rendered screen coordinates";
            }
            case "binder_verify" -> {
                int nowCount = (int) mc.player.getInventory().items.stream()
                        .filter(i -> i.is(ModItems.INSTANCE.getTEAM_BINDING_CARD().get()))
                        .mapToInt(ItemStack::getCount).sum();
                require(nowCount == s.binderBefore + 1, "Screen-coordinate GetBinderButton click did not grant 1 card: "
                        + s.binderBefore + " -> " + nowCount);
                mc.setScreen(null);
                restoreServerInventory(s.savedInv);
                s.next++;
                return "Verified TeamBindingCard delivery and requested server inventory restore";
            }
            case "binder_restored" -> {
                requireInventory(s.savedInv);
                s.next++;
                return "Inventory cleanly restored after binder button click";
            }
            case "submit_tooltips_and_jei" -> {
                ClientQuestFile file = ClientQuestFile.INSTANCE;
                Chapter tempChapter = new Chapter(file.newID(), file, file.getDefaultChapterGroup(), "cq_submit_ui_tmp");
                file.getDefaultChapterGroup().addChapter(tempChapter);
                boolean origEdit = file.canEdit();
                try {
                    Quest q = new Quest(file.newID(), tempChapter);
                    tempChapter.addQuest(q);

                    ParcelTask parcel = new ParcelTask(file.newID(), q).setStackAndCount(new ItemStack(Items.DIAMOND), 2);
                    parcel.setTargetAddressee("CQSubmitNpc");
                    RedPacketTask red = new RedPacketTask(file.newID(), q).setStackAndCount(new ItemStack(Items.GOLD_INGOT), 1);
                    red.setBlessing("Bless"); red.setTargetAddressee("CQSubmitNpc");
                    PostcardTask card = new PostcardTask(file.newID(), q);
                    card.setPostcardStyle("contact:default"); card.setPostcardText("Hello"); card.setTargetAddressee("CQSubmitNpc");
                    q.addTask(parcel); q.addTask(red); q.addTask(card);

                    // Missing materials -> DISABLED + missing tooltip
                    mc.player.getInventory().clearContent();
                    var pScreen = new ValidParcelItemsScreen(parcel, new ArrayList<>(List.of(new ItemStack(Items.DIAMOND))));
                    pScreen.openGui();
                    TooltipList pMissingTip = new TooltipList();
                    pScreen.getSubmitButton().addMouseOverText(pMissingTip);
                    require(pScreen.getSubmitButton().getWidgetType() == WidgetType.DISABLED
                                    && pMissingTip.getLines().stream().anyMatch(c -> c.getString().contains(
                                            Component.translatable("contactquest.gui.missing_requirements").getString())),
                            "ValidParcelItemsScreen did not disable/warn when materials missing");
                    // Grid ingredient extraction for JEI/REI (PositionedIngredient requires hover)
                    boolean foundDiamondIngredient = false;
                    for (Widget w : pScreen.getMainPanel().getWidgets()) {
                        if (w instanceof Button) {
                            pointFtb(w, 0.5);
                            if (w.getIngredientUnderMouse()
                                    .map(pi -> pi.ingredient() instanceof ItemStack is && is.is(Items.DIAMOND)).orElse(false)) {
                                foundDiamondIngredient = true;
                                break;
                            }
                        }
                    }
                    require(foundDiamondIngredient, "ValidItemButton.getIngredientUnderMouse missed diamond");

                    var rScreen = new ValidRedPacketItemsScreen(red, new ArrayList<>(List.of(new ItemStack(Items.GOLD_INGOT))));
                    rScreen.openGui();
                    TooltipList rMissing = new TooltipList(); rScreen.getSubmitButton().addMouseOverText(rMissing);
                    require(rScreen.getSubmitButton().getWidgetType() == WidgetType.DISABLED && !rMissing.getLines().isEmpty(),
                            "ValidRedPacketItemsScreen did not disable/warn");

                    // Wrong style postcard cannot activate ValidPostcardItemsScreen submit
                    mc.player.getInventory().setItem(0, PostcardItem.getPostcard(ResourceLocation.parse("contact:stripes"), false));
                    var cScreen = new ValidPostcardItemsScreen(card);
                    cScreen.openGui();
                    TooltipList cMissing = new TooltipList(); cScreen.getSubmitButton().addMouseOverText(cMissing);
                    require(cScreen.getSubmitButton().getWidgetType() == WidgetType.DISABLED,
                            "ValidPostcardItemsScreen accepted mismatched card style");

                    // Valid materials enable all 3 submit buttons
                    mc.player.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("contact:envelope"))));
                    mc.player.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 2));
                    TooltipList pReadyTip = new TooltipList(); pScreen.getSubmitButton().addMouseOverText(pReadyTip);
                    require(pScreen.getSubmitButton().getWidgetType() == WidgetType.NORMAL, "ValidParcelItemsScreen stayed disabled when ready");

                    mc.player.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("contact:red_packet_envelope"))));
                    mc.player.getInventory().setItem(1, new ItemStack(Items.GOLD_INGOT, 1));
                    require(rScreen.getSubmitButton().getWidgetType() == WidgetType.NORMAL, "ValidRedPacketItemsScreen stayed disabled when ready");

                    mc.player.getInventory().setItem(0, PostcardItem.getPostcard(ResourceLocation.parse("contact:default"), false));
                    require(cScreen.getSubmitButton().getWidgetType() == WidgetType.NORMAL, "ValidPostcardItemsScreen stayed disabled when ready");

                    // Reward.getIngredient visible vs hideContents sealed
                    RewardTable tbl = new RewardTable(file.newID(), file);
                    tbl.addReward(new WeightedReward(new ItemReward(file.newID(), tbl.getFakeQuest(), new ItemStack(Items.DIAMOND), 1), 1));
                    ParcelReward pr = new ParcelReward(file.newID(), q); pr.setItem(new ItemStack(Items.EMERALD));
                    RedPacketReward rr = new RedPacketReward(file.newID(), q); rr.setItem(new ItemStack(Items.GOLD_INGOT));
                    PostcardReward cr = new PostcardReward(file.newID(), q);
                    ParcelRandomReward mr = new ParcelRandomReward(file.newID(), q); mr.setTable(tbl);
                    Widget dummyW = pScreen.getSubmitButton();
                    file.setEditorPermission(false);
                    require(pr.getIngredient(dummyW).isPresent() && rr.getIngredient(dummyW).isPresent() && cr.getIngredient(dummyW).isPresent(),
                            "Unsealed mail rewards did not expose JEI ingredient");
                    pr.setHideContents(true); rr.setHideContents(true); cr.setHideContents(true); mr.setHideContents(true);
                    require(pr.getIngredient(dummyW).isEmpty() && rr.getIngredient(dummyW).isEmpty()
                                    && cr.getIngredient(dummyW).isEmpty() && mr.getIngredient(dummyW).isEmpty(),
                            "Sealed mail rewards leaked ingredient when hideContents=true");

                    boolean jeiChecked = false;
                    if (ModList.get().isLoaded("jei")) {
                        ContactQuestsJeiPlugin.showRecipes(new ItemStack(Items.CRAFTING_TABLE));
                        require(mc.screen != null && mc.screen.getClass().getName().contains("mezz.jei"),
                                "ContactQuestsJeiPlugin.showRecipes did not open JEI screen");
                        jeiChecked = true;
                    }
                    completenessSession = null;
                    return "Verified submit tooltips/WidgetTypes, reward hideContents ingredient guard, and JEI=" + jeiChecked;
                } finally {
                    file.setEditorPermission(origEdit);
                    file.getDefaultChapterGroup().removeChapter(tempChapter);
                    file.clearCachedData();
                    mc.setScreen(null);
                    for (int i = 0; i < s.savedInv.stacks.size(); i++) mc.player.getInventory().setItem(i, s.savedInv.stacks.get(i).copy());
                    mc.player.getInventory().selected = s.savedInv.selected;
                }
            }
            default -> throw new IllegalArgumentException("Unknown ui_completeness phase: " + phase);
        }
    }

    private static void restoreServerInventory(InventoryState savedInv) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        server.submit(() -> {
            var sp = server.getPlayerList().getPlayer(savedInv.player);
            if (sp != null) {
                for (int i = 0; i < savedInv.stacks.size(); i++) sp.getInventory().setItem(i, savedInv.stacks.get(i).copy());
                sp.getInventory().selected = savedInv.selected;
                sp.getInventory().setChanged();
                sp.inventoryMenu.broadcastFullState();
            }
            return null;
        }).join();
    }

    private static String config(String phase) throws Exception {
        var mc = Minecraft.getInstance();
        if (phase.equals("pause")) {
            require(configSession == null && tagSession == null && mc.screen == null,
                    "Begin an independent config sequence in-game with no active fixture");
            configSession = new ConfigSession();
        }
        ConfigSession s = configSession;
        require(s != null, "Run type=config/phase=pause first");
        expectPhase(CONFIG_PHASES, s.next, phase);
        requireInventory(s.inventory);
        switch (phase) {
            case "pause" -> {
                mc.pauseGame(false);
                require(mc.screen instanceof PauseScreen pause && pause.showsPauseMenu(), "Native Pause menu did not open");
                s.pause = mc.screen;
            }
            case "mods" -> {
                require(mc.screen == s.pause, "Original Pause menu is not active");
                clickVanilla(s.pause, vanillaButton(s.pause, Component.translatable("fml.menu.mods")));
                require(mc.screen instanceof ModListScreen, "Native Pause Mods button did not open ModListScreen");
                s.mods = (ModListScreen) mc.screen;
                require(read(s.mods, "parentScreen") == s.pause, "Native Mods parent changed");
            }
            case "search" -> {
                requireMods(s, false);
                replaceText(s.mods, (EditBox) read(s.mods, "search"), "ContactQuests");
            }
            case "select" -> selectMod(s);
            case "open" -> openConfig(s, s.original);
            case "seed_done", "overflow_seed_done" -> {
                requireConfig(s);
                replaceText(s.page, (EditBox) read(s.page, "autoFillSpeedBox"), "7");
                replaceText(s.page, (EditBox) read(s.page, "retryIntervalBox"), "77");
                done(s);
            }
            case "seed_reopen", "overflow_seed_reopen" ->
                    openConfig(s, new ConfigState(7, 77, s.original.delivery, s.original.addressee));
            case "empty_done" -> {
                requireConfig(s);
                replaceText(s.page, (EditBox) read(s.page, "autoFillSpeedBox"), "");
                replaceText(s.page, (EditBox) read(s.page, "retryIntervalBox"), "");
                require(configState().equals(new ConfigState(7, 77, s.original.delivery, s.original.addressee)),
                        "Typing alone changed live configuration");
                done(s);
            }
            case "empty_reopen" -> openConfig(s, defaults(s));
            case "overflow_done" -> {
                requireConfig(s);
                String overflow = "999999999999999999999999999999";
                replaceText(s.page, (EditBox) read(s.page, "autoFillSpeedBox"), overflow);
                replaceText(s.page, (EditBox) read(s.page, "retryIntervalBox"), overflow);
                require(configState().equals(new ConfigState(7, 77, s.original.delivery, s.original.addressee)),
                        "Overflow typing changed the independently seeded live configuration before Done");
                done(s);
            }
            case "overflow_reopen" -> openConfig(s, defaults(s));
            case "toggle_done" -> {
                requireConfig(s);
                var toggle = (CycleButton<?>) read(s.page, "enableDeliveryButton");
                require(toggle.getValue().equals(s.original.delivery), "Wrong initial delivery toggle");
                clickVanilla(s.page, toggle);
                require(toggle.getValue().equals(!s.original.delivery), "Actual toggle click did not change its value");
                require(configState().equals(defaults(s)), "Delivery click saved before Done");
                done(s);
            }
            case "toggle_reopen" -> {
                openConfig(s, toggled(s));
                s.beforeCancel = Files.readAllBytes(s.path);
            }
            case "cancel" -> {
                requireConfig(s);
                replaceText(s.page, (EditBox) read(s.page, "autoFillSpeedBox"), "87");
                replaceText(s.page, (EditBox) read(s.page, "retryIntervalBox"), "987");
                var toggle = (CycleButton<?>) read(s.page, "enableDeliveryButton");
                clickVanilla(s.page, toggle);
                require(toggle.getValue().equals(s.original.delivery), "Cancel case must actually dirty the delivery toggle");
                require(configState().equals(toggled(s)), "Unaccepted input changed live settings");
                clickVanilla(s.page, vanillaButton(s.page, CommonComponents.GUI_CANCEL));
                requireMods(s);
            }
            case "cancel_reopen" -> {
                openConfig(s, toggled(s));
                require(Arrays.equals(s.beforeCancel, Files.readAllBytes(s.path)), "Cancel changed persisted TOML bytes");
            }
            case "restore_done" -> {
                requireConfig(s);
                replaceText(s.page, (EditBox) read(s.page, "autoFillSpeedBox"), Integer.toString(s.original.speed));
                replaceText(s.page, (EditBox) read(s.page, "retryIntervalBox"), Integer.toString(s.original.retry));
                var toggle = (CycleButton<?>) read(s.page, "enableDeliveryButton");
                if (!toggle.getValue().equals(s.original.delivery)) clickVanilla(s.page, toggle);
                done(s);
            }
            case "restore_verify" -> {
                openConfig(s, s.original);
                require(disk(s.path).equals(s.originalDisk), "Original full parsed TOML was not restored");
                clickVanilla(s.page, vanillaButton(s.page, CommonComponents.GUI_CANCEL));
                requireMods(s);
            }
            case "back" -> {
                requireMods(s);
                clickVanilla(s.mods, (AbstractWidget) read(s.mods, "doneButton"));
                require(mc.screen == s.pause, "Mods Done did not restore the original Pause menu");
            }
            case "resume" -> {
                require(mc.screen == s.pause, "Native Pause menu is not active");
                clickVanilla(s.pause, vanillaButton(s.pause, Component.translatable("menu.returnToGame")));
                require(mc.screen == null && configState().equals(s.original), "Return to Game/config restoration failed");
                requireDiskValues(s, s.original);
            }
            default -> throw new IllegalArgumentException("Unknown config phase: " + phase);
        }
        requireInventory(s.inventory);
        s.next++;
        if (phase.equals("resume")) configSession = null;
        return "Actual Pause -> Mods -> selected ContactQuests -> registered Config factory; " + phase
                + "; current=" + configState() + "; inventory and unrelated TOML settings preserved";
    }

    private static String tags(String type, String phase) throws Exception {
        require(List.of("required_tags", "unlock_tags").contains(type), "Expected type=config|required_tags|unlock_tags");
        var mc = Minecraft.getInstance();
        require(mc.getSingleplayerServer() != null, "Independent Tag snapshots require the integrated server");
        if (phase.equals("prepare")) {
            require(tagSession == null && configSession == null, "Finish/restore the previous additional UI sequence");
            Quest quest = (Quest) readController("editorQuest");
            require(quest != null, "Prepare and fully edit the existing editorQuest fixture first");
            quest = ClientQuestFile.INSTANCE.getQuest(quest.id);
            require(quest != null, "The editor quest is absent from the current client graph");
            QuestObjectBase object = type.equals("required_tags") ? quest : quest.getRewards().stream()
                    .filter(r -> r instanceof ParcelReward).findFirst().orElseThrow();
            tagSession = new TagSession(type, quest, object);
        }
        TagSession s = tagSession;
        require(s != null && s.type.equals(type) && mc.player.getUUID().equals(s.player), "Prepare this exact Tag type/player first");
        expectPhase(TAG_PHASES, s.next, phase);
        switch (phase) {
            case "prepare" -> { /* Snapshot requested on the owning server thread. */ }
            case "open" -> {
                require(tagsInNbt(completedSnapshot(s.serverBefore), s).equals(s.original), "Client/server initial Tags differ");
                openTagObject(s, s.original);
            }
            case "expand", "reopen_expand" -> {
                requireGui(s.editor);
                Widget expand = (Widget) read(s.editor, "buttonExpandAll");
                if (flatten(s.editor).contains(expand)) clickFtb(expand, 0.5);
                else {
                    require(((Number) read(s.editor, "groupSize")).intValue() <= 1,
                            "Multi-group native editor is missing its Expand All button");
                    configEntry(s); // A single-group editor has no Expand All control; its rows are already visible.
                }
            }
            case "focus", "reopen_focus" -> {
                requireGui(s.editor);
                focus(configEntry(s));
            }
            case "row", "reopen_row" -> {
                requireGui(s.editor);
                clickFtb(configEntry(s), 0.5);
                require(gui() instanceof EditConfigListScreen<?, ?>, "Native config row did not open EditConfigListScreen");
                s.listEditor = (EditConfigListScreen<?, ?>) gui();
                require(read(s.listEditor, "listConfig") == s.listConfig && s.listConfig.getType() instanceof TagConfig,
                        "List editor lost the production TagConfig element type");
                require(localTags(s).equals(phase.equals("row") ? s.original : s.selected), "List editor initialized wrong Tags");
            }
            case "add" -> {
                requireGui(s.listEditor);
                require(TagUtils.getAllExistingTags(ClientQuestFile.INSTANCE).contains(s.choice),
                        "The selected Tag must still exist in the real graph before native Add");
                clickFtb((Widget) read(s.listEditor, "addButton"), 0.5);
                require(gui() instanceof TagSelectionScreen selector && selector.getConfig() == s.listConfig.getType(),
                        "Actual native Add did not call this row's production TagConfig.onClicked");
                require(objectTags(clientObject(s), type).equals(s.original), "Opening the Tag selector changed the object");
            }
            case "focus_tag" -> {
                require(gui() instanceof TagSelectionScreen, "Production Tag selector is not active");
                focus(existingTagButton(s));
            }
            case "select" -> {
                require(gui() instanceof TagSelectionScreen, "Production Tag selector is not active");
                clickFtb(existingTagButton(s), 0.5);
                requireGui(s.listEditor);
                require(localTags(s).equals(s.selected), "Existing Tag selection did not append exactly one expected value");
                require(objectTags(clientObject(s), type).equals(s.original), "Selecting the row saved the object before Done");
            }
            case "save_list" -> {
                requireGui(s.listEditor);
                accept(s.listEditor);
                requireGui(s.editor);
                require(s.listConfig.getValue().equals(s.selected), "List Done did not preserve the exact selected Tags");
            }
            case "save_object" -> {
                requireGui(s.editor);
                accept(s.editor);
                require(gui() != s.editor, "Object Done did not close the native editor");
            }
            case "snapshot_saved" -> {
                require(clientMatches(s, s.selected), "Saved client object or unrelated object fields are incorrect");
                s.serverAfter = serverTag(s);
            }
            case "verify_saved" -> verifyServer(s, s.selected);
            case "reopen" -> openTagObject(s, s.selected);
            case "tooltip" -> {
                requireGui(s.listEditor);
                require(localTags(s).equals(s.selected), "Reopened list lost the saved selection");
                Widget row = addedRow(s);
                focus(row);
                pointFtb(row, 0.1);
                var tooltip = new TooltipList();
                row.addMouseOverText(tooltip);
                String expected = Component.translatable("contactquests.gui.current_tag", Component.literal(s.choice)).getString();
                require(tooltip.getLines().stream().map(Component::getString).anyMatch(expected::equals),
                        "Real native Tag row tooltip lacks the selected current-tag text: " + tooltip.getLines());
                require(s.listConfig.getType().getValue().equals(s.choice), "Tooltip was not provided by the actual selected TagConfig");
                require(clientMatches(s, s.selected), "Reopening/tooltip changed the saved object");
            }
            case "remove" -> {
                requireGui(s.listEditor);
                Widget row = addedRow(s);
                focus(row);
                clickFtb(row, 1.0); // Native row's rightmost delete control, not its edit area.
                require(localTags(s).equals(s.original), "Native delete must remove only the appended test Tag");
                require(clientMatches(s, s.selected), "Deleting a local row saved the object before Done");
            }
            case "restore_list" -> {
                requireGui(s.listEditor);
                accept(s.listEditor);
                requireGui(s.editor);
                require(s.listConfig.getValue().equals(s.original), "List Done did not restore original Tags");
            }
            case "restore_object" -> {
                requireGui(s.editor);
                accept(s.editor);
                require(gui() != s.editor, "Restoration Done did not close the editor");
            }
            case "snapshot_restored" -> {
                require(clientMatches(s, s.original), "Client object has not been restored exactly");
                s.serverAfter = serverTag(s);
            }
            case "verify_restored" -> {
                verifyServer(s, s.original);
                TagConfig.Companion.getSessionTags().clear();
                TagConfig.Companion.getSessionTags().addAll(s.sessionTags);
                ClientQuestFile.INSTANCE.setEditorPermission(s.editPermission);
                mc.setScreen(s.parent);
                require(TagConfig.Companion.getSessionTags().equals(s.sessionTags)
                                && ClientQuestFile.INSTANCE.hasEditorPermission() == s.editPermission && mc.screen == s.parent,
                        "Original session Tags/editor permission/parent screen were not restored");
            }
            default -> throw new IllegalArgumentException("Unknown native Tag-row phase: " + phase);
        }
        s.next++;
        if (phase.equals("verify_restored")) tagSession = null;
        return type + "/" + phase + ": native config/list/Tag clicks; expected selection=" + s.choice
                + "; original=" + s.original + "; server snapshots and full object NBT guard persistence and unrelated fields";
    }

    private static void selectMod(ConfigSession s) throws Exception {
        requireMods(s, false);
        var list = (ModListWidget) read(s.mods, "modList");
        var entry = list.children().stream().filter(row -> row.getInfo().getModId().equals("contactquests"))
                .findFirst().orElseThrow(() -> new IllegalStateException("ContactQuests mod row missing after native search refresh"));
        require(!(read(s.mods, "selected") instanceof ModListWidget.ModEntry selected
                        && selected.getInfo().getModId().equals("contactquests")),
                "ContactQuests was already selected before the real mod-row click");
        int index = list.children().indexOf(entry);
        int height = ((Number) read(list, "itemHeight")).intValue();
        list.setScrollAmount((double) index * height);
        Method rowTop = AbstractSelectionList.class.getDeclaredMethod("getRowTop", int.class);
        rowTop.setAccessible(true);
        double x = list.getRowLeft() + list.getRowWidth() / 2.0;
        double y = ((Number) rowTop.invoke(list, index)).doubleValue() + height / 2.0;
        require(list.isMouseOver(x, y), "Filtered mod row is outside the visible list");
        // Native ModListEntry selects successfully while returning false.
        s.mods.mouseClicked(x, y, 0);
        s.mods.mouseReleased(x, y, 0);
        requireMods(s);
    }

    private static void openConfig(ConfigSession s, ConfigState expected) throws Exception {
        requireMods(s);
        require(configState().equals(expected), "Wrong live settings before reopening: " + configState() + ", expected=" + expected);
        requireDiskValues(s, expected);
        clickVanilla(s.mods, (AbstractWidget) read(s.mods, "configButton"));
        require(Minecraft.getInstance().screen instanceof ContactConfigScreen, "Registered ModList Config factory did not open ContactConfigScreen");
        s.page = (ContactConfigScreen) Minecraft.getInstance().screen;
        require(read(s.page, "parent") == s.mods, "Config factory lost the exact selected ModList parent");
        require(((EditBox) read(s.page, "autoFillSpeedBox")).getValue().equals(Integer.toString(expected.speed))
                        && ((EditBox) read(s.page, "retryIntervalBox")).getValue().equals(Integer.toString(expected.retry))
                        && ((CycleButton<?>) read(s.page, "enableDeliveryButton")).getValue().equals(expected.delivery),
                "Factory-created/reopened native controls do not match expected persisted values " + expected);
    }

    private static ConfigState defaults(ConfigSession s) { return new ConfigState(1, 10, s.original.delivery, s.original.addressee); }
    private static ConfigState toggled(ConfigSession s) { return new ConfigState(1, 10, !s.original.delivery, s.original.addressee); }

    private static void done(ConfigSession s) throws Exception {
        clickVanilla(s.page, vanillaButton(s.page, CommonComponents.GUI_DONE));
        requireMods(s);
    }

    private static void requireConfig(ConfigSession s) {
        require(Minecraft.getInstance().screen == s.page && s.page != null, "Factory-created Config is not active");
    }

    private static void requireMods(ConfigSession s) throws Exception { requireMods(s, true); }

    private static void requireMods(ConfigSession s, boolean selected) throws Exception {
        require(s.mods != null && Minecraft.getInstance().screen == s.mods, "Native ModListScreen is not active");
        if (selected) {
            Object selectedEntry = read(s.mods, "selected");
            require(selectedEntry instanceof ModListWidget.ModEntry row && row.getInfo().getModId().equals("contactquests"),
                    "ContactQuests is not selected in the actual ModList");
            var config = (AbstractWidget) read(s.mods, "configButton");
            require(config.active && config.visible, "Selected mod's registered Config button is disabled/hidden");
        }
    }

    private static ConfigState configState() {
        var c = ContactConfig.INSTANCE;
        return new ConfigState(c.getAutoFillSpeed().get(), c.getRetryInterval().get(),
                c.getEnableDeliveryTime().get(), c.getDefaultTargetAddressee().get());
    }

    private static Map<String, Object> disk(Path path) throws Exception {
        require(Files.isRegularFile(path), "Active ContactQuests TOML does not exist: " + path);
        try (var reader = Files.newBufferedReader(path)) {
            return flattenToml(new TomlParser().parse(reader), "");
        }
    }

    private static Map<String, Object> flattenToml(UnmodifiableConfig value, String prefix) {
        var result = new LinkedHashMap<String, Object>();
        value.valueMap().forEach((key, object) -> {
            String name = prefix + key;
            if (object instanceof UnmodifiableConfig child) result.putAll(flattenToml(child, name + "."));
            else result.put(name, object);
        });
        return result;
    }

    private static void requireDiskValues(ConfigSession s, ConfigState expected) throws Exception {
        Map<String, Object> now = disk(s.path);
        require(number(now, "general.auto_fill_speed") == expected.speed
                        && number(now, "general.retry_interval") == expected.retry
                        && Boolean.valueOf(expected.delivery).equals(now.get("general.enable_delivery_time"))
                        && expected.addressee.equals(now.get("general.default_target_addressee")),
                "Saved TOML values do not match expected configuration: " + now + ", expected=" + expected);
        var untouchedBefore = new LinkedHashMap<>(s.originalDisk);
        var untouchedNow = new LinkedHashMap<>(now);
        for (String key : List.of("general.auto_fill_speed", "general.retry_interval", "general.enable_delivery_time")) {
            untouchedBefore.remove(key);
            untouchedNow.remove(key);
        }
        require(untouchedBefore.equals(untouchedNow), "UI save changed unrelated parsed TOML fields");
    }

    private static long number(Map<String, Object> data, String key) {
        require(data.get(key) instanceof Number, "Saved TOML key is missing/not numeric: " + key);
        return ((Number) data.get(key)).longValue();
    }

    private static void openTagObject(TagSession s, List<String> expected) throws Exception {
        require(objectTags(clientObject(s), s.type).equals(expected), "Wrong target Tags before native object editor");
        require(((Quest) readController("editorQuest")).id == s.questId, "Existing editor entry now points to another quest");
        ClientQuestFile.INSTANCE.setEditorPermission(true);
        Method open = ContactQuestsClientTestController.class.getDeclaredMethod("openObjectEditor", String.class, String.class);
        open.setAccessible(true);
        try { s.editor = (EditConfigScreen) open.invoke(null, s.type.equals("required_tags") ? "quest" : "reward", "parcel"); }
        catch (InvocationTargetException error) { throw new IllegalStateException("Existing native object Edit entry failed", error.getCause()); }
        requireGui(s.editor);
        ConfigValue<?> value = findConfig((ConfigGroup) read(s.editor, "group"), s.configId());
        require(value instanceof ListConfig<?, ?>, "The real native object editor lacks the expected Tag list config");
        s.listConfig = (ListConfig<?, ?>) value;
        require(s.listConfig.getType() instanceof TagConfig && s.listConfig.getValue().equals(expected),
                "Native list must use production TagConfig and exact saved values");
    }

    private static Widget configEntry(TagSession s) throws Exception {
        for (Widget w : flatten(s.editor)) {
            if (w.getClass().getSimpleName().equals("ConfigEntryButton") && read(w, "configValue") == s.listConfig) return w;
        }
        throw new IllegalStateException("Expanded visible native Tag ConfigEntryButton not found; wait after expand");
    }

    private static Widget existingTagButton(TagSession s) {
        return flatten((Panel) gui()).stream().filter(w -> w instanceof Button b && b.getTitle().getString().equals(s.choice))
                .findFirst().orElseThrow(() -> new IllegalStateException("Existing graph Tag has no selectable native row: " + s.choice));
    }

    private static Widget addedRow(TagSession s) throws Exception {
        for (Widget w : flatten(s.listEditor)) {
            if (w.getClass().getSimpleName().equals("ButtonConfigValue")
                    && ((Number) read(w, "index")).intValue() == s.original.size()) return w;
        }
        throw new IllegalStateException("Reopened native list element for the appended Tag is missing");
    }

    private static List<?> localTags(TagSession s) throws Exception { return List.copyOf((List<?>) read(s.listEditor, "localValues")); }

    private static QuestObjectBase clientObject(TagSession s) {
        Quest quest = ClientQuestFile.INSTANCE.getQuest(s.questId);
        require(quest != null, "Editor quest disappeared from client graph");
        return s.type.equals("required_tags") ? quest : quest.getRewards().stream()
                .filter(r -> r.id == s.objectId).findFirst().orElseThrow();
    }

    private static List<String> objectTags(QuestObjectBase object, String type) {
        return type.equals("required_tags")
                ? List.copyOf(((IQuestExtension) object).contactQuests$getRequiredTags())
                : List.copyOf(((ParcelRewardBase) object).getUnlockTags());
    }

    private static CompoundTag nativeTag(QuestObjectBase object) {
        var tag = new CompoundTag();
        object.writeData(tag, Minecraft.getInstance().level.registryAccess());
        return tag;
    }

    private static CompletableFuture<CompoundTag> serverTag(TagSession s) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        require(server != null, "Integrated server not available");
        return server.submit(() -> {
            var player = server.getPlayerList().getPlayer(s.player);
            require(player != null && ServerQuestFile.INSTANCE != null, "Owning server player/graph is missing");
            Quest quest = ServerQuestFile.INSTANCE.getQuest(s.questId);
            require(quest != null, "Editor quest is absent from the actual server graph");
            QuestObjectBase object = s.type.equals("required_tags") ? quest : quest.getRewards().stream()
                    .filter(r -> r.id == s.objectId).findFirst().orElseThrow();
            var tag = new CompoundTag();
            object.writeData(tag, player.registryAccess());
            return tag;
        });
    }

    private static List<String> tagsInNbt(CompoundTag nbt, TagSession s) {
        var tags = nbt.getList(s.nbtKey(), 8);
        var result = new ArrayList<String>();
        for (int i = 0; i < tags.size(); i++) result.add(tags.getString(i));
        return List.copyOf(result);
    }

    private static CompoundTag expectedTag(CompoundTag original, TagSession s, List<String> tags) {
        var expected = original.copy();
        if (tags.isEmpty() && !original.contains(s.nbtKey())) expected.remove(s.nbtKey());
        else {
            var list = new ListTag();
            for (String tag : tags) list.add(StringTag.valueOf(tag));
            expected.put(s.nbtKey(), list);
        }
        return expected;
    }

    private static boolean clientMatches(TagSession s, List<String> expected) {
        QuestObjectBase object = clientObject(s);
        return objectTags(object, s.type).equals(expected) && nativeTag(object).equals(expectedTag(s.clientBefore, s, expected));
    }

    private static void verifyServer(TagSession s, List<String> expected) {
        var actual = completedSnapshot(s.serverAfter);
        require(tagsInNbt(actual, s).equals(expected) && actual.equals(expectedTag(completedSnapshot(s.serverBefore), s, expected)),
                "Actual server object differs from the exact expected Tags/unrelated original NBT; actual=" + actual);
        require(clientMatches(s, expected), "Client object changed while observing the server");
    }

    private static CompoundTag completedSnapshot(CompletableFuture<CompoundTag> snapshot) {
        require(snapshot != null && snapshot.isDone(), "Server snapshot pending; schedule 5 ticks after prepare/snapshot");
        CompoundTag result = snapshot.getNow(null); // No join/get/wait on the client tick thread.
        require(result != null, "Owning server returned an empty snapshot");
        return result;
    }

    private static void accept(Panel editor) throws Exception {
        clickFtb((Widget) read(read(editor, "bottomPanel"), "buttonAccept"), 0.5);
    }

    private static ConfigValue<?> findConfig(ConfigGroup group, String id) {
        for (ConfigValue<?> value : group.getValues()) if (value.id.equals(id)) return value;
        for (ConfigGroup child : group.getSubgroups()) {
            ConfigValue<?> result = findConfig(child, id);
            if (result != null) return result;
        }
        return null;
    }

    private static List<Widget> flatten(Panel root) {
        var result = new ArrayList<Widget>();
        for (Widget widget : root.getWidgets()) {
            result.add(widget);
            if (widget instanceof Panel panel) result.addAll(flatten(panel));
        }
        return result;
    }

    private static Object gui() {
        var screen = Minecraft.getInstance().screen;
        return screen instanceof IScreenWrapper wrapper ? wrapper.getGui() : screen;
    }

    private static void requireGui(Panel expected) { require(expected != null && gui() == expected, "Expected original native GUI: " + expected); }

    private static void focus(Widget widget) {
        Panel parent = widget.getParent();
        require(parent != null, "Widget has no native panel");
        double limit = Math.max(0, parent.getContentHeight() - parent.getHeight());
        parent.setScrollY(Math.max(0, Math.min(limit, widget.getPosY() - parent.getHeight() / 2.0 + widget.height / 2.0)));
    }

    private static int[] pointFtb(Widget widget, double fraction) {
        require(gui() == widget.getGui() && flatten(widget.getGui()).contains(widget)
                        && widget.isEnabled() && widget.shouldDraw() && widget.width > 0 && widget.height > 0,
                "Native widget is not attached/enabled/visible/laid out on the active GUI");
        // FTB applies scroll offsets while drawing/dispatching then clears them.
        // Compute the rendered hit coordinates with the same panel offsets.
        var parents = new ArrayList<Panel>();
        var offsets = new ArrayList<Boolean>();
        for (Panel p = widget.getParent(); p != null; p = p.getParent()) {
            parents.add(p);
            offsets.add(p.isOffset());
            p.setOffset(true);
        }
        int x, y;
        try {
            x = widget.getX() + (fraction == 1.0 ? widget.width - 7 : Math.max(1, (int) (widget.width * fraction)));
            y = widget.getY() + widget.height / 2;
        } finally {
            for (int i = 0; i < parents.size(); i++) parents.get(i).setOffset(offsets.get(i));
        }
        Screen screen = Minecraft.getInstance().screen;
        require(screen != null && x >= 0 && y >= 0 && x < screen.width && y < screen.height,
                "Native widget hit is outside the visible screen: " + x + "," + y);
        BaseScreen gui = widget.getGui();
        gui.updateGui(x, y, 0);
        gui.updateMouseOver(x, y);
        screen.mouseMoved(x, y);
        require(widget.isMouseOver(), "Native widget is clipped or not hovered at its rendered hit coordinates");
        return new int[]{x, y};
    }

    private static void clickFtb(Widget widget, double fraction) {
        var screen = Minecraft.getInstance().screen;
        int[] at = pointFtb(widget, fraction);
        require(screen.mouseClicked(at[0], at[1], 0), "Native widget did not consume the real screen click");
        screen.mouseReleased(at[0], at[1], 0);
    }

    private static AbstractWidget vanillaButton(Screen screen, Component title) {
        return screen.children().stream().filter(w -> w instanceof AbstractWidget button && button.getMessage().getString().equals(title.getString()))
                .map(w -> (AbstractWidget) w).findFirst().orElseThrow(() -> new IllegalStateException("Native button missing: " + title.getString()));
    }

    private static void clickVanilla(Screen screen, AbstractWidget widget) {
        require(Minecraft.getInstance().screen == screen && screen.children().contains(widget) && widget.active && widget.visible,
                "Native widget is not attached/active/visible on the expected screen");
        double x = widget.getX() + widget.getWidth() / 2.0, y = widget.getY() + widget.getHeight() / 2.0;
        require(screen.mouseClicked(x, y, 0), "Native screen did not consume the actual button click");
        screen.mouseReleased(x, y, 0);
    }

    private static void replaceText(Screen screen, EditBox box, String value) {
        clickVanilla(screen, box);
        require(box.isFocused(), "Actual text-field click did not focus the field");
        int oldLength = box.getValue().length();
        screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        screen.keyReleased(GLFW.GLFW_KEY_HOME, 0, 0);
        for (int i = 0; i < oldLength; i++) {
            screen.keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0);
            screen.keyReleased(GLFW.GLFW_KEY_DELETE, 0, 0);
        }
        require(box.getValue().isEmpty(), "Real Home/Delete input did not clear the text field");
        for (char ch : value.toCharArray()) require(screen.charTyped(ch, 0), "Native text field rejected typed character");
        require(box.getValue().equals(value), "Real typing was truncated or altered: " + box.getValue());
    }

    private static InventoryState inventory() {
        var player = Minecraft.getInstance().player;
        var stacks = new ArrayList<ItemStack>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) stacks.add(player.getInventory().getItem(i).copy());
        return new InventoryState(player.getUUID(), player.getInventory().selected, List.copyOf(stacks), player.containerMenu.getCarried().copy());
    }

    private static void requireInventory(InventoryState before) {
        var now = inventory();
        require(now.player.equals(before.player) && now.selected == before.selected && now.stacks.size() == before.stacks.size()
                        && ItemStack.matches(now.carried, before.carried), "Config UI changed owner/selected slot/cursor");
        for (int i = 0; i < now.stacks.size(); i++) require(ItemStack.matches(now.stacks.get(i), before.stacks.get(i)), "Config UI changed inventory slot " + i);
    }

    private static void requirePlayer() {
        var mc = Minecraft.getInstance();
        require(mc.player != null && mc.level != null && mc.player.containerMenu == mc.player.inventoryMenu
                        && mc.player.containerMenu.getCarried().isEmpty(), "A live world/player with ordinary inventory and empty cursor is required");
    }

    private static void requireIdleAutofillers() throws Exception {
        for (Object filler : List.of(ParcelAutoFiller.INSTANCE, RedPacketAutoFiller.INSTANCE, PostcardAutoFiller.INSTANCE)) {
            require(Boolean.FALSE.equals(read(filler, "isTaskScheduled")) && read(filler, "taskData") == null,
                    "Wait for existing autofill before additional UI tests: " + filler.getClass().getSimpleName());
        }
    }

    private static void expectPhase(List<String> phases, int next, String phase) {
        require(next < phases.size() && phases.get(next).equals(phase), "Phase out of order: expected "
                + (next < phases.size() ? phases.get(next) : "finished") + ", got " + phase);
    }

    private static Object readController(String name) throws Exception {
        Field field = ContactQuestsClientTestController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static Object read(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(object.getClass().getName() + "." + name);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
