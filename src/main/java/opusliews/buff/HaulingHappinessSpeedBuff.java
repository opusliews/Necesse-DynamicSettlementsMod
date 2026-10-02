package opusliews.buff;

import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffEventSubscriber;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.entity.mobs.buffs.staticBuffs.Buff;

public class HaulingHappinessSpeedBuff extends Buff {
	public static final String stringID = "dynamicsettlementshaulinghappinessspeed";
	public static final String speedMultiplierKey = "speedMultiplier";

	public HaulingHappinessSpeedBuff() {
		shouldSave = false;
		isVisible = false;
	}

	@Override
	public void init(ActiveBuff buff, BuffEventSubscriber eventSubscriber) {
		float multiplier = buff.getGndData().getFloat(speedMultiplierKey);
		if (multiplier <= 0.0F) multiplier = 1.0F;
		buff.setModifier(BuffModifiers.SPEED, multiplier - 1.0F);
	}
}
