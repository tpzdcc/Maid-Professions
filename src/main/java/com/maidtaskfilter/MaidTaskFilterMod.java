package com.maidtaskfilter;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.data.TaskDataRegister;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
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
 *   <li>新女仆默认空闲，只能做通用任务</li>
 *   <li>手持转职书右键女仆 → 消耗道具 → 赋予职业</li>
 *   <li>职业绑定 TLM 好感度系统，好感度提升自动获得职业加成</li>
 *   <li>/maidjob 指令保留作为管理员工具</li>
 * </ul>
 *
 * <h3>数据流</h3>
 * <pre>
 *   玩家右键女仆 → JobBookItem.interactLivingEntity
 *     → setJobTasks(maid, job.tasksAsString())
 *     → persistentData.putString("maidtaskfilter_job", jobKey)
 *     → applyJobBonuses(maid, job)
 *   ↓
 *   好感度变化 → MaidFavorabilityLevelChangeEvent
 *     → onFavorabilityChange(event)
 *     → applyJobBonuses(maid, job)      [重新计算]
 *   ↓
 *   客户端 UI → TaskManagerMixin.getNotHiddenTaskList
 *     → getAllAllowedTasks(jobKey)      [通用任务 + 职业独占任务]
 *     → 过滤显示
 * </pre>
 */
@Mod(MaidTaskFilterMod.MOD_ID)
public class MaidTaskFilterMod {

    public static final String MOD_ID = "maidtaskfilter";
    public static final Logger LOGGER = LogManager.getLogger();

    private static final ResourceLocation JOB_TASKS_KEY_ID =
            new ResourceLocation(MOD_ID, "job_tasks");
    private static TaskDataKey<TaskData> JOB_TASKS_KEY = null;

    /** 用于追踪已应用的好感度等级，避免重复施加 */
    private static final String FAV_TRACK_KEY = "maidtaskfilter_fav_level";

    public MaidTaskFilterMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(bus);
        ModItems.TABS.register(bus);
        bus.register(new MaidTaskFilterEventHandler());
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
        maid.getPersistentData().putString("maidtaskfilter_job", jobKey);
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
        String fbJobKey = maid.getPersistentData().getString("maidtaskfilter_job");
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
     * 如果未分配职业 → 仅 idle。全能手册（omni）→ 空集合 = 不过滤。
     */
    public static Set<String> getAllAllowedTasks(EntityMaid maid) {
        String tasksStr = getJobTasks(maid);
        String jobKey = getJobKey(maid);

        // 全能手册：不过滤
        if ("omni".equals(jobKey)) return Collections.emptySet();

        // 未转职（无职业数据）→ 仅允许 idle
        if (jobKey.isEmpty() && tasksStr.isEmpty()) {
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

        for (FavorabilityBonus bonus : bonuses) {
            // 属性修正
            if (bonus.hasAttribute()) {
                applyAttributeBonus(maid, bonus);
            }
            // 药水效果（永久）
            if (bonus.hasEffect()) {
                applyEffectBonus(maid, bonus);
            }
        }

        // 记录已应用的好感度等级
        maid.getPersistentData().putInt(FAV_TRACK_KEY, favLevel);
    }

    /** 清除女仆的所有职业加成 */
    public static void clearJobBonuses(EntityMaid maid) {
        // 清除属性修正
        for (String attrId : KNOWN_ATTRIBUTES) {
            Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(attrId));
            if (attr != null) {
                maid.getAttribute(attr).removeModifier(JOB_BONUS_UUID);
            }
        }
        // 清除永久药水效果
        for (String effectId : KNOWN_EFFECTS) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(effectId));
            if (effect != null && maid.hasEffect(effect)) {
                // 只移除我们施加的（无限时长、环境来源的效果）
                MobEffectInstance instance = maid.getEffect(effect);
                if (instance != null && instance.isInfiniteDuration() && !instance.isVisible()) {
                    maid.removeEffect(effect);
                }
            }
        }
        maid.getPersistentData().remove(FAV_TRACK_KEY);
    }

    // ---- 内部实现 ----

    private static final UUID JOB_BONUS_UUID = UUID.fromString("c8f7d3a1-5e2b-4f9c-a6d8-1b3e5f7a9c2d");
    private static final List<String> KNOWN_ATTRIBUTES = List.of(
            "minecraft:generic.attack_damage",
            "minecraft:generic.armor",
            "minecraft:generic.movement_speed",
            "minecraft:generic.attack_speed"
    );
    private static final List<String> KNOWN_EFFECTS = List.of(
            "minecraft:haste", "minecraft:luck", "minecraft:strength",
            "minecraft:resistance", "minecraft:speed", "minecraft:water_breathing",
            "minecraft:night_vision", "minecraft:regeneration"
    );

    private static void applyAttributeBonus(EntityMaid maid, FavorabilityBonus bonus) {
        Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(bonus.attribute()));
        if (attr == null) {
            LOGGER.warn("[MaidTaskFilter] Unknown attribute: {}", bonus.attribute());
            return;
        }
        var instance = maid.getAttribute(attr);
        if (instance == null) return;
        // 移除旧的同 UUID 修正后再加新的
        instance.removeModifier(JOB_BONUS_UUID);
        instance.addPermanentModifier(
                new AttributeModifier(JOB_BONUS_UUID, "maidtaskfilter_job_bonus",
                        bonus.value(), AttributeModifier.Operation.ADDITION));
    }

    private static void applyEffectBonus(EntityMaid maid, FavorabilityBonus bonus) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(bonus.effect()));
        if (effect == null) {
            LOGGER.warn("[MaidTaskFilter] Unknown effect: {}", bonus.effect());
            return;
        }
        // 永久效果：duration = -1, ambient = true, visible = false（不显示粒子）
        maid.addEffect(new MobEffectInstance(effect, -1, bonus.effectLevel(), true, false));
    }

    /** 检查好感度变化并重新应用加成 */
    public static void onFavorabilityChanged(EntityMaid maid) {
        String jobKey = maid.getPersistentData().getString("maidtaskfilter_job");
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
