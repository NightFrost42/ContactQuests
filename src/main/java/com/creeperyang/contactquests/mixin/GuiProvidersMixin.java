package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.task.ParcelTask;
import com.creeperyang.contactquests.task.TaskRegistry;
import dev.ftb.mods.ftblibrary.config.ItemStackConfig;
import dev.ftb.mods.ftblibrary.config.ui.resource.SelectItemStackScreen;
import dev.ftb.mods.ftbquests.client.GuiProviders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiProviders.class)
class GuiProvidersMixin {
    @Inject(method = "setTaskGuiProviders", at = @At("TAIL"))
    private static void setMyTaskGuiProviders(CallbackInfo ci) {
        TaskRegistry.PARCEL.setGuiProvider((gui, quest, callback) -> {
            ItemStackConfig c = new ItemStackConfig(false, false);

            new SelectItemStackScreen(c, accepted -> {
                gui.run();
                if (accepted) {
                    ParcelTask parcelTask = new ParcelTask(0L, quest).setStackAndCount(c.getValue(), c.getValue().getCount());
                    callback.accept(parcelTask, parcelTask.getType().makeExtraNBT());
                }
            }).openGui();
        });
//        TaskRegistry.PARCEL_TASK.guiProvider =
//                TaskType.GuiProvider { gui: Panel?, quest: Quest?, callback: BiConsumer<Task?, CompoundTag?>? ->
//        val c = ItemStackConfig(false, false)
//        SelectItemStackScreen(c) { accepted: Boolean ->
//                gui!!.run()
//            if (accepted) {
//                val itemTask = ItemTask(0L, quest).setStackAndCount(c.getValue(), c.getValue().count)
//                callback!!.accept(itemTask, itemTask.type.makeExtraNBT())
//            }
//        }.openGui()
//    }
    }
}
