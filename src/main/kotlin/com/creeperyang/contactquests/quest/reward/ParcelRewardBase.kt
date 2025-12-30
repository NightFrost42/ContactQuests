package com.creeperyang.contactquests.quest.reward

import com.creeperyang.contactquests.data.CollectionSavedData
import com.creeperyang.contactquests.data.RewardDistributionManager
import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.reward.Reward
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

abstract class ParcelRewardBase(id: Long, quest: Quest) : Reward(id, quest) {
    var targetAddressee: String = "QuestNPC"
    var isEnder: Boolean = false

    protected fun distributeItem(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) return
        RewardDistributionManager.distribute(player, stack, targetAddressee, isEnder)
        CollectionSavedData.get(player.serverLevel()).returnReward(player, targetAddressee)
    }

    override fun writeData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.writeData(nbt, provider)
        nbt.putString("target_addressee", targetAddressee)
        nbt.putBoolean("is_ender", isEnder)
    }

    override fun readData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.readData(nbt, provider)
        if (nbt.contains("target_addressee")) targetAddressee = nbt.getString("target_addressee")
        isEnder = nbt.getBoolean("is_ender")
    }

    override fun writeNetData(buffer: RegistryFriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeUtf(targetAddressee)
        buffer.writeBoolean(isEnder)
    }

    override fun readNetData(buffer: RegistryFriendlyByteBuf) {
        super.readNetData(buffer)
        targetAddressee = buffer.readUtf()
        isEnder = buffer.readBoolean()
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
    }
}