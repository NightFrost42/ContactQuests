package com.creeperyang.contactquests.client

import com.creeperyang.contactquests.client.util.ParcelAutoFiller
import dev.ftb.mods.ftbquests.client.GuiProviders
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.common.NeoForge
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

class ContactQuestsClient {

    companion object{
        fun init() {
            MOD_BUS.addListener(::onClientSetup)
            NeoForge.EVENT_BUS.register(ParcelAutoFiller)
        }

        private fun onClientSetup(event: FMLClientSetupEvent) {
            GuiProviders.setTaskGuiProviders()
            GuiProviders.setRewardGuiProviders()
        }
    }
}