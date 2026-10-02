package opusliews.settler;

import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.job.activeJob.ActiveJob;
import opusliews.buff.HaulingHappinessSpeedBuff;
import opusliews.logging.Logging;

public final class HaulingHappinessSpeedSystem {
	private static final float multiplierEpsilon = 0.0001F;

	private HaulingHappinessSpeedSystem() {
	}

	public static void serverTick(HumanMob human) {
		if (human == null || !human.isServer()) return;

		boolean hauling = isHauling(human);
		ActiveBuff current = human.buffManager.getBuff(HaulingHappinessSpeedBuff.stringID);

		if (!hauling) {
			if (current != null) {
				human.buffManager.removeBuff(HaulingHappinessSpeedBuff.stringID, true);
				if (Logging.logEnabled) Logging.logMessage("[HappinessHaulingSpeed] Removed hauling speed buff worker="
						+ human.getUniqueID());
			}
			return;
		}

		float multiplier = SettlerHappinessScaling.getHaulingSpeedMultiplier(human);
		if (current != null) {
			float currentMultiplier = current.getGndData().getFloat(HaulingHappinessSpeedBuff.speedMultiplierKey);
			if (Math.abs(currentMultiplier - multiplier) <= multiplierEpsilon) return;
		}

		ActiveBuff buff = new ActiveBuff(HaulingHappinessSpeedBuff.stringID, human, Integer.MAX_VALUE, null);
		buff.getGndData().setFloat(HaulingHappinessSpeedBuff.speedMultiplierKey, multiplier);
		human.buffManager.addBuff(buff, true);

		if (Logging.logEnabled) Logging.logMessage("[HappinessHaulingSpeed] Applied hauling speed buff worker="
				+ human.getUniqueID() + " happiness=" + human.getSettlerHappiness() + " multiplier=" + multiplier);
	}

	private static boolean isHauling(HumanMob human) {
		if (!human.isSettler() || human.ai == null || human.ai.blackboard == null) return false;
		ActiveJob currentJob = human.ai.blackboard.getObject(ActiveJob.class, "currentJob");
		return currentJob != null
				&& currentJob.priority != null
				&& currentJob.priority.type != null
				&& "hauling".equals(currentJob.priority.type.getStringID());
	}
}
