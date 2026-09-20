package com.oathbreaker.naturalskillgrowth;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModAttachments
{
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, NaturalSkillGrowth.MODID);

    // delete copyOnDeath if want to clear all the skills data when dead
    public static final Supplier<AttachmentType<PlayerSkillsData>> PLAYER_SKILLS =
            ATTACHMENT_TYPES.register("player_skills", () ->
                    AttachmentType.builder(PlayerSkillsData::createDefault)
                            .serialize(PlayerSkillsData.CODEC)
                            .sync(PlayerSkillsData.STREAM_CODEC)
                            .copyOnDeath()
                            .build());
}