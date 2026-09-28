package opusliews.multilevelsettlement;

import necesse.engine.network.server.ServerClient;
import necesse.inventory.container.settlement.SettlementDependantContainer;
import necesse.level.maps.Level;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;

public final class SettlementLevelStorageActionSupport {
	private SettlementLevelStorageActionSupport() {
	}

	public static Context getCustomContext(SettlementDependantContainer container) {
		if (container == null || !container.client.isServer()) return null;
		ServerClient client = container.client.getServerClient();
		ServerSettlementData settlement = container.getServerData();
		if (client == null || settlement == null || settlement.getLevel() == null) return null;
		Level level = client.getLevel();
		if (level == null || level.getIdentifier().equals(settlement.getLevel().getIdentifier())) return null;
		SettlementLevelDomain domain = SettlementMultiLevelSystem.get(settlement);
		if (domain == null || domain.getLevelType(level.getIdentifier()) == null) return null;
		return new Context(client, settlement, level, settlement.networkData.doesClientHaveAccess(client));
	}

	public static final class Context {
		public final ServerClient client;
		public final ServerSettlementData settlement;
		public final Level level;
		public final boolean hasAccess;

		Context(ServerClient client, ServerSettlementData settlement, Level level, boolean hasAccess) {
			this.client = client;
			this.settlement = settlement;
			this.level = level;
			this.hasAccess = hasAccess;
		}
	}
}
