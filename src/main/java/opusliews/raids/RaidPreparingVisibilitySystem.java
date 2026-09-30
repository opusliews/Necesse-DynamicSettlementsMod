package opusliews.raids;

import necesse.entity.mobs.RaiderMobPhase;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.hostile.ItemAttackerRaiderMob;
import opusliews.buff.RaidPreparingHiddenBuff;

public final class RaidPreparingVisibilitySystem {
	public static final int revealTimeMillis = 2000;

	private RaidPreparingVisibilitySystem() {
	}

	public static void initializeRaider(ItemAttackerRaiderMob raider) {
		if (raider == null || !raider.isServer()) return;
		updateHiddenState(raider, false);
	}

	public static void serverTick(ItemAttackerRaiderMob raider) {
		if (raider == null || !raider.isServer()) return;
		updateHiddenState(raider, true);
	}

	public static boolean isHidden(ItemAttackerRaiderMob raider) {
		return raider != null && raider.buffManager.hasBuff(RaidPreparingHiddenBuff.stringID);
	}

	private static void updateHiddenState(ItemAttackerRaiderMob raider, boolean sendUpdatePacket) {
		boolean shouldHide = raider.phase == RaiderMobPhase.PREPARING && raider.getRaidingStartTimer() > revealTimeMillis;
		boolean hidden = isHidden(raider);
		if (shouldHide && !hidden) {
			raider.buffManager.addBuff(new ActiveBuff(RaidPreparingHiddenBuff.stringID, raider, Integer.MAX_VALUE, null), sendUpdatePacket);
		} else if (!shouldHide && hidden) {
			raider.buffManager.removeBuff(RaidPreparingHiddenBuff.stringID, true);
		}
	}
}
