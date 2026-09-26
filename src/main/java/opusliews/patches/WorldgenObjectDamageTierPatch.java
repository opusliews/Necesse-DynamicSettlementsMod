package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.DamagedObjectEntity;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenLockedContainerSystem;

@ModMethodPatch(
		target = DamagedObjectEntity.class,
		name = "doObjectDamage",
		arguments = {int.class, int.class, float.class, necesse.entity.mobs.Attacker.class, necesse.engine.network.server.ServerClient.class, boolean.class, int.class, int.class}
)
public class WorldgenObjectDamageTierPatch {
	@Advice.OnMethodEnter
	public static void onEnter(
			@Advice.This DamagedObjectEntity entity,
			@Advice.Argument(0) int objectLayerID,
			@Advice.Argument(value = 1, readOnly = false) int damage,
			@Advice.Argument(2) float toolTier,
			@Advice.Argument(4) necesse.engine.network.server.ServerClient client
	) {
		damage = WorldgenLockedContainerSystem.handleDamage(entity.getLevel(), objectLayerID, entity.tileX, entity.tileY, damage, toolTier, client);
	}
}
