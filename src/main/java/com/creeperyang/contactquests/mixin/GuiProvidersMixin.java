package com.creeperyang.contactquests.mixin;

import com.creeperyang.contactquests.quest.reward.ParcelAllTableReward;
import com.creeperyang.contactquests.quest.reward.ParcelRandomReward;
import com.creeperyang.contactquests.quest.reward.ParcelReward;
import com.creeperyang.contactquests.quest.reward.RewardRegistry;
import com.creeperyang.contactquests.quest.task.ParcelTask;
import com.creeperyang.contactquests.quest.task.RedPacketTask;
import com.creeperyang.contactquests.quest.task.TaskRegistry;
import dev.ftb.mods.ftblibrary.config.ItemStackConfig;
import dev.ftb.mods.ftblibrary.config.ui.resource.SelectItemStackScreen;
import dev.ftb.mods.ftbquests.client.GuiProviders;
import dev.ftb.mods.ftbquests.client.gui.SelectQuestObjectScreen;
import dev.ftb.mods.ftbquests.quest.QuestObjectType;
import dev.ftb.mods.ftbquests.quest.loot.RewardTable;
import dev.ftb.mods.ftbquests.util.ConfigQuestObject;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiProviders.class)
public abstract class GuiProvidersMixin {
    @Inject(method = "setTaskGuiProviders", at = @At("TAIL"))
    private static void setMyTaskGuiProviders(CallbackInfo ci) {
        TaskRegistry.PARCEL.setGuiProvider((gui, quest, callback) -> {
            ItemStackConfig c = new ItemStackConfig(false, false);

            new SelectItemStackScreen(c, accepted -> {
                gui.run();
                if (accepted) {
                    ParcelTask parcelTask = new ParcelTask(0L, quest).setStackAndCount(c.getValue(), c.getValue().getCount());
                    callback.accept(parcelTask, parcelTask.getType().makeExtraNBT());
                }
            }).openGui();
        });
        TaskRegistry.RED_PACKET.setGuiProvider((gui, quest, callback) -> {
            ItemStackConfig c = new ItemStackConfig(false, false);

            new SelectItemStackScreen(c, accepted -> {
                gui.run();
                if (accepted) {
                    RedPacketTask parcelTask = new RedPacketTask(0L, quest).setStackAndCount(c.getValue(), c.getValue().getCount());
                    callback.accept(parcelTask, parcelTask.getType().makeExtraNBT());
                }
            }).openGui();
        });
    }

    @Inject(method = "setRewardGuiProviders", at = @At("TAIL"))
    private static void setMyRewardGuiProviders(CallbackInfo ci) {
        RewardRegistry.INSTANCE.getPARCEL().setGuiProvider((gui, quest, callback) -> {
            ItemStackConfig c = new ItemStackConfig(false, false);
            new SelectItemStackScreen(c, accepted -> {
                if (accepted) {
                    ItemStack copy = c.getValue().copy();
                    copy.setCount(1);
                    ParcelReward reward = new ParcelReward(0L, quest);
                    reward.setItem(copy);
                    reward.setCount(c.getValue().getCount());
                    callback.accept(reward);
                }
                gui.run();
            }).openGui();
        });

        RewardRegistry.INSTANCE.getPARCEL_RANDOM().setGuiProvider((gui, quest, callback) -> {
            ConfigQuestObject<RewardTable> config = new ConfigQuestObject<>(QuestObjectType.REWARD_TABLE);

            SelectQuestObjectScreen<?> s = new SelectQuestObjectScreen<>(config, accepted -> {
                if (accepted) {
                    ParcelRandomReward reward = new ParcelRandomReward(0L, quest);
                    reward.setTable(config.getValue());
                    callback.accept(reward);
                }
                gui.run();
            });
            s.setTitle(Component.translatable("ftbquests.gui.select_reward_table"));
            s.setHasSearchBox(true);
            s.openGui();
        });

        RewardRegistry.INSTANCE.getPARCEL_ALL_TABLE().setGuiProvider((gui, quest, callback) -> {
            ConfigQuestObject<RewardTable> config = new ConfigQuestObject<>(QuestObjectType.REWARD_TABLE);

            SelectQuestObjectScreen<?> s = new SelectQuestObjectScreen<>(config, accepted -> {
                if (accepted) {
                    ParcelAllTableReward reward = new ParcelAllTableReward(0L, quest);
                    reward.setTable(config.getValue());
                    callback.accept(reward);
                }
                gui.run();
            });
            s.setTitle(Component.translatable("ftbquests.gui.select_reward_table"));
            s.setHasSearchBox(true);
            s.openGui();
        });
    }
}
