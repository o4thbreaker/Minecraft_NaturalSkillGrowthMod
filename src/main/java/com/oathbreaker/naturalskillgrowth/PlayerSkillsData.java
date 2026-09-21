package com.oathbreaker.naturalskillgrowth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Attachment-данные игрока. Immutable record - applyAction()/withFocus()/withUnfocused()
 * должны возвращать НОВЫЙ объект, а не мутировать текущий на месте.
 *
 * @param progress      прогресс по каждому известному навыку (attention + peak).
 *                       Если ключа нет для какого-то навыка из SkillDefinitions.allSkillIds() -
 *                       считается, что игрок ни разу его не трогал (см. getAttention/getPeak).
 * @param focusedSkill  какой навык сейчас сфокусирован (см. механику "Специалист"),
 *                       пусто - фокуса нет. Одновременно можно фокусировать только один навык.
 */
public record PlayerSkillsData(Map<ResourceLocation, SkillProgress> progress, Optional<ResourceLocation> focusedSkill)
{
    public static final Codec<PlayerSkillsData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(ResourceLocation.CODEC, SkillProgress.CODEC)
                    .fieldOf("progress").forGetter(PlayerSkillsData::progress),
            ResourceLocation.CODEC.optionalFieldOf("focused_skill")
                    .forGetter(PlayerSkillsData::focusedSkill)
    ).apply(instance, PlayerSkillsData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerSkillsData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, SkillProgress.STREAM_CODEC),
            PlayerSkillsData::progress,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC),
            PlayerSkillsData::focusedSkill,
            PlayerSkillsData::new
    );

    private static final float EPSILON = 0.005f;

    public static PlayerSkillsData createDefault() {
        return new PlayerSkillsData(new HashMap<>(), Optional.empty());
    }

    /**
     * Проверка на фокус на навыке. По факту проверяет на focusedSkill == skillId
     * @param skillId рассматриваемый навык
     * @return наличие фокуса
     */
    public boolean isFocusedOn(ResourceLocation skillId) {
        return focusedSkill.isPresent() && focusedSkill.get().equals(skillId);
    }

    /**
     * Безусловно ставит фокус на навык
     * ВАЖНО: метод не проверяет никакие значения, просто ставит фокус
     * @param skillId рассматриваемый навык
     * @return обновленный SkillsData с новым фокусом
     */
    public PlayerSkillsData withFocus(ResourceLocation skillId) {
        return new PlayerSkillsData(progress, Optional.of(skillId));
    }

    /**
     * Снимает фокус с навыка
     * ВАЖНО: метод не проверяет никакие значения, просто снимает фокус
     * @return обновленный SkillsData без фокуса
     */
    public PlayerSkillsData withUnfocused() {
        return new PlayerSkillsData(progress, Optional.empty());
    }

    // MAIN METHODS

    /**
     * Считает текущую эффективность
     * Если эффективность дошла до max-значения и была включена фокусировка на навыке,
     * то эффективность начинает изменяться от max до max+focusBonus
     * в зависимости от текущего значения focusAttention
     * @param skillId рассматриваемый навык
     * @return подсчитанная эффективность (или max, если вылезло за него без разрешения)
     */
    public float getEfficiency(ResourceLocation skillId)
    {
        SkillDefinition def = SkillDefinitionsHandler.get(skillId);

        float attention = getAttention(skillId);

        // текущая эффективность линейно зависит от attention.
        // чем больше attention, тем выше эффективность по этому навыку.
        float efficiency = def.floor() + (def.max() - def.floor()) * attention;

        float distanceToMax = def.max() - efficiency;

        boolean isEfficiencyMaxed = (distanceToMax <= (def.max() * EPSILON)) || (efficiency >= def.max());
        if (isFocusedOn(skillId) && isEfficiencyMaxed)
        {
            return def.max() + (def.focusBonus() * getFocusAttention(skillId));
        }

        return Math.min(efficiency, def.max());
    }

    public PlayerSkillsData applyAction(ResourceLocation skillId, float weight) {
        throw new UnsupportedOperationException("TODO: имплементировать");
    }

    /**
     * Получает уровень вовлеченности в навык
     * Т.е. если игрок вкладывается в навык, он растет быстрее
     * Но если игрок переключится и начнет вкладываться в другой,
     * тот будет расти медленнее, чем изначальный, т.е. attention уменьшится
     * Потому что смена профессии - тяжелое дело
     * @param skillId рассматриваемый навык
     * @return текущее значение вовлеченности ИЛИ "нетронутую" вовлеченность
     */
    private float getAttention(ResourceLocation skillId)
    {
        if (progress.containsKey(skillId))
        {
            return progress.get(skillId).attention();
        }

        return calculateA0(skillId);
    }

    /**
     * Получает уровень вовлеченность при фокусировке на навык
     * @param skillId рассматриваемый навык
     * @return текущее значение сфокусированной вовлеченности или дефолтная вовлеченность
     */
    private float getFocusAttention(ResourceLocation skillId)
    {
        if (progress.containsKey(skillId))
        {
            return progress.get(skillId).focusAttention();
        }
        return 0.0f;
    }

    /**
     * Наивысшее когда-либо достигнутое attention по этому навыку
     * @param skillId рассматриваемый навык
     * @return либо значение peak, либо то же, что вернет getAttention по умолчанию (т.е. A0)
     */
    private float getPeak(ResourceLocation skillId)
    {
        if (progress.containsKey(skillId))
        {
            return progress.get(skillId).peak();
        }
        return calculateA0(skillId);
    }

    /**
     * Хелпер-метод, который подсчитывает "нетронутую" вовлеченность, как у свежего навыка
     * формулу А0 (aka attention0 - взял из аналогичной формулы подсчета getEfficiency)
     * @param skillId рассматриваемый навык
     * @return посчитанная вовлеченность как при старте игры
     */
    private float calculateA0(ResourceLocation skillId)
    {
        SkillDefinition def = SkillDefinitionsHandler.get(skillId);

        return (def.startEfficiency() - def.floor()) / (def.max() - def.floor());
    }
}

