package com.creeperyang.contactquests.client.util

import com.creeperyang.contactquests.ContactQuests
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickType
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.event.TickEvent

@OnlyIn(Dist.CLIENT)
abstract class BaseAutoFiller<T> {

    protected var taskData: T? = null
    protected var isTaskScheduled = false
    protected var actionDelay = 0

    protected open val maxTimeoutTicks: Int = 60

    protected open val checkContainerId: Boolean = true

    fun schedule(data: T) {
        ContactQuests.debug("${this.javaClass.simpleName}: 任务已调度 -> $data")
        this.taskData = data
        this.isTaskScheduled = true
        this.actionDelay = 0
        resetState()
    }

    protected open fun finishTask(reason: String) {
        ContactQuests.debug("${this.javaClass.simpleName}: 任务结束 ($reason)")
        isTaskScheduled = false
        taskData = null
        resetState()
    }

    protected abstract fun resetState()

    protected abstract fun isValidScreen(screen: net.minecraft.client.gui.screens.Screen?): Boolean

    protected abstract fun runLogic(mc: Minecraft, player: Player, screen: net.minecraft.client.gui.screens.Screen)

    open fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (!isTaskScheduled) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val currentScreen = mc.screen

        if (!isValidScreen(currentScreen)) {
            handleScreenClosedOrInvalid()
            return
        }

        if (checkContainerId && player.containerMenu.containerId == 0) {
            actionDelay++
            return
        }

        if (actionDelay > 0) {
            actionDelay--
            return
        }

        runLogic(mc, player, currentScreen!!)
    }

    private fun handleScreenClosedOrInvalid() {
        actionDelay++
        if (actionDelay > maxTimeoutTicks) {
            finishTask("界面关闭或超时")
        }
    }

    protected fun click(mc: Minecraft, containerId: Int, slotId: Int, button: Int, type: ClickType, player: Player) {
        mc.gameMode?.handleInventoryMouseClick(containerId, slotId, button, type, player)
    }
}