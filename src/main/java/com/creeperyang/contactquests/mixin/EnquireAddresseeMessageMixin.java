package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.ContactQuests;
import com.creeperyang.contactquests.data.DataManager;
import com.flechazo.contact.common.item.LetterItem;
import com.flechazo.contact.common.item.ParcelItem;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.common.item.RedPacketItem;
import com.flechazo.contact.common.screenhandler.PostboxScreenHandler;
import com.flechazo.contact.common.storage.IMailboxDataProvider;
import com.flechazo.contact.network.ActionS2CMessage;
import com.flechazo.contact.network.EnquireAddresseeMessage;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(EnquireAddresseeMessage.class)
public class EnquireAddresseeMessageMixin {

    @Inject(method = "handleSendMail", at = @At("HEAD"), cancellable = true)
    private void onHandleSendMail(ServerPlayer player, IMailboxDataProvider data, String recipientName, int deliveryTicks, CallbackInfo ci){
        Map<String, Set<Long>> parcelReceiver = DataManager.parcelReceiver;
        if (parcelReceiver.containsKey(recipientName) && player.containerMenu instanceof PostboxScreenHandler container) {

            ItemStack stackInSlot = container.parcel.getItem(0);

            switch (stackInSlot.getItem()) {
                case ParcelItem ignored -> {
                    ItemContainerContents contents = stackInSlot.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);

                    DataManager.INSTANCE.matchTaskItem(player, contents, recipientName);

                    container.parcel.setItem(0, ItemStack.EMPTY);
                    ActionS2CMessage.create(1).sendTo(player);
                    ci.cancel();
                }
                case PostcardItem ignored -> {
                    // TODO: 明信片逻辑
                }
                case LetterItem ignored -> {
                    // TODO: 信件逻辑
                }
                case RedPacketItem ignored -> {
                    // TODO: 红包逻辑
                }
                default -> ContactQuests.warn("Unknown item in postbox: " + stackInSlot);
            }
        }
    }

    @SuppressWarnings("UnresolvedLocalCapture")
    @Inject(
            method = "handleNormalEnquiry",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/flechazo/contact/network/EnquireAddresseeMessage;shouldSend:Z",
                    opcode = Opcodes.GETFIELD)
    )
    private void injectCustomTargetsNames(
            ServerPlayer player,
            IMailboxDataProvider data,
            String lowerIn,
            CallbackInfo ci,
            @Local(ordinal = 0) @Coerce List<String> names,
            @Local(ordinal = 1) @Coerce List<Integer> ticks
    ) {

        Set<String> customTargets = DataManager.INSTANCE.getAvailableTargets(player);
        int count = 0;

        for (String target : customTargets) {
            if (count >= 4) break;

            if (target.toLowerCase(Locale.ROOT).startsWith(lowerIn) && !names.contains(target)) {
                //noinspection SequencedCollectionMethodCanBeUsed
                names.add(0, target);
                //noinspection SequencedCollectionMethodCanBeUsed
                ticks.add(0, DataManager.INSTANCE.getSendTime(target));
                count++;
                ContactQuests.debug("[ContactQuests] Injecting: " + target);
            }
        }

        while (names.size() > 4) {
            //noinspection SequencedCollectionMethodCanBeUsed
            names.remove(names.size() - 1);
            //noinspection SequencedCollectionMethodCanBeUsed
            ticks.remove(ticks.size() - 1);
        }
    }
}
