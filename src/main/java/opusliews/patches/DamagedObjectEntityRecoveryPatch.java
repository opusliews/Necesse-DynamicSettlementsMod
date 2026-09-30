package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.DamagedObjectEntity;
import necesse.level.maps.Level;
import net.bytebuddy.asm.Advice;
import opusliews.damage.DamageRepairLevelData;
import opusliews.damage.MaterialWeatheringClassifier;

@ModMethodPatch(target = DamagedObjectEntity.class, name = "tickDamageRecovery", arguments = {})
public class DamagedObjectEntityRecoveryPatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	static boolean onEnter(@Advice.This DamagedObjectEntity damagedEntity) {
		Level level = damagedEntity.getLevel();
		if (level == null) return true;

		boolean recoverNaturalTile = damagedEntity.tileDamage > 0
				&& (!level.tileLayer.isPlayerPlaced(damagedEntity.tileX, damagedEntity.tileY)
						|| MaterialWeatheringClassifier.isSelfRecoveringTile(level.getTile(damagedEntity.tileX, damagedEntity.tileY)));
		boolean recoverTreeObject = false;

		for (int layerID = 0; layerID < damagedEntity.objectDamage.length; layerID++) {
			if (damagedEntity.objectDamage[layerID] > 0
					&& DamageRepairLevelData.isTreeObject(level, layerID, damagedEntity.tileX, damagedEntity.tileY)) {
				recoverTreeObject = true;
				break;
			}
		}

		if (!recoverNaturalTile && !recoverTreeObject) return true;

		int startTime = DamagedObjectEntity.RECOVER_START_TIME;
		if (damagedEntity.hasExtendedRecoveryStartTime) startTime *= 4;

		if (damagedEntity.getTimeSinceLastDamage() > startTime) {
			damagedEntity.damageRecoverBuffer += (float)DamagedObjectEntity.DAMAGE_RECOVERY_PER_SECOND / 20.0F;

			if (damagedEntity.damageRecoverBuffer >= 1.0F) {
				int damageRecover = (int)damagedEntity.damageRecoverBuffer;
				damagedEntity.damageRecoverBuffer -= damageRecover;

				if (recoverNaturalTile) {
					damagedEntity.tileDamage = Math.max(damagedEntity.tileDamage - damageRecover, 0);
				}

				for (int layerID = 0; layerID < damagedEntity.objectDamage.length; layerID++) {
					if (damagedEntity.objectDamage[layerID] > 0
							&& DamageRepairLevelData.isTreeObject(level, layerID, damagedEntity.tileX, damagedEntity.tileY)) {
						damagedEntity.objectDamage[layerID] = Math.max(damagedEntity.objectDamage[layerID] - damageRecover, 0);
					}
				}
			}

			if (damagedEntity.shouldRemove()) damagedEntity.remove();
		} else {
			damagedEntity.damageRecoverBuffer = 0.0F;
		}

		return true;
	}

}
