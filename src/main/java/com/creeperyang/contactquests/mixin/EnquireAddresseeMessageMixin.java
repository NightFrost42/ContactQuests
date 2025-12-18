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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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

                    DataManager.INSTANCE.matchTaskItem(player, contents);

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

    @Unique
    private final ThreadLocal<Integer> contactQuests$insertedCount = ThreadLocal.withInitial(() -> 0);

    @Inject(
            method = "handleNormalEnquiry",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;isEmpty()Z"
            )
    )
    private void injectCustomTargetsNames(
            ServerPlayer player,
            IMailboxDataProvider data,
            String lowerIn,
            CallbackInfo ci,
            @Local(name = "names") List<String> names
    ) {
        contactQuests$insertedCount.set(0);

        Set<String> customTargets = DataManager.parcelReceiver.keySet();
        int count = 0;

        for (String target : customTargets) {
            if (count >= 4) break;

            if (target.toLowerCase(Locale.ROOT).startsWith(lowerIn) && !names.contains(target)) {
                //noinspection SequencedCollectionMethodCanBeUsed
                names.add(0, target);
                count++;
            }
        }

        while (names.size() > 4) {
            //noinspection SequencedCollectionMethodCanBeUsed
            names.remove(names.size() - 1);
        }

        contactQuests$insertedCount.set(count);
    }


    @ModifyVariable(
            method = "handleNormalEnquiry",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;isEmpty()Z"
            ),
            ordinal = 1
    )
    private List<Integer> injectCustomTargetsTicks(List<Integer> ticks) {
        int count = contactQuests$insertedCount.get();

        if (count > 0) {
            for (int i = 0; i < count; i++) {
                //noinspection SequencedCollectionMethodCanBeUsed
                ticks.add(0, 0);
            }

            while (ticks.size() > 4) {
                //noinspection SequencedCollectionMethodCanBeUsed
                ticks.remove(ticks.size() - 1);
            }

            contactQuests$insertedCount.set(0);
        }
        
        contactQuests$insertedCount.remove();

        return ticks;
    }
}
