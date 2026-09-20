package com.oathbreaker.naturalskillgrowth;

import net.minecraft.resources.ResourceLocation;

/**
 * Константы для текущих навыков. Система ими не ограничена -
 * добавление нового навыка это 1 новая константа тут для удобства + 2 новых
 * JSON в датапаке. Ничего в PlayerSkillsData/SkillDefinitions менять не нужно.
 */
public class SkillIds
{
    public static final ResourceLocation MINER = ResourceLocation.fromNamespaceAndPath(NaturalSkillGrowth.MODID, "miner");
    public static final ResourceLocation FARMER = ResourceLocation.fromNamespaceAndPath(NaturalSkillGrowth.MODID, "farmer");
    public static final ResourceLocation WARRIOR = ResourceLocation.fromNamespaceAndPath(NaturalSkillGrowth.MODID, "warrior");

}
