package com.maidtaskfilter;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * Maid Task Filter v2 —— 女仆职业系统。
 *
 * <h3>核心机制</h3>
 * <ul>
 *   <li>新女仆默认空闲，只能做通用任务（可用配置项 {@code restrictUnassignedMaid} 关掉）</li>
 *   <li>手持转职书右键女仆 → 消耗道具 → 赋予职业</li>
 *   <li>职业绑定 TLM 好感度系统，好感度提升自动获得职业加成</li>
 *   <li>/maidjob 指令保留作为管理员工具（权限等级 2）</li>
 * </ul>
 *
 * <h3>数据流</h3>
 * <pre>
 *   玩家右键女仆 → JobBookItem.interactLivingEntity
 *     → setJobData(maid, job.key(), job.tasksAsString())
 *     → applyJobBonuses(maid, job)
 *   ↓
 *   好感度变化 → MaidFavorabilityLevelChangeEvent → onFavorabilityChanged
 *     → applyJobBonuses(maid, job)      [重新计算]
 *   ↓
 *   客户端 UI → TaskManagerMixin.getNotHiddenTaskList
 *     → getAllAllowedTasks(maid)        [通用任务 + 职业独占任务]
 * </pre>
 */
@Mod(MaidTaskFilterMod.MOD_ID)
public class MaidTaskFilterMod {

    public static final String MOD_ID = "maidtaskfilter";
    public static final Logger LOGGER = LogManager.getLogger();

    /**
     * 全能职业的保留 key —— 任务过滤见到它就放行（返回空集合 = 不过滤）。
     *
     * <p>这是**内部哨兵值，不是用户偏好**，所以刻意不做成配置项：
     * 做成配置只会多一种「和 jobs.json 里的职业 key 对不上」的坏法。
     * 它必须与 {@code jobs.json} 里那个 {@code "key": "omni"} 的职业一致。
     */
    public static final String OMNI_JOB_KEY = "omni";

    /** 职业 key 的 persistentData 备份键（KubeJS 直写时只有它，TaskData 同步是后补的） */
    public static final String JOB_KEY_FALLBACK = "maidtaskfilter_job";

    private static final ResourceLocation JOB_TASKS_KEY_ID =
            new ResourceLocation(MOD_ID, "job_tasks");
    private static TaskDataKey<TaskData> JOB_TASKS_KEY = null;

    /** 用于追踪已应用的好感度等级，避免重复施加 */
    private static final String FAV_TRACK_KEY = "maidtaskfilter_fav_level";

    /**
     * 记录「我们往这只女仆身上施加过哪些药水效果」—— 逗号分隔的效果 ID。
     *
     * <p>清除时按这份清单**精确移除**，而不是靠「无限时长 + 不可见」这类特征去猜。
     * 特征式匹配会误删别的模组给的永久效果，而且只要配置里写了白名单外的效果就永远清不掉。
     */
    private static final String APPLIED_EFFECTS_KEY = "maidtaskfilter_applied_effects";

