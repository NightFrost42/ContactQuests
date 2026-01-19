package com.creeperyang.contactquests.client.util

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.mixin.RedPacketEnvelopeScreenAccessor
import com.creeperyang.contactquests.quest.task.RedPacketTask
import com.flechazo.contact.client.gui.screen.RedPacketEnvelopeScreen
import com.flechazo.contact.common.screenhandler.RedPacketEnvelopeScreenHandler
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import dev.ftb.mods.ftbquests.integration.item_filtering.ItemMatchingSystem
import dev.ftb.mods.ftbquests.quest.TeamData
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import kotlin.math.min

@OnlyIn(Dist.CLIENT)
object RedPacketAutoFiller : BaseAutoFiller<RedPacketTask>() {

    private enum class State { IDLE, FILL_TEXT, WAIT_TEXT, PICKUP_SOURCE, DEPOSIT, RETURN_REST }

    private var currentState = State.IDLE
    private var currentSourceSlot = -1

    private var itemsToDeposit = 0
    private const val CLICKS_PER_TICK = 4

    data class RedPacketRequirement(
        val itemStack: ItemStack,
        val blessing: String,
        val amountNeeded: Long
    )

    override fun resetState() {
        currentState = State.IDLE
        currentSourceSlot = -1
        actionDelay = 0
    }

    override fun isValidScreen(screen: Screen?): Boolean {
        return screen is RedPacketEnvelopeScreen
    }

    @SubscribeEvent
    override fun onClientTick(event: TickEvent.ClientTickEvent) {
        super.onClientTick(event)
    }

    override fun runLogic(mc: Minecraft, player: Player, screen: Screen) {
        val envScreen = screen as RedPacketEnvelopeScreen
        val menu = envScreen.menu

        when (currentState) {
            State.IDLE -> handleIdleState(menu, player)
            State.FILL_TEXT -> handleFillTextState(envScreen, menu, player)
            State.WAIT_TEXT -> handleWaitTextState()
            State.PICKUP_SOURCE -> handlePickupSource(mc, menu, player)
            State.DEPOSIT -> handleDeposit(mc, menu, player)
            State.RETURN_REST -> handleReturnRest(mc, menu, player)
        }
    }

    private fun handleIdleState(menu: RedPacketEnvelopeScreenHandler, player: Player) {
        val teamData = ClientQuestFile.INSTANCE.selfTeamData
        val req = getCurrentTaskRequirement(teamData, player)
        if (req == null) {
            finishTask("任务无效或已完成")
            return
        }

        if (menu.blessings != req.blessing) {
            currentState = State.FILL_TEXT
            return
        }

        val slotItem = menu.getSlot(0).item

        if (!slotItem.isEmpty) {
            val isMatch = !req.itemStack.isEmpty &&
                    ItemMatchingSystem.INSTANCE.doesItemMatch(
                        req.itemStack,
                        slotItem,
                        taskData!!.shouldMatchNBT(),
                        taskData!!.weakNBTmatch
                    )

            if (!isMatch) {
                finishTask("槽位已有错误物品")
                return
            }

            val targetCount = min(req.amountNeeded, 64L).toInt()
            if (slotItem.count >= targetCount) {
                finishTask("需求已满足")
                return
            }
        } else {
            if (req.itemStack.isEmpty) {
                finishTask("无需物品")
                return
            }
        }
        findItemAndSetup(menu, req, player)
    }

    private fun handleFillTextState(
        screen: RedPacketEnvelopeScreen,
        menu: RedPacketEnvelopeScreenHandler,
        player: Player
    ) {
        val teamData = ClientQuestFile.INSTANCE.selfTeamData
        val req = getCurrentTaskRequirement(teamData, player) ?: return

        if (screen is RedPacketEnvelopeScreenAccessor) {
            val editBox = screen.`contactQuests$getBlessingsBox`()
            if (editBox != null) {
                editBox.value = req.blessing
            }
            menu.blessings = req.blessing
            ContactQuests.debug("RedPacketAutoFiller: 写入祝福语 '${req.blessing}'")
        } else {
            ContactQuests.error("RedPacketAutoFiller: Mixin 转换失败!")
        }

        currentState = State.WAIT_TEXT
        actionDelay = 1
    }

    private fun handleWaitTextState() {
        currentState = State.IDLE
        actionDelay = 0
    }

    private fun findItemAndSetup(menu: RedPacketEnvelopeScreenHandler, req: RedPacketRequirement, player: Player) {
        val inventoryStart = RedPacketEnvelopeScreenHandler.CONTENT_COUNT
        val inventoryEnd = menu.slots.size

        for (i in inventoryStart until inventoryEnd) {
            val stack = menu.getSlot(i).item
            if (ItemMatchingSystem.INSTANCE.doesItemMatch(
                    req.itemStack,
                    stack,
                    taskData!!.shouldMatchNBT(),
                    taskData!!.weakNBTmatch
                )
            ) {
                setupTransferAction(menu.containerId, player, i, stack, req.amountNeeded)
                return
            }
        }
        finishTask("未找到匹配物品")
    }

    private fun setupTransferAction(
        containerId: Int, player: Player, sourceSlot: Int,
        stack: ItemStack, amountNeeded: Long
    ) {
        val mc = Minecraft.getInstance()
        val maxAddable = 64
        val amountToTransfer = min(amountNeeded, maxAddable.toLong()).toInt()

        if (stack.count <= amountToTransfer) {
            ContactQuests.debug("RedPacketAutoFiller: 快速移动全部 ${stack.hoverName.string}")
            click(mc, containerId, sourceSlot, 0, ClickType.QUICK_MOVE, player)
            currentState = State.IDLE
            actionDelay = 1
        } else {
            ContactQuests.debug("RedPacketAutoFiller: 启动精准拆分 ${stack.hoverName.string} x$amountToTransfer")
            currentSourceSlot = sourceSlot
            itemsToDeposit = amountToTransfer
            currentState = State.PICKUP_SOURCE
            actionDelay = 1
        }
    }

    private fun handlePickupSource(mc: Minecraft, menu: RedPacketEnvelopeScreenHandler, player: Player) {
        click(mc, menu.containerId, currentSourceSlot, 0, ClickType.PICKUP, player)
        currentState = State.DEPOSIT
        actionDelay = 1
    }

    private fun handleDeposit(mc: Minecraft, menu: RedPacketEnvelopeScreenHandler, player: Player) {
        var clicksThisTick = 0
        while (itemsToDeposit > 0 && clicksThisTick < CLICKS_PER_TICK) {
            click(mc, menu.containerId, 0, 1, ClickType.PICKUP, player)
            itemsToDeposit--
            clicksThisTick++
        }

        if (itemsToDeposit <= 0) {
            val carried = player.containerMenu.carried
            currentState = if (!carried.isEmpty) {
                State.RETURN_REST
            } else {
                State.IDLE
            }
        }
    }

    private fun handleReturnRest(mc: Minecraft, menu: RedPacketEnvelopeScreenHandler, player: Player) {
        click(mc, menu.containerId, currentSourceSlot, 0, ClickType.PICKUP, player)
        currentState = State.IDLE
        actionDelay = 1
    }

    private fun getCurrentTaskRequirement(teamData: TeamData, player: Player): RedPacketRequirement? {
        val task = taskData ?: return null
        if (teamData.isCompleted(task)) return null

        val needed = task.count - teamData.getProgress(task)

        return RedPacketRequirement(task.itemStack, task.blessing, needed)
    }
}