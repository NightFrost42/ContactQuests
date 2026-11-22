package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.compat.kubejs.ContactKubeJSPlugin
import com.creeperyang.contactquests.compat.kubejs.KubeJSMailQueueSavedData
import com.creeperyang.contactquests.compat.kubejs.KubeJSNpcSavedData
import com.creeperyang.contactquests.compat.kubejs.MailReceivedEventJS
import com.creeperyang.contactquests.config.ContactConfig
import com.creeperyang.contactquests.config.NpcConfigManager
import com.creeperyang.contactquests.llm.NpcLlmConfig
import com.creeperyang.contactquests.llm.NpcLlmService
import com.creeperyang.contactquests.quest.task.ContactTask
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.creeperyang.contactquests.quest.task.RedPacketTask
import com.creeperyang.contactquests.utils.ContactMailNbt
import com.flechazo.contact.common.item.ParcelItem
import com.flechazo.contact.common.item.PostcardItem
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.TeamData
import net.minecraft.world.SimpleContainer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraftforge.fml.ModList
import java.math.BigInteger
import kotlin.math.min

/** 负责将玩家寄出的邮件物品在多个匹配任务之间按完成度比例分配，并将剩余物转交后续延迟队列或暂存回信管线。 */
internal object TaskMailAllocator {
    data class TaskStrategy<T : ContactTask>(
        val receiverMap: Map<String, MutableSet<Long>>,
        val taskMap: Map<Long, T>
    )

    private data class Candidate<T : ContactTask>(val task: T, val amount: Long, val indices: List<Int>)

    fun <T : ContactTask> allocateTasks(
        player: ServerPlayer,
        teamData: TeamData,
        initialStacks: List<ItemStack>,
        contextStack: ItemStack,
        recipientName: String,
        strategy: TaskStrategy<T>
    ): List<ItemStack> {
        val remaining = initialStacks.map { it.copy() }.toMutableList()
        val taskIds = strategy.receiverMap[recipientName]?.toMutableSet() ?: return remaining
        val queue = TaskDeliverySavedData[player.server.overworld()]
        val sendTime = calculateEffectiveSendTime(player.serverLevel(), recipientName, contextStack)
        while (taskIds.isNotEmpty()) {
            val candidates = taskIds.mapNotNull { id ->
                val task = strategy.taskMap[id] ?: return@mapNotNull null
                if (teamData.isLocked || !teamData.canStartTasks(task.quest) || teamData.isCompleted(task)) return@mapNotNull null
                val indices = remaining.indices.filter { matches(task, player, teamData, remaining[it]) }
                val available = indices.sumOf { index -> progressStack(task, remaining[index]).count.toLong() }
                val capacity = (task.getAmountNeeded(teamData) - queue.getPendingAmount(teamData.teamId, id)).coerceAtLeast(0L)
                val amount = min(available, capacity)
                if (amount > 0) Candidate(task, amount, indices) else null
            }
            // 使用精确分数交叉相乘进行比较，避免任务需求上限接近 Long.MAX_VALUE 时发生浮点精度丢失或溢出。
            val selected = candidates.maxWithOrNull { a, b ->
                val left = BigInteger.valueOf(a.amount).multiply(BigInteger.valueOf(b.task.count))
                val right = BigInteger.valueOf(b.amount).multiply(BigInteger.valueOf(a.task.count))
                val comparison = left.compareTo(right)
                if (comparison != 0) comparison else java.lang.Long.compareUnsigned(b.task.id, a.task.id)
            } ?: break
            taskIds.remove(selected.task.id)
            for (index in selected.indices) {
                val stack = remaining[index]
                val contents = if (selected.task is RedPacketTask) {
                    parcelContents(stack)
                } else null
                val submittedStack = contents?.firstOrNull { !it.isEmpty } ?: if (contents == null) stack else ItemStack.EMPTY
                val submitted = executeDelivery(player, teamData, selected.task, submittedStack, sendTime, recipientName)
                submittedStack.shrink(submitted.toInt())
                if (contents != null && submitted > 0) {
                    remaining[index] = if (contents.all { it.isEmpty }) ItemStack.EMPTY else stack.copy().also {
                        val container = SimpleContainer(contents.size)
                        contents.forEachIndexed { slot, content -> container.setItem(slot, content) }
                        ContactMailNbt.writeContents(it, container)
                    }
                }
            }
        }
        return remaining.filter { !it.isEmpty }
    }

    fun dispatchToLlmNpc(
        player: ServerPlayer,
        recipientName: String,
        delay: Int,
        originalItems: List<ItemStack>,
        remainder: List<ItemStack>,
        isPostcard: Boolean,
        isRedPacket: Boolean
    ) {
        if (delay > 0) {
            KubeJSMailQueueSavedData.get(player.serverLevel()).addMail(
                player, recipientName, remainder, delay, isPostcard, isRedPacket, originalItems
            )
        } else {
            NpcLlmService.receiveAfterTasks(player, recipientName, originalItems, remainder)
        }
    }

