package opusliews.worldgengating;

import necesse.engine.localization.Localization;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.hostile.MimicMob;
import necesse.entity.objectEntity.TrialEntranceObjectEntity;
import necesse.gfx.gameTooltips.GameTooltipManager;
import necesse.gfx.gameTooltips.StringTooltips;
import necesse.gfx.gameTooltips.TooltipLocation;
import necesse.inventory.InventoryItem;
import opusliews.network.PacketWorldgenMimicGatingState;

import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Map;

public final class WorldgenSpecialLootGatingSystem {
	private static final String mimicUnlockedSaveKey = "dsWorldgenMimicUnlocked";
	private static final Map<Integer, MimicState> clientMimicStates = new HashMap<>();
	private static final Map<MimicMob, Boolean> serverMimicUnlocked = new WeakHashMap<>();

	private WorldgenSpecialLootGatingSystem() {
	}

	public static void refreshTrialEntrance(TrialEntranceObjectEntity entity) {
		if (entity == null || entity.getLevel() == null) return;
		WorldgenLootTier tier = null;
		for (Object rawList : entity.lootList) {
			if (!(rawList instanceof List)) continue;
			for (Object rawItem : (List)rawList) {
				if (rawItem instanceof InventoryItem) tier = WorldgenLootTier.max(tier, WorldgenContentTierResolver.getItemTier((InventoryItem)rawItem));
			}
		}
		if (tier == null) return;
		WorldgenGatingLevelData data = WorldgenGatingLevelData.get(entity.getLevel(), true);
		data.markSpecial(0, entity.tileX, entity.tileY, WorldgenGatingData.NaturalType.LOCKED_CONTAINER, tier);
	}

	public static void writeMimicSpawnState(Mob mob, PacketWriter writer) {
		if (!(mob instanceof MimicMob)) return;
		MimicMob mimic = (MimicMob)mob;
		WorldgenLootTier tier = getServerMimicTier(mimic);
		writer.putNextByteUnsigned(tier == null ? 0 : tier.ordinal() + 1);
		writer.putNextBoolean(isServerMimicUnlocked(mimic));
	}

	public static void readMimicSpawnState(Mob mob, PacketReader reader) {
		if (!(mob instanceof MimicMob)) return;
		int tierIndex = reader.getNextByteUnsigned();
		WorldgenLootTier tier = tierIndex == 0 ? null : WorldgenLootTier.values()[tierIndex - 1];
		boolean unlocked = reader.getNextBoolean();
		applyClientMimicState(mob.getUniqueID(), tier, unlocked);
	}

	public static void applyClientMimicState(int uniqueID, WorldgenLootTier tier, boolean unlocked) {
		clientMimicStates.put(uniqueID, new MimicState(tier, unlocked));
	}

	public static void handleMimicInteract(Mob mob, PlayerMob player) {
		if (!(mob instanceof MimicMob) || player == null) return;
		MimicMob mimic = (MimicMob)mob;
		WorldgenLootTier tier = getMimicTier(mimic);
		if (tier == null || isMimicUnlocked(mimic)) return;

		if (mimic.getLevel().isClient()) {
			if (!WorldgenLockedContainerSystem.hasHeldMatchingKey(player, tier)) WorldgenLockedContainerSystem.playLockedSound(mimic.getLevel(), mimic.getTileX(), mimic.getTileY());
			return;
		}

		WorldgenLockedContainerSystem.discover(player.getServerClient());
		if (!WorldgenLockedContainerSystem.consumeHeldMatchingKey(player, tier)) return;
		setServerMimicUnlocked(mimic, true);
		mimic.getLevel().getServer().network.sendToClientsWithEntity(new PacketWorldgenMimicGatingState(mimic.getUniqueID(), tier, true), mimic);
	}

	public static boolean shouldBlockMimicDamage(Mob mob, Attacker attacker) {
		if (!(mob instanceof MimicMob) || mob.getLevel() == null || !mob.getLevel().isServer()) return false;
		MimicMob mimic = (MimicMob)mob;
		WorldgenLootTier tier = getServerMimicTier(mimic);
		if (tier == null || isServerMimicUnlocked(mimic)) return false;
		return attacker != null && attacker.getFirstPlayerOwner() != null;
	}

	public static void addMimicHoverTooltip(MimicMob mimic) {
		if (mimic == null || mimic.getLevel() == null || !mimic.getLevel().isClient()) return;
		WorldgenLootTier tier = getMimicTier(mimic);
		if (tier == null || isMimicUnlocked(mimic)) return;
		String keyName = necesse.engine.registries.ItemRegistry.getDisplayName(necesse.engine.registries.ItemRegistry.getItemID(tier.keyStringID));
		GameTooltipManager.addTooltip(new StringTooltips(
				Localization.translate("worldgengating", "lockedhint1"),
				Localization.translate("worldgengating", "lockedhint3", "key", keyName)
		), TooltipLocation.INTERACT_FOCUS);
	}

	private static WorldgenLootTier getMimicTier(MimicMob mimic) {
		if (mimic.getLevel() != null && mimic.getLevel().isClient()) {
			MimicState state = clientMimicStates.get(mimic.getUniqueID());
			return state == null ? null : state.tier;
		}
		return getServerMimicTier(mimic);
	}

	private static WorldgenLootTier getServerMimicTier(MimicMob mimic) {
		WorldgenLootTier tier = null;
		for (Object rawItem : mimic.loot) {
			if (rawItem instanceof InventoryItem) tier = WorldgenLootTier.max(tier, WorldgenContentTierResolver.getItemTier((InventoryItem)rawItem));
		}
		return tier;
	}

	private static boolean isMimicUnlocked(MimicMob mimic) {
		if (mimic.getLevel() != null && mimic.getLevel().isClient()) {
			MimicState state = clientMimicStates.get(mimic.getUniqueID());
			return state != null && state.unlocked;
		}
		return isServerMimicUnlocked(mimic);
	}

	public static void addMimicSaveData(MimicMob mimic, SaveData save) {
		if (mimic == null || save == null) return;
		if (isServerMimicUnlocked(mimic)) save.addBoolean(mimicUnlockedSaveKey, true);
	}

	public static void applyMimicLoadData(MimicMob mimic, LoadData save) {
		if (mimic == null || save == null) return;
		serverMimicUnlocked.put(mimic, save.getBoolean(mimicUnlockedSaveKey, false, false));
	}

	private static boolean isServerMimicUnlocked(MimicMob mimic) {
		return Boolean.TRUE.equals(serverMimicUnlocked.get(mimic));
	}

	private static void setServerMimicUnlocked(MimicMob mimic, boolean unlocked) {
		serverMimicUnlocked.put(mimic, unlocked);
	}

	private static class MimicState {
		public final WorldgenLootTier tier;
		public final boolean unlocked;

		private MimicState(WorldgenLootTier tier, boolean unlocked) {
			this.tier = tier;
			this.unlocked = unlocked;
		}
	}
}
