package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.ContactQuests;
import com.flechazo.contact.common.block.MailboxBlock;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;
import java.util.UUID;

@Mixin(MailboxBlock.class)
public class MailboxBlockMixin {

    @Redirect(
            method = "use",
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
            TeamManager manager = FTBTeamsAPI.api().getManager();
            if (manager != null) {
                return manager.arePlayersInSameTeam(mailboxID, playerUUID);
            }
        } catch (Exception e) {
            ContactQuests.error("Error checking team permissions for mailbox", e);
            return false;
        }

        return false;
    }
}