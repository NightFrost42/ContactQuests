package com.creeperyang.contactquests.client.gui

import com.creeperyang.contactquests.network.RequestBinderPayload
import com.creeperyang.contactquests.registry.ModItems
import dev.ftb.mods.ftblibrary.icon.ItemIcon
import dev.ftb.mods.ftblibrary.ui.Panel
import dev.ftb.mods.ftblibrary.ui.input.MouseButton
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.client.gui.quests.TabButton
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.neoforged.neoforge.network.PacketDistributor

class GetBinderButton(panel: Panel) : TabButton(
    panel,
    Component.translatable("contactquests.sidebar.get_binder"),
    ItemIcon.getItemIcon(ModItems.TEAM_BINDING_CARD.get())
) {
    override fun onClicked(button: MouseButton) {
        Minecraft.getInstance().soundManager.play(
            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f)
        )
        PacketDistributor.sendToServer(RequestBinderPayload)
    }

    override fun addMouseOverText(list: TooltipList) {
        list.add(title)
    }
}