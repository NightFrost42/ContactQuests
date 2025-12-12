package com.creeperyang.contactquests.utils;

import com.creeperyang.contactquests.task.ParcelTask;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen;
import dev.ftb.mods.ftbquests.api.ItemFilterAdapter;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.Comparator;
import java.util.List;

public class ParcelTagSelectionScreen extends AbstractButtonListScreen {
    private final List<TagKey<Item>> tags;
    private final ParcelTask parcelTask;
    private final ItemFilterAdapter adapter;
    private final QuestScreen questScreen;

    public ParcelTagSelectionScreen(List<TagKey<Item>> tags, ParcelTask parcelTask, ItemFilterAdapter adapter, QuestScreen questScreen) {
        this.parcelTask = parcelTask;
        this.tags = tags;
        this.adapter = adapter;
        this.questScreen = questScreen;

        setTitle(Component.translatable("ftbquests.task.ftbquests.item.select_tag"));
        showBottomPanel(false);
        showCloseButton(true);
    }

    @Override
    public void addButtons(Panel panel) {
        tags.stream()
                .sorted(Comparator.comparing(itemTagKey -> itemTagKey.location().toString()))
                .forEach(tag -> panel.add(new ParcelTagSelectionScreen.TagSelectionButton(panel, tag)));
    }

    @Override
    public boolean onInit() {
        int titleW = getTheme().getStringWidth(getTitle());
        int w = tags.stream()
                .map(t -> getTheme().getStringWidth(t.location().toString()))
                .max(Comparator.naturalOrder())
                .orElse(100);
        setSize(Math.max(titleW, w) + 20, getScreen().getGuiScaledHeight() * 3 / 4);

        return true;
    }

    @Override
    protected void doCancel() {
        questScreen.openGui();
    }

    @Override
    protected void doAccept() {
        questScreen.openGui();
    }

    private void setTagFilterAndSave(ParcelTask parcelTask, ItemFilterAdapter adapter, TagKey<Item> tag) {
        parcelTask.setStackAndCount(adapter.makeTagFilterStack(tag), parcelTask.getItemStack().getCount());

        if (parcelTask.getRawTitle().isEmpty()) {
            parcelTask.setRawTitle("Any #" + tag.location());
        }

        EditObjectMessage.sendToServer(parcelTask);
    }

    private class TagSelectionButton extends SimpleTextButton {
        private final TagKey<Item> tag;

        public TagSelectionButton(Panel panel, TagKey<Item> tag) {
            super(panel, Component.literal(tag.location().toString()), Color4I.empty());
            this.tag = tag;
        }

        @Override
        public void onClicked(MouseButton button) {
            questScreen.openGui();
            setTagFilterAndSave(parcelTask, adapter, tag);
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            if (isMouseOver) {
                Color4I.WHITE.withAlpha(30).draw(graphics, x, y, w, h);
            }
            Color4I.GRAY.withAlpha(40).draw(graphics, x, y + h, w, 1);
        }
    }
}