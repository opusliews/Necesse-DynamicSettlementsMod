package opusliews.object;

import necesse.engine.localization.Localization;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.registries.ContainerRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.inventory.item.Item;
import necesse.level.gameObject.PaintingObject;
import necesse.level.maps.Level;
import opusliews.crafting.JobRequestBulletinFeature;

public class JobRequestBulletinObject extends PaintingObject {
	public static final String stringID = "jobrequestbulletin";

	public JobRequestBulletinObject() {
		super(Item.Rarity.COMMON);
		this.texturePath = "jobrequestbulletin";
		this.setItemCategory("objects", "misc");
		this.setCraftingCategory("objects", "misc");
	}

	@Override
	public String canPlace(Level level, int layerID, int x, int y, int rotation, boolean byPlayer, boolean ignoreOtherLayers) {
		if (rotation != 2) return "tilecovered";
		return super.canPlace(level, layerID, x, y, rotation, byPlayer, ignoreOtherLayers);
	}

	@Override
	public String getInteractTip(Level level, int x, int y, PlayerMob perspective, boolean debug) {
		return Localization.translate("controls", "usetip");
	}

	@Override
	public boolean canInteract(Level level, int x, int y, PlayerMob player) {
		return true;
	}

	@Override
	public void interact(Level level, int x, int y, PlayerMob player) {
		if (!level.isServer()) return;
		ObjectEntity entity = level.entityManager.getObjectEntity(x, y);
		if (!(entity instanceof JobRequestBulletinObjectEntity)) return;
		ContainerRegistry.openAndSendContainer(
				player.getServerClient(),
				PacketOpenContainer.ObjectEntity(JobRequestBulletinFeature.containerID, entity)
		);
	}

	@Override
	public ObjectEntity getNewObjectEntity(Level level, int x, int y) {
		return new JobRequestBulletinObjectEntity(level, x, y);
	}
}
