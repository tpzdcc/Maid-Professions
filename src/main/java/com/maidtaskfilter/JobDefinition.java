package com.maidtaskfilter;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
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

    public String key()                          { return key; }
    public String name()                         { return name; }
    public String icon()                         { return icon; }
    public String description()                  { return description; }
    public List<String> tasks()                  { return tasks; }
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

    /** 根据好感度等级查找对应的加成，无则返回 null */
    public FavorabilityBonus getBonusForLevel(int level) {
        if (favorabilityBonuses == null) return null;
        for (FavorabilityBonus b : favorabilityBonuses) {
            if (b.level() == level) return b;
        }
        return null;
    }

    /** 该职业在指定好感度等级下的所有加成（从 0 到 level 累积） */
    public List<FavorabilityBonus> getBonusesUpToLevel(int level) {
        List<FavorabilityBonus> result = new ArrayList<>();
        if (favorabilityBonuses == null) return result;
        for (FavorabilityBonus b : favorabilityBonuses) {
            if (b.level() <= level) result.add(b);
        }
        return result;
    }
}
