package com.creeperyang.contactquests.client.util

import com.creeperyang.contactquests.ContactQuests
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.client.event.ClientTickEvent

@OnlyIn(Dist.CLIENT)
abstract class BaseAutoFiller<T> {

    protected var taskData: T? = null
    protected var isTaskScheduled = false
    protected var actionDelay = 0
    private var awaitingScreenTicks = 0
    private var boundScreen: Screen? = null
    private var boundMenu: AbstractContainerMenu? = null
    private var boundContainerId = -1

    protected open val maxTimeoutTicks: Int = 60

    protected open val checkContainerId: Boolean = true

    fun schedule(data: T) {
        ContactQuests.debug("${this.javaClass.simpleName}: 任务已调度 -> $data")
        this.taskData = data
        this.isTaskScheduled = true
        this.actionDelay = 0
        clearBinding()
        resetState()
    }

    protected open fun finishTask(reason: String) {
        ContactQuests.debug("${this.javaClass.simpleName}: 任务结束 ($reason)")
        isTaskScheduled = false
        taskData = null
        clearBinding()
        resetState()
    }

    private fun clearBinding() {
        awaitingScreenTicks = 0
        boundScreen = null
        boundMenu = null
        boundContainerId = -1
    }

    fun onScreenClosed(screen: Screen) {
        // 玩家可能在相邻两个客户端 tick 之间快速关闭并重开同类信件界面。
        if (isTaskScheduled && (screen === boundScreen || isValidScreen(screen))) {
            finishTask("原寄件界面已关闭")
        }
    }

    protected abstract fun resetState()

    protected abstract fun isValidScreen(screen: net.minecraft.client.gui.screens.Screen?): Boolean

    protected abstract fun runLogic(mc: Minecraft, player: Player, screen: net.minecraft.client.gui.screens.Screen)

    open fun onClientTick(event: ClientTickEvent.Post) {
        if (!isTaskScheduled) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: run {
            finishTask("玩家已离开世界")
            return
        }
        val currentScreen = mc.screen

        if (boundScreen != null && (currentScreen !== boundScreen ||
                    checkContainerId && (player.containerMenu !== boundMenu ||
                    player.containerMenu.containerId != boundContainerId))) {
            finishTask("寄件界面或容器已更换")
            return
        }

        if (!isValidScreen(currentScreen) || checkContainerId &&
            (player.containerMenu.containerId == 0 ||
                    (currentScreen as? AbstractContainerScreen<*>)?.menu !== player.containerMenu)) {
            // 预填调度早于 Contact 原生延迟打开界面的时机，此处使用独立等待计时器，避免正常点击延时耗尽超时额度。
            if (++awaitingScreenTicks > maxTimeoutTicks) finishTask("等待寄件界面超时")
            return
        }

        if (boundScreen == null) {
            boundScreen = currentScreen
            boundMenu = player.containerMenu
            boundContainerId = player.containerMenu.containerId
            awaitingScreenTicks = 0
        }

        if (actionDelay > 0) {
            actionDelay--
            return
        }

        runLogic(mc, player, currentScreen!!)
    }

    protected fun click(mc: Minecraft, containerId: Int, slotId: Int, button: Int, type: ClickType, player: Player) {
        mc.gameMode?.handleInventoryMouseClick(containerId, slotId, button, type, player)
    }
}
