package com.oathbreaker.naturalskillgrowth;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SkillDefinitionsHandler extends SimpleJsonResourceReloadListener
{
    public record SkillGainInfo(ResourceLocation skillId, float weight) {}
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Map<ResourceLocation, SkillDefinition> DEFINITIONS = Map.of();
    private static Map<ResourceLocation, SkillGainInfo> BLOCK_TO_SKILL_MAP = Map.of();
    private static Map<ResourceLocation, SkillGainInfo> ENTITY_TO_SKILL_MAP = Map.of();


    public SkillDefinitionsHandler()
    {
        super(new Gson(), "skills");
    }

    /**
     * Вызывается на создании мира, захода на сервер, при /reload
     * Перебирает всю мапу loaded, парсит к SkillDefinition
     * при успехе, загружаем в DEFINITIONS, а также
     * записываем все ассоциированные блоки с профессией в BLOCK_TO_SKILL_MAP
     * @param loaded мапа полученных айдишников (в сыром GSON-формате), формируется на асинхронном этапе подготовки
     * @param manager менеджер ресурсов Майнкрафта, понадобится, если нужно прочитать не-JSON навык (ex. текстура)
     * @param profiler инструмент для профилирования
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> loaded, ResourceManager manager, ProfilerFiller profiler)
    {
        Map<ResourceLocation, SkillDefinition> parsedDefinitions = new HashMap<>();
        Map<ResourceLocation, SkillGainInfo> tempBlockMap = new HashMap<>();

        loaded.forEach((location, jsonElement) ->
        {
            SkillDefinition.CODEC.parse(JsonOps.INSTANCE, jsonElement)
                    .resultOrPartial(errorMessage -> LOGGER.error("Ошибка чтения навыка {}: {}", location, errorMessage))
                    .ifPresent(skillDefinition ->
                    {
                        parsedDefinitions.put(location, skillDefinition);

                        skillDefinition.affectedBlocks().forEach((blockId, weight) ->
                        {
                            SkillGainInfo newInfo = new SkillGainInfo(location, weight);
                            SkillGainInfo existing = tempBlockMap.put(blockId, newInfo);

                            if (existing != null) { LOGGER.warn("Блок {} заявлен сразу двумя навыками: {} и {} - используется последний",
                                    blockId, existing.skillId(), location); }
                        });
                    });
        });

        DEFINITIONS = Map.copyOf(parsedDefinitions);
        BLOCK_TO_SKILL_MAP = Map.copyOf(tempBlockMap);
        LOGGER.info("Успешно загружено навыков: {}, зарегистрировано с весом: {}", DEFINITIONS.size(), BLOCK_TO_SKILL_MAP.size());
    }

    /**
     * Функция, которая переваривает skill id (ex. naturalskillgrowth:miner)
     * в объект SkillDefinition
     * @param skillId тэг, который хотим перевести
     * @return объект класса-модели данных
     */
    public static SkillDefinition get(ResourceLocation skillId) {
        SkillDefinition def = DEFINITIONS.get(skillId);
        if (def == null) {
            throw new IllegalStateException("Неизвестный навык (нет JSON в датапаке?): " + skillId);
        }
        return def;
    }

    /**
     * Получить сет всех айдишников профессий
     * @return хэшированный сет skill id
     */
    public static Set<ResourceLocation> allSkillIds() {
        return DEFINITIONS.keySet();
    }

    /**
     * Общая точка для "к какому навыку относится этот блок" - используется и
     * SkillGainEvents (начисление опыта), и SkillEffectEvents (применение бонуса
     * скорости), чтобы не дублировать эту логику в двух местах.
     * Специально сделано как цикл по ВСЕМ зарегистрированным навыкам, а не
     * захардкоженные if/else на "шахтер"/"фермер" - так добавление четвёртого
     * навыка со своим affected_blocks не потребует правок в этом методе,
     * только новый JSON в датапаке.
     *
     * Возвращает null, если ни один навык не заявил этот блок своим.
     */
    public static SkillGainInfo classifyBlock(BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return BLOCK_TO_SKILL_MAP.get(blockId);
    }

    /**
     * Классификация сущности при убийстве.
     * <p>
     * Сначала ищет точное совпадение моба в ENTITY_TO_SKILL_MAP (O(1)).
     * Если моба нет в мапе, ищет целевой навык (например, WARRIOR или переданный skillId)
     * и возвращает default_entity_weight из его JSON.
     *
     * @param skillId       навык, которому по умолчанию отходит опыт за неперечисленных мобов (например, SkillIds.WARRIOR)
     * @param entity        убитая сущность
     * @return              объект SkillGainInfo или null, если fallbackSkillId не найден
     */
    public static SkillGainInfo classifyEntity(ResourceLocation skillId, Entity entity)
    {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

        SkillGainInfo info = ENTITY_TO_SKILL_MAP.get(entityId);
        if (info != null) return info;

        // if we didn't find explicit mob via ENTITY_TO_SKILL_MAP
        SkillDefinition fallbackDef = DEFINITIONS.get(skillId);

        if (fallbackDef != null && fallbackDef.defaultEntityWeight() > 0.0f)
        {
            return new SkillGainInfo(skillId, fallbackDef.defaultEntityWeight());
        }

        return null;
    }

    /**
     * Регистрирует этот класс для корректного вызова apply
     * Т.е. когда произойдет запуск сервера/подключение/reload - вызовется apply
     */
    @EventBusSubscriber(modid = NaturalSkillGrowth.MODID)
    public static class RegistrationHandler {
        @SubscribeEvent
        static void onAddReloadListeners(AddReloadListenerEvent event) {
            event.addListener(new SkillDefinitionsHandler());
        }
    }
}

