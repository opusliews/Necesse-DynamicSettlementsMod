package opusliews.hunger;

import java.util.function.Supplier;

import necesse.entity.mobs.friendly.human.HumanMob;

/** Extends vanilla ConsumeFood eligibility while a critically hungry settler is refilling. */
public final class EmergencyFoodCanPerform implements Supplier<Boolean> {
	private final HumanMob human;

	public EmergencyFoodCanPerform(HumanMob human) {
		this.human = human;
	}

	@Override
	public Boolean get() {
		return SettlerStarvationSystem.shouldConsumeFood(human);
	}
}
