package com.creeperyang.contactquests.client.util

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.task.ParcelTask
import com.flechazo.contact.client.gui.screen.EnvelopeScreen
import com.flechazo.contact.client.gui.screen.WrappingPaperScreen
import com.flechazo.contact.common.screenhandler.EnvelopeScreenHandler
import com.flechazo.contact.common.screenhandler.WrappingPaperScreenHandler
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import dev.ftb.mods.ftbquests.quest.TeamData
import net.minecraft.client.Minecraft
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import kotlin.math.min

@OnlyIn(Dist.CLIENT)
object ParcelAutoFiller {
    private enum class State { IDLE, PICKUP_SOURCE, DEPOSIT_ONE, RETURN_REST }

    private var pendingReceiver: String? = null
    private var isTaskScheduled = false

    private var currentState = State.IDLE
    private var currentSourceSlot = -1
    private var currentTargetSlot = -1
    private var itemsToDeposit = 0
    private var executionDelay = 0

    private const val CLICKS_PER_TICK = 4

    data class TaskRequirement(
        val matcher: (ItemStack) -> Boolean,
        var amountNeeded: Long,
        val debugName: String
    )

    fun schedule(receiverName: String) {
        ContactQuests.debug("ParcelAutoFiller: 任务已调度! 收件人: $receiverName")
        pendingReceiver = receiverName
        isTaskScheduled = true
        executionDelay = 0
        resetState()
    }

    private fun resetState() {
        currentState = State.IDLE
        currentSourceSlot = -1
        currentTargetSlot = -1
        itemsToDeposit = 0
    }

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        if (!isTaskScheduled) return

        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val currentScreen = minecraft.screen

        val context = resolveScreenContext(currentScreen)

        if (context == null) {
            handleScreenClosedOrInvalid()
            return
        }

        val (menu, contentCount) = context

        if (executionDelay < 5) {
            executionDelay++
            return
        }

        if (player.containerMenu.containerId == 0 || player.containerMenu.containerId != menu.containerId) {
            if (executionDelay > 60) finishTask("容器同步超时") else executionDelay++
            return
        }

