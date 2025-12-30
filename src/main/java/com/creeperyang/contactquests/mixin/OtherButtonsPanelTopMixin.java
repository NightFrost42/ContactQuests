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

    // 必须有这个构造函数来匹配父类
    protected OtherButtonsPanelTopMixin(Panel panel) {
        super(panel);
    }

    @Inject(method = "addWidgets", at = @At("TAIL"), remap = false)
    private void contactQuests$addBinderButton(CallbackInfo ci) {
        // 这里的 this 会被 Mixin 转换为 OtherButtonsPanelTop 实例
        this.add(new GetBinderButton(this));
    }
}