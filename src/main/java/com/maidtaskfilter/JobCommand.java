package com.maidtaskfilter;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.Collection;
import java.util.List;

/**
 * 注册 {@code /maidjob} 指令及其子命令。
 *
 * <h3>子命令</h3>
 * <ul>
 *   <li>{@code /maidjob list} —— 列出所有已定义的职业</li>
 *   <li>{@code /maidjob set <职业key> [女仆名字]} —— 为女仆分配职业（不填名字找最近的）</li>
 *   <li>{@code /maidjob get [女仆名字]} —— 查看女仆的当前职业</li>
 *   <li>{@code /maidjob info <职业key>} —— 查看职业详情及允许的任务列表</li>
 * </ul>
 */
public final class JobCommand {

    /** 搜索附近女仆的半径（格） */
    private static final int SEARCH_RANGE = 16;

    private JobCommand() {}

    // ================================================================
    // 指令注册
    // ================================================================

    /**
     * 向 Brigadier 指令调度器注册所有 /maidjob 子命令。
     * 由 {@link ForgeEventHandler#onRegisterCommands} 调用。
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("maidjob")
                // /maidjob list
                .then(Commands.literal("list")
                    .executes(ctx -> cmdList(ctx.getSource())))
                // /maidjob set <jobKey> [maidName]
                .then(Commands.literal("set")
                    .then(Commands.argument("jobKey", StringArgumentType.string())
                        .executes(ctx -> cmdSet(ctx.getSource(),
                            StringArgumentType.getString(ctx, "jobKey"), ""))
                        .then(Commands.argument("maidName", StringArgumentType.greedyString())
                            .executes(ctx -> cmdSet(ctx.getSource(),
                                StringArgumentType.getString(ctx, "jobKey"),
                                StringArgumentType.getString(ctx, "maidName"))))))
                // /maidjob get [maidName]
                .then(Commands.literal("get")
                    .executes(ctx -> cmdGet(ctx.getSource(), ""))
                    .then(Commands.argument("maidName", StringArgumentType.greedyString())
                        .executes(ctx -> cmdGet(ctx.getSource(),
                            StringArgumentType.getString(ctx, "maidName")))))
                // /maidjob info <jobKey>
                .then(Commands.literal("info")
                    .then(Commands.argument("jobKey", StringArgumentType.string())
                        .executes(ctx -> cmdInfo(ctx.getSource(),
                            StringArgumentType.getString(ctx, "jobKey")))))
        );
    }

    // ================================================================
    // 子命令实现
    // ================================================================

    /** /maidjob list —— 列出所有职业 */
    private static int cmdList(CommandSourceStack src) {
        Collection<JobDefinition> jobs = JobConfig.getAllJobs();
        send(src, "§6========== 可用职业列表 ==========");
        for (JobDefinition job : jobs) {
            String taskCount = "omni".equals(job.key())
                    ? "∞ 全部" : (job.tasks().size() + " 个任务");
            send(src, "§e[" + job.name() + "§e] §7(key: " + job.key()
                    + ") §f- " + job.description()
                    + " §7[" + taskCount + "]");
        }
        send(src, "§6=====================================");
        send(src, "§7使用 §e/maidjob set <key> [名字] §7为女仆分配职业");
        send(src, "§7使用 §e/maidjob info <key> §7查看职业详情");
        return 1;
    }

    /** /maidjob set <jobKey> [maidName] —— 分配职业 */
    private static int cmdSet(CommandSourceStack src, String jobKey, String maidName) {
        if (!(src.getEntity() instanceof ServerPlayer player)) return 0;

        // 验证职业是否存在
        JobDefinition job = JobConfig.getJob(jobKey);
        if (job == null) {
            send(src, "§c未知职业 '" + jobKey + "'。使用 /maidjob list 查看所有职业。");
            return 0;
        }

        // 查找目标女仆
        EntityMaid maid = findNearestMaid(player, maidName);
        if (maid == null) {
            send(src, "§c附近未找到" + (maidName.isEmpty() ? "女仆。" : "名字包含 '" + maidName + "' 的女仆。"));
            return 0;
        }

        // 写入 TaskData（自动同步到客户端，含 jobKey）
        MaidTaskFilterMod.setJobData(maid, jobKey, job.tasksAsString());

        double dist = player.distanceTo(maid);
        send(src, "§a✓ 已将女仆 §e" + maid.getName().getString()
                + " §a的职业设置为 §6[" + job.name() + "] §a（距离: " + Math.round(dist) + " 格）");
        send(src, "§7允许的任务: §f" + String.join(", ", job.tasks()));
        return 1;
    }

