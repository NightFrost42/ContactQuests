package com.creeperyang.contactquests.quest.reward

import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftblibrary.icon.Icon
import dev.ftb.mods.ftblibrary.icon.ItemIcon
import dev.ftb.mods.ftblibrary.ui.Widget
import dev.ftb.mods.ftblibrary.util.client.PositionedIngredient
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.reward.RewardType
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import java.util.*

class ParcelReward(id: Long, quest: Quest) : ParcelRewardBase(id, quest) {
    var item: ItemStack = ItemStack(Items.APPLE)
    var count: Int = 1
    var randomBonus: Int = 0

    override fun getType(): RewardType = RewardRegistry.PARCEL

    override fun claim(player: ServerPlayer, notify: Boolean) {
        val actualCount = count + player.level().random.nextInt(randomBonus + 1)

        val stackToSend = item.copy()
        stackToSend.count = actualCount

        distributeItem(player, stackToSend)
    }

    override fun writeData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.writeData(nbt, provider)
        if (!item.isEmpty) nbt.put("item", item.save(provider))
        if (count > 1) nbt.putInt("count", count)
        if (randomBonus > 0) nbt.putInt("random_bonus", randomBonus)
    }

    override fun readData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.readData(nbt, provider)
        if (nbt.contains("item")) {
            item = ItemStack.parseOptional(provider, nbt.getCompound("item"))
        }
        count = nbt.getInt("count")
        if (count == 0) {
            count = item.count
            item.count = 1
        }
        randomBonus = nbt.getInt("random_bonus")
    }

    override fun writeNetData(buffer: RegistryFriendlyByteBuf) {
        super.writeNetData(buffer)
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, item)
        buffer.writeVarInt(count)
        buffer.writeVarInt(randomBonus)
    }

    override fun readNetData(buffer: RegistryFriendlyByteBuf) {
        super.readNetData(buffer)
        item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)
        count = buffer.readVarInt()
        randomBonus = buffer.readVarInt()
    }

    @OnlyIn(Dist.CLIENT)
    override fun fillConfigGroup(config: ConfigGroup) {
        super.fillConfigGroup(config)
        config.addItemStack("item", item, { v -> item = v }, ItemStack.EMPTY, true, false).nameKey =
            "ftbquests.reward.ftbquests.item"
        config.addInt("count", count, { v -> count = v }, 1, 1, 8192).nameKey = "ftbquests.reward.parcel.item_count"
        config.addInt("random_bonus", randomBonus, { v -> randomBonus = v }, 0, 0, 8192).nameKey =
            "ftbquests.reward.random_bonus"
    }

    @OnlyIn(Dist.CLIENT)
    override fun getAltTitle(): MutableComponent {
        return if (randomBonus > 0) {
            Component.literal("$count-${count + randomBonus}x ").append(item.hoverName)
        } else if (count > 1) {
            Component.literal("${count}x ").append(item.hoverName)
        } else {
            item.hoverName.copy()
        }
    }

    @OnlyIn(Dist.CLIENT)
    override fun getAltIcon(): Icon {
        return if (item.isEmpty) super.getAltIcon() else ItemIcon.getItemIcon(item.copy().also { it.count = 1 })
    }

    @OnlyIn(Dist.CLIENT)
    override fun getIngredient(widget: Widget): Optional<PositionedIngredient> {
        return PositionedIngredient.of(item, widget, true)
    }
}