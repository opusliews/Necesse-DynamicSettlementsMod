package opusliews.forge;

import java.lang.reflect.Field;
import necesse.entity.objectEntity.AnyLogFueledProcessingTechInventoryObjectEntity;
import necesse.entity.objectEntity.FueledProcessingInventoryObjectEntity;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;

public final class ForgeHeatSystem {
	private static final ThreadLocal<ProcessingForgeObjectEntity> allowedIdleFuelStart = new ThreadLocal<>();
	private static final Field remainingFuelTimeField;
	private static final Field fuelPausedField;

	static {
		try {
			remainingFuelTimeField = FueledProcessingInventoryObjectEntity.class.getDeclaredField("remainingFuelTime");
			remainingFuelTimeField.setAccessible(true);
			fuelPausedField = FueledProcessingInventoryObjectEntity.class.getDeclaredField("fuelPaused");
			fuelPausedField.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Could not initialize forge heat reflection", e);
		}
	}

	private ForgeHeatSystem() {
	}

	public static boolean isRunning(ProcessingForgeObjectEntity forge) {
		return forge != null && forge.isFuelRunning();
	}

	public static int getRemainingFuelTime(ProcessingForgeObjectEntity forge) {
		if (forge == null) return 0;
		try {
			return Math.max(0, remainingFuelTimeField.getInt(forge));
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Could not read forge remaining fuel time", e);
		}
	}

	public static boolean hasStoredFuel(ProcessingForgeObjectEntity forge) {
		return forge != null && forge.canUseFuel();
	}

	public static void ensureFuelUnpaused(ProcessingForgeObjectEntity forge) {
		if (forge == null || getRemainingFuelTime(forge) <= 0) return;
		try {
			if (fuelPausedField.getBoolean(forge)) {
				fuelPausedField.setBoolean(forge, false);
				forge.markProgressDirty();
			}
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Could not unpause forge fuel", e);
		}
	}

	public static boolean shouldKeepCurrentFuelBurning(AnyLogFueledProcessingTechInventoryObjectEntity objectEntity) {
		return objectEntity instanceof ProcessingForgeObjectEntity && objectEntity.isFuelRunning();
	}

	public static boolean shouldBlockAutomaticIdleFuelUse(
			AnyLogFueledProcessingTechInventoryObjectEntity objectEntity,
			boolean useFuel
	) {
		if (!useFuel || !(objectEntity instanceof ProcessingForgeObjectEntity)) return false;

		ProcessingForgeObjectEntity forge = (ProcessingForgeObjectEntity)objectEntity;
		if (allowedIdleFuelStart.get() == forge) return false;
		if (forge.getNextProcessTask() != null) return false;

		return forge.shouldKeepFuelRunning();
	}

	public static boolean consumeOneFuelUnit(ProcessingForgeObjectEntity forge) {
		if (forge == null) return false;

		ProcessingForgeObjectEntity old = allowedIdleFuelStart.get();
		allowedIdleFuelStart.set(forge);
		try {
			boolean used = forge.useFuel(true);
			if (used) ensureFuelUnpaused(forge);
			return used;
		} finally {
			if (old == null) {
				allowedIdleFuelStart.remove();
			} else {
				allowedIdleFuelStart.set(old);
			}
		}
	}

	public static boolean tryStartIdleForge(ProcessingForgeObjectEntity forge) {
		if (forge == null) return false;
		if (forge.isFuelRunning()) return true;
		return consumeOneFuelUnit(forge);
	}
}
