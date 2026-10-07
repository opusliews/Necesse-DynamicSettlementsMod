package opusliews.commands;

import necesse.engine.commands.CommandLog;
import necesse.engine.commands.ModularChatCommand;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementLevelType;
import opusliews.multilevelsettlement.SettlementResidentSystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

public final class ListSettlementSettlersCommand extends ModularChatCommand {
	public ListSettlementSettlersCommand() {
		super("dssettlers", "Lists all settlers in the current multi-level settlement", PermissionLevel.ADMIN, true);
	}

	@Override
	public void runModular(Client client, Server server, ServerClient serverClient, Object[] args, String[] errors, CommandLog logs) {
		SettlementLevelDomain domain = SettlementDevCommandSupport.resolveCurrentSettlement(server, serverClient, logs, "dssettlers");
		if (domain == null) return;

		ServerSettlementData settlement = domain.getSettlement();
		ArrayList<LevelSettler> settlers = new ArrayList<>();
		for (LevelSettler settler : settlement.getSettlers()) settlers.add(settler);
		settlers.sort(Comparator.comparingInt(settler -> settler.mobUniqueID));

		logs.add("Settlers in " + settlement.getSettlementName().translate()
				+ " (settlement=" + settlement.uniqueID + ", count=" + settlers.size() + "):");

		for (LevelSettler levelSettler : settlers) {
			SettlerMob settlerMob = SettlementResidentSystem.findLiveDomainMob(levelSettler);
			if (settlerMob == null) settlerMob = levelSettler.getMob();
			Mob mob = settlerMob == null ? null : settlerMob.getMob();
			String displayName;
			String mobStringID;
			String levelText = "unloaded";
			String levelTypeText = "UNKNOWN";
			String hungerText = "n/a";
			String downedText = "";

			if (mob != null) {
				displayName = mob.getDisplayName();
				mobStringID = mob.getStringID();
				if (mob.getLevel() != null) {
					levelText = mob.getLevel().getIdentifier().toString();
					SettlementLevelType levelType = domain.getLevelType(mob.getLevel().getIdentifier());
					if (levelType != null) levelTypeText = levelType.name();
				}
				if (mob instanceof HumanMob) {
					HumanMob human = (HumanMob)mob;
					hungerText = String.format(Locale.ROOT, "%.1f%%", human.hungerLevel * 100.0F);
					downedText = human.isDowned() ? " | DOWNED" : "";
				}
			}
			else {
				displayName = levelSettler.settler == null ? "unknown" : levelSettler.settler.getStringID();
				mobStringID = levelSettler.settler == null ? "unknown" : levelSettler.settler.getStringID();
			}

			logs.add("ID=" + levelSettler.mobUniqueID
					+ " | " + displayName
					+ " | mob=" + mobStringID
					+ " | hunger=" + hungerText
					+ " | levelType=" + levelTypeText
					+ " | level=" + levelText
					+ downedText);
		}

		Logging.logMessage("[DevCommands] dssettlers listed settlement=" + settlement.uniqueID
				+ " count=" + settlers.size()
				+ " player=" + serverClient.getName()
				+ " level=" + serverClient.getLevelIdentifier());
	}
}
