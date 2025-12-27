package com.creeperyang.contactquests.quest.reward

import com.creeperyang.contactquests.data.PackagingType
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
    var targetAddressee: String = "Quest System"
    var isEnder: Boolean = false
    var packaging: PackagingType = PackagingType.PARCEL

    protected fun distributeItem(player: ServerPlayer, stack: ItemStack) {
        if (stack.isEmpty) return
        RewardDistributionManager.distribute(player, stack, targetAddressee, isEnder, packaging)
    }

    override fun writeData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.writeData(nbt, provider)
        nbt.putString("target_addressee", targetAddressee)
        nbt.putBoolean("is_ender", isEnder)
        nbt.putString("packaging", packaging.id)
    }

    override fun readData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.readData(nbt, provider)
        if (nbt.contains("target_addressee")) targetAddressee = nbt.getString("target_addressee")
        isEnder = nbt.getBoolean("is_ender")
        if (nbt.contains("packaging")) {
            val typeId = nbt.getString("packaging")
            packaging = PackagingType.entries.find { it.id == typeId } ?: PackagingType.PARCEL
        }
    }

    override fun writeNetData(buffer: RegistryFriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeUtf(targetAddressee)
        buffer.writeBoolean(isEnder)
        buffer.writeEnum(packaging)
    }

    override fun readNetData(buffer: RegistryFriendlyByteBuf) {
        super.readNetData(buffer)
        targetAddressee = buffer.readUtf()
        isEnder = buffer.readBoolean()
        packaging = buffer.readEnum(PackagingType::class.java)
    }

    @OnlyIn(Dist.CLIENT)
    override fun fillConfigGroup(config: ConfigGroup) {
        super.fillConfigGroup(config)
        config.addString("target_addressee", targetAddressee, { v -> targetAddressee = v }, "Quest System")
            .setNameKey("contact.quest.sender_name")
            .setOrder(-3)

        config.addBool("is_ender", isEnder, { v -> isEnder = v }, false)
            .setNameKey("contact.quest.is_ender")
            .setOrder(-2)

        config.addEnum("packaging", packaging, { v -> packaging = v }, PackagingType.NAME_MAP)
            .setNameKey("contact.quest.packaging")
            .setOrder(-1)
    }
}