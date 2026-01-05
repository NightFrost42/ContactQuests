package com.creeperyang.contactquests.quest.reward

import com.creeperyang.contactquests.data.CollectionSavedData
import com.creeperyang.contactquests.data.RewardDistributionManager
import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.reward.Reward
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn

abstract class ParcelRewardBase(id: Long, quest: Quest) : Reward(id, quest) {
    var targetAddressee: String = "QuestNPC"
    var isEnder: Boolean = false

    var enableTeamMultiplier: Boolean = false
    var teamRewardMultiplier: Float = 1.0f

    protected fun distributeItem(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) return
        RewardDistributionManager.distribute(player, stack, targetAddressee, isEnder)
        CollectionSavedData.get(player.serverLevel()).returnReward(player, targetAddressee)
    }

    fun calculateMultipliedCount(originalCount: Int, player: ServerPlayer): Int {
        if (!enableTeamMultiplier) return originalCount

        val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null) ?: return originalCount

        return (originalCount * team.members.size * teamRewardMultiplier).toInt()
    }

    override fun writeData(nbt: CompoundTag) {
        super.writeData(nbt)
        nbt.putString("target_addressee", targetAddressee)
        nbt.putBoolean("is_ender", isEnder)

        nbt.putBoolean("is_team_reward", enableTeamMultiplier)
        nbt.putFloat("team_reward_multiplier", teamRewardMultiplier)
    }

    override fun readData(nbt: CompoundTag) {
        super.readData(nbt)
        if (nbt.contains("target_addressee")) targetAddressee = nbt.getString("target_addressee")
        isEnder = nbt.getBoolean("is_ender")

        enableTeamMultiplier = nbt.getBoolean("is_team_reward")
        teamRewardMultiplier = if (nbt.contains("team_reward_multiplier")) {
            nbt.getFloat("team_reward_multiplier")
        } else {
            1.0f
        }
    }

    override fun writeNetData(buffer: FriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeUtf(targetAddressee)
        buffer.writeBoolean(isEnder)

        buffer.writeBoolean(enableTeamMultiplier)
        buffer.writeFloat(teamRewardMultiplier)
    }

    override fun readNetData(buffer: FriendlyByteBuf) {
        super.readNetData(buffer)
        targetAddressee = buffer.readUtf()
        isEnder = buffer.readBoolean()

        enableTeamMultiplier = buffer.readBoolean()
        teamRewardMultiplier = buffer.readFloat()
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
    }
}