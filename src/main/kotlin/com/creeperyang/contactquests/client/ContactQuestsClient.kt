package com.creeperyang.contactquests.client

import com.creeperyang.contactquests.client.gui.ContactConfigScreen
import com.creeperyang.contactquests.client.renderer.MailboxGlobalRenderer
import com.creeperyang.contactquests.client.util.ParcelAutoFiller
import com.creeperyang.contactquests.client.util.PostcardAutoFiller
import com.creeperyang.contactquests.client.util.RedPacketAutoFiller
import dev.ftb.mods.ftbquests.client.GuiProviders
import net.minecraftforge.client.ConfigScreenHandler
import net.minecraftforge.client.event.ScreenEvent
import net.minecraftforge.common.MinecraftForge.EVENT_BUS
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import thedarkcolour.kotlinforforge.forge.MOD_BUS

object ContactQuestsClient {

    fun init() {
        MOD_BUS.addListener(::onClientSetup)

        ModLoadingContext.get().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory::class.java
        ) {
            ConfigScreenHandler.ConfigScreenFactory { _, parent ->
                ContactConfigScreen(parent)
            }
        }

        EVENT_BUS.register(ParcelAutoFiller)
        EVENT_BUS.register(RedPacketAutoFiller)
        EVENT_BUS.register(PostcardAutoFiller)

        EVENT_BUS.addListener<ScreenEvent.Closing> { event ->
            ParcelAutoFiller.onScreenClosed(event.screen)
            RedPacketAutoFiller.onScreenClosed(event.screen)
            PostcardAutoFiller.onScreenClosed(event.screen)
        }
        EVENT_BUS.register(MailboxGlobalRenderer)
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        GuiProviders.setTaskGuiProviders()
        GuiProviders.setRewardGuiProviders()
    }

}