    fun receiveRemainder(
        player: ServerPlayer,
        recipient: String,
        items: List<ItemStack>,
        delay: Int,
        isPostcard: Boolean,
        isRedPacket: Boolean
    ): Boolean {
        if (items.isEmpty()) return false
        val afterChoice = ChoiceRewardSavedData.get(player.serverLevel()).receive(player, recipient, items, delay)
        if (afterChoice.isEmpty()) return true
        if (NpcLlmConfig.npc(recipient) != null) {
            if (delay > 0) {
                KubeJSMailQueueSavedData.get(player.serverLevel()).addMail(
                    player, recipient, afterChoice.map { it.copy() }, delay, isPostcard, isRedPacket
                )
            } else {
                NpcLlmService.receive(player, recipient, afterChoice)
            }
            return true
        }
        if (delay > 0 && KubeJSNpcSavedData.get(player.serverLevel()).getNpcData(recipient) != null) {
            KubeJSMailQueueSavedData.get(player.serverLevel()).addMail(
                player, recipient, afterChoice.map { it.copy() }, delay, isPostcard, isRedPacket
            )
        } else {
            dispatchRemainder(player, recipient, afterChoice, isPostcard)
        }
        return true
    }

    fun dispatchRemainder(player: ServerPlayer, recipient: String, items: List<ItemStack>, isPostcard: Boolean) {
        val remaining = items.filter { !it.isEmpty }
        if (remaining.isEmpty()) return
        if (NpcLlmConfig.npc(recipient) != null) {
            NpcLlmService.receive(player, recipient, remaining)
            return
        }
        if (ModList.get().isLoaded("kubejs")) {
            val event = MailReceivedEventJS(player, recipient, remaining.map { it.copy() }, isPostcard)
            ContactKubeJSPlugin.MAIL_RECEIVED.post(event)
            if (event.isIntercepted()) return
        }
        CollectionSavedData.get(player.server.overworld()).addItems(player, recipient, remaining)
    }

    fun calculateEffectiveSendTime(level: ServerLevel, recipientName: String, contextStack: ItemStack): Int {
        if (!ContactConfig.enableDeliveryTime.get()) return 0

        val configTime = NpcConfigManager.getDeliveryTime(recipientName, level)
        if (configTime <= 0) return 0

        val item = contextStack.item
        if (item is ParcelItem && item.isEnderType) {
            ContactQuests.debug("检测到末影包裹！$recipientName 即时送达。")
            return 0
        } else if (item is PostcardItem && item.isEnderType) {
            ContactQuests.debug("检测到末影明信片！$recipientName 即时送达。")
            return 0
        }

        return configTime
    }

    private fun parcelContents(stack: ItemStack): List<ItemStack> = ContactMailNbt.readContents(stack)

    private fun progressStack(task: ContactTask, mail: ItemStack): ItemStack =
        if (task is RedPacketTask) parcelContents(mail).firstOrNull() ?: ItemStack.EMPTY else mail

    private fun matches(task: ContactTask, player: ServerPlayer, data: TeamData, stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (task is PostcardTask) task.setContext(player, data)
        if (task is RedPacketTask) task.setContext(player, data)
        return try {
            task.test(stack)
        } finally {
            if (task is PostcardTask) task.clearContext()
            if (task is RedPacketTask) task.clearContext()
        }
    }

    private fun <T : ContactTask> executeDelivery(
        player: ServerPlayer,
        teamData: TeamData,
        task: T,
        stackToSubmit: ItemStack,
        sendTime: Int,
        recipientName: String
    ): Long {
        if (teamData.isLocked || stackToSubmit.isEmpty) return 0
        val queue = TaskDeliverySavedData[player.server.overworld()]
        val available = (task.getAmountNeeded(teamData) - queue.getPendingAmount(teamData.teamId, task.id)).coerceAtLeast(0L)
        val amount = min(stackToSubmit.count.toLong(), available)
        if (amount <= 0) return 0

        if (sendTime > 0) {
            queue.addParcel(player, task.id, sendTime, recipientName, amount, teamData.teamId)
            return amount
        }

        val before = teamData.getProgress(task)
        val submission = stackToSubmit.copy().also { it.count = amount.toInt() }
        ServerQuestFile.INSTANCE.withPlayerContext(player) {
            // 上游已完成包含动态玩家/团队占位符上下文在内的物品匹配，此处跳过重复测试以避免二次求值。
            task.insert(teamData, submission, false, needTest = false)
        }
        return (teamData.getProgress(task) - before).coerceIn(0L, amount)
    }
}
