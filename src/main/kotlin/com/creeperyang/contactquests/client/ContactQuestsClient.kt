package com.creeperyang.contactquests.client

import com.creeperyang.contactquests.client.renderer.MailboxGlobalRenderer
import com.creeperyang.contactquests.client.util.ParcelAutoFiller
import com.creeperyang.contactquests.client.util.PostcardAutoFiller
import com.creeperyang.contactquests.client.util.RedPacketAutoFiller
import dev.ftb.mods.ftbquests.client.GuiProviders
import net.minecraftforge.common.MinecraftForge.EVENT_BUS
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import thedarkcolour.kotlinforforge.forge.MOD_BUS

object ContactQuestsClient {

    fun init() {
        MOD_BUS.addListener(::onClientSetup)

        EVENT_BUS.register(ParcelAutoFiller)
        EVENT_BUS.register(RedPacketAutoFiller)
        EVENT_BUS.register(PostcardAutoFiller)

        EVENT_BUS.register(MailboxGlobalRenderer)
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        GuiProviders.setTaskGuiProviders()
        GuiProviders.setRewardGuiProviders()
    }

}