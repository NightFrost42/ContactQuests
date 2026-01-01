package com.creeperyang.contactquests.command

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.data.CollectionSavedData
import com.creeperyang.contactquests.registry.ModItems
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

object ModCommands {

    private val NPC_NAME_SUGGESTION_PROVIDER = SuggestionProvider<CommandSourceStack> { context, builder ->
        val level = context.source.level
        val data = CollectionSavedData.get(level)
        val names = data.getStoredNpcNames()
        SharedSuggestionProvider.suggest(names, builder)
    }

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        val dispatcher = event.dispatcher

        dispatcher.register(
            Commands.literal("contactquests")
                .then(
                    Commands.literal("get_binder_internal")
                        .executes(this::giveSlefBinder)
                )
                .then(
                    Commands.literal("admin")
                        .then(
                            Commands.literal("givebinder")
                                .then(
                                    Commands.argument("target", EntityArgument.player())
                                        .executes(this::giveBinder)
                                )
                        )
                        .then(
                            Commands.literal("removedata")
                                .then(
                                    Commands.argument("npc", StringArgumentType.string())
                                        .suggests(NPC_NAME_SUGGESTION_PROVIDER)
                                        .executes(this::removeData)
                                )
                        )
                        .then(
                            Commands.literal("setcount")
                                .then(
                                    Commands.argument("npc", StringArgumentType.string())
                                        .suggests(NPC_NAME_SUGGESTION_PROVIDER)
                                        .then(
                                            Commands.argument("count", IntegerArgumentType.integer(0))
                                                .executes(this::setCount)
                                        )
                                )
                        )
                ).requires { source -> source.hasPermission(2) }
        )
    }

    private fun giveSlefBinder(context: CommandContext<CommandSourceStack>): Int {
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

    private fun giveBinder(context: CommandContext<CommandSourceStack>): Int {
        try {
            val targetPlayer = EntityArgument.getPlayer(context, "target")
            val itemStack = ItemStack(ModItems.TEAM_BINDING_CARD.get())

            if (!targetPlayer.inventory.add(itemStack)) {
                targetPlayer.drop(itemStack, false)
            }

            context.source.sendSuccess(
                { Component.translatable("contactquests.command.give_binder.success", targetPlayer.displayName) },
                true
            )
            return Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("Error executing givebinder command", e)
            return 0
        }
    }

    private fun removeData(context: CommandContext<CommandSourceStack>): Int {
        try {
            val level = context.source.level
            val npcName = StringArgumentType.getString(context, "npc")
            val data = CollectionSavedData.get(level)

            if (data.getStoredNpcNames().contains(npcName)) {
                data.removeData(npcName)
                context.source.sendSuccess(
                    { Component.translatable("contactquests.command.remove_data.success", npcName) },
                    true
                )
            } else {
                context.source.sendFailure(Component.translatable("contactquests.command.remove_data.failure", npcName))
            }

            return Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("Error executing removedata command", e)
            return 0
        }
    }

    private fun setCount(context: CommandContext<CommandSourceStack>): Int {
        try {
            val level = context.source.level
            val npcName = StringArgumentType.getString(context, "npc")
            val count = IntegerArgumentType.getInteger(context, "count")

            val data = CollectionSavedData.get(level)

            data.setTriggerCount(npcName, count)

            context.source.sendSuccess(
                { Component.translatable("contactquests.command.set_count.success", npcName, count) },
                true
            )
            return Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("Error executing setcount command", e)
            return 0
        }
    }
}