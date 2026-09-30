package opusliews.raids;

import necesse.entity.mobs.RaiderMobPhase;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import opusliews.buff.RaidPreparingProtectionBuff;

public final class RaidPreparingProtectionSystem {
	private RaidPreparingProtectionSystem() {
	}

	public static void initializeRaider(ItemAttackerRaiderMob raider) {
		if (raider == null || !raider.isServer()) return;
		if (raider.phase == RaiderMobPhase.PREPARING && !isProtected(raider)) {
			raider.buffManager.addBuff(new ActiveBuff(RaidPreparingProtectionBuff.stringID, raider, Integer.MAX_VALUE, null), false);
		}
	}

	public static void serverTick(ItemAttackerRaiderMob raider) {
		if (raider == null || !raider.isServer()) return;
		if (raider.phase == RaiderMobPhase.PREPARING) {
			if (!isProtected(raider)) raider.buffManager.addBuff(new ActiveBuff(RaidPreparingProtectionBuff.stringID, raider, Integer.MAX_VALUE, null), true);
		} else {
			clearProtection(raider);
		}
	}

	public static void clearProtection(ItemAttackerRaiderMob raider) {
		if (raider != null && raider.buffManager.hasBuff(RaidPreparingProtectionBuff.stringID)) {
			raider.buffManager.removeBuff(RaidPreparingProtectionBuff.stringID, true);
		}
	}

	public static boolean isProtected(ItemAttackerRaiderMob raider) {
		return raider != null && raider.buffManager.hasBuff(RaidPreparingProtectionBuff.stringID);
	}
}
