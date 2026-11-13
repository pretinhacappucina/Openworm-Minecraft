package com.openwormmc.registry;

import com.openwormmc.entity.CElegansEntity;
import com.openwormmc.entity.BacterialColonyBlock;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** UNCOMPILED in the build environment (Fabric/Minecraft artifacts were unreachable). Written for Fabric 1.20.1 / Yarn. */
public final class ModRegistry {
    public static final String MOD_ID = "openwormmc";
    public static EntityType<CElegansEntity> C_ELEGANS;
    public static Block BACTERIAL_COLONY;
    public static Item BACTERIAL_COLONY_ITEM, SPAWN_EGG;

    public static void init() {
        C_ELEGANS = Registry.register(Registries.ENTITY_TYPE, new Identifier(MOD_ID, "c_elegans"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, CElegansEntity::new).dimensions(EntityDimensions.fixed(1.0f, 0.15f)).trackRangeBlocks(48).build());
        FabricDefaultAttributeRegistry.register(C_ELEGANS, CElegansEntity.attributes());
        BACTERIAL_COLONY = Registry.register(Registries.BLOCK, new Identifier(MOD_ID, "bacterial_colony"),
            new BacterialColonyBlock(AbstractBlock.Settings.create().strength(0.3f).sounds(net.minecraft.sound.BlockSoundGroup.SLIME)));
        BACTERIAL_COLONY_ITEM = Registry.register(Registries.ITEM, new Identifier(MOD_ID, "bacterial_colony"), new BlockItem(BACTERIAL_COLONY, new Item.Settings()));
        SPAWN_EGG = Registry.register(Registries.ITEM, new Identifier(MOD_ID, "c_elegans_spawn_egg"), new SpawnEggItem(C_ELEGANS, 0xE8DCC0, 0x7A6A4F, new Item.Settings()));
        ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.SPAWN_EGGS).register(e -> e.add(SPAWN_EGG));
        ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.NATURAL).register(e -> e.add(BACTERIAL_COLONY_ITEM));
    }
}
