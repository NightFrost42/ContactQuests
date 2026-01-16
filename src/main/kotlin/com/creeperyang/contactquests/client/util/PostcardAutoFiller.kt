package com.creeperyang.contactquests.client.util

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.config.ContactConfig
import com.creeperyang.contactquests.mixin.PostcardEditScreenAccessor
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.flechazo.contact.client.gui.screen.PostcardEditScreen
import dev.ftb.mods.ftbquests.client.ClientQuestFile
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.lang.reflect.Field
import java.lang.reflect.Method

@OnlyIn(Dist.CLIENT)
object PostcardAutoFiller : BaseAutoFiller<PostcardTask>() {

    private var currentIndex = 0
    private var typingTimer = 0
    private var typingSpeed = 0
    private var targetText = ""

    private var cachedHelperField: Field? = null
    private var cachedPageField: Field? = null
    private var cachedSelectAllMethod: Method? = null
    private var cachedInsertTextMethod: Method? = null
    private var cachedRefreshMethod: Method? = null

    override val checkContainerId: Boolean = false

    override val maxTimeoutTicks: Int = 100

    override fun resetState() {
        val task = taskData ?: return

        val player = Minecraft.getInstance().player
        val teamData = ClientQuestFile.INSTANCE.selfTeamData
        targetText = task.getResolvedText(teamData, player)
        typingSpeed = ContactConfig.autoFillSpeed.get()
        currentIndex = 0
        typingTimer = 0
        actionDelay = 10
        clearReflectionCache()
    }

    override fun isValidScreen(screen: Screen?): Boolean {
        return screen is PostcardEditScreen
    }

    @SubscribeEvent
    override fun onClientTick(event: ClientTickEvent.Post) {
        super.onClientTick(event)
    }

    override fun runLogic(mc: Minecraft, player: Player, screen: Screen) {
        val editScreen = screen as PostcardEditScreen

        if (typingSpeed <= 0) {
            if (fillContentInstant(editScreen)) {
                finishTask("瞬间填充完成")
            } else {
                actionDelay++
                if (actionDelay > 60) finishTask("瞬间填充超时失败")
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
                if (!initializeReflection(textBox)) return
                clearTextBox(textBox)
            }

            typingTimer++
            if (typingTimer < typingSpeed) return
            typingTimer = 0

            if (currentIndex >= targetText.length) {
                finishTask("打字完成")
                return
            }

            val charToType = targetText[currentIndex].toString()
            val helperObj = cachedHelperField!![textBox]
            if (helperObj != null) {
                cachedInsertTextMethod?.invoke(helperObj, charToType)
            }

            val currentProgressText = targetText.substring(0, currentIndex + 1)
            updatePageRender(textBox, currentProgressText)
            triggerRefresh(textBox)
            currentIndex++

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
                cachedSelectAllMethod?.invoke(helperObj)
                cachedInsertTextMethod?.invoke(helperObj, targetText)
            }

            updatePageRender(textBox, targetText)
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
            val helperClass = helperObj.javaClass

            cachedSelectAllMethod = helperClass.getMethod("selectAll")
            cachedInsertTextMethod = helperClass.getMethod("insertText", String::class.java)

            try {
                val p = textBox.javaClass.getDeclaredField("page")
                p.isAccessible = true
                cachedPageField = p
            } catch (e: NoSuchFieldException) { /* ignore */ }

            try {
                cachedRefreshMethod = textBox.javaClass.getMethod("shouldRefresh")
            } catch (ignored: NoSuchMethodException) {}

            return true
        } catch (e: Exception) {
            ContactQuests.error("反射初始化异常", e)
            return false
        }
    }

    private fun updatePageRender(textBox: Any, text: String) {
        try { cachedPageField?.set(textBox, text) } catch (ignored: Exception) {}
    }

    private fun triggerRefresh(textBox: Any) {
        try {
            cachedRefreshMethod?.invoke(textBox) ?: run {
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
                cachedSelectAllMethod?.invoke(helperObj)
                cachedInsertTextMethod?.invoke(helperObj, "")
                updatePageRender(textBox, "")
            }
        } catch (ignored: Exception) {}
    }

    private fun clearReflectionCache() {
        cachedHelperField = null
        cachedPageField = null
        cachedSelectAllMethod = null
        cachedInsertTextMethod = null
        cachedRefreshMethod = null
    }
}