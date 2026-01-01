package com.creeperyang.contactquests.client.gui

import com.creeperyang.contactquests.config.ContactConfig
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class ContactConfigScreen(private val parent: Screen) : Screen(Component.translatable("contactquests.config.title")) {

    private lateinit var autoFillSpeedBox: EditBox
    private lateinit var retryIntervalBox: EditBox
    private lateinit var enableDeliveryButton: CycleButton<Boolean>

    override fun init() {
        super.init()

        val listLeft = this.width / 2 - 100
        val listTop = 50
        val step = 45
        this.enableDeliveryButton = CycleButton.onOffBuilder(ContactConfig.enableDeliveryTime.get())
            .create(
                listLeft,
                listTop,
                200,
                20,
                Component.translatable("contactquests.config.enable_delivery")
            ) { _, _ ->
            }
        this.addRenderableWidget(enableDeliveryButton)

        this.addRenderableWidget(Button.builder(Component.translatable("contactquests.config.auto_fill_speed")) { }
            .bounds(listLeft, listTop + step - 12, 200, 10).build().apply { active = false })

        this.autoFillSpeedBox =
            EditBox(this.font, listLeft, listTop + step, 200, 20, Component.literal("Auto Fill Speed"))
        this.autoFillSpeedBox.value = ContactConfig.autoFillSpeed.get().toString()
        this.autoFillSpeedBox.setFilter { it.all { char -> char.isDigit() } }
        this.addRenderableWidget(autoFillSpeedBox)

        this.addRenderableWidget(Button.builder(Component.translatable("contactquests.config.retry_interval")) { }
            .bounds(listLeft, listTop + step * 2 - 12, 200, 10).build().apply { active = false })

        this.retryIntervalBox =
            EditBox(this.font, listLeft, listTop + step * 2, 200, 20, Component.literal("Retry Interval"))
        this.retryIntervalBox.value = ContactConfig.retryInterval.get().toString()
        this.retryIntervalBox.setFilter { it.all { char -> char.isDigit() } }
        this.addRenderableWidget(retryIntervalBox)

        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE) { saveAndClose() }
                .bounds(this.width / 2 - 105, this.height - 30, 100, 20)
                .build()
        )
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL) { this.onClose() }
                .bounds(this.width / 2 + 5, this.height - 30, 100, 20)
                .build()
        )
    }

    private fun saveAndClose() {
        ContactConfig.enableDeliveryTime.set(enableDeliveryButton.value)

        try {
            val speedVal = autoFillSpeedBox.value.toIntOrNull() ?: 1
            val clampedSpeed = speedVal.coerceIn(0, 100)
            ContactConfig.autoFillSpeed.set(clampedSpeed)
        } catch (_: Exception) {
            ContactConfig.autoFillSpeed.set(1)
        }

        try {
            val retryVal = retryIntervalBox.value.toIntOrNull() ?: 10
            val clampedRetry = retryVal.coerceIn(1, 12000)
            ContactConfig.retryInterval.set(clampedRetry)
        } catch (_: Exception) {
            ContactConfig.retryInterval.set(10)
        }

        ContactConfig.SPEC.save()

        this.onClose()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF)
    }

    override fun onClose() {
        this.minecraft!!.setScreen(parent)
    }
}