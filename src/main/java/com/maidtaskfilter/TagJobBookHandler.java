package com.maidtaskfilter;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * tag 转职书 —— 数据包驱动的转职书扩展通道。
 *
 * <p><b>机制照搬 TLM 驯服物品的「物品标签」做法</b>：
 * TLM 用 tag {@code touhou_little_maid:maid_tamed_item} 定义驯服物，
 * 整合包用数据包覆写该 tag 就能指定任意物品当驯服物。
 * 我们同理：任意物品只要出现在 tag {@code maidtaskfilter:job_<职业key>} 里，
 * 右键女仆即可转成该职业。
 *
 * <p><b>零编译新增转职书三步</b>：
 * <ol>
 *   <li>{@code jobs.json} 加职业（本模组本来就数据驱动）</li>
 *   <li>用 KubeJS 等注册一个物品（{@code StartupEvents.registry('item', ...)}）</li>
 *   <li>数据包加 tag：{@code data/maidtaskfilter/tags/items/job_<key>.json}
 *       → {@code {"values": ["<新物品id>"]}}；祭坛配方同理放
 *       {@code data/maidtaskfilter/recipes/altar/}</li>
 * </ol>
 *
 * <p><b>与内置转职书的关系</b>：内置 12 本书是 {@link JobBookItem} 类，
 * 自带行为，且默认<b>不</b>在任何 {@code job_*} tag 里 —— 它们走物品类路径，
 * 不经过本处理器。若整合包作者故意把某内置书写进别的 {@code job_*} tag，
 * 以 tag 为准（本事件先于物品方法触发）。
 *
 * <p>转职逻辑调用 {@link MaidTaskFilterMod#tryApplyJobBook}，与物品类路径同一入口。
 */
@Mod.EventBusSubscriber(modid = MaidTaskFilterMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TagJobBookHandler {

    private TagJobBookHandler() {}

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // 只处理服务端：客户端取消会造成预测不同步；转职数据靠 TaskData 同步
        if (event.getLevel().isClientSide()) return;
        if (!(event.getTarget() instanceof EntityMaid maid)) return;

        ItemStack stack = event.getItemStack();
        // 遍历 jobs.json 里全部职业，看手里物品命中哪个 job_<key> tag。
        // 职业数量很少（十几个），每次右键现场构建 TagKey 的开销可忽略。
        for (JobDefinition job : JobConfig.getAllJobs()) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM,
                    new ResourceLocation(MaidTaskFilterMod.MOD_ID, "job_" + job.key()));
            if (!stack.is(tag)) continue;

            InteractionResult result = MaidTaskFilterMod.tryApplyJobBook(
                    maid, event.getEntity(), job.key(), stack);
            // 取消原版交互：命中即转职，不再让物品继续走原版右键逻辑
            event.setCanceled(true);
            event.setCancellationResult(result);
            return;
        }
    }
}
