package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.client.gui.TagSelectionScreen;
import com.creeperyang.contactquests.config.TagConfig;
import com.google.gson.JsonObject;
import dev.ftb.mods.ftblibrary.config.StringConfig;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.IScreenWrapper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.TextBox;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dispatch a new controller action (suggested name: tag_selection_ui) to run(step).
 * Existing controller testTagSelection and its original cases are not replaced.
 *
 * Enter sequence, variant=enter on every step:
 * prepare -> wait 5 -> create -> wait 5 -> blank_enter -> wait 5 -> verify_blank
 * -> type_name -> enter -> wait 5 -> verify -> restore.
 *
 * Local-cancel sequence, variant=cancel on every step:
 * prepare -> wait 5 -> create -> wait 5 -> type_name -> cancel -> wait 5 -> verify
 * -> reopen -> wait 5 -> verify_reopened -> restore.
 *
 * Waits allow FTB's scheduled refreshWidgets to rebuild the real creation row.
 * All characters, Enter and the row-local cancel use the live ScreenWrapper input API.
 * No production callbacks, confirmCreating, cancelCreating or doCancel are invoked directly.
 * The isolated config callback records every save attempt and its exact value without
 * closing the screen; this detects duplicate callbacks and delayed mutation after input.
 * Reflection reads state only. No server, game launch, fixture graph or real config is changed.
 * Run with a live ClientQuestFile and no concurrent Tag editor/session-tag mutation.
 */
public final class ContactQuestsTagSelectionAssertions {
    private static final String PREFIX = "client.tags.creation.";
    private static final String INITIAL_VALUE = "CQTagKeyboardOriginalValue";
    private static final String ENTER_TAG = "CQTagEnterKeyboard";
    private static final String CANCEL_TAG = "CQTagCancelDiscard";
    private static final String BLANK = "   ";
    private static Session active;

    private enum Stage {
        OPENED, CREATING, BLANK_ENTERED, BLANK_VERIFIED, NAMED, ENTERED,
        CANCELLED, VERIFIED, REOPENING, REOPENED
    }

    private record SaveCall(boolean accepted, String value) {}

    private static final class Session {
        final UUID player;
        final String variant;
        final Screen parent;
        final Set<String> originalTags;
        final StringConfig config = new StringConfig();
        final List<SaveCall> saves = new ArrayList<>();
        final TagSelectionScreen selector;
        Screen wrapper;
        Stage stage = Stage.OPENED;
        boolean blankVerified;
        boolean resultVerified;

        Session(Minecraft mc, String variant) {
            player = mc.player.getUUID();
            this.variant = variant;
            parent = mc.screen;
            originalTags = new HashSet<>(sessionTags());
            config.setValue(INITIAL_VALUE);
            selector = new TagSelectionScreen(config,
                    accepted -> saves.add(new SaveCall(accepted, config.getValue())), List.of());
        }

        String name() { return variant.equals("enter") ? ENTER_TAG : CANCEL_TAG; }
        String paddedName() { return "  " + name() + "  "; }
    }

    private ContactQuestsTagSelectionAssertions() {}

