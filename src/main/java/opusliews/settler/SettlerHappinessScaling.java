package opusliews.settler;

import necesse.entity.mobs.friendly.human.HumanMob;

public final class SettlerHappinessScaling {
	public static final int vanillaWorkInventoryStacks = 5;
	public static final float vanillaWorkInventoryBrokerValue = 300.0F;

	private SettlerHappinessScaling() {
	}

	public static float getHappinessRatio(HumanMob human) {
		if (human == null) return 0.0F;
		return Math.max(0.0F, Math.min(100.0F, human.getSettlerHappiness())) / 100.0F;
	}

	public static int getScaledStackLimit(HumanMob human, int baseStacks) {
		int safeBase = Math.max(1, baseStacks);
		return Math.max(safeBase, Math.round(safeBase * (1.0F + getHappinessRatio(human))));
	}

	public static float getScaledBrokerValueLimit(HumanMob human) {
		return vanillaWorkInventoryBrokerValue * (1.0F + getHappinessRatio(human));
	}

	public static float getHaulingSpeedMultiplier(HumanMob human) {
		return 0.5F + 1.5F * getHappinessRatio(human);
	}
}
