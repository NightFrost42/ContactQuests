package com.creeperyang.contactquests.quest.reward

import com.creeperyang.contactquests.mixin.ItemRewardAccessor
import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftblibrary.icon.Icon
import dev.ftb.mods.ftblibrary.icon.Icons
import dev.ftb.mods.ftblibrary.snbt.SNBTCompoundTag
import dev.ftb.mods.ftblibrary.ui.Widget
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftblibrary.util.client.PositionedIngredient
import dev.ftb.mods.ftbquests.quest.BaseQuestFile
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.QuestObjectType
import dev.ftb.mods.ftbquests.quest.loot.RewardTable
import dev.ftb.mods.ftbquests.quest.reward.ItemReward
import dev.ftb.mods.ftbquests.quest.reward.Reward
import dev.ftb.mods.ftbquests.quest.reward.RewardType
import dev.ftb.mods.ftbquests.util.ConfigQuestObject
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import java.util.*

open class ParcelRandomReward(id: Long, quest: Quest) : ParcelRewardBase(id, quest) {
    var table: RewardTable? = null
        get() {
            if (field != null && !field!!.isValid) {
                field = null
            }
            return field
        }

    override fun getType(): RewardType = RewardRegistry.PARCEL_RANDOM

    override fun claim(player: ServerPlayer, notify: Boolean) {
        val currentTable = table ?: return

        val rewards = currentTable.generateWeightedRandomRewards(player.random, 1, false)

        rewards.forEach { weightedReward ->
            handleReward(player, weightedReward.reward, notify)
        }
    }

    protected fun handleReward(player: ServerPlayer, reward: Reward, notify: Boolean) {
        if (reward is ItemReward) {
            val stack = reward.item.copy()

            val rBonus = (reward as ItemRewardAccessor).`contactquests$getRandomBonus`()
            val bonus = player.level().random.nextInt(rBonus + 1)

            stack.count = calculateMultipliedCount(reward.count + bonus, player)

            distributeItem(player, stack)
        } else {
            reward.claim(player, notify)
        }
    }

    override fun writeData(nbt: CompoundTag) {
        super.writeData(nbt)
        if (table != null) {
            nbt.putLong("table_id", table!!.id)
            if (table!!.id == -1L) {
                val tag = SNBTCompoundTag()
                table!!.writeData(tag)
                nbt.put("table_data", tag)
            }
        }
    }

    override fun readData(nbt: CompoundTag) {
        super.readData(nbt)
        var tempTable: RewardTable? = null

        val file: BaseQuestFile = questFile
        val tableId = nbt.getLong("table_id")

        if (tableId != 0L) {
            tempTable = file.getRewardTable(tableId)
        }

        if (tempTable == null && nbt.contains("table_data")) {
            tempTable = RewardTable(-1L, file)
            tempTable.readData(nbt.getCompound("table_data"))
        }

        this.table = tempTable
    }

    override fun writeNetData(buffer: FriendlyByteBuf) {
        super.writeNetData(buffer)
        val t = table
        buffer.writeLong(t?.id ?: 0L)
        if (t != null && t.id == -1L) {
            t.writeNetData(buffer)
        }
    }

    override fun readNetData(buffer: FriendlyByteBuf) {
        super.readNetData(buffer)
        val tId = buffer.readLong()
        if (tId == -1L) {
            val t = RewardTable(-1L, questFile)
            t.readNetData(buffer)
            this.table = t
        } else {
            this.table = questFile.getRewardTable(tId)
        }
    }

    @OnlyIn(Dist.CLIENT)
    override fun fillConfigGroup(config: ConfigGroup) {
        super.fillConfigGroup(config)
        config.add("table", ConfigQuestObject(QuestObjectType.REWARD_TABLE), table, { v -> table = v }, table).nameKey =
            "ftbquests.reward_table"
    }

    @OnlyIn(Dist.CLIENT)
    override fun getAltTitle(): Component {
        return table?.getTitleOrElse(super.getAltTitle()) ?: super.getAltTitle()
    }

    @OnlyIn(Dist.CLIENT)
    override fun getAltIcon(): Icon {
        return table?.icon ?: Icons.DICE
    }

    @OnlyIn(Dist.CLIENT)
    override fun addMouseOverText(list: TooltipList) {
        table?.addMouseOverText(list, true, false)
    }

    @OnlyIn(Dist.CLIENT)
    override fun getIngredient(widget: Widget): Optional<PositionedIngredient> {
        val t = table
        return if (t != null && t.lootCrate != null) {
            PositionedIngredient.of(t.lootCrate!!.createStack(), widget)
        } else {
            Optional.empty()
        }
    }
}