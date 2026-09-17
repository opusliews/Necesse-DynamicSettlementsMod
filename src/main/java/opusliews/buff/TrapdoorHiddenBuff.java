package opusliews.buff;

import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffEventSubscriber;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.entity.mobs.buffs.staticBuffs.Buff;

public class TrapdoorHiddenBuff extends Buff {
	public static final String stringID = "trapdoorhidden";

	public TrapdoorHiddenBuff() {
		this.shouldSave = true;
		this.isVisible = false;
		this.canCancel = false;
		this.isImportant = true;
	}

	@Override
	public void init(ActiveBuff buff, BuffEventSubscriber eventSubscriber) {
		buff.setModifier(BuffModifiers.UNTARGETABLE, true);
		buff.setMaxModifier(BuffModifiers.KNOCKBACK_INCOMING_MOD, 0.0F);
	}
}
