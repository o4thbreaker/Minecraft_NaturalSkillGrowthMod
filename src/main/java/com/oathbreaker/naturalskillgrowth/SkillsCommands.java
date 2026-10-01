package com.oathbreaker.naturalskillgrowth;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = NaturalSkillGrowth.MODID)
public class SkillsCommands
{
    /**
     * Регистрирует все кастомные комманды в шину.
     *
     * @param event сам ивент регистрации
     */
    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal("skills")
                .then(Commands.literal("show")
                        .executes(SkillsCommands::show))
                .then(Commands.literal("focus")
                        .then(Commands.argument("skill", ResourceLocationArgument.id())
                                .suggests(SkillsCommands.SUGGEST_SKILLS)
                                .executes(SkillsCommands::focus)))
                .then(Commands.literal("unfocus")
                        .executes(SkillsCommands::unfocus))
                .then(Commands.literal("clear")
                        .executes(SkillsCommands::clearAll)
                        .then(Commands.argument("skill", ResourceLocationArgument.id())
                                .suggests(SkillsCommands.SUGGEST_SKILLS)
                                .executes(SkillsCommands::clearSingle)))
        );
    }

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_SKILLS = (context, builder) -> {
        return SharedSuggestionProvider.suggestResource(
                SkillDefinitionsHandler.allSkillIds(),
                builder
        );
    };

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerSkillsData data = player.getData(ModAttachments.PLAYER_SKILLS);

        StringBuilder builder = new StringBuilder("=== Skills status ===\n");
        for (ResourceLocation id : SkillDefinitionsHandler.allSkillIds())
        {
            builder.append(id.getPath())
                    .append(" | ")
                    .append(data.getEfficiency(id))
                    .append("\n");
        }

        ctx.getSource().sendSuccess(() -> Component.literal(builder.toString().trim()), false);

        return 1;
    }

    private static int clearAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerSkillsData data = PlayerSkillsData.createDefault();

        player.setData(ModAttachments.PLAYER_SKILLS, data);
        ctx.getSource().sendSuccess(() -> Component.literal("All skills have been reset to default."), false);

        return 1;
    }

    private static int clearSingle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceLocation skillId = ResourceLocationArgument.getId(ctx, "skill");
        PlayerSkillsData currentData = player.getData(ModAttachments.PLAYER_SKILLS);

        PlayerSkillsData updatedData = currentData.withoutSkill(skillId);

        player.setData(ModAttachments.PLAYER_SKILLS, updatedData);
        ctx.getSource().sendSuccess(() -> Component.literal("The " + skillId.getPath() + " skill has been reset to default."), false);

        return 1;
    }

    private static int focus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceLocation skillId = ResourceLocationArgument.getId(ctx, "skill");
        PlayerSkillsData data = player.getData(ModAttachments.PLAYER_SKILLS);

        if (!data.isEfficiencyAtMax(skillId))
        {
            ctx.getSource().sendFailure(Component.literal("The " + skillId.getPath() + " skill wasn't maxed yet."));
            return 0;
        }

        if (data.isFocusedOn(skillId))
        {
            ctx.getSource().sendFailure(Component.literal("Already focusing on " + skillId.getPath() + "!"));
            return 0;
        }

        player.setData(ModAttachments.PLAYER_SKILLS, data.withFocus(skillId));
        ctx.getSource().sendSuccess(() -> Component.literal("The " + skillId.getPath() + " skill is being focused."), false);

        return 1;
    }

    private static int unfocus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException
    {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerSkillsData data = player.getData(ModAttachments.PLAYER_SKILLS);

        player.setData(ModAttachments.PLAYER_SKILLS, data.withUnfocused());
        ctx.getSource().sendSuccess(() -> Component.literal("Unfocused."), false);

        return 1;
    }
}
