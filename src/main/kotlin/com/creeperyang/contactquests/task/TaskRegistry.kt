package com.creeperyang.contactquests.task


import com.creeperyang.contactquests.ContactQuests
import dev.ftb.mods.ftblibrary.icon.Icon
import dev.ftb.mods.ftbquests.quest.task.TaskType
import dev.ftb.mods.ftbquests.quest.task.TaskTypes
import net.minecraft.resources.ResourceLocation

object TaskRegistry  {
    @JvmField
    val PARCEL: TaskType = TaskTypes.register(
        ResourceLocation.fromNamespaceAndPath(ContactQuests.ID, "parcel_task"),
        ::ParcelTask
    ) { Icon.getIcon("contact:item/parcel") }

    @JvmField
    val RED_PACKET: TaskType = TaskTypes.register(
        ResourceLocation.fromNamespaceAndPath(ContactQuests.ID, "red_packet_task"),
        ::RedPacketTask
    ) { Icon.getIcon("contact:item/red_packet") }

    fun init() {
        //引入
    }
}