package com.creeperyang.contactquests.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 仅在 JEI 存在时加载的配方查看兜底桥接；若原版/FTB 配方助手可用则优先由 FTB 助手处理。 */
@JeiPlugin
public final class ContactQuestsJeiPlugin implements IModPlugin {
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("contactquests", "mail_task_recipes");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime available) {
        runtime = available;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** 在 JEI 配方界面中打开指定物品的产出配方。 */
    public static void showRecipes(ItemStack stack) {
        IJeiRuntime available = runtime;
        if (available == null || stack.isEmpty()) return;
        var focus = available.getJeiHelpers().getFocusFactory()
                .createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack.copy());
        available.getRecipesGui().show(focus);
    }
}
