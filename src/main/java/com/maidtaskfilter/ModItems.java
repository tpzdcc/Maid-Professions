package com.maidtaskfilter;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 注册所有模组物品。
 *
 * 1 空白转职书 + 11 职业转职书 + 1 全能手册 = 13 个物品。
 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MaidTaskFilterMod.MOD_ID);

    /** 空白转职书 —— 神龛合成基础材料 */
    public static final RegistryObject<Item> BLANK_JOB_BOOK = ITEMS.register("blank_job_book",
            () -> new Item(new Item.Properties()));

    /** 11 本职业转职书 —— 右键女仆使用 */
    public static final RegistryObject<JobBookItem> FARMER_BOOK   = ITEMS.register("farmer_book",   () -> new JobBookItem("farmer"));
    public static final RegistryObject<JobBookItem> RANCHER_BOOK  = ITEMS.register("rancher_book",  () -> new JobBookItem("rancher"));
    public static final RegistryObject<JobBookItem> MELEE_BOOK    = ITEMS.register("melee_book",    () -> new JobBookItem("melee"));
    public static final RegistryObject<JobBookItem> RANGED_BOOK   = ITEMS.register("ranged_book",   () -> new JobBookItem("ranged"));
    public static final RegistryObject<JobBookItem> FISHER_BOOK   = ITEMS.register("fisher_book",   () -> new JobBookItem("fisher"));
    public static final RegistryObject<JobBookItem> SPELLBLADE_BOOK = ITEMS.register("spellblade_book", () -> new JobBookItem("spellblade"));
    public static final RegistryObject<JobBookItem> BAKER_BOOK    = ITEMS.register("baker_book",    () -> new JobBookItem("baker"));
    public static final RegistryObject<JobBookItem> CHEF_BOOK     = ITEMS.register("chef_book",     () -> new JobBookItem("chef"));
    public static final RegistryObject<JobBookItem> WAITER_BOOK   = ITEMS.register("waiter_book",   () -> new JobBookItem("waiter"));
    public static final RegistryObject<JobBookItem> BREWER_BOOK   = ITEMS.register("brewer_book",   () -> new JobBookItem("brewer"));
    public static final RegistryObject<JobBookItem> OPERATOR_BOOK = ITEMS.register("operator_book", () -> new JobBookItem("operator"));

    /** 全能手册 —— 创造模式限定 */
    public static final RegistryObject<JobBookItem> OMNI_BOOK = ITEMS.register("omni_book",
            () -> new JobBookItem("omni"));

    // ---- 创造模式标签页 ----

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MaidTaskFilterMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.maidtaskfilter"))
                    .icon(() -> new ItemStack(BLANK_JOB_BOOK.get()))
                    .displayItems((params, output) -> {
                        output.accept(BLANK_JOB_BOOK.get());
                        output.accept(FARMER_BOOK.get());
                        output.accept(RANCHER_BOOK.get());
                        output.accept(MELEE_BOOK.get());
                        output.accept(RANGED_BOOK.get());
                        output.accept(FISHER_BOOK.get());
                        output.accept(SPELLBLADE_BOOK.get());
                        output.accept(BAKER_BOOK.get());
                        output.accept(CHEF_BOOK.get());
                        output.accept(WAITER_BOOK.get());
                        output.accept(BREWER_BOOK.get());
                        output.accept(OPERATOR_BOOK.get());
                        output.accept(OMNI_BOOK.get());
                    })
                    .build());

    private ModItems() {}

    /** 根据职业 key 获取对应的注册物品 */
    public static RegistryObject<? extends Item> getBookForJob(String jobKey) {
        return switch (jobKey) {
            case "farmer"    -> FARMER_BOOK;
            case "rancher"   -> RANCHER_BOOK;
            case "melee"     -> MELEE_BOOK;
            case "ranged"    -> RANGED_BOOK;
            case "fisher"    -> FISHER_BOOK;
            case "spellblade" -> SPELLBLADE_BOOK;
            case "baker"     -> BAKER_BOOK;
            case "chef"      -> CHEF_BOOK;
            case "waiter"    -> WAITER_BOOK;
            case "brewer"    -> BREWER_BOOK;
            case "operator"  -> OPERATOR_BOOK;
            case "omni"      -> OMNI_BOOK;
            default          -> null;
        };
    }
}
