package opusliews.sleep;

import java.util.List;
import java.util.stream.Collectors;

import necesse.engine.network.server.Server;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.MobWasHitEvent;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.container.BedContainer;
import opusliews.logging.Logging;

public final class SleepAttackWakeSystem {
	private SleepAttackWakeSystem() {
	}

	public static void onPlayerHit(Mob mob, MobWasHitEvent event, String source) {
		if (!(mob instanceof PlayerMob) || !mob.isServer() || event == null) {
			return;
		}

		PlayerMob player = (PlayerMob)mob;
		if (!player.isServerClient()) {
			return;
		}

		ServerClient attackedClient = player.getServerClient();
		boolean inBed = attackedClient.getContainer() instanceof BedContainer;
		boolean hasAttacker = event.attacker != null;
		boolean healthLost = player.getHealth() < event.beforeHealth;

		if (Logging.logEnabled) {
			String attackerName = event.attacker == null ? "none" : event.attacker.getAttackerName().translate();
			Logging.logMessage("SleepWakeDebug: source=" + source
					+ " player=" + attackedClient.getName()
					+ " inBed=" + inBed
					+ " prevented=" + event.wasPrevented
					+ " damage=" + event.damage
					+ " health=" + event.beforeHealth + "->" + player.getHealth()
					+ " attacker=" + attackerName);
		}

		if (!inBed || (!hasAttacker && !healthLost)) {
			return;
		}

		wakeAllSleepingPlayers(attackedClient.getServer(), attackedClient, source);
	}

	public static void wakeAllSleepingPlayers(Server server, ServerClient attackedClient, String source) {
		if (server == null) {
			return;
		}

		List<ServerClient> sleepingClients = server.streamClients()
				.filter(client -> client.getContainer() instanceof BedContainer)
				.collect(Collectors.toList());

		if (sleepingClients.isEmpty()) {
			return;
		}

		if (Logging.logEnabled) {
			Logging.logMessage("SleepWakeDebug: waking " + sleepingClients.size()
					+ " sleeping player(s), attackedPlayer=" + attackedClient.getName()
					+ " source=" + source);
		}

		for (ServerClient client : sleepingClients) {
			client.closeContainer(true);
		}

		SleepSimulationSystem.stopServer(server);
	}
}
