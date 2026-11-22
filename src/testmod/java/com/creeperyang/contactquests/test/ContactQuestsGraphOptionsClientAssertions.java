package com.creeperyang.contactquests.test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.IScreenWrapper;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.QuestShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Controller: run("first"|"rejoin"), retry false after 5 ticks. Requires the completed
 * graph-options server fixture and synchronized player/team; never seeds or sends commands.
 * Each phase records client.graph_options.PHASE.{fields,chapter_autofocus,quest_buttons,rendered}.
 * Screen mouse/keyboard events perform navigation. Reflection only observes native state.
 * A passive Render.Post listener saves real framebuffer PNGs and native geometry JSON.
 */
public final class ContactQuestsGraphOptionsClientAssertions {
    private enum Stage { SYNC, CHAPTER, CHAPTER_FRAME, FOCUS_FRAME, INHERITED, INHERITED_FRAME, DONE }
    private static final Set<String> completed = new HashSet<>();
    private static Session session;
    private static boolean listening;

    private static final class Session {
        final String phase;
        final long started = System.nanoTime();
        final JsonObject evidence = new JsonObject();
        Stage stage = Stage.SYNC;
        String currentCase = "fields", pending = "Awaiting player and real graph synchronization";
        int calls, frames, panKeys;
        boolean observed, panning, panPrimed;
        double panStartX, panStartY, panX, panY;
        Exception renderError;
        ClientQuestFile file;
        QuestScreen gui;
        Screen screen;
        Panel chapters;
        Button chapterButton;
        Path folder;

        Session(String phase) { this.phase = phase; }
        String caseId() { return "client.graph_options." + phase + "." + currentCase; }
        void stage(Stage next) { stage = next; frames = 0; observed = false; }
    }

