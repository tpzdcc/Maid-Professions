package com.maidtaskfilter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 任务数据的包装类 —— v2 扩展为存储 tasks + jobKey。
 *
 * 使用 RecordCodecBuilder 而非 Codec.STRING：TLM 的 TaskDataRegister.writeSaveData
 * 返回 CompoundTag，Codec.STRING 在 NbtOps 下产生 StringTag 强转失败导致崩溃。
 * RecordCodecBuilder 产生 CompoundTag{"tasks": "...", "job": "..."}，类型兼容。
 *
 * 关键：jobKey 存进 TaskData 后会自动通过 TLM 的 setAndSyncData 同步到客户端，
 * 解决了 persistentData 只存服务端的问题。
 */
public record TaskData(String tasks, String jobKey) {

    public static final Codec<TaskData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("tasks").forGetter(TaskData::tasks),
                    Codec.STRING.fieldOf("job").forGetter(TaskData::jobKey)
            ).apply(instance, TaskData::new)
    );

    /** 兼容旧版仅 tasks 的构造 */
    public TaskData(String tasks) {
        this(tasks, "");
    }
}