    public static void run(JsonObject step) {
        String variant = step.has("variant") ? step.get("variant").getAsString() : "invalid";
        String phase = step.has("phase") ? step.get("phase").getAsString() : "invalid";
        String failureId = PREFIX + variant + "." + phase;
        try {
            require(List.of("enter", "cancel").contains(variant), "Expected variant=enter|cancel");
            Minecraft mc = Minecraft.getInstance();
            require(mc.player != null && mc.level != null && ClientQuestFile.exists(),
                    "A live player/world/ClientQuestFile is required");
            if (phase.equals("prepare")) {
                require(active == null, "Restore the previous Tag boundary before preparing another");
                require(!sessionTags().contains(ENTER_TAG) && !sessionTags().contains(CANCEL_TAG),
                        "Boundary tag names already exist; do not overwrite previous session state");
                active = new Session(mc, variant);
                active.selector.openGui();
                active.wrapper = mc.screen;
                requireSelector(active);
                requireUnchanged(active);
                return;
            }

            Session s = active;
            require(s != null && s.variant.equals(variant) && mc.player.getUUID().equals(s.player),
                    "Prepare this exact variant and keep the same player/world");
            requireSelector(s);
            switch (phase) {
                case "create" -> {
                    require(s.stage == Stage.OPENED, "Create once after prepare");
                    requireUnchanged(s);
                    require(!creating(s) && hasSearch(s), "Initial Tag list/search is unavailable");
                    click(s, labelled(s.selector, "contactquests.gui.create_tag"));
                    require(creating(s) && !hasSearch(s), "Create click did not enter creation mode");
                    s.stage = Stage.CREATING;
                }
                case "blank_enter" -> {
                    require(variant.equals("enter") && s.stage == Stage.CREATING, "Blank Enter belongs to the enter variant");
                    replaceWithRealInput(s, BLANK);
                    requireUnchanged(s);
                    press(s, GLFW.GLFW_KEY_ENTER);
                    requireUnchanged(s);
                    s.stage = Stage.BLANK_ENTERED;
                }
                case "verify_blank" -> {
                    require(s.stage == Stage.BLANK_ENTERED, "Send actual whitespace Enter first");
                    requireUnchanged(s);
                    require(creating(s) && !hasSearch(s) && textBox(s).getText().equals(BLANK),
                            "Whitespace Enter should leave the creation row without creating a tag");
                    s.blankVerified = true;
                    s.stage = Stage.BLANK_VERIFIED;
                    record("enter_blank_noop", "Real whitespace typing and Enter left the exact Tag set/config unchanged and made zero save callbacks through delayed verification");
                }
                case "type_name" -> {
                    require(variant.equals("enter") ? s.stage == Stage.BLANK_VERIFIED : s.stage == Stage.CREATING,
                            "Wait for the creation row, and verify blank Enter before the valid Enter case");
                    replaceWithRealInput(s, s.paddedName());
                    requireUnchanged(s);
                    s.stage = Stage.NAMED;
                }
                case "enter" -> {
                    require(variant.equals("enter") && s.stage == Stage.NAMED && s.blankVerified,
                            "Type the padded name after whitespace verification");
                    require(textBox(s).getText().equals(s.paddedName()), "Padded text was changed before Enter");
                    press(s, GLFW.GLFW_KEY_ENTER);
                    requireEntered(s);
                    s.stage = Stage.ENTERED;
                }
                case "cancel" -> {
                    require(variant.equals("cancel") && s.stage == Stage.NAMED, "Type a nonempty draft before local cancel");
                    requireUnchanged(s);
                    Panel row = creationRow(s);
                    click(s, labelled(row, "gui.cancel")); // Row-scoped: never the screen's bottom Cancel.
                    requireUnchanged(s);
                    require(!creating(s) && hasSearch(s) && draft(s).isEmpty(), "Local Cancel did not reset draft/list state");
                    s.stage = Stage.CANCELLED;
                }
                case "verify" -> {
                    if (variant.equals("enter")) {
                        require(s.stage == Stage.ENTERED, "Use the real Enter key before verification");
                        requireEntered(s);
                        record("enter_trim_once", "One real Enter trimmed surrounding spaces, added exactly the intended Tag, set the exact config value and made precisely one save(true) callback with no delayed duplicate");
                    } else {
                        require(s.stage == Stage.CANCELLED, "Click the creation row's Cancel first");
                        requireCancelledList(s);
                        record("cancel_noop", "Real row-local Cancel discarded the nonempty draft, restored the searchable list, preserved the exact Tag/config state and made zero save callbacks");
                    }
                    s.resultVerified = true;
                    s.stage = Stage.VERIFIED;
                }
                case "reopen" -> {
                    require(variant.equals("cancel") && s.stage == Stage.VERIFIED, "Verify cancellation before reopening creation");
                    requireCancelledList(s);
                    click(s, labelled(s.selector, "contactquests.gui.create_tag"));
                    s.stage = Stage.REOPENING;
                }
                case "verify_reopened" -> {
                    require(variant.equals("cancel") && s.stage == Stage.REOPENING, "Reopen creation and allow refresh first");
                    requireUnchanged(s);
                    require(creating(s) && !hasSearch(s) && draft(s).isEmpty() && textBox(s).getText().isEmpty(),
                            "Cancelled draft reappeared in the rebuilt creation row");
                    s.stage = Stage.REOPENED;
                    record("cancel_reopen_empty", "Reopening the actual creation row starts empty; discarded text never reappears, with no Tag/config/save-callback changes");
                }
                case "restore" -> {
                    require(s.resultVerified && (variant.equals("enter") ? s.stage == Stage.VERIFIED : s.stage == Stage.REOPENED),
                            "Verify the whole variant before recording restoration");
                    if (variant.equals("enter")) requireEntered(s); else requireUnchanged(s);
                    List<SaveCall> beforeClose = List.copyOf(s.saves);
                    restore(s);
                    require(s.saves.equals(beforeClose), "Closing the isolated fixture introduced a save callback");
                    require(sessionTags().equals(s.originalTags) && mc.screen == s.parent,
                            "Original Tag set and parent screen were not restored");
                    record(variant + "_restored", "Removed only this fixture's added Tag and restored the original parent screen; no extra save callback and no pre-existing Tag changes");
                }
                default -> throw new IllegalArgumentException("Unknown Tag creation phase: " + phase);
            }
        } catch (Exception error) {
            Session s = active;
            if (s != null) {
                try { restore(s); } catch (Exception cleanup) { error.addSuppressed(cleanup); }
            }
            ContactQuestsClientTestController.assertCase(failureId, false, phase + ": " + error);
        }
    }

    private static void requireEntered(Session s) {
        Set<String> expected = new HashSet<>(s.originalTags);
        expected.add(ENTER_TAG);
        require(s.config.getValue().equals(ENTER_TAG) && sessionTags().equals(expected),
                "Enter must trim exactly, create no blank/padded extra Tags and preserve all original Tags");
        require(s.saves.equals(List.of(new SaveCall(true, ENTER_TAG))),
                "Expected exactly one save(true) with the trimmed value, actual=" + s.saves);
    }

