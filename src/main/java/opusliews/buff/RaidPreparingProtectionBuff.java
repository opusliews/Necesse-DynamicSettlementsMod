package opusliews.buff;

import necesse.entity.mobs.MobBeforeHitEvent;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffEventSubscriber;
import necesse.entity.mobs.buffs.staticBuffs.Buff;

public class RaidPreparingProtectionBuff extends Buff {
	public static final String stringID = "raidpreparingprotection";

	public RaidPreparingProtectionBuff() {
		this.shouldSave = false;
		this.isVisible = false;
		this.canCancel = false;
		this.isImportant = true;
	}

	@Override
	public void init(ActiveBuff buff, BuffEventSubscriber eventSubscriber) {
	}

	@Override
	public void onBeforeHit(ActiveBuff buff, MobBeforeHitEvent event) {
		super.onBeforeHit(buff, event);
		event.prevent();
		event.showDamageTip = false;
		event.playHitSound = false;
	}
}
