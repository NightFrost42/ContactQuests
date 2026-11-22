package com.creeperyang.contactquests.mailbox

import com.creeperyang.contactquests.bridge.IMailboxTeamAccessor
import com.flechazo.contact.common.handler.MailboxManager
import com.flechazo.contact.common.tileentity.MailboxBlockEntity
import com.flechazo.contact.platform.PlatformHelper
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import dev.ftb.mods.ftbteams.api.event.TeamEvent
import net.minecraft.core.BlockPos
import net.minecraft.core.GlobalPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import java.util.UUID

/** 负责维护 Contact 真实邮箱归属数据与客户端可见队伍标记（TEAM_ID）之间的一致性。 */
object TeamMailboxBinding {
    fun init() {
        TeamEvent.DELETED.register { event ->
            val teamId = event.team.id
            val pos = PlatformHelper.getMailboxPos(teamId) ?: return@register
            PlatformHelper.removeMailboxData(pos)
            val level = FTBTeamsAPI.api().manager.server.getLevel(pos.dimension())
            if (level != null && level.hasChunkAt(pos.pos())) {
                updateMarker(level, pos.pos(), null)
                MailboxManager.updateState(level, pos.pos())
            }
            // 已注销队伍下的邮件内容保留在原 UUID 存储中，避免误删历史信件；但新建队伍无法继承认领。
        }
    }

    @JvmStatic
    fun refresh(level: ServerLevel, pos: BlockPos): UUID? {
        val be = level.getBlockEntity(pos) as? IMailboxTeamAccessor ?: return null
        // 世界刚启动时方块实体可能早于 FTB Teams 管理器加载完成，此时需暂留持久化标记等待团队管理器就绪。
        if (!FTBTeamsAPI.api().isManagerLoaded) return be.`contactQuests$getTeamId`()
        val previous = be.`contactQuests$getTeamId`()
        val owner = PlatformHelper.getMailboxOwner(level.dimension(), pos)
        val team = owner?.let { FTBTeamsAPI.api().manager.getTeamByID(it).orElse(null) }
        if (owner != null && owner == previous && (team == null || !team.isValid)) {
            // 若队伍在区块未加载期间被解散，则在区块重新加载时清理孤立坐标绑定与方块标记，仍保留原存储中的邮件。
            PlatformHelper.removeMailboxData(GlobalPos.of(level.dimension(), pos))
            updateMarker(level, pos, null)
            return null
        }
        val effective = team?.takeIf { it.isValid && (!it.isPlayerTeam || previous == it.id) }?.id
        if (be.`contactQuests$getTeamId`() != effective) updateMarker(level, pos, effective)
        return effective
    }

    fun updateMarker(level: Level, pos: BlockPos, id: UUID?) {
        val be = level.getBlockEntity(pos)
        if (be is MailboxBlockEntity && be is IMailboxTeamAccessor) {
            be.`contactQuests$setTeamId`(id)
            be.setChanged()
            level.sendBlockUpdated(pos, be.blockState, be.blockState, 3)
        }
    }
}
