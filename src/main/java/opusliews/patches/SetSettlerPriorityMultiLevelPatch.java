package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.PacketReader;
import necesse.engine.network.server.ServerClient;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.job.EntityJobWorker;
import necesse.entity.mobs.job.JobTypeHandler;
import necesse.inventory.container.settlement.actions.SetSettlerPriorityAction;
import necesse.inventory.container.settlement.events.SettlementSettlerPrioritiesChangedEvent;
import necesse.inventory.container.settlement.events.SettlementSettlersChangedEvent;
import necesse.level.maps.levelData.settlementData.LevelSettler;
import necesse.level.maps.levelData.settlementData.ServerSettlementData;
import necesse.level.maps.levelData.settlementData.settler.SettlerMob;
import net.bytebuddy.asm.Advice;
import opusliews.logging.Logging;

/**
 * Vanilla SetSettlerPriorityAction only looks for the settler mob on the
 * canonical settlement Level. A settler currently living/working in the cave
 * therefore appears missing and the requested priority change is discarded.
 *
 * Resolve the logical LevelSettler instead. Our multi-level LevelSettler.getMob
 * patch can then return the resident mob regardless of which settlement level
 * currently owns it.
 */
@ModMethodPatch(target = SetSettlerPriorityAction.class, name = "executePacket", arguments = {PacketReader.class, ServerSettlementData.class, ServerClient.class})
public class SetSettlerPriorityMultiLevelPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.Argument(0) PacketReader reader,
			@Advice.Argument(1) ServerSettlementData data,
			@Advice.Argument(2) ServerClient client
	) {
		int mobUniqueID = reader.getNextInt();
		int typeID = reader.getNextShortUnsigned();
		int priority = reader.getNextInt();
		boolean disabledByPlayer = reader.getNextBoolean();

		LevelSettler levelSettler = data.getSettler(mobUniqueID);
		if (levelSettler == null) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelPriorities] Priority change failed: LevelSettler missing settler=" + mobUniqueID + " typeID=" + typeID + " priority=" + priority + " disabled=" + disabledByPlayer);
			new SettlementSettlersChangedEvent(data).applyAndSendToClient(client);
			return true;
		}

		SettlerMob settlerMob = levelSettler.getMob();
		if (!(settlerMob instanceof EntityJobWorker)) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelPriorities] Priority change failed: settler mob unavailable/not worker settler=" + mobUniqueID + " mob=" + (settlerMob == null ? "null" : settlerMob.getClass().getSimpleName()) + " typeID=" + typeID);
			new SettlementSettlersChangedEvent(data).applyAndSendToClient(client);
			return true;
		}

		EntityJobWorker worker = (EntityJobWorker)settlerMob;
		JobTypeHandler handler = worker.getJobTypeHandler();
		if (handler == null) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelPriorities] Priority change failed: JobTypeHandler missing settler=" + mobUniqueID + " typeID=" + typeID);
			new SettlementSettlersChangedEvent(data).applyAndSendToClient(client);
			return true;
		}

		Mob mob = (Mob)settlerMob;
		String levelText = mob.getLevel() == null ? "null" : mob.getLevel().getIdentifier().toString();
		JobTypeHandler.TypePriority typePriority = handler.getPriority(typeID);
		if (typePriority == null) {
			if (Logging.logEnabled) Logging.logMessage("[MultiLevelPriorities] Priority change failed: job type priority missing settler=" + mobUniqueID + " typeID=" + typeID + " level=" + levelText);
			new SettlementSettlersChangedEvent(data).applyAndSendToClient(client);
			return true;
		}

		typePriority.priority = priority;
		typePriority.disabledByPlayer = disabledByPlayer;
		if (Logging.logEnabled) Logging.logMessage("[MultiLevelPriorities] Applied priority change settler=" + mobUniqueID + " level=" + levelText + " jobType=" + typePriority.type.getStringID() + " priority=" + priority + " disabled=" + disabledByPlayer);
		new SettlementSettlerPrioritiesChangedEvent(data, mobUniqueID, typePriority.type, typePriority.priority, typePriority.disabledByPlayer).applyAndSendToClientsAt(client);
		return true;
	}
}
