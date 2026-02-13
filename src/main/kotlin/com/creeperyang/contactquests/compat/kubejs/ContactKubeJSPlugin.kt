package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.data.CollectionSavedData
import com.creeperyang.contactquests.data.DataManager
import com.creeperyang.contactquests.data.RewardDistributionManager
import com.creeperyang.contactquests.network.OpenQuestMessage
import com.creeperyang.contactquests.network.SyncQuestTextMessage
import com.creeperyang.contactquests.quest.reward.ParcelReward
import com.creeperyang.contactquests.quest.reward.PostcardReward
import com.creeperyang.contactquests.quest.task.ContactTask
import com.creeperyang.contactquests.quest.task.ParcelTask
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.creeperyang.contactquests.quest.task.RedPacketTask
import com.creeperyang.contactquests.utils.IQuestExtension
import com.creeperyang.contactquests.utils.ITeamDataExtension
import com.creeperyang.contactquests.utils.RedPacketUtils
import com.flechazo.contact.common.item.PostcardItem
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.QuestObjectBase
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbquests.quest.reward.Reward
import dev.ftb.mods.ftbquests.quest.task.Task
import dev.ftb.mods.ftbquests.quest.translation.TranslationKey
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import dev.latvian.mods.kubejs.event.EventGroup
import dev.latvian.mods.kubejs.event.KubeEvent
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.neoforged.neoforge.network.PacketDistributor
import org.apache.logging.log4j.LogManager
import java.util.function.BiFunction

object ContactKubeJSPlugin {
    val GROUP = EventGroup.of("ContactEvents")!!

    val REGISTER_REPLACERS = GROUP.server("registerReplacers") { RegisterReplacersEvent::class.java }!!

    val MAIL_RECEIVED = GROUP.server("mailReceived") { MailReceivedEventJS::class.java }!!

    private val LOGGER = LogManager.getLogger("contactquests-kubejs")

    private fun syncObject(obj: QuestObjectBase, saveToConfig: Boolean = true) {
        obj.clearCachedData()

        handleConfigState(saveToConfig)

        val server = ServerQuestFile.INSTANCE.server ?: return

        val message = createSyncPacket() ?: run {
            fallbackRefresh("Failed to create sync packet")
            return
        }

        var sent = false
        if (sendViaNetworkHelper(server, message)) sent = true
        else if (sendViaArchitectury(server, message)) sent = true

        if (!sent) {
            fallbackRefresh("Packet sync failed completely")
        }

        resendAllOverrides()
    }

    private fun resendAllOverrides() {
        val server = ServerQuestFile.INSTANCE.server ?: return

        server.tell(net.minecraft.server.TickTask(server.tickCount + 1) {
            server.playerList.players.forEach { player ->
                syncAllOverridesToPlayer(player)
            }
            LOGGER.info("ContactQuests: Resent all overrides to ${server.playerList.playerCount} players (delayed 1 tick).")
        })
    }

    private fun handleConfigState(saveToConfig: Boolean) {
        if (saveToConfig) {
            ServerQuestFile.INSTANCE.markDirty()
            ServerQuestFile.INSTANCE.saveNow()
            LOGGER.info("ContactQuests: Global edit saved to disk.")
        } else {
            resetDirtyFlagsReflectively()
        }
    }

    private fun resetDirtyFlagsReflectively() {
        runCatching {
            val file = ServerQuestFile.INSTANCE
            ServerQuestFile::class.java.declaredFields
                .filter { it.type == Boolean::class.javaPrimitiveType || it.type == Boolean::class.java }
                .forEach { field ->
                    field.isAccessible = true
                    if (field.getBoolean(file)) {
                        field.setBoolean(file, false)
                    }
                }
        }
    }

    private fun createSyncPacket(): Any? {
        val packetClasses = listOf(
            "dev.ftb.mods.ftbquests.net.SyncQuestsMessage",
            "dev.ftb.mods.ftbquests.network.SyncQuestsMessage"
        )

        return packetClasses.firstNotNullOfOrNull { className ->
            runCatching {
                val msgClass = Class.forName(className)
                msgClass.constructors.firstOrNull { ctor ->
                    ctor.parameterCount == 1 &&
                            ctor.parameterTypes[0].isAssignableFrom(ServerQuestFile.INSTANCE.javaClass)
                }?.newInstance(ServerQuestFile.INSTANCE)
            }.getOrNull()
        }
    }

    private fun sendViaNetworkHelper(server: Any, message: Any): Boolean {
        return runCatching {
            val helperClass = Class.forName("dev.ftb.mods.ftblibrary.util.NetworkHelper")
            val sendMethod = helperClass.methods.find { m ->
                m.name == "sendToAll" &&
                        m.parameterCount == 2 &&
                        m.parameterTypes[0] == MinecraftServer::class.java
            }

            sendMethod?.invoke(null, server, message)
            LOGGER.info("ContactQuests: Full sync sent via NetworkHelper.")
            true
        }.getOrDefault(false)
    }

