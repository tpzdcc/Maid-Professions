package com.maidtaskfilter;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 模组行为开关 —— 用 Forge 标准的 {@link ForgeConfigSpec}，
 * 首次启动自动生成 {@code config/maidtaskfilter-common.toml}（带注释，玩家可直接编辑，改完重启生效）。
 *
 * <p><b>为什么不用 jobs.json 加一段</b>：jobs.json 是职业数据，而且玩家机器上那个文件**已经存在**，
 * 往里加新段落 → 老玩家的文件里不会有那一段 → 新选项他们根本看不见。
 * ForgeConfigSpec 会自动生成带注释的完整文件，是 Forge 模组的通用做法。
 *
 * <p><b>所有选项的默认值 = 本模组最初的行为</b>，所以升级后老存档、老配置零影响。
 */
public final class MaidTaskFilterConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ================================================================
    // 选项定义
    // ================================================================

    private static final ForgeConfigSpec.BooleanValue RESTRICT_UNASSIGNED_MAID = BUILDER
            .comment("未转职（没有任何职业数据）的女仆，是否只允许执行「空闲」工作。",
                    "true  = 只允许 touhou_little_maid:idle（默认，本模组最初的行为）",
                    "false = 不做任何限制 —— 新生成的女仆可以直接做全部工作，职业系统变成纯可选")
            .define("restrictUnassignedMaid", true);

    private static final ForgeConfigSpec.BooleanValue CONSUME_JOB_BOOK = BUILDER
            .comment("使用转职书后是否消耗掉。",
                    "true  = 消耗（默认；生存模式下一本只能转一个女仆）",
                    "false = 不消耗 —— 一本可以反复使用（等于所有玩家都有创造模式的待遇）")
            .define("consumeJobBook", true);

    private static final ForgeConfigSpec.BooleanValue ALLOW_JOB_OVERWRITE = BUILDER
            .comment("已经有职业的女仆，能否再用转职书改成别的职业。",
                    "true  = 允许覆盖（默认，本模组最初的行为）",
                    "false = 只有未转职的女仆能使用转职书；已转职的会提示并拒绝，防止误点洗掉职业")
            .define("allowJobOverwrite", true);

    private static final ForgeConfigSpec.IntValue COMMAND_SEARCH_RANGE = BUILDER
            .comment("指令 /maidjob set|get 在玩家周围搜索女仆的半径（单位：格）。",
                    "默认 16。多人服务器上女仆分散得开时，管理员可以调大。",
                    "取值 1 ~ 128。")
            .defineInRange("commandSearchRange", 16, 1, 128);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private MaidTaskFilterConfig() {}

    // ================================================================
    // 读取
    // ================================================================

    /** 未转职的女仆是否只允许 idle。默认 {@code true}。 */
    public static boolean restrictUnassignedMaid() {
        return RESTRICT_UNASSIGNED_MAID.get();
    }

    /** 使用转职书后是否消耗。默认 {@code true}。 */
    public static boolean consumeJobBook() {
        return CONSUME_JOB_BOOK.get();
    }

    /** 已转职的女仆能否被覆盖。默认 {@code true}。 */
    public static boolean allowJobOverwrite() {
        return ALLOW_JOB_OVERWRITE.get();
    }

    /** /maidjob 的搜索半径（格）。默认 {@code 16}。 */
    public static int commandSearchRange() {
        return COMMAND_SEARCH_RANGE.get();
    }
}
