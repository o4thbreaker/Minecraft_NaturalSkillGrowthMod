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

    public static PlayerSkillsData createDefault() {
        return new PlayerSkillsData(new HashMap<>(), Optional.empty());
    }

    /**
     * Проверка на фокус на навыке. По факту проверяет на focusedSkill == skillId
     * @param skillId айдишник проверяемого навыка
     * @return наличие фокуса
     */
    public boolean isFocusedOn(ResourceLocation skillId) {
        return focusedSkill.isPresent() && focusedSkill.get().equals(skillId);
    }

    /**
     * Безусловно ставит фокус на навык
     * ВАЖНО: метод не проверяет никакие значения, просто ставит фокус
     * @param skillId навык для фокусировки
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

    public float getAttention(ResourceLocation skillId) {
        throw new UnsupportedOperationException("TODO: A0 из SkillDefinitions.get(skillId).floor(), если ключа нет");
    }

    public float getPeak(ResourceLocation skillId) {
        throw new UnsupportedOperationException("TODO: имплементировать");
    }

    public float getEfficiency(ResourceLocation skillId) {
        throw new UnsupportedOperationException("TODO: имплементировать");
    }

    public PlayerSkillsData applyAction(ResourceLocation skillId, float weight) {
        throw new UnsupportedOperationException("TODO: имплементировать");
    }
}

