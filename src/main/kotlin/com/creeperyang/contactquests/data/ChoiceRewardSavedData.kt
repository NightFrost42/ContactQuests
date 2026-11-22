package com.creeperyang.contactquests.data

import com.creeperyang.contactquests.config.ChoiceTemplateConfig
import com.creeperyang.contactquests.quest.reward.ParcelChoiceReward
import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.common.component.ContactDataComponents
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.saveddata.SavedData
import java.security.MessageDigest
import java.util.UUID

class ChoiceRewardSavedData : SavedData() {
    data class Request(
        val token: UUID,
        val playerId: UUID,
        val teamId: UUID,
        val rewardId: Long,
        val option: Int,
        val completedAt: Long,
        val fingerprint: String,
        val recipient: String,
        val templateId: String,
        val text: String,
        var accepted: Boolean = false,
        var ticks: Int = 0,
        var delivered: Boolean = false,
        var submittedCard: ItemStack = ItemStack.EMPTY,
        var granted: Boolean = false,
        var cancelled: Boolean = false
    )
    private val requests = linkedMapOf<String, Request>()

    fun request(player: ServerPlayer, reward: ParcelChoiceReward, option: Int): Request {
        check(player.server.isSameThread) { "Choice requests must run on the server thread" }
        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElseThrow()
        val data = reward.questFile.getNullableTeamData(team.id) ?: error("Missing team progress")
        require(canClaim(data, player, reward)) { "This reward is not ready to claim" }
        val selected = reward.table?.weightedRewards?.getOrNull(option)?.reward ?: error("Invalid reward option")
        require(
            selected is dev.ftb.mods.ftbquests.quest.reward.ItemReward ||
                selected is com.creeperyang.contactquests.quest.reward.ParcelReward ||
                selected is com.creeperyang.contactquests.quest.reward.RedPacketReward ||
                selected is com.creeperyang.contactquests.quest.reward.PostcardReward
        ) {
            "Choice entries must be deliverable item or Contact mail rewards"
        }
        val completion = data.getCompletedTime(reward.quest.id).orElseThrow().time
        val fingerprint = fingerprint(reward, player.serverLevel().registryAccess())
        val key = key(reward, team.id, player.uuid)
        val previous = requests[key]
        if (
            previous != null && !previous.delivered && !previous.cancelled && previous.completedAt == completion &&
            previous.fingerprint == fingerprint && previous.recipient == reward.claimRecipient
        ) {
            require(!previous.accepted || previous.option == option) { "A postcard is already in transit" }
            if (previous.option == option && previous.playerId == player.uuid) return previous
            require(!previous.accepted) { "The team already submitted this choice" }
        }
        if (previous?.accepted == true && !previous.delivered && !previous.cancelled)
            cancel(player.serverLevel(), previous, player)
        val token = UUID.randomUUID()
        val template = ChoiceTemplateConfig.selectForLocale(
            reward.templateGroup,
            player.clientInformation().language(),
            player.random
        )
        val label = reward.optionLabel(option, reward.hideContents).string
        val text = ChoiceTemplateConfig.fill(
            template,
            mapOf(
                "recipient" to reward.targetAddressee,
                "player" to player.scoreboardName,
                "quest" to reward.quest.title.string,
                "task_goal" to reward.quest.tasks.joinToString("\u003B ") { it.title.string },
                "reward" to label
            )
        )
        return Request(
            token, player.uuid, team.id, reward.id, option, completion, fingerprint,
            reward.claimRecipient, template.id, text
        ).also {
            requests[key] = it
            setDirty()
        }
    }

    /** 将背包中真实的明信片绑定到服务端选定的奖励条目；卡面正文仍可由玩家自由编辑。 */
    fun prepareCard(player: ServerPlayer, rewardId: Long, slot: Int): Request {
        check(player.server.isSameThread) { "Choice postcards must be prepared on the server thread" }
        require(slot in player.inventory.items.indices) { "Invalid postcard slot" }
        val reward = ServerQuestFile.INSTANCE?.getReward(rewardId) as? ParcelChoiceReward ?: error("Reward is absent")
        val teamId = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElseThrow().id
        val request = requests[key(reward, teamId, player.uuid)] ?: error("Select a reward first")
        val data = reward.questFile.getNullableTeamData(teamId) ?: error("Missing progress")
        require(
            !request.accepted && !request.delivered && request.playerId == player.uuid &&
                valid(request, reward, data, player)
        ) { "Selection is no longer ready" }
        val card = player.inventory.getItem(slot)
        require(isEditablePostcard(card)) { "An editable Contact postcard is required" }
        bindCard(card, request)
        player.inventory.setChanged()
        player.inventoryMenu.broadcastFullState()
        return request
    }

