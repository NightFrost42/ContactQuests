package com.creeperyang.contactquests.test;

import com.creeperyang.contactquests.mixin.PostcardEditScreenAccessor;
import com.flechazo.contact.client.gui.screen.PostcardEditScreen;
import com.flechazo.contact.client.gui.screen.PostcardReadScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Check the lines Contact actually builds with the player's loaded font, never infer layout from character count. */
public final class ContactQuestsPostcardLayoutAssertions {
    private ContactQuestsPostcardLayoutAssertions() {}

    public static void verifyPreview(com.creeperyang.contactquests.client.gui.ValidPostcardItemsScreen screen, String id) {
        try {
            var mc = Minecraft.getInstance();
            var style = com.flechazo.contact.data.PostcardDataManager.getPostcard(
                    new net.minecraft.resources.ResourceLocation(screen.getTask().getPostcardStyle()));
            var panel = screen.getMainPanel();
            int cardX = panel.getX() + (panel.getWidth() - style.cardWidth()) / 2;
            int cardY = panel.getY() + (panel.getHeight() - style.cardHeight()) / 2;
            int left = cardX + style.textPosX(), top = cardY + style.textPosY();
            int right = left + style.textWidth(), bottom = top + style.textHeight();
            int limit = style.textHeight() / 12;
            var text = screen.getTask().getResolvedText(dev.ftb.mods.ftbquests.client.ClientQuestFile.INSTANCE.selfTeamData, mc.player);
            if (mc.font.split(Component.literal(text), style.textWidth()).size() <= limit)
                throw new IllegalStateException("Preview boundary fixture must have more lines than fit on the actual card");
            class ObservedGraphics extends net.minecraft.client.gui.GuiGraphics {
                int lines;
                boolean clipped, clipSeen, bounds = true;
                ObservedGraphics() { super(mc, mc.renderBuffers().bufferSource()); }
                @Override public void enableScissor(int x1, int y1, int x2, int y2) {
                    clipped = true; clipSeen = x1 == left && y1 == top && x2 == right && y2 == bottom;
                    super.enableScissor(x1, y1, x2, y2);
                }
                @Override public void disableScissor() { clipped = false; super.disableScissor(); }
                @Override public int drawString(net.minecraft.client.gui.Font font, net.minecraft.util.FormattedCharSequence line,
                                                int x, int y, int color, boolean shadow) {
                    bounds &= clipped && clipSeen && x == left && y == top + lines * 12
                            && x + font.width(line) <= right && y + font.lineHeight <= bottom;
                    lines++;
                    return super.drawString(font, line, x, y, color, shadow);
                }
            }
            var graphics = new ObservedGraphics();
            panel.drawBackground(graphics, screen.getTheme(), panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight());
            graphics.flush();
            ContactQuestsClientTestController.assertCase(id, graphics.bounds && graphics.clipSeen && !graphics.clipped && graphics.lines == limit,
                    "Actual ordinary task preview drew " + graphics.lines + "/" + limit
                            + " lines at 12-pixel spacing inside the actual card, with a matching active scissor and no extra lines");
        } catch (Exception error) { ContactQuestsClientTestController.assertCase(id, false, error.toString()); }
    }

    public static void verifyBundledTemplates() {
        var mc = Minecraft.getInstance(); var previous = mc.screen;
        try {
            int count = 0;
            for (var group : com.creeperyang.contactquests.config.ChoiceTemplateConfig.INSTANCE.bundledDefaults().entrySet()) {
                for (var template : group.getValue()) {
                    String text = com.creeperyang.contactquests.config.ChoiceTemplateConfig.INSTANCE.fill(template, java.util.Map.of(
                            "recipient", "旅行商人", "quest", "河畔的委托", "task_goal", "送去三份苹果",
                            "reward", group.getKey().equals("en_us") ? "7 × Diamond" : "钻石 × 7", "player", "TravelingPlayer"));
                    var card = com.flechazo.contact.common.item.PostcardItem.setText(
                            com.flechazo.contact.common.item.PostcardItem.getPostcard(new net.minecraft.resources.ResourceLocation("contact:default"), false), text);
                    mc.setScreen(new PostcardReadScreen(card));
                    verify("client.postcard.templates." + group.getKey() + "." + template.getId()); count++;
                }
            }
            ContactQuestsClientTestController.assertCase("client.postcard.templates.all_fit", count == 8,
                    "All eight bundled natural-letter templates render within real Contact default-card bounds with the loaded player font and filled names/reward");
        } catch (Exception error) {
            ContactQuestsClientTestController.assertCase("client.postcard.templates.all_fit", false, error.toString());
        } finally { mc.setScreen(previous); }
    }

    public static void verify(String id) {
        try {
            var mc = Minecraft.getInstance();
            boolean edit = mc.screen instanceof PostcardEditScreen;
            if (!edit && !(mc.screen instanceof PostcardReadScreen)) throw new IllegalStateException("Expected a real Contact postcard editor or reader");
            Object box = edit ? ((PostcardEditScreenAccessor) mc.screen).contactQuests$getTextBox() : field(mc.screen, "textBox");
            AbstractWidget widget = (AbstractWidget) box;
            Object page;
            if (edit) {
                Method getPage = box.getClass().getDeclaredMethod("getPage"); getPage.setAccessible(true);
                page = getPage.invoke(box);
            } else page = field(box, "currentPage");
            String text = (String) field(box, "page");
            Object[] lines = (Object[]) field(page, "lines");
            boolean fits = text.length() < 1024;
            int bottom = widget.getY(), widest = 0;
            for (Object line : lines) {
                int x = (Integer) field(line, "x"), y = (Integer) field(line, "y");
                int width = mc.font.width((Component) field(line, "lineTextComponent"));
                fits &= x >= widget.getX() && y >= widget.getY() && x + width <= widget.getX() + widget.getWidth()
                        && y + mc.font.lineHeight <= widget.getY() + widget.getHeight();
                bottom = Math.max(bottom, y + mc.font.lineHeight); widest = Math.max(widest, width);
            }
            if (edit) fits &= mc.font.wordWrapHeight(text, widget.getWidth()) <= widget.getHeight() * mc.font.lineHeight / 12;
            ContactQuestsClientTestController.assertCase(id, fits,
                    "Native Contact " + (edit ? "editor" : "reader") + " lines=" + lines.length + ", chars=" + text.length()
                            + ", widest=" + widest + "/" + widget.getWidth() + ", bottom=" + (bottom - widget.getY()) + "/" + widget.getHeight());
        } catch (Exception error) {
            ContactQuestsClientTestController.assertCase(id, false, error.toString());
        }
    }

    private static Object field(Object value, String name) throws ReflectiveOperationException {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(value);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
