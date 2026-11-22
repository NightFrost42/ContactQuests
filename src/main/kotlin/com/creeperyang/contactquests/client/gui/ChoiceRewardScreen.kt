package com.creeperyang.contactquests.client.gui

import com.creeperyang.contactquests.client.util.TooltipUtils
import com.creeperyang.contactquests.network.RequestChoiceMessage
import com.creeperyang.contactquests.quest.reward.ParcelChoiceReward
import dev.ftb.mods.ftblibrary.icon.Color4I
import dev.ftb.mods.ftblibrary.ui.BaseScreen
import dev.ftb.mods.ftblibrary.ui.Button
import dev.ftb.mods.ftblibrary.ui.Panel
import dev.ftb.mods.ftblibrary.ui.PanelScrollBar
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton
import dev.ftb.mods.ftblibrary.ui.Theme
import dev.ftb.mods.ftblibrary.ui.input.MouseButton
import dev.ftb.mods.ftblibrary.util.TooltipList
import dev.ftb.mods.ftbquests.client.gui.FTBQuestsTheme
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import com.creeperyang.contactquests.network.NetworkHandler

/** 自选邮寄包裹奖励的选项列表界面，供玩家挑选希望寄回的奖励条目。 */
class ChoiceRewardScreen(val reward: ParcelChoiceReward) : BaseScreen() {
    val optionButtons = mutableListOf<Button>()

    override fun addWidgets() {
        optionButtons.clear()
        val rewards = reward.table?.weightedRewards.orEmpty()
        setSize(310, 54 + rewards.size.coerceAtMost(12) * 24)
        val panel = object : Panel(this) {
            override fun addWidgets() {
                rewards.forEachIndexed { index, entry ->
                    val hidden = reward.isContentsHiddenForViewer()
                    val label = reward.optionLabel(index, hidden)
                    val button = object : SimpleTextButton(this, label, if (hidden) reward.sealedIcon() else entry.reward.icon) {
                        override fun onClicked(button: MouseButton?) {
                            if (button?.isLeft != false) NetworkHandler.sendToServer(RequestChoiceMessage(reward.id, index))
                        }

                        override fun addMouseOverText(list: TooltipList) {
                            list.add(label)
                            if (!hidden) entry.reward.addMouseOverText(list)
                            list.add(Component.translatable("contactquests.choice.postcard_required"))
                            TooltipUtils.wrap(list)
                        }
                    }
                    button.setPosAndSize(0, index * 24, 282, 22)
                    optionButtons.add(button)
                    add(button)
                }
            }

            override fun alignWidgets() {}
        }
        panel.setPosAndSize(12, 32, 286, height - 42)
        panel.setOnlyInteractWithWidgetsInside(true)
        add(panel)
        add(PanelScrollBar(this, panel).apply { setPosAndSize(294, 32, 8, height - 42) })
    }

    override fun alignWidgets() {}
    override fun getTheme(): Theme = FTBQuestsTheme.INSTANCE

    override fun drawBackground(g: GuiGraphics, theme: Theme, x: Int, y: Int, w: Int, h: Int) {
        super.drawBackground(g, theme, x, y, w, h)
        theme.drawString(g, Component.translatable("contactquests.choice.title"), x + 12, y + 10, Color4I.WHITE, 0)
    }
}
