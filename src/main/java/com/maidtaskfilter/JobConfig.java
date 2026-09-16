package com.maidtaskfilter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * v2 配置加载器。
 *
 * jobs.json 新格式：
 * <pre>{@code
 * {
 *   "commonTasks": ["touhou_little_maid:idle", ...],
 *   "conditionalCommonTasks": { "maid_bakeries": ["maid_bakeries:eat_cake"] },
 *   "jobs": [ { "key": "farmer", "name": "...", "tasks": [...], ... }, ... ]
 * }
 * }</pre>
 */
public final class JobConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("maidtaskfilter");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("jobs.json");

    private static final Map<String, JobDefinition> JOBS = new LinkedHashMap<>();
    private static List<String> COMMON_TASKS = new ArrayList<>();
    private static final Map<String, List<String>> CONDITIONAL_COMMON_TASKS = new LinkedHashMap<>();

    private JobConfig() {}

    @SuppressWarnings("unchecked")
    public static void load() {
        JOBS.clear();
        COMMON_TASKS.clear();
        CONDITIONAL_COMMON_TASKS.clear();

        try {
            Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(CONFIG_FILE)) {
                createDefaultConfig();
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                Map<String, Object> raw = GSON.fromJson(reader, Map.class);
                if (raw == null) return;

                if (raw.containsKey("commonTasks")) {
                    List<String> ct = (List<String>) raw.get("commonTasks");
                    if (ct != null) COMMON_TASKS = new ArrayList<>(ct);
                }

                if (raw.containsKey("conditionalCommonTasks")) {
                    Map<String, List<String>> cct = (Map<String, List<String>>) raw.get("conditionalCommonTasks");
                    if (cct != null) CONDITIONAL_COMMON_TASKS.putAll(cct);
                }

                if (raw.containsKey("jobs")) {
                    List<Map<String, Object>> jobsList = (List<Map<String, Object>>) raw.get("jobs");
                    if (jobsList != null) {
                        int total = 0, skipped = 0, failed = 0;
                        for (int i = 0; i < jobsList.size(); i++) {
                            Map<String, Object> j = jobsList.get(i);
                            // 单个职业条目出错只跳过它自己。
                            // 不加这层的话，jobs[] 里第 5 个职业写错一个字段，整份 jobs.json
                            // 就会从第 5 个开始全部加载失败 —— 前面的已进 JOBS，后面的一律没有。
                            try {
                                String json = GSON.toJson(j);
                                JobDefinition job = GSON.fromJson(json, JobDefinition.class);
                                if (job.key() == null || job.key().isEmpty()) continue;
                                total++;

                                // 检查 requiresMod：非空且模组未装 → 跳过
                                String req = job.requiresMod();
                                if (req != null && !req.isEmpty()
                                        && !net.minecraftforge.fml.ModList.get().isLoaded(req)) {
                                    skipped++;
                                    MaidTaskFilterMod.LOGGER.info("[MaidTaskFilter v2] Job '{}' skipped: mod '{}' not installed",
                                            job.key(), req);
                                    continue;
                                }

                                // 合并条件任务
                                job.mergeConditionalTasks();

                                JOBS.put(job.key(), job);
                            } catch (Exception entryError) {
                                failed++;
                                MaidTaskFilterMod.LOGGER.error(
                                        "[MaidTaskFilter v2] jobs[{}] (key={}) failed to load, skipped: {}",
                                        i, j.get("key"), entryError.toString());
                            }
                        }
                        if (skipped > 0) {
                            MaidTaskFilterMod.LOGGER.info("[MaidTaskFilter v2] {} of {} jobs skipped (mod not installed)",
                                    skipped, total);
                        }
                        if (failed > 0) {
                            MaidTaskFilterMod.LOGGER.warn(
                                    "[MaidTaskFilter v2] {} job entries failed to load — check jobs.json syntax",
                                    failed);
                        }
                    }
                }
            }

            MaidTaskFilterMod.LOGGER.info("[MaidTaskFilter v2] Loaded {} active jobs, {} common tasks",
                    JOBS.size(), COMMON_TASKS.size());
        } catch (Exception e) {
            MaidTaskFilterMod.LOGGER.error("[MaidTaskFilter v2] Failed to load jobs.json: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createDefaultConfig() throws IOException {
        String json = """
        {
          "_readme": "===============================================================",
          "_readme2": "  Maid Task Filter v2 — 职业配置文件",
          "_readme3": "  路径: config/maidtaskfilter/jobs.json",
          "_readme4": "  修改后重启服务器即可生效，无需重新编译模组",
          "_readme5": "",
          "_readme6": "  ■ commonTasks: 所有职业共享的通用任务（始终生效）",
          "_readme7": "  ■ conditionalCommonTasks: 指定模组安装后才追加的通用任务",
          "_readme8": "  ■ jobs[]: 职业定义数组",
          "_readme9": "    - key: 职业内部标识（英文，唯一，对应转职书的 jobKey）",
          "_readme10": "    - name: 职业中文显示名称",
          "_readme11": "    - requiresMod: 职业的硬前置模组 modId。非空时，若该模组未装则本职业自动隐藏，转职书不可用",
          "_readme12": "    - tasks: 职业基础任务列表（仅依赖 TLM 原版的任务放这里）",
          "_readme13": "    - conditionalTasks: 条件任务 { modId: [任务UID, ...] }。modId 安装时才注入到 tasks",
          "_readme14": "    - favorabilityBonuses: 好感度等级加成（level 0-3），支持 attribute 和 effect",
          "_readme14b": "      ★ 同一等级要加多个加成 → 写多条 entry（level 相同即可），例如 fisher 的 level 2。",
          "_readme14c": "        不要自造 effect2 / effectLevel2 这类字段：解析器会静默丢弃，效果不生效且不报错。",
          "_readme15": "",
          "_readme16": "  ■ 如何新增职业：在 jobs[] 末尾加一个对象，配 key/name/tasks 即可",
          "_readme17": "  ■ 如何追加任务：编辑现有职业的 tasks 数组或 conditionalTasks",
          "_readme18": "  ■ 如何追加联动：在 conditionalTasks 中添加 新模组id: [任务UID1, ...]",
          "_readme19": "  ■ 以 _ 开头的键（_readme / _comment）只是写给人看的注释，解析时会被忽略",
          "_readme20": "  ■ 某个职业写错了只会跳过它自己，并在日志里留一条 error，不影响其他职业",
          "_readme21": "===============================================================",
          "commonTasks": [
            "touhou_little_maid:idle",
            "touhou_little_maid:board_games",
            "touhou_little_maid:feed"
          ],
          "conditionalCommonTasks": {},
          "jobs": [
            {
              "_comment": "====== 核心职业：纯 TLM 无需额外模组 ======",
              "key": "farmer",
              "name": "挥洒汗水的种地小女仆",
              "icon": "minecraft:diamond_hoe",
              "description": "负责种植作物、收割庄稼",
              "tasks": [
                "touhou_little_maid:farm",
                "touhou_little_maid:sugar_cane",
                "touhou_little_maid:melon",
                "touhou_little_maid:cocoa",
                "touhou_little_maid:grass"
              ],
              "assignmentItem": "maidtaskfilter:farmer_book",
              "recipeItems": [
                "minecraft:diamond_hoe",
                "minecraft:wheat_seeds",
                "minecraft:bone_meal"
              ],
              "favorabilityBonuses": [
                { "level": 1, "effect": "minecraft:haste", "effectLevel": 0 },
                { "level": 2, "attribute": "minecraft:generic.movement_speed", "value": 0.15, "effect": "minecraft:haste", "effectLevel": 0 },
                { "level": 3, "attribute": "minecraft:generic.movement_speed", "value": 0.30, "effect": "minecraft:haste", "effectLevel": 1 }
              ]
            },
            {
              "key": "rancher",
              "name": "努力放羊的放牧小女仆",
              "icon": "minecraft:wheat",
              "description": "负责喂养动物、剪毛、挤奶、采蜜",
              "tasks": [
                "touhou_little_maid:feed_animal",
                "touhou_little_maid:shears",
                "touhou_little_maid:milk",
                "touhou_little_maid:honey",
                "touhou_little_maid:snow"
              ],
              "assignmentItem": "maidtaskfilter:rancher_book",
              "recipeItems": [
                "minecraft:shears",
                "minecraft:milk_bucket",
                "minecraft:wheat"
              ],
              "favorabilityBonuses": [
                { "level": 1, "attribute": "minecraft:generic.movement_speed", "value": 0.10 },
                { "level": 2, "attribute": "minecraft:generic.movement_speed", "value": 0.15, "effect": "minecraft:luck", "effectLevel": 0 },
                { "level": 3, "attribute": "minecraft:generic.movement_speed", "value": 0.25, "effect": "minecraft:luck", "effectLevel": 1 }
              ]
            },
            {
              "key": "melee",
              "name": "绝对不会让主人受伤的贴身小女仆",
              "icon": "minecraft:diamond_sword",
              "description": "近战护卫，负责插火把和灭火",
              "tasks": [
                "touhou_little_maid:attack",
                "touhou_little_maid:torch",
                "touhou_little_maid:extinguishing"
              ],
              "assignmentItem": "maidtaskfilter:melee_book",
              "recipeItems": [
                "minecraft:diamond_sword",
                "minecraft:iron_ingot"
              ],
              "favorabilityBonuses": [
                { "level": 1, "attribute": "minecraft:generic.attack_damage", "value": 2.0 },
                { "level": 2, "attribute": "minecraft:generic.attack_damage", "value": 2.0, "effect": "minecraft:resistance", "effectLevel": 0 },
                { "level": 3, "attribute": "minecraft:generic.attack_damage", "value": 4.0, "effect": "minecraft:resistance", "effectLevel": 0 }
              ]
            },
            {
              "key": "ranged",
              "name": "百发百中的神射手小女仆",
              "icon": "minecraft:bow",
              "description": "远程射手，弓弩为主，安装枪械模组后追加枪击",
              "tasks": [
                "touhou_little_maid:ranged_attack",
                "touhou_little_maid:crossbow_attack"
              ],
              "conditionalTasks": {
                "tacz": [
                  "touhou_little_maid:gun_attack"
                ]
              },
              "assignmentItem": "maidtaskfilter:ranged_book",
              "recipeItems": [
                "minecraft:bow",
                "minecraft:arrow"
              ],
              "favorabilityBonuses": [
                { "level": 1, "effect": "minecraft:strength", "effectLevel": 0 },
                { "level": 2, "attribute": "minecraft:generic.movement_speed", "value": 0.10, "effect": "minecraft:strength", "effectLevel": 0 },
                { "level": 3, "attribute": "minecraft:generic.movement_speed", "value": 0.20, "effect": "minecraft:strength", "effectLevel": 1 }
              ]
            },
            {
              "key": "fisher",
              "name": "绝对不会空军的钓鱼小女仆",
              "icon": "minecraft:fishing_rod",
              "description": "专注钓鱼，有幸运加持",
              "tasks": [
                "touhou_little_maid:fishing"
              ],
              "assignmentItem": "maidtaskfilter:fisher_book",
              "recipeItems": [
                "minecraft:fishing_rod",
                "minecraft:water_bucket"
              ],
              "favorabilityBonuses": [
                { "level": 1, "effect": "minecraft:luck", "effectLevel": 0 },
                { "level": 2, "effect": "minecraft:luck", "effectLevel": 0 },
                { "level": 2, "effect": "minecraft:water_breathing", "effectLevel": 0 },
                { "level": 3, "effect": "minecraft:luck", "effectLevel": 1 }
              ]
            },
            {
              "_comment": "====== 联动职业：requiresMod 指定必需模组，未装则自动跳过 ======",
              "key": "spellblade",
              "name": "魔力涌动的法术小女仆",
              "icon": "minecraft:enchanted_book",
              "description": "使用法术战斗，需安装万法皆通",
              "requiresMod": "maidspell",
              "tasks": [
                "maidspell:spell_combat",
                "maidspell:spell_combat_far",
                "maidspell:spell_combat_melee"
              ],
              "assignmentItem": "maidtaskfilter:spellblade_book",
              "recipeItems": [
                "irons_spellbooks:arcane_essence"
              ],
              "favorabilityBonuses": [
                { "level": 1, "attribute": "minecraft:generic.armor", "value": 2.0 },
                { "level": 2, "attribute": "minecraft:generic.armor", "value": 2.0, "effect": "minecraft:resistance", "effectLevel": 0 },
                { "level": 3, "attribute": "minecraft:generic.armor", "value": 4.0, "effect": "minecraft:resistance", "effectLevel": 1 }
              ]
            },
            {
              "key": "baker",
              "name": "香喷喷的烘焙小女仆",
              "icon": "minecraft:cake",
              "description": "负责烘焙面包蛋糕，需安装女仆烘焙坊",
              "requiresMod": "maid_bakeries",
              "tasks": [
                "maid_bakeries:baking",
                "maid_bakeries:oven",
                "maid_bakeries:blender",
                "maid_bakeries:cut",
                "maid_bakeries:drink"
              ],
              "assignmentItem": "maidtaskfilter:baker_book",
              "recipeItems": [
                "maid_bakeries:craft_order",
                "maid_bakeries:oven_sticky_note"
              ],
              "favorabilityBonuses": [
                { "level": 1, "effect": "minecraft:haste", "effectLevel": 0 },
                { "level": 2, "attribute": "minecraft:generic.movement_speed", "value": 0.10, "effect": "minecraft:haste", "effectLevel": 0 },
                { "level": 3, "attribute": "minecraft:generic.movement_speed", "value": 0.20, "effect": "minecraft:haste", "effectLevel": 1 }
              ]
            },
            {
              "key": "chef",
              "name": "米其林大厨小女仆",
              "icon": "minecraft:cooked_beef",
              "description": "负责烹饪料理，需安装女仆餐厅",
              "requiresMod": "maid_restaurant",
              "tasks": [
                "maid_restaurant:cook"
              ],
              "assignmentItem": "maidtaskfilter:chef_book",
              "recipeItems": [
                "maid_restaurant:order_menu"
              ],
              "favorabilityBonuses": [
                { "level": 1, "effect": "minecraft:haste", "effectLevel": 0 },
                { "level": 2, "effect": "minecraft:haste", "effectLevel": 0 },
                { "level": 2, "effect": "minecraft:luck", "effectLevel": 0 },
                { "level": 3, "effect": "minecraft:haste", "effectLevel": 1 }
              ]
            },
            {
              "key": "waiter",
              "name": "优雅的服务员小女仆",
              "icon": "minecraft:bowl",
              "description": "负责上菜服务，需安装女仆餐厅",
              "requiresMod": "maid_restaurant",
              "tasks": [
                "maid_restaurant:waiter"
              ],
              "assignmentItem": "maidtaskfilter:waiter_book",
              "recipeItems": [
                "minecraft:bowl",
                "minecraft:bread"
              ],
              "favorabilityBonuses": [
                { "level": 1, "attribute": "minecraft:generic.movement_speed", "value": 0.15 },
                { "level": 2, "attribute": "minecraft:generic.movement_speed", "value": 0.15, "effect": "minecraft:resistance", "effectLevel": 0 },
                { "level": 3, "attribute": "minecraft:generic.movement_speed", "value": 0.30, "effect": "minecraft:speed", "effectLevel": 0 }
              ]
            },
            {
              "key": "brewer",
              "name": "踩得一脚好果汁的酿造小女仆",
              "icon": "minecraft:glass_bottle",
              "description": "负责踩踏酿造果汁酒品，需安装森罗物语：兼容（kaleidoscope_compat）",
              "requiresMod": "kaleidoscope_compat",
              "tasks": [
                "kaleidoscope_compat:pressing_tub"
              ],
              "assignmentItem": "maidtaskfilter:brewer_book",
              "recipeItems": [
                "minecraft:glass_bottle",
                "minecraft:sugar"
              ],
              "favorabilityBonuses": [
                { "level": 1, "effect": "minecraft:haste", "effectLevel": 0 },
                { "level": 2, "effect": "minecraft:haste", "effectLevel": 0 },
                { "level": 2, "effect": "minecraft:luck", "effectLevel": 0 },
                { "level": 3, "effect": "minecraft:haste", "effectLevel": 1 }
              ]
            },
            {
              "key": "operator",
              "name": "汗水就是力量的机械小女仆",
              "icon": "minecraft:iron_ingot",
              "description": "负责操作手摇工具和机械，需安装手摇工具",
              "requiresMod": "muhc",
              "tasks": [
                "muhc:hand_crank_task"
              ],
              "assignmentItem": "maidtaskfilter:operator_book",
              "recipeItems": [
                "create:precision_mechanism",
                "minecraft:copper_ingot"
              ],
              "favorabilityBonuses": [
                { "level": 1, "effect": "minecraft:haste", "effectLevel": 0 },
                { "level": 2, "effect": "minecraft:haste", "effectLevel": 0 },
                { "level": 2, "effect": "minecraft:strength", "effectLevel": 0 },
                { "level": 3, "effect": "minecraft:haste", "effectLevel": 1 }
              ]
            },
            {
              "key": "omni",
              "name": "全知全能的女仆",
              "icon": "minecraft:nether_star",
              "description": "解锁全部任务，无任何限制。创造/初始女仆限定。",
              "tasks": [],
              "assignmentItem": "maidtaskfilter:omni_book",
              "recipeItems": [],
              "favorabilityBonuses": []
            }
          ]
        }
        """;

        Files.writeString(CONFIG_FILE, json, StandardCharsets.UTF_8);
        MaidTaskFilterMod.LOGGER.info("[MaidTaskFilter v2] Created default jobs.json with {} jobs", 12);
    }

    // ---- 查询 API ----

    public static JobDefinition getJob(String key) { return JOBS.get(key); }

    public static Collection<JobDefinition> getAllJobs() {
        return Collections.unmodifiableCollection(JOBS.values());
    }

    public static List<String> getCommonTasks() {
        return Collections.unmodifiableList(COMMON_TASKS);
    }

    public static Map<String, List<String>> getConditionalCommonTasks() {
        return Collections.unmodifiableMap(CONDITIONAL_COMMON_TASKS);
    }

    public static int jobCount() { return JOBS.size(); }
}
