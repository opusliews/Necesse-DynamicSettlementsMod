package opusliews.commands;

import necesse.engine.commands.CmdParameter;
import necesse.engine.commands.CommandLog;
import necesse.engine.commands.ModularChatCommand;
import necesse.engine.commands.PermissionLevel;
import necesse.engine.commands.parameterHandlers.FloatParameterHandler;
import necesse.engine.commands.parameterHandlers.IntParameterHandler;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import opusliews.hunger.SettlerStarvationSystem;
import opusliews.logging.Logging;
import opusliews.multilevelsettlement.SettlementLevelDomain;
import opusliews.multilevelsettlement.SettlementResidentSystem;

import java.util.Locale;

public final class SetSettlerHungerCommand extends ModularChatCommand {
	public SetSettlerHungerCommand() {
		super(
				"dssethunger",
				"Sets a settler's current hunger percent by unique ID",
				PermissionLevel.ADMIN,
				true,
				new CmdParameter("settlerID", new IntParameterHandler()),
				new CmdParameter("hungerPercent", new FloatParameterHandler())
		);
	}

	@Override
	public void runModular(Client client, Server server, ServerClient serverClient, Object[] args, String[] errors, CommandLog logs) {
		int settlerID = (Integer)args[0];
		float hungerPercent = (Float)args[1];
		if (!Float.isFinite(hungerPercent) || hungerPercent < 0.0F || hungerPercent > 100.0F) {
			logs.add("Hunger percent must be between 0 and 100");
			Logging.logMessage("[DevCommands] dssethunger rejected settler=" + settlerID
					+ " hungerPercent=" + hungerPercent + " reason=out-of-range");
			return;
		}

		SettlementLevelDomain domain = SettlementDevCommandSupport.resolveCurrentSettlement(server, serverClient, logs, "dssethunger");
		if (domain == null) return;

		ServerSettlementData settlement = domain.getSettlement();
		LevelSettler levelSettler = settlement.getSettler(settlerID);
		if (levelSettler == null) {
			logs.add("No settler with ID " + settlerID + " exists in the current settlement");
			Logging.logMessage("[DevCommands] dssethunger failed settlement=" + settlement.uniqueID
					+ " settler=" + settlerID + " reason=not-in-settlement");
			return;
		}

		SettlerMob settlerMob = SettlementResidentSystem.findLiveDomainMob(levelSettler);
		if (settlerMob == null) settlerMob = levelSettler.getMob();
		Mob mob = settlerMob == null ? null : settlerMob.getMob();
		if (!(mob instanceof HumanMob)) {
			logs.add("Settler " + settlerID + " is not currently available as a human mob");
			Logging.logMessage("[DevCommands] dssethunger failed settlement=" + settlement.uniqueID
					+ " settler=" + settlerID + " reason=human-mob-unavailable");
			return;
		}

		HumanMob human = (HumanMob)mob;
		float oldHunger = human.hungerLevel;
		float newHunger = hungerPercent / 100.0F;
		human.hungerLevel = newHunger;
		SettlerStarvationSystem.resetTransientStateAfterDebugHungerChange(human);
		human.sendWorkUpdatePacket();

		String oldText = String.format(Locale.ROOT, "%.1f", oldHunger * 100.0F);
		String newText = String.format(Locale.ROOT, "%.1f", newHunger * 100.0F);
		logs.add("Set " + human.getDisplayName() + " (ID=" + settlerID + ") hunger from " + oldText + "% to " + newText + "%");
		Logging.logMessage("[DevCommands] dssethunger settlement=" + settlement.uniqueID
				+ " settler=" + human.getStringID() + "#" + settlerID
				+ " player=" + serverClient.getName()
				+ " level=" + (human.getLevel() == null ? "null" : human.getLevel().getIdentifier())
				+ " oldHunger=" + oldHunger
				+ " newHunger=" + newHunger);
	}
}
