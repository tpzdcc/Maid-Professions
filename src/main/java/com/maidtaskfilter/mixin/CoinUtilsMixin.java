package com.maidtaskfilter.mixin;

import com.maidtaskfilter.MaidTaskFilterMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 ordertocook 的 CoinUtils.giveCoins，在发放 oTc 币的同时增加声誉。
 *
 * <p>数据流：
 * <pre>
 *   订单完成 → CoinUtils.giveCoins(player, oTcAmount)
 *     → [此 Mixin] player.persistentData.dd_reputation += oTcAmount / 10
 *     → reputation.js 的 /reputation 指令读取同一 persistentData 键
 * </pre>
 *
 * <p>汇率：{@value #COINS_PER_REPUTATION} oTc = 1 声誉
 *
 * <p>与 KubeJS 的互操作：
 * reputation.js 通过 {@code persistentData.getInt("dd_reputation")} 读取。
 * 此 Mixin 写入完全相同的键，两端共享同一数据源。
 */
@Pseudo // 目标类不在编译 classpath 中（ordertocook 是运行时依赖）
@Mixin(targets = "cn.breezeth.ordertocook.util.CoinUtils", remap = false)
public class CoinUtilsMixin {

    /** 多少 oTc 币兑换 1 点声誉 */
    private static final int COINS_PER_REPUTATION = 10;

    /**
     * 在 giveCoins 返回前拦截，将币量按汇率转为声誉并写入 persistentData。
     *
     * @param player 获得硬币的玩家（服务端）
     * @param amount 发放的 oTc 币数量
     * @param ci     Mixin 回调信息
     */
    @Inject(method = "giveCoins", at = @At("RETURN"), remap = false)
    private static void maidtaskfilter$onGiveCoins(Player player, int amount, CallbackInfo ci) {
        int repGain = amount / COINS_PER_REPUTATION;
        if (repGain <= 0) return;

        // 与服务端 KubeJS reputation.js 使用同一 persistentData 键
        var data = player.getPersistentData();
        int current = data.getInt("dd_reputation");
        int updated = current + repGain;
        data.putInt("dd_reputation", updated);

        // 聊天栏提示（与 reputation.js 的 tellraw 格式一致）
        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(
                Component.literal("声譽 +" + repGain + "  §7(当前: " + updated + ")"),
                false  // overlay = false，显示在聊天栏
            );
        }

        MaidTaskFilterMod.LOGGER.debug(
            "[OTC→声誉] {} oTc → +{} 声譽 (玩家: {}, 当前: {})",
            amount, repGain, player.getName().getString(), updated
        );
    }
}