    public static boolean run(String phase) {
        if (!List.of("first", "rejoin").contains(phase)) throw new IllegalArgumentException("Unknown graph UI phase: " + phase);
        if (completed.contains(phase)) return true;
        if (session == null || session.stage == Stage.DONE) session = new Session(phase);
        Session s = session;
        try {
            require(s.phase.equals(phase), "Finish the active graph UI phase before starting another");
            require(++s.calls <= 240 && System.nanoTime() - s.started < 60_000_000_000L,
                    "Graph UI timed out at " + s.stage + ": " + s.pending);
            if (s.renderError != null) throw s.renderError;
            var mc = Minecraft.getInstance();
            if (s.stage == Stage.SYNC) {
                var file = ClientQuestFile.INSTANCE;
                if (mc.player == null || mc.level == null || file == null || file.selfTeamData == null) return false;
                try {
                    ContactQuestsGraphOptionsAssertions.verifyClientFields(file, mc.level.registryAccess());
                } catch (IllegalStateException | NullPointerException | NoSuchElementException pending) {
                    s.pending = pending.toString();
                    return false;
                }
                s.file = file;
                s.folder = Path.of(System.getProperty("contactquests.test.clientResult")).toAbsolutePath().getParent().resolve("screenshots");
                s.evidence.addProperty("phase", phase);
                s.evidence.addProperty("pid", ProcessHandle.current().pid());
                s.evidence.addProperty("player", mc.player.getUUID().toString());
                s.evidence.addProperty("locale", Minecraft.getInstance().getLanguageManager().getSelected());
                pass(s, "Real ClientQuestFile options, NBT icons, dependencies and both empty-description semantics synchronized");
                s.currentCase = "chapter_autofocus";
                if (!listening) {
                    MinecraftForge.EVENT_BUS.addListener(ContactQuestsGraphOptionsClientAssertions::afterRender);
                    listening = true;
                }
                // A fresh native view excludes unrelated remembered scroll positions; all objects are real synced objects.
                s.gui = new QuestScreen(file, null);
                s.gui.openGui();
                s.screen = mc.screen;
                require(s.screen instanceof IScreenWrapper wrapper && wrapper.getGui() == s.gui, "Native QuestScreen did not open");
                s.chapters = (Panel) read(s.gui, "chapterPanel");
                movePointer(5, s.screen.height / 2.0);
                s.stage(Stage.CHAPTER);
                return false;
            }
            requireScreen(s);
            switch (s.stage) {
                case CHAPTER -> {
                    s.pending = "Expanding/scrolling the native chapter list";
                    if (!Boolean.TRUE.equals(read(s.chapters, "expanded")) || s.chapters.getX() < 0) {
                        movePointer(5, s.screen.height / 2.0);
                        return false;
                    }
                    s.chapterButton = chapterButton(s);
                    Rect row = bounds(s.chapterButton), list = bounds(s.chapters);
                    if (row.y < list.y || row.bottom() > list.bottom()) {
                        double x = Math.max(5, list.x + 8), y = list.y + list.h / 2.0;
                        movePointer(x, y);
                        s.gui.updateMouseOver((int) x, (int) y);
                        double direction = row.y < list.y ? 1 : -1;
                        require(s.screen.mouseScrolled(x, y, direction * 3), "Native chapter-list wheel event was not consumed");
                        return false;
                    }
                    require(inside(row, list), "Chapter row lies outside the native list: " + row);
                    movePointer(row.cx(), row.cy());
                    s.stage(Stage.CHAPTER_FRAME);
                }
                case CHAPTER_FRAME -> {
                    if (!s.observed) {
                        Rect row = bounds(s.chapterButton);
                        movePointer(row.cx(), row.cy());
                        return false;
                    }
                    var chapter = s.file.getChapter(id("chapter", "main"));
                    require(read(s.gui, "selectedChapter") != chapter, "Graph chapter was already selected before the actual click");
                    require(read(s.gui, "pendingPersistedData") == null,
                            "Pending persisted scroll data would bypass the native autofocus branch");
                    click(s, s.chapterButton);
                    require(read(s.gui, "selectedChapter") == chapter, "Native ChapterButton did not select the Graph chapter");
                    movePointer(s.gui.questPanel.getX() + s.gui.questPanel.getWidth() - 4, s.screen.height / 2.0);
                    s.pending = "Awaiting actual focus-page render";
                    s.stage(Stage.FOCUS_FRAME);
                }
                case FOCUS_FRAME -> {
                    if (!s.observed) {
                        movePointer(s.gui.questPanel.getX() + s.gui.questPanel.getWidth() - 4, s.screen.height / 2.0);
                        return false;
                    }
                    pass(s, "Actual ChapterPanel$ChapterButton click opened Graph; rendered center=6,-4 and focus button is centered; chapter title/NBT icon verified");
                    s.currentCase = "quest_buttons";
                    s.stage(Stage.INHERITED);
                }
                case INHERITED -> {
                    s.pending = "Bringing the inherited-shape QuestButton into the real viewport";
                    if (s.panning) {
                        s.screen.mouseReleased(s.panX, s.panY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                        s.panning = false;
                        return false;
                    }
                    if (s.panPrimed) {
                        if (Math.abs(s.gui.getMouseX() - (int) s.panStartX) > 2 || Math.abs(s.gui.getMouseY() - (int) s.panStartY) > 2) {
                            movePointer(s.panStartX, s.panStartY);
                            return false;
                        }
                        require(s.screen.mouseClicked(s.panStartX, s.panStartY, GLFW.GLFW_MOUSE_BUTTON_LEFT), "Native canvas drag did not start");
                        movePointer(s.panX, s.panY);
                        s.panPrimed = false;
                        s.panning = true;
                        return false;
                    }
                    var button = questButton(s, "inherited");
                    Rect rect = bounds(button), viewport = viewport(s);
                    // Drag the empty QuestPanel canvas with the mouse without editing quest coordinates or zoom.
                    int dx = rect.x < viewport.x + 4 ? 48 : rect.right() > viewport.right() - 4 ? -48 : 0;
                    int dy = rect.y < viewport.y + 4 ? 48 : rect.bottom() > viewport.bottom() - 4 ? -48 : 0;
                    if (dx != 0 || dy != 0) pan(s, viewport, dx, dy);
                    else s.stage(Stage.INHERITED_FRAME);
                }
                case INHERITED_FRAME -> {
                    if (!s.observed) return false;
                    ContactQuestsGraphOptionsAssertions.verifyClientFields(s.file, mc.level.registryAccess());
                    pass(s, "Actual rendered QuestButtons: explicit square/1.75 and inherited circle/1.25; native titles, exact focus icon name/style/NBT and pixel sizes verified");
                    s.currentCase = "rendered";
                    s.evidence.addProperty("panKeyEvents", s.panKeys);
                    s.evidence.addProperty("success", true);
                    writeJson(s.folder.resolve("graph-options-" + phase + "-result.json"), s.evidence);
                    s.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    require(mc.screen != s.screen, "Native ESC did not close the Graph screen");
                    pass(s, "Chapter, autofocus and inherited-button native Render.Post observations saved as PNG/geometry JSON; fields unchanged after navigation; " + s.folder);
                    s.stage(Stage.DONE);
                    completed.add(phase);
                    return true;
                }
                default -> throw new IllegalStateException("Unexpected graph UI stage: " + s.stage);
            }
            return false;
        } catch (Exception error) {
            s.evidence.addProperty("success", false);
            s.evidence.addProperty("error", s.stage + ": " + error);
            try {
                if (s.folder != null) writeJson(s.folder.resolve("graph-options-" + s.phase + "-result.json"), s.evidence);
            } catch (Exception evidenceError) { error.addSuppressed(evidenceError); }
            s.stage(Stage.DONE);
            completed.add(s.phase);
            ContactQuestsClientTestController.assertCase(s.caseId(), false, error.toString());
            return true;
        }
    }

    /** Observe only after Minecraft rendered the active native screen; never call draw/render ourselves. */
    private static void afterRender(ScreenEvent.Render.Post event) {
        Session s = session;
        if (s == null || s.observed || s.renderError != null || event.getScreen() != s.screen
                || !List.of(Stage.CHAPTER_FRAME, Stage.FOCUS_FRAME, Stage.INHERITED_FRAME).contains(s.stage)) return;
        try {
            requireScreen(s);
            if (s.stage != Stage.CHAPTER_FRAME && !Boolean.TRUE.equals(read(s.chapters, "expanded"))
                    && s.chapters.getX() > -s.chapters.getWidth()) return; // Let the native slide-out animation finish.
            if (++s.frames < 2) return;
            JsonObject observed = new JsonObject();
            observed.addProperty("renderFrames", s.frames);
            observed.addProperty("guiWidth", s.screen.width);
            observed.addProperty("guiHeight", s.screen.height);
            observed.addProperty("guiScale", Minecraft.getInstance().getWindow().getGuiScale());
            observed.addProperty("zoom", s.gui.getZoom());
            Rect pixels;
            String view;
            if (s.stage == Stage.CHAPTER_FRAME) {
                view = "chapter";
                require(s.chapters.shouldDraw() && Boolean.TRUE.equals(read(s.chapters, "expanded"))
                        && s.chapters.getX() == 0, "Native chapter list collapsed before it rendered");
                require(s.chapterButton.getTitle().getString().equals("Graph options chapter"), "Native ChapterButton title differs");
                observed.addProperty("icon", verifyIcon(s.chapterButton, true));
                pixels = bounds(s.chapterButton);
                require(inside(pixels, bounds(s.chapters)) && s.chapterButton.shouldDraw(), "Chapter row was clipped/not rendered");
                require(inside(pixels, new Rect(0, 0, s.screen.width, s.screen.height)), "Chapter row lies outside the actual window");
                observed.add("chapterButton", rectangle(pixels));
                observed.addProperty("title", s.chapterButton.getTitle().getString());
            } else {
                require(read(s.gui, "selectedChapter") == s.file.getChapter(id("chapter", "main")) && !s.gui.isViewingQuest(), "Wrong native chapter page");
                boolean focus = s.stage == Stage.FOCUS_FRAME;
                view = focus ? "autofocus" : "inherited";
                var button = questButton(s, focus ? "focus" : "inherited");
                pixels = verifyButton(s, button, focus, observed);
                double centerX = ((Number) read(s.gui.questPanel, "centerQuestX")).doubleValue();
                double centerY = ((Number) read(s.gui.questPanel, "centerQuestY")).doubleValue();
                observed.addProperty("centerQuestX", centerX); observed.addProperty("centerQuestY", centerY);
                observed.addProperty("scrollX", s.gui.questPanel.getScrollX());
                observed.addProperty("scrollY", s.gui.questPanel.getScrollY());
                observed.add("viewport", rectangle(viewport(s)));
                if (focus) {
                    require(Math.abs(centerX - 6) < 1.0e-7 && Math.abs(centerY + 4) < 1.0e-7,
                            "Chapter click did not autofocus to 6,-4: " + centerX + "," + centerY);
                    require(Math.abs(pixels.cx() - s.screen.width / 2.0) <= 1.5
                            && Math.abs(pixels.cy() - s.screen.height / 2.0) <= 1.5, "Rendered focus button is not centered: " + pixels);
                    observed.addProperty("icon", verifyIcon(button, false));
                }
            }
            saveFrame(s, event, view, pixels, observed);
            s.evidence.add(view, observed);
            s.observed = true;
        } catch (Exception error) { s.renderError = error; }
    }

    private static Rect verifyButton(Session s, QuestButton button, boolean focus, JsonObject observed) throws Exception {
        double size = focus ? 1.75 : 1.25, x = focus ? 6 : 2, y = focus ? -4 : 3;
        String shape = focus ? "square" : "circle", title = focus ? "Graph options focus" : "Graph inherited preset";
        var position = button.getPosition();
        require(position.x() == x && position.y() == y && position.w() == size && position.h() == size, "Native QuestButton position/size differs");
        int expected = (int) Math.round(s.gui.getZoom() * 1.5 * size);
        require(button.getWidth() == expected && button.getHeight() == expected, "Native shape/size pixel size differs: expected " + expected);
        require(button.getTitle().getString().equals(title), "Native QuestButton title differs");
        // FTB Quests 2001 resolves button shape via QuestButton.getShape() -> QuestShape.get(...) during draw.
        Method getShape = QuestButton.class.getDeclaredMethod("getShape");
        getShape.setAccessible(true);
        String actualShape = String.valueOf(getShape.invoke(button));
        require(QuestShape.get(shape).equals(QuestShape.get(actualShape)),
                "Native QuestButton shape differs: expected " + shape + " got " + actualShape);
        Rect rect = bounds(button);
        require(button.shouldDraw() && inside(rect, viewport(s)), "QuestButton was clipped/not rendered: " + rect);
        observed.add("button", rectangle(rect));
        observed.addProperty("title", title); observed.addProperty("shape", shape);
        observed.addProperty("size", size); observed.addProperty("questX", x); observed.addProperty("questY", y);
        observed.addProperty("expectedPixels", expected);
        return rect;
    }

    private static String verifyIcon(Button button, boolean chapter) throws Exception {
        Object ingredient = ((Icon) read(button, "icon")).getIngredient();
        require(ingredient instanceof ItemStack, "Native button icon is not an ItemStack");
        var actual = (ItemStack) ingredient;
        var expected = new ItemStack(chapter ? Items.DIAMOND : Items.EMERALD);
        // Match the fixture's literal display.Name JSON as well as its custom NBT.
        expected.getOrCreateTagElement("display").putString("Name", "{\"text\":\""
                + (chapter ? "Graph chapter icon" : "Graph quest icon") + "\",\"color\":\"gold\",\"bold\":true}");
        var data = expected.getOrCreateTag();
        data.putString("cq_graph_options", chapter ? "chapter" : "quest"); data.putString("sentinel", "keep-exact");
        require(ItemStack.matches(actual, expected), "Actual native button icon lost item/count/title/style/NBT");
        return actual.save(new CompoundTag()).toString();
    }

    private static void saveFrame(Session s, ScreenEvent.Render.Post event, String view, Rect rect, JsonObject observed) throws Exception {
        Files.createDirectories(s.folder);
        Path png = s.folder.resolve("graph-options-" + s.phase + "-" + view + ".png");
        event.getGuiGraphics().flush();
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            double scale = Minecraft.getInstance().getWindow().getGuiScale();
            Set<Integer> colors = new HashSet<>();
            int left = Math.max(0, (int) (rect.x * scale)), top = Math.max(0, (int) (rect.y * scale));
            int right = Math.min(image.getWidth(), (int) (rect.right() * scale));
            int bottom = Math.min(image.getHeight(), (int) (rect.bottom() * scale));
            for (int y = top; y < bottom && colors.size() < 16; y++)
                for (int x = left; x < right && colors.size() < 16; x++) colors.add(image.getPixelRGBA(x, y));
            image.writeToFile(png);
            observed.addProperty("distinctPixelColorsCapped16", colors.size());
            observed.addProperty("framebufferWidth", image.getWidth()); observed.addProperty("framebufferHeight", image.getHeight());
            require(colors.size() >= 4, "Actual native button framebuffer region is blank/uniform");
        }
        observed.addProperty("screenshot", png.toString());
        writeJson(s.folder.resolve("graph-options-" + s.phase + "-" + view + ".json"), observed);
    }

