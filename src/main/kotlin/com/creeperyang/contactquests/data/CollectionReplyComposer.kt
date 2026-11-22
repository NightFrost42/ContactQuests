package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.config.ErrorSolveData
import com.creeperyang.contactquests.config.MessageData
import com.creeperyang.contactquests.config.StyleType
import com.creeperyang.contactquests.utils.ContactMailNbt
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.data.PostcardDataManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.random.Random

/** 负责根据 NPC 异常处理策略挑选加权回信文本、执行内置/KubeJS 占位符替换并生成真实明信片投递。 */
internal object CollectionReplyComposer {
    private val ITEM_NAME_PATTERN = Pattern.compile("<item_(\\d+)_name>")
    private val ITEM_COUNT_PATTERN = Pattern.compile("<item_(\\d+)_count>")

    fun buildDefaultReplacers(): List<CollectionSavedData.CollectionTextReplacer> = listOf(
        CollectionSavedData.CollectionTextReplacer { text, context ->
            text.replace("<trigger_count>", context.triggerCount.toString())
                .replace("<triggerCount>", context.triggerCount.toString())
        },
        CollectionSavedData.CollectionTextReplacer { text, context ->
            val matcher = ITEM_NAME_PATTERN.matcher(text)
            val sb = StringBuilder()
            while (matcher.find()) {
                val index = matcher.group(1).toIntOrNull() ?: return@CollectionTextReplacer null
                val listIndex = index - 1
                if (listIndex < 0 || listIndex >= context.itemStacks.size) return@CollectionTextReplacer null
                matcher.appendReplacement(sb, Matcher.quoteReplacement(context.itemStacks[listIndex].hoverName.string))
            }
            matcher.appendTail(sb)
            sb.toString()
        },
        CollectionSavedData.CollectionTextReplacer { text, context ->
            val matcher = ITEM_COUNT_PATTERN.matcher(text)
            val sb = StringBuilder()
            while (matcher.find()) {
                val index = matcher.group(1).toIntOrNull() ?: return@CollectionTextReplacer null
                val listIndex = index - 1
                if (listIndex < 0 || listIndex >= context.itemStacks.size) return@CollectionTextReplacer null
                matcher.appendReplacement(sb, context.itemStacks[listIndex].count.toString())
            }
            matcher.appendTail(sb)
            sb.toString()
        },
        CollectionSavedData.CollectionTextReplacer { text, context ->
            val total = context.itemStacks.sumOf { it.count }
            text.replace("<total_amount>", total.toString())
                .replace("<total_count>", total.toString())
        },
        CollectionSavedData.CollectionTextReplacer { text, context ->
            val types = context.itemStacks.size
            text.replace("<total_types>", types.toString())
        }
    )

    fun extractPostcardStyle(stack: ItemStack): String? {
        if (stack.item !is PostcardItem) return null
        return ContactMailNbt.getPostcardStyleRaw(stack).takeIf { it.isNotEmpty() }
    }

    fun processAndDistribute(
        store: CollectionSavedData,
        player: ServerPlayer,
        name: String,
        npcData: ErrorSolveData,
        itemsToSend: MutableList<ItemStack>,
        contextItems: List<ItemStack>,
        incomingStyle: String? = null,
        isEnderOverride: Boolean = false
    ) {
        val triggerCount = store.getTriggerCount(player.uuid, name)
        val (selectedMsgData, processedText) = selectValidMessage(player, name, triggerCount, npcData, contextItems)
        val isEnder = isEnderOverride || npcData.isAllEnder || selectedMsgData.isEnder
        val finalMessageData = selectedMsgData.copy(text = processedText, isEnder = isEnder)
        itemsToSend.add(generatePostcard(name, npcData, finalMessageData, incomingStyle))
        for (item in itemsToSend) {
            RewardDistributionManager.distribute(player, item, name, isEnder)
        }
    }

    fun generatePostcard(
        name: String,
        npcData: ErrorSolveData,
        messageData: MessageData,
        incomingStyle: String? = null
    ): ItemStack {
        val styleType = npcData.styleType
        val isEnder = npcData.isAllEnder || messageData.isEnder
        val styleId = when (styleType) {
            StyleType.RANDOM -> {
                val allStyles = PostcardDataManager.getPostcards().keys.map { it.toString() }.sorted()
                if (allStyles.isNotEmpty()) allStyles[Random.nextInt(allStyles.size)] else "contact:stripes"
            }
            StyleType.SAME -> incomingStyle?.takeIf { it.isNotBlank() } ?: npcData.style
            StyleType.SPECIFIC -> messageData.style
        }
        val style = if (styleId.isNotBlank()) ResourceLocation.tryParse(styleId.trim())?.takeIf { it.path.isNotEmpty() } else null
        var postcard = if (style != null) {
            PostcardItem.getPostcard(style, isEnder)
        } else {
            PostcardItem.getPostcard(ResourceLocation("contact", "default"), isEnder)
        }

        val message = messageData.text.replace("\\n", "\n")
        if (messageData.text.isNotEmpty()) {
            postcard = ContactMailNbt.setText(postcard, message)
        }

        ContactMailNbt.setSender(postcard, name)
        return postcard
    }

    private fun selectValidMessage(
        player: ServerPlayer,
        name: String,
        triggerCount: Int,
        npcData: ErrorSolveData,
        contextItems: List<ItemStack>
    ): Pair<MessageData, String> {
        val messages = ArrayList(npcData.message)
        val context = CollectionSavedData.ReplacerContext(player, name, triggerCount, contextItems)

        while (messages.isNotEmpty()) {
            val pickedMsg = pickWeightedMessage(messages)
            val processedText = applyReplacements(pickedMsg.text, context, CollectionSavedData.currentReplacers())
            if (processedText != null) {
                return Pair(pickedMsg, processedText)
            }
            messages.remove(pickedMsg)
        }
        val defaultMsg = MessageData(text = "Error: No valid message", weight = 1)
        return Pair(defaultMsg, defaultMsg.text)
    }

    private fun pickWeightedMessage(messages: List<MessageData>): MessageData {
        if (messages.isEmpty()) return MessageData()
        val totalWeight = messages.sumOf { it.weight }
        if (totalWeight <= 0) return messages.random()
        var randomVal = Random.nextInt(totalWeight)
        for (msg in messages) {
            randomVal -= msg.weight
            if (randomVal < 0) return msg
        }
        return messages.first()
    }

    fun applyReplacements(
        text: String,
        context: CollectionSavedData.ReplacerContext,
        replacers: List<CollectionSavedData.CollectionTextReplacer>
    ): String? {
        var currentText = text
        for (replacer in replacers) {
            val result = replacer.replace(currentText, context) ?: return null
            currentText = result
        }
        return currentText
    }
}
