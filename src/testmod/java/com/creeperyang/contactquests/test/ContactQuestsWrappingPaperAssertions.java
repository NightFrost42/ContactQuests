package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.client.gui.BaseValidTaskScreen;
import com.creeperyang.contactquests.client.util.ParcelAutoFiller;
import com.creeperyang.contactquests.quest.task.ParcelTask;
import com.flechazo.contact.client.gui.screen.WrappingPaperScreen;
import com.flechazo.contact.common.item.WrappingPaperItem;
import com.flechazo.contact.common.screenhandler.WrappingPaperScreenHandler;
import com.google.gson.JsonObject;
import dev.ftb.mods.ftblibrary.ui.IScreenWrapper;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Real ordinary TaskButton / Submit entry and native four-slot Contact wrapping paper. */
@EventBusSubscriber(modid = ContactQuestsTestMod.MOD_ID, value = Dist.CLIENT)
public final class ContactQuestsWrappingPaperAssertions {
    private static String variant;
    private static boolean submitted;
    private static Snapshot initialPrefill;
    private static CompletableFuture<Snapshot> serverSnapshot;

    private ContactQuestsWrappingPaperAssertions() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void observe(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (!submitted || initialPrefill != null || mc.player == null
                || !(mc.screen instanceof WrappingPaperScreen)
                || !(mc.player.containerMenu instanceof WrappingPaperScreenHandler)
                || !("prefilled".equals(variant) || "full".equals(variant))) return;
        try {
            var snapshot = snapshot(mc.player);
            if (!scheduled()) return;
            boolean ready = variant.equals("prefilled")
                    ? ItemStack.matches(snapshot.inputs.get(0), new ItemStack(Items.DIAMOND))
                        && snapshot.diamonds == 3 && snapshot.dirt == 3
                    : snapshot.inputs.stream().allMatch(s -> ItemStack.matches(s, new ItemStack(Items.DIRT)))
                        && snapshot.diamonds == 3 && snapshot.dirt == 3;
            if (ready && snapshot.cursor.isEmpty()) initialPrefill = snapshot;
        } catch (Exception error) {
            ContactQuestsClientTestController.assertCase(id(), false, "Observing actual wrapping inputs: " + error);
        }
    }

    public static void run(JsonObject step) {
        String phase = step.get("phase").getAsString();
        String requested = step.get("variant").getAsString();
        try {
            var mc = Minecraft.getInstance();
            require(mc.player != null && mc.getSingleplayerServer() != null, "Real integrated player/server required");
            if (phase.equals("prepare")) {
                require(!scheduled(), "Previous fill is still active");
                if (mc.screen != null) mc.screen.onClose();
                if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
                variant = requested; submitted = false; initialPrefill = null; serverSnapshot = null;
                invoke("sendServerCommand", "contactquests_test seed_autofill parcel_wrapping_" + variant);
                return;
            }
            require(requested.equals(variant), "Wrong wrapping fixture");
            switch (phase) {
                case "open" -> {
                    require(mc.player.getInventory().getItem(18).getItem() instanceof WrappingPaperItem,
                            "Expected real wrapping paper outside the hotbar");
                    invoke("openTaskDisplay", "parcel");
                    var gui = mc.screen instanceof IScreenWrapper wrapper ? wrapper.getGui() : null;
                    require(gui instanceof BaseValidTaskScreen<?> && !ClientQuestFile.INSTANCE.canEdit(),
                            "Ordinary non-editor TaskButton did not open valid-items page");
                    var task = (ParcelTask) read(null, "parcelTask");
                    require(((BaseValidTaskScreen<?>) gui).getTask().id == task.id
                                    && ClientQuestFile.INSTANCE.selfTeamData.getProgress(task) == 0 && task.getCount() == 2,
                            "Wrong ordinary task/count/progress");
                }
                case "submit" -> {
                    invoke("clickTaskSubmit", "parcel");
                    require(scheduled(), "Right-hand Submit did not schedule actual autofill");
                    submitted = true;
                }
                case "snapshot" -> {
                    UUID owner = mc.player.getUUID();
                    serverSnapshot = mc.getSingleplayerServer().submit(() -> snapshot(mc.getSingleplayerServer()
                            .getPlayerList().getPlayer(owner)));
                }
                case "verify" -> {
                    require(submitted && mc.screen instanceof WrappingPaperScreen && !scheduled()
                                    && read(ParcelAutoFiller.INSTANCE, "taskData") == null,
                            "Native wrapping paper/autofill did not finish");
                    require(serverSnapshot != null && serverSnapshot.isDone(), "Server snapshot not completed");
                    var client = snapshot(mc.player); var server = serverSnapshot.join();
                    verify(client); verify(server);
                    require(client.containerId == server.containerId, "Client/server menu identity differs");
                    for (int i = 0; i < 4; i++) require(ItemStack.matches(client.inputs.get(i), server.inputs.get(i)),
                            "Native input differs between client/server at " + i);
                    if (variant.equals("prefilled") || variant.equals("full"))
                        require(initialPrefill != null && initialPrefill.containerId == client.containerId,
                                "Actual moved inventory prefill was not observed before autofill");
                    ContactQuestsClientTestController.assertCase(id(), true,
                            "Ordinary TaskButton/Submit opened real four-slot wrapping paper: exact client/server inputs, remainder and empty cursor; " + client);
                }
                default -> throw new IllegalArgumentException(phase);
            }
        } catch (Exception error) {
            ContactQuestsClientTestController.assertCase("client.autofill.wrapping." + requested, false, phase + ": " + error);
        }
    }