    private fun sendViaArchitectury(server: MinecraftServer, message: Any): Boolean {
        return runCatching {
            val netManagerClass = Class.forName("dev.architectury.networking.NetworkManager")
            val sendMethod = netManagerClass.methods.find { m ->
                m.name == "sendToPlayer" &&
                        m.parameterCount == 2 &&
                        m.parameterTypes[0] == ServerPlayer::class.java
            } ?: return false

            var sentCount = 0
            for (player in server.playerList.players) {
                sendMethod.invoke(null, player, message)
                sentCount++
            }

            if (sentCount > 0) {
                LOGGER.info("ContactQuests: Full sync sent to $sentCount players via NetworkManager.")
                true
            } else false
        }.getOrDefault(false)
    }

    private fun fallbackRefresh(reason: String) {
        ServerQuestFile.INSTANCE.refreshGui()
        LOGGER.warn("ContactQuests: $reason. Triggered GUI refresh.")
    }

    private fun asItemStack(item: Any?): ItemStack? {
        if (item == null) return null
        if (item is ItemStack) return item
        try {
            return item as ItemStack
        } catch (e: ClassCastException) {
            LOGGER.warn("ContactQuests: Could not cast ${item.javaClass.name} to ItemStack.")
        }
        return null
    }

    private fun getReward(id: Any): Reward? {
        val longId = if (id is Number) id.toLong() else parseId(id.toString()) ?: return null
        val file = ServerQuestFile.INSTANCE

        val reward = file.getReward(longId)
        if (reward != null) return reward

        LOGGER.warn("ContactQuests: Reward $longId not found in index. Searching ${file.allChapters.size} chapters...")
        var questCount = 0
        var rewardCount = 0

        for (chapter in file.allChapters) {
            for (quest in chapter.quests) {
                questCount++
                for (r in quest.rewards) {
                    rewardCount++
                    if (r.id == longId) return r
                }
            }
        }

        LOGGER.warn("ContactQuests: Deep search finished. Scanned $questCount quests and $rewardCount rewards. Reward $longId not found.")
        return null
    }

    @JvmStatic
    fun init() {
        LOGGER.info("Initializing ContactQuests KubeJS Plugin Helper...")
    }

    @JvmStatic
    fun reload() {
        REGISTER_REPLACERS.post(RegisterReplacersEvent())
        LOGGER.info("Reloaded ContactQuests KubeJS integration")
    }

    private fun getQuest(id: Any): Quest? {
        val longId = resolveId(id) ?: return null
        return ServerQuestFile.INSTANCE.get(longId) as? Quest
    }

    @JvmStatic
    fun loadPersistentData(server: MinecraftServer) {
        val level = server.overworld()
        val savedData = QuestOverrideSavedData.get(level)
        val overrides = savedData.overrides

        val registryAccess = server.registryAccess()

        if (overrides.isEmpty()) return

        LOGGER.info("ContactQuests: Loading ${overrides.size} quest overrides from world data...")
        var count = 0

        for ((id, tag) in overrides) {
            try {
                val task = DataManager.parcelTasks[id]
                    ?: DataManager.redPacketTasks[id]
                    ?: DataManager.postcardTasks[id]

                if (task != null) {
                    applyTaskOverride(task, tag, registryAccess)
                    count++
                    continue
                }

                val reward = getReward(id)
                if (reward != null) {
                    applyRewardOverride(reward, tag, registryAccess)
                    count++
                }

                val quest = getQuest(id)
                if (quest != null) {
                    applyQuestOverride(quest, tag, registryAccess)
                    count++
                }

            } catch (e: Exception) {
                LOGGER.error("ContactQuests: Failed to apply override for ID $id", e)
            }
        }
        LOGGER.info("ContactQuests: Applied $count overrides.")
    }

    private fun applyQuestOverride(quest: Quest, tag: CompoundTag, provider: HolderLookup.Provider) {
        val ext = quest as? IQuestExtension ?: return

        if (tag.contains("description", Tag.TAG_COMPOUND.toInt())) {
            val map = tag.getCompound("description")
            map.allKeys.forEach { locale ->
                val list = map.getList(locale, Tag.TAG_STRING.toInt()).map { it.asString }
                ext.`contactQuests$setDescriptionOverride`(locale, ArrayList(list))
            }
        }

        if (tag.contains("title", Tag.TAG_COMPOUND.toInt())) {
            val map = tag.getCompound("title")
            map.allKeys.forEach { locale ->
                ext.`contactQuests$setTitleOverride`(locale, map.getString(locale))
            }
        }

        if (tag.contains("subtitle", Tag.TAG_COMPOUND.toInt())) {
            val map = tag.getCompound("subtitle")
            map.allKeys.forEach { locale ->
                ext.`contactQuests$setSubtitleOverride`(locale, map.getString(locale))
            }
        }

        quest.clearCachedData()
    }

    private fun applyTaskOverride(task: ContactTask, tag: CompoundTag, provider: HolderLookup.Provider) {
        val helper = OverrideHelper(tag, provider)

        when (task) {
            is ParcelTask -> {
                helper.updateItem { task.itemStack = it }
                helper.updateLong("count") { task.count = it }
                helper.updateString("target") { updateTaskTarget(task, it) }
            }

            is RedPacketTask -> {
                helper.updateItem { task.itemStack = it }
                helper.updateLong("count") { task.count = it }
                helper.updateString("blessing") { task.blessing = it }
                helper.updateString("target") { updateTaskTarget(task, it) }
            }

            is PostcardTask -> {
                helper.updateString("style") { task.postcardStyle = it }
                helper.updateString("text") { task.postcardText = it }
                helper.updateString("target") { updateTaskTarget(task, it) }
            }
        }

        if (helper.changed) syncObject(task, false)
    }

