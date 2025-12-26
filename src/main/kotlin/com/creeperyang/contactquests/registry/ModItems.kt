package com.creeperyang.contactquests.registry

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.item.TeamBindingItem
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

object ModItems {
    val ITEMS: DeferredRegister<Item> = DeferredRegister.create(Registries.ITEM, ContactQuests.ID)

    val TEAM_BINDING_CARD: DeferredHolder<Item, TeamBindingItem> = ITEMS.register("team_binding_card") { ->
        TeamBindingItem(Item.Properties().stacksTo(1))
    }

    fun register(eventBus: IEventBus) {
        ITEMS.register(eventBus)
    }
}