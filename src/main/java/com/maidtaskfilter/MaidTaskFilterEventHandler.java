package com.maidtaskfilter;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * MOD bus event handler.
 *
 * Registration happens via:
 * <ul>
 *   <li>{@code @LittleMaidExtension} — TaskDataKey (TLM scans and calls registerTaskData)</li>
 *   <li>{@link MaidTaskFilterMod#onRegisterCommands} — /maidjob command</li>
 *   <li>{@link MaidTaskFilterMod#onServerStarting} — job config loading</li>
 *   <li>{@link MaidTaskEnableHandler} — server-side task blocking (Forge bus)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = MaidTaskFilterMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MaidTaskFilterEventHandler {

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        MaidTaskFilterMod.LOGGER.info("[MaidTaskFilter v2] Common setup complete");
    }
}