    private fun applyRewardOverride(reward: Reward, tag: CompoundTag, provider: HolderLookup.Provider) {
        val helper = OverrideHelper(tag, provider)

        when (reward) {
            is ParcelReward -> {
                helper.updateItem { reward.item = it }
                helper.updateInt("count") { reward.count = it }
                helper.updateInt("randomBonus") { reward.randomBonus = it }
                helper.updateString("target") { reward.targetAddressee = it }
            }

            is com.creeperyang.contactquests.quest.reward.RedPacketReward -> {
                helper.updateItem { reward.item = it }
                helper.updateInt("count") { reward.count = it }
                helper.updateInt("randomBonus") { reward.randomBonus = it }
                helper.updateString("blessing") { reward.blessing = it }
                helper.updateString("target") { reward.targetAddressee = it }
            }

            is PostcardReward -> {
                helper.updateString("style") { reward.postcardStyle = it }
                helper.updateString("text") { reward.postcardText = it }
                helper.updateString("target") { reward.targetAddressee = it }
            }
        }

        if (helper.changed) syncObject(reward, false)
    }

    private class OverrideHelper(val tag: CompoundTag, val provider: HolderLookup.Provider) {
        var changed = false

        private inline fun <T> update(key: String, getter: (String) -> T, setter: (T) -> Unit) {
            if (tag.contains(key)) {
                setter(getter(key))
                changed = true
            }
        }

        fun updateString(key: String, setter: (String) -> Unit) =
            update(key, { tag.getString(it) }, setter)

        fun updateInt(key: String, setter: (Int) -> Unit) =
            update(key, { tag.getInt(it) }, setter)

        fun updateLong(key: String, setter: (Long) -> Unit) =
            update(key, { tag.getLong(it) }, setter)

        fun updateItem(key: String = "item", setter: (ItemStack) -> Unit) {
            if (tag.contains(key)) {
                setter(ItemStack.parseOptional(provider, tag.getCompound(key)))
                changed = true
            }
        }

        fun updateStringList(key: String, setter: (List<String>) -> Unit) {
            if (tag.contains(key, Tag.TAG_LIST.toInt())) {
                val listTag = tag.getList(key, Tag.TAG_STRING.toInt())
                val list = listTag.map { it.asString }
                setter(list)
                changed = true
            }
        }
    }

    @JvmStatic
    fun getData(player: Player): TeamData {
        val team = FTBTeamsAPI.api().manager.getTeamForPlayerID(player.uuid).orElseThrow {
            RuntimeException("ContactQuests: Could not find FTB Team for player ${player.name.string}")
        }
        return ServerQuestFile.INSTANCE.getOrCreateTeamData(team)
    }

    @JvmStatic
    fun addTag(team: TeamData, tag: String): Boolean {
        return (team as ITeamDataExtension).`contactQuests$unlockTag`(tag)
    }

    @JvmStatic
    fun removeTag(team: TeamData, tag: String): Boolean {
        return (team as ITeamDataExtension).`contactQuests$removeTag`(tag)
    }

    @JvmStatic
    fun hasTag(team: TeamData, tag: String): Boolean {
        return (team as ITeamDataExtension).`contactQuests$hasTag`(tag)
    }

    @JvmStatic
    fun getTags(team: TeamData): Collection<String> {
        return (team as ITeamDataExtension).`contactQuests$getTags`()
    }

    @JvmStatic
    fun setNpcConfig(level: ServerLevel, name: String, deliveryTime: Int, autoFill: Boolean) {
        KubeJSNpcSavedData.get(level).setNpcData(name, deliveryTime, autoFill)
    }

    @JvmStatic
    fun clearNpcConfigs(level: ServerLevel) {
        KubeJSNpcSavedData.get(level).clearAll()
    }

    @JvmStatic
    fun removeNpcConfig(level: ServerLevel, name: String) {
        KubeJSNpcSavedData.get(level).removeNpcData(name)
    }

    @JvmStatic
    fun setTriggerCount(player: ServerPlayer, name: String, count: Int) {
        CollectionSavedData.get(player.serverLevel()).setTriggerCount(player.uuid, name, count)
    }

    @JvmStatic
    fun setTriggerCount(level: ServerLevel, uuid: java.util.UUID, name: String, count: Int) {
        CollectionSavedData.get(level).setTriggerCount(uuid, name, count)
    }

    @JvmStatic
    fun getNpcStash(player: ServerPlayer, npcName: String): List<ItemStack> {
        return CollectionSavedData.get(player.serverLevel()).getStacks(player.uuid, npcName)
    }

    @JvmStatic
    fun setNpcStash(player: ServerPlayer, npcName: String, items: List<ItemStack>) {
        CollectionSavedData.get(player.serverLevel()).setStacks(player.uuid, npcName, items)
    }

    @JvmStatic
    fun addToNpcStash(player: ServerPlayer, npcName: String, items: List<ItemStack>) {
        CollectionSavedData.get(player.serverLevel()).addItemsSilent(player, npcName, items)
    }

