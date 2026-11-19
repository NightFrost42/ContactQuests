package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.client.gui.BaseValidTaskScreen;
import com.creeperyang.contactquests.client.gui.ContactConfigScreen;
import com.creeperyang.contactquests.client.util.ParcelAutoFiller;
import com.creeperyang.contactquests.client.util.PostcardAutoFiller;
import com.creeperyang.contactquests.client.util.RedPacketAutoFiller;
import com.creeperyang.contactquests.config.ContactConfig;
import com.creeperyang.contactquests.quest.task.ContactTask;
import com.google.gson.JsonObject;
import dev.ftb.mods.ftblibrary.ui.IScreenWrapper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.WidgetType;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforge.client.gui.widget.ModListWidget;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Dispatch a controller scenario action to {@link #run(JsonObject)}. This class owns no seeds.
 *
 * <p>Task steps require {@code type=parcel|red_packet|postcard}, {@code variant=back|esc|inventory|recipe}:
 * prepare, wait 5 ticks, open, wait 2 ticks, back/esc/inventory, wait 5 ticks, snapshot,
 * wait 5 ticks, verify. Recipe is supported for the two native item grids only: after open,
 * recipe_click, wait 10 ticks, recipe_verify, recipe_return, wait 5 ticks, snapshot,
 * wait 5 ticks, verify. Recipe return must restore the very same submission screen.
 *
 * <p>Prerequisites: loaded integrated-server world, controller install_synthetic_tasks,
 * idle autofillers, unfinished/startable task, and synchronized seed_autofill inventory
 * for the chosen type (wait 20 ticks after that command). The existing diamond/gold seeds
 * have real vanilla recipes. Server snapshots read the owning server's TeamData by ID;
 * synthetic client tasks need not be inserted into the server graph.
 * The inventory variant requires the current inventory binding to match E and sends E
 * through the active screen. Its onClosedByKey path may return false after navigating;
 * the exact restored quest page and unchanged client/server state determine success.
 *
 * <p>Mods steps use {@code type=mods_config}, no variant: pause, mods, search,
 * wait 2 ticks, select, open, wait 2 ticks, cancel, wait 2 ticks, back, resume.
 * Start in world with no screen/container open. These use the real pause-menu Mods
 * button, list row, Config button/factory, Cancel, Mods Done, and Return to Game.
 * No configuration is saved. JEI is runtime-only, so its state is inspected through
 * the pinned public interfaces rather than adding a compile dependency or invoking show().
 *
 * <p>Every phase records its own fixed case ID:
 * client.navigation.TYPE.VARIANT.PHASE, or client.navigation.mods_config.PHASE.
 * Failures are recorded, never converted into a successful compatibility fallback.
 */
public final class ContactQuestsUiNavigationAssertions {
    private static TaskNavigation taskNavigation;
    private static ModsNavigation modsNavigation;

    private ContactQuestsUiNavigationAssertions() {}

    public static void run(JsonObject step) {
        String id = "client.navigation.invalid_step";
        try {
            String type = step.get("type").getAsString();
            String phase = step.get("phase").getAsString();
            String detail;
            if (type.equals("mods_config")) {
                id = "client.navigation.mods_config." + phase;
                detail = runMods(phase);
            } else {
                String variant = step.get("variant").getAsString();
                id = "client.navigation." + type + "." + variant + "." + phase;
                detail = runTask(type, variant, phase);
            }
            ContactQuestsClientTestController.assertCase(id, true, detail);
        } catch (Exception error) {
            ContactQuestsClientTestController.assertCase(id, false, error.toString());
        }
    }

    private static String runTask(String type, String variant, String phase) throws Exception {
        require(List.of("parcel", "red_packet", "postcard").contains(type), "Unknown task type: " + type);
        require(List.of("back", "esc", "inventory", "recipe").contains(variant), "Unknown navigation variant: " + variant);
        require(!variant.equals("recipe") || !type.equals("postcard"), "Postcards have no native item recipe grid");
        requirePlayerInventory();
        requireIdleAutofillers();

        if (phase.equals("prepare")) {
            require(taskNavigation == null || taskNavigation.verified, "Previous navigation was not verified");
            ContactTask task = (ContactTask) read(null, switch (type) {
                case "parcel" -> "parcelTask";
                case "red_packet" -> "redPacketTask";
                default -> "postcardTask";
            });
            require(task != null && ClientQuestFile.INSTANCE.selfTeamData != null, "Install the synthetic tasks first");
            var team = ClientQuestFile.INSTANCE.selfTeamData;
            require(team.canStartTasks(task.getQuest()) && !team.isCompleted(task), "Task must be startable and unfinished");
            taskNavigation = new TaskNavigation(type, variant, task,
                    state(Minecraft.getInstance().player, team, task.id, task.getQuest().id),
                    serverState(task.id, task.getQuest().id));
            return "Captured full client inventory and task/quest progress; requested independent server baseline";
        }

        TaskNavigation nav = taskNavigation;
        require(nav != null && nav.type.equals(type) && nav.variant.equals(variant) && !nav.verified,
                "Prepare this exact task/variant before continuing");
        requireSameState(nav.before, clientState(nav), "Client navigation changed inventory or progress");
        switch (phase) {
            case "open" -> {
                require(nav.submission == null, "Submission page was already opened");
                require(nav.serverBefore.isDone(), "Server baseline pending; wait at least 5 ticks after prepare");
                requireSameInventory(nav.before.inventory, nav.serverBefore.join().inventory,
                        "Seed inventory has not synchronized between client and server");
                invokeControllerOpen(type);
                require(gui(Minecraft.getInstance().screen) instanceof BaseValidTaskScreen<?>,
                        "Ordinary TaskButton did not open the native submission page");
                nav.submission = Minecraft.getInstance().screen;
                nav.valid = (BaseValidTaskScreen<?>) gui(nav.submission);
                nav.parent = nav.valid.getPrevScreen();
                require(nav.valid.getTask().id == nav.task.id && !ClientQuestFile.INSTANCE.canEdit(),
                        "Wrong task or editor mode at ordinary entry");
                requireQuestParent(nav);
                require(nav.valid.findValidSlot(Minecraft.getInstance().player) >= 0
                                && nav.valid.getSubmitButton().getWidgetType() != WidgetType.DISABLED,
                        "Seed genuine stationery so this is a usable submission page");
            }
            case "back" -> {
                require(variant.equals("back"), "Back phase requires variant=back");
                requireSubmission(nav);
                clickFtb(nav.submission, nav.valid.getBackButton());
                requireReturnedQuest(nav);
                nav.returned = true;
            }
            case "esc" -> {
                require(variant.equals("esc"), "ESC phase requires variant=esc");
                requireSubmission(nav);
                require(nav.submission.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0), "Native screen did not consume ESC");
                requireReturnedQuest(nav);
                nav.returned = true;
            }
            case "inventory" -> {
                require(variant.equals("inventory"), "Inventory-key phase requires variant=inventory");
                requireSubmission(nav);
                var mc = Minecraft.getInstance();
                require(mc.options.keyInventory.matches(GLFW.GLFW_KEY_E, 0), "Expected the real inventory binding on E");
                // onClosedByKey navigates and returns false; assert the resulting page, not event consumption.
                nav.submission.keyPressed(GLFW.GLFW_KEY_E, 0, 0);
                requireReturnedQuest(nav);
                mc.screen.keyReleased(GLFW.GLFW_KEY_E, 0, 0);
                requireReturnedQuest(nav);
                nav.returned = true;
            }
            case "recipe_click" -> {
                require(variant.equals("recipe") && !nav.recipeClicked, "Wrong/repeated recipe click");
                requireSubmission(nav);
                Widget item = flatten(nav.valid.getMainPanel()).stream()
                        .filter(widget -> widget.getClass().getName().equals(
                                "com.creeperyang.contactquests.client.gui.BaseGridTaskScreen$ValidItemButton"))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Native valid-item grid is empty"));
                Object ingredient = item.getIngredientUnderMouse().orElseThrow().ingredient();
                require(ingredient instanceof ItemStack && !((ItemStack) ingredient).isEmpty(), "Item cell has no real ingredient");
                nav.recipeItem = ((ItemStack) ingredient).copy();
                clickFtb(nav.submission, item);
                nav.recipeClicked = true;
            }
            case "recipe_verify" -> {
                require(variant.equals("recipe") && nav.recipeClicked, "Recipe cell was not clicked");
                verifyRecipe(nav);
                nav.recipeVerified = true;
            }
            case "recipe_return" -> {
                require(variant.equals("recipe") && nav.recipeVerified, "Verify the real JEI screen before returning");
                verifyRecipe(nav);
                require(Minecraft.getInstance().screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0), "JEI did not consume ESC");
                requireSubmission(nav);
                nav.returned = true;
            }
            case "snapshot" -> {
                require(nav.returned && nav.serverAfter == null, "Finish the actual navigation before snapshot");
                requireFinalScreen(nav);
                nav.serverAfter = serverState(nav.task.id, nav.task.getQuest().id);
            }
            case "verify" -> {
                require(nav.returned && nav.serverAfter != null && nav.serverAfter.isDone(),
                        "Server final snapshot pending; wait at least 5 ticks after snapshot");
                requireFinalScreen(nav);
                requireSameState(nav.serverBefore.join(), nav.serverAfter.join(), "Server inventory/progress changed during navigation");
                requireSameInventory(nav.before.inventory, nav.serverAfter.join().inventory,
                        "Final client/server inventory does not match its original full snapshot");
                nav.verified = true;
            }
            default -> throw new IllegalArgumentException("Unknown task navigation phase: " + phase);
        }
        requireIdleAutofillers();
        requireSameState(nav.before, clientState(nav), "Client inventory/progress changed during " + phase);
        return type + "/" + variant + "/" + phase
                + ": actual UI navigation preserved full inventory, task/quest progress and idle autofillers"
                + (phase.equals("recipe_verify") ? "; JEI has OUTPUT focus for the clicked item and nonempty recipes" : "");
    }

    private static String runMods(String phase) throws Exception {
        requirePlayerInventory();
        requireIdleAutofillers();
        var mc = Minecraft.getInstance();
        if (phase.equals("pause")) {
            require(modsNavigation == null || modsNavigation.resumed, "Previous Mods navigation is unfinished");
            require(mc.screen == null, "Begin Mods navigation in the game, with no screen open");
            modsNavigation = new ModsNavigation(inventory(mc.player), config());
            mc.pauseGame(false);
            require(mc.screen instanceof PauseScreen pause && pause.showsPauseMenu(), "Normal pause menu did not open");
            modsNavigation.pause = mc.screen;
            return "Opened the normal in-game pause menu without constructing a configuration screen";
        }
        ModsNavigation nav = modsNavigation;
        require(nav != null && !nav.resumed, "Run mods_config/pause first");
        require(nav.config.equals(config()), "Navigation unexpectedly saved or changed configuration");
        requireSameInventory(nav.inventory, inventory(mc.player), "Mods navigation changed player inventory");
        switch (phase) {
            case "mods" -> {
                require(mc.screen == nav.pause && nav.mods == null, "Expected the initial pause menu");
                clickVanilla(nav.pause, button(nav.pause, Component.translatable("fml.menu.mods")));
                require(mc.screen instanceof ModListScreen, "Pause-menu Mods button did not open NeoForge Mods");
                nav.mods = (ModListScreen) mc.screen;
                require(read(nav.mods, "parentScreen") == nav.pause, "Mods has the wrong parent screen");
            }
            case "search" -> {
                require(mc.screen == nav.mods && nav.mods != null, "NeoForge Mods is not open");
                EditBox search = (EditBox) read(nav.mods, "search");
                search.setValue("ContactQuests");
                require(search.getValue().equals("ContactQuests"), "Native Mods search did not accept the filter");
            }
            case "select" -> {
                require(mc.screen == nav.mods && nav.mods != null, "NeoForge Mods is not open");
                var list = (ModListWidget) read(nav.mods, "modList");
                var entry = list.children().stream().filter(row -> row.getInfo().getModId().equals("contactquests"))
                        .findFirst().orElseThrow(() -> new IllegalStateException("ContactQuests row missing after search/tick"));
                require(!entry.isFocused(), "ContactQuests row was already selected before the real click");
                int row = list.children().indexOf(entry);
                int height = ((Number) read(list, "itemHeight")).intValue();
                list.setScrollAmount((double) row * height);
                Method rowTop = AbstractSelectionList.class.getDeclaredMethod("getRowTop", int.class);
                rowTop.setAccessible(true);
                double x = list.getRowLeft() + list.getRowWidth() / 2.0;
                double y = ((Number) rowTop.invoke(list, row)).doubleValue() + height / 2.0;
                require(list.isMouseOver(x, y), "ContactQuests row is outside the visible list");
                // This native row returns false after selecting; assert its actual selection, not that return value.
                nav.mods.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                nav.mods.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                require(selectedContactQuests(nav.mods), "Real row click did not select ContactQuests");
                Button config = (Button) read(nav.mods, "configButton");
                require(config.active && config.visible, "Selected mod has no enabled Config factory button");
            }
            case "open" -> {
                require(mc.screen == nav.mods && nav.mods != null && selectedContactQuests(nav.mods), "Select ContactQuests first");
                clickVanilla(nav.mods, (Button) read(nav.mods, "configButton"));
                require(mc.screen instanceof ContactConfigScreen, "Actual Config button/factory did not create ContactConfigScreen");
                nav.configScreen = mc.screen;
                require(read(nav.configScreen, "parent") == nav.mods, "Config factory lost the exact Mods parent");
                require(((EditBox) read(nav.configScreen, "autoFillSpeedBox")).getValue().equals(Integer.toString(nav.config.speed))
                                && ((EditBox) read(nav.configScreen, "retryIntervalBox")).getValue().equals(Integer.toString(nav.config.retry)),
                        "Factory-created screen did not initialize the live configuration fields");
            }
            case "cancel" -> {
                require(nav.configScreen != null && mc.screen == nav.configScreen, "Factory-created Config is not open");
                ((EditBox) read(nav.configScreen, "autoFillSpeedBox")).setValue(nav.config.speed == 9 ? "8" : "9");
                ((EditBox) read(nav.configScreen, "retryIntervalBox")).setValue(nav.config.retry == 44 ? "45" : "44");
                clickVanilla(nav.configScreen, button(nav.configScreen, CommonComponents.GUI_CANCEL));
                require(mc.screen == nav.mods && selectedContactQuests(nav.mods), "Cancel did not return to the selected Mods page");
                nav.cancelled = true;
            }
            case "back" -> {
                require(nav.cancelled && mc.screen == nav.mods, "Cancel the actual Config screen first");
                clickVanilla(nav.mods, (Button) read(nav.mods, "doneButton"));
                require(mc.screen == nav.pause, "Native Mods Done did not restore the exact pause menu");
            }
            case "resume" -> {
                require(nav.cancelled && mc.screen == nav.pause, "Expected pause menu after native Mods Done");
                clickVanilla(nav.pause, button(nav.pause, Component.translatable("menu.returnToGame")));
                require(mc.screen == null, "Return to Game did not close the menu");
                nav.resumed = true;
            }
            default -> throw new IllegalArgumentException("Unknown Mods navigation phase: " + phase);
        }
        require(nav.config.equals(config()), "Config navigation saved discarded edits");
        requireSameInventory(nav.inventory, inventory(mc.player), "Config navigation changed player inventory");
        requireIdleAutofillers();
        return "mods_config/" + phase + ": real native menu/list/button path; inventory and live configuration unchanged";
    }

    private static void verifyRecipe(TaskNavigation nav) throws Exception {
        Screen screen = Minecraft.getInstance().screen;
        require(screen != null && screen.getClass().getName().equals("mezz.jei.gui.recipes.RecipesGui"), "Real JEI RecipesGui is not open");
        require(read(screen, "parentScreen") == nav.submission, "JEI did not retain the exact native submission page");
        Object lookup = read(read(screen, "logic"), "state");
        Object focuses = callApi(lookup, "mezz.jei.gui.recipes.lookups.ILookupState", "getFocuses");
        List<?> all = (List<?>) callApi(focuses, "mezz.jei.api.recipe.IFocusGroup", "getAllFocuses");
        boolean matching = false;
        for (Object focus : all) {
            Object role = callApi(focus, "mezz.jei.api.recipe.IFocus", "getRole");
            Object typed = callApi(focus, "mezz.jei.api.recipe.IFocus", "getTypedValue");
            Optional<?> stack = (Optional<?>) callApi(typed, "mezz.jei.api.ingredients.ITypedIngredient", "getItemStack");
            if (role instanceof Enum<?> value && value.name().equals("OUTPUT") && stack.orElse(null) instanceof ItemStack item
                    && ItemStack.isSameItemSameComponents(nav.recipeItem, item)) matching = true;
        }
        require(matching, "JEI output focus does not match the actually clicked cell");
        Object focused = callApi(lookup, "mezz.jei.gui.recipes.lookups.ILookupState", "getFocusedRecipes");
        require(!((List<?>) callApi(focused, "mezz.jei.gui.recipes.lookups.IFocusedRecipes", "getRecipes")).isEmpty(),
                "JEI has no actual recipes for the clicked output");
    }

    private static void requireSubmission(TaskNavigation nav) {
        require(nav.submission != null && Minecraft.getInstance().screen == nav.submission
                        && gui(nav.submission) == nav.valid && nav.valid.getTask().id == nav.task.id
                        && !ClientQuestFile.INSTANCE.canEdit(), "Original ordinary submission page is not active");
    }

    private static void requireQuestParent(TaskNavigation nav) {
        require(gui(nav.parent) instanceof QuestScreen quest && quest.getViewedQuest() == nav.task.getQuest(),
                "Submission parent is not the original task's quest detail page");
    }

    private static void requireReturnedQuest(TaskNavigation nav) {
        requireQuestParent(nav);
        require(Minecraft.getInstance().screen == nav.parent && !ClientQuestFile.INSTANCE.canEdit(),
                "Back/ESC/inventory key did not restore the exact ordinary quest page");
    }

    private static void requireFinalScreen(TaskNavigation nav) {
        if (nav.variant.equals("recipe")) requireSubmission(nav);
        else requireReturnedQuest(nav);
    }

    private static void requirePlayerInventory() {
        Player player = Minecraft.getInstance().player;
        require(player != null && player.containerMenu == player.inventoryMenu && player.containerMenu.getCarried().isEmpty(),
                "Navigation requires a player with the ordinary inventory menu and an empty cursor");
    }

    private static void requireIdleAutofillers() throws Exception {
        for (Object filler : List.of(ParcelAutoFiller.INSTANCE, RedPacketAutoFiller.INSTANCE, PostcardAutoFiller.INSTANCE)) {
            require(Boolean.FALSE.equals(read(filler, "isTaskScheduled")) && read(filler, "taskData") == null,
                    "Navigation scheduled or inherited pending " + filler.getClass().getSimpleName());
        }
    }

    private static NavigationState clientState(TaskNavigation nav) {
        return state(Minecraft.getInstance().player, ClientQuestFile.INSTANCE.selfTeamData, nav.task.id, nav.task.getQuest().id);
    }

    private static CompletableFuture<NavigationState> serverState(long taskId, long questId) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        require(server != null, "Independent navigation snapshots require the integrated server");
        UUID owner = Minecraft.getInstance().player.getUUID();
        return server.submit(() -> {
            var player = server.getPlayerList().getPlayer(owner);
            require(player != null && player.containerMenu == player.inventoryMenu, "Server player/menu unavailable");
            return state(player, TeamData.get(player), taskId, questId);
        });
    }

    private static NavigationState state(Player player, TeamData team, long taskId, long questId) {
        return new NavigationState(inventory(player), team.getTeamId(), team.getProgress(taskId),
                team.getCompletedTime(taskId).map(java.util.Date::getTime).orElse(-1L),
                team.getCompletedTime(questId).map(java.util.Date::getTime).orElse(-1L));
    }

    private static InventoryState inventory(Player player) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) items.add(player.getInventory().getItem(i).copy());
        return new InventoryState(player.getUUID(), List.copyOf(items), player.getInventory().selected, player.containerMenu.getCarried().copy());
    }

    private static void requireSameState(NavigationState before, NavigationState after, String message) {
        requireSameInventory(before.inventory, after.inventory, message);
        require(before.team.equals(after.team) && before.progress == after.progress
                        && before.taskCompleted == after.taskCompleted && before.questCompleted == after.questCompleted,
                message + ": team/task progress/completion changed");
    }

    private static void requireSameInventory(InventoryState before, InventoryState after, String message) {
        require(before.owner.equals(after.owner) && before.selected == after.selected && before.items.size() == after.items.size()
                        && ItemStack.matches(before.carried, after.carried), message + ": owner/selected/cursor changed");
        for (int i = 0; i < before.items.size(); i++) {
            require(ItemStack.matches(before.items.get(i), after.items.get(i)), message + ": slot " + i + " changed");
        }
    }

    private static ConfigState config() {
        var config = ContactConfig.INSTANCE;
        return new ConfigState(config.getAutoFillSpeed().get(), config.getRetryInterval().get(), config.getEnableDeliveryTime().get());
    }

    private static Object gui(Screen screen) {
        return screen instanceof IScreenWrapper wrapper ? wrapper.getGui() : screen;
    }

    private static List<Widget> flatten(Panel panel) {
        List<Widget> widgets = new ArrayList<>();
        for (Widget widget : panel.getWidgets()) {
            widgets.add(widget);
            if (widget instanceof Panel child) widgets.addAll(flatten(child));
        }
        return widgets;
    }

    private static void clickFtb(Screen screen, Widget widget) {
        require(Minecraft.getInstance().screen == screen && widget.getWidth() > 0 && widget.getHeight() > 0,
                "Native item/button is not laid out on the active screen");
        double x = widget.getX() + widget.getWidth() / 2.0, y = widget.getY() + widget.getHeight() / 2.0;
        require(screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT), "Native FTB screen did not consume the actual cell/button click");
        screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static Button button(Screen screen, Component label) {
        return screen.children().stream().filter(child -> child instanceof Button button
                        && button.getMessage().getString().equals(label.getString()))
                .map(child -> (Button) child).findFirst()
                .orElseThrow(() -> new IllegalStateException("Visible native button missing: " + label.getString()));
    }

    private static void clickVanilla(Screen screen, Button button) {
        require(Minecraft.getInstance().screen == screen && screen.children().contains(button) && button.active && button.visible,
                "Native button is not active on the expected screen");
        double x = button.getX() + button.getWidth() / 2.0, y = button.getY() + button.getHeight() / 2.0;
        require(screen.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT), "Native screen did not consume the button click");
        screen.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static boolean selectedContactQuests(ModListScreen screen) throws Exception {
        Object selected = read(screen, "selected");
        return selected instanceof ModListWidget.ModEntry row && row.getInfo().getModId().equals("contactquests");
    }

    private static Object callApi(Object owner, String apiName, String method) throws Exception {
        Class<?> api = Class.forName(apiName);
        require(api.isInstance(owner), "Expected pinned runtime interface " + apiName);
        return invoke(api.getMethod(method), owner);
    }

    private static void invokeControllerOpen(String type) throws Exception {
        Method open = ContactQuestsClientTestController.class.getDeclaredMethod("openTaskDisplay", String.class);
        open.setAccessible(true);
        invoke(open, null, type);
        require(!Boolean.TRUE.equals(read(null, "finished")), "Controller failed opening the ordinary TaskButton");
    }

    private static Object invoke(Method method, Object owner, Object... arguments) throws Exception {
        try { return method.invoke(owner, arguments); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            throw error;
        }
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record InventoryState(UUID owner, List<ItemStack> items, int selected, ItemStack carried) {}
    private record NavigationState(InventoryState inventory, UUID team, long progress, long taskCompleted, long questCompleted) {}
    private record ConfigState(int speed, int retry, boolean delivery) {}

    private static final class TaskNavigation {
        final String type, variant;
        final ContactTask task;
        final NavigationState before;
        final CompletableFuture<NavigationState> serverBefore;
        Screen submission, parent;
        BaseValidTaskScreen<?> valid;
        ItemStack recipeItem = ItemStack.EMPTY;
        CompletableFuture<NavigationState> serverAfter;
        boolean recipeClicked, recipeVerified, returned, verified;

        TaskNavigation(String type, String variant, ContactTask task, NavigationState before,
                       CompletableFuture<NavigationState> serverBefore) {
            this.type = type;
            this.variant = variant;
            this.task = task;
            this.before = before;
            this.serverBefore = serverBefore;
        }
    }

    private static final class ModsNavigation {
        final InventoryState inventory;
        final ConfigState config;
        Screen pause, configScreen;
        ModListScreen mods;
        boolean cancelled, resumed;

        ModsNavigation(InventoryState inventory, ConfigState config) {
            this.inventory = inventory;
            this.config = config;
        }
    }
}
