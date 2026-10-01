package com.oathbreaker.naturalskillgrowth;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = NaturalSkillGrowth.MODID)
public class SkillGainEvents
{
    @SubscribeEvent
    private static void onBlockBreak(BlockEvent.BreakEvent event)
    {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        // it actually works for ore too - it always has the mature state
        if (!isCropMature(event.getState())) return;

        SkillDefinitionsHandler.SkillGainInfo info = SkillDefinitionsHandler.classifyBlock(event.getState());
        if (info == null) return;

        grantSkill(player, info.skillId(), info.weight());
    }

    @SubscribeEvent
    private static void onLivingDeath(LivingDeathEvent event)
    {
        LivingEntity killed = event.getEntity();

        // do not add score for killing player
        if (killed instanceof Player) return;
        if (killed instanceof ArmorStand) return;

        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer attacker)) return;

        SkillDefinitionsHandler.SkillGainInfo info = SkillDefinitionsHandler.classifyEntity(SkillIds.WARRIOR, killed);

        if (info == null || info.weight() <= 0.0f) return;

        grantSkill(attacker, info.skillId(), info.weight());
    }

    private static void grantSkill(ServerPlayer player, ResourceLocation skillId, float weight)
    {
        PlayerSkillsData currentData = player.getData(ModAttachments.PLAYER_SKILLS);
        PlayerSkillsData updatedData = currentData.applyAction(skillId, weight);
        player.setData(ModAttachments.PLAYER_SKILLS, updatedData);
    }

    private static boolean isCropMature(BlockState state)
    {
        // some default crops
        if (state.getBlock() instanceof CropBlock cropBlock)
            return cropBlock.isMaxAge(state);

        // cocoa
        if (state.getBlock() instanceof CocoaBlock cocoaBlock)
            return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;

        // nether wart
        if (state.getBlock() instanceof NetherWartBlock)
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;

        // TODO: add bamboo and sugar cane logic (abuse-free!)

        return true;
    }
}
