package com.oathbreaker.naturalskillgrowth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Настройки одного навыка. Грузится из датапака: data/naturalskillsgrowth/skills/<id>.json
 *
 * Каждое поле, кроме floor, необязательно в JSON и имеет разумный дефолт -
 * так что можно начать с минимального файла ({"floor": 50.0}) и дописывать
 * остальное по мере необходимости.
 *
 * @param startEfficiency       стартовый показатель навыка (условно 85 из 100)
 * @param floor                 минимум, до которого падает ЗАБРОШЕННЫЙ навык (0..100)
 * @param max                   потолок ОБЫЧНОГО (не сфокусированного) роста, обычно 100 = ваниль
 * @param decaySpeed            во сколько раз быстрее/медленнее ЭТОТ навык увядает
 *                              у соседей относительно базового темпа (1.0 = обычный темп)
 * @param focusBonus            (N) насколько можно превысить max при включённом фокусе (max+N)
 * @param extraFloorPenalty     (M) когда ЭТОТ навык сфокусирован - на сколько глубже,
 *                              чем обычный floor, падают ВСЕ ОСТАЛЬНЫЕ навыки (floor-M)
 * @param specialistMagnitude   (K) величина бонуса способности специалиста; смысл K свой
 *                              для каждого класса (флэт-урон у воина, %-шанс у остальных)
 * @param switchRateMultiplier  во сколько раз медленнее растет ЭТОТ навык, если игрок уже
 *                              исторически вложился в другие (см. PlayerSkillsData.peak)
 * @param affectedBlocks        блоки, которые этот навык затрагивает - и для начисления опыта,
 *                              и для применения эффектов (руда/камень у шахтера, урожай у фермера).
 */
public record SkillDefinition(
        float startEfficiency,
        float floor,
        float max,
        float decaySpeed,
        float focusBonus,
        float extraFloorPenalty,
        float specialistMagnitude,
        float switchRateMultiplier,
        List<ResourceLocation> affectedBlocks
) {

    public static final Codec<SkillDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("start_efficiency").forGetter(SkillDefinition::startEfficiency),
            Codec.FLOAT.fieldOf("floor").forGetter(SkillDefinition::floor),
            Codec.FLOAT.optionalFieldOf("max", 100.0f).forGetter(SkillDefinition::max),
            Codec.FLOAT.optionalFieldOf("decay_speed", 1.0f).forGetter(SkillDefinition::decaySpeed),
            Codec.FLOAT.optionalFieldOf("focus_bonus", 0.0f).forGetter(SkillDefinition::focusBonus),
            Codec.FLOAT.optionalFieldOf("extra_floor_penalty", 0.0f).forGetter(SkillDefinition::extraFloorPenalty),
            Codec.FLOAT.optionalFieldOf("specialist_magnitude", 0.0f).forGetter(SkillDefinition::specialistMagnitude),
            Codec.FLOAT.optionalFieldOf("switch_rate_multiplier", 1.0f).forGetter(SkillDefinition::switchRateMultiplier),
            ResourceLocation.CODEC.listOf().optionalFieldOf("affected_blocks", List.of())
                    .forGetter(SkillDefinition::affectedBlocks)
    ).apply(instance, SkillDefinition::new));
}
