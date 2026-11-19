package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.ContactQuests;
import com.creeperyang.contactquests.data.DataManager;
import com.flechazo.contact.common.item.LetterItem;
import com.flechazo.contact.common.item.ParcelItem;
import com.flechazo.contact.common.item.PostcardItem;
import com.flechazo.contact.common.item.RedPacketItem;
import com.flechazo.contact.common.screenhandler.PostboxScreenHandler;
import com.flechazo.contact.helper.NetworkHelper;
import com.flechazo.contact.network.ActionMessage;
import com.flechazo.contact.platform.PlatformHelper;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

/** 拦截 Contact 发信网络处理与收件人列表包，将 NPC/自定义收件目标路由到任务队列并安全注入自动补全列表。 */
@Mixin(NetworkHelper.class)
public class NetworkHelperMixin {

    @Inject(method = "handleSendMail", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onHandleSendMail(ServerPlayer player, String recipientName, int deliveryTicks, CallbackInfo ci) {
        if (!(player.containerMenu instanceof PostboxScreenHandler container)) {
            return;
        }

        ItemStack stackInSlot = container.parcel.getItem(0);
        if (stackInSlot.isEmpty()) {
            ci.cancel();
            return;
        }

        DataManager.INSTANCE.ensureTargetsFresh();

        String finalRecipientKey = findKeyIgnoreCase(recipientName, player);

        if (finalRecipientKey == null) {
            finalRecipientKey = recipientName;
        }

        if (com.creeperyang.contactquests.data.ChoiceRewardSavedData.hasChoiceMarker(stackInSlot)
                || DataManager.INSTANCE.isChoiceAddress(finalRecipientKey)) {
            var remainder = DataManager.INSTANCE.receiveChoiceCard(player, stackInSlot, finalRecipientKey);
            if (remainder.getCount() < stackInSlot.getCount()) {
                container.parcel.setItem(0, remainder);
                new ActionMessage(1, "").sendTo(player);
            }
            ci.cancel();
            return;
        }

        if (DataManager.INSTANCE.acceptsNpcMail(player, finalRecipientKey)) {
            boolean accepted = false;

            if (stackInSlot.getItem() instanceof PostcardItem) {
                accepted = DataManager.INSTANCE.matchPostcardTaskItem(player, stackInSlot, finalRecipientKey);
            } else if (stackInSlot.getItem() instanceof RedPacketItem) {
                accepted = DataManager.INSTANCE.matchRedPacketTaskItem(player, stackInSlot, finalRecipientKey);
            } else if (stackInSlot.getItem() instanceof ParcelItem || stackInSlot.getItem() instanceof LetterItem) {
                ItemContainerContents contents = stackInSlot.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
                accepted = DataManager.INSTANCE.matchParcelTaskItem(player, stackInSlot, contents, finalRecipientKey);
            } else {
                ContactQuests.debug("未知的物品类型 " + stackInSlot.getItem().getClass().getName());
            }

            if (accepted) {
                container.parcel.setItem(0, ItemStack.EMPTY);
                ActionMessage msg = new ActionMessage(1, "");
                msg.sendTo(player);
            }
            ci.cancel();
        } else if (PlatformHelper.getNameToUUID().get(recipientName) == null) {
            // 原生 Contact 路径对未知收件人会排队写入 null UUID，进而导致整个邮箱附加数据保存失败，此处直接拦截。
            ci.cancel();
        }

    }

    @SuppressWarnings("AddedMixinMembersNamePattern")
    @Unique
    private static String findKeyIgnoreCase(String inputName, ServerPlayer player) {
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(DataManager.parcelReceiver.keySet());
        allKeys.addAll(DataManager.redPacketReceiver.keySet());
        allKeys.addAll(DataManager.postcardReceiver.keySet());
        allKeys.addAll(DataManager.rewardSenders);

        allKeys.addAll(DataManager.INSTANCE.getAvailableTargets(player).keySet());
        allKeys.addAll(DataManager.INSTANCE.getHiddenTargets(player).keySet());

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
                    target = "Lnet/minecraft/server/level/ServerPlayer;containerMenu:Lnet/minecraft/world/inventory/AbstractContainerMenu;",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 2
            ),
            slice = @Slice(
                    from = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/server/level/ServerPlayer;containerMenu:Lnet/minecraft/world/inventory/AbstractContainerMenu;",
                            opcode = Opcodes.GETFIELD,
                            ordinal = 0
                    )
            ),
            remap = false
    )
    private static void injectCustomTargetsNames(
            ServerPlayer player,
            String lowerIn,
            String nameIn,
            boolean shouldSend,
            CallbackInfo ci,
            @Local(ordinal = 0) List<String> names,
            @Local(ordinal = 1) List<Integer> ticks
    ) {
        // 前两次列表读取处于玩家名遍历循环内部；必须等循环结束后再插入 NPC 目标，避免并发修改异常。
        DataManager.INSTANCE.ensureTargetsFresh();
        List<Map.Entry<String, Integer>> matches = getSortedMatches(player, lowerIn, names);

        insertAndTrim(names, ticks, matches);

        ensureHiddenTarget(player, lowerIn, names, ticks);
    }

    @Unique
    private static List<Map.Entry<String, Integer>> getSortedMatches(ServerPlayer player, String lowerIn, List<String> currentNames) {
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
    private static int compareTargets(String name1, String name2, String lowerIn) {
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
    private static void insertAndTrim(List<String> names, List<Integer> ticks, List<Map.Entry<String, Integer>> matches) {
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
    private static void ensureHiddenTarget(ServerPlayer player, String lowerIn, List<String> names, List<Integer> ticks) {
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
    private static void trimToSize(List<String> names, List<Integer> ticks) {
        while (names.size() > 4) {
            //noinspection SequencedCollectionMethodCanBeUsed
            names.remove(names.size() - 1);
            //noinspection SequencedCollectionMethodCanBeUsed
            ticks.remove(ticks.size() - 1);
        }
    }
}
