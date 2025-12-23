package com.creeperyang.contactquests.client.util

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.task.RedPacketTask
import com.flechazo.contact.client.gui.screen.RedPacketEnvelopeScreen
import com.flechazo.contact.common.screenhandler.RedPacketEnvelopeScreenHandler
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem
import dev.ftb.mods.ftbquests.quest.TeamData
import net.minecraft.client.Minecraft
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.lang.reflect.Field

@OnlyIn(Dist.CLIENT)
object RedPacketAutoFiller {

    private enum class State {
        IDLE,
        FILL_TEXT,
        WAIT_TEXT,
        PICKUP_SOURCE,
        DEPOSIT,
        RETURN_REST
    }

    private var pendingTask: RedPacketTask? = null
    private var isTaskScheduled = false

    private var currentState = State.IDLE
    private var currentSourceSlot = -1
    private var actionDelay = 0

    private var cachedBlessingsField: Field? = null

    data class RedPacketRequirement(
        val itemStack: ItemStack,
        val blessing: String,
        val taskTitle: String
    )

    fun schedule(task: RedPacketTask) {
        ContactQuests.debug("RedPacketAutoFiller: 任务已调度! 目标任务: ${task.title.string}")
        pendingTask = task
        isTaskScheduled = true
        actionDelay = 0
        currentState = State.IDLE
        currentSourceSlot = -1
    }

    private fun resetState() {
        currentState = State.IDLE
        currentSourceSlot = -1
        actionDelay = 0
    }

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        if (!isTaskScheduled) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val currentScreen = mc.screen

        if (currentScreen !is RedPacketEnvelopeScreen) {
            handleScreenClosedOrInvalid()
            return
        }

        val menu = currentScreen.menu

        if (player.containerMenu.containerId != menu.containerId) {
            return
        }

        if (actionDelay > 0) {
            actionDelay--
            return
        }

        runStateMachine(mc, currentScreen, player, menu)
    }

    private fun handleScreenClosedOrInvalid() {
        actionDelay++
        if (actionDelay > 20) {
            finishTask("界面关闭")
        }
    }

    private fun runStateMachine(
        mc: Minecraft,
        screen: RedPacketEnvelopeScreen,
        player: net.minecraft.world.entity.player.Player,
        menu: RedPacketEnvelopeScreenHandler
    ) {
        when (currentState) {
            State.IDLE -> handleIdleState(menu)
            State.FILL_TEXT -> handleFillTextState(screen, menu)
            State.WAIT_TEXT -> handleWaitTextState()
            State.PICKUP_SOURCE -> handlePickupSource(mc, menu, player)
            State.DEPOSIT -> handleDeposit(mc, menu, player)
            State.RETURN_REST -> handleReturnRest(mc, menu, player)
        }
    }

    private fun handleIdleState(menu: RedPacketEnvelopeScreenHandler) {
        val teamData = ClientQuestFile.INSTANCE.selfTeamData
        val req = getCurrentTaskRequirement(teamData)
        if (req == null) {
            finishTask("任务无效或已完成")
            return
        }

        if (menu.blessings != req.blessing) {
            ContactQuests.debug("RedPacketAutoFiller: 需要填写文本. 当前: '${menu.blessings}', 目标: '${req.blessing}'")
            currentState = State.FILL_TEXT
            return
        }

        val slotItem = menu.getSlot(0).item
        val isItemCorrect = if (slotItem.isEmpty) {
            req.itemStack.isEmpty
        } else {
            ItemMatchingSystem.INSTANCE.doesItemMatch(req.itemStack, slotItem, pendingTask?.matchComponents)
        }

        if (isItemCorrect) {
            finishTask("需求已满足")
            return
        }

        if (!req.itemStack.isEmpty) {
            if (!slotItem.isEmpty) {
                finishTask("槽位已有错误物品")
                return
            }
            findItemAndSetup(menu, req)
        }
    }

    private fun handleFillTextState(screen: RedPacketEnvelopeScreen, menu: RedPacketEnvelopeScreenHandler) {
        val teamData = ClientQuestFile.INSTANCE.selfTeamData
        val req = getCurrentTaskRequirement(teamData) ?: return

        if (screen is com.creeperyang.contactquests.mixin.RedPacketEnvelopeScreenAccessor) {

            val editBox = screen.`contactQuests$getBlessingsBox`()

            if (editBox != null) {
                editBox.setValue(req.blessing)
            }

            menu.blessings = req.blessing

            ContactQuests.debug("RedPacketAutoFiller: 写入祝福语 '${req.blessing}'")
        } else {
            ContactQuests.error("RedPacketAutoFiller: Mixin 转换失败! 请检查 mixins.json 配置")
        }

        currentState = State.WAIT_TEXT
        actionDelay = 1
    }

    private fun handleWaitTextState() {
        currentState = State.IDLE
        actionDelay = 0
    }

    private fun findItemAndSetup(menu: RedPacketEnvelopeScreenHandler, req: RedPacketRequirement) {
        val inventoryStart = RedPacketEnvelopeScreenHandler.CONTENT_COUNT
        val inventoryEnd = menu.slots.size

        for (i in inventoryStart until inventoryEnd) {
            val stack = menu.getSlot(i).item
            if (ItemMatchingSystem.INSTANCE.doesItemMatch(req.itemStack, stack, pendingTask?.matchComponents)) {
                currentSourceSlot = i
                currentState = State.PICKUP_SOURCE
                ContactQuests.debug("RedPacketAutoFiller: 找到物品在槽位 $i, 准备移动")
                return // 找到后立即返回
            }
        }
        finishTask("未找到匹配物品")
    }

    private fun handlePickupSource(mc: Minecraft, menu: RedPacketEnvelopeScreenHandler, player: net.minecraft.world.entity.player.Player) {
        click(mc, menu.containerId, currentSourceSlot, 0, ClickType.PICKUP, player)

        currentState = State.DEPOSIT
        actionDelay = 1
    }

    private fun handleDeposit(mc: Minecraft, menu: RedPacketEnvelopeScreenHandler, player: net.minecraft.world.entity.player.Player) {
        click(mc, menu.containerId, 0, 1, ClickType.PICKUP, player)

        val carried = player.containerMenu.carried
        if (!carried.isEmpty) {
            currentState = State.RETURN_REST
        } else {
            currentState = State.IDLE
        }
        actionDelay = 1
    }

    private fun handleReturnRest(mc: Minecraft, menu: RedPacketEnvelopeScreenHandler, player: net.minecraft.world.entity.player.Player) {
        click(mc, menu.containerId, currentSourceSlot, 0, ClickType.PICKUP, player)

        currentState = State.IDLE
        actionDelay = 1
    }

    private fun finishTask(reason: String) {
        ContactQuests.debug("RedPacketAutoFiller: 任务结束 ($reason)")
        isTaskScheduled = false
        pendingTask = null
        resetState()
    }

    private fun click(mc: Minecraft, containerId: Int, slotId: Int, button: Int, type: ClickType, player: net.minecraft.world.entity.player.Player) {
        mc.gameMode?.handleInventoryMouseClick(containerId, slotId, button, type, player)
    }

    private fun getCurrentTaskRequirement(teamData: TeamData): RedPacketRequirement? {
        val task = pendingTask ?: return null
        if (teamData.isCompleted(task)) return null
        return RedPacketRequirement(task.itemStack, task.blessing, task.title.string)
    }
}