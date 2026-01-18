package com.creeperyang.contactquests.command

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.config.NpcConfigManager
import com.creeperyang.contactquests.data.CollectionSavedData
import com.creeperyang.contactquests.network.OpenQuestMessage
import com.creeperyang.contactquests.registry.ModItems
import com.creeperyang.contactquests.utils.ITeamDataExtension
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.network.PacketDistributor
import java.lang.Long
import java.util.concurrent.CompletableFuture
import kotlin.Exception
import kotlin.Int
import kotlin.String

object ModCommands {

    private val NPC_NAME_SUGGESTION_PROVIDER = SuggestionProvider<CommandSourceStack> { context, builder ->
        val level = context.source.level
        val data = CollectionSavedData.get(level)
        val names = data.getStoredNpcNames() + NpcConfigManager.getAllNpcNames()

        val quotedNames = names.map { "\"$it\"" }

        SharedSuggestionProvider.suggest(quotedNames, builder)
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
                    Commands.literal("open")
                    .then(
                        Commands.argument("id", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val idStr = StringArgumentType.getString(ctx, "id")
                                try {
                                    val id = Long.parseUnsignedLong(idStr, 16)
                                    val player = ctx.source.playerOrException

                                    PacketDistributor.sendToPlayer(player, OpenQuestMessage(id))
                                    1
                                } catch (e: Exception) {
                                    0
                                }
                            }
                    )
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
                                        .executes(this::removeDataAll)
                                        .then(
                                            Commands.argument("target", EntityArgument.player())
                                                .executes(this::removeDataTarget)
                                        )
                                )
                        )
                        .then(
                            Commands.literal("setcount")
                                .then(
                                    Commands.argument("npc", StringArgumentType.string())
                                        .suggests(NPC_NAME_SUGGESTION_PROVIDER)
                                        .then(
                                            Commands.argument("count", IntegerArgumentType.integer(0))
                                                .executes(this::setCountSelf)
                                                .then(
                                                    Commands.argument("target", EntityArgument.player())
                                                        .executes(this::setCountTarget)
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("getcount")
                                .then(
                                    Commands.argument("npc", StringArgumentType.string())
                                        .suggests(NPC_NAME_SUGGESTION_PROVIDER)
                                        .executes(this::getCountSelf)
                                        .then(
                                            Commands.argument("target", EntityArgument.player())
                                                .executes(this::getCountTarget)
                                        )
                                )
                        )
                        .then(
                            Commands.literal("tags")
                                .requires { it.hasPermission(2) }
                                .then(
                                    Commands.literal("view")
                                    .then(
                                        Commands.argument("target", EntityArgument.player())
                                            .executes { ctx ->
                                                val target = EntityArgument.getPlayer(ctx, "target")
                                                val team =
                                                    FTBTeamsAPI.api().manager.getTeamForPlayer(
                                                        target
                                                    ).orElse(null)
                                                if (team == null) {
                                                    ctx.source.sendFailure(Component.translatable("contactquests.command.error.no_team"))
                                                    return@executes 0
                                                }
                                                val teamData = ServerQuestFile.INSTANCE.getNullableTeamData(team.id)
                                                if (teamData is ITeamDataExtension) {
                                                    val tags: Collection<String?> = teamData.`contactQuests$getTags`()
                                                    ctx.source.sendSuccess({
                                                        Component.translatable(
                                                            "contactquests.command.tags.view", target.name,
                                                            Component.literal(tags.joinToString(", "))
                                                                .withStyle(ChatFormatting.GREEN)
                                                        )
                                                    }, false)
                                                    1
                                                } else {
                                                    0
                                                }
                                            }
                                    )
                                )
                                .then(
                                    Commands.literal("add")
                                    .then(
                                        Commands.argument("target", EntityArgument.player())
                                        .then(
                                            Commands.argument("tag", StringArgumentType.string())
                                                .executes { ctx ->
                                                    val target = EntityArgument.getPlayer(ctx, "target")
                                                    val tag = StringArgumentType.getString(ctx, "tag")
                                                    val team =
                                                        FTBTeamsAPI.api().manager.getTeamForPlayer(
                                                            target
                                                        ).orElse(null)
                                                    val teamData = ServerQuestFile.INSTANCE.getNullableTeamData(
                                                        team?.id ?: return@executes 0
                                                    )

                                                    if (teamData is ITeamDataExtension) {
                                                        if (teamData.`contactQuests$unlockTag`(tag)) {
                                                            ctx.source.sendSuccess({
                                                                Component.translatable(
                                                                    "contactquests.command.tags.added",
                                                                    tag
                                                                ).withStyle(
                                                                    ChatFormatting.GREEN
                                                                )
                                                            }, true)
                                                            1
                                                        } else {
                                                            ctx.source.sendFailure(Component.translatable("contactquests.command.error.tag_exists"))
                                                            0
                                                        }
                                                    } else 0
                                                }
                                        )
                                    )
                                )
                                .then(
                                    Commands.literal("remove")
                                    .then(
                                        Commands.argument("target", EntityArgument.player())
                                        .then(
                                            Commands.argument("tag", StringArgumentType.string())
                                                .suggests { ctx, builder ->
                                                    val target = try {
                                                        EntityArgument.getPlayer(ctx, "target")
                                                    } catch (e: Exception) {
                                                        null
                                                    }
                                                    if (target != null) {
                                                        val team =
                                                            FTBTeamsAPI.api().manager.getTeamForPlayer(
                                                                target
                                                            ).orElse(null)
                                                        val teamData = ServerQuestFile.INSTANCE.getNullableTeamData(
                                                            team?.id
                                                                ?: return@suggests builder.build() as CompletableFuture<Suggestions?>?
                                                        )
                                                        if (teamData is ITeamDataExtension) {
                                                            teamData.`contactQuests$getTags`()
                                                                .forEach { builder.suggest(it) }
                                                        }
                                                    }
                                                    builder.buildFuture()
                                                }
                                                .executes { ctx ->
                                                    val target = EntityArgument.getPlayer(ctx, "target")
                                                    val tag = StringArgumentType.getString(ctx, "tag")
                                                    val team =
                                                        FTBTeamsAPI.api().manager.getTeamForPlayer(
                                                            target
                                                        ).orElse(null)
                                                    val teamData = ServerQuestFile.INSTANCE.getNullableTeamData(
                                                        team?.id ?: return@executes 0
                                                    )

                                                    if (teamData is ITeamDataExtension) {
                                                        if (teamData.`contactQuests$removeTag`(tag)) {
                                                            ctx.source.sendSuccess({
                                                                Component.translatable(
                                                                    "contactquests.command.tags.removed",
                                                                    tag
                                                                ).withStyle(ChatFormatting.YELLOW)
                                                            }, true)
                                                            1
                                                        } else {
                                                            ctx.source.sendFailure(Component.translatable("contactquests.command.error.tag_not_found"))
                                                            0
                                                        }
                                                    } else 0
                                                }
                                        )
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

    private fun removeDataAll(context: CommandContext<CommandSourceStack>): Int {
        try {
            val level = context.source.level
            val npcName = StringArgumentType.getString(context, "npc")
            val data = CollectionSavedData.get(level)

            data.removeDataAll(npcName)
            context.source.sendSuccess({
                Component.translatable(
                    "contactquests.command.remove_data.success_all",
                    npcName
                )
            }, true)
            return Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("Error executing removedata command", e)
            return 0
        }
    }

    private fun removeDataTarget(context: CommandContext<CommandSourceStack>): Int {
        try {
            val level = context.source.level
            val npcName = StringArgumentType.getString(context, "npc")
            val target = EntityArgument.getPlayer(context, "target")
            val data = CollectionSavedData.get(level)

            data.removeData(target.uuid, npcName)
            context.source.sendSuccess({
                Component.translatable(
                    "contactquests.command.remove_data.success_target",
                    npcName,
                    target.displayName
                )
            }, true)
            return Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("Error executing removedata target command", e)
            return 0
        }
    }

    private fun setCountSelf(context: CommandContext<CommandSourceStack>): Int {
        try {
            val player = context.source.playerOrException
            val level = context.source.level
            val npcName = StringArgumentType.getString(context, "npc")
            val count = IntegerArgumentType.getInteger(context, "count")

            val data = CollectionSavedData.get(level)
            data.setTriggerCount(player.uuid, npcName, count)

            context.source.sendSuccess({
                Component.translatable(
                    "contactquests.command.set_count.success",
                    npcName,
                    count
                )
            }, true)
            return Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("Error executing setcount command", e)
            return 0
        }
    }

    private fun setCountTarget(context: CommandContext<CommandSourceStack>): Int {
        try {
            val level = context.source.level
            val target = EntityArgument.getPlayer(context, "target")
            val npcName = StringArgumentType.getString(context, "npc")
            val count = IntegerArgumentType.getInteger(context, "count")

            val data = CollectionSavedData.get(level)
            data.setTriggerCount(target.uuid, npcName, count)

            context.source.sendSuccess({
                Component.translatable(
                    "contactquests.command.set_count.success_target",
                    target.displayName,
                    npcName,
                    count
                )
            }, true)
            return Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("Error executing setcount target command", e)
            return 0
        }
    }

    private fun getCountSelf(context: CommandContext<CommandSourceStack>): Int {
        try {
            val player = context.source.playerOrException
            val level = context.source.level
            val npcName = StringArgumentType.getString(context, "npc")
            val count = IntegerArgumentType.getInteger(context, "count")

            val data = CollectionSavedData.get(level)
            data.getTriggerCount(player.uuid, npcName)

            context.source.sendSuccess({
                Component.translatable(
                    "contactquests.command.get_count.success",
                    npcName,
                    count
                )
            }, true)
            return Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("Error executing setcount command", e)
            return 0
        }
    }

    private fun getCountTarget(context: CommandContext<CommandSourceStack>): Int {
        try {
            val level = context.source.level
            val target = EntityArgument.getPlayer(context, "target")
            val npcName = StringArgumentType.getString(context, "npc")
            val count = IntegerArgumentType.getInteger(context, "count")

            val data = CollectionSavedData.get(level)
            data.getTriggerCount(target.uuid, npcName)

            context.source.sendSuccess({
                Component.translatable(
                    "contactquests.command.get_count.success_target",
                    target.displayName,
                    npcName,
                    count
                )
            }, true)
            return Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("Error executing setcount target command", e)
            return 0
        }
    }
}