package opusliews.forge;

import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;

import java.lang.reflect.Method;

/** Optional Manaworks support; never statically loads Manaworks classes. */
public final class ForgeLinkCompatibility {
	private static final String MANAWORKS_FORGE = "vdgmana.forge.entity.UpgradedForgeEntity";

	private ForgeLinkCompatibility() {}

	public static boolean isSupported(ObjectEntity entity) {
		return entity instanceof ProcessingForgeObjectEntity || isManaworks(entity);
	}

	public static boolean isManaworks(ObjectEntity entity) {
		return entity != null && MANAWORKS_FORGE.equals(entity.getClass().getName());
	}

	/** Manaworks owns its mana network and determines whether this Forge is powered. */
	public static boolean isPowered(ObjectEntity entity) {
		if (!isManaworks(entity)) return false;
		try {
			Method method = entity.getClass().getMethod("isGridPowered");
			return Boolean.TRUE.equals(method.invoke(entity));
		} catch (ReflectiveOperationException | SecurityException exception) {
			return false;
		}
	}
}
