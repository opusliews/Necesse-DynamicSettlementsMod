package opus.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.registries.ObjectRegistry;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;
import necesse.inventory.item.Item;
import necesse.level.gameObject.GameObject;
import opus.DynamicSettlements;
import opus.blueprint.BlueprintObjectMaterialResolver;
import opus.item.BlueprintItem;
import opus.logging.Logging;
import opus.tools.BlueprintData;
import opus.tools.BlueprintElement;
import opus.tools.BlueprintLayerObject;

import java.util.LinkedHashSet;
import java.util.Set;

public class PacketBlueprintUpdate extends Packet {
	private final int inventoryID;
	private final int slotIndex;
	private final boolean clear;
	private final String blueprintName;
	private final String blueprintJson;
	private final boolean giveDebugMaterials;

	public PacketBlueprintUpdate(byte[] data) {
		super(data);

		PacketReader reader = new PacketReader(this);

		this.inventoryID = reader.getNextInt();
		this.slotIndex = reader.getNextInt();
		this.clear = reader.getNextBoolean();
		this.giveDebugMaterials = reader.getNextBoolean();

		if (clear) {
			this.blueprintName = "";
			this.blueprintJson = "";
		} else {
			this.blueprintName = reader.getNextString();
			this.blueprintJson = reader.getNextStringLong();
		}
	}

	public PacketBlueprintUpdate(
		int inventoryID,
		int slotIndex,
		String blueprintName,
		BlueprintData blueprintData
	) {
		this.inventoryID = inventoryID;
		this.slotIndex = slotIndex;
		this.clear = false;
		this.giveDebugMaterials = false;
		this.blueprintName = blueprintName;
		this.blueprintJson = blueprintData.toJson();

		PacketWriter writer = new PacketWriter(this);

		writer.putNextInt(inventoryID);
		writer.putNextInt(slotIndex);
		writer.putNextBoolean(false);
		writer.putNextBoolean(false);
		writer.putNextString(blueprintName);
		writer.putNextStringLong(this.blueprintJson);
	}

	public PacketBlueprintUpdate(
		int inventoryID,
		int slotIndex
	) {
		this.inventoryID = inventoryID;
		this.slotIndex = slotIndex;
		this.clear = true;
		this.giveDebugMaterials = false;
		this.blueprintName = "";
		this.blueprintJson = "";

		PacketWriter writer = new PacketWriter(this);

		writer.putNextInt(inventoryID);
		writer.putNextInt(slotIndex);
		writer.putNextBoolean(true);
		writer.putNextBoolean(false);
	}

	public PacketBlueprintUpdate(
			int inventoryID,
			int slotIndex,
			boolean giveDebugMaterials
	) {
		this.inventoryID = inventoryID;
		this.slotIndex = slotIndex;
		this.clear = false;
		this.giveDebugMaterials = giveDebugMaterials;
		this.blueprintName = "";
		this.blueprintJson = "";

		PacketWriter writer = new PacketWriter(this);

		writer.putNextInt(inventoryID);
		writer.putNextInt(slotIndex);
		writer.putNextBoolean(false);
		writer.putNextBoolean(giveDebugMaterials);
	}

	@Override
	public void processServer(
		NetworkPacket packet,
		Server server,
		ServerClient client
	) {
		PlayerInventorySlot playerSlot = new PlayerInventorySlot(
			inventoryID,
			slotIndex
		);

		InventoryItem item = playerSlot.getItem(
			client.playerMob.getInv()
		);

		if (item == null || !(item.item instanceof BlueprintItem)) {
			return;
		}

		BlueprintItem blueprintItem = (BlueprintItem)item.item;

		if (giveDebugMaterials) {
			if (!DynamicSettlements.debugBlueprintMaterialGrant || !blueprintItem.hasBlueprint(item)) {
				return;
			}

			BlueprintData blueprintData = blueprintItem.getBlueprintData(item);

			if (blueprintData == null) {
				return;
			}

			giveDebugMaterials(client, blueprintData);
			return;
		}

		if (clear) {
			blueprintItem.clearBlueprint(item);
		} else {
			BlueprintData blueprintData;

			try {
				blueprintData = BlueprintData.fromJson(blueprintJson);
			} catch (Exception e) {
				return;
			}

			blueprintItem.setBlueprint(
				item,
				blueprintName,
				blueprintData
			);
		}

		playerSlot.setItem(
			client.playerMob.getInv(),
			item
		);

		playerSlot.markDirty(
			client.playerMob.getInv()
		);
	}

	private static void giveDebugMaterials(ServerClient client, BlueprintData blueprintData) {
		Set<String> materialIDs = getDebugMaterialIDs(blueprintData);

		for (String materialID : materialIDs) {
			Item material = ItemRegistry.getItem(materialID);

			if (material == null) {
				Logging.logMessage("Blueprint debug material could not resolve item: " + materialID);
				continue;
			}

			int amount = material.getStackSize();
			InventoryItem stack = new InventoryItem(material, amount);

			client.playerMob.getInv().addItem(
					stack,
					true,
					"blueprintdebug"
			);

			Logging.logMessage(
					"Blueprint debug gave "
							+ amount
							+ "x "
							+ materialID
			);
		}
	}

	private static Set<String> getDebugMaterialIDs(BlueprintData blueprintData) {
		Set<String> materials = new LinkedHashSet<>();

		for (BlueprintElement element : blueprintData.getElements()) {
			if (element.getTileID() != null) {
				materials.add(element.getTileID());
			}

			for (BlueprintLayerObject layerObject : element.getObjects()) {
				GameObject object = ObjectRegistry.getObject(layerObject.getObjectID());

				if (object == null || !object.isMultiTileMaster()) {
					continue;
				}

				String prerequisiteItemID =
						BlueprintObjectMaterialResolver.getPlacementPrerequisiteItemID(
								layerObject.getObjectID()
						);

				if (prerequisiteItemID != null) {
					materials.add(prerequisiteItemID);
				}

				String materialItemID =
						BlueprintObjectMaterialResolver.getMaterialItemID(
								layerObject.getObjectID()
						);

				if (materialItemID != null) {
					materials.add(materialItemID);
				}
			}

			if (element.getWireMask() != 0) {
				materials.add("wire");
			}

			if (element.getLogicGateID() != null) {
				materials.add(element.getLogicGateID());
			}
		}

		return materials;
	}
}
