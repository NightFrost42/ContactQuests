package com.creeperyang.contactquests.client

import com.creeperyang.contactquests.client.gui.ContactConfigScreen
import com.creeperyang.contactquests.client.renderer.MailboxGlobalRenderer
import com.creeperyang.contactquests.client.util.ParcelAutoFiller
import com.creeperyang.contactquests.client.util.PostcardAutoFiller
import com.creeperyang.contactquests.client.util.RedPacketAutoFiller
import dev.ftb.mods.ftbquests.client.GuiProviders
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.common.NeoForge
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@OnlyIn(Dist.CLIENT)
object ContactQuestsClient {

    fun init() {
        MOD_BUS.addListener(::onClientSetup)

        ModLoadingContext.get().registerExtensionPoint(
            IConfigScreenFactory::class.java
        ) {
            IConfigScreenFactory { _, parent ->
                ContactConfigScreen(parent)
            }
        }

        NeoForge.EVENT_BUS.register(ParcelAutoFiller)
        NeoForge.EVENT_BUS.register(RedPacketAutoFiller)
        NeoForge.EVENT_BUS.register(PostcardAutoFiller)
        NeoForge.EVENT_BUS.register(MailboxGlobalRenderer)
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        GuiProviders.setTaskGuiProviders()
        GuiProviders.setRewardGuiProviders()
    }

}