package com.creeperyang.contactquests.command

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.config.NpcConfigManager
import com.creeperyang.contactquests.data.CollectionSavedData
import com.creeperyang.contactquests.data.DataManager
import com.creeperyang.contactquests.network.OpenQuestMessage
import com.creeperyang.contactquests.registry.ModItems
import com.creeperyang.contactquests.bridge.ITeamDataExtension
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import dev.ftb.mods.ftbquests.quest.ServerQuestFile
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.event.RegisterCommandsEvent
import com.creeperyang.contactquests.network.NetworkHandler

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
                        .executes(this::giveSelfBinder)
                )
                .then(
                    Commands.literal("open")
                    .then(
                        Commands.argument("id", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val idStr = StringArgumentType.getString(ctx, "id")
                                try {
                                    val id = java.lang.Long.parseUnsignedLong(idStr, 16)
                                    val player = ctx.source.playerOrException

                                    NetworkHandler.sendToPlayer(OpenQuestMessage(id), player)
                                    1
                                } catch (e: Exception) {
                                    0
                                }
                            }
                    )
                )
                .then(
                    Commands.literal("admin")
                        .requires { source -> source.hasPermission(2) }
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
                            Commands.literal("removedRedundantNPC")
                                .executes(this::pruneNpcs)
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
                                                val team = resolveTeam(target)
                                                if (team == null) {
                                                    ctx.source.sendFailure(Component.translatable("contactquests.command.error.no_team"))
                                                    return@executes 0
                                                }
                                                val teamData = resolveTeamData(target) ?: return@executes 0
                                                val tags: Collection<String?> = teamData.`contactQuests$getTags`()
                                                ctx.source.sendSuccess({
                                                    Component.translatable(
                                                        "contactquests.command.tags.view", target.name,
                                                        Component.literal(tags.joinToString(", "))
                                                            .withStyle(ChatFormatting.GREEN)
                                                    )
                                                }, false)
                                                1
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
                                                    val teamData = resolveTeamData(target) ?: return@executes 0

                                                    if (teamData.`contactQuests$unlockTag`(tag)) {
                                                        ctx.source.sendSuccess({
                                                            Component.translatable(
                                                                "contactquests.command.tags.added",
                                                                tag
                                                            ).withStyle(ChatFormatting.GREEN)
                                                        }, true)
                                                        1
                                                    } else {
                                                        ctx.source.sendFailure(Component.translatable("contactquests.command.error.tag_exists"))
                                                        0
                                                    }
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
                                                    } catch (_: Exception) {
                                                        null
                                                    }
                                                    target?.let(::resolveTeamData)
                                                        ?.`contactQuests$getTags`()
                                                        ?.forEach { builder.suggest(it) }
                                                    builder.buildFuture()
                                                }
                                                .executes { ctx ->
                                                    val target = EntityArgument.getPlayer(ctx, "target")
                                                    val tag = StringArgumentType.getString(ctx, "tag")
                                                    val teamData = resolveTeamData(target) ?: return@executes 0

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
                                                }
                                        )
                                    )
                                )
                        )
                )
        )
    }

    private fun resolveTeam(target: net.minecraft.server.level.ServerPlayer) =
        FTBTeamsAPI.api().manager.getTeamForPlayer(target).orElse(null)

    private fun resolveTeamData(target: net.minecraft.server.level.ServerPlayer): ITeamDataExtension? {
        val team = resolveTeam(target) ?: return null
        return ServerQuestFile.INSTANCE.getNullableTeamData(team.id) as? ITeamDataExtension
    }

    private fun giveBinderToPlayer(player: net.minecraft.server.level.ServerPlayer) {
        val itemStack = ItemStack(ModItems.TEAM_BINDING_CARD.get())
        if (!player.inventory.add(itemStack)) {
            player.drop(itemStack, false)
        }
    }

    /** 为指令执行者本人发放一张团队绑定卡。 */
    private fun giveSelfBinder(context: CommandContext<CommandSourceStack>): Int {
        return try {
            val player = context.source.playerOrException
            giveBinderToPlayer(player)
            context.source.sendSuccess(
                { Component.translatable("contactquests.command.give_binder") },
                false
            )
            Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("执行 giveSelfBinder 指令失败", e)
            0
        }
    }

    /** 为指定目标玩家发放一张团队绑定卡。 */
    private fun giveBinder(context: CommandContext<CommandSourceStack>): Int {
        return try {
            val targetPlayer = EntityArgument.getPlayer(context, "target")
            giveBinderToPlayer(targetPlayer)
            context.source.sendSuccess(
                { Component.translatable("contactquests.command.give_binder.success", targetPlayer.displayName) },
                true
            )
            Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("执行 givebinder 指令失败", e)
            0
        }
    }

    private fun removeDataAll(context: CommandContext<CommandSourceStack>): Int {
        return try {
            val level = context.source.level
            val npcName = StringArgumentType.getString(context, "npc")
            CollectionSavedData.get(level).removeDataAll(npcName)
            context.source.sendSuccess({
                Component.translatable("contactquests.command.remove_data.success_all", npcName)
            }, true)
            Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("执行 removedata 指令失败", e)
            0
        }
    }

    private fun removeDataTarget(context: CommandContext<CommandSourceStack>): Int {
        return try {
            val level = context.source.level
            val npcName = StringArgumentType.getString(context, "npc")
            val target = EntityArgument.getPlayer(context, "target")
            CollectionSavedData.get(level).removeData(target.uuid, npcName)
            context.source.sendSuccess({
                Component.translatable(
                    "contactquests.command.remove_data.success_target",
                    npcName,
                    target.displayName
                )
            }, true)
            Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("执行 removedata target 指令失败", e)
            0
        }
    }

    private fun setCountSelf(context: CommandContext<CommandSourceStack>): Int =
        setCountForPlayer(context, targeted = false) { context.source.playerOrException }

    private fun setCountTarget(context: CommandContext<CommandSourceStack>): Int =
        setCountForPlayer(context, targeted = true) { EntityArgument.getPlayer(context, "target") }

    private fun setCountForPlayer(
        context: CommandContext<CommandSourceStack>,
        targeted: Boolean,
        playerResolver: () -> ServerPlayer
    ): Int {
        return try {
            val player = playerResolver()
            val npcName = StringArgumentType.getString(context, "npc")
            val count = IntegerArgumentType.getInteger(context, "count")
            CollectionSavedData.get(context.source.level).setTriggerCount(player.uuid, npcName, count)
            context.source.sendSuccess({
                if (targeted) {
                    Component.translatable("contactquests.command.set_count.success_target", player.displayName, npcName, count)
                } else {
                    Component.translatable("contactquests.command.set_count.success", npcName, count)
                }
            }, true)
            Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("执行 setcount 指令失败", e)
            0
        }
    }

    private fun getCountSelf(context: CommandContext<CommandSourceStack>): Int =
        getCountForPlayer(context, targeted = false) { context.source.playerOrException }

    private fun getCountTarget(context: CommandContext<CommandSourceStack>): Int =
        getCountForPlayer(context, targeted = true) { EntityArgument.getPlayer(context, "target") }

    private fun getCountForPlayer(
        context: CommandContext<CommandSourceStack>,
        targeted: Boolean,
        playerResolver: () -> ServerPlayer
    ): Int {
        return try {
            val player = playerResolver()
            val npcName = StringArgumentType.getString(context, "npc")
            val count = CollectionSavedData.get(context.source.level).getTriggerCount(player.uuid, npcName)
            context.source.sendSuccess({
                if (targeted) {
                    Component.translatable("contactquests.command.get_count.success_target", player.displayName, npcName, count)
                } else {
                    Component.translatable("contactquests.command.get_count.success", npcName, count)
                }
            }, true)
            Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("执行 getcount 指令失败", e)
            0
        }
    }

    /** 清理已不再被任何任务收件人或发件人配置引用的孤立 NPC 数据。 */
    private fun pruneNpcs(context: CommandContext<CommandSourceStack>): Int {
        return try {
            val activeNpcs = mutableSetOf<String>().apply {
                addAll(DataManager.parcelReceiver.keys)
                addAll(DataManager.redPacketReceiver.keys)
                addAll(DataManager.postcardReceiver.keys)
                addAll(DataManager.rewardSenders)
            }
            val removedCount = NpcConfigManager.removeUnusedNpcs(activeNpcs)
            context.source.sendSuccess({
                Component.translatable("contactquests.command.prune.success", removedCount)
                    .withStyle(ChatFormatting.GREEN)
            }, true)
            Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            ContactQuests.LOGGER.error("执行 prune 指令失败", e)
            context.source.sendFailure(Component.translatable("contactquests.command.prune.failure"))
            0
        }
    }
}
