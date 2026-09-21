package opusliews.durability;

import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.recipe.Recipe;
import necesse.level.maps.Level;
import necesse.level.maps.LevelObject;

public class DurabilityContext {
	public final Level level;
	public final PlayerMob player;
	public final Mob targetMob;
	public final LevelObject targetObject;
	public final Recipe recipe;
	public final String customAction;

	public DurabilityContext(Level level, PlayerMob player, Mob targetMob, LevelObject targetObject, Recipe recipe, String customAction) {
		this.level = level;
		this.player = player;
		this.targetMob = targetMob;
		this.targetObject = targetObject;
		this.recipe = recipe;
		this.customAction = customAction;
	}

	public static DurabilityContext player(Level level, PlayerMob player) {
		return new DurabilityContext(level, player, null, null, null, null);
	}

	public static DurabilityContext mob(Level level, PlayerMob player, Mob targetMob) {
		return new DurabilityContext(level, player, targetMob, null, null, null);
	}

	public static DurabilityContext object(Level level, PlayerMob player, LevelObject targetObject) {
		return new DurabilityContext(level, player, null, targetObject, null, null);
	}

	public static DurabilityContext crafting(Level level, PlayerMob player, Recipe recipe) {
		return new DurabilityContext(level, player, null, null, recipe, null);
	}

	public static DurabilityContext custom(Level level, PlayerMob player, String customAction) {
		return new DurabilityContext(level, player, null, null, null, customAction);
	}
}
