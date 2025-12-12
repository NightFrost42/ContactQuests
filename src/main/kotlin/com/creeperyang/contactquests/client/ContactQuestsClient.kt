package com.creeperyang.contactquests.client

import dev.ftb.mods.ftbquests.client.GuiProviders
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

class ContactQuestsClient {

    companion object{
        fun init() {
            MOD_BUS.addListener(::onClientSetup)
        }

        private fun onClientSetup(event: FMLClientSetupEvent) {
            GuiProviders.setTaskGuiProviders()
            GuiProviders.setRewardGuiProviders()
        }
    }
}