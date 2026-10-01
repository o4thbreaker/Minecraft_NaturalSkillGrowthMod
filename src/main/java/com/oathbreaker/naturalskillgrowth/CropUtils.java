package com.oathbreaker.naturalskillgrowth;

import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Класс-хелпер для работы с блоками урожая
 */
public class CropUtils
{
    public static boolean isCropMature(BlockState state)
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

        // should work on ore as its state is always "mature"
        return true;
    }
}