    private static Button chapterButton(Session s) throws Exception {
        for (Widget widget : s.chapters.getWidgets())
            if (widget.getClass().getName().endsWith("ChapterPanel$ChapterButton")
                    && read(widget, "chapter") == s.file.getChapter(id("chapter", "main"))) return (Button) widget;
        throw new IllegalStateException("Native Graph ChapterButton is missing");
    }
    private static QuestButton questButton(Session s, String leaf) throws Exception {
        for (Widget widget : s.gui.questPanel.getWidgets())
            if (widget instanceof QuestButton button && read(button, "quest") == s.file.getQuest(id("quest", leaf))) return button;
        throw new IllegalStateException("Native QuestButton is missing: " + leaf);
    }
    private static void click(Session s, Widget widget) {
        Rect rect = bounds(widget);
        require(widget.isEnabled() && widget.shouldDraw(), "Native button is not enabled/visible");
        require(s.screen.mouseClicked(rect.cx(), rect.cy(), GLFW.GLFW_MOUSE_BUTTON_LEFT), "Native screen did not consume ChapterButton click");
        s.screen.mouseReleased(rect.cx(), rect.cy(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private static void pan(Session s, Rect viewport, int dx, int dy) throws Exception {
        require(((List<?>) read(s.gui, "selectedObjects")).isEmpty() && !Boolean.TRUE.equals(read(s.gui, "movingObjects")),
                "Navigation would move selected quests");
        require(++s.panKeys <= 80, "Could not bring inherited QuestButton into the viewport by panning");
        double startX = dx > 0 ? viewport.x + 12 : viewport.right() - 12;
        double startY = dy > 0 ? viewport.y + 12 : viewport.bottom() - 12;
        movePointer(startX, startY);
        s.gui.updateMouseOver((int) startX, (int) startY);
        if (s.gui.questPanel.getWidgets().stream().anyMatch(w -> w.isMouseOver() && bounds(w).w < viewport.w)) {
            startX = viewport.cx();
            startY = viewport.cy();
            movePointer(startX, startY);
        }
        s.panStartX = startX;
        s.panStartY = startY;
        s.panX = Math.max(viewport.x + 2.0, Math.min(viewport.right() - 2.0, startX + dx));
        s.panY = Math.max(viewport.y + 2.0, Math.min(viewport.bottom() - 2.0, startY + dy));
        s.panPrimed = true;
    }
    private static void movePointer(double x, double y) throws Exception {
        ContactQuestsTestVirtualPointer.moveScaled(Minecraft.getInstance(), x, y);
    }
    private static void requireScreen(Session s) {
        require(Minecraft.getInstance().screen == s.screen && ClientQuestFile.INSTANCE == s.file
                && s.screen instanceof IScreenWrapper wrapper && wrapper.getGui() == s.gui, "Native screen or synchronized quest file changed during observation");
    }
    private static long id(String kind, String leaf) { return Long.parseUnsignedLong(ContactQuestsGraphOptionsAssertions.id(kind, leaf), 16); }
    private static Rect viewport(Session s) {
        Rect panel = bounds(s.gui.questPanel);
        int left = Math.max(panel.x, Math.max(0, s.chapters.getX() + s.chapters.getWidth()));
        return new Rect(left, panel.y, panel.right() - left, panel.h);
    }
    private static Rect bounds(Widget widget) {
        Panel parent = widget.getParent();
        // Panel.draw resets its transient offset before Render.Post; mirror its integer truncation read-only.
        return new Rect(widget.getX() + (parent.isOffset() ? 0 : (int) -parent.getScrollX()),
                widget.getY() + (parent.isOffset() ? 0 : (int) -parent.getScrollY()), widget.getWidth(), widget.getHeight());
    }
    private record Rect(int x, int y, int w, int h) {
        int right() { return x + w; } int bottom() { return y + h; }
        double cx() { return x + w / 2.0; } double cy() { return y + h / 2.0; }
    }
    private static boolean inside(Rect inner, Rect outer) {
        return inner.w > 0 && inner.h > 0 && inner.x >= outer.x && inner.y >= outer.y
                && inner.right() <= outer.right() && inner.bottom() <= outer.bottom();
    }
    private static JsonObject rectangle(Rect rect) {
        var json = new JsonObject(); json.addProperty("x", rect.x); json.addProperty("y", rect.y);
        json.addProperty("width", rect.w); json.addProperty("height", rect.h); return json;
    }
    private static void writeJson(Path path, JsonObject json) throws Exception {
        Files.createDirectories(path.getParent()); Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(json));
    }
    private static Object read(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(object.getClass().getName() + "." + name);
    }
    private static void pass(Session s, String message) { ContactQuestsClientTestController.assertCase(s.caseId(), true, message); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private ContactQuestsGraphOptionsClientAssertions() { }
}
