package com.creeperyang.contactquests

import com.creeperyang.contactquests.client.ContactQuestsClient
import com.creeperyang.contactquests.client.gui.ContactConfigScreen
import com.creeperyang.contactquests.command.ModCommands
import com.creeperyang.contactquests.config.ContactConfig
import com.creeperyang.contactquests.config.NpcConfigManager
import com.creeperyang.contactquests.data.DataManager
import com.creeperyang.contactquests.data.RewardDistributionManager
import com.creeperyang.contactquests.data.TaskDeliverySavedData
import com.creeperyang.contactquests.network.NetworkHandler
import com.creeperyang.contactquests.quest.reward.RewardRegistry
import com.creeperyang.contactquests.quest.task.TaskRegistry
import com.creeperyang.contactquests.registry.ModItems
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import net.minecraft.client.Minecraft
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.config.ModConfigEvent
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.common.NeoForge.EVENT_BUS
import net.neoforged.neoforge.event.TagsUpdatedEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
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

    @JvmStatic
    fun debug(format: String, vararg data: Any?) {
        LOGGER.log(Level.DEBUG, String.format(format, *data))
    }

    init {
        ModLoadingContext.get().activeContainer.registerConfig(
            ModConfig.Type.COMMON,
            ContactConfig.SPEC,
            "contactquests/contactquests-common.toml"
        )

        TaskRegistry.init()
        RewardRegistry.init()
        ModItems.register(MOD_BUS)
        EVENT_BUS.register(ModCommands)
        EVENT_BUS.register(EventHandler)

        MOD_BUS.addListener(::onConfigLoad)
        MOD_BUS.addListener(::onConfigReload)
        MOD_BUS.addListener(::onCommonSetup)
        MOD_BUS.addListener(NetworkHandler::register)

        ModLoadingContext.get().registerExtensionPoint(
            IConfigScreenFactory::class.java
        ) {
            IConfigScreenFactory { _, parent ->
                ContactConfigScreen(parent)
            }
        }

        runForDist(
            clientTarget = {
                ContactQuestsClient.init()

                MOD_BUS.addListener(::onClientSetup)
                Minecraft.getInstance()
            },
            serverTarget = {
                MOD_BUS.addListener(::onServerSetup)
                "test"
            })

        EVENT_BUS.register(this)
    }

    /**
     * This is used for initializing client specific
     * things such as renderers and keymaps
     * Fired on the mod specific event bus.
     */
    private fun onClientSetup(event: FMLClientSetupEvent) {
//        ContactQuestsClient.init()
    }

    /**
     * Fired on the global Forge bus.
     */
    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        //服务端函数在这里初始化
    }

    fun onCommonSetup(event: FMLCommonSetupEvent) {
        NpcConfigManager.initFile()
    }

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        try {
            DataManager.init()
        } catch (e: Exception) {
            LOGGER.error("DataManager init failed", e)
        }

    }

    @SubscribeEvent
    fun onTagsUpdated(event: TagsUpdatedEvent) {
        if (event.updateCause == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD && ServerQuestFile.INSTANCE != null) {
            try {
                DataManager.init()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        val server = event.server ?: return
        val overworld = server.overworld()

        TaskDeliverySavedData[overworld].tick(overworld)
        RewardDistributionManager.onServerTick(overworld)
    }

    private fun onConfigLoad(event: ModConfigEvent.Loading) {
        if (event.config.spec == ContactConfig.SPEC) {
            info("Loaded ContactQuests Common Config")
        }
    }

    private fun onConfigReload(event: ModConfigEvent.Reloading) {
        if (event.config.spec == ContactConfig.SPEC) {
            info("Reloaded ContactQuests Common Config")
        }
    }
}
