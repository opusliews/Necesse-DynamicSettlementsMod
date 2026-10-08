package opusliews.object;

import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.friendly.human.humanShop.AlchemistHumanMob;
import necesse.level.maps.Level;
import opusliews.network.PacketCraftingStationSound;

public class AlchemyTableObjectEntity extends DynamicCraftingStationObjectEntity {
	public static final String TYPE = "dynamicalchemytable";

	public AlchemyTableObjectEntity(Level level, int tileX, int tileY) {
		super(level, TYPE, tileX, tileY);
	}

	@Override
	public String getTaskBoardTextureKey() {
		return "alchemy";
	}

	@Override
	public boolean supportsSettlerCraftingTasks() {
		return true;
	}

	@Override
	public boolean canSettlerPerformCrafting(HumanMob worker) {
		return worker instanceof AlchemistHumanMob;
	}

	@Override
	public String getSettlerCraftingWorkItemStringID() {
		return "glassbottle";
	}

	@Override
	public void playSettlerCraftingWorkEffect() {
		if (!getLevel().isServer() || getLevel().getServer() == null) return;
		getLevel().getServer().network.sendToClientsWithTile(
				new PacketCraftingStationSound(tileX, tileY, PacketCraftingStationSound.ALCHEMY),
				getLevel(),
				tileX,
				tileY
		);
	}
}