    @JvmStatic
    fun clearNpcStash(player: ServerPlayer, npcName: String) {
        CollectionSavedData.get(player.serverLevel()).setStacks(player.uuid, npcName, emptyList())
    }

    @JvmStatic
    fun updateNpcDeliveryTime(level: ServerLevel, name: String, deliveryTime: Int) {
        KubeJSNpcSavedData.get(level).setNpcData(name, deliveryTime, null)
    }

    @JvmStatic
    fun updateNpcAutoFill(level: ServerLevel, name: String, autoFill: Boolean) {
        KubeJSNpcSavedData.get(level).setNpcData(name, null, autoFill)
    }

    @JvmStatic
    fun sendParcel(player: ServerPlayer, senderName: String, items: List<ItemStack>, isEnder: Boolean) {
        ItemStack.EMPTY

        items.forEach { stack ->
            RewardDistributionManager.distribute(player, stack, senderName, isEnder)
        }
    }

    @JvmStatic
    fun sendMail(player: ServerPlayer, senderName: String, item: ItemStack, isEnder: Boolean) {
        RewardDistributionManager.distribute(player, item, senderName, isEnder)
    }

    @JvmStatic
    fun createRedPacket(sender: String, blessing: String, item: ItemStack): ItemStack {
        val container = SimpleContainer(1)
        container.setItem(0, item.copy())
        return RedPacketUtils.getRedPacket(container, blessing, sender)
    }

    @JvmStatic
    fun createPostcard(
        sender: String,
        text: String,
        unlockTags: List<String>,
        style: String?,
        isEnder: Boolean
    ): ItemStack {
        val styleId =
            if (!style.isNullOrEmpty()) ResourceLocation.tryParse(style) else ResourceLocation.fromNamespaceAndPath(
                "contact",
                "default"
            )

        var postcardStack = PostcardItem.getPostcard(styleId, isEnder)

        if (text.isNotEmpty()) {
            val processedText = text.replace("\\n", "\n")
            postcardStack = PostcardItem.setText(postcardStack, processedText)
        }

        if (sender.isNotEmpty()) {
            try {
                val componentId = ResourceLocation.fromNamespaceAndPath("contact", "postcard_sender")
                val rawComponentType = BuiltInRegistries.DATA_COMPONENT_TYPE[componentId]

                @Suppress("UNCHECKED_CAST")
                val senderComponentType = rawComponentType as? DataComponentType<String>

                if (senderComponentType != null) {
                    postcardStack[senderComponentType] = sender
                }
            } catch (e: Exception) {
                LOGGER.error("Failed to set postcard sender component", e)
            }
        }

        if (unlockTags.isNotEmpty()) {
            val componentType = net.minecraft.core.component.DataComponents.CUSTOM_DATA
            val customData = postcardStack.getOrDefault(componentType, CustomData.EMPTY).copyTag()

            val tagList = ListTag()
            unlockTags.forEach { t -> tagList.add(StringTag.valueOf(t)) }
            customData.put("ContactQuestsUnlockTags", tagList)

            postcardStack[componentType] = CustomData.of(customData)
        }

        return postcardStack
    }

    @JvmStatic
    fun openQuest(player: ServerPlayer, questIdStr: String) {
        try {
            val id = try {
                java.lang.Long.parseUnsignedLong(questIdStr, 16)
            } catch (e: NumberFormatException) {
                questIdStr.toLong()
            }
            PacketDistributor.sendToPlayer(player, OpenQuestMessage(id))
        } catch (e: Exception) {
            LOGGER.error("Failed to open quest $questIdStr for ${player.name.string}", e)
        }
    }

    @JvmStatic
    fun forceQuest(team: TeamData, questIdStr: String): Boolean {
        val id = parseId(questIdStr) ?: return false
        return (team as ITeamDataExtension).`contactQuests$forceQuest`(id)
    }

    @JvmStatic
    fun unforceQuest(team: TeamData, questIdStr: String): Boolean {
        val id = parseId(questIdStr) ?: return false
        return (team as ITeamDataExtension).`contactQuests$unforceQuest`(id)
    }

    @JvmStatic
    fun blockQuest(team: TeamData, questIdStr: String): Boolean {
        val id = parseId(questIdStr) ?: return false
        return (team as ITeamDataExtension).`contactQuests$blockQuest`(id)
    }

    @JvmStatic
    fun unblockQuest(team: TeamData, questIdStr: String): Boolean {
        val id = parseId(questIdStr) ?: return false
        return (team as ITeamDataExtension).`contactQuests$unblockQuest`(id)
    }

    @JvmStatic
    fun resetQuestState(team: TeamData, questIdStr: String) {
        val id = parseId(questIdStr) ?: return
        val ext = team as ITeamDataExtension
        ext.`contactQuests$unforceQuest`(id)
        ext.`contactQuests$unblockQuest`(id)
    }

