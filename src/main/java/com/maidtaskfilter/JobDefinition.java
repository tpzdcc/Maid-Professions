package com.maidtaskfilter;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 职业定义 —— v2 扩展版。
 *
 * 新增 assignmentItem（转职书 ID）、recipeItems（神龛配方材料）、
 * favorabilityBonuses（好感度等级加成列表）。
 */
public class JobDefinition {

    @SerializedName("key")
    private String key;

    @SerializedName("name")
    private String name;

    @SerializedName("icon")
    private String icon;

    @SerializedName("description")
    private String description;

    @SerializedName("tasks")
    private List<String> tasks;

    @SerializedName("requiresMod")
    private String requiresMod;

    @SerializedName("conditionalTasks")
    private Map<String, List<String>> conditionalTasks;

    @SerializedName("assignmentItem")
    private String assignmentItem;

    @SerializedName("recipeItems")
    private List<String> recipeItems;

    @SerializedName("favorabilityBonuses")
    private List<FavorabilityBonus> favorabilityBonuses;

    public JobDefinition() {}

    // ---- Getters ----
    //
    // 下面几个 getter 对 null 做了兜底。原因是这些值会直接进 Component.translatable 的
    // %s 参数或 .size() —— jobs.json 是玩家手写的，漏一个 "tasks" 字段就会让
    // /maidjob list 或转职书的 tooltip 直接 NPE（客户端渲染阶段崩，比服务端崩更难查）。

    public String key()                          { return key; }
    public String name()                         { return name == null ? "" : name; }
    public String icon()                         { return icon == null ? "" : icon; }
    public String description()                  { return description == null ? "" : description; }
    public List<String> tasks()                  { return tasks == null ? List.of() : tasks; }
    public String requiresMod()                  { return requiresMod; }
    public Map<String, List<String>> conditionalTasks() { return conditionalTasks; }
    public String assignmentItem()               { return assignmentItem; }
    public List<String> recipeItems()            { return recipeItems; }
    public List<FavorabilityBonus> favorabilityBonuses() { return favorabilityBonuses; }

    /**
     * 将已装模组的条件任务合并到基础任务列表中。
     * 由 {@link JobConfig#load()} 在加载配置后调用。
     */
    public void mergeConditionalTasks() {
        if (conditionalTasks == null || conditionalTasks.isEmpty()) return;
        if (tasks == null) tasks = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : conditionalTasks.entrySet()) {
            if (net.minecraftforge.fml.ModList.get().isLoaded(entry.getKey())) {
                for (String task : entry.getValue()) {
                    if (!tasks.contains(task)) {
                        tasks.add(task);
                    }
                }
            }
        }
    }

    /** 逗号分隔的任务 UID 字符串 —— 写入 TaskData */
    public String tasksAsString() {
        return tasks == null ? "" : String.join(",", tasks);
    }

    /**
     * 该职业在指定好感度等级下的所有加成（从 0 到 level 累积）。
     *
     * <p><b>必须按 level 升序返回</b>：同一个属性可能在多个等级各有一条
     * （例如 farmer 的 movement_speed 在 level 2 是 0.15、level 3 是 0.30），
     * 应用时后一条会覆盖前一条（同一个 UUID）。若不排序，
     * 「哪条最后生效」就取决于作者在 JSON 里的书写顺序 —— 在数组末尾补一条 level 1
     * 会把高等级的加成一脚踩回去。排序后语义固定为「最高等级的值胜出」。
     *
     * <p>同 level 内的相对顺序保持原样（{@code sorted} 是稳定排序）。
     */
    public List<FavorabilityBonus> getBonusesUpToLevel(int level) {
        List<FavorabilityBonus> result = new ArrayList<>();
        if (favorabilityBonuses == null) return result;
        for (FavorabilityBonus b : favorabilityBonuses) {
            if (b.level() <= level) result.add(b);
        }
        result.sort(Comparator.comparingInt(FavorabilityBonus::level));
        return result;
    }
}
