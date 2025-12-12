package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.task.ParcelTask;
import com.creeperyang.contactquests.utils.ParcelTagSelectionScreen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbquests.api.ItemFilterAdapter;
import dev.ftb.mods.ftbquests.client.gui.ContextMenuBuilder;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.client.gui.quests.TaskButton;
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem;
import dev.ftb.mods.ftbquests.net.EditObjectMessage;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.theme.property.ThemeProperties;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Mixin(TaskButton.class)
public abstract class TaskButtonMixin {

    @Shadow
    Task task;

    @Shadow(remap = false)
    @Final
    private QuestScreen questScreen;

    /**
     * 在 TaskButton.draw() 的结尾绘制自定义图标
     */
    @Inject(method = "draw", at = @At("TAIL"))
    private void drawOverlay(GuiGraphics graphics, Theme theme,
    int x, int y, int w, int h,
    CallbackInfo ci) {

        if (!(task instanceof ParcelTask)) {
            return;
        }

        // 使用较高 z-index 绘制
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 300); // 高于 CHECK_ICON 的 z=200

        Icon parcelIcon = Icon.getIcon("contact:item/parcel");
        RenderSystem.enableBlend();

        // 绘制在右下角
        int size = (h >= 32 ? 16 : 10); // 根据按钮大小改变图标大小
        int drawX = x + w - size;
        int drawY = y + h - size;

        parcelIcon.draw(graphics, drawX, drawY, size, size);

        RenderSystem.disableBlend();
        pose.popPose();
    }

    @Inject(method = "onClicked", at = @At(value = "INVOKE", target = "Ldev/ftb/mods/ftbquests/client/gui/ContextMenuBuilder;openContextMenu(Ldev/ftb/mods/ftblibrary/ui/BaseScreen;)V"),locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    private void adaptFilter(MouseButton button, CallbackInfo ci, ContextMenuBuilder builder){
        if (task instanceof ParcelTask parcelTask && !parcelTask.getItemStack().isEmpty()) {
            var tags = parcelTask.getItemStack().getItem().builtInRegistryHolder().tags().toList();
            if (!tags.isEmpty() && !ItemMatchingSystem.INSTANCE.isItemFilter(parcelTask.getItemStack())) {
                for (ItemFilterAdapter adapter : ItemMatchingSystem.INSTANCE.adapters()) {
                    if (adapter.hasItemTagFilter()) {
                        builder.insertAtTop(List.of(new ContextMenuItem(Component.translatable("ftbquests.task.ftbquests.item.convert_tag", adapter.getName()),
                                ThemeProperties.RELOAD_ICON.get(),
                                b -> {
                                    if (tags.size() == 1) {
                                        contactQuests$setTagFilterAndSave(parcelTask, adapter, tags.getFirst());
                                    } else {
                                        new ParcelTagSelectionScreen(tags, parcelTask, adapter, questScreen).openGui();
                                    }
                                })
                        ));
                    }
                }
            }
        }
    }

    @Unique
    private void contactQuests$setTagFilterAndSave(ParcelTask parcelTask, ItemFilterAdapter adapter, TagKey<Item> tag) {
        parcelTask.setStackAndCount(adapter.makeTagFilterStack(tag), parcelTask.getItemStack().getCount());

        if (parcelTask.getRawTitle().isEmpty()) {
            parcelTask.setRawTitle("Any #" + tag.location());
        }

        EditObjectMessage.sendToServer(parcelTask);
    }
}
