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
 *
 * <p>权限等级 2（op 级），在 {@link #register} 里统一声明。
 */
public final class JobCommand {

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
                // 权限等级 2（op 级）—— 与 TLM 本体 /tlm、本包 /ddmaid 一致。
                // 不加这行 → Brigadier 默认谓词是 s -> true → 任何玩家可执行
                // /maidjob set <任意职业>，把别人女仆改成 omni 免费解锁全部任务。
                .requires(src -> src.hasPermission(2))
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
        send(src, Component.translatable("maidtaskfilter.command.list.header"));
        for (JobDefinition job : jobs) {
            Component taskCount = MaidTaskFilterMod.OMNI_JOB_KEY.equals(job.key())
                    ? Component.translatable("maidtaskfilter.command.list.tasks_omni")
                    : Component.translatable("maidtaskfilter.command.list.tasks_count",
                            job.tasks().size());
            send(src, Component.translatable("maidtaskfilter.command.list.entry",
                    job.name(), job.key(), job.description(), taskCount));
        }
        send(src, Component.translatable("maidtaskfilter.command.list.footer"));
        send(src, Component.translatable("maidtaskfilter.command.list.hint_set"));
        send(src, Component.translatable("maidtaskfilter.command.list.hint_info"));
        return 1;
    }

    /** /maidjob set <jobKey> [maidName] —— 分配职业 */
    private static int cmdSet(CommandSourceStack src, String jobKey, String maidName) {
        if (!(src.getEntity() instanceof ServerPlayer player)) return 0;

        // 验证职业是否存在
        JobDefinition job = JobConfig.getJob(jobKey);
        if (job == null) {
            send(src, Component.translatable("maidtaskfilter.command.error.unknown_job", jobKey));
            return 0;
        }

        // 查找目标女仆
        EntityMaid maid = findNearestMaid(player, maidName);
        if (maid == null) {
            send(src, maidNotFound(maidName));
            return 0;
        }

        // 写入 TaskData（自动同步到客户端，含 jobKey）
        MaidTaskFilterMod.setJobData(maid, jobKey, job.tasksAsString());

        double dist = player.distanceTo(maid);
        send(src, Component.translatable("maidtaskfilter.command.set.success",
                maid.getName().getString(), job.name(), Math.round(dist)));
        send(src, Component.translatable("maidtaskfilter.command.set.allowed_tasks",
                String.join(", ", job.tasks())));
        return 1;
    }

    /** /maidjob get [maidName] —— 查询女仆当前职业 */
    private static int cmdGet(CommandSourceStack src, String maidName) {
        if (!(src.getEntity() instanceof ServerPlayer player)) return 0;

        EntityMaid maid = findNearestMaid(player, maidName);
        if (maid == null) {
            send(src, maidNotFound(maidName));
            return 0;
        }

        String jobKey = MaidTaskFilterMod.getJobKey(maid);
        JobDefinition job = jobKey.isEmpty() ? null : JobConfig.getJob(jobKey);
        double dist = player.distanceTo(maid);

        send(src, Component.translatable("maidtaskfilter.command.get.header"));
        send(src, Component.translatable("maidtaskfilter.command.get.maid",
                maid.getName().getString(), Math.round(dist)));
        if (jobKey.isEmpty()) {
            // 没有职业数据 —— 之前这里会硬填 "idle"，然后查不到职业，显示成「未知」，误导管理员
            send(src, Component.translatable("maidtaskfilter.command.get.job_none"));
        } else {
            // 职业 key 存在但 jobs.json 里找不到（职业被删了 / requiresMod 未装）→ 直接显示 key
            send(src, Component.translatable("maidtaskfilter.command.get.job",
                    job != null ? job.name() : jobKey, jobKey));
        }
        if (job != null) {
            send(src, Component.translatable("maidtaskfilter.command.get.description",
                    job.description()));
            send(src, Component.translatable("maidtaskfilter.command.get.tasks_header",
                    job.tasks().size()));
            for (String t : job.tasks()) {
                send(src, Component.translatable("maidtaskfilter.command.get.task_entry", t));
            }
        }
        send(src, Component.translatable("maidtaskfilter.command.get.footer"));
        return 1;
    }

    /** /maidjob info <jobKey> —— 查看职业详情 */
    private static int cmdInfo(CommandSourceStack src, String jobKey) {
        JobDefinition job = JobConfig.getJob(jobKey);
        if (job == null) {
            send(src, Component.translatable("maidtaskfilter.command.error.unknown_job", jobKey));
            return 0;
        }
        send(src, Component.translatable("maidtaskfilter.command.info.header", job.name()));
        send(src, Component.translatable("maidtaskfilter.command.info.key", job.key()));
        send(src, Component.translatable("maidtaskfilter.command.info.icon", job.icon()));
        send(src, Component.translatable("maidtaskfilter.command.info.description",
                job.description()));
        if (MaidTaskFilterMod.OMNI_JOB_KEY.equals(jobKey)) {
            send(src, Component.translatable("maidtaskfilter.command.info.tasks_all"));
        } else {
            send(src, Component.translatable("maidtaskfilter.command.info.tasks_header",
                    job.tasks().size()));
            for (String t : job.tasks()) {
                send(src, Component.translatable("maidtaskfilter.command.info.task_entry", t));
            }
        }
        send(src, Component.translatable("maidtaskfilter.command.info.footer"));
        return 1;
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    /** 「附近没找到女仆」的两种说法 */
    private static Component maidNotFound(String maidName) {
        return maidName.isEmpty()
                ? Component.translatable("maidtaskfilter.command.error.maid_not_found")
                : Component.translatable("maidtaskfilter.command.error.maid_not_found_named",
                        maidName);
    }

    /**
     * 在玩家周围 {@link MaidTaskFilterConfig#commandSearchRange()} 格内查找最近的 EntityMaid。
     *
     * 名字匹配规则（nameFilter 非空时）：
     * <ol>
     *   <li>优先匹配命名牌名字（{@code getCustomName()}，换模型不变）</li>
     *   <li>其次匹配模型名（{@code getName()}，换模型会变）</li>
     * </ol>
     * 建议用命名牌给女仆起名后再分配职业，避免模型变更后找不到。
     */
    private static EntityMaid findNearestMaid(ServerPlayer player, String nameFilter) {
        int range = MaidTaskFilterConfig.commandSearchRange();
        AABB area = player.getBoundingBox().inflate(range);
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
    private static void send(CommandSourceStack src, Component msg) {
        src.sendSuccess(() -> msg, false);
    }
}
