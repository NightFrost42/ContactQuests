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

        fun init() {
            //引入
        }
}