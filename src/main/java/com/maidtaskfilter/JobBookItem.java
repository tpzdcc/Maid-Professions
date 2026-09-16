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
     * 右键实体（女仆）时触发。
     * 检查目标是否为女仆 → 检查所有权 → 检查能否覆盖 → 消耗转职书 → 写入职业数据。
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof EntityMaid maid)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;

        // 检查所有权（只有主人可以转职）
        if (maid.getOwner() != player) {
            player.sendSystemMessage(Component.translatable("maidtaskfilter.book.not_owner"));
            return InteractionResult.FAIL;
        }

        // 检查能否覆盖已有职业（配置项 allowJobOverwrite，默认允许）
        String currentJob = MaidTaskFilterMod.getJobKey(maid);
        if (!MaidTaskFilterConfig.allowJobOverwrite() && !currentJob.isEmpty()) {
            JobDefinition current = JobConfig.getJob(currentJob);
            String currentName = current != null ? current.name() : currentJob;
            player.sendSystemMessage(Component.translatable(
                    "maidtaskfilter.book.already_assigned", currentName));
            return InteractionResult.FAIL; // 拒绝时不消耗转职书
        }

        // 应用职业
        if (MaidTaskFilterMod.OMNI_JOB_KEY.equals(jobKey)) {
            // 全能手册：清空职业限制，允许所有任务
            applyOmni(maid, player);
        } else {
            // 普通转职书
            JobDefinition job = JobConfig.getJob(jobKey);
            if (job == null) {
                player.sendSystemMessage(Component.translatable(
                        "maidtaskfilter.book.unknown_job", jobKey));
                return InteractionResult.FAIL;
            }
            applyJob(maid, player, job);
        }

        // 消耗转职书（创造模式永不消耗；配置项 consumeJobBook=false 时也不消耗）
        if (!player.isCreative() && MaidTaskFilterConfig.consumeJobBook()) {
            stack.shrink(1);
        }

        return InteractionResult.SUCCESS;
    }

    /** 为女仆分配职业 */
    private void applyJob(EntityMaid maid, Player player, JobDefinition job) {
        // 写入职业数据（自动同步客户端，含 jobKey）
        MaidTaskFilterMod.setJobData(maid, job.key(), job.tasksAsString());
        // 清除旧的好感度加成记录，由 applyJobBonuses 重新计算
        maid.getPersistentData().remove("maidtaskfilter_fav_level");

        MaidTaskFilterMod.applyJobBonuses(maid, job);

        player.sendSystemMessage(Component.translatable(
                "maidtaskfilter.book.assigned", maid.getName().getString(), job.name()));
        MaidTaskFilterMod.LOGGER.info("[MaidTaskFilter] {} assigned job '{}' to maid {}",
                player.getName().getString(), job.key(), maid.getName().getString());
    }

    /** 全能手册 —— 清空所有职业限制 */
    private void applyOmni(EntityMaid maid, Player player) {
        // 写入 omni 职业数据（不过滤 = 全部可用）
        MaidTaskFilterMod.setJobData(maid, MaidTaskFilterMod.OMNI_JOB_KEY, "");
        maid.getPersistentData().remove("maidtaskfilter_fav_level");

        // 清除所有职业加成
        MaidTaskFilterMod.clearJobBonuses(maid);

        player.sendSystemMessage(Component.translatable(
                "maidtaskfilter.book.omni", maid.getName().getString()));
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
