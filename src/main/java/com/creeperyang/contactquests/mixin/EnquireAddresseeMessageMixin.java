package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.ContactQuests;
import com.creeperyang.contactquests.data.DataManager;
import com.flechazo.contact.common.item.LetterItem;
import com.flechazo.contact.common.item.ParcelItem;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.common.item.RedPacketItem;
import com.flechazo.contact.common.screenhandler.PostboxScreenHandler;
import com.flechazo.contact.common.storage.IMailboxDataProvider;
import com.flechazo.contact.network.ActionMessage;
import com.flechazo.contact.network.EnquireAddresseeMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.*;

@Mixin(EnquireAddresseeMessage.class)
public class EnquireAddresseeMessageMixin {

    @Inject(method = "handleSendMail", at = @At("HEAD"), cancellable = true, remap = false)
    private void onHandleSendMail(ServerPlayer player, IMailboxDataProvider data, String recipientName, int deliveryTicks, CallbackInfo ci) {
        if (!(player.containerMenu instanceof PostboxScreenHandler container)) {
            return;
        }

        ItemStack stackInSlot = container.parcel.getItem(0);
        if (stackInSlot.isEmpty()) return;


        String finalRecipientKey = findKeyIgnoreCase(recipientName);

        if (finalRecipientKey == null) {
            return;
        }

        boolean taskMatched = false;

        if (stackInSlot.getItem() instanceof PostcardItem) {
            if (DataManager.postcardReceiver.containsKey(finalRecipientKey)) {
                DataManager.INSTANCE.matchPostcardTaskItem(player, stackInSlot, finalRecipientKey);
                taskMatched = true;
            }
        }
        else if (stackInSlot.getItem() instanceof RedPacketItem) {
            if (DataManager.redPacketReceiver.containsKey(finalRecipientKey)) {
                DataManager.INSTANCE.matchRedPacketTaskItem(player, stackInSlot, finalRecipientKey);
                taskMatched = true;
            }
        }
        else if (stackInSlot.getItem() instanceof ParcelItem || stackInSlot.getItem() instanceof LetterItem) {
            if (DataManager.parcelReceiver.containsKey(finalRecipientKey)) {
                DataManager.INSTANCE.matchParcelTaskItem(player, stackInSlot, finalRecipientKey);
                taskMatched = true;
            }
        } else {
            ContactQuests.debug("未知的物品类型 " + stackInSlot.getItem().getClass().getName());
        }

        if (taskMatched) {
            container.parcel.setItem(0, ItemStack.EMPTY);
            ActionMessage.create(1).sendTo(player);
            ci.cancel();
        }
    }

    @SuppressWarnings("AddedMixinMembersNamePattern")
    @Unique
    private String findKeyIgnoreCase(String inputName) {
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(DataManager.parcelReceiver.keySet());
        allKeys.addAll(DataManager.redPacketReceiver.keySet());
        allKeys.addAll(DataManager.postcardReceiver.keySet());

        for (String key : allKeys) {
            if (key.equalsIgnoreCase(inputName)) {
                return key;
            }
        }
        return null;
    }

    @SuppressWarnings("InvalidInjectorMethodSignature")
    @Inject(
            method = "handleNormalEnquiry",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/flechazo/contact/network/EnquireAddresseeMessage;shouldSend:Z",
                    opcode = Opcodes.GETFIELD),
            locals = LocalCapture.CAPTURE_FAILHARD, remap = false
    )
    private void injectCustomTargetsNames(
            ServerPlayer player,
            IMailboxDataProvider data,
            String lowerIn,
            CallbackInfo ci,
            List<String> names,
            List<Integer> ticks
    ) {
        Map<String, Integer> customTargets = DataManager.INSTANCE.getAvailableTargets(player);
        List<Map.Entry<String, Integer>> matches = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : customTargets.entrySet()) {
            String targetName = entry.getKey();

            if (targetName.toLowerCase(Locale.ROOT).startsWith(lowerIn) && !names.contains(targetName)) {
                matches.add(entry);
            }
        }

        matches.sort((e1, e2) -> {
            String name1 = e1.getKey();
            String name2 = e2.getKey();
            String lower1 = name1.toLowerCase(Locale.ROOT);
            String lower2 = name2.toLowerCase(Locale.ROOT);

            boolean exact1 = lower1.equals(lowerIn);
            boolean exact2 = lower2.equals(lowerIn);
            if (exact1 && !exact2) return -1;
            if (!exact1 && exact2) return 1;

            int lenCompare = Integer.compare(name1.length(), name2.length());
            if (lenCompare != 0) return lenCompare;

            return name1.compareTo(name2);
        });

        for (int i = matches.size() - 1; i >= 0; i--) {
            Map.Entry<String, Integer> entry = matches.get(i);
            //noinspection SequencedCollectionMethodCanBeUsed
            names.add(0, entry.getKey());
            //noinspection SequencedCollectionMethodCanBeUsed
            ticks.add(0, entry.getValue());
        }

        while (names.size() > 4) {
            //noinspection SequencedCollectionMethodCanBeUsed
            names.remove(names.size() - 1);
            //noinspection SequencedCollectionMethodCanBeUsed
            ticks.remove(ticks.size() - 1);
        }
    }
}