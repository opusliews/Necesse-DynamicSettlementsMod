package opusliews.forge;

import necesse.entity.objectEntity.AnyLogFueledProcessingTechInventoryObjectEntity;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;

public final class ForgeHeatSystem {
	private static final ThreadLocal<ProcessingForgeObjectEntity> allowedIdleFuelStart = new ThreadLocal<>();

	private ForgeHeatSystem() {
	}

	public static boolean isRunning(ProcessingForgeObjectEntity forge) {
		return forge != null && forge.isFuelRunning();
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

	public static boolean tryStartIdleForge(ProcessingForgeObjectEntity forge) {
		if (forge == null) return false;
		if (forge.isFuelRunning()) return true;

		ProcessingForgeObjectEntity old = allowedIdleFuelStart.get();
		allowedIdleFuelStart.set(forge);
		try {
			return forge.useFuel(true);
		} finally {
			if (old == null) {
				allowedIdleFuelStart.remove();
			} else {
				allowedIdleFuelStart.set(old);
			}
		}
	}
}