        runStateMachine(minecraft, player, menu, contentCount)
    }

    private data class ScreenContext(val menu: AbstractContainerMenu, val contentCount: Int)

    private fun resolveScreenContext(screen: net.minecraft.client.gui.screens.Screen?): ScreenContext? {
        return when (screen) {
            is WrappingPaperScreen -> ScreenContext(screen.menu, WrappingPaperScreenHandler.CONTENT_COUNT)
            is EnvelopeScreen -> ScreenContext(screen.menu, EnvelopeScreenHandler.CONTENT_COUNT)
            else -> null
        }
    }

    private fun handleScreenClosedOrInvalid() {
        executionDelay++
        if (executionDelay > 60) {
            finishTask("界面关闭或超时")
        }
    }

    private fun runStateMachine(mc: Minecraft, player: net.minecraft.world.entity.player.Player, menu: AbstractContainerMenu, contentCount: Int) {
        when (currentState) {
            State.IDLE -> handleIdleState(mc, player, menu, contentCount)
            State.PICKUP_SOURCE -> handlePickupSource(mc, menu, player)
            State.DEPOSIT_ONE -> handleDepositOne(mc, menu, player)
            State.RETURN_REST -> handleReturnRest(mc, menu, player)
        }
    }

    private fun handleIdleState(mc: Minecraft, player: net.minecraft.world.entity.player.Player, menu: AbstractContainerMenu, contentCount: Int) {
        if (!player.containerMenu.carried.isEmpty) return

        val receiver = pendingReceiver ?: return
        val teamData = ClientQuestFile.INSTANCE.selfTeamData

        val requirements = getRequirementsForReceiver(receiver, teamData)
        if (requirements.isEmpty()) {
            finishTask("无需求")
            return
        }

        reduceRequirementsFromInputSlots(menu, requirements, contentCount)

        val activeReqs = requirements.filter { it.amountNeeded > 0 }
        if (activeReqs.isEmpty()) {
            finishTask("需求满足")
            return
        }

        scanInventoryAndSetupAction(mc, menu, player, activeReqs, contentCount)
    }

    private fun handlePickupSource(mc: Minecraft, menu: AbstractContainerMenu, player: net.minecraft.world.entity.player.Player) {
        click(mc, menu.containerId, currentSourceSlot, 0, ClickType.PICKUP, player)
        currentState = State.DEPOSIT_ONE
    }

    private fun handleDepositOne(mc: Minecraft, menu: AbstractContainerMenu, player: net.minecraft.world.entity.player.Player) {
        var clicksThisTick = 0
        while (itemsToDeposit > 0 && clicksThisTick < CLICKS_PER_TICK) {
            click(mc, menu.containerId, currentTargetSlot, 1, ClickType.PICKUP, player)
            itemsToDeposit--
            clicksThisTick++
        }

        if (itemsToDeposit <= 0) {
            currentState = State.RETURN_REST
        }
    }

    private fun handleReturnRest(mc: Minecraft, menu: AbstractContainerMenu, player: net.minecraft.world.entity.player.Player) {
        click(mc, menu.containerId, currentSourceSlot, 0, ClickType.PICKUP, player)
        resetState()
    }

    private fun reduceRequirementsFromInputSlots(menu: AbstractContainerMenu, requirements: List<TaskRequirement>, contentCount: Int) {
        for (i in 0 until contentCount) {
            val stack = menu.getSlot(i).item
            if (!stack.isEmpty) {
                for (req in requirements) {
                    if (req.amountNeeded > 0 && req.matcher(stack)) {
                        req.amountNeeded -= stack.count
                    }
                }
            }
        }
    }

    private fun scanInventoryAndSetupAction(
        mc: Minecraft,
        menu: AbstractContainerMenu,
        player: net.minecraft.world.entity.player.Player,
        activeReqs: List<TaskRequirement>,
        contentCount: Int
    ) {
        val inventoryEnd = menu.slots.size

        for (slotIndex in contentCount until inventoryEnd) {
            val slot = menu.getSlot(slotIndex)
            if (!slot.hasItem()) continue
            val stack = slot.item

            for (req in activeReqs) {
                if (req.matcher(stack)) {
                    val targetIdx = findEmptyInputSlot(menu, contentCount)
                    if (targetIdx == -1) {
                        finishTask("包装纸已满")
                        return
                    }

                    setupTransferAction(mc, menu.containerId, player, slotIndex, targetIdx, stack, req.amountNeeded)
                    return
                }
            }
        }
        finishTask("无合适物品")
    }

    private fun findEmptyInputSlot(menu: AbstractContainerMenu, contentCount: Int): Int {
        for (i in 0 until contentCount) {
            if (!menu.getSlot(i).hasItem()) return i
        }
        return -1
    }

    private fun setupTransferAction(
        mc: Minecraft,
        containerId: Int,
        player: net.minecraft.world.entity.player.Player,
        sourceSlot: Int,
        targetSlot: Int,
        stack: ItemStack,
        amountNeeded: Long
    ) {
        val have = stack.count.toLong()

        if (have <= amountNeeded) {
            ContactQuests.debug("ParcelAutoFiller: 快速移动全部 ${stack.hoverName.string}")
            click(mc, containerId, sourceSlot, 0, ClickType.QUICK_MOVE, player)
        } else {
            val moveAmount = min(amountNeeded, stack.maxStackSize.toLong()).toInt()
            ContactQuests.debug("ParcelAutoFiller: 启动精准拆分 ${stack.hoverName.string} x$moveAmount")

            currentSourceSlot = sourceSlot
            currentTargetSlot = targetSlot
            itemsToDeposit = moveAmount
            currentState = State.PICKUP_SOURCE
        }
    }

    private fun finishTask(reason: String) {
        ContactQuests.debug("ParcelAutoFiller: 任务结束 ($reason)")
        isTaskScheduled = false
        pendingReceiver = null
        resetState()
    }

    private fun click(mc: Minecraft, containerId: Int, slotId: Int, button: Int, type: ClickType, player: net.minecraft.world.entity.player.Player) {
        mc.gameMode?.handleInventoryMouseClick(containerId, slotId, button, type, player)
    }

    private fun getRequirementsForReceiver(receiverName: String, teamData: TeamData): List<TaskRequirement> {
        val questFile = ClientQuestFile.INSTANCE ?: return emptyList()
        val targetName = receiverName.trim()

        return questFile.allChapters.asSequence()
            .flatMap { it.quests }
            .filter { teamData.canStartTasks(it) }
            .flatMap { it.tasks }
            .filterIsInstance<ParcelTask>()
            .filter { task ->
                task.targetAddressee.trim() == targetName && !teamData.isCompleted(task)
            }
            .mapNotNull { task ->
                val needed = task.getAmountNeeded(teamData)
                if (needed > 0) {
                    TaskRequirement(
                        matcher = { stack -> task.test(stack) },
                        amountNeeded = needed,
                        debugName = task.title.string
                    )
                } else null
            }
            .toList()
    }
}