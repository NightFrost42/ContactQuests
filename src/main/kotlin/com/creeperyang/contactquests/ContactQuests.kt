package com.creeperyang.contactquests

import com.creeperyang.contactquests.client.ContactQuestsClient
import com.creeperyang.contactquests.data.DataManager
import com.creeperyang.contactquests.task.TaskRegistry
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist

/**
 * Main mod class.
 *
 * An example for blocks is in the `blocks` package of this mod.
 */
@Mod(ContactQuests.ID)
object ContactQuests {
    const val ID = "contactquests"

    // the logger for our mod
    val LOGGER: Logger = LogManager.getLogger(ID)

    @JvmStatic
    fun error(format: String, vararg data: Any?) {
        LOGGER.log(Level.ERROR, String.format(format, *data))
    }

    @JvmStatic
    fun warn(format: String, vararg data: Any?) {
        LOGGER.log(Level.WARN, String.format(format, *data))
    }

    @JvmStatic
    fun info(format: String, vararg data: Any?) {
        LOGGER.log(Level.INFO, String.format(format, *data))
    }

    init {
        LOGGER.log(Level.INFO, "Contactquests init")
        TaskRegistry.init()
        DataManager.init()

        val obj = runForDist(
            clientTarget = {
                MOD_BUS.addListener(::onClientSetup)
                Minecraft.getInstance()
            },
            serverTarget = {
                MOD_BUS.addListener(::onServerSetup)
                "test"
            })
    }

    /**
     * This is used for initializing client specific
     * things such as renderers and keymaps
     * Fired on the mod specific event bus.
     */
    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.log(Level.INFO, "Initializing client...")
        ContactQuestsClient.init()
    }

    /**
     * Fired on the global Forge bus.
     */
    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
//        LOGGER.log(Level.INFO, "Server starting...")
    }

    @SubscribeEvent
    fun onCommonSetup(event: FMLCommonSetupEvent) {
//        LOGGER.log(Level.INFO, "Hello! This is working!")
    }
}
