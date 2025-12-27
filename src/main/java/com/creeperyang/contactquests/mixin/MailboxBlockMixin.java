package com.creeperyang.contactquests.mixin;

import com.flechazo.contact.common.block.MailboxBlock;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Mixin(MailboxBlock.class)
public class MailboxBlockMixin {

    @Redirect(
            method = "useWithoutItem",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Objects;equals(Ljava/lang/Object;Ljava/lang/Object;)Z"
            )
    )
    private boolean interceptOwnerCheck(Object ownerUuidObj, Object playerUuidObj) {
        if (!(ownerUuidObj instanceof UUID mailboxID) || !(playerUuidObj instanceof UUID playerUUID)) {
            return Objects.equals(ownerUuidObj, playerUuidObj);
        }

        if (mailboxID.equals(playerUUID)) {
            return true;
        }

        try {
            Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayerID(playerUUID);

            if (teamOpt.isPresent()) {
                UUID currentPlayerTeamID = teamOpt.get().getId();
                if (mailboxID.equals(currentPlayerTeamID)) {
                    return true;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return false;
    }
}