package com.creeperyang.contactquests.registry

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.item.TeamBindingItem
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject

object ModItems {
    val ITEMS: DeferredRegister<Item> = DeferredRegister.create(Registries.ITEM, ContactQuests.ID)

    val TEAM_BINDING_CARD: RegistryObject<TeamBindingItem> = ITEMS.register("team_binding_card") {
        TeamBindingItem(Item.Properties().stacksTo(1))
    }

    fun register(eventBus: IEventBus) {
        ITEMS.register(eventBus)
    }
}