    /** /maidjob get [maidName] —— 查询女仆当前职业 */
    private static int cmdGet(CommandSourceStack src, String maidName) {
        if (!(src.getEntity() instanceof ServerPlayer player)) return 0;

        EntityMaid maid = findNearestMaid(player, maidName);
        if (maid == null) {
            send(src, "§c附近未找到" + (maidName.isEmpty() ? "女仆。" : "名字包含 '" + maidName + "' 的女仆。"));
            return 0;
        }

        String jobKey = MaidTaskFilterMod.getJobKey(maid);
        if (jobKey.isEmpty()) jobKey = "idle";
        JobDefinition job = JobConfig.getJob(jobKey);
        String jobName = job != null ? job.name() : "未知";
        double dist = player.distanceTo(maid);

        send(src, "§6========== 女仆职业信息 ==========");
        send(src, "§e女仆: §f" + maid.getName().getString()
                + " §7(距离: " + Math.round(dist) + " 格)");
        send(src, "§e职业: §6[" + jobName + "] §7(key: " + jobKey + ")");
        if (job != null) {
            send(src, "§e描述: §f" + job.description());
            send(src, "§e允许的任务 (" + job.tasks().size() + " 个):");
            for (String t : job.tasks()) {
                send(src, "  §a✓ §f" + t);
            }
        }
        send(src, "§6====================================");
        return 1;
    }

    /** /maidjob info <jobKey> —— 查看职业详情 */
    private static int cmdInfo(CommandSourceStack src, String jobKey) {
        JobDefinition job = JobConfig.getJob(jobKey);
        if (job == null) {
            send(src, "§c未知职业 '" + jobKey + "'。使用 /maidjob list 查看所有职业。");
            return 0;
        }
        send(src, "§6========== 职业详情: " + job.name() + " ==========");
        send(src, "§eKey: §7" + job.key());
        send(src, "§e图标: §7" + job.icon());
        send(src, "§e描述: §f" + job.description());
        if ("omni".equals(jobKey)) {
            send(src, "§e允许的任务: §6∞ 全部（不做任何限制）");
        } else {
            send(src, "§e允许的任务 (" + job.tasks().size() + " 个):");
            for (String t : job.tasks()) {
                send(src, "  §a· §f" + t);
            }
        }
        send(src, "§6================================================");
        return 1;
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    /**
     * 在玩家周围 {@link #SEARCH_RANGE} 格内查找最近的 EntityMaid。
     *
     * 名字匹配规则（nameFilter 非空时）：
     * <ol>
     *   <li>优先匹配命名牌名字（{@code getCustomName()}，换模型不变）</li>
     *   <li>其次匹配模型名（{@code getName()}，换模型会变）</li>
     * </ol>
     * 建议用命名牌给女仆起名后再分配职业，避免模型变更后找不到。
     */
    private static EntityMaid findNearestMaid(ServerPlayer player, String nameFilter) {
        AABB area = player.getBoundingBox().inflate(SEARCH_RANGE);
        List<Entity> entities = player.level().getEntities(player, area,
                e -> e instanceof EntityMaid);

        EntityMaid nearest = null;
        double nearestDist = Double.MAX_VALUE;
        String filter = nameFilter.toLowerCase();

        for (Entity e : entities) {
            EntityMaid maid = (EntityMaid) e;
            if (!filter.isEmpty()) {
                // 同时检查命名牌名字（稳定）和模型名（会随模型变更）
                String customName = maid.getCustomName() != null
                        ? maid.getCustomName().getString().toLowerCase() : "";
                String displayName = maid.getName().getString().toLowerCase();
                if (!customName.contains(filter) && !displayName.contains(filter)) continue;
            }
            double dist = player.distanceToSqr(maid);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = maid;
            }
        }
        return nearest;
    }

    /** 向指令执行者发送消息 */
    private static void send(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), false);
    }
}
