package com.oathbreaker.naturalskillgrowth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;


/**
 * Прогресс игрока по ОДНОМУ навыку
 *
 * @param attention текущая "вовлеченность" (A), 0..1. Растет при использовании навыка,
 *                  падает при использовании остальных.
 * @param peak      пиковое значение attention, которого КОГДА-ЛИБО достигал этот навык
 *                  у этого игрока. Само по себе никогда не уменьшается, даже если текущий
 *                  attention упал от floor. Нужно для правила "если уже вложился в
 *                  шахтерство до 96, а потом решил качать фермерство - фермерство будет
 *                  расти медленнее, чем росло шахтерство с нуля".
 *                  Как именно peak ОСТАЛЬНЫХ навыков тормозит рост ТЕКУЩЕГО - см. TODO в
 *                  PlayerSkillsData.applyAction().
 */
public record SkillProgress(float attention, float peak)
{
    public static final Codec<SkillProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("attention").forGetter(SkillProgress::attention),
            Codec.FLOAT.fieldOf("peak").forGetter(SkillProgress::peak)
    ).apply(instance, SkillProgress::new));

    public static final StreamCodec<ByteBuf, SkillProgress> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SkillProgress::attention,
            ByteBufCodecs.FLOAT, SkillProgress::peak,
            SkillProgress::new
    );
}
