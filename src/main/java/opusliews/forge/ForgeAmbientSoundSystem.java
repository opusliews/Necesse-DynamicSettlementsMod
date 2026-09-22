package opusliews.forge;

import java.lang.reflect.Field;
import necesse.engine.sound.SoundPlayer;
import necesse.entity.objectEntity.ObjectEntity;
import necesse.entity.objectEntity.ProcessingForgeObjectEntity;

public final class ForgeAmbientSoundSystem {
	private static final Field ambientSoundPlayerField;

	static {
		try {
			ambientSoundPlayerField = ObjectEntity.class.getDeclaredField("ambientSoundPlayer");
			ambientSoundPlayerField.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Could not initialize forge ambient sound reflection", e);
		}
	}

	private ForgeAmbientSoundSystem() {
	}

	public static void stopIfCold(ProcessingForgeObjectEntity forge) {
		if (forge == null || forge.isFuelRunning()) return;

		try {
			SoundPlayer player = (SoundPlayer)ambientSoundPlayerField.get(forge);
			if (player != null && !player.isDone()) player.stop();
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Could not stop forge ambient sound", e);
		}
	}
}
