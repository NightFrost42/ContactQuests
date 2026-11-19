package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.client.gui.BaseValidTaskScreen;
import com.creeperyang.contactquests.client.util.ParcelAutoFiller;
import com.creeperyang.contactquests.client.util.PostcardAutoFiller;
import com.creeperyang.contactquests.client.util.RedPacketAutoFiller;
import com.creeperyang.contactquests.config.ContactConfig;
import com.creeperyang.contactquests.mixin.PostcardEditScreenAccessor;
import com.creeperyang.contactquests.mixin.RedPacketEnvelopeScreenAccessor;
import com.creeperyang.contactquests.quest.task.ContactTask;
import com.creeperyang.contactquests.quest.task.PostcardTask;
import com.flechazo.contact.client.gui.screen.EnvelopeScreen;
import com.flechazo.contact.client.gui.screen.PostcardEditScreen;
import com.flechazo.contact.client.gui.screen.RedPacketEnvelopeScreen;
import com.flechazo.contact.common.item.EnvelopeItem;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.common.item.RedPacketEnvelopeItem;
import com.flechazo.contact.common.screenhandler.EnvelopeScreenHandler;
import com.flechazo.contact.common.screenhandler.RedPacketEnvelopeScreenHandler;
import com.google.gson.JsonObject;
import dev.ftb.mods.ftblibrary.ui.IScreenWrapper;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Client scenario action: {@code autofill_boundary}; dispatch to {@link #run(JsonObject)}.
 * Every fill starts through the controller's ordinary TaskButton and right-hand Submit.
 *
 * Server fixture contract for {@code contactquests_test seed_autofill <type>}:
 * <ul>
 *   <li>parcel_exact: envelope in inventory 18, stick in 0, diamond x2 in 9, dirt x3 in 10.</li>
 *   <li>red_packet_exact: envelope in 18, stick in 0, gold x2 in 9, dirt x3 in 10.</li>
 *   <li>red_packet_prefilled: envelope in 0, gold x4 in 9, dirt x3 in 10;
 *       on the next native red-packet container open, MOVE gold x1 from 9 to input 0.</li>
 *   <li>red_packet_wrong: envelope in 0, gold x3 in 9, dirt x4 in 10;
 *       on the next native red-packet container open, MOVE dirt x1 from 10 to input 0.</li>
 *   <li>red_packet_missing: envelope in 0, empty 9, dirt x3 in 10.</li>
 * </ul>
 * Suffix fixtures must clear inventory, broadcast changes, and consume pending prefill
 * once on PlayerContainerEvent.Open, broadcasting that actual menu after the move.
 * The passive highest-priority tick observer proves that prefill reached the client
 * before autofill consumed the inventory; merely reaching the same final count cannot pass.
 * The existing postcard seed is sufficient. These scenarios use the integrated server
 * only for independent read-only snapshots, never to call autofill or fabricate results.
 */
@EventBusSubscriber(modid = ContactQuestsTestMod.MOD_ID, value = Dist.CLIENT)
public final class ContactQuestsAutofillAssertions {
    private static final String INSTANT_TEXT = "中文自动填入\n换行：春风。";
    private static final String CANCEL_TEXT = "这封信还没有写完，我会稍后继续。\n".repeat(4);
    private static final String ORIGINAL_DRAFT = "原来的字，请保留。";
    private static final String BLESSING = "fixed autofill blessing";

    private static String activeType;
    private static String activeVariant;
    private static boolean ordinaryEntry;
    private static boolean ordinarySubmit;
    private static Integer previousSpeed;
    private static String previousPostcardText;
    private static String previousPostcardStyle;
    private static ItemStack originalPostcard = ItemStack.EMPTY;
    private static int closedAtTick;
    private static boolean cancelledBeforeComplete;
    private static boolean timeoutVerified;
    private static CompletableFuture<ContainerSnapshot> serverContainer;
    private static CompletableFuture<ItemStack> serverPostcard;
    private static CompletableFuture<ItemStack> serverRedPacket;
    private static ContainerSnapshot observedPrefill;
    private static Exception observationFailure;
    private static boolean overflowDraftPrimed;

    private ContactQuestsAutofillAssertions() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void observePrefill(ClientTickEvent.Post event) {
        if (ordinarySubmit && activeType.equals("postcard") && activeVariant.startsWith("overflow_")
                && !overflowDraftPrimed && observationFailure == null
                && Minecraft.getInstance().screen instanceof PostcardEditScreen) {
            try { primeDraft(); overflowDraftPrimed = true; }
            catch (Exception error) { observationFailure = error; }
        }
        if (!ordinarySubmit || observedPrefill != null || observationFailure != null
                || !"red_packet".equals(activeType)
                || !("prefilled_remaining".equals(activeVariant) || "wrong_item_preserved".equals(activeVariant))) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.screen instanceof RedPacketEnvelopeScreen)
                || !(mc.player.containerMenu instanceof RedPacketEnvelopeScreenHandler)) return;
        try {
            ContainerSnapshot state = snapshot(mc.player, "red_packet");
            ItemStack expected = activeVariant.equals("prefilled_remaining") ? new ItemStack(Items.GOLD_INGOT) : new ItemStack(Items.DIRT);
            if (scheduled(RedPacketAutoFiller.INSTANCE) && ItemStack.matches(state.input, expected)
                    && state.goodInventory == 3 && state.dirtInventory == 3 && state.carried.isEmpty()) {
                observedPrefill = state;
            }
        } catch (Exception error) {
            observationFailure = error;
        }
    }

    public static void run(JsonObject step) {
        String phase = step.get("phase").getAsString();
        String type = step.get("type").getAsString();
        String variant = step.get("variant").getAsString();
        String id = assertionId(type, variant, phase);
        try {
            require(Minecraft.getInstance().player != null, "Client player is unavailable");
            if (phase.equals("prepare")) {
                prepare(type, variant);
                return;
            }
            require(type.equals(activeType) && variant.equals(activeVariant), "Boundary case was not prepared");
            switch (phase) {
                case "open" -> open();
                case "submit" -> submit();
                case "snapshot" -> requestContainerSnapshot();
                case "verify" -> {
                    require(ordinaryEntry && ordinarySubmit, "Ordinary task entry and right-hand Submit were not used");
                    if (type.equals("postcard")) verifyInstantText();
                    else verifyContainer();
                    record(id, "Ordinary " + type + "/" + variant + " completed with exact client/server state");
                }
                case "verify_rejected" -> {
                    require(ordinaryEntry && ordinarySubmit && variant.startsWith("overflow_"), "Overflow ordinary flow was not used");
                    require(overflowDraftPrimed && observationFailure == null,
                            "Original native draft was not observed before overflow rejection: " + observationFailure);
                    require(ORIGINAL_DRAFT.equals(page()) && !scheduled(PostcardAutoFiller.INSTANCE)
                                    && read(PostcardAutoFiller.INSTANCE, "taskData") == null,
                            "Over-capacity fill changed the existing draft or left a pending autofill");
                    require(ItemStack.matches(originalPostcard, Minecraft.getInstance().player.getInventory().getItem(0))
                                    && serverPostcard != null && serverPostcard.isDone()
                                    && ItemStack.matches(originalPostcard, serverPostcard.join()),
                            "Rejected overflow changed the real client/server postcard components");
                    ContactQuestsPostcardLayoutAssertions.verify("client.postcard.layout." + variant);
                    record(id, "Ordinary right-hand fill rejected oversized text before editing; original native draft and client/server card preserved");
                }
                case "done" -> pressDone();
                case "snapshot_saved" -> requestSavedPostcard();
                case "seal_red_packet" -> {
                    require(activeType.equals("red_packet") && activeVariant.equals("quick_move_exact"), "Wrong seal boundary");
                    verifyContainer();
                    Object pack = read(Minecraft.getInstance().screen, "buttonPack");
                    require(pack instanceof Button && ((Button) pack).active, "Native red packet Seal is disabled");
                    ((Button) pack).onPress();
                }
                case "snapshot_sealed" -> {
                    var mc = Minecraft.getInstance(); UUID owner = mc.player.getUUID();
                    require(mc.screen == null, "Native Seal did not close the editor");
                    serverRedPacket = mc.getSingleplayerServer().submit(() -> mc.getSingleplayerServer().getPlayerList()
                            .getPlayer(owner).getInventory().items.stream()
                            .filter(s -> s.getItem() instanceof com.flechazo.contact.common.item.RedPacketItem).findFirst().orElseThrow().copy());
                }
                case "verify_sealed" -> {
                    require(serverRedPacket != null && serverRedPacket.isDone(), "Server sealed item snapshot is missing");
                    var serverCard = serverRedPacket.join();
                    var clientCard = Minecraft.getInstance().player.getInventory().items.stream()
                            .filter(s -> s.getItem() instanceof com.flechazo.contact.common.item.RedPacketItem).findFirst().orElseThrow();
                    var inside = serverCard.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,
                            net.minecraft.world.item.component.ItemContainerContents.EMPTY).stream().toList();
                    require(ItemStack.matches(serverCard, clientCard) && BLESSING.equals(serverCard.get(
                                    com.flechazo.contact.common.component.ContactDataComponents.RED_PACKET_BLESSING.get()))
                                    && inside.stream().filter(s -> s.is(Items.GOLD_INGOT)).mapToInt(ItemStack::getCount).sum() == 2
                                    && inside.stream().filter(s -> !s.isEmpty()).count() == 1
                                    && inventoryCount(Minecraft.getInstance().player, new ItemStack(Items.GOLD_INGOT)) == 0
                                    && inventoryCount(Minecraft.getInstance().player, new ItemStack(Items.DIRT)) == 3,
                            "Real native Seal did not persist exact contents/blessing on both sides");
                    record(id, "Native Seal packet persisted the exact autofilled blessing and gold x2 in an actual client/server Contact red packet");
                }
                case "verify_saved" -> {
                    verifySavedPostcard();
                    record(id, "Native Done saved the exact Chinese/newline text on both client and server postcard items");
                }
                case "close_early" -> {
                    closeEarly();
                    record(id, "Closed the native editor while ordinary animated autofill was still incomplete");
                }
                case "verify_timeout" -> {
                    verifyTimeout();
                    record(id, "More than 100 client ticks after close: scheduled state cleared and the real card stayed unchanged");
                }
                case "reopen" -> reopenWithoutScheduling();
                case "verify_idle" -> {
                    verifyReopenedIdle();
                    record(id, "Reopening real stationery after timeout did not resume stale typing");
                }
                case "restore" -> restore();
                default -> throw new IllegalArgumentException("Unknown autofill boundary phase: " + phase);
            }
        } catch (Exception error) {
            try { restore(); } catch (Exception cleanup) { error.addSuppressed(cleanup); }
            ContactQuestsClientTestController.assertCase(id, false, phase + ": " + error);
        }
    }

    private static void prepare(String type, String variant) throws Exception {
        require(!scheduled(filler(type)), "Previous autofill is still active");
        closeScreen();
        restore();
        activeType = type;
        activeVariant = variant;
        ordinaryEntry = false;
        ordinarySubmit = false;
        cancelledBeforeComplete = false;
        timeoutVerified = false;
        serverContainer = null;
        serverPostcard = null;
        serverRedPacket = null;
        observedPrefill = null;
        observationFailure = null;
        overflowDraftPrimed = false;
        originalPostcard = ItemStack.EMPTY;
        String seed;
        switch (type + "/" + variant) {
            case "parcel/quick_move_exact" -> seed = "parcel_exact";
            case "red_packet/quick_move_exact" -> seed = "red_packet_exact";
            case "red_packet/prefilled_remaining" -> seed = "red_packet_prefilled";
            case "red_packet/wrong_item_preserved" -> seed = "red_packet_wrong";
            case "red_packet/missing_item" -> seed = "red_packet_missing";
            case "postcard/instant", "postcard/cancel", "postcard/overflow_instant", "postcard/overflow_animated", "postcard/overflow_narrow" -> {
                previousSpeed = ContactConfig.INSTANCE.getAutoFillSpeed().get();
                PostcardTask task = (PostcardTask) task(type);
                previousPostcardText = task.getPostcardText();
                previousPostcardStyle = task.getPostcardStyle();
                task.setPostcardStyle(variant.equals("overflow_narrow") ? "contact:icarus_wings" : "contact:default");
                boolean instant = variant.equals("instant");
                ContactConfig.INSTANCE.getAutoFillSpeed().set(instant || variant.equals("overflow_instant") ? 0 : 4);
                task.setPostcardText(switch (variant) {
                    case "instant" -> INSTANT_TEXT;
                    case "cancel" -> CANCEL_TEXT;
                    case "overflow_instant" -> "界".repeat(1024);
                    case "overflow_animated" -> "一行\n".repeat(15);
                    case "overflow_narrow" -> "山".repeat(90);
                    default -> throw new IllegalArgumentException(variant);
                });
                seed = instant ? "postcard_inventory" : variant.equals("overflow_narrow") ? "postcard_narrow" : "postcard";
            }
            default -> throw new IllegalArgumentException("Unknown boundary case: " + type + "/" + variant);
        }
        invokeController("sendServerCommand", new Class<?>[]{String.class}, "contactquests_test seed_autofill " + seed);
    }

    private static void open() throws Exception {
        Player player = Minecraft.getInstance().player;
        int stationerySlot = activeVariant.equals("quick_move_exact") || activeVariant.equals("instant") ? 18 : 0;
        ItemStack stationery = player.getInventory().getItem(stationerySlot);
        if (activeType.equals("postcard")) {
            require(stationery.getItem() instanceof PostcardItem && stationery.getCount() == 1,
                    "Expected one real postcard in inventory " + stationerySlot);
            require(PostcardItem.getText(stationery).isEmpty(), "Seeded postcard already contains text");
            originalPostcard = stationery.copy();
        } else {
            int good = activeType.equals("parcel") ? 2 : switch (activeVariant) {
                case "quick_move_exact" -> 2;
                case "prefilled_remaining" -> 4;
                case "wrong_item_preserved" -> 3;
                case "missing_item" -> 0;
                default -> throw new IllegalArgumentException(activeVariant);
            };
            int dirt = activeVariant.equals("wrong_item_preserved") ? 4 : 3;
            require(activeType.equals("parcel") ? stationery.getItem() instanceof EnvelopeItem
                    : stationery.getItem() instanceof RedPacketEnvelopeItem, "Expected native stationery");
            require(inventoryCount(player, goodItem()) == good && inventoryCount(player, new ItemStack(Items.DIRT)) == dirt,
                    "Server seed suffix did not provide exact quantities before opening: expected=" + good + "/" + dirt
                            + "; actual=" + inventoryCount(player, goodItem()) + "/" + inventoryCount(player, new ItemStack(Items.DIRT)));
            require(task(activeType).getCount() == 2
                    && ClientQuestFile.INSTANCE.selfTeamData.getProgress(task(activeType)) == 0,
                    "Boundary fixture requires unfinished count=2, progress=0");
        }
        invokeController("openTaskDisplay", new Class<?>[]{String.class}, activeType);
        Object gui = Minecraft.getInstance().screen instanceof IScreenWrapper wrapper ? wrapper.getGui() : null;
        require(gui instanceof BaseValidTaskScreen<?>, "Ordinary TaskButton did not open the native valid-items screen");
        BaseValidTaskScreen<?> valid = (BaseValidTaskScreen<?>) gui;
        require(!ClientQuestFile.INSTANCE.canEdit() && valid.getTask().id == task(activeType).id,
                "Wrong task or editor mode at ordinary entry");
        ordinaryEntry = true;
        if (activeType.equals("postcard") && activeVariant.startsWith("overflow_")) {
            var tooltip = new dev.ftb.mods.ftblibrary.util.TooltipList();
            valid.getSubmitButton().addMouseOverText(tooltip);
            // FTB wraps translated tooltip components; compare normalized visible text.
            var getText = ContactQuestsClientTestController.class.getDeclaredMethod("tooltipText", dev.ftb.mods.ftblibrary.util.TooltipList.class);
            getText.setAccessible(true);
            String visible = ((String) getText.invoke(null, tooltip)).replaceAll("\\s+", " ");
            String warning = net.minecraft.network.chat.Component.translatable("contactquests.postcard.text_too_long").getString().replaceAll("\\s+", " ");
            require(visible.contains(warning), "Ordinary overflow Submit tooltip omits the actual capacity warning");
            ContactQuestsPostcardLayoutAssertions.verifyPreview(
                    (com.creeperyang.contactquests.client.gui.ValidPostcardItemsScreen) valid,
                    "client.postcard.preview." + activeVariant);
        }
    }

    private static void submit() throws Exception {
        require(ordinaryEntry, "No ordinary TaskButton entry");
        Object gui = Minecraft.getInstance().screen instanceof IScreenWrapper wrapper ? wrapper.getGui() : null;
        require(gui instanceof BaseValidTaskScreen<?>, "Valid-items screen was closed before Submit");
        BaseValidTaskScreen<?> valid = (BaseValidTaskScreen<?>) gui;
        require(!ClientQuestFile.INSTANCE.canEdit() && valid.getSubmitButton().getPosX() > valid.getBackButton().getPosX(),
                "Submit is not the right-hand ordinary button");
        invokeController("clickTaskSubmit", new Class<?>[]{String.class}, activeType);
        require(scheduled(filler(activeType)), "Actual Submit did not schedule autofill");
        ordinarySubmit = true;
    }

    private static void primeDraft() throws Exception {
        if (activeVariant.startsWith("overflow_")) {
            require(ordinarySubmit && scheduled(PostcardAutoFiller.INSTANCE)
                            && Minecraft.getInstance().screen instanceof PostcardEditScreen,
                    "Actual Contact editor must be open while the autofill action delay is still active");
            Object box = ((PostcardEditScreenAccessor) Minecraft.getInstance().screen).contactQuests$getTextBox();
            var widget = (net.minecraft.client.gui.components.AbstractWidget) box;
            String target = ((PostcardTask) task("postcard")).getPostcardText();
            var font = Minecraft.getInstance().font;
            require(target.length() >= 1024 || font.wordWrapHeight(target, widget.getWidth()) > widget.getHeight() * font.lineHeight / 12,
                    "Overflow fixture must exceed the actual native capacity");
            Object helper = read(box, "textInputUtil");
            helper.getClass().getMethod("insertText", String.class).invoke(helper, ORIGINAL_DRAFT);
            box.getClass().getMethod("shouldRefresh").invoke(box);
            require(ORIGINAL_DRAFT.equals(page()), "Could not create the original native draft");
        }
    }

    private static void requestContainerSnapshot() {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        require(server != null, "Boundary scenario requires its real integrated server");
        UUID playerId = mc.player.getUUID();
        String type = activeType;
        serverContainer = server.submit(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            require(player != null, "Server player missing");
            return snapshot(player, type);
        });
    }

    private static void verifyContainer() throws Exception {
        var mc = Minecraft.getInstance();
        require(activeType.equals("parcel") ? mc.screen instanceof EnvelopeScreen : mc.screen instanceof RedPacketEnvelopeScreen,
                "Expected genuine native container screen");
        require(!scheduled(filler(activeType)) && read(filler(activeType), "taskData") == null,
                "Autofill did not finish");
        require(serverContainer != null && serverContainer.isDone(), "Server container snapshot has not completed");
        ContainerSnapshot client = snapshot(mc.player, activeType);
        ContainerSnapshot server = serverContainer.join();
        if (activeVariant.equals("prefilled_remaining") || activeVariant.equals("wrong_item_preserved")) {
            require(observationFailure == null, "Failed to observe initial native prefill: " + observationFailure);
            require(observedPrefill != null && observedPrefill.containerId == client.containerId,
                    "Never observed actual input x1 and unchanged inventory before ordinary autofill moved items");
        }
        verifyQuantities(client);
        verifyQuantities(server);
        require(client.containerId == server.containerId && client.menu.equals(server.menu), "Client/server menu identity differs");
        require(ItemStack.matches(client.input, server.input), "Client/server input differs");
        if (activeType.equals("red_packet")) {
            require(mc.screen instanceof RedPacketEnvelopeScreenAccessor, "Native blessing field is unavailable");
            var box = ((RedPacketEnvelopeScreenAccessor) mc.screen).contactQuests$getBlessingsBox();
            require(box != null && BLESSING.equals(box.getValue()), "Native blessing text box differs");
            require(BLESSING.equals(client.blessing) && server.blessing.isEmpty(),
                    "Before native Seal, Contact keeps typed text locally; server blessing must still be its untouched default");
        }
    }

    private static void verifyQuantities(ContainerSnapshot state) {
        int input = 2, goodInventory = 0, dirtInventory = 3, totalGood = 2;
        ItemStack expected = goodItem().copyWithCount(2);
        switch (activeVariant) {
            case "quick_move_exact" -> {}
            case "prefilled_remaining" -> { goodInventory = 2; totalGood = 4; }
            case "wrong_item_preserved" -> {
                input = 1; expected = new ItemStack(Items.DIRT); goodInventory = 3; totalGood = 3;
            }
            case "missing_item" -> { input = 0; expected = ItemStack.EMPTY; totalGood = 0; }
            default -> throw new IllegalArgumentException(activeVariant);
        }
        require(ItemStack.matches(state.input, expected) && state.input.getCount() == input,
                "Wrong input for " + activeVariant + ": " + state);
        require(state.goodInventory == goodInventory && state.dirtInventory == dirtInventory && state.carried.isEmpty(),
                "Inventory remainder/cursor changed: " + state);
        int inputGood = state.input.is(goodItem().getItem()) ? state.input.getCount() : 0;
        require(inputGood + state.goodInventory == totalGood, "Item conservation failed: " + state);
        int inputDirt = state.input.is(Items.DIRT) ? state.input.getCount() : 0;
        require(inputDirt + state.dirtInventory == (activeVariant.equals("wrong_item_preserved") ? 4 : 3),
                "Unrelated dirt was lost or moved: " + state);
    }

    private static ContainerSnapshot snapshot(Player player, String type) {
        AbstractContainerMenu menu = player.containerMenu;
        require(type.equals("parcel") ? menu instanceof EnvelopeScreenHandler : menu instanceof RedPacketEnvelopeScreenHandler,
                "Snapshot is not the real native container: " + menu.getClass().getName());
        ItemStack good = new ItemStack(type.equals("parcel") ? Items.DIAMOND : Items.GOLD_INGOT);
        String blessing = menu instanceof RedPacketEnvelopeScreenHandler red ? red.blessings : "";
        return new ContainerSnapshot(menu.getClass().getName(), menu.containerId, menu.getSlot(0).getItem().copy(),
                inventoryCount(player, good), inventoryCount(player, new ItemStack(Items.DIRT)), menu.getCarried().copy(), blessing);
    }

    private static void verifyInstantText() throws Exception {
        require(ContactConfig.INSTANCE.getAutoFillSpeed().get() == 0, "Instant mode was not selected");
        require(INSTANT_TEXT.equals(page()), "Instant page did not preserve exact Chinese and newline characters");
        require(!scheduled(PostcardAutoFiller.INSTANCE) && read(PostcardAutoFiller.INSTANCE, "taskData") == null,
                "Instant autofill did not finish");
        ContactQuestsPostcardLayoutAssertions.verify("client.postcard.layout.instant");
    }

    private static void pressDone() throws Exception {
        require(activeVariant.equals("instant") && ordinaryEntry && ordinarySubmit, "Instant ordinary flow was not used");
        verifyInstantText();
        var screen = Minecraft.getInstance().screen;
        Object done = read(screen, "buttonDone");
        require(done instanceof Button && ((Button) done).active, "Native Done button is unavailable or disabled");
        ((Button) done).onPress();
        require(Minecraft.getInstance().screen == null, "Native Done did not close the editor");
    }

    private static void requestSavedPostcard() {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        require(server != null, "Expected the integrated server that owns the real postcard");
        UUID playerId = mc.player.getUUID();
        serverPostcard = server.submit(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            require(player != null, "Server player missing while checking saved card");
            return player.getInventory().getItem(0).copy();
        });
    }

    private static void verifySavedPostcard() {
        require(ordinaryEntry && ordinarySubmit && Minecraft.getInstance().screen == null, "Done flow did not finish");
        require(serverPostcard != null && serverPostcard.isDone(), "Saved server postcard snapshot has not completed");
        // Build only an expected copy; the actual item is written exclusively by native Done/network handling.
        ItemStack expected = PostcardItem.setText(originalPostcard.copy(), INSTANT_TEXT);
        ItemStack client = Minecraft.getInstance().player.getInventory().getItem(0);
        ItemStack server = serverPostcard.join();
        require(ItemStack.matches(expected, client) && ItemStack.matches(expected, server),
                "Done did not persist the exact text and original card components on both sides: client="
                        + PostcardItem.getText(client) + "; server=" + PostcardItem.getText(server));
    }

    private static void closeEarly() throws Exception {
        require(ordinaryEntry && ordinarySubmit && activeVariant.equals("cancel"), "Cancellation ordinary flow was not used");
        require(Minecraft.getInstance().screen instanceof PostcardEditScreen && scheduled(PostcardAutoFiller.INSTANCE),
                "Must close an active native editor");
        require(!CANCEL_TEXT.equals(page()) && ((Number) read(PostcardAutoFiller.INSTANCE, "currentIndex")).intValue() < CANCEL_TEXT.length(),
                "Autofill had already completed before close");
        ContactQuestsPostcardLayoutAssertions.verify("client.postcard.layout.animated_partial");
        closedAtTick = Minecraft.getInstance().player.tickCount;
        closeScreen();
        require(Minecraft.getInstance().screen == null, "Native editor did not close");
        cancelledBeforeComplete = true;
    }

    private static void verifyTimeout() throws Exception {
        var mc = Minecraft.getInstance();
        require(cancelledBeforeComplete && mc.player.tickCount - closedAtTick > 100, "Did not observe the real timeout interval");
        require(mc.screen == null && !scheduled(PostcardAutoFiller.INSTANCE) && read(PostcardAutoFiller.INSTANCE, "taskData") == null,
                "Closed-screen timeout left a stale scheduled task");
        require(ItemStack.matches(originalPostcard, mc.player.getInventory().getItem(0)), "Closing without Done changed the real postcard");
        require(serverPostcard != null && serverPostcard.isDone(), "Server cancellation snapshot has not completed");
        require(ItemStack.matches(originalPostcard, serverPostcard.join()), "Closing without Done changed the server-owned postcard");
        timeoutVerified = true;
    }

    private static void reopenWithoutScheduling() {
        var mc = Minecraft.getInstance();
        require(timeoutVerified && mc.gameMode != null && mc.player.getInventory().selected == 0,
                "Reopen requires the same held postcard after a verified timeout");
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
    }

    private static void verifyReopenedIdle() throws Exception {
        require(timeoutVerified && Minecraft.getInstance().screen instanceof PostcardEditScreen, "Real postcard editor did not reopen");
        require(page().isEmpty() && !scheduled(PostcardAutoFiller.INSTANCE) && read(PostcardAutoFiller.INSTANCE, "taskData") == null,
                "Stale autofill resumed after cancellation");
        require(ItemStack.matches(originalPostcard, Minecraft.getInstance().player.getInventory().getItem(0)),
                "Reopening changed the cancelled postcard");
    }

    private static String page() throws Exception {
        Object screen = Minecraft.getInstance().screen;
        require(screen instanceof PostcardEditScreen && screen instanceof PostcardEditScreenAccessor,
                "Native postcard editor/text accessor missing");
        Object box = ((PostcardEditScreenAccessor) screen).contactQuests$getTextBox();
        require(box != null, "Native postcard text box missing");
        return (String) read(box, "page");
    }

    private static void closeScreen() {
        var mc = Minecraft.getInstance();
        if (mc.screen != null) mc.screen.onClose();
        if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
    }

    private static void restore() throws Exception {
        if (previousSpeed != null) {
            ContactConfig.INSTANCE.getAutoFillSpeed().set(previousSpeed);
            previousSpeed = null;
        }
        if (previousPostcardText != null) {
            ((PostcardTask) task("postcard")).setPostcardText(previousPostcardText);
            previousPostcardText = null;
        }
        if (previousPostcardStyle != null) {
            ((PostcardTask) task("postcard")).setPostcardStyle(previousPostcardStyle);
            previousPostcardStyle = null;
        }
    }

    private static ContactTask task(String type) throws Exception {
        return (ContactTask) read(null, switch (type) {
            case "parcel" -> "parcelTask";
            case "red_packet" -> "redPacketTask";
            case "postcard" -> "postcardTask";
            default -> throw new IllegalArgumentException(type);
        });
    }

    private static Object filler(String type) {
        return switch (type) {
            case "parcel" -> ParcelAutoFiller.INSTANCE;
            case "red_packet" -> RedPacketAutoFiller.INSTANCE;
            case "postcard" -> PostcardAutoFiller.INSTANCE;
            default -> throw new IllegalArgumentException(type);
        };
    }

    private static boolean scheduled(Object filler) throws Exception {
        return (Boolean) read(filler, "isTaskScheduled");
    }

    private static ItemStack goodItem() {
        return new ItemStack(activeType.equals("parcel") ? Items.DIAMOND : Items.GOLD_INGOT);
    }

    private static int inventoryCount(Player player, ItemStack item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item.getItem())).mapToInt(ItemStack::getCount).sum();
    }

    private static Object read(Object owner, String name) throws Exception {
        Class<?> type = owner == null ? ContactQuestsClientTestController.class : owner.getClass();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static void invokeController(String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = ContactQuestsClientTestController.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try { method.invoke(null, arguments); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            throw error;
        }
        require(!(Boolean) read(null, "finished"), "Controller failed during " + name);
    }

    private static String assertionId(String type, String variant, String phase) {
        if (phase.equals("seal_red_packet") || phase.equals("snapshot_sealed") || phase.equals("verify_sealed"))
            return "client.autofill.red_packet.sealed_server_state";
        String suffix = switch (variant) {
            case "instant" -> phase.equals("verify_saved") ? "instant_done_saved" : "instant_text";
            case "cancel" -> switch (phase) {
                case "snapshot_saved", "verify_timeout" -> "cancel_timeout";
                case "reopen", "verify_idle", "restore" -> "cancel_reopen_idle";
                default -> "cancel_before_complete";
            };
            default -> variant;
        };
        return "client.autofill." + type + "." + suffix;
    }

    private static void record(String id, String message) {
        ContactQuestsClientTestController.assertCase(id, true, message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record ContainerSnapshot(String menu, int containerId, ItemStack input, int goodInventory,
                                     int dirtInventory, ItemStack carried, String blessing) {}
}