    private static void verify(Snapshot state) {
        List<ItemStack> expected = new ArrayList<>(List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        int diamonds = 0, dirt = 3, totalDiamonds = 0, totalDirt = 3;
        switch (variant) {
            case "precise" -> { expected.set(0, new ItemStack(Items.DIAMOND, 2)); diamonds = 3; totalDiamonds = 5; }
            case "prefilled" -> {
                expected.set(0, new ItemStack(Items.DIAMOND)); expected.set(1, new ItemStack(Items.DIAMOND));
                diamonds = 2; totalDiamonds = 4;
            }
            case "full" -> {
                for (int i = 0; i < 4; i++) expected.set(i, new ItemStack(Items.DIRT));
                diamonds = 3; totalDiamonds = 3; totalDirt = 7;
            }
            case "missing" -> {}
            default -> throw new IllegalArgumentException(variant);
        }
        require(state.inputs.size() == 4, "Expected exactly four native input slots");
        for (int i = 0; i < 4; i++) require(ItemStack.matches(expected.get(i), state.inputs.get(i)),
                "Wrong wrapping input " + i + ": " + state);
        require(state.diamonds == diamonds && state.dirt == dirt && state.cursor.isEmpty(), "Wrong inventory/cursor: " + state);
        require(state.diamonds + count(state.inputs, Items.DIAMOND) == totalDiamonds
                && state.dirt + count(state.inputs, Items.DIRT) == totalDirt, "Item conservation failed: " + state);
    }

    private static Snapshot snapshot(Player player) {
        require(player != null && player.containerMenu instanceof WrappingPaperScreenHandler, "Not the actual wrapping menu");
        var menu = player.containerMenu;
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < 4; i++) inputs.add(menu.getSlot(i).getItem().copy());
        return new Snapshot(menu.containerId, inputs, count(player.getInventory().items, Items.DIAMOND),
                count(player.getInventory().items, Items.DIRT), menu.getCarried().copy());
    }

    private static int count(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        return stacks.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static String id() { return "client.autofill.wrapping." + variant; }
    private static boolean scheduled() throws Exception { return (Boolean) read(ParcelAutoFiller.INSTANCE, "isTaskScheduled"); }
    private static void invoke(String name, String value) throws Exception {
        Method method = ContactQuestsClientTestController.class.getDeclaredMethod(name, String.class);
        method.setAccessible(true); method.invoke(null, value);
        require(!(Boolean) read(null, "finished"), "Controller failed during " + name);
    }
    private static Object read(Object object, String name) throws Exception {
        for (Class<?> type = object == null ? ContactQuestsClientTestController.class : object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private record Snapshot(int containerId, List<ItemStack> inputs, int diamonds, int dirt, ItemStack cursor) {}
}
