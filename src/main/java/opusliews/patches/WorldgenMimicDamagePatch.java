package opusliews.patches;

import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.GameDamage;
import necesse.entity.mobs.Mob;
import net.bytebuddy.asm.Advice;
import opusliews.worldgengating.WorldgenSpecialLootGatingSystem;

@ModMethodPatch(target = Mob.class, name = "isServerHit", arguments = {GameDamage.class, float.class, float.class, float.class, Attacker.class})
public class WorldgenMimicDamagePatch {
	@Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
	public static boolean onEnter(@Advice.This Mob mob, @Advice.Argument(4) Attacker attacker) {
		return WorldgenSpecialLootGatingSystem.shouldBlockMimicDamage(mob, attacker);
	}
}