    public MaidTaskFilterMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(bus);
        ModItems.TABS.register(bus);
        bus.register(new MaidTaskFilterEventHandler());
        // 行为开关 → config/maidtaskfilter-common.toml（首次启动自动生成）
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MaidTaskFilterConfig.SPEC);
        LOGGER.info("[MaidTaskFilter v2] 女仆职业系统已加载");
    }

    // ---- TaskDataKey 注册 ----

    public static void registerKey(TaskDataRegister register) {
        if (JOB_TASKS_KEY != null) return;
        JOB_TASKS_KEY = register.register(JOB_TASKS_KEY_ID, TaskData.CODEC);
        LOGGER.info("[MaidTaskFilter v2] TaskDataKey registered: {}", JOB_TASKS_KEY_ID);
    }

    // ---- 职业数据读写 ----

    /** 写入职业数据（服务端调用，自动同步客户端） */
    public static void setJobData(EntityMaid maid, String jobKey, String tasks) {
        if (JOB_TASKS_KEY == null) return;
        maid.setAndSyncData(JOB_TASKS_KEY, new TaskData(tasks, jobKey));
        // 同时存 persistentData 作为备份（服务端查询用）
        maid.getPersistentData().putString(JOB_KEY_FALLBACK, jobKey);
    }

    /** 读取任务白名单（两端均可调用） */
    public static String getJobTasks(EntityMaid maid) {
        if (JOB_TASKS_KEY == null) return "";
        TaskData data = maid.getData(JOB_TASKS_KEY);
        return data != null ? data.tasks() : "";
    }

    /** 读取职业 key（两端均可调用，自动同步） */
    public static String getJobKey(EntityMaid maid) {
        if (JOB_TASKS_KEY == null) return "";
        TaskData data = maid.getData(JOB_TASKS_KEY);
        if (data != null && !data.jobKey().isEmpty()) return data.jobKey();
        // TaskData 无职业 → 回退到 persistentData（兼容 KubeJS 直写）
        String fbJobKey = maid.getPersistentData().getString(JOB_KEY_FALLBACK);
        // 自修复仅在服务端：客户端 setAndSyncData 会抛异常
        if (!fbJobKey.isEmpty() && data != null && !maid.level().isClientSide()) {
            JobDefinition job = JobConfig.getJob(fbJobKey);
            String tasks = job != null ? job.tasksAsString() : "";
            setJobData(maid, fbJobKey, tasks);
            LOGGER.info("[MaidTaskFilter] Auto-repaired TaskData for '{}' with job '{}'",
                    maid.getName().getString(), fbJobKey);
        }
        return fbJobKey;
    }

    // ---- 通用任务 + 职业独占任务合并 ----

    /**
     * 获取女仆所有允许执行的任务（通用 + 职业独占）。
     *
     * <p><b>返回空集合 = 不过滤</b>（调用方按这个约定处理）。三种情况返回空集合：
     * <ol>
     *   <li>全能手册（{@link #OMNI_JOB_KEY}）—— 设计上就是全部解锁</li>
     *   <li>未转职的女仆 + 配置项 {@code restrictUnassignedMaid = false} —— 用户主动关掉了限制</li>
     * </ol>
     *
     * <p>未转职且限制开启（默认）→ 只允许 {@code touhou_little_maid:idle}。
     */
    public static Set<String> getAllAllowedTasks(EntityMaid maid) {
        String tasksStr = getJobTasks(maid);
        String jobKey = getJobKey(maid);

        // 全能手册：不过滤
        if (OMNI_JOB_KEY.equals(jobKey)) return Collections.emptySet();

        // 未转职（无职业数据）
        if (jobKey.isEmpty() && tasksStr.isEmpty()) {
            // 配置关掉了限制 → 不过滤，新女仆什么都能干
            if (!MaidTaskFilterConfig.restrictUnassignedMaid()) return Collections.emptySet();
            return Collections.singleton("touhou_little_maid:idle");
        }

        // 已分配职业：通用 + 职业独占
        Set<String> allowed = new HashSet<>(JobConfig.getCommonTasks());
        if (!tasksStr.isEmpty()) {
            allowed.addAll(Arrays.asList(tasksStr.split(",")));
        }

        // 添加条件通用任务
        for (Map.Entry<String, List<String>> entry : JobConfig.getConditionalCommonTasks().entrySet()) {
            if (net.minecraftforge.fml.ModList.get().isLoaded(entry.getKey())) {
                allowed.addAll(entry.getValue());
            }
        }

        return allowed;
    }

    // ---- 转职逻辑（JobBookItem 与 tag 转职书共用）----

    /**
     * 转职书生效逻辑的单一入口。
     *
     * <p>两个调用方：
     * <ol>
     *   <li>{@link JobBookItem#interactLivingEntity} —— 内置 12 本转职书（物品类自带行为）</li>
     *   <li>{@link TagJobBookHandler} —— 数据包 tag {@code maidtaskfilter:job_<key>}
     *       通道（任意物品放进 tag 即成转职书，零编译扩展）</li>
     * </ol>
     *
     * <p>顺序：所有权 → 覆盖门禁（{@code allowJobOverwrite}）→ 应用职业 →
     * 消耗（创造模式不消耗；{@code consumeJobBook=false} 不消耗）。
     * 拒绝时返回 {@link InteractionResult#FAIL} 且不消耗转职书。
     */
    public static InteractionResult tryApplyJobBook(EntityMaid maid, Player player, String jobKey, ItemStack stack) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;

        // 检查所有权（只有主人可以转职）
        if (maid.getOwner() != player) {
            player.sendSystemMessage(Component.translatable("maidtaskfilter.book.not_owner"));
            return InteractionResult.FAIL;
        }

        // 检查能否覆盖已有职业（配置项 allowJobOverwrite，默认允许）
        String currentJob = getJobKey(maid);
        if (!MaidTaskFilterConfig.allowJobOverwrite() && !currentJob.isEmpty()) {
            JobDefinition current = JobConfig.getJob(currentJob);
            String currentName = current != null ? current.name() : currentJob;
            player.sendSystemMessage(Component.translatable(
                    "maidtaskfilter.book.already_assigned", currentName));
            return InteractionResult.FAIL; // 拒绝时不消耗转职书
        }

        // 应用职业
        if (OMNI_JOB_KEY.equals(jobKey)) {
            // 全能手册：清空职业限制，允许所有任务
            applyOmni(maid, player);
        } else {
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
    private static void applyJob(EntityMaid maid, Player player, JobDefinition job) {
        // 写入职业数据（自动同步客户端，含 jobKey）
        setJobData(maid, job.key(), job.tasksAsString());
        // 清除旧的好感度加成记录，由 applyJobBonuses 重新计算
        maid.getPersistentData().remove(FAV_TRACK_KEY);

        applyJobBonuses(maid, job);

        player.sendSystemMessage(Component.translatable(
                "maidtaskfilter.book.assigned", maid.getName().getString(), job.name()));
        LOGGER.info("[MaidTaskFilter] {} assigned job '{}' to maid {}",
                player.getName().getString(), job.key(), maid.getName().getString());
    }

    /** 全能手册 —— 清空所有职业限制 */
    private static void applyOmni(EntityMaid maid, Player player) {
        // 写入 omni 职业数据（不过滤 = 全部可用）
        setJobData(maid, OMNI_JOB_KEY, "");
        maid.getPersistentData().remove(FAV_TRACK_KEY);

        // 清除所有职业加成
        clearJobBonuses(maid);

        player.sendSystemMessage(Component.translatable(
                "maidtaskfilter.book.omni", maid.getName().getString()));
    }

    // ---- 好感度加成 ----

    /**
     * 根据职业定义和女仆当前好感度等级，施加属性修正和药水效果。
     * 用于转职时和好感度变化时调用。
     */
    public static void applyJobBonuses(EntityMaid maid, JobDefinition job) {
        if (job == null) return;

        int favLevel = maid.getFavorabilityManager().getLevel(); // TLM 好感度 0-3
        List<FavorabilityBonus> bonuses = job.getBonusesUpToLevel(favLevel);

        // 先清除旧加成
        clearJobBonuses(maid);

        Set<String> appliedEffects = new LinkedHashSet<>();
        for (FavorabilityBonus bonus : bonuses) {
            // 属性修正
            if (bonus.hasAttribute()) {
                applyAttributeBonus(maid, bonus);
            }
            // 药水效果（永久）
            if (bonus.hasEffect() && applyEffectBonus(maid, bonus)) {
                appliedEffects.add(bonus.effect());
            }
        }

        // 记下实际施加上去的效果 —— clearJobBonuses 靠这份清单精确移除
        maid.getPersistentData().putString(APPLIED_EFFECTS_KEY,
                String.join(",", appliedEffects));
        // 记录已应用的好感度等级
        maid.getPersistentData().putInt(FAV_TRACK_KEY, favLevel);
    }

    /**
     * 清除女仆的所有职业加成。
     *
     * <p><b>属性</b>：{@link #JOB_BONUS_UUID} 是本模组专用的固定 UUID，
     * 所以直接遍历**全部已注册属性**逐个摘 —— 配置里写任何属性都清得掉。
     * （旧版只遍历 4 条硬编码属性名，配置写了表外的属性就永远留一份永久加成。）
     * 属性总数约 40（原版 + 各模组），调用只发生在转职 / 好感度变化时，开销可忽略。
     *
     * <p><b>效果</b>：按 {@link #APPLIED_EFFECTS_KEY} 记下的清单精确移除。
     * 旧版按「无限时长 + 不可见」扫 8 个硬编码效果，既漏（表外效果清不掉）
     * 又可能误伤（别的模组给的永久效果长得一模一样）。
     */
    public static void clearJobBonuses(EntityMaid maid) {
        // ① 属性修正：遍历全部已注册属性
        for (Attribute attr : ForgeRegistries.ATTRIBUTES) {
            AttributeInstance instance = maid.getAttribute(attr);
            if (instance != null) {
                instance.removeModifier(JOB_BONUS_UUID);
            }
        }

        // ② 药水效果：按记录精确移除
        for (String effectId : readAppliedEffects(maid)) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(effectId));
            if (effect != null && maid.hasEffect(effect)) {
                maid.removeEffect(effect);
            }
        }

        // ③ 兜底：记录机制上线前转职的老女仆没有清单，按旧白名单 + 特征扫一遍。
        //    只为老存档迁移而留，新转职的女仆走 ② 就够了。
        clearLegacyEffects(maid);

        maid.getPersistentData().remove(APPLIED_EFFECTS_KEY);
        maid.getPersistentData().remove(FAV_TRACK_KEY);
    }

    // ---- 内部实现 ----

    /** 职业加成的固定 UUID —— 全模组共用一个，所以「移除同 UUID」就是「移除职业加成」 */
    private static final UUID JOB_BONUS_UUID = UUID.fromString("c8f7d3a1-5e2b-4f9c-a6d8-1b3e5f7a9c2d");

    /**
     * 旧版（记录机制上线前）按硬编码白名单清理效果时用的候选集。
     *
     * <p><b>只用于老存档兜底</b>，不要往这里加东西 —— 新增效果应当由
     * {@link #applyJobBonuses} 自动记进 {@link #APPLIED_EFFECTS_KEY}。
     */
    private static final List<String> LEGACY_KNOWN_EFFECTS = List.of(
            "minecraft:haste", "minecraft:luck", "minecraft:strength",
            "minecraft:resistance", "minecraft:speed", "minecraft:water_breathing",
            "minecraft:night_vision", "minecraft:regeneration"
    );

    /** 读取「我们施加过哪些效果」的清单，无记录时返回空列表 */
    private static List<String> readAppliedEffects(EntityMaid maid) {
        String raw = maid.getPersistentData().getString(APPLIED_EFFECTS_KEY);
        if (raw.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String id : raw.split(",")) {
            if (!id.isEmpty()) result.add(id);
        }
        return result;
    }

    /** 老存档兜底：按旧白名单 + 「无限时长 + 不可见」特征清除效果 */
    private static void clearLegacyEffects(EntityMaid maid) {
        for (String effectId : LEGACY_KNOWN_EFFECTS) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(effectId));
            if (effect == null || !maid.hasEffect(effect)) continue;
            MobEffectInstance instance = maid.getEffect(effect);
            if (instance != null && instance.isInfiniteDuration() && !instance.isVisible()) {
                maid.removeEffect(effect);
            }
        }
    }

    private static void applyAttributeBonus(EntityMaid maid, FavorabilityBonus bonus) {
        Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(bonus.attribute()));
        if (attr == null) {
            LOGGER.warn("[MaidTaskFilter] Unknown attribute: {}", bonus.attribute());
            return;
        }
        AttributeInstance instance = maid.getAttribute(attr);
        if (instance == null) return;
        // 移除旧的同 UUID 修正后再加新的
        instance.removeModifier(JOB_BONUS_UUID);
        instance.addPermanentModifier(
                new AttributeModifier(JOB_BONUS_UUID, "maidtaskfilter_job_bonus",
                        bonus.value(), AttributeModifier.Operation.ADDITION));
    }

    /** @return 是否真的施加了（效果 ID 无效时返回 false，就不会被记进清单） */
    private static boolean applyEffectBonus(EntityMaid maid, FavorabilityBonus bonus) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(bonus.effect()));
        if (effect == null) {
            LOGGER.warn("[MaidTaskFilter] Unknown effect: {}", bonus.effect());
            return false;
        }
        // 永久效果：duration = -1, ambient = true, visible = false（不显示粒子）
        maid.addEffect(new MobEffectInstance(effect, -1, bonus.effectLevel(), true, false));
        return true;
    }

    /** 检查好感度变化并重新应用加成 */
    public static void onFavorabilityChanged(EntityMaid maid) {
        String jobKey = maid.getPersistentData().getString(JOB_KEY_FALLBACK);
        if (jobKey.isEmpty()) return;

        int trackedLevel = maid.getPersistentData().getInt(FAV_TRACK_KEY);
        int currentLevel = maid.getFavorabilityManager().getLevel();
        if (currentLevel == trackedLevel) return; // 未变化

        JobDefinition job = JobConfig.getJob(jobKey);
        if (job != null) {
            applyJobBonuses(maid, job);
            LOGGER.info("[MaidTaskFilter] Maid {} favorability {} → {}, reapplied '{}' bonuses",
                    maid.getName().getString(), trackedLevel, currentLevel, jobKey);
        }
    }
}
