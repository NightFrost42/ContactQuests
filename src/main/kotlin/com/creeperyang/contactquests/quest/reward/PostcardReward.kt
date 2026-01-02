package com.creeperyang.contactquests.quest.reward

import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.resourse.PostcardDataManager
import dev.ftb.mods.ftblibrary.config.ConfigCallback
import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftblibrary.config.ConfigValue
import dev.ftb.mods.ftblibrary.icon.Color4I
import dev.ftb.mods.ftblibrary.icon.Icon
import dev.ftb.mods.ftblibrary.icon.ItemIcon
import dev.ftb.mods.ftblibrary.ui.Panel
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton
import dev.ftb.mods.ftblibrary.ui.Widget
import dev.ftb.mods.ftblibrary.ui.input.MouseButton
import dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.reward.RewardType
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn

class PostcardReward(id: Long, quest: Quest) : ParcelRewardBase(id, quest) {

    var postcardStyle: String = ""
    var postcardText: String = ""

    override fun getType(): RewardType = RewardRegistry.POSTCARD

    override fun claim(player: ServerPlayer, notify: Boolean) {
        val styleId = if (postcardStyle.isNotEmpty()) ResourceLocation.tryParse(postcardStyle) else null

        var postcardStack = if (styleId != null) {
            PostcardItem.getPostcard(styleId, isEnder)
        } else {
            PostcardItem.getPostcard(ResourceLocation("contact", "default"), isEnder)
        }

        if (postcardText.isNotEmpty()) {
            val processedText = postcardText.replace("\\n", "\n")
            postcardStack = PostcardItem.setText(postcardStack, processedText)
        }

        if (targetAddressee.isNotEmpty()) {
            postcardStack.getOrCreateTag().putString("Sender", targetAddressee)
        }

        distributeItem(player, postcardStack)
    }

    override fun writeData(nbt: CompoundTag) {
        super.writeData(nbt)
        nbt.putString("postcard_style", postcardStyle)
        nbt.putString("postcard_text", postcardText)
    }

    override fun readData(nbt: CompoundTag) {
        super.readData(nbt)
        postcardStyle = nbt.getString("postcard_style")
        postcardText = nbt.getString("postcard_text")
    }

    override fun writeNetData(buffer: FriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeUtf(postcardStyle)
        buffer.writeUtf(postcardText)
    }

    override fun readNetData(buffer: FriendlyByteBuf) {
        super.readNetData(buffer)
        postcardStyle = buffer.readUtf()
        postcardText = buffer.readUtf()
    }

    @OnlyIn(Dist.CLIENT)
    override fun fillConfigGroup(config: ConfigGroup) {
        super.fillConfigGroup(config)

        config.add("postcard_style", PostcardStyleConfig(this), postcardStyle, { postcardStyle = it }, "")
            .setNameKey("contactquest.task.postcard.style")
            .setOrder(10)

        config.addString("postcard_text", postcardText, { postcardText = it }, "")
            .setNameKey("contactquest.task.postcard.text")
            .setOrder(11)
    }

    @OnlyIn(Dist.CLIENT)
    fun getStyleDisplayName(id: String?): Component {
        if (id.isNullOrEmpty()) {
            return Component.translatable("contactquest.postcard.default_style")
        }
        val rl = ResourceLocation.tryParse(id)
        if (rl != null) {
            return Component.translatable("tooltip.postcard.${rl.namespace}.${rl.path}")
        }
        return Component.literal(id)
    }

    @OnlyIn(Dist.CLIENT)
    override fun getAltIcon(): Icon {
        if (postcardStyle.isNotEmpty()) {
            val rl = ResourceLocation.tryParse(postcardStyle)
            if (rl != null) {
                val stack = PostcardItem.getPostcard(rl, isEnder)
                return ItemIcon.getItemIcon(stack)
            }
        }
        return Icon.getIcon("contact:item/postcard")
    }

    @OnlyIn(Dist.CLIENT)
    override fun getAltTitle(): Component {
        return Component.translatable("item.contact.postcard")
    }

    @OnlyIn(Dist.CLIENT)
    private class PostcardStyleConfig(private val reward: PostcardReward) : ConfigValue<String>() {
        init {
            this.value = reward.postcardStyle
        }

        override fun getStringForGUI(v: String?): Component {
            return reward.getStyleDisplayName(v)
        }

        override fun onClicked(widget: Widget, button: MouseButton, callback: ConfigCallback) {
            PostcardStyleSelectorScreen(this, callback, reward).openGui()
        }
    }

    @OnlyIn(Dist.CLIENT)
    private class PostcardStyleSelectorScreen(
        private val configValue: PostcardStyleConfig,
        private val callback: ConfigCallback,
        private val reward: PostcardReward
    ) : AbstractButtonListScreen() {

        init {
            title = Component.translatable("contactquest.task.postcard.style")
            setHasSearchBox(true)
            showBottomPanel(false)
            showCloseButton(true)
        }

        override fun addButtons(panel: Panel) {
            val allStyles = PostcardDataManager.getPostcards().keys.map { it.toString() }.sorted().toMutableList()
            if (!allStyles.contains("")) allStyles.add(0, "")

            for (styleId in allStyles) {
                val btnName = reward.getStyleDisplayName(styleId)
                panel.add(StyleButton(panel, btnName, styleId))
            }
        }

        override fun doCancel() {
            closeGui()
        }

        override fun doAccept() {
            closeGui()
        }

        private inner class StyleButton(panel: Panel, title: Component, val styleId: String) :
            SimpleTextButton(panel, title, Color4I.empty()) {
            override fun onClicked(btn: MouseButton?) {
                playClickSound()
                configValue.value = styleId
                callback.save(true)
                closeGui()
            }
        }
    }
}