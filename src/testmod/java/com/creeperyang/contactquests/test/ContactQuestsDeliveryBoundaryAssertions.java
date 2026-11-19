package com.creeperyang.contactquests.test;

import com.flechazo.contact.client.gui.screen.PostboxScreen;
import com.flechazo.contact.common.screenhandler.PostboxScreenHandler;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dispatch {@code delivery_boundary} to {@link #run(JsonObject)} with type and phase.
 * Phases: arm, move, staged, early, due, no_repeat, finish. All record the fixed ID
 * client.delivery_boundary.TYPE.PHASE. Use the server fixture and the real existing
 * postbox recipient/enquiry/selection/click_send actions, not a direct send helper.
 *
 * <p>arm requires the synchronized server seed in slot 18 and no screen/menu.
 * move clicks the real Contact player-inventory slot with QUICK_MOVE. Wait 10 ticks
 * and server .staged before staged. staged must run BEFORE set_recipient, proving
 * the native slot listener has already set status 1; the shared controller's
 * legacy status-0 fallback must not be needed. Send normally, wait for server .accepted and
 * 10 ticks before early (normal .due must still be absent). Close the screen before
 * the long wait using player.closeContainer(), not merely setScreen(null).
 * deleted_task then needs the server delete command and its .delete case.
 * Wait for server .due plus 10 ticks before due, then server
 * .no_repeat plus 10 ticks before no_repeat. Finally server verify/finish, wait
 * 10 ticks, finish. The latter waits are for actual notification/menu packets.
 *
 * <p>This observer counts actual received translated system messages for exactly
 * this NPC from arm through cleanup. same_tick expects one message for two real
 * due entries; ordinary parcel/postcard expect one; ender/deleted_task expect none.
 * It reads server milestones through the controller's existing result reader, so
 * dedicated multiplayer does not require a fake integrated-server snapshot.
 */
@EventBusSubscriber(modid = ContactQuestsTestMod.MOD_ID, value = Dist.CLIENT)
public final class ContactQuestsDeliveryBoundaryAssertions {
    private static final String DELIVERED_KEY = "contactquests.message.mail_delivered";
    private static Window active;

    private ContactQuestsDeliveryBoundaryAssertions() {}

    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        var window = active;
        if (window == null) return;
        var contents = event.getMessage().getContents();
        if (!(contents instanceof TranslatableContents translation) || !DELIVERED_KEY.equals(translation.getKey())) return;
        var args = translation.getArgs();
        if (args.length == 0) return;
        String recipient = args[0] instanceof Component component ? component.getString() : String.valueOf(args[0]);
        if (window.npc.equals(recipient)) window.notices.add(new Notice(event.getMessage().copy(), event.isOverlay()));
    }

    public static void run(JsonObject step) {
        String id = "client.delivery_boundary.invalid_step";
        try {
            String type = step.get("type").getAsString();
            String phase = step.get("phase").getAsString();
            String npc = ContactQuestsDeliveryBoundaryFixtures.recipient(type);
            id = "client.delivery_boundary." + type + "." + phase;
            var mc = Minecraft.getInstance();
            require(mc.player != null && mc.gameMode != null, "Real online player/game mode required");
            String detail;
            if (phase.equals("arm")) {
                require(active == null, "Finish the previous notification window before arming another");
                require(mc.screen == null && mc.player.containerMenu == mc.player.inventoryMenu
                                && mc.player.containerMenu.getCarried().isEmpty(), "Arm with no screen/menu and an empty cursor");
                requireServerCase(type, "prepare");
                var seed = ContactQuestsDeliveryBoundaryFixtures.mail(type);
                require(ItemStack.matches(mc.player.getInventory().getItem(18), seed), "Exact native mail has not synchronized into slot 18");
                requireGuardInventory(seed);
                active = new Window(type, npc, mc.player.getUUID(), seed);
                detail = "Armed an independent actual-message observer before normal item movement/send; exact native seed and guard inventory synchronized";
            } else {
                var window = active;
                require(window != null && window.type.equals(type) && window.player.equals(mc.player.getUUID()),
                        "Arm this exact type/player before executing its phases");
                detail = switch (phase) {
                    case "move" -> move(window);
                    case "staged" -> staged(window);
                    case "early" -> early(window);
                    case "due" -> due(window);
                    case "no_repeat" -> noRepeat(window);
                    case "finish" -> finish(window);
                    default -> throw new IllegalArgumentException("Unknown client delivery phase: " + phase);
                };
            }
            ContactQuestsClientTestController.assertCase(id, true, detail);
        } catch (Exception | LinkageError error) {
            ContactQuestsClientTestController.assertCase(id, false, error.toString());
        }
    }

    private static String move(Window window) {
        var mc = Minecraft.getInstance();
        var menu = postbox();
        require(!window.moved && menu.parcel.getItem(0).isEmpty(), "Move exactly one envelope/card into an empty real postbox");
        requireGuardInventory(window.seed);
        int found = -1;
        for (int i = 1; i < menu.slots.size(); i++) {
            if (!ItemStack.matches(menu.getSlot(i).getItem(), window.seed)) continue;
            require(found == -1, "Multiple exact mail seeds would make the ordinary move ambiguous");
            found = i;
        }
        require(found >= 1, "Exact native seed is not in the Contact postbox's player inventory slots");
        requireNoticeCount(window, 0);
        mc.gameMode.handleInventoryMouseClick(menu.containerId, found, 0, ClickType.QUICK_MOVE, mc.player);
        window.moved = true;
        return "Clicked the actual Contact player-inventory mail slot with normal QUICK_MOVE; staged phase checks the resulting real menu on both sides";
    }

    private static String staged(Window window) throws Exception {
        var menu = postbox();
        require(window.moved && !window.staged && ItemStack.matches(menu.parcel.getItem(0), window.seed),
                "Real Contact postbox did not receive the exact selected native envelope/card");
        ContactQuestsDeliveryBoundaryFixtures.requireUnsent(menu.parcel.getItem(0));
        require(menu.status == 1, "Native client slot listener must make unsent mail sendable before recipient entry");
        requireGuardInventory(ItemStack.EMPTY);
        requireServerCase(window.type, "staged");
        requireNoticeCount(window, 0);
        window.staged = true;
        return "Actual Contact postbox contains the exact unsent seed at native status 1 on client/server before recipient entry; inventory/cursor consumed it once without changing the guard";
    }

    private static String early(Window window) throws Exception {
        require(window.staged && !window.early, "Verify the real staged envelope before sending");
        require(postbox().parcel.getItem(0).isEmpty(), "Native Send did not clear the real postbox slot");
        requireGuardInventory(ItemStack.EMPTY);
        requireServerCase(window.type, "accepted");
        if (window.type.startsWith("ender_")) requireServerCase(window.type, "due");
        else require(!serverCases().has(ContactQuestsDeliveryBoundaryFixtures.caseId(window.type, "due")),
                "Normal mail already reached its due tick; early-delay assertion was not executed in time");
        requireNoticeCount(window, 0);
        window.early = true;
        return "Real Send consumed the mail once; no early notification; server captured normal zero-progress/exact queue or ender immediate exact progress with no queue";
    }

    private static String due(Window window) throws Exception {
        require(window.early && !window.due, "Complete the actual early-send assertion before due verification");
        if (window.type.equals("deleted_task")) requireServerCase(window.type, "delete");
        requireServerCase(window.type, "due");
        requireNoScreen();
        requireGuardInventory(ItemStack.EMPTY);
        requireNoticeCount(window, ContactQuestsDeliveryBoundaryFixtures.expectedNotifications(window.type));
        window.due = true;
        return "Actual online translated delivery notifications=" + window.notices.size()
                + "; exact server due progress/empty queue recorded in a natural tick"
                + (window.type.equals("same_tick") ? "; two entries for the same NPC produced exactly one notification" : "");
    }

    private static String noRepeat(Window window) throws Exception {
        require(window.due && !window.noRepeat, "Verify actual due notification count before the repeat window");
        requireServerCase(window.type, "no_repeat");
        requireNoScreen();
        requireGuardInventory(ItemStack.EMPTY);
        requireNoticeCount(window, ContactQuestsDeliveryBoundaryFixtures.expectedNotifications(window.type));
        window.noRepeat = true;
        return "Actual message count stayed exact through " + ContactQuestsDeliveryBoundaryFixtures.REPEAT_WINDOW_TICKS
                + " additional natural server ticks; no repeated receipt, queue entry or progress";
    }

    private static String finish(Window window) throws Exception {
        require(window.noRepeat, "Complete the actual no-repeat notification assertion before cleanup");
        requireServerCase(window.type, "verify");
        requireServerCase(window.type, "finish");
        requireNoScreen();
        requireNoticeCount(window, ContactQuestsDeliveryBoundaryFixtures.expectedNotifications(window.type));
        active = null;
        return "Server restored the original inventory/configuration; observed delivery message count remained exact through cleanup";
    }

    private static PostboxScreenHandler postbox() {
        var mc = Minecraft.getInstance();
        require(mc.screen instanceof PostboxScreen && mc.player.containerMenu instanceof PostboxScreenHandler,
                "Use the real Contact PostboxScreen and server-backed native menu");
        return (PostboxScreenHandler) mc.player.containerMenu;
    }

    private static void requireNoScreen() {
        var mc = Minecraft.getInstance();
        require(mc.screen == null && mc.player.containerMenu == mc.player.inventoryMenu
                        && mc.player.containerMenu.getCarried().isEmpty(),
                "Close the real postbox before waiting so natural integrated-server ticks can continue");
    }

    private static void requireGuardInventory(ItemStack mail) {
        var player = Minecraft.getInstance().player;
        require(player.getInventory().selected == 0 && player.containerMenu.getCarried().isEmpty(), "Native send changed selected slot/cursor");
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var expected = i == 0 ? new ItemStack(Items.STICK, 7) : i == 18 ? mail : ItemStack.EMPTY;
            require(ItemStack.matches(player.getInventory().getItem(i), expected),
                    "Native inventory count/components differ after mail movement/send at slot " + i);
        }
    }

    private static void requireNoticeCount(Window window, int expected) {
        require(window.notices.size() == expected, "Wrong actual online delivery notification count for "
                + window.npc + ": expected=" + expected + ", actual=" + window.notices.size());
        for (Notice notice : window.notices) {
            var translation = (TranslatableContents) notice.message.getContents();
            require(!notice.overlay && translation.getArgs().length == 1
                            && notice.message.getString().equals(Component.translatable(DELIVERED_KEY, window.npc).getString())
                            && TextColor.fromLegacyFormat(ChatFormatting.GREEN).equals(notice.message.getStyle().getColor()),
                    "Actual receipt has the wrong target/translated text/style/overlay channel: " + notice.message);
        }
    }

    private static void requireServerCase(String type, String phase) throws Exception {
        String id = ContactQuestsDeliveryBoundaryFixtures.caseId(type, phase);
        var cases = serverCases();
        require(cases.has(id) && cases.get(id).isJsonObject(), "Wait for the actual server boundary milestone object: " + id);
        var result = cases.getAsJsonObject(id);
        require(result.has("success") && result.get("success").getAsBoolean(),
                "Server boundary milestone failed: " + id + "; " + result);
    }

    private static JsonObject serverCases() throws Exception {
        Method reader = ContactQuestsClientTestController.class.getDeclaredMethod("editorServerResult");
        reader.setAccessible(true);
        var result = (JsonObject) reader.invoke(null);
        require(result.has("cases") && result.get("cases").isJsonObject(), "Actual server case evidence is not available");
        return result.getAsJsonObject("cases");
    }

    private record Notice(Component message, boolean overlay) {}

    private static final class Window {
        final String type;
        final String npc;
        final UUID player;
        final ItemStack seed;
        final List<Notice> notices = new ArrayList<>();
        boolean moved;
        boolean staged;
        boolean early;
        boolean due;
        boolean noRepeat;

        Window(String type, String npc, UUID player, ItemStack seed) {
            this.type = type;
            this.npc = npc;
            this.player = player;
            this.seed = seed.copy();
        }
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new IllegalStateException(detail);
    }
}
