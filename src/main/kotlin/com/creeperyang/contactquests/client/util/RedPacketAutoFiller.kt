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
import net.minecraftforge.eventbus.api.SubscribeEvent

@OnlyIn(Dist.CLIENT)
object RedPacketAutoFiller : BaseAutoFiller<RedPacketTask>() {

    private enum class State { IDLE, FILL_TEXT, WAIT_TEXT, PICKUP_SOURCE, DEPOSIT, RETURN_REST }

    private var currentState = State.IDLE
    private var currentSourceSlot = -1

    data class RedPacketRequirement(
        val itemStack: ItemStack,
        val blessing: String
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
    override fun onClientTick(event: ClientTickEvent.Post) {
        super.onClientTick(event)
    }

    override fun runLogic(mc: Minecraft, player: Player, screen: Screen) {
        val envScreen = screen as RedPacketEnvelopeScreen
        val menu = envScreen.menu

        when (currentState) {
            State.IDLE -> handleIdleState(menu)
            State.FILL_TEXT -> handleFillTextState(envScreen, menu)
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
            currentState = State.FILL_TEXT
            return
        }

        val slotItem = menu.getSlot(0).item
        val isItemCorrect = if (slotItem.isEmpty) {
            req.itemStack.isEmpty
        } else {
            ItemMatchingSystem.INSTANCE.doesItemMatch(req.itemStack, slotItem, taskData?.matchComponents)
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

    private fun findItemAndSetup(menu: RedPacketEnvelopeScreenHandler, req: RedPacketRequirement) {
        val inventoryStart = RedPacketEnvelopeScreenHandler.CONTENT_COUNT
        val inventoryEnd = menu.slots.size

        for (i in inventoryStart until inventoryEnd) {
            val stack = menu.getSlot(i).item
            if (ItemMatchingSystem.INSTANCE.doesItemMatch(req.itemStack, stack, taskData?.matchComponents)) {
                currentSourceSlot = i
                currentState = State.PICKUP_SOURCE
                ContactQuests.debug("RedPacketAutoFiller: 找到物品在槽位 $i")
                return
            }
        }
        finishTask("未找到匹配物品")
    }

    private fun handlePickupSource(mc: Minecraft, menu: RedPacketEnvelopeScreenHandler, player: Player) {
        click(mc, menu.containerId, currentSourceSlot, 0, ClickType.PICKUP, player)
        currentState = State.DEPOSIT
        actionDelay = 1
    }

    private fun handleDeposit(mc: Minecraft, menu: RedPacketEnvelopeScreenHandler, player: Player) {
        click(mc, menu.containerId, 0, 1, ClickType.PICKUP, player) // 放入单个

        val carried = player.containerMenu.carried
        currentState = if (!carried.isEmpty) {
            State.RETURN_REST
        } else {
            State.IDLE
        }
        actionDelay = 1
    }

    private fun handleReturnRest(mc: Minecraft, menu: RedPacketEnvelopeScreenHandler, player: Player) {
        click(mc, menu.containerId, currentSourceSlot, 0, ClickType.PICKUP, player)
        currentState = State.IDLE
        actionDelay = 1
    }

    private fun getCurrentTaskRequirement(teamData: TeamData): RedPacketRequirement? {
        val task = taskData ?: return null
        if (teamData.isCompleted(task)) return null
        return RedPacketRequirement(task.itemStack, task.blessing)
    }
}