package com.creeperyang.contactquests

import com.creeperyang.contactquests.command.ModCommands
import com.creeperyang.contactquests.config.ContactConfig
import com.creeperyang.contactquests.config.NpcConfigManager
import com.creeperyang.contactquests.data.CollectionSavedData
import com.creeperyang.contactquests.data.DataManager
import com.creeperyang.contactquests.data.RewardDistributionManager
import com.creeperyang.contactquests.data.RewardDeliverySavedData
import com.creeperyang.contactquests.data.TaskDeliverySavedData
import com.creeperyang.contactquests.network.NetworkHandler
import com.creeperyang.contactquests.quest.reward.PostcardReward
import com.creeperyang.contactquests.quest.reward.RedPacketReward
import com.creeperyang.contactquests.quest.reward.RewardRegistry
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.creeperyang.contactquests.quest.task.RedPacketTask
import com.creeperyang.contactquests.quest.task.TaskRegistry
import com.creeperyang.contactquests.registry.ModItems
import com.creeperyang.contactquests.mailbox.TeamMailboxBinding
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.config.ModConfig
import net.minecraftforge.fml.event.config.ModConfigEvent
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.minecraftforge.common.MinecraftForge.EVENT_BUS
import net.minecraftforge.event.TagsUpdatedEvent
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.server.ServerLifecycleHooks
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.forge.MOD_BUS

/** 模组主入口，负责注册配置、网络协议、事件监听与服务端数据加载生命周期。 */
@Mod(ContactQuests.ID)
object ContactQuests {
    const val ID = "contactquests"

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
        ModLoadingContext.get().registerConfig(
            ModConfig.Type.COMMON,
            ContactConfig.SPEC,
            "contactquests/contactquests-common.toml"
        )

        TaskRegistry.init()
        RewardRegistry.init()
        TeamMailboxBinding.init()
        ModItems.register(MOD_BUS)
        EVENT_BUS.register(ModCommands)
        EVENT_BUS.register(EventHandler)

        MOD_BUS.addListener(::onConfigLoad)
        MOD_BUS.addListener(::onConfigReload)
        MOD_BUS.addListener(::onCommonSetup)
        NetworkHandler.register()

        if (ModList.get().isLoaded("kubejs")) {
            try {
                val clazz = Class.forName("com.creeperyang.contactquests.compat.kubejs.ContactKubeJSPlugin")

                val instanceField = clazz.getField("INSTANCE")
                val instance = instanceField.get(null)

                val initMethod = clazz.getMethod("init")

                initMethod.invoke(instance)

            } catch (e: Exception) {
                LOGGER.error("Failed to load ContactQuests KubeJS integration", e)
            }
        }

        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            val client = Class.forName("com.creeperyang.contactquests.client.ContactQuestsClient")
            client.getMethod("init").invoke(client.getField("INSTANCE").get(null))
        }

        EVENT_BUS.register(this)
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
    }

    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
    }

    fun onCommonSetup(event: FMLCommonSetupEvent) {
        NpcConfigManager.initFile()
        com.creeperyang.contactquests.config.ChoiceTemplateConfig.reload()
        com.creeperyang.contactquests.llm.NpcLlmConfig.reload()
    }

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        try {
            DataManager.init()
            // 在主世界 tick 循环开始前先导入早期按维度分开存储的奖励待派发队列，迁移标记会防止重复导入。
            event.server.allLevels.forEach { RewardDeliverySavedData.get(it) }
            if (ModList.get().isLoaded("kubejs")) {
                try {
                    val clazz = Class.forName("com.creeperyang.contactquests.compat.kubejs.ContactKubeJSPlugin")
                    val method = clazz.getMethod("loadPersistentData", net.minecraft.server.MinecraftServer::class.java)
                    method.invoke(null, event.server)
                } catch (e: Exception) {
                    LOGGER.error("Failed to load ContactQuests KubeJS persistent data", e)
                }
            }
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

        if (event.updateCause == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            com.creeperyang.contactquests.config.ChoiceTemplateConfig.reload()
            com.creeperyang.contactquests.llm.NpcLlmConfig.reload()
            PostcardTask.PostcardPlaceholderSupport.reset()
            RedPacketTask.RedPacketPlaceholderSupport.reset()
            PostcardReward.reset()
            RedPacketReward.reset()
            CollectionSavedData.reset()

            if (ModList.get().isLoaded("kubejs")) {
                try {
                    val clazz = Class.forName("com.creeperyang.contactquests.compat.kubejs.ContactKubeJSPlugin")
                    val method = clazz.getMethod("reload")
                    method.invoke(null)

                    val server = ServerLifecycleHooks.getCurrentServer()
                    if (server != null) {
                        val loadMethod =
                            clazz.getMethod("loadPersistentData", net.minecraft.server.MinecraftServer::class.java)
                        loadMethod.invoke(null, server)
                    }
                } catch (e: Exception) {
                    LOGGER.error("Failed to reload ContactQuests KubeJS integration", e)
                }
            }
        }
    }

    private fun onConfigLoad(event: ModConfigEvent.Loading) {
        if (event.config.getSpec() == ContactConfig.SPEC) {
            info("Loaded ContactQuests Common Config")
        }
    }

    private fun onConfigReload(event: ModConfigEvent.Reloading) {
        if (event.config.getSpec() == ContactConfig.SPEC) {
            info("Reloaded ContactQuests Common Config")
        }
    }
}