    private fun parseId(idStr: String): Long? {
        return try {
            java.lang.Long.parseUnsignedLong(idStr, 16)
        } catch (e: Exception) {
            try {
                idStr.toLong()
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun updateTaskTarget(task: Task, newTarget: String) {
        if (task !is ContactTask) return

        val oldTarget = task.targetAddressee
        if (oldTarget == newTarget) return

        val map = when (task) {
            is ParcelTask -> DataManager.parcelReceiver
            is RedPacketTask -> DataManager.redPacketReceiver
            is PostcardTask -> DataManager.postcardReceiver
            else -> null
        }

        if (map != null) {
            map[oldTarget]?.remove(task.id)
            if (map[oldTarget]?.isEmpty() == true) map.remove(oldTarget)
            map.computeIfAbsent(newTarget) { mutableSetOf() }.add(task.id)
        }

        task.targetAddressee = newTarget
        LOGGER.info("ContactQuests: Updated task ${task.id} target from '$oldTarget' to '$newTarget'")
    }

    private fun resolveId(id: Any): Long? =
        if (id is Number) id.toLong() else parseId(id.toString())

    private class ChangeTracker {
        var changed = false

        fun <T> update(current: T, new: T?, setter: (T) -> Unit) {
            if (new != null && current != new) {
                setter(new)
                changed = true
            }
        }

        fun updateItem(current: ItemStack, new: ItemStack?, setter: (ItemStack) -> Unit) {
            if (new != null && !ItemStack.matches(current, new)) {
                setter(new)
                changed = true
            }
        }

        fun setTarget(task: ContactTask, newTarget: String?) {
            if (newTarget != null && task.targetAddressee != newTarget) {
                updateTaskTarget(task, newTarget)
                changed = true
            }
        }
    }

    private fun <T : QuestObjectBase> executeEdit(
        id: Any,
        saveToConfig: Boolean,
        lookup: (Long) -> T?,
        applier: (T, ChangeTracker) -> Unit
    ): Boolean {
        val longId = resolveId(id) ?: return false
        val obj = lookup(longId) ?: return false
        val tracker = ChangeTracker()

        applier(obj, tracker)

        if (tracker.changed) {
            syncObject(obj, saveToConfig)
            LOGGER.info("ContactQuests: Edited ${obj.javaClass.simpleName} '$longId' (save=$saveToConfig)")
        }
        return tracker.changed
    }

    private fun saveToNbt(level: ServerLevel, id: Any, data: Map<String, Any?>) {
        val longId = resolveId(id) ?: return
        val savedData = QuestOverrideSavedData.get(level)
        val registry = level.registryAccess()

        data.forEach { (key, value) ->
            if (value != null) {
                when (value) {
                    is ItemStack -> savedData.setOverride(longId, key, value, registry)
                    is Number -> savedData.setOverride(
                        longId,
                        key,
                        value.toLong(),
                        registry
                    )
                    is String -> savedData.setOverride(longId, key, value, registry)
                }
            }
        }
    }

    @JvmStatic
    fun editParcelTask(id: Any, item: ItemStack?, count: Number?, target: String?) =
        editParcelTaskInternal(id, item, count, target, true)

    @JvmStatic
    fun editParcelTaskSaved(
        level: ServerLevel,
        id: Any,
        item: ItemStack? = null,
        count: Number? = null,
        target: String? = null
    ): Boolean {
        if (editParcelTaskInternal(id, item, count, target, false)) {
            saveToNbt(level, id, mapOf("item" to item, "count" to count?.toLong(), "target" to target))
            return true
        }
        return false
    }

    private fun editParcelTaskInternal(id: Any, item: ItemStack?, count: Number?, target: String?, save: Boolean) =
        executeEdit(id, save, { DataManager.parcelTasks[it] }) { task, tracker ->
            tracker.updateItem(task.itemStack, item) { task.itemStack = it }
            tracker.update(task.count, count?.toLong()) { task.count = it }
            tracker.setTarget(task, target)
        }

    @JvmStatic
    fun editRedPacketTask(id: Any, item: ItemStack?, count: Number?, blessing: String?, target: String?) =
        editRedPacketTaskInternal(id, item, count, blessing, target, true)

    @JvmStatic
    fun editRedPacketTaskSaved(
        level: ServerLevel,
        id: Any,
        item: ItemStack? = null,
        count: Number? = null,
        blessing: String? = null,
        target: String? = null
    ): Boolean {
        if (editRedPacketTaskInternal(id, item, count, blessing, target, false)) {
            saveToNbt(
                level,
                id,
                mapOf("item" to item, "count" to count?.toLong(), "blessing" to blessing, "target" to target)
            )
            return true
        }
        return false
    }

    private fun editRedPacketTaskInternal(
        id: Any,
        item: ItemStack?,
        count: Number?,
        blessing: String?,
        target: String?,
        save: Boolean
    ) =
        executeEdit(id, save, { DataManager.redPacketTasks[it] }) { task, tracker ->
            tracker.updateItem(task.itemStack, item) { task.itemStack = it }
            tracker.update(task.count, count?.toLong()) { task.count = it }
            tracker.update(task.blessing, blessing) { task.blessing = it }
            tracker.setTarget(task, target)
        }

    @JvmStatic
    fun editPostcardTask(id: Any, style: String?, text: String?, target: String?) =
        editPostcardTaskInternal(id, style, text, target, true)

    @JvmStatic
    fun editPostcardTaskSaved(
        level: ServerLevel,
        id: Any,
        style: String? = null,
        text: String? = null,
        target: String? = null
    ): Boolean {
        if (editPostcardTaskInternal(id, style, text, target, false)) {
            saveToNbt(level, id, mapOf("style" to style, "text" to text, "target" to target))
            return true
        }
        return false
    }

    private fun editPostcardTaskInternal(id: Any, style: String?, text: String?, target: String?, save: Boolean) =
        executeEdit(id, save, { DataManager.postcardTasks[it] }) { task, tracker ->
            tracker.update(task.postcardStyle, style) { task.postcardStyle = it }
            tracker.update(task.postcardText, text) { task.postcardText = it }
            tracker.setTarget(task, target)
        }

    @JvmStatic
    fun editParcelReward(id: Any, item: ItemStack?, count: Number?, randomBonus: Number?, target: String?) =
        editParcelRewardInternal(id, item, count, randomBonus, target, true)

    @JvmStatic
    fun editParcelRewardSaved(
        level: ServerLevel,
        id: Any,
        item: ItemStack? = null,
        count: Number? = null,
        randomBonus: Number? = null,
        target: String? = null
    ): Boolean {
        if (editParcelRewardInternal(id, item, count, randomBonus, target, false)) {
            saveToNbt(
                level,
                id,
                mapOf(
                    "item" to item,
                    "count" to count?.toInt(),
                    "randomBonus" to randomBonus?.toInt(),
                    "target" to target
                )
            )
            return true
        }
        return false
    }

    private fun editParcelRewardInternal(
        id: Any,
        item: ItemStack?,
        count: Number?,
        randomBonus: Number?,
        target: String?,
        save: Boolean
    ) =
        executeEdit(id, save, { getReward(it) as? ParcelReward }) { reward, tracker ->
            tracker.updateItem(reward.item, item) { reward.item = it }
            tracker.update(reward.count, count?.toInt()) { reward.count = it }
            tracker.update(reward.randomBonus, randomBonus?.toInt()) { reward.randomBonus = it }
            tracker.update(reward.targetAddressee, target) { reward.targetAddressee = it }
        }

    @JvmStatic
    fun editRedPacketReward(
        id: Any,
        item: ItemStack?,
        count: Number?,
        blessing: String?,
        randomBonus: Number?,
        target: String?
    ) =
        editRedPacketRewardInternal(id, item, count, blessing, randomBonus, target, true)

    @JvmStatic
    fun editRedPacketRewardSaved(
        level: ServerLevel,
        id: Any,
        item: ItemStack? = null,
        count: Number? = null,
        blessing: String? = null,
        randomBonus: Number? = null,
        target: String? = null
    ): Boolean {
        if (editRedPacketRewardInternal(id, item, count, blessing, randomBonus, target, false)) {
            saveToNbt(
                level,
                id,
                mapOf(
                    "item" to item,
                    "count" to count?.toInt(),
                    "blessing" to blessing,
                    "randomBonus" to randomBonus?.toInt(),
                    "target" to target
                )
            )
            return true
        }
        return false
    }

    private fun editRedPacketRewardInternal(
        id: Any,
        item: ItemStack?,
        count: Number?,
        blessing: String?,
        randomBonus: Number?,
        target: String?,
        save: Boolean
    ) =
        executeEdit(
            id,
            save,
            { getReward(it) as? com.creeperyang.contactquests.quest.reward.RedPacketReward }) { reward, tracker ->
            tracker.updateItem(reward.item, item) { reward.item = it }
            tracker.update(reward.count, count?.toInt()) { reward.count = it }
            tracker.update(reward.blessing, blessing) { reward.blessing = it }
            tracker.update(reward.randomBonus, randomBonus?.toInt()) { reward.randomBonus = it }
            tracker.update(reward.targetAddressee, target) { reward.targetAddressee = it }
        }

    @JvmStatic
    fun editPostcardReward(id: Any, style: String?, text: String?, sender: String?) =
        editPostcardRewardInternal(id, style, text, sender, true)

    @JvmStatic
    fun editPostcardRewardSaved(
        level: ServerLevel,
        id: Any,
        style: String? = null,
        text: String? = null,
        sender: String? = null
    ): Boolean {
        if (editPostcardRewardInternal(id, style, text, sender, false)) {
            saveToNbt(level, id, mapOf("style" to style, "text" to text, "target" to sender))
            return true
        }
        return false
    }

    private fun editPostcardRewardInternal(id: Any, style: String?, text: String?, sender: String?, save: Boolean) =
        executeEdit(id, save, { getReward(it) as? PostcardReward }) { reward, tracker ->
            tracker.update(reward.postcardStyle, style) { reward.postcardStyle = it }
            tracker.update(reward.postcardText, text) { reward.postcardText = it }
            tracker.update(reward.targetAddressee, sender) { reward.targetAddressee = it }
        }

    private fun editQuestTextInternal(id: Any, content: Any?, locale: String, type: Int, saveGlobal: Boolean): Boolean {
        val longId = resolveId(id) ?: return false
        val quest = getQuest(longId) ?: return false
        val key = getTranslationKey(type)

        val changed = if (saveGlobal) {
            applyGlobalQuestEdit(quest, longId, locale, key, type, content)
        } else {
            applySavedQuestEdit(quest, longId, locale, type, content)
        }

        if (changed) {
            PacketDistributor.sendToAllPlayers(
                SyncQuestTextMessage(longId, locale, type, content)
            )
        }

        return changed
    }

    private fun applyGlobalQuestEdit(
        quest: Quest,
        longId: Long,
        locale: String,
        key: TranslationKey,
        type: Int,
        content: Any?
    ): Boolean {
        if (type == 2) {
            val list = if (content == null) {
                ArrayList<String>()
            } else {
                (content as? List<*>)?.mapTo(ArrayList()) { it.toString() } ?: ArrayList()
            }
            quest.questFile.translationManager.addTranslation(quest, locale, key, list)
        } else {
            val str = content?.toString() ?: ""
            quest.questFile.translationManager.addTranslation(quest, locale, key, str)
        }

        ServerQuestFile.INSTANCE.markDirty()
        ServerQuestFile.INSTANCE.saveNow()
        quest.clearCachedData()

        LOGGER.info("ContactQuests: Globally edited text for quest $longId (Silent Save)")
        return true
    }

    private fun applySavedQuestEdit(
        quest: Quest,
        longId: Long,
        locale: String,
        type: Int,
        content: Any?
    ): Boolean {
        val ext = quest as? IQuestExtension ?: return false

        when (type) {
            0 -> ext.`contactQuests$setTitleOverride`(locale, content as? String)
            1 -> ext.`contactQuests$setSubtitleOverride`(locale, content as? String)
            2 -> ext.`contactQuests$setDescriptionOverride`(locale, content as? ArrayList<String>)
        }

        quest.clearCachedData()
        LOGGER.debug("ContactQuests: Updated in-memory text override for quest $longId")
        return true
    }

    private fun getTranslationKey(type: Int): TranslationKey {
        return when (type) {
            0 -> TranslationKey.TITLE
            1 -> TranslationKey.QUEST_SUBTITLE
            else -> TranslationKey.QUEST_DESC
        }
    }

    @JvmStatic
    @JvmOverloads
    fun resetQuestTextSaved(level: ServerLevel, id: Any, locale: String = "zh_cn"): Boolean {
        var result = true
        if (!editQuestTextInternal(id, null, locale, 0, false)) result = false
        if (!editQuestTextInternal(id, null, locale, 1, false)) result = false
        if (!editQuestTextInternal(id, null, locale, 2, false)) result = false

        saveTextOverride(level, id, "title", null, locale)
        saveTextOverride(level, id, "subtitle", null, locale)
        saveTextOverride(level, id, "description", null, locale)

        return result
    }

    @JvmStatic
    @JvmOverloads
    fun editQuestDescriptionSaved(level: ServerLevel, id: Any, desc: List<String>?, locale: String = "zh_cn"): Boolean {
        if (desc != null && editQuestTextInternal(id, desc, locale, 2, false)) {
            saveTextOverride(level, id, "description", desc, locale)
            return true
        }
        return false
    }

    @JvmStatic
    @JvmOverloads
    fun editQuestDescription(id: Any, desc: List<String>? = null, locale: String = "zh_cn"): Boolean {
        if (desc != null) {
            return editQuestTextInternal(id, desc, locale, 2, true)
        }
        return false
    }

    @JvmStatic
    @JvmOverloads
    fun editQuestTitleSaved(level: ServerLevel, id: Any, title: String? = null, locale: String = "zh_cn"): Boolean {
        if (title != null && editQuestTextInternal(id, title, locale, 0, false)) {
            saveTextOverride(level, id, "title", title, locale)
            return true
        }
        return false
    }

    @JvmStatic
    @JvmOverloads
    fun editQuestTitle(id: Any, title: String? = null, locale: String = "zh_cn"): Boolean {
        if (title != null) {
            return editQuestTextInternal(id, title, locale, 0, true)
        }
        return false
    }

    @JvmStatic
    @JvmOverloads
    fun editQuestSubtitleSaved(
        level: ServerLevel,
        id: Any,
        subtitle: String? = null,
        locale: String = "zh_cn"
    ): Boolean {
        if (subtitle != null && editQuestTextInternal(id, subtitle, locale, 1, false)) {
            saveTextOverride(level, id, "subtitle", subtitle, locale)
            return true
        }
        return false
    }

    @JvmStatic
    @JvmOverloads
    fun editQuestSubtitle(id: Any, subtitle: String? = null, locale: String = "zh_cn"): Boolean {
        if (subtitle != null) {
            return editQuestTextInternal(id, subtitle, locale, 1, true)
        }
        return false
    }

    private fun saveTextOverride(level: ServerLevel, id: Any, key: String, content: Any?, locale: String) {
        val savedData = QuestOverrideSavedData.get(level)
        val questId = resolveId(id) ?: return
        val currentOverride = savedData.getOverride(questId) ?: CompoundTag()

        val currentMap = if (currentOverride.contains(key, Tag.TAG_COMPOUND.toInt())) {
            currentOverride.getCompound(key)
        } else {
            CompoundTag()
        }

        if (content != null) {
            if (content is String) currentMap.putString(locale, content)
            else if (content is List<*>) {
                val listTag = ListTag()
                content.forEach { listTag.add(StringTag.valueOf(it.toString())) }
                currentMap.put(locale, listTag)
            }
        } else {
            currentMap.remove(locale)
        }

        val mapToSave = HashMap<String, Any>()
        currentMap.allKeys.forEach { k ->
            if (currentMap.contains(k, Tag.TAG_STRING.toInt())) mapToSave[k] = currentMap.getString(k)
            else if (currentMap.contains(k, Tag.TAG_LIST.toInt())) {
                mapToSave[k] = currentMap.getList(k, Tag.TAG_STRING.toInt()).map { it.asString }
            }
        }
        savedData.setOverride(questId, key, mapToSave, level.registryAccess())
    }
    @JvmStatic
    fun syncAllOverridesToPlayer(player: ServerPlayer) {
        val savedData = QuestOverrideSavedData.get(player.serverLevel())
        savedData.overrides.forEach { (id, tag) ->
            if (tag.contains("title", Tag.TAG_COMPOUND.toInt())) {
                tag.getCompound("title").allKeys.forEach { locale ->
                    PacketDistributor.sendToPlayer(
                        player,
                        SyncQuestTextMessage(id, locale, 0, tag.getCompound("title").getString(locale))
                    )
                }
            }
            if (tag.contains("subtitle", Tag.TAG_COMPOUND.toInt())) {
                tag.getCompound("subtitle").allKeys.forEach { locale ->
                    PacketDistributor.sendToPlayer(
                        player,
                        SyncQuestTextMessage(id, locale, 1, tag.getCompound("subtitle").getString(locale))
                    )
                }
            }
            if (tag.contains("description", Tag.TAG_COMPOUND.toInt())) {
                val map = tag.getCompound("description")
                map.allKeys.forEach { locale ->
                    val list = map.getList(locale, Tag.TAG_STRING.toInt()).map { it.asString }
                    PacketDistributor.sendToPlayer(player, SyncQuestTextMessage(id, locale, 2, list))
                }
            }
        }
    }

    @JvmStatic
    fun refreshQuests() {
        val server = ServerQuestFile.INSTANCE.server
        if (server != null) {
            val count = updateAllTeamsTaskCache(server)
            LOGGER.info("ContactQuests: Calculated postcard texts for $count teams.")
        }

        ServerQuestFile.INSTANCE.refreshGui()
        LOGGER.info("ContactQuests: Refreshed Quest GUI for all players.")
    }

    fun updatePlayerTaskCache(player: ServerPlayer) {
        try {
            val team = FTBTeamsAPI.api().manager.getTeamForPlayerID(player.uuid).orElse(null) ?: return
            val teamData = ServerQuestFile.INSTANCE.getOrCreateTeamData(team)
            val ext = teamData as ITeamDataExtension

            val postcardTasks = DataManager.postcardTasks.values
            for (task in postcardTasks) {
                val originalText = task.postcardText
                val resolvedText = PostcardTask.PostcardPlaceholderSupport.replace(originalText, player, teamData)

                val cached = ext.`contactQuests$getPostcardText`(task.id)
                if (cached != resolvedText) {
                    ext.`contactQuests$setPostcardText`(task.id, resolvedText)
                    ContactQuests.LOGGER.debug("ContactQuests: Updated postcard cache for ${player.name.string} (Task ${task.id})")
                }
            }

            val redPacketTasks = DataManager.redPacketTasks.values
            for (task in redPacketTasks) {
                val originalText = task.blessing
                val resolvedText = RedPacketTask.RedPacketPlaceholderSupport.replace(originalText, player, teamData)

                val cached = ext.`contactQuests$getRedPacketBlessing`(task.id)
                if (cached != resolvedText) {
                    ext.`contactQuests$setRedPacketBlessing`(task.id, resolvedText)
                    ContactQuests.LOGGER.debug("ContactQuests: Updated red packet cache for ${player.name.string} (Task ${task.id})")
                }
            }

        } catch (e: Exception) {
            ContactQuests.LOGGER.error("Failed to update task cache for player ${player.name.string}", e)
        }
    }

    private fun updateAllTeamsTaskCache(server: MinecraftServer): Int {
        var count = 0
        server.playerList.players.forEach { player ->
            updatePlayerTaskCache(player)
            count++
        }
        return count
    }

    class RegisterReplacersEvent : KubeEvent {
        fun registerPostcardTask(key: String, callback: BiFunction<Player, TeamData, String>) {
            PostcardTask.PostcardPlaceholderSupport.register(key) { p, t -> callback.apply(p, t) }
        }

        fun registerRedPacketReward(callback: BiFunction<String, ServerPlayer, String>) {
            com.creeperyang.contactquests.quest.reward.RedPacketReward.registerReplacer { text, player ->
                callback.apply(text, player)
            }
        }

        fun registerPostcardReward(callback: BiFunction<String, ServerPlayer, String>) {
            PostcardReward.registerReplacer { text, player -> callback.apply(text, player) }
        }

        fun registerNpcReply(callback: BiFunction<String, CollectionSavedData.ReplacerContext, String?>) {
            CollectionSavedData.registerReplacer { text, ctx -> callback.apply(text, ctx) }
        }

        fun registerRedPacketTask(key: String, callback: BiFunction<Player, TeamData, String>) {
            RedPacketTask.RedPacketPlaceholderSupport.register(key) { p, t -> callback.apply(p, t) }
        }
    }
}