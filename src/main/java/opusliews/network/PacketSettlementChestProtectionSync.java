package opusliews.network;

import necesse.engine.network.NetworkPacket;
import necesse.engine.network.Packet;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.util.LevelIdentifier;
import necesse.engine.world.worldData.SettlementsWorldData;
import necesse.level.maps.levelData.settlementData.NetworkSettlementData;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import opusliews.forms.SettlementChestProtectionForm;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;
import opusliews.settlement.SettlementChestProtectionSystem;

public class PacketSettlementChestProtectionSync extends Packet {
	private final int settlementUniqueID;
	private final boolean enabled;
	private final boolean hasBounds;
	private final LevelIdentifier surfaceIdentifier;
	private final int flagTileX;
	private final int flagTileY;
	private final int flagTier;

	public PacketSettlementChestProtectionSync(int settlementUniqueID, boolean enabled) {
		this(settlementUniqueID, enabled, null, 0, 0, 0);
	}

	public PacketSettlementChestProtectionSync(ServerSettlementData settlement, boolean enabled) {
		this(
				settlement.uniqueID,
				enabled,
				SettlementMultiLevelSystem.getSurfaceIdentifier(settlement.getLevel().getIdentifier()),
				settlement.networkData.getTileX(),
				settlement.networkData.getTileY(),
				settlement.networkData.getFlagTier()
		);
	}

	public PacketSettlementChestProtectionSync(int settlementUniqueID, boolean enabled, LevelIdentifier surfaceIdentifier, int flagTileX, int flagTileY, int flagTier) {
		this.settlementUniqueID = settlementUniqueID;
		this.enabled = enabled;
		this.hasBounds = surfaceIdentifier != null;
		this.surfaceIdentifier = surfaceIdentifier;
		this.flagTileX = flagTileX;
		this.flagTileY = flagTileY;
		this.flagTier = flagTier;

		PacketWriter writer = new PacketWriter(this);
		writer.putNextInt(settlementUniqueID);
		writer.putNextBoolean(enabled);
		writer.putNextBoolean(hasBounds);
		if (hasBounds) {
			surfaceIdentifier.writePacket(writer);
			writer.putNextInt(flagTileX);
			writer.putNextInt(flagTileY);
			writer.putNextInt(flagTier);
		}
	}

	public PacketSettlementChestProtectionSync(byte[] data) {
		super(data);
		PacketReader reader = new PacketReader(this);
		settlementUniqueID = reader.getNextInt();
		enabled = reader.getNextBoolean();
		hasBounds = reader.getNextBoolean();
		if (hasBounds) {
			surfaceIdentifier = new LevelIdentifier(reader);
			flagTileX = reader.getNextInt();
			flagTileY = reader.getNextInt();
			flagTier = reader.getNextInt();
		} else {
			surfaceIdentifier = null;
			flagTileX = 0;
			flagTileY = 0;
			flagTier = 0;
		}
	}

	@Override
	public void processClient(NetworkPacket packet, Client client) {
		if (hasBounds) {
			SettlementChestProtectionSystem.setClientProtected(settlementUniqueID, enabled, surfaceIdentifier, flagTileX, flagTileY, flagTier);
		} else {
			NetworkSettlementData networkData = SettlementsWorldData.getSettlementsData(client).getNetworkData(settlementUniqueID);
			if (networkData != null) SettlementChestProtectionSystem.setClientProtected(networkData, enabled);
			else {
				SettlementChestProtectionSystem.setClientProtected(settlementUniqueID, enabled);
				if (Logging.logEnabled) Logging.logMessage("[ChestProtection] Client sync could not find NetworkSettlementData for settlement=" + settlementUniqueID + "; cached setting without bounds");
			}
		}

		SettlementChestProtectionForm openForm = SettlementChestProtectionFormState.openForm;
		if (openForm != null && openForm.getSettlementUniqueID() == settlementUniqueID) {
			openForm.applySetting(enabled);
		}
		if (Logging.logEnabled) {
			Logging.logMessage("[ChestProtection] Applied settings sync settlement=" + settlementUniqueID + " enabled=" + enabled
					+ " hasBounds=" + hasBounds + (hasBounds ? " surface=" + surfaceIdentifier + " flag=" + flagTileX + "," + flagTileY + " tier=" + flagTier : ""));
		}
	}

	public static final class SettlementChestProtectionFormState {
		public static SettlementChestProtectionForm openForm;
	}
}
