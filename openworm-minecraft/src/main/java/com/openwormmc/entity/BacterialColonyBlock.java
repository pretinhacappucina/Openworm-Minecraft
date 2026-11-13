package com.openwormmc.entity;

import net.minecraft.block.Block;

/** Food source. CElegansEntity scans for this block to build the odour stimulus (see SensorMap.odor). */
public class BacterialColonyBlock extends Block {
    public BacterialColonyBlock(Settings settings) { super(settings); }
}
