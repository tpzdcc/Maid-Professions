package com.maidtaskfilter;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * MOD 事件总线处理器。
 *
 * <p>本模组的全部注册入口一览（容易记混，这里统一列一下）：
 * <ul>
 *   <li>{@link MaidTaskFilterMod} 构造函数 —— 物品与创造标签页注册、本处理器注册、
 *       ForgeConfigSpec 注册（MOD 总线）</li>
 *   <li>{@link MaidFilterExtension} 上的 {@code @LittleMaidExtension} —— TaskDataKey，
 *       TLM 扫描到后回调注册（MOD 总线，与 {@code @Mod} 分在不同类，TLM 扫描不到同一个类里的两个注解）</li>
 *   <li>{@link ForgeEventHandler} —— /maidjob 指令注册、jobs.json 加载、好感度变化（FORGE 总线）</li>
 *   <li>{@link MaidTaskEnableHandler} —— 任务拦截（FORGE 总线）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = MaidTaskFilterMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MaidTaskFilterEventHandler {

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        MaidTaskFilterMod.LOGGER.info("[MaidTaskFilter v2] Common setup complete");
    }
}
