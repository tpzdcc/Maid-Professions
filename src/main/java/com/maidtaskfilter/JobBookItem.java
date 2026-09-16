package com.maidtaskfilter;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 转职书 —— 手持右键女仆消耗并赋予职业。
 *
 * <p>构造参数 jobKey 对应 jobs.json 中的职业 key。
 * {@link MaidTaskFilterMod#OMNI_JOB_KEY} 为全能手册，解锁全部任务。
 *
 * <p>转职逻辑的单一入口在 {@link MaidTaskFilterMod#tryApplyJobBook}，
 * 与 {@link TagJobBookHandler}（数据包 tag 通道）共用，避免两份实现走岔。
 * 本类只负责「我是转职书物品、持有 jobKey」这一身份。
 *
 * <p>受配置项控制：{@code consumeJobBook}（是否消耗）、
 * {@code allowJobOverwrite}（已转职的女仆能否被覆盖）。
 * 落盘位置 {@code config/maidtaskfilter-common.toml}。
 */
public class JobBookItem extends Item {

    private final String jobKey;

    public JobBookItem(String jobKey) {
        super(new Item.Properties().stacksTo(1));
        this.jobKey = jobKey;
    }

    public String getJobKey() {
        return jobKey;
    }

    /**
     * 右键实体（女仆）时触发。转发给公共的转职逻辑入口。
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof EntityMaid maid)) return InteractionResult.PASS;
        return MaidTaskFilterMod.tryApplyJobBook(maid, player, jobKey, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (MaidTaskFilterMod.OMNI_JOB_KEY.equals(jobKey)) {
            tooltip.add(Component.translatable("maidtaskfilter.book.tooltip.omni.line1"));
            tooltip.add(Component.translatable("maidtaskfilter.book.tooltip.omni.line2"));
        } else {
            JobDefinition job = JobConfig.getJob(jobKey);
            if (job != null) {
                tooltip.add(Component.translatable("maidtaskfilter.book.tooltip.job", job.name()));
                tooltip.add(Component.translatable("maidtaskfilter.book.tooltip.tasks",
                        job.tasks().size()));
            }
        }
    }
}
