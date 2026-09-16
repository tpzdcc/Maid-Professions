package com.maidtaskfilter.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.maidtaskfilter.MaidTaskFilterMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * v2：注入 TLM 的 TaskManager，按女仆的当前职业过滤任务 UI。
 *
 * 通过 {@link MaidTaskFilterMod#getAllAllowedTasks(EntityMaid)} 获取
 * 通用任务 + 职业独占任务的合集。
 *
 * 空集合 → 不过滤（全能手册或未限制状态）。
 * 非空集合 → 仅显示白名单中的任务。
 */
@Mixin(value = TaskManager.class, remap = false)
public class TaskManagerMixin {

    @Inject(method = "getNotHiddenTaskList", at = @At("RETURN"), cancellable = true, remap = false)
    private static void maidtaskfilter$filterTasksByJob(EntityMaid maid,
                                                         CallbackInfoReturnable<List<IMaidTask>> cir) {
        List<IMaidTask> originalList = cir.getReturnValue();
        if (originalList == null || originalList.isEmpty()) return;

        Set<String> allowed = MaidTaskFilterMod.getAllAllowedTasks(maid);

        // 空集合 = 不过滤（全能手册 或 未限制 或 无职业数据）
        if (allowed.isEmpty()) return;

        List<IMaidTask> filtered = new ArrayList<>();
        for (IMaidTask task : originalList) {
            if (allowed.contains(task.getUid().toString())) {
                filtered.add(task);
            }
        }

        MaidTaskFilterMod.LOGGER.debug(
            "[MaidTaskFilter v2] Filtered tasks: {} → {} (job data: {})",
            originalList.size(), filtered.size(),
            maid.getPersistentData().getString(MaidTaskFilterMod.JOB_KEY_FALLBACK));

        cir.setReturnValue(filtered);
    }
}
