package com.creeperyang.contactquests.utils;

import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class ClientLocaleHelper {
    @OnlyIn(Dist.CLIENT)
    public static String getLocale() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }

    protected String contactQuests$getLocale() {
        if (((QuestObjectBase) (Object) this).getQuestFile().isServerSide()) {
            return "zh_cn";
        } else {
            return Minecraft.getInstance().getLanguageManager().getSelected();
        }
    }
}