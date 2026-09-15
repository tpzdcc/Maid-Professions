package com.maidtaskfilter;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidFavorabilityLevelChangeEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 事件总线处理器。
 *
 * 处理指令注册、配置加载、好感度变化等事件。
 */
@Mod.EventBusSubscriber(modid = MaidTaskFilterMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeEventHandler {

    private ForgeEventHandler() {}

    /** 注册 /maidjob 指令及其子命令 */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        JobCommand.register(event.getDispatcher());
        MaidTaskFilterMod.LOGGER.info("[MaidTaskFilter v2] /maidjob command registered");
    }

    /** 服务端启动时加载职业配置文件 */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        JobConfig.load();
        MaidTaskFilterMod.LOGGER.info("[MaidTaskFilter v2] Job config loaded on server start");
    }

    /**
     * TLM 好感度等级变化时触发。
     * 重新计算并应用职业加成。
     */
    @SubscribeEvent
    public static void onFavorabilityChange(MaidFavorabilityLevelChangeEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid == null) return;
        MaidTaskFilterMod.onFavorabilityChanged(maid);
    }
}
