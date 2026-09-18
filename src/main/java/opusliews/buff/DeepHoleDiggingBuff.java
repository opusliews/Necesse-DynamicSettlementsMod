package opusliews.buff;

import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffEventSubscriber;
import necesse.entity.mobs.buffs.staticBuffs.Buff;

public class DeepHoleDiggingBuff extends Buff {
	public static final String stringID = "deepholedigging";

	public DeepHoleDiggingBuff() {
		this.shouldSave = false;
		this.isVisible = false;
		this.canCancel = false;
		this.isImportant = true;
	}

	@Override
	public void init(ActiveBuff buff, BuffEventSubscriber eventSubscriber) {
	}
}
