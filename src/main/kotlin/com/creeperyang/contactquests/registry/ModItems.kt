package com.creeperyang.contactquests.registry

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.item.TeamBindingItem
import net.minecraftforge.registries.ForgeRegistries
import net.minecraft.world.item.Item
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.registries.RegistryObject
import net.minecraftforge.registries.DeferredRegister

object ModItems {
    val ITEMS: DeferredRegister<Item> = DeferredRegister.create(ForgeRegistries.ITEMS, ContactQuests.ID)

    val TEAM_BINDING_CARD: RegistryObject<TeamBindingItem> = ITEMS.register("team_binding_card") { ->
        TeamBindingItem(Item.Properties().stacksTo(1))
    }

    fun register(eventBus: IEventBus) {
        ITEMS.register(eventBus)
    }
}