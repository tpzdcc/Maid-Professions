package com.maidtaskfilter;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTaskEnableEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * 任务拦截器：阻止女仆执行职业白名单之外的任务。
 *
 * <h3>为什么是「取消事件」而不是「加启用条件说明」</h3>
 *
 * TLM 把 {@code enableConditionDesc} 里我们给的字符串当作**翻译键的最后一段**去拼：
 * <pre>task.&lt;任务命名空间&gt;.&lt;任务路径&gt;.enable_condition.&lt;我们给的字符串&gt;</pre>
 * 拼完直接丢给 {@code Component.translatable(...)} —— 查不到就原样显示那个 key。
 * 也就是说，想让这句话显示成人话，就得在语言文件里为**每一个任务**各登记一条
 * {@code task.xxx.yyy.enable_condition.zzz}。而 jobs.json 是玩家可自由增删任务的，
 * 玩家加一个任务就会冒出一串原始 key 垃圾。所以这里改走两条更稳的路：
 * <ol>
 *   <li>{@code setCanceled(true)} —— 服务端真正拒绝设置该任务，客户端把按钮置灰</li>
 *   <li>服务端给主人发一条聊天提示说明原因 —— 聊天消息可以带职业名，且完全可翻译</li>
 * </ol>
 *
 * <h3>取消之后 TLM 会怎样</h3>
 * <ul>
 *   <li><b>服务端</b>：{@code MaidTaskMessage} 收到 {@code post() == true} 后直接 return，
 *       后面的 {@code task.isEnable(maid)} 检查和 {@code maid.setTask(task)} 都不会执行
 *       —— 任务根本没换上，也不需要我们再拦一层。</li>
 *   <li><b>客户端</b>：{@code AbstractMaidContainerGui} 收到 {@code true} 后
 *       把该任务按钮标记为不可用（置灰）。</li>
 * </ul>
 *
 * <p>TLM 只在「目标任务不是 idle」时才发这个事件，所以「让女仆停手」永远不会被拦。
 */
@Mod.EventBusSubscriber(modid = MaidTaskFilterMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MaidTaskEnableHandler {

    private MaidTaskEnableHandler() {}

    @SubscribeEvent
    public static void onMaidTaskEnable(MaidTaskEnableEvent event) {
        EntityMaid maid = event.getEntityMaid();
        String targetTaskUid = event.getTargetTask().getUid().toString();

        Set<String> allowed = MaidTaskFilterMod.getAllAllowedTasks(maid);
        // 空集合 = 不过滤（全能手册 / 配置关掉了限制 / 无职业数据）
        if (allowed.isEmpty()) return;
        // 白名单内 → 放行
        if (allowed.contains(targetTaskUid)) return;

        event.setCanceled(true);

        // 提示只发服务端：客户端每打开一次界面就会对每个任务各触发一次事件，发消息会刷屏。
        if (maid.level().isClientSide()) return;
        if (!(maid.getOwner() instanceof ServerPlayer owner)) return;

        owner.sendSystemMessage(Component.translatable(
                "maidtaskfilter.task.blocked", jobDisplayName(maid), targetTaskUid));
    }

    /** 职业的显示名；没有职业数据时回退到「未转职」 */
    private static Component jobDisplayName(EntityMaid maid) {
        String jobKey = MaidTaskFilterMod.getJobKey(maid);
        JobDefinition job = jobKey.isEmpty() ? null : JobConfig.getJob(jobKey);
        return job != null
                ? Component.literal(job.name())
                : Component.translatable("maidtaskfilter.task.no_job");
    }
}
