package opusliews.tile;

import necesse.engine.registries.BuffRegistry;
import necesse.entity.mobs.GameDamage;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.level.maps.Level;

public final class FireTileDamage {
	private static final String cooldownKey = "dynamicsettlementsfiretile";
	private static final long damageCooldown = 1000L;
	private static final float contactDamage = 10.0F;
	private static final float onFireDuration = 5.0F;

	private FireTileDamage() {
	}

	public static void tick(Mob mob, Level level) {
		if (mob == null || level == null || !level.isServer()) return;
		if (!mob.canLevelInteract() || mob.isFlying() || !mob.canTakeDamage()) return;
		if (mob.isOnGenericCooldown(cooldownKey)) return;

		float fireMultiplier = mob.buffManager.getModifier(BuffModifiers.FIRE_DAMAGE);
		float damage = contactDamage * fireMultiplier;
		if (damage != 0.0F) mob.isServerHit(new GameDamage(damage), 0.0F, 0.0F, 0.0F, null);

		mob.startGenericCooldown(cooldownKey, damageCooldown);
		mob.addBuff(new ActiveBuff(BuffRegistry.Debuffs.ON_FIRE, mob, onFireDuration, null), true);
	}
}
