package com.oathbreaker.naturalskillgrowth;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

@EventBusSubscriber(modid = NaturalSkillGrowth.MODID)
public class SkillEffectsEvents
{
    private static final ResourceLocation ATTACK_SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(NaturalSkillGrowth.MODID, "warrior_skill_bonus");

    /**
     * Вызывается при заходе игрока,
     * нужно чтобы применился аттрибут (на данный момент это скорость атаки)
     * к игроку.
     *
     * @param event     событие захода на сервер
     */
    @SubscribeEvent
    static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            refreshAttackSpeed(player);
        }
    }

    // ┌──────────────────────────────────────────────────────────────┐
    // │ Miner: breaking ore speed + TODO: additional ore drop chance │
    // └──────────────────────────────────────────────────────────────┘

    @SubscribeEvent
    private static void onBreakSpeed(PlayerEvent.BreakSpeed event)
    {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ResourceLocation skillId = SkillDefinitionsHandler.classifyBlock(event.getState()).skillId();
        if (skillId == null) return;

        float efficiency = serverPlayer.getData(ModAttachments.PLAYER_SKILLS).getEfficiency(skillId);
        float speedMultiplier = efficiency / 100.0f;

        event.setNewSpeed(event.getNewSpeed() * speedMultiplier);
    }

    // ┌──────────────────────────────────────────────────────┐
    // │ Warrior: hit recovery speed + specialist flat-damage │
    // └──────────────────────────────────────────────────────┘

    public static void refreshAttackSpeed(ServerPlayer player)
    {
        AttributeInstance attribute = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attribute == null) return;

        attribute.removeModifier(ATTACK_SPEED_MODIFIER_ID);

        float efficiency = player.getData(ModAttachments.PLAYER_SKILLS).getEfficiency(SkillIds.WARRIOR);

        // only because ADD_MULTIPLIED_TOTAL is adding (not multiplying)
        float amount = (efficiency - 100.0f) / 100.0f;

        attribute.addPermanentModifier(new AttributeModifier(
                ATTACK_SPEED_MODIFIER_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    @SubscribeEvent
    private static void onIncomingDamage(LivingIncomingDamageEvent event)
    {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;

        PlayerSkillsData data = attacker.getData(ModAttachments.PLAYER_SKILLS);

        if (data.isSpecialist(SkillIds.WARRIOR))
        {
            float bonusDamage = SkillDefinitionsHandler.get(SkillIds.WARRIOR).specialistMagnitude();

            event.setAmount(event.getAmount() + bonusDamage);
        }
    }

    // ┌──────────────────────────────────────────────────────────┐
    // │ Farmer: crop loose chance + TODO: specialist grow speed  │
    // └──────────────────────────────────────────────────────────┘

     @SubscribeEvent
     private static void onBlockDrops(BlockDropsEvent event)
     {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;

        BlockState state = event.getState();
        SkillDefinitionsHandler.SkillGainInfo info = SkillDefinitionsHandler.classifyBlock(state);
        if (info == null || !info.skillId().equals(SkillIds.FARMER)) return;

        /// NOTE: uncomment to apply debuff only to IMMATURE crops
        //if (CropUtils.isCropMature(event.getState())) return;

        PlayerSkillsData data = player.getData(ModAttachments.PLAYER_SKILLS);
        SkillDefinition def = SkillDefinitionsHandler.get(SkillIds.FARMER);

        float efficiency = data.getEfficiency(SkillIds.FARMER);
        float failChance = def.maxFailChance() * (100.0f - efficiency) / (100.0f - def.floor());
        failChance = Math.max(0f, failChance / 100.0f);

        if (player.level().getRandom().nextFloat() < failChance)
        {
            event.getDrops().clear();
            return;
        }

        // also try to give more drop if farmer is a specialist
        addExtraCropDrop(player, data, def, event);
     }

    /**
     * Хелпер-метод для обработки способности специалиста:
     * увеличивать кол-во дропа урожая
     *
     * @param player    специалист
     * @param data      данные для обработки
     * @param def       описание навыка
     * @param event     событие выпадения дропа
     */
     private static void addExtraCropDrop(ServerPlayer player, PlayerSkillsData data, SkillDefinition def, BlockDropsEvent event)
     {
         if (!data.isSpecialist(SkillIds.FARMER)) return;

         if (player.level().getRandom().nextFloat() < def.specialistMagnitude() / 100.0f
                                                            && !event.getDrops().isEmpty())
         {
             ItemEntity original = event.getDrops().getFirst();
             ItemStack copiedStack = original.getItem().copy();
             copiedStack.setCount(1);

             ItemEntity extra = new ItemEntity(original.level(), original.getX(), original.getY(), original.getZ(), copiedStack);
             event.getDrops().add(extra);
         }
     }

     // NOTE: maybe it doesn't really do anything
     // have to looks for yourself whether Result.GROW either
     // just allows to grow or stacks up over vanilla tick
     @SubscribeEvent
     private static void onCropGrow(CropGrowEvent.Pre event)
     {
         Level level = (Level) event.getLevel();
         BlockPos pos = event.getPos();
         SkillDefinition def = SkillDefinitionsHandler.get(SkillIds.FARMER);

         Player nearest = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), def.auraRadius(), false);
         if (!(nearest instanceof ServerPlayer farmer)) return;

         if (!farmer.getData(ModAttachments.PLAYER_SKILLS).isSpecialist(SkillIds.FARMER)) return;

         if (level.getRandom().nextFloat() < def.specialistMagnitude() / 100.0f)
         {
             event.setResult(CropGrowEvent.Pre.Result.GROW);
         }
     }
}
