package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.client.gui.GetBinderButton;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftbquests.client.gui.quests.OtherButtonsPanelTop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OtherButtonsPanelTop.class)
public abstract class OtherButtonsPanelTopMixin extends Panel {

    protected OtherButtonsPanelTopMixin(Panel panel) {
        super(panel);
    }

    @Inject(method = "addWidgets", at = @At("TAIL"), remap = false)
    private void contactQuests$addBinderButton(CallbackInfo ci) {
        this.add(new GetBinderButton(this));
    }
}