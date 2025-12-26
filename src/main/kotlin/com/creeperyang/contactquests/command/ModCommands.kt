package com.creeperyang.contactquests.command

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.registry.ModItems
import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent

object ModCommands {

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        val dispatcher = event.dispatcher

        dispatcher.register(
            Commands.literal("contactquests")
                .requires { source -> source.hasPermission(2) }
                .then(
                    Commands.literal("getbinder")
                        .executes(this::giveBinder)
                )
        )
    }

    private fun giveBinder(context: CommandContext<CommandSourceStack>): Int {
        try {
            val player = context.source.playerOrException
            val itemStack = ItemStack(ModItems.TEAM_BINDING_CARD.get())

            if (!player.inventory.add(itemStack)) {
                player.drop(itemStack, false)
            }

            context.source.sendSuccess(
                { Component.translatable("contactquests.command.give_binder") },
                false
            )
            return Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("Error executing command", e)
            return 0
        }
    }
}