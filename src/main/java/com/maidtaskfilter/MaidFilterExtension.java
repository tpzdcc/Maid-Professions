package com.maidtaskfilter;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;

/**
 * TLM 扩展入口 —— 注册我们的 TaskDataKey。
 *
 * 必须独立于 {@link MaidTaskFilterMod}（被 Forge {@code @Mod} 注解管理），
 * 因为 TLM 的 {@code AnnotatedInstanceUtil.getModExtensions()} 通过反射
 * {@code Class.newInstance()} 实例化 {@code @LittleMaidExtension} 类，
 * {@code @Mod} + {@code @LittleMaidExtension} 同在一个类上会导致类加载冲突。
 *
 * 本类只做一件事：在 TLM 初始化 TaskData 时注册我们的 key。
 * 实际的注册逻辑仍然在 {@link MaidTaskFilterMod#registerKey} 中。
 */
@LittleMaidExtension
public class MaidFilterExtension implements ILittleMaid {

    public MaidFilterExtension() {
        MaidTaskFilterMod.LOGGER.info("[女仆任务过滤] TLM 扩展已实例化");
    }

    @Override
    public void registerTaskData(TaskDataRegister register) {
        MaidTaskFilterMod.registerKey(register);
    }
}
