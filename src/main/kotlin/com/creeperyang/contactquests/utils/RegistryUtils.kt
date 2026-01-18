package com.creeperyang.contactquests.utils

import net.minecraft.client.Minecraft
import net.minecraft.core.HolderLookup
import net.minecraft.core.RegistryAccess
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.server.ServerLifecycleHooks

object RegistryUtils {
    val registryAccess: HolderLookup.Provider
        get() {
            val server = ServerLifecycleHooks.getCurrentServer()
            if (server != null) {
                return server.registryAccess()
            }

            if (FMLEnvironment.dist == Dist.CLIENT) {
                return ClientHandler.clientRegistryAccess
            }

            throw IllegalStateException("无法获取 RegistryAccess! 请确保在游戏世界加载后调用此方法。")
        }

    private object ClientHandler {
        val clientRegistryAccess: HolderLookup.Provider
            get() {
                val minecraft = Minecraft.getInstance()
                if (minecraft.level != null) {
                    return minecraft.level!!.registryAccess()
                }
                if (minecraft.connection != null) {
                    return minecraft.connection!!.registryAccess()
                }
                return RegistryAccess.EMPTY
            }
    }
}