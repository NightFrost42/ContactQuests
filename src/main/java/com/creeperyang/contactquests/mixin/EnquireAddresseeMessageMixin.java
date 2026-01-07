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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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


        String finalRecipientKey = findKeyIgnoreCase(recipientName, player);

        if (finalRecipientKey == null) {
            finalRecipientKey = recipientName;
        }

        boolean taskMatched = false;

        if (stackInSlot.getItem() instanceof PostcardItem) {
            if (DataManager.INSTANCE.matchPostcardTaskItem(player, stackInSlot, finalRecipientKey)) {
                taskMatched = true;
            }
        }
        else if (stackInSlot.getItem() instanceof RedPacketItem) {
            if (DataManager.INSTANCE.matchRedPacketTaskItem(player, stackInSlot, finalRecipientKey)) {
                taskMatched = true;
            }
        }
        else if (stackInSlot.getItem() instanceof ParcelItem || stackInSlot.getItem() instanceof LetterItem) {
            ItemContainerContents contents = stackInSlot.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            if (DataManager.INSTANCE.matchParcelTaskItem(player, stackInSlot, contents, finalRecipientKey)) {
                taskMatched = true;
            }
        } else {
            ContactQuests.debug("未知的物品类型 " + stackInSlot.getItem().getClass().getName());
        }

        if (taskMatched) {
            container.parcel.setItem(0, ItemStack.EMPTY);
            ActionS2CMessage.create(1).sendTo(player);
            ci.cancel();
        }
    }

    @SuppressWarnings("AddedMixinMembersNamePattern")
    @Unique
    private String findKeyIgnoreCase(String inputName, ServerPlayer player) {
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(DataManager.parcelReceiver.keySet());
        allKeys.addAll(DataManager.redPacketReceiver.keySet());
        allKeys.addAll(DataManager.postcardReceiver.keySet());

        allKeys.addAll(DataManager.INSTANCE.getAvailableTargets(player).keySet());

        for (String key : allKeys) {
            if (key.equalsIgnoreCase(inputName)) {
                return key;
            }
        }
        return null;
    }

    @SuppressWarnings("UnresolvedLocalCapture")
    @Inject(
            method = "handleNormalEnquiry",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/flechazo/contact/network/EnquireAddresseeMessage;shouldSend:Z",
                    opcode = Opcodes.GETFIELD),
            remap = false
    )
    private void injectCustomTargetsNames(
            ServerPlayer player,
            IMailboxDataProvider data,
            String lowerIn,
            CallbackInfo ci,
            @Local(ordinal = 0) @Coerce List<String> names,
            @Local(ordinal = 1) @Coerce List<Integer> ticks
    ) {
        List<Map.Entry<String, Integer>> matches = getSortedMatches(player, lowerIn, names);

        insertAndTrim(names, ticks, matches);

        ensureHiddenTarget(player, lowerIn, names, ticks);
    }

    @Unique
    private List<Map.Entry<String, Integer>> getSortedMatches(ServerPlayer player, String lowerIn, List<String> currentNames) {
        Map<String, Integer> customTargets = DataManager.INSTANCE.getAvailableTargets(player);
        List<Map.Entry<String, Integer>> matches = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : customTargets.entrySet()) {
            String targetName = entry.getKey();
            if (targetName.toLowerCase(Locale.ROOT).startsWith(lowerIn) && !currentNames.contains(targetName)) {
                matches.add(entry);
            }
        }

        matches.sort((e1, e2) -> compareTargets(e1.getKey(), e2.getKey(), lowerIn));
        return matches;
    }

    @Unique
    private int compareTargets(String name1, String name2, String lowerIn) {
        String lower1 = name1.toLowerCase(Locale.ROOT);
        String lower2 = name2.toLowerCase(Locale.ROOT);

        boolean exact1 = lower1.equals(lowerIn);
        boolean exact2 = lower2.equals(lowerIn);
        if (exact1 && !exact2) return -1;
        if (!exact1 && exact2) return 1;

        int lenCompare = Integer.compare(name1.length(), name2.length());
        if (lenCompare != 0) return lenCompare;

        return name1.compareTo(name2);
    }

    @Unique
    private void insertAndTrim(List<String> names, List<Integer> ticks, List<Map.Entry<String, Integer>> matches) {
        for (int i = matches.size() - 1; i >= 0; i--) {
            Map.Entry<String, Integer> entry = matches.get(i);
            //noinspection SequencedCollectionMethodCanBeUsed
            names.add(0, entry.getKey());
            //noinspection SequencedCollectionMethodCanBeUsed
            ticks.add(0, entry.getValue());
        }

        trimToSize(names, ticks);
    }

    @Unique
    private void ensureHiddenTarget(ServerPlayer player, String lowerIn, List<String> names, List<Integer> ticks) {
        if (!names.isEmpty() && names.get(0).toLowerCase(Locale.ROOT).equals(lowerIn)) {
            return;
        }

        Map<String, Integer> hiddenTargets = DataManager.INSTANCE.getHiddenTargets(player);
        for (Map.Entry<String, Integer> entry : hiddenTargets.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(lowerIn)) {
                names.add(0, entry.getKey());
                ticks.add(0, entry.getValue());
                trimToSize(names, ticks);
                break;
            }
        }
    }

    @Unique
    private void trimToSize(List<String> names, List<Integer> ticks) {
        while (names.size() > 4) {
            //noinspection SequencedCollectionMethodCanBeUsed
            names.remove(names.size() - 1);
            //noinspection SequencedCollectionMethodCanBeUsed
            ticks.remove(ticks.size() - 1);
        }
    }
}