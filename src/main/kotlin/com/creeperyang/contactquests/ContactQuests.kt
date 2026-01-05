package com.creeperyang.contactquests

import com.creeperyang.contactquests.client.ContactQuestsClient
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
import net.minecraft.world.level.Level
import net.minecraftforge.common.MinecraftForge.EVENT_BUS
import net.minecraftforge.event.TagsUpdatedEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.config.ModConfig
import net.minecraftforge.fml.event.config.ModConfigEvent
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.forge.runForDist
import org.apache.logging.log4j.Level as LogLevel

@Mod(ContactQuests.ID)
class ContactQuests {

    companion object {
        const val ID = "contactquests"

        val LOGGER: Logger = LogManager.getLogger(ID)

        @JvmStatic
        fun error(format: String, vararg data: Any?) {
            LOGGER.log(LogLevel.ERROR, String.format(format, *data))
        }

        @JvmStatic
        fun warn(format: String, vararg data: Any?) {
            LOGGER.log(LogLevel.WARN, String.format(format, *data))
        }

        @JvmStatic
        fun info(format: String, vararg data: Any?) {
            LOGGER.log(LogLevel.INFO, String.format(format, *data))
        }

        @JvmStatic
        fun debug(format: String, vararg data: Any?) {
            LOGGER.log(LogLevel.DEBUG, String.format(format, *data))
        }
    }

    init {
        ModLoadingContext.get().registerConfig(
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

        NetworkHandler.register()

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
        // ContactQuestsClient.init()
    }

    /**
     * Fired on the global Forge bus.
     */
    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        // 服务端函数在这里初始化
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
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
    fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase == TickEvent.Phase.END) {
            val server = event.server ?: return
            val overworld = server.getLevel(Level.OVERWORLD) ?: return

            TaskDeliverySavedData[overworld].tick(overworld)
            RewardDistributionManager.onServerTick(overworld)
        }
    }

    private fun onConfigLoad(event: ModConfigEvent.Loading) {
        if (event.config == ContactConfig.SPEC) {
            info("Loaded ContactQuests Common Config")
        }
    }

    private fun onConfigReload(event: ModConfigEvent.Reloading) {
        if (event.config == ContactConfig.SPEC) {
            info("Reloaded ContactQuests Common Config")
        }
    }
}