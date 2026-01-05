package com.creeperyang.contactquests.client.util

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.config.ContactConfig
import com.creeperyang.contactquests.mixin.PostcardEditScreenAccessor
import com.creeperyang.contactquests.mixin.TextBoxAccessor
import com.creeperyang.contactquests.mixin.TextInputUtilAccessor
import com.creeperyang.contactquests.quest.task.PostcardTask
import com.flechazo.contact.client.gui.screen.PostcardEditScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

@OnlyIn(Dist.CLIENT)
object PostcardAutoFiller : BaseAutoFiller<PostcardTask>() {

    private var currentIndex = 0
    private var typingTimer = 0
    private var typingSpeed = 0
    private var targetText = ""

    override val checkContainerId: Boolean = false

    override val maxTimeoutTicks: Int = 100

    override fun resetState() {
        val task = taskData ?: return
        targetText = task.postcardText.replace("\\n", "\n")
        typingSpeed = ContactConfig.autoFillSpeed.get()
        currentIndex = 0
        typingTimer = 0
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

        val textBox = (editScreen as? PostcardEditScreenAccessor)?.`contactQuests$getTextBox`() ?: return
        val textBoxAcc = textBox as? TextBoxAccessor ?: return

        val helper = textBoxAcc.textInputUtil as? TextInputUtilAccessor ?: return

        if (typingSpeed <= 0) {
            fillContentInstant(textBoxAcc, helper)
            finishTask("瞬间填充完成")
        } else {
            processTyping(textBoxAcc, helper)
        }
    }

    private fun processTyping(textBoxAcc: TextBoxAccessor, helper: TextInputUtilAccessor) {
        try {
            if (currentIndex == 0) {
                helper.invokeSelectAll()
                helper.invokeInsertText("")
            }

            typingTimer++
            if (typingTimer < typingSpeed) return
            typingTimer = 0

            if (currentIndex >= targetText.length) {
                finishTask("打字完成")
                return
            }

            val charToType = targetText[currentIndex].toString()
            helper.invokeInsertText(charToType)

            textBoxAcc.setPage(targetText.substring(0, currentIndex + 1))
            textBoxAcc.setIsModified(true)
            textBoxAcc.invokeShouldRefresh()

            currentIndex++

        } catch (e: Exception) {
            ContactQuests.error("PostcardAutoFiller: 打字过程出错", e)
            finishTask("出错中止")
        }
    }

    private fun fillContentInstant(textBoxAcc: TextBoxAccessor, helper: TextInputUtilAccessor) {
        helper.invokeSelectAll()
        helper.invokeInsertText(targetText)
        textBoxAcc.setPage(targetText)
        textBoxAcc.invokeShouldRefresh()
    }
}