package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.ObjectDamageResult;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.Mob;
import necesse.level.gameObject.DoorObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opus.breaching.ZombieBreaching;

@ModMethodPatch(
		target = DoorObject.class,
		name = "onPathBreakDown",
		arguments = {Level.class, int.class, int.class, int.class, Attacker.class, int.class, int.class}
)
public class ZombieDoorPathBreakDestructionPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(
			@Advice.This DoorObject door,
			@Advice.Argument(0) Level level,
			@Advice.Argument(1) int tileX,
			@Advice.Argument(2) int tileY,
			@Advice.Argument(3) int damage,
			@Advice.Argument(4) Attacker attacker,
			@Advice.Argument(5) int hitX,
			@Advice.Argument(6) int hitY,
			@Advice.Local("destroyed") boolean destroyed
	) {
		if (attacker == null) {
			return false;
		}

		Mob attackOwner = attacker.getAttackOwner();
		if (!ZombieBreaching.isZombie(attackOwner)) {
			return false;
		}

		ObjectDamageResult damageResult = level.entityManager.doObjectDamage(
				0,
				tileX,
				tileY,
				damage,
				door.toolTier,
				attacker,
				(ServerClient)null,
				true,
				hitX,
				hitY
		);
		destroyed = damageResult != null && damageResult.destroyed;
		return true;
	}

	@Advice.OnMethodExit
	public static void onExit(
			@Advice.Enter boolean zombieHandled,
			@Advice.Local("destroyed") boolean destroyed,
			@Advice.Return(readOnly = false) boolean result
	) {
		if (zombieHandled) {
			result = destroyed;
		}
	}
}