    private static void requireUnchanged(Session s) {
        require(s.config.getValue().equals(INITIAL_VALUE), "Draft or cancellation changed the config value");
        require(sessionTags().equals(s.originalTags), "Draft or cancellation changed the exact session Tag set");
        require(s.saves.isEmpty(), "Draft or cancellation invoked a save callback: " + s.saves);
    }

    private static void requireCancelledList(Session s) throws ReflectiveOperationException {
        requireUnchanged(s);
        require(!creating(s) && hasSearch(s) && draft(s).isEmpty(), "Cancelled row did not restore search/list state");
        require(flatten(s.selector).stream().noneMatch(w -> w instanceof Panel p && p.getTitle().getString().equals("creation_row")),
                "Cancelled creation row remains after widget refresh");
        labelled(s.selector, "contactquests.gui.create_tag");
    }

    private static void replaceWithRealInput(Session s, String value) {
        TextBox text = textBox(s);
        click(s, text);
        require(text.isFocused(), "Actual creation textbox click did not focus it");
        int oldLength = text.getText().length();
        press(s, GLFW.GLFW_KEY_HOME);
        for (int i = 0; i < oldLength; i++) press(s, GLFW.GLFW_KEY_DELETE);
        require(text.getText().isEmpty(), "Native Home/Delete input did not clear the previous draft");
        for (char ch : value.toCharArray())
            require(s.wrapper.charTyped(ch, 0), "Native creation textbox rejected typed character: " + (int) ch);
        require(text.getText().equals(value), "Real typed draft differs from the exact expected text");
    }

    private static void press(Session s, int key) {
        requireSelector(s);
        require(s.wrapper.keyPressed(key, 0, 0), "Native Tag screen did not consume key " + key);
        s.wrapper.keyReleased(key, 0, 0);
    }

    private static void click(Session s, Widget widget) {
        requireSelector(s);
        require(widget.getWidth() > 0 && widget.getHeight() > 0, "Actual creation control has not been laid out; wait for refresh");
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        s.selector.updateGui((int) x, (int) y, 0);
        s.selector.updateMouseOver((int) x, (int) y);
        require(s.wrapper.mouseClicked(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT), "Native Tag screen did not consume the actual control click");
        s.wrapper.mouseReleased(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static Button labelled(Panel panel, String key) {
        String label = Component.translatable(key).getString();
        List<Button> matches = flatten(panel).stream()
                .filter(w -> w instanceof Button b && b.getTitle().getString().equals(label))
                .map(w -> (Button) w).toList();
        require(matches.size() == 1, "Expected exactly one scoped button " + key + ", actual=" + matches.size());
        return matches.get(0);
    }

    private static Panel creationRow(Session s) {
        List<Panel> rows = flatten(s.selector).stream()
                .filter(w -> w instanceof Panel p && p.getTitle().getString().equals("creation_row"))
                .map(w -> (Panel) w).toList();
        require(rows.size() == 1, "Expected the refreshed real creation row, actual=" + rows.size());
        return rows.get(0);
    }

    private static TextBox textBox(Session s) {
        List<TextBox> inputs = flatten(creationRow(s)).stream().filter(w -> w instanceof TextBox)
                .map(w -> (TextBox) w).toList();
        require(inputs.size() == 1, "Expected exactly the row's real draft textbox");
        return inputs.get(0);
    }

    private static boolean creating(Session s) throws ReflectiveOperationException { return (Boolean) read(s.selector, "isCreating"); }
    private static boolean hasSearch(Session s) throws ReflectiveOperationException { return (Boolean) read(s.selector, "hasSearchBox"); }
    private static String draft(Session s) throws ReflectiveOperationException { return (String) read(s.selector, "newTagValue"); }

    private static Object read(Object object, String name) throws ReflectiveOperationException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) { /* Look in the pinned native superclass. */ }
        }
        throw new NoSuchFieldException(object.getClass().getName() + "." + name);
    }

    private static List<Widget> flatten(Panel panel) {
        List<Widget> result = new ArrayList<>();
        for (Widget widget : panel.getWidgets()) {
            result.add(widget);
            if (widget instanceof Panel child) result.addAll(flatten(child));
        }
        return result;
    }

    private static void requireSelector(Session s) {
        require(Minecraft.getInstance().screen == s.wrapper && s.wrapper instanceof IScreenWrapper wrapper
                && wrapper.getGui() == s.selector, "Expected this fixture's actual Tag ScreenWrapper");
    }

    private static Set<String> sessionTags() { return TagConfig.Companion.getSessionTags(); }

    private static void restore(Session s) {
        // Remove only the successful test's own addition; never clear unrelated session state.
        if (!s.originalTags.contains(ENTER_TAG)) sessionTags().remove(ENTER_TAG);
        Minecraft.getInstance().setScreen(s.parent);
        if (active == s) active = null;
    }

    private static void record(String suffix, String message) {
        ContactQuestsClientTestController.assertCase(PREFIX + suffix, true, message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
