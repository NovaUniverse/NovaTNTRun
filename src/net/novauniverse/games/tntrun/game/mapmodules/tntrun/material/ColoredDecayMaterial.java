package net.novauniverse.games.tntrun.game.mapmodules.tntrun.material;

import org.bukkit.DyeColor;
import org.bukkit.block.Block;

import net.zeeraa.novacore.spigot.NovaCore;
import net.zeeraa.novacore.spigot.abstraction.enums.ColoredBlockType;

public class ColoredDecayMaterial implements IDecayMaterial {
	private DyeColor color;
	private ColoredBlockType type;

	public ColoredDecayMaterial(DyeColor color, ColoredBlockType type) {
		this.color = color;
		this.type = type;
	}

	@Override
	public void setBlock(Block block) {
		NovaCore.getInstance().getVersionIndependentUtils().setColoredBlock(block, color, type);
	}
}