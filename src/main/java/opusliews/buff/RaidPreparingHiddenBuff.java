package opusliews.buff;

import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffEventSubscriber;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.entity.mobs.buffs.staticBuffs.Buff;

public class RaidPreparingHiddenBuff extends Buff {
	public static final String stringID = "raidpreparinghidden";

	public RaidPreparingHiddenBuff() {
		this.shouldSave = false;
		this.isVisible = false;
		this.canCancel = false;
		this.isImportant = true;
	}

	@Override
	public void init(ActiveBuff buff, BuffEventSubscriber eventSubscriber) {
		buff.setModifier(BuffModifiers.UNTARGETABLE, true);
		buff.setModifier(BuffModifiers.INVISIBILITY, true);
	}
}
