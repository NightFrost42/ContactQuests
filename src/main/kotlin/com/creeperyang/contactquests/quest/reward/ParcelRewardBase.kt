package com.creeperyang.contactquests.quest.reward

import com.creeperyang.contactquests.config.TagConfig
import com.creeperyang.contactquests.data.CollectionSavedData
import com.creeperyang.contactquests.data.RewardDistributionManager
import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.reward.Reward
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

abstract class ParcelRewardBase(id: Long, quest: Quest) : Reward(id, quest) {
    var targetAddressee: String = "QuestNPC"
    var isEnder: Boolean = false

    var unlockTags: MutableList<String> = ArrayList()

    var enableTeamMultiplier: Boolean = false
    var teamRewardMultiplier: Float = 1.0f

    protected fun distributeItem(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) return

        if (unlockTags.isNotEmpty()) {
            val componentType = net.minecraft.core.component.DataComponents.CUSTOM_DATA

            val customData =
                stack.getOrDefault(componentType, net.minecraft.world.item.component.CustomData.EMPTY).copyTag()

            val tagList = ListTag()
            unlockTags.forEach { t -> tagList.add(StringTag.valueOf(t)) }
            customData.put("ContactQuestsUnlockTags", tagList)

            stack[componentType] = net.minecraft.world.item.component.CustomData.of(customData)
        }

        RewardDistributionManager.distribute(player, stack, targetAddressee, isEnder)
        CollectionSavedData.get(player.serverLevel()).returnReward(player, targetAddressee)
    }

    fun calculateMultipliedCount(originalCount: Int, player: ServerPlayer): Int {
        if (!enableTeamMultiplier) return originalCount

        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null) ?: return originalCount

        return (originalCount * team.members.size * teamRewardMultiplier).toInt()
    }

    override fun writeData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.writeData(nbt, provider)
        nbt.putString("target_addressee", targetAddressee)
        nbt.putBoolean("is_ender", isEnder)

        nbt.putBoolean("is_team_reward", enableTeamMultiplier)
        nbt.putFloat("team_reward_multiplier", teamRewardMultiplier)

        if (unlockTags.isNotEmpty()) {
            val list = ListTag()
            unlockTags.forEach { list.add(StringTag.valueOf(it)) }
            nbt.put("unlock_tags", list)
        }
    }

    override fun readData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.readData(nbt, provider)
        if (nbt.contains("target_addressee")) targetAddressee = nbt.getString("target_addressee")
        isEnder = nbt.getBoolean("is_ender")

        enableTeamMultiplier = nbt.getBoolean("is_team_reward")
        teamRewardMultiplier = if (nbt.contains("team_reward_multiplier")) {
            nbt.getFloat("team_reward_multiplier")
        } else {
            1.0f
        }

        unlockTags.clear()
        if (nbt.contains("unlock_tags")) {
            val list = nbt.getList("unlock_tags", 8)
            list.forEach { unlockTags.add(it.asString) }
        }
    }

    override fun writeNetData(buffer: RegistryFriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeUtf(targetAddressee)
        buffer.writeBoolean(isEnder)

        buffer.writeBoolean(enableTeamMultiplier)
        buffer.writeFloat(teamRewardMultiplier)

        buffer.writeVarInt(unlockTags.size)
        unlockTags.forEach { buffer.writeUtf(it) }
    }

    override fun readNetData(buffer: RegistryFriendlyByteBuf) {
        super.readNetData(buffer)
        targetAddressee = buffer.readUtf()
        isEnder = buffer.readBoolean()

        enableTeamMultiplier = buffer.readBoolean()
        teamRewardMultiplier = buffer.readFloat()

        val size = buffer.readVarInt()
        unlockTags.clear()
        repeat(size) { unlockTags.add(buffer.readUtf()) }
    }

    @OnlyIn(Dist.CLIENT)
    override fun fillConfigGroup(config: ConfigGroup) {
        super.fillConfigGroup(config)
        config.addString("target_addressee", targetAddressee, { v -> targetAddressee = v }, "QuestNPC")
            .setNameKey("contact.quest.sender_name")
            .setOrder(-3)

        config.addBool("is_ender", isEnder, { v -> isEnder = v }, false)
            .setNameKey("contact.quest.is_ender")
            .setOrder(-2)

        config.addBool("is_team_reward", enableTeamMultiplier, { v -> enableTeamMultiplier = v }, false)
            .setNameKey("contactquest.reward.is_team_reward")
            .setOrder(-1)

        config.addDouble(
            "team_reward_multiplier",
            teamRewardMultiplier.toDouble(),
            { v -> teamRewardMultiplier = v.toFloat() },
            1.0,
            0.0,
            100.0
        )
            .setNameKey("contactquest.reward.team_reward_multiplier")
            .setOrder(0)

        val tagConfig = TagConfig(questFile, unlockTags)

        config.addList("unlock_tags", unlockTags, tagConfig, { v ->
            unlockTags.clear()
            unlockTags.addAll(v)
        }, "")
            .setNameKey("contactquests.config.unlock_tags")
            .setOrder(-1)
    }
}