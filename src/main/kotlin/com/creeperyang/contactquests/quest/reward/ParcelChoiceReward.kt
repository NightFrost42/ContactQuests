package com.creeperyang.contactquests.quest.reward

import com.creeperyang.contactquests.client.gui.ChoiceRewardScreen
import com.creeperyang.contactquests.data.ChoiceRewardSavedData
import com.creeperyang.contactquests.utils.QuestObjectText
import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftblibrary.ui.Button
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.reward.RewardType
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn

/** A selected table entry is granted only inside the verified postcard transaction. */
class ParcelChoiceReward(id: Long, quest: Quest) : ParcelRandomReward(id, quest) {
    var templateGroup = "default"
    var optionLabels: MutableList<String> = mutableListOf()

    /** The normal NPC name is visible, and the hidden postcard marker routes the claim before ordinary tasks. */
    val claimRecipient: String
        get() = targetAddressee

    override fun getType(): RewardType = RewardRegistry.PARCEL_CHOICE
    override fun getExcludeFromClaimAll() = true

    override fun claim(player: ServerPlayer, notify: Boolean) {
        val request = ChoiceRewardSavedData.consumeAuthorization(player, this) ?: return
        val selected = table?.weightedRewards?.getOrNull(request.option) ?: return
        if (handleReward(player, selected.reward, notify)) request.granted = true
    }

    fun optionLabel(index: Int, hidden: Boolean): Component =
        optionLabels.getOrNull(index)?.takeIf { it.isNotBlank() }
            ?.let(Component::literal) ?: if (hidden) Component.translatable("contactquests.choice.option", index + 1)
            else table?.weightedRewards?.getOrNull(index)?.reward?.let(QuestObjectText::safeTitle)
                ?: Component.translatable("contactquests.choice.option", index + 1)

    override fun writeData(nbt: CompoundTag) {
        super.writeData(nbt)
        nbt.putString("template_group", templateGroup)
        nbt.put("option_labels", net.minecraft.nbt.ListTag().apply {
            optionLabels.forEach { add(net.minecraft.nbt.StringTag.valueOf(it)) }
        })
    }

    override fun readData(nbt: CompoundTag) {
        super.readData(nbt)
        templateGroup = nbt.getString("template_group").ifBlank { "default" }
        optionLabels = nbt.getList("option_labels", net.minecraft.nbt.Tag.TAG_STRING.toInt())
            .map { it.asString }.toMutableList()
    }

    override fun writeNetData(buffer: FriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeUtf(templateGroup)
        buffer.writeVarInt(optionLabels.size)
        optionLabels.forEach { buffer.writeUtf(it) }
    }

    override fun readNetData(buffer: FriendlyByteBuf) {
        super.readNetData(buffer)
        templateGroup = buffer.readUtf()
        optionLabels = MutableList(buffer.readVarInt()) { buffer.readUtf() }
    }

    @OnlyIn(Dist.CLIENT)
    override fun fillConfigGroup(config: ConfigGroup) {
        super.fillConfigGroup(config)
        config.addString("template_group", templateGroup, { templateGroup = it.ifBlank { "default" } }, "default")
            .setNameKey("contactquests.reward.template_group")
        config.addList(
            "option_labels", optionLabels, dev.ftb.mods.ftblibrary.config.StringConfig(),
            { optionLabels = it.toMutableList() }, ""
        ).setNameKey("contactquests.reward.option_labels")
    }

    override fun onButtonClicked(button: Button, canClaim: Boolean) {
        if (FMLEnvironment.dist.isClient && canClaim) ClientOpener.open(this, button)
    }

    @OnlyIn(Dist.CLIENT)
    private object ClientOpener {
        fun open(reward: ParcelChoiceReward, button: Button) {
            button.playClickSound()
            ChoiceRewardScreen(reward).openGui()
        }
    }

    @OnlyIn(Dist.CLIENT)
    override fun addMouseOverText(list: TooltipList) {
        if (!isContentsHiddenForViewer()) table?.addMouseOverText(list, false, false)
        list.add(Component.translatable("contactquests.choice.postcard_required"))
    }

    @OnlyIn(Dist.CLIENT)
    override fun getAltTitle(): Component = Component.translatable("contactquests.reward.parcel_choice")
}
