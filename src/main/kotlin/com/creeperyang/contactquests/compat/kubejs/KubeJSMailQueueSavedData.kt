package com.creeperyang.contactquests.compat.kubejs

import com.creeperyang.contactquests.data.DataManager
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import java.util.*

class KubeJSMailQueueSavedData : SavedData() {
    private val pendingMails = mutableListOf<PendingMail>()

    data class PendingMail(
        val playerUUID: UUID,
        val recipient: String,
        val items: List<ItemStack>,
        var ticksLeft: Int,
        val isPostcard: Boolean,
        val isRedPacket: Boolean
    )

    companion object {
        private const val TAG_MAILS = "PendingMails"

        fun get(level: ServerLevel): KubeJSMailQueueSavedData {
            return level.server.overworld().dataStorage.computeIfAbsent(
                { tag -> load(tag) },
                { KubeJSMailQueueSavedData() },
                "contactquests_kubejs_mail_queue"
            )
        }

        private fun load(tag: CompoundTag): KubeJSMailQueueSavedData {
            val data = KubeJSMailQueueSavedData()
            val list = tag.getList(TAG_MAILS, Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                val mailTag = list.getCompound(i)
                val itemsList = mutableListOf<ItemStack>()
                val itemsTag = mailTag.getList("Items", Tag.TAG_COMPOUND.toInt())
                for (j in 0 until itemsTag.size) {
                    itemsList.add(ItemStack.of(itemsTag.getCompound(j)))
                }

                data.pendingMails.add(
                    PendingMail(
                        playerUUID = mailTag.getUUID("PlayerUUID"),
                        recipient = mailTag.getString("Recipient"),
                        items = itemsList,
                        ticksLeft = mailTag.getInt("TicksLeft"),
                        isPostcard = mailTag.getBoolean("IsPostcard"),
                        isRedPacket = mailTag.getBoolean("IsRedPacket")
                    )
                )
            }
            return data
        }
    }

    override fun save(tag: CompoundTag): CompoundTag {
        val list = ListTag()
        pendingMails.forEach { mail ->
            val mailTag = CompoundTag()
            mailTag.putUUID("PlayerUUID", mail.playerUUID)
            mailTag.putString("Recipient", mail.recipient)
            mailTag.putInt("TicksLeft", mail.ticksLeft)
            mailTag.putBoolean("IsPostcard", mail.isPostcard)
            mailTag.putBoolean("IsRedPacket", mail.isRedPacket)

            val itemsTag = ListTag()
            mail.items.forEach { item ->
                itemsTag.add(item.save(CompoundTag()))
            }
            mailTag.put("Items", itemsTag)
            list.add(mailTag)
        }
        tag.put(TAG_MAILS, list)
        return tag
    }

    fun addMail(
        player: Player,
        recipient: String,
        items: List<ItemStack>,
        delay: Int,
        isPostcard: Boolean,
        isRedPacket: Boolean
    ) {
        pendingMails.add(PendingMail(player.uuid, recipient, items, delay, isPostcard, isRedPacket))
        setDirty()
    }

    fun tick(level: ServerLevel) {
        if (pendingMails.isEmpty()) return

        val iterator = pendingMails.iterator()
        while (iterator.hasNext()) {
            val mail = iterator.next()
            mail.ticksLeft--

            if (mail.ticksLeft <= 0) {
                val player = level.server.playerList.getPlayer(mail.playerUUID)
                if (player != null) {
                    if (mail.isPostcard) {
                        DataManager.processPostcardDelivery(
                            player,
                            mail.items.firstOrNull() ?: ItemStack.EMPTY,
                            mail.recipient
                        )
                    } else if (mail.isRedPacket) {
                        DataManager.processRedPacketDelivery(
                            player,
                            mail.items.firstOrNull() ?: ItemStack.EMPTY,
                            mail.recipient
                        )
                    } else {
                        DataManager.processParcelDelivery(player, mail.items, mail.recipient)
                    }
                }
                iterator.remove()
                setDirty()
            }
        }
        setDirty()
    }
}