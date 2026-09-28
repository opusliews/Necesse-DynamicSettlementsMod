package opusliews.multilevelsettlement;

import necesse.engine.network.Packet;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.packet.PacketOpenContainer;
import necesse.engine.registries.ContainerRegistry;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.interfaces.OEInventory;
import necesse.level.gameObject.CheesePressObject;
import necesse.level.gameObject.GameObject;
import necesse.level.gameObject.container.BannerStandObject;
import necesse.level.gameObject.container.BookshelfObject;
import necesse.level.gameObject.container.CabinetObject;
import necesse.level.gameObject.container.CampfireObject;
import necesse.level.gameObject.container.CoffinObject;
import necesse.level.gameObject.container.CompostBinObject;
import necesse.level.gameObject.container.CoolingBoxInventoryObject;
import necesse.level.gameObject.container.DisplayStandObject;
import necesse.level.gameObject.container.FeedingTroughObject;
import necesse.level.gameObject.container.FireworkDispenserObject;
import necesse.level.gameObject.container.GrainMillBaseObject;
import necesse.level.gameObject.container.IncineratorInventoryObject;
import necesse.level.gameObject.container.InventoryObject;
import necesse.level.gameObject.container.MusicPlayerObject;
import necesse.level.gameObject.container.ShippingChestObject;
import necesse.level.gameObject.container.ArmorStandObject;
import necesse.level.gameObject.furniture.DresserObject;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.logging.Logging;

/**
 * Opens vanilla inventory containers with the canonical settlement attached when the
 * inventory itself is on a linked non-canonical settlement level.
 */
public final class SettlementCaveInventoryOpenSupport {
	private SettlementCaveInventoryOpenSupport() {
	}

	public static boolean tryOpen(LevelObject levelObject, PlayerMob player) {
		if (levelObject == null || player == null || !player.isServer()) return false;
		Level level = levelObject.level;
		if (level == null || !level.isServer()) return false;

		LevelObject master = (LevelObject)levelObject.getMasterLevelObject().orElse(levelObject);
		ObjectEntity objectEntity = level.entityManager.getObjectEntity(master.tileX, master.tileY);
		if (!(objectEntity instanceof OEInventory)) return false;

		ServerSettlementData settlement = SettlementLevelObjectStatusSupport.resolveSettlement(level, master.tileX, master.tileY);
		if (settlement == null || settlement.getLevel() == null) return false;
		if (level.getIdentifier().equals(settlement.getLevel().getIdentifier())) return false;

		int containerID = getContainerID(master.object);
		if (containerID < 0) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelStorage] Unsupported cave inventory container object=" + master.object.getStringID() + " class=" + master.object.getClass().getName() + " tile=" + master.tileX + "," + master.tileY);
			return false;
		}

		Packet content = new Packet();
		PacketWriter writer = new PacketWriter(content);
		if (!SettlementLevelObjectStatusSupport.writeCustomContent(settlement, level, master.tileX, master.tileY, writer)) return false;

		PacketOpenContainer openPacket = PacketOpenContainer.SettlementObjectEntity(containerID, settlement, objectEntity, content);
		ContainerRegistry.openAndSendContainer(player.getServerClient(), openPacket);
		if (Logging.logEnabled) Logging.logMessage("[MultiLevelStorage] Opened linked-level inventory settlement=" + settlement.uniqueID + " level=" + level.getIdentifier() + " object=" + master.object.getStringID() + " tile=" + master.tileX + "," + master.tileY + " containerID=" + containerID);
		return true;
	}

	private static int getContainerID(GameObject object) {
		if (object instanceof DresserObject) return ContainerRegistry.DRESSER_CONTAINER;
		if (object instanceof ArmorStandObject) return ContainerRegistry.ARMOR_STAND_CONTAINER;
		if (object instanceof CoolingBoxInventoryObject) return ContainerRegistry.FUELED_REFRIGERATOR_INVENTORY_CONTAINER;
		if (object instanceof CampfireObject) return ContainerRegistry.FUELED_OE_INVENTORY_CONTAINER;
		if (object instanceof IncineratorInventoryObject) return ContainerRegistry.INCINERATOR_INVENTORY_CONTAINER;
		if (object instanceof ShippingChestObject) return ContainerRegistry.SHIPPING_CHEST_CONTAINER;
		if (object instanceof MusicPlayerObject) return ContainerRegistry.MUSIC_PLAYER_CONTAINER;
		if (object instanceof CheesePressObject || object instanceof GrainMillBaseObject || object instanceof CompostBinObject) return ContainerRegistry.PROCESSING_INVENTORY_CONTAINER;
		if (object instanceof InventoryObject
				|| object instanceof BookshelfObject
				|| object instanceof FeedingTroughObject
				|| object instanceof DisplayStandObject
				|| object instanceof CoffinObject
				|| object instanceof CabinetObject
				|| object instanceof BannerStandObject
				|| object instanceof FireworkDispenserObject) return ContainerRegistry.OE_INVENTORY_CONTAINER;
		return -1;
	}
}
