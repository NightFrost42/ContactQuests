package com.creeperyang.contactquests.client.renderer

import com.creeperyang.contactquests.ContactQuests
import com.creeperyang.contactquests.registry.ModItems
import com.creeperyang.contactquests.utils.IMailboxTeamAccessor
import com.flechazo.contact.common.tileentity.MailboxBlockEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object MailboxGlobalRenderer {

    private val displayStack by lazy { ItemStack(ModItems.TEAM_BINDING_CARD.get()) }

    private val TRACKED_MAILBOXES: MutableSet<MailboxBlockEntity> = Collections.newSetFromMap(ConcurrentHashMap())

    private const val ICON_HEIGHT = 1.25

    fun track(be: MailboxBlockEntity) {
        TRACKED_MAILBOXES.add(be)
    }

    fun untrack(be: MailboxBlockEntity) {
        TRACKED_MAILBOXES.remove(be)
    }

    @SubscribeEvent
    fun onLoggingOut(event: ClientPlayerNetworkEvent.LoggingOut) {
        TRACKED_MAILBOXES.clear()
        ContactQuests.LOGGER.info("世界退出，已清空所有邮箱追踪缓存。")
    }

    @SubscribeEvent
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        val poseStack = event.poseStack
        val cameraPos = event.camera.position
        val bufferSource = mc.renderBuffers().bufferSource()

        val iterator = TRACKED_MAILBOXES.iterator()
        while (iterator.hasNext()) {
            val be = iterator.next()

            if (be.isRemoved || be.level != level) {
                iterator.remove()
                continue
            }

            if (be.blockPos.distToCenterSqr(player.position()) > 64 * 64) continue

            if (level.getBlockEntity(be.blockPos) != be) {
                iterator.remove()
                continue
            }

            if (be !is IMailboxTeamAccessor) continue
            val teamId = be.`contactquests$getTeamId`()

            if (teamId == null) {
                iterator.remove()
                continue
            }

            poseStack.pushPose()
            val pos = Vec3.atLowerCornerOf(be.blockPos).subtract(cameraPos)
            poseStack.translate(pos.x, pos.y, pos.z)

            renderIcon(poseStack, bufferSource)

            poseStack.popPose()
        }
    }

    private fun renderIcon(poseStack: PoseStack, bufferSource: MultiBufferSource) {
        poseStack.pushPose()
        poseStack.translate(0.5, ICON_HEIGHT, 0.5)
        val angle = (System.currentTimeMillis() / 20.0) % 360.0
        poseStack.mulPose(Axis.YP.rotationDegrees(angle.toFloat()))
        poseStack.scale(1.0f, 1.0f, 1.0f)

        Minecraft.getInstance().itemRenderer.renderStatic(
            displayStack,
            ItemDisplayContext.GROUND,
            0xF000F0,
            0,
            poseStack,
            bufferSource,
            null,
            0
        )
        poseStack.popPose()
    }
}