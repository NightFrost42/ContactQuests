package com.creeperyang.contactquests.quest.reward

import com.flechazo.contact.common.item.PostcardItem
import com.flechazo.contact.data.PostcardDataManager
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
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

class PostcardReward(id: Long, quest: Quest) : ParcelRewardBase(id, quest) {

    var postcardStyle: String = ""
    var postcardText: String = ""

    fun interface PostcardTextReplacer {
        fun replace(text: String, player: ServerPlayer): String
    }

    companion object {
        private val replacers = mutableListOf<PostcardTextReplacer>()

        fun registerReplacer(replacer: PostcardTextReplacer) {
            replacers.add(replacer)
        }

        fun processText(text: String, player: ServerPlayer): String {
            var currentText = text
            for (replacer in replacers) {
                currentText = replacer.replace(currentText, player)
            }
            return currentText
        }

        init {
            registerReplacer { text, player ->
                if (text.contains("<team_size>")) {
                    val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null)
                    val size = team?.members?.size ?: 1
                    text.replace("<team_size>", size.toString())
                } else {
                    text
                }
            }

            registerReplacer { text, player ->
                if (text.contains("<team_name>")) {
                    val team = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElse(null)
                    val name = team.name.string
                    text.replace("<team_name>", name)
                } else {
                    text
                }
            }

            registerReplacer { text, player ->
                if (text.contains("<player_name>")) {
                    text.replace("<player_name>", player.gameProfile.name)
                } else {
                    text
                }
            }
        }
    }

    override fun getType(): RewardType = RewardRegistry.POSTCARD

    override fun claim(player: ServerPlayer, notify: Boolean) {
        val styleId = if (postcardStyle.isNotEmpty()) ResourceLocation.tryParse(postcardStyle) else null

        var postcardStack = if (styleId != null) {
            PostcardItem.getPostcard(styleId, isEnder)
        } else {
            PostcardItem.getPostcard(ResourceLocation.fromNamespaceAndPath("contact", "default"), isEnder)
        }

        if (postcardText.isNotEmpty()) {
            val processedText = postcardText.replace("\\n", "\n")
            val processedMessage = processText(processedText, player)
            postcardStack = PostcardItem.setText(postcardStack, processedMessage)
        }

        if (targetAddressee.isNotEmpty()) {
            try {
                val componentId = ResourceLocation.fromNamespaceAndPath("contact", "postcard_sender")
                val rawComponentType = BuiltInRegistries.DATA_COMPONENT_TYPE[componentId]

                @Suppress("UNCHECKED_CAST")
                val senderComponentType = rawComponentType as? DataComponentType<String>

                if (senderComponentType != null) {
                    postcardStack.set(senderComponentType, targetAddressee)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        distributeItem(player, postcardStack)
    }

    override fun writeData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.writeData(nbt, provider)
        nbt.putString("postcard_style", postcardStyle)
        nbt.putString("postcard_text", postcardText)
    }

    override fun readData(nbt: CompoundTag, provider: HolderLookup.Provider) {
        super.readData(nbt, provider)
        postcardStyle = nbt.getString("postcard_style")
        postcardText = nbt.getString("postcard_text")
    }

    override fun writeNetData(buffer: RegistryFriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeUtf(postcardStyle)
        buffer.writeUtf(postcardText)
    }

    override fun readNetData(buffer: RegistryFriendlyByteBuf) {
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