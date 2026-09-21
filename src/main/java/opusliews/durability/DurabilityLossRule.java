package opusliews.durability;

import necesse.engine.util.GameRandom;

import java.util.function.Predicate;

public class DurabilityLossRule {
	public final int minLoss;
	public final int maxLoss;
	public final Predicate<DurabilityContext> condition;

	public DurabilityLossRule(int minLoss, int maxLoss, Predicate<DurabilityContext> condition) {
		if (minLoss < 0 || maxLoss < minLoss) {
			throw new IllegalArgumentException("Invalid durability loss range: " + minLoss + "-" + maxLoss);
		}

		this.minLoss = minLoss;
		this.maxLoss = maxLoss;
		this.condition = condition;
	}

	public boolean matches(DurabilityContext context) {
		return condition == null || condition.test(context);
	}

	public int rollLoss() {
		if (minLoss == maxLoss) return minLoss;
		return GameRandom.globalRandom.getIntBetween(minLoss, maxLoss);
	}
}
