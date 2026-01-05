package com.creeperyang.contactquests.mixin;

import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.Movable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(QuestScreen.class)
public interface QuestScreenAccessor {
    @Accessor("selectedObjects")
    List<Movable> getSelectedObjects();
}