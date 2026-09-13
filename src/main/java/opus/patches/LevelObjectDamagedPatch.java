package opus.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.network.server.ServerClient;
import necesse.entity.ObjectDamageResult;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.Mob;
import necesse.level.gameObject.DoorObject;
import necesse.level.gameObject.GameObject;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opus.breaching.WarningBellSystem;
import opus.breaching.ZombieBreaching;
import opus.damage.DamageRepairLevelData;

@ModMethodPatch(
		target = Level.class,
		name = "onObjectDamaged",
		arguments = {GameObject.class, int.class, int.class, int.class, Attacker.class, ServerClient.class, ObjectDamageResult.class}
)
public class LevelObjectDamagedPatch {
	@Advice.OnMethodEnter
	static void onEnter(
			@Advice.This Level level,
			@Advice.Argument(0) GameObject object,
			@Advice.Argument(1) int objectLayerID,
			@Advice.Argument(2) int tileX,
			@Advice.Argument(3) int tileY,
			@Advice.Argument(4) Attacker attacker,
			@Advice.Argument(6) ObjectDamageResult result
	) {
		if (!level.isServer() || result == null) {
			return;
		}

		DamageRepairLevelData data = DamageRepairLevelData.get(level, true);
		data.recordDamage(result, objectLayerID);

		if (!result.destroyed || attacker == null || object == null || !(object.isFence || object instanceof DoorObject)) {
			return;
		}

		Mob attackOwner = attacker.getAttackOwner();
		if (ZombieBreaching.isZombie(attackOwner)) {
			WarningBellSystem.onZombieBarrierBroken(attackOwner, object, tileX, tileY);
		}
	}
}
