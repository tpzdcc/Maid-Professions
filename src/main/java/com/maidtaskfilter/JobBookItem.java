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
 * 构造参数 jobKey 对应 jobs.json 中的职业 key。
 * "omni" 为全能手册，解锁全部任务。
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
     * 检查目标是否为女仆 → 检查权限 → 消耗转职书 → 写入职业数据。
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof EntityMaid maid)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;

        // 检查所有权（只有主人可以转职）
        if (maid.getOwner() != player) {
            player.sendSystemMessage(Component.literal("§c这不是你的女仆，无法使用转职书。"));
            return InteractionResult.FAIL;
        }

        // 应用职业
        if ("omni".equals(jobKey)) {
            // 全能手册：清空职业限制，允许所有任务
            applyOmni(maid, player);
        } else {
            // 普通转职书
            JobDefinition job = JobConfig.getJob(jobKey);
            if (job == null) {
                player.sendSystemMessage(Component.literal("§c未知职业: " + jobKey));
                return InteractionResult.FAIL;
            }
            applyJob(maid, player, job);
        }

        // 消耗转职书
        if (!player.isCreative()) {
            stack.shrink(1);
        }

        return InteractionResult.SUCCESS;
    }

    /** 为女仆分配职业 */
    private void applyJob(EntityMaid maid, Player player, JobDefinition job) {
        // 写入职业数据（自动同步客户端，含 jobKey）
        MaidTaskFilterMod.setJobData(maid, job.key(), job.tasksAsString());
        // 清除旧的好感度加成记录，由 FavorabilityHandler 重新计算
        maid.getPersistentData().remove("maidtaskfilter_fav_level");

        MaidTaskFilterMod.applyJobBonuses(maid, job);

        player.sendSystemMessage(Component.literal(
                "§a✓ " + maid.getName().getString() + " §a已转职为 §6[" + job.name() + "]§a！"));
        MaidTaskFilterMod.LOGGER.info("[MaidTaskFilter] {} assigned job '{}' to maid {}",
                player.getName().getString(), job.key(), maid.getName().getString());
    }

    /** 全能手册 —— 清空所有职业限制 */
    private void applyOmni(EntityMaid maid, Player player) {
        // 写入 omni 职业数据（不过滤 = 全部可用）
        MaidTaskFilterMod.setJobData(maid, "omni", "");
        maid.getPersistentData().remove("maidtaskfilter_fav_level");

        // 清除所有职业加成
        MaidTaskFilterMod.clearJobBonuses(maid);

        player.sendSystemMessage(Component.literal(
                "§6★ " + maid.getName().getString() + " §6已获得全能之力！所有工作已解锁。"));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if ("omni".equals(jobKey)) {
            tooltip.add(Component.literal("§6创造模式限定"));
            tooltip.add(Component.literal("§7右键女仆使用，解锁全部任务"));
        } else {
            JobDefinition job = JobConfig.getJob(jobKey);
            if (job != null) {
                tooltip.add(Component.literal("§7右键女仆使用，转职为 §e" + job.name()));
                tooltip.add(Component.literal("§7允许 " + job.tasks().size() + " 个独占任务"));
            }
        }
    }
}
