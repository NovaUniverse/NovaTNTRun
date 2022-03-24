package net.novauniverse.games.tntrun.game.mapmodules.tntrun.material;

import org.bukkit.Material;
import org.bukkit.block.Block;

public class NormalDecayMaterial implements IDecayMaterial{
	private Material material;
	
	public NormalDecayMaterial(Material material) {
		this.material = material;
	}

	@Override
	public void setBlock(Block block) {
		block.setType(material);
	}
}