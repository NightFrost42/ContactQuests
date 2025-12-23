package com.creeperyang.contactquests.client.util

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.config.ContactConfig
import com.creeperyang.contactquests.mixin.PostcardEditScreenAccessor
import com.creeperyang.contactquests.task.PostcardTask
import com.flechazo.contact.client.gui.screen.PostcardEditScreen
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.lang.reflect.Field
import java.lang.reflect.Method

@OnlyIn(Dist.CLIENT)
object PostcardAutoFiller {

    private var pendingTask: PostcardTask? = null
    private var isTaskScheduled = false

    private var actionDelay = 0
    private var currentIndex = 0
    private var typingTimer = 0
    private var typingSpeed = 0
    private var targetText = ""

    private var cachedHelperField: Field? = null
    private var cachedPageField: Field? = null
    private var cachedSelectAllMethod: Method? = null
    private var cachedInsertTextMethod: Method? = null
    private var cachedRefreshMethod: Method? = null

    fun schedule(task: PostcardTask) {
        val processed = task.postcardText.replace("\\n", "\n")

        typingSpeed = ContactConfig.autoFillSpeed.get()

        ContactQuests.debug("PostcardAutoFiller: 任务调度 -> 速度: $typingSpeed, 长度: ${processed.length}")

        pendingTask = task
        isTaskScheduled = true

        actionDelay = 0
        currentIndex = 0
        typingTimer = 0
        targetText = processed

        clearReflectionCache()
    }

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        if (!isTaskScheduled) return

        val mc = Minecraft.getInstance()
        val currentScreen = mc.screen

        if (currentScreen !is PostcardEditScreen) {
            handleScreenClosedOrInvalid()
            return
        }

        if (actionDelay < 10) {
            actionDelay++
            return
        }

        if (typingSpeed <= 0) {
            if (fillContentInstant(currentScreen)) {
                finishTask("瞬间填充完成")
            } else {
                actionDelay++
                if (actionDelay > 60) finishTask("瞬间填充超时失败")
            }
        } else {
            processTyping(currentScreen)
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
                    return
                }
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

            val helperObj = cachedHelperField!!.get(textBox)
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
            when (cachedHelperField) {
                null -> {
                    if (!initializeReflection(textBox)) return false
                }
            }

            val helperObj = cachedHelperField!!.get(textBox)
            if (helperObj != null) {
                cachedSelectAllMethod?.invoke(helperObj) // 全选
                cachedInsertTextMethod?.invoke(helperObj, targetText) // 替换
            }

            updatePageRender(textBox, targetText)
            triggerRefresh(textBox)

            ContactQuests.info("PostcardAutoFiller: 瞬间注入完成")
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

            val helperObj = cachedHelperField!!.get(textBox) ?: return false
            val helperClass = helperObj.javaClass

            cachedSelectAllMethod = helperClass.getMethod("selectAll")
            cachedInsertTextMethod = helperClass.getMethod("insertText", String::class.java)

            try {
                val p = textBox.javaClass.getDeclaredField("page")
                p.isAccessible = true
                cachedPageField = p
            } catch (e: NoSuchFieldException) {
                ContactQuests.error("反射警告: 未找到 page 字段")
            }

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
        try {
            cachedPageField?.set(textBox, text)
        } catch (ignored: Exception) {}
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
            val helperObj = cachedHelperField!!.get(textBox)
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

    private fun handleScreenClosedOrInvalid() {
        actionDelay++
        if (actionDelay > 100) finishTask("界面打开超时或中途关闭")
    }

    private fun finishTask(reason: String) {
        ContactQuests.debug("PostcardAutoFiller: 任务结束 ($reason)")
        isTaskScheduled = false
        pendingTask = null
        targetText = ""
    }
}