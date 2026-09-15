package com.maidtaskfilter;

import com.google.gson.annotations.SerializedName;

/**
 * 好感度等级对应的职业加成。
 *
 * 每个等级可以同时有属性修正（AttributeModifier）和药水效果（MobEffect）。
 * 从 jobs.json 的 favorabilityBonuses 数组反序列化。
 */
public class FavorabilityBonus {

    @SerializedName("level")
    private int level;           // TLM 好感度等级 0-3

    @SerializedName("attribute")
    private String attribute;    // 属性 ID，如 "generic.attack_damage"，null 表示无属性

    @SerializedName("value")
    private double value;        // 属性加成值（加法）

    @SerializedName("effect")
    private String effect;       // 药水效果 ID，如 "minecraft:haste"，null 表示无效果

    @SerializedName("effectLevel")
    private int effectLevel;     // 药水等级（0 = I 级）

    public FavorabilityBonus() {}

    public int level()            { return level; }
    public String attribute()     { return attribute; }
    public double value()          { return value; }
    public String effect()         { return effect; }
    public int effectLevel()       { return effectLevel; }

    public boolean hasAttribute()  { return attribute != null && !attribute.isEmpty(); }
    public boolean hasEffect()     { return effect != null && !effect.isEmpty(); }
}
