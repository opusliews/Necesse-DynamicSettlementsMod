package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.DamagedObjectEntity;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;

@ModMethodPatch(target = DamagedObjectEntity.class, name = "tickDamageRecovery", arguments = {})
public class DamagedObjectEntityRecoveryPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	static boolean onEnter(@Advice.This DamagedObjectEntity damagedEntity) {
		Level level = damagedEntity.getLevel();
		boolean recoverNaturalTile = level != null
				&& damagedEntity.tileDamage > 0
				&& !level.tileLayer.isPlayerPlaced(damagedEntity.tileX, damagedEntity.tileY);

		if (!recoverNaturalTile) {
			return true;
		}

		int startTime = DamagedObjectEntity.RECOVER_START_TIME;
		if (damagedEntity.hasExtendedRecoveryStartTime) {
			startTime *= 4;
		}

		if (damagedEntity.getTimeSinceLastDamage() > startTime) {
			damagedEntity.damageRecoverBuffer += (float)DamagedObjectEntity.DAMAGE_RECOVERY_PER_SECOND / 20.0F;

			if (damagedEntity.damageRecoverBuffer >= 1.0F) {
				int damageRecover = (int)damagedEntity.damageRecoverBuffer;
				damagedEntity.damageRecoverBuffer -= damageRecover;
				damagedEntity.tileDamage = Math.max(damagedEntity.tileDamage - damageRecover, 0);
			}

			if (damagedEntity.shouldRemove()) {
				damagedEntity.remove();
			}
		} else {
			damagedEntity.damageRecoverBuffer = 0.0F;
		}

		return true;
	}
}
