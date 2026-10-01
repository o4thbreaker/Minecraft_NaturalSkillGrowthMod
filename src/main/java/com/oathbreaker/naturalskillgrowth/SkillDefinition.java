package com.oathbreaker.naturalskillgrowth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;

/**
 * Настройки одного навыка. Грузится из датапака: data/naturalskillsgrowth/skills/<id>.json
 *
 * Каждое поле, кроме floor, необязательно в JSON и имеет разумный дефолт -
 * так что можно начать с минимального файла ({"floor": 50.0}) и дописывать
 * остальное по мере необходимости.
 *
 * @param startEfficiency       стартовый показатель навыка (условно 85 из 100).
 * @param floor                 минимум, до которого падает ЗАБРОШЕННЫЙ навык (0..100).
 * @param max                   потолок ОБЫЧНОГО (не сфокусированного) роста, обычно 100 = ваниль.
 * @param decaySpeed            во сколько раз быстрее/медленнее ЭТОТ навык увядает
 *                              у соседей относительно базового темпа (1.0 = обычный темп).
 * @param focusBonus            (N) насколько можно превысить max при включённом фокусе (max+N).
 * @param extraFloorPenalty     (M) когда ЭТОТ навык сфокусирован - на сколько глубже,
 *                              чем обычный floor, падают ВСЕ ОСТАЛЬНЫЕ навыки (floor-M).
 * @param specialistMagnitude   (K) величина бонуса способности специалиста; смысл K свой
 *                              для каждого класса (флэт-урон у воина, %-шанс у остальных).
 * @param switchRateMultiplier  во сколько раз медленнее растет ЭТОТ навык, если игрок уже
 *                              исторически вложился в другие (см. PlayerSkillsData.peak).
 * @param maxFailChance         максимальный шанс неудачи (0..100). Сейчас используется только
 *                              для фермера, чтобы отразить шанс невыпадения урожая
 *                              при низком навыке.
 * @param affectedBlocks        блоки и их вклад в навык (weight), которые этот навык затрагивает - и для начисления опыта,
 *                              и для применения эффектов (руда/камень у шахтера, урожай у фермера).
 * @param defaultEntityWeight   дефолтный вклад (weight) за убийство любой сущности, не прописанной явно
 *                              в affectedEntities (например, 0.01 для базовых мобов).
 * @param affectedEntities      сущности и их точечный вклад в навык (weight), заменяющий дефолтный
 *                              (например, 1.0 за иссушителя, 2.0 за дракона).
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
        float maxFailChance,
        float auraRadius,
        Map<ResourceLocation, Float> affectedBlocks,
        float defaultEntityWeight,
        Map<ResourceLocation, Float> affectedEntities
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
            Codec.FLOAT.optionalFieldOf("max_fail_chance", 100.0f).forGetter(SkillDefinition::maxFailChance),
            Codec.FLOAT.optionalFieldOf("aura_radius", 200.0f).forGetter(SkillDefinition::auraRadius),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.FLOAT)
                    .optionalFieldOf("affected_blocks", Map.of())
                    .forGetter(SkillDefinition::affectedBlocks),
            Codec.FLOAT.optionalFieldOf("default_entity_weight", 0.01f).forGetter(SkillDefinition::defaultEntityWeight),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.FLOAT)
                    .optionalFieldOf("affected_entities", Map.of())
                    .forGetter(SkillDefinition::affectedEntities)
            ).apply(instance, SkillDefinition::new));
}
