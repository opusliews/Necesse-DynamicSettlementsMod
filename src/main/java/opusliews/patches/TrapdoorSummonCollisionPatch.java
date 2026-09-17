package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.itemAttacker.ItemAttackerMob;
import necesse.entity.mobs.summon.summonFollowingMob.attackingFollowingMob.AttackingFollowingMob;
import net.bytebuddy.asm.Advice;
import opusliews.trapdoor.TrapdoorSystem;

@ModMethodPatch(target = AttackingFollowingMob.class, name = "canCollisionHit", arguments = {Mob.class})
public class TrapdoorSummonCollisionPatch {
	@Advice.OnMethodExit
	public static void onExit(
			@Advice.This AttackingFollowingMob summon,
			@Advice.Return(readOnly = false) boolean result
	) {
		ItemAttackerMob owner = summon.getFollowingItemAttacker();
		if (owner instanceof PlayerMob && TrapdoorSystem.isHidden((PlayerMob)owner)) result = false;
	}
}