    /** 仅凭隐藏标签、玩家归属与源请求状态校验自选奖励授权，绝不信任玩家填写的明信片正文。 */
    fun receive(player: ServerPlayer, recipient: String, stacks: List<ItemStack>, delay: Int): List<ItemStack> {
        val teamId = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null)?.id ?: return stacks
        return stacks.mapNotNull { original ->
            val stack = original.copy()
            if (stack.item is PostcardItem) {
                val token = cardToken(stack)
                val sender = stack[ContactDataComponents.POSTCARD_SENDER.get()]
                val request = requests.values.firstOrNull {
                    !it.accepted && !it.delivered && !it.cancelled && it.playerId == player.uuid &&
                        it.teamId == teamId && it.recipient.equals(recipient, true) && it.token == token
                }
                val reward = request?.let { ServerQuestFile.INSTANCE?.getReward(it.rewardId) as? ParcelChoiceReward }
                val data = request?.let { ServerQuestFile.INSTANCE?.getNullableTeamData(it.teamId) }
                if (
                    request != null && reward != null && data != null &&
                    (sender.isNullOrBlank() || sender == player.scoreboardName) && valid(request, reward, data, player)
                ) {
                    request.accepted = true
                    request.ticks = delay.coerceAtLeast(0)
                    request.submittedCard = stack.copyWithCount(1)
                    stack.shrink(1)
                    setDirty()
                    if (request.ticks == 0) finish(player, request, reward, data)
                }
            }
            stack.takeUnless { it.isEmpty }
        }
    }

    fun tick(level: ServerLevel) {
        requests.values.toList().filter { it.accepted && !it.delivered && !it.cancelled }.forEach { request ->
            if (request.ticks > 0) {
                request.ticks--
                setDirty()
            }
            if (request.ticks > 0) return@forEach
            val player = level.server.playerList.getPlayer(request.playerId)
            val reward = ServerQuestFile.INSTANCE?.getReward(request.rewardId) as? ParcelChoiceReward
            val data = ServerQuestFile.INSTANCE?.getNullableTeamData(request.teamId)
            if (reward == null || data == null || !sourceValid(request, reward, data, level.registryAccess())) {
                cancel(level, request, player)
                return@forEach
            }
            // 仅按原请求绑定时的玩家与队伍身份重试，在途请求绝不会随玩家更换队伍而迁移。
            if (
                player == null || FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null)?.id != request.teamId
            ) return@forEach
            if (valid(request, reward, data, player)) finish(player, request, reward, data)
        }
    }

    private fun finish(player: ServerPlayer, request: Request, reward: ParcelChoiceReward, team: TeamData) {
        if (!valid(request, reward, team, player)) return
        if (active != null) return
        active = request
        authorizationUsed = false
        try {
            team.claimReward(player, reward, false)
        } finally {
            active = null
            authorizationUsed = false
        }
        // 原生领奖前置事件可能触发重入，且一次性授权仍要求以原生已领取标记为准，不能凭异常状态重放。
        if (
            request.granted && !team.isRewardClaimed(player.uuid, reward) &&
            team.getCompletedTime(reward.quest.id).map { it.time }.orElse(-1L) == request.completedAt
        ) {
            team.markRewardAsClaimed(player.uuid, reward, System.currentTimeMillis())
        }
        if (request.granted || team.isRewardClaimed(player.uuid, reward)) {
            request.delivered = true
            RewardDistributionManager.flush(player.serverLevel())
            setDirty()
            player.sendSystemMessage(Component.translatable("contactquests.choice.accepted"))
        }
    }

    private fun valid(request: Request, reward: ParcelChoiceReward, data: TeamData, player: ServerPlayer): Boolean =
        canClaim(data, player, reward) && !request.granted && !request.cancelled &&
            sourceValid(request, reward, data, player.serverLevel().registryAccess())

    private fun sourceValid(
        request: Request,
        reward: ParcelChoiceReward,
        data: TeamData,
        provider: HolderLookup.Provider
    ) =
        data.getCompletedTime(reward.quest.id).map { it.time }.orElse(-1L) == request.completedAt &&
            fingerprint(reward, provider) == request.fingerprint && request.recipient == reward.claimRecipient

    private fun cancel(level: ServerLevel, request: Request, player: ServerPlayer?) {
        if (request.cancelled || request.delivered || request.granted) return
        if (!request.submittedCard.isEmpty) {
            RewardDistributionManager.distributeTo(
                request.playerId, request.teamId, request.submittedCard, request.recipient, false, true
            )
            RewardDistributionManager.flush(level)
        }
        request.cancelled = true
        setDirty()
        player?.sendSystemMessage(Component.translatable("contactquests.choice.cancelled"))
    }

    override fun save(tag: CompoundTag, provider: HolderLookup.Provider): CompoundTag {
        tag.put("Requests", ListTag().apply {
            requests.forEach { (key, r) ->
                add(CompoundTag().apply {
                    putString("key", key)
                    putUUID("token", r.token)
                    putUUID("player", r.playerId)
                    putUUID("team", r.teamId)
                    putLong("reward", r.rewardId)
                    putInt("option", r.option)
                    putLong("completed", r.completedAt)
                    putString("fingerprint", r.fingerprint)
                    putString("recipient", r.recipient)
                    putString("template", r.templateId)
                    putString("text", r.text)
                    putBoolean("accepted", r.accepted)
                    putInt("ticks", r.ticks)
                    putBoolean("delivered", r.delivered)
                    putBoolean("granted", r.granted)
                    putBoolean("cancelled", r.cancelled)
                    if (!r.submittedCard.isEmpty) put("card", r.submittedCard.save(provider))
                })
            }
        })
        return tag
    }

    companion object {
        private const val CARD_TAG = "ContactQuestsChoice"
        // 只要明信片带有 postcard_sender 组件（哪怕为空字符串），Contact 都会将其视作只读已寄明信片打开。
        @JvmStatic
        fun isEditablePostcard(card: ItemStack): Boolean =
            !card.isEmpty && card.item is PostcardItem && !card.has(ContactDataComponents.POSTCARD_SENDER.get())
        @JvmStatic
        fun hasChoiceMarker(card: ItemStack): Boolean =
            card.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains(CARD_TAG)
        @JvmStatic
        fun cardToken(card: ItemStack): UUID? = runCatching {
            val marker = card.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(CARD_TAG)
            if (marker.hasUUID("request")) marker.getUUID("request") else null
        }.getOrNull()
        @JvmStatic
        fun bindCard(card: ItemStack, request: Request) {
            require(card.item is PostcardItem) { "A real Contact postcard is required" }
            val data = card.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
            data.put(CARD_TAG, CompoundTag().apply { putUUID("request", request.token) })
            card[DataComponents.CUSTOM_DATA] = CustomData.of(data)
        }
        private var active: Request? = null
        private var authorizationUsed = false
        fun get(level: ServerLevel): ChoiceRewardSavedData = level.server.overworld().dataStorage.computeIfAbsent(
            Factory(::ChoiceRewardSavedData, ::load), "contactquests_choice_requests"
        )
        @JvmStatic
        fun authorized(player: ServerPlayer, reward: ParcelChoiceReward): Request? = active?.takeIf {
            !authorizationUsed && it.playerId == player.uuid && it.rewardId == reward.id &&
                it.accepted && !it.delivered && !it.granted && !it.cancelled &&
                FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null)?.id == it.teamId &&
                reward.questFile.getNullableTeamData(it.teamId)?.let { data ->
                    canClaim(data, player, reward) &&
                        data.getCompletedTime(reward.quest.id).map { time -> time.time }.orElse(-1L) ==
                            it.completedAt &&
                        fingerprint(reward, player.serverLevel().registryAccess()) == it.fingerprint
                } == true
        }
        @JvmStatic
        fun consumeAuthorization(player: ServerPlayer, reward: ParcelChoiceReward): Request? =
            authorized(player, reward)?.also { authorizationUsed = true }
        private fun canClaim(data: TeamData, player: ServerPlayer, reward: ParcelChoiceReward): Boolean =
            data.isCompleted(reward.quest) && data.isRewardReadyToClaim(player.uuid, reward)
        private fun key(reward: ParcelChoiceReward, team: UUID, player: UUID) =
            "$team:${if (reward.isTeamReward) "team" else player}:${reward.codeString}"
        private fun fingerprint(reward: ParcelChoiceReward, provider: HolderLookup.Provider): String {
            val data = CompoundTag().also { reward.writeData(it, provider) }
            reward.table?.let { data.put("resolved_table", CompoundTag().also { t -> it.writeData(t, provider) }) }
            return MessageDigest.getInstance("SHA-256")
                .digest(data.toString().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
        fun load(
            tag: CompoundTag,
            provider: HolderLookup.Provider
        ): ChoiceRewardSavedData = ChoiceRewardSavedData().apply {
            tag.getList("Requests", Tag.TAG_COMPOUND.toInt()).forEach { raw ->
                val r = raw as CompoundTag
                runCatching {
                    requests[r.getString("key")] = Request(
                        r.getUUID("token"),
                        r.getUUID("player"),
                        r.getUUID("team"),
                        r.getLong("reward"),
                        r.getInt("option"),
                        r.getLong("completed"),
                        r.getString("fingerprint"),
                        r.getString("recipient"),
                        r.getString("template"),
                        r.getString("text"),
                        r.getBoolean("accepted"),
                        r.getInt("ticks").coerceAtLeast(0),
                        r.getBoolean("delivered"),
                        if (r.contains("card"))
                            ItemStack.parseOptional(provider, r.getCompound("card")) else ItemStack.EMPTY,
                        r.getBoolean("granted"),
                        r.getBoolean("cancelled")
                    )
                }
            }
        }
    }
}
