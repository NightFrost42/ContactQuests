package com.creeperyang.contactquests.client.util

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.config.ContactConfig
import com.creeperyang.contactquests.mixin.PostcardEditScreenAccessor
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.flechazo.contact.client.gui.screen.PostcardEditScreen
import com.flechazo.contact.client.widget.EditableTextBox
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.font.TextFieldHelper
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.event.TickEvent
import net.minecraft.network.chat.Component
import java.lang.reflect.Field

@OnlyIn(Dist.CLIENT)
object PostcardAutoFiller : BaseAutoFiller<String>() {

    fun schedule(task: PostcardTask) {
        schedule(task.getResolvedText(ClientQuestFile.INSTANCE.selfTeamData, Minecraft.getInstance().player))
    }

    private var currentIndex = 0
    private var typingTimer = 0
    private var typingSpeed = 0
    private var targetText = ""
    private var failedAttempts = 0

    private var cachedHelperField: Field? = null
    private var cachedPageField: Field? = null

    override val checkContainerId: Boolean = false

    override val maxTimeoutTicks: Int = 100

    override fun resetState() {
        targetText = taskData ?: ""
        typingSpeed = ContactConfig.autoFillSpeed.get()
        currentIndex = 0
        typingTimer = 0
        failedAttempts = 0
        actionDelay = 10
        clearReflectionCache()
    }

    override fun isValidScreen(screen: Screen?): Boolean {
        return screen is PostcardEditScreen
    }

    @SubscribeEvent
    override fun onClientTick(event: TickEvent.ClientTickEvent) {
        super.onClientTick(event)
    }

    override fun runLogic(mc: Minecraft, player: Player, screen: Screen) {
        val editScreen = screen as PostcardEditScreen
        val textBox = (editScreen as? PostcardEditScreenAccessor)?.`contactQuests$getTextBox`()
        if (textBox != null && (targetText.length >= 1024 ||
                    mc.font.wordWrapHeight(targetText, textBox.width) > textBox.height * mc.font.lineHeight / 12)) {
            player.sendSystemMessage(Component.translatable("contactquests.postcard.text_too_long"))
            finishTask("正文超出明信片容量，保留原卡")
            return
        }

        if (typingSpeed <= 0) {
            if (fillContentInstant(editScreen)) {
                finishTask("瞬间填充完成")
            } else {
                if (++failedAttempts > 60) finishTask("瞬间填充超时失败")
            }
        } else {
            processTyping(editScreen)
        }
    }

    private fun processTyping(screen: PostcardEditScreen) {
        if (screen !is PostcardEditScreenAccessor) {
            finishTask("Mixin 访问失败")
            return
        }

        val textBox = screen.`contactQuests$getTextBox`() ?: return

        try {
            if (cachedHelperField == null) {
                if (!initializeReflection(textBox)) {
                    if (++failedAttempts > 60) finishTask("打字初始化超时失败")
                    return
                }
                clearTextBox(textBox)
            }

            // 在向草稿插入下一字符前，校验当前页内容以感知玩家手工修改。
            if (cachedPageField?.get(textBox) != targetText.substring(0, currentIndex)) {
                finishTask("玩家已修改正文，停止自动填写")
                return
            }

            typingTimer++
            if (typingTimer < typingSpeed) return
            typingTimer = 0

            if (currentIndex >= targetText.length) {
                finishTask("打字完成")
                return
            }

            val nextIndex = targetText.offsetByCodePoints(currentIndex, 1)
            val charToType = targetText.substring(currentIndex, nextIndex)
            val helperObj = cachedHelperField!![textBox]
            if (helperObj != null) {
                (helperObj as? TextFieldHelper)?.insertText(charToType)
            }

            val currentProgressText = targetText.substring(0, nextIndex)
            if (cachedPageField?.get(textBox) != currentProgressText) {
                finishTask("原生输入拒绝，停止自动填写")
                return
            }
            triggerRefresh(textBox)
            currentIndex = nextIndex

        } catch (e: Exception) {
            ContactQuests.error("PostcardAutoFiller: 打字过程出错", e)
            finishTask("出错中止")
        }
    }

    private fun fillContentInstant(screen: PostcardEditScreen): Boolean {
        if (screen !is PostcardEditScreenAccessor) return false
        val textBox = screen.`contactQuests$getTextBox`() ?: return false

        try {
            if (cachedHelperField == null && !initializeReflection(textBox)) return false

            val helperObj = cachedHelperField!![textBox]
            if (helperObj != null) {
                (helperObj as? TextFieldHelper)?.selectAll()
                (helperObj as? TextFieldHelper)?.insertText(targetText)
            }

            if (cachedPageField?.get(textBox) != targetText) return false
            triggerRefresh(textBox)
            return true

        } catch (e: Exception) {
            ContactQuests.error("PostcardAutoFiller: 瞬间写入出错", e)
        }
        return false
    }

    private fun initializeReflection(textBox: Any): Boolean {
        try {
            var clazz: Class<*>? = textBox.javaClass
            while (clazz != null) {
                try {
                    val f = clazz.getDeclaredField("textInputUtil")
                    f.isAccessible = true
                    cachedHelperField = f
                    break
                } catch (e: NoSuchFieldException) {
                    clazz = clazz.superclass
                }
            }

            if (cachedHelperField == null) return false

            val helperObj = cachedHelperField!![textBox] ?: return false
            if (helperObj !is TextFieldHelper) return false

            try {
                val p = textBox.javaClass.getDeclaredField("page")
                p.isAccessible = true
                cachedPageField = p
            } catch (_: NoSuchFieldException) { /* 兼容不包含该字段的版本或自定义控件 */ }

            return cachedPageField != null
        } catch (e: Exception) {
            ContactQuests.error("反射初始化异常", e)
            return false
        }
    }

    private fun triggerRefresh(textBox: Any) {
        try {
            (textBox as? EditableTextBox)?.shouldRefresh() ?: run {
                val modifiedField = textBox.javaClass.getDeclaredField("isModified")
                modifiedField.isAccessible = true
                modifiedField.setBoolean(textBox, true)
            }
        } catch (ignored: Exception) {}
    }

    private fun clearTextBox(textBox: Any) {
        try {
            val helperObj = cachedHelperField!![textBox]
            if (helperObj != null) {
                (helperObj as? TextFieldHelper)?.selectAll()
                (helperObj as? TextFieldHelper)?.insertText("")
            }
        } catch (ignored: Exception) {}
    }

    private fun clearReflectionCache() {
        cachedHelperField = null
        cachedPageField = null
    }
}
