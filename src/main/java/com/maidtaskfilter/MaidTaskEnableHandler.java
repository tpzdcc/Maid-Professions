package com.maidtaskfilter;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTaskEnableEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * 服务端拦截器：阻止女仆执行职业白名单之外的任务。
 *
 * v2：改为通过 getAllAllowedTasks() 获取通用任务 + 职业独占任务的合集。
 * 如果女仆未分配职业（任务数据为空 + job key 为空）→ 仅允许通用任务。
 */
@Mod.EventBusSubscriber(modid = MaidTaskFilterMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaidTaskEnableHandler {

    private MaidTaskEnableHandler() {}

    @SubscribeEvent
    public static void onMaidTaskEnable(MaidTaskEnableEvent event) {
        EntityMaid maid = event.getEntityMaid();
        String targetTaskUid = event.getTargetTask().getUid().toString();

        Set<String> allowed = MaidTaskFilterMod.getAllAllowedTasks(maid);

        // 空集合 = 不过滤（全能手册或未配置）
        if (allowed.isEmpty()) return;

        if (!allowed.contains(targetTaskUid)) {
            String jobKey = maid.getPersistentData().getString("maidtaskfilter_job");
            JobDefinition job = JobConfig.getJob(jobKey);
            String jobName = job != null ? job.name()
                    : (jobKey.isEmpty() ? "白痴小女仆" : jobKey);

            event.getEnableConditionDesc().add(
                Pair.of("§c[职业限制] §7当前职业 §6[" + jobName + "] §7不允许使用此工作",
                        m -> false)
            );
        }
    }
}
