package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.compat.kubejs.KubeJSObjectEditors.OverrideHelper
import com.creeperyang.contactquests.compat.kubejs.KubeJSObjectEditors.executeEdit
import com.creeperyang.contactquests.compat.kubejs.KubeJSObjectEditors.getReward
import com.creeperyang.contactquests.compat.kubejs.KubeJSObjectEditors.saveToNbt
import com.creeperyang.contactquests.compat.kubejs.KubeJSObjectEditors.syncObject
import com.creeperyang.contactquests.quest.reward.ParcelReward
import com.creeperyang.contactquests.quest.reward.PostcardReward
import com.creeperyang.contactquests.quest.reward.RedPacketReward
import dev.ftb.mods.ftbquests.quest.reward.Reward
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack

/** 编辑邮件奖励及按存档覆写，统一复用变更跟踪与同步。 */
internal object KubeJSRewardEditors {
    fun editParcelReward(id: Any, item: ItemStack?, count: Number?, randomBonus: Number?, target: String?) =
        editParcelRewardInternal(id, item, count, randomBonus, target, true)

    fun editParcelRewardSaved(
        level: ServerLevel,
        id: Any,
        item: ItemStack?,
        count: Number?,
        randomBonus: Number?,
        target: String?
    ): Boolean {
        if (editParcelRewardInternal(id, item, count, randomBonus, target, false)) {
            saveToNbt(
                level,
                id,
                mapOf(
                    "item" to item,
                    "count" to count?.toInt(),
                    "randomBonus" to randomBonus?.toInt(),
                    "target" to target
                )
            )
            return true
        }
        return false
    }

    private fun editParcelRewardInternal(
        id: Any,
        item: ItemStack?,
        count: Number?,
        randomBonus: Number?,
        target: String?,
        save: Boolean
    ) =
        executeEdit(id, save, { getReward(it) as? ParcelReward }) { reward, tracker ->
            tracker.updateItem(reward.item, item) { reward.item = it }
            tracker.update(reward.count, count?.toInt()) { reward.count = it }
            tracker.update(reward.randomBonus, randomBonus?.toInt()) { reward.randomBonus = it }
            tracker.update(reward.targetAddressee, target) { reward.targetAddressee = it }
        }

    fun editRedPacketReward(
        id: Any,
        item: ItemStack?,
        count: Number?,
        blessing: String?,
        randomBonus: Number?,
        target: String?
    ) =
        editRedPacketRewardInternal(id, item, count, blessing, randomBonus, target, true)

    fun editRedPacketRewardSaved(
        level: ServerLevel,
        id: Any,
        item: ItemStack?,
        count: Number?,
        blessing: String?,
        randomBonus: Number?,
        target: String?
    ): Boolean {
        if (editRedPacketRewardInternal(id, item, count, blessing, randomBonus, target, false)) {
            saveToNbt(
                level,
                id,
                mapOf(
                    "item" to item,
                    "count" to count?.toInt(),
                    "blessing" to blessing,
                    "randomBonus" to randomBonus?.toInt(),
                    "target" to target
                )
            )
            return true
        }
        return false
    }

    private fun editRedPacketRewardInternal(
        id: Any,
        item: ItemStack?,
        count: Number?,
        blessing: String?,
        randomBonus: Number?,
        target: String?,
        save: Boolean
    ) =
        executeEdit(id, save, { getReward(it) as? RedPacketReward }) { reward, tracker ->
            tracker.updateItem(reward.item, item) { reward.item = it }
            tracker.update(reward.count, count?.toInt()) { reward.count = it }
            tracker.update(reward.blessing, blessing) { reward.blessing = it }
            tracker.update(reward.randomBonus, randomBonus?.toInt()) { reward.randomBonus = it }
            tracker.update(reward.targetAddressee, target) { reward.targetAddressee = it }
        }

    fun editPostcardReward(id: Any, style: String?, text: String?, sender: String?) =
        editPostcardRewardInternal(id, style, text, sender, true)

    fun editPostcardRewardSaved(
        level: ServerLevel,
        id: Any,
        style: String?,
        text: String?,
        sender: String?
    ): Boolean {
        if (editPostcardRewardInternal(id, style, text, sender, false)) {
            saveToNbt(level, id, mapOf("style" to style, "text" to text, "target" to sender))
            return true
        }
        return false
    }

    private fun editPostcardRewardInternal(id: Any, style: String?, text: String?, sender: String?, save: Boolean) =
        executeEdit(id, save, { getReward(it) as? PostcardReward }) { reward, tracker ->
            tracker.update(reward.postcardStyle, style) { reward.postcardStyle = it }
            tracker.update(reward.postcardText, text) { reward.postcardText = it }
            tracker.update(reward.targetAddressee, sender) { reward.targetAddressee = it }
        }

    internal fun applyRewardOverride(reward: Reward, tag: CompoundTag) {
        val helper = OverrideHelper(tag)

        when (reward) {
            is ParcelReward -> {
                helper.updateItem { reward.item = it }
                helper.updateInt("count") { reward.count = it }
                helper.updateInt("randomBonus") { reward.randomBonus = it }
                helper.updateString("target") { reward.targetAddressee = it }
            }

            is RedPacketReward -> {
                helper.updateItem { reward.item = it }
                helper.updateInt("count") { reward.count = it }
                helper.updateInt("randomBonus") { reward.randomBonus = it }
                helper.updateString("blessing") { reward.blessing = it }
                helper.updateString("target") { reward.targetAddressee = it }
            }

            is PostcardReward -> {
                helper.updateString("style") { reward.postcardStyle = it }
                helper.updateString("text") { reward.postcardText = it }
                helper.updateString("target") { reward.targetAddressee = it }
            }
        }

        if (helper.changed) syncObject(reward, false)
    }
}
