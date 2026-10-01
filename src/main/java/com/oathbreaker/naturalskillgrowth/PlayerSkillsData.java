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
     * Проверка на фокус на навыке. По факту проверяет на focusedSkill == skillId.
     *
     * @param skillId   рассматриваемый навык
     * @return          наличие фокуса
     */
    public boolean isFocusedOn(ResourceLocation skillId) {
        return focusedSkill.isPresent() && focusedSkill.get().equals(skillId);
    }

    /**
     * Wither-метод, который безусловно ставит фокус на навык.
     * ВАЖНО: метод не проверяет никакие значения, просто ставит фокус.
     *
     * @param skillId   рассматриваемый навык
     * @return          обновленный SkillsData с новым фокусом
     */
    public PlayerSkillsData withFocus(ResourceLocation skillId) {
        return new PlayerSkillsData(progress, Optional.of(skillId));
    }

    /**
     * Wither-метод, который снимает фокус с навыка.
     * ВАЖНО: метод не проверяет никакие значения, просто снимает фокус.
     *
     * @return обновленный SkillsData без фокуса
     */
    public PlayerSkillsData withUnfocused() {
        return new PlayerSkillsData(progress, Optional.empty());
    }

    public PlayerSkillsData withoutSkill(ResourceLocation skillId)
    {
        if (!progress.containsKey(skillId)) return this;

        Map<ResourceLocation, SkillProgress> newProgress = new HashMap<>(this.progress);
        newProgress.remove(skillId);

        Optional<ResourceLocation> newFocus = isFocusedOn(skillId) ? Optional.empty() : focusedSkill;

        return new PlayerSkillsData(newProgress, newFocus);
    }

    // NOTE: MAIN METHODS

    /**
     * Считает текущую эффективность.
     * Если эффективность дошла до max-значения и была включена фокусировка на навыке,
     * то эффективность начинает изменяться от max до max+focusBonus
     * в зависимости от текущего значения focusAttention
     *
     * @param skillId   рассматриваемый навык
     * @return          подсчитанная эффективность (или max, если вылезло за него без разрешения)
     */
    public float getEfficiency(ResourceLocation skillId)
    {
        SkillDefinition def = SkillDefinitionsHandler.get(skillId);

        float focusAttention = getFocusAttention(skillId);
        if (focusAttention > EPSILON)
        {
            return def.max() + def.focusBonus() * getFocusAttention(skillId);
        }

        // текущая эффективность линейно зависит от attention.
        // чем больше attention, тем выше эффективность по этому навыку.
        float attention = getAttention(skillId);

        // TODO: fix the equal decay by adding the extraFloorPenalty
        float efficiency = def.floor() + (def.max() - def.floor()) * attention;

        return Math.min(efficiency, def.max());
    }

    /**
     * Проверяет, достиг ли efficiency своего потолка
     * (точнее, с погрешностью EPSILON)
     *
     * @param skillId   рассматриваемый навык
     * @return          достиг
     */
    public boolean isEfficiencyAtMax(ResourceLocation skillId)
    {
        SkillDefinition def = SkillDefinitionsHandler.get(skillId);
        float efficiency = def.floor() + (def.max() - def.floor()) * getAttention(skillId);
        float distanceToMax = def.max() - efficiency;
        return distanceToMax <= (def.max() * EPSILON) || efficiency >= def.max();
    }

    /**
     * Применяет "действие" (добыча руды, посадка урожая, убийство моба, etc).
     * В зависимости от параметра weight меняется "важность" этого действия на шкалу роста соответствующего навыка.
     * При любом применении действия теряется часть прогресса в других навыках.
     * Значение, насколько "увяняет" навык зависит от weight и decaySpeed.
     * Если есть фокус на навыке и прогресс превышает max, то действие применяется к focus-шкале.
     *
     * @param skillId   навык, к которому будет применено действие
     * @param weight    величина "важности" этого действия на шкалу прогресса
     * @return          новый объект с обновленными значениями ВСЕХ навыков
     */
    public PlayerSkillsData applyAction(ResourceLocation skillId, float weight)
    {
        // if frozen due to other skill is being focused and maxed - do nothing
        if (isFrozen()) { return this; }

        Map<ResourceLocation, SkillProgress> newProgress = new HashMap<>(this.progress);

        SkillProgress currentProgress = newProgress.getOrDefault(skillId,
                new SkillProgress(getAttention(skillId), getFocusAttention(skillId), getPeak(skillId)));

        if (isFocusedOn(skillId) && isEfficiencyAtMax(skillId))
        {
            // a = a + w * (1-a)
            float calculatedFocusAttention = currentProgress.focusAttention() + weight * (1.0f - currentProgress.focusAttention());
            float newFocusAttention = Math.min(1.0f, calculatedFocusAttention);

            // updated progress is like the current progress but with focus attention
            SkillProgress updatedProgress = currentProgress.withFocusAttention(newFocusAttention);
            newProgress.put(skillId, updatedProgress);
        }
        else
        {
            float calculatedAttention = currentProgress.attention() + weight * (1 - currentProgress.attention());
            float newAttention = Math.min(1.0f, calculatedAttention);

            SkillProgress updatedProgress = currentProgress.withAttention(newAttention);

            if (newAttention > getPeak(skillId))
            {
                updatedProgress = updatedProgress.withPeak(newAttention);
            }

            newProgress.put(skillId, updatedProgress);
        }

        for (ResourceLocation id : SkillDefinitionsHandler.allSkillIds())
        {
            if (id.equals(skillId)) continue;
            if (focusedSkill.isPresent() && id.equals(focusedSkill.get())) continue;

            SkillDefinition def = SkillDefinitionsHandler.get(id);
            float a0 = calculateA0(id);
            float a = getAttention(id);
            int skillsAmount = SkillDefinitionsHandler.allSkillIds().size();

            float drain = (1 - a0) / (a0 * (skillsAmount - 1)) * def.decaySpeed();

            float drainedAttention = Math.max(0f, a - weight * drain * a);

            SkillProgress otherProgress = newProgress.getOrDefault(id,
                    new SkillProgress(a, getFocusAttention(id), getPeak(id)));

            newProgress.put(id, otherProgress.withAttention(drainedAttention));
        }

        return new PlayerSkillsData(newProgress, focusedSkill);
    }

    /**
     * Получает уровень вовлеченности в навык.
     * Т.е. если игрок вкладывается в навык, он растет быстрее,
     * НО если игрок переключится и начнет вкладываться в другой,
     * тот будет расти медленнее, чем изначальный, т.е. attention уменьшится,
     * потому что смена профессии - тяжелое дело.
     *
     * @param   skillId рассматриваемый навык
     * @return  текущее значение вовлеченности ИЛИ "нетронутую" вовлеченность
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
     * Получает уровень вовлеченность при фокусировке на навык.
     * @param   skillId рассматриваемый навык
     * @return  текущее значение сфокусированной вовлеченности или дефолтная вовлеченность
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
     * Наивысшее когда-либо достигнутое attention по этому навыку.
     * @param   skillId рассматриваемый навык
     * @return  либо значение peak, либо то же, что вернет getAttention по умолчанию (т.е. A0)
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
     * Хелпер-метод, который подсчитывает "нетронутую" вовлеченность, как у свежего навыка.
     * Формулу А0 (aka attention0) - взял из аналогичной формулы подсчета getEfficiency.
     * @param   skillId рассматриваемый навык
     * @return  посчитанная вовлеченность как при старте игры
     */
    private float calculateA0(ResourceLocation skillId)
    {
        SkillDefinition def = SkillDefinitionsHandler.get(skillId);
        return (def.startEfficiency() - def.floor()) / (def.max() - def.floor());
    }

    /**
     * Если игрок фокусируется на навыке и этот навык достиг максимального значения - true.
     * @return текущее состояние заморозки прогресса
     */
    private boolean isFrozen()
    {
        return focusedSkill.isPresent() && getFocusAttention(focusedSkill.get()) >= 1.0 - EPSILON;
    }
}

