package opusliews.commands;

import necesse.engine.commands.CommandLog;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementMultiLevelSystem;

public final class SettlementDevCommandSupport {
	private SettlementDevCommandSupport() {
	}

	public static SettlementLevelDomain resolveCurrentSettlement(Server server, ServerClient serverClient, CommandLog logs, String commandName) {
		if (server == null) {
			logs.add("Server is unavailable");
			Logging.logMessage("[DevCommands] " + commandName + " failed reason=no-server");
			return null;
		}
		if (serverClient == null || serverClient.playerMob == null || serverClient.playerMob.getLevel() == null) {
			logs.add("This command must be run by a player standing inside a settlement");
			Logging.logMessage("[DevCommands] " + commandName + " failed reason=no-player-context");
			return null;
		}

		SettlementLevelDomain domain = SettlementMultiLevelSystem.findDomainQuiet(
				server,
				serverClient.getLevelIdentifier(),
				serverClient.playerMob.getTileX(),
				serverClient.playerMob.getTileY()
		);
		if (domain == null) {
			logs.add("Could not find a Dynamic Settlements settlement at your current position");
			Logging.logMessage("[DevCommands] " + commandName
					+ " failed reason=no-current-settlement"
					+ " player=" + serverClient.getName()
					+ " level=" + serverClient.getLevelIdentifier()
					+ " tile=" + serverClient.playerMob.getTileX() + "," + serverClient.playerMob.getTileY());
			return null;
		}

		return domain;
	}
}
