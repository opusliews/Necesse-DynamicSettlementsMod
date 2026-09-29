package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.level.maps.Level;
import necesse.inventory.container.settlement.events.SettlementSettlerBasicsEvent;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementCaveBed;
import opusliews.multilevelsettlement.SettlementCaveBedSystem;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelType;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

@ModMethodPatch(target = ServerSettlementData.class, name = "moveSettler", arguments = {int.class, int.class, int.class, ServerClient.class})
public class ServerSettlementMoveToCaveBedCoordinatesPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This ServerSettlementData settlement,
			@Advice.Argument(0) int settlerUniqueID,
			@Advice.Argument(1) int tileX,
			@Advice.Argument(2) int tileY,
			@Advice.Argument(3) ServerClient client,
			@Advice.Local("handledResult") boolean handledResult) {
		if (settlement == null || client == null) return false;

		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null) return false;

		Level clientLevel = client.getLevel();
		if (clientLevel == null || domain.getLevelType(clientLevel.getIdentifier()) != SettlementLevelType.CAVE) return false;

		if (!domain.isTileWithinBounds(clientLevel.getIdentifier(), tileX, tileY)) {
			if (Logging.logEnabled) {
				Logging.logMessage("[CaveBeds] Cave bed assignment click rejected outside settlement bounds settler="
						+ settlerUniqueID + " bed=" + tileX + "," + tileY + " level=" + clientLevel.getIdentifier());
			}
			handledResult = false;
			return true;
		}

		SettlementCaveBed bed = SettlementCaveBedSystem.getOrCreateCaveBed(settlement, tileX, tileY);
		if (bed == null) {
			if (Logging.logEnabled) {
				Logging.logMessage("[CaveBeds] Cave bed assignment click could not resolve valid bed settler="
						+ settlerUniqueID + " bed=" + tileX + "," + tileY + " level=" + clientLevel.getIdentifier());
			}
			handledResult = false;
			return true;
		}

		if (Logging.logEnabled) {
			Logging.logMessage("[CaveBeds] Routing coordinate bed assignment to cave bed settler="
					+ settlerUniqueID + " bed=" + tileX + "," + tileY + " level=" + clientLevel.getIdentifier());
		}
		handledResult = settlement.moveSettler(settlerUniqueID, bed, client);
		if (handledResult) {
			new SettlementSettlerBasicsEvent(settlement).applyAndSendToClient(client);
			if (Logging.logEnabled) Logging.logMessage("[CaveBeds] Sent immediate settler basics update after cave bed assignment settler=" + settlerUniqueID);
		}
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(@Advice.This ServerSettlementData settlement,
			@Advice.Argument(3) ServerClient client,
			@Advice.Local("handledResult") boolean handledResult,
			@Advice.Return(readOnly = false) boolean result) {
		if (settlement == null || client == null) return;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		Level clientLevel = client.getLevel();
		if (domain != null && clientLevel != null && domain.getLevelType(clientLevel.getIdentifier()) == SettlementLevelType.CAVE) {
			result = handledResult;
		}
	}
}
