package com.creeperyang.contactquests.task


import com.creeperyang.contactquests.ContactQuests
import dev.ftb.mods.ftblibrary.icon.Icon
import dev.ftb.mods.ftbquests.quest.task.TaskType
import dev.ftb.mods.ftbquests.quest.task.TaskTypes
import net.minecraft.resources.ResourceLocation

class TaskRegistry : TaskTypes {
    companion object {
        @JvmField
        val PARCEL: TaskType = TaskTypes.register(
            ResourceLocation.fromNamespaceAndPath(ContactQuests.ID, "parcel_task"),
            ::ParcelTask
        ) { Icon.getIcon("contact:item/parcel") }
//    val POSTCARD_TASK: TaskType = TaskTypes.register(
//        ResourceLocation.fromNamespaceAndPath(ContactQuests.ID, "postcard_task"),
//        ::PostcardTask
//    ) { Icon.getIcon("contact:item/postcard") }

        fun init() {
        }
    }
}