package opusliews.progression;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import necesse.engine.GlobalData;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.network.client.Client;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.state.MainGame;
import necesse.entity.mobs.Attacker;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.item.Item;
import necesse.inventory.item.placeableItem.consumableItem.food.FoodConsumableItem;
import opusliews.network.PacketEarlyProgressionSync;

public class EarlyHealthProgressionSystem {
	public static final int startingMaxHealth = 20;
	public static final int healthPerGoal = 20;
	public static final int hostileKillsTarget = 50;
	public static final int oreMinedTarget = 50;
	public static final int uniqueFoodsTarget = 8;
	public static final int startingHungerPips = 2;
	public static final int maxHungerPips = 8;

	private static final String saveKey = "DS_EARLY_PROGRESSION";
	private static final Set<String> earlyOreObjects =  Stream.of(
			"copperorerock", "ironorerock", "goldorerock").collect(Collectors.toSet());
	private static final WeakHashMap<PlayerMob, ProgressData> dataByPlayer = new WeakHashMap<>();
	private static ClientProgress clientProgress = new ClientProgress();

	public enum Goal {
		HOSTILE_KILLS,
		ORE_MINED,
		UNIQUE_FOODS,
		CAVE_TIME
	}

	public static class ProgressData {
		public int hostileKills;
		public int oreMined;
		public long caveTime;
		public long lastWorldTime = -1L;
		public long lastSyncTime;
		public boolean hostileKillsRewarded;
		public boolean oreMinedRewarded;
		public boolean uniqueFoodsRewarded;
		public boolean caveTimeRewarded;
	}

	public static class ClientProgress {
		public int hostileKills;
		public int oreMined;
		public int uniqueFoods;
		public long caveTime;
		public long caveTarget;
		public boolean hostileKillsRewarded;
		public boolean oreMinedRewarded;
		public boolean uniqueFoodsRewarded;
		public boolean caveTimeRewarded;
	}

	public static ProgressData getData(PlayerMob player) {
		synchronized (dataByPlayer) {
			return dataByPlayer.computeIfAbsent(player, key -> new ProgressData());
		}
	}

	public static void initializeNewPlayer(PlayerMob player) {
		if (player == null) return;
		player.setMaxHealth(startingMaxHealth);
		player.setHealthHidden(startingMaxHealth);
		player.hungerLevel = 1.0F;
	}

	public static void addSaveData(PlayerMob player, SaveData save) {
		ProgressData data = getData(player);
		SaveData out = new SaveData(saveKey);
		out.addInt("hostileKills", data.hostileKills);
		out.addInt("oreMined", data.oreMined);
		out.addLong("caveTime", data.caveTime);
		out.addBoolean("hostileKillsRewarded", data.hostileKillsRewarded);
		out.addBoolean("oreMinedRewarded", data.oreMinedRewarded);
		out.addBoolean("uniqueFoodsRewarded", data.uniqueFoodsRewarded);
		out.addBoolean("caveTimeRewarded", data.caveTimeRewarded);
		save.addSaveData(out);
	}

	public static void applyLoadData(PlayerMob player, LoadData save) {
		ProgressData data = getData(player);
		LoadData in = save.getFirstLoadDataByName(saveKey);
		if (in == null) return;
		data.hostileKills = in.getInt("hostileKills", 0, false);
		data.oreMined = in.getInt("oreMined", 0, false);
		data.caveTime = in.getLong("caveTime", 0L, false);
		data.hostileKillsRewarded = in.getBoolean("hostileKillsRewarded", false, false);
		data.oreMinedRewarded = in.getBoolean("oreMinedRewarded", false, false);
		data.uniqueFoodsRewarded = in.getBoolean("uniqueFoodsRewarded", false, false);
		data.caveTimeRewarded = in.getBoolean("caveTimeRewarded", false, false);
		data.lastWorldTime = -1L;
	}

	public static void serverTick(PlayerMob player) {
		if (player == null || !player.isServerClient() || player.getLevel() == null) return;
		ServerClient client = player.getServerClient();
		ProgressData data = getData(player);
		long now = player.getLevel().getWorldTime();

		if (data.lastWorldTime < 0L) data.lastWorldTime = now;
		long delta = Math.max(0L, now - data.lastWorldTime);
		data.lastWorldTime = now;
		if (!data.caveTimeRewarded && player.getLevel().isCave) {
			data.caveTime += delta;
		}

		int uniqueFoods = getUniqueFoodCount(client);
		checkRewards(client, data, uniqueFoods);

		long realNow = System.currentTimeMillis();
		if (realNow - data.lastSyncTime >= 1000L) {
			data.lastSyncTime = realNow;
			sendSync(client, data, uniqueFoods);
		}
	}

	public static void onPlayerDeath(PlayerMob player) {
		if (player == null || !player.isServerClient()) return;
		ServerClient client = player.getServerClient();
		ProgressData data = getData(player);
		if (data.caveTimeRewarded) {
			data.caveTime = Math.max(data.caveTime, getCaveTarget(client));
		}
		else {
			data.caveTime = 0L;
		}
		data.lastWorldTime = -1L;
		sendSync(client, data, getUniqueFoodCount(client));
	}

	private static boolean isValidFoodForProgress(Item item) {
		if (!(item instanceof FoodConsumableItem))
			return false;

		return !((FoodConsumableItem)item).isDebuff;
	}

	public static void onMobDeath(Mob mob, Set attackers) {
		if (mob == null || !mob.isHostile || mob.getLevel() == null || !mob.getLevel().isServer()) return;
		HashSet<ServerClient> credited = new HashSet<>();
		for (Object value : attackers) {
			if (!(value instanceof Attacker)) continue;
			Mob owner = ((Attacker)value).getAttackOwner();
			if (!(owner instanceof PlayerMob) || !((PlayerMob)owner).isServerClient()) continue;
			credited.add(((PlayerMob)owner).getServerClient());
		}
		for (ServerClient client : credited) {
			ProgressData data = getData(client.playerMob);
			if (!data.hostileKillsRewarded) data.hostileKills++;
			checkRewards(client, data, getUniqueFoodCount(client));
			sendSync(client, data, getUniqueFoodCount(client));
		}
	}

	public static void onOreMined(ServerClient client, String objectStringID) {
		if (client == null || !earlyOreObjects.contains(objectStringID)) return;
		ProgressData data = getData(client.playerMob);
		if (!data.oreMinedRewarded) data.oreMined++;
		checkRewards(client, data, getUniqueFoodCount(client));
		sendSync(client, data, getUniqueFoodCount(client));
	}

	public static void onFoodConsumed(PlayerMob player, FoodConsumableItem item) {
		if (player == null || item == null || !player.isServerClient()) return;
		ServerClient client = player.getServerClient();
		client.forceCombineNewStats();
		int uniqueFoods = getUniqueFoodCount(client);
		ProgressData data = getData(player);
		checkRewards(client, data, uniqueFoods);
		sendSync(client, data, uniqueFoods);
		player.sendHungerPacket();
	}

	public static boolean isEarlyOreObject(String stringID) {
		return earlyOreObjects.contains(stringID);
	}

	public static int getUniqueFoodCount(ServerClient client) {
		if (client == null) return 0;
		final int[] count = {0};
		client.characterStats().food_consumed.forEach((stringID, amount) -> {
			Item item = ItemRegistry.getItem(stringID);

			if (amount > 0 && isValidFoodForProgress(item)) {
				count[0]++;
			}
		});
		return count[0];
	}

	public static int getUniqueFoodCount(Client client) {
		if (client == null || client.characterStats == null) return clientProgress.uniqueFoods;

		final int[] count = {0};
		client.characterStats.food_consumed.forEach((stringID, amount) -> {
			Item item = ItemRegistry.getItem(stringID);

			if (amount > 0 && isValidFoodForProgress(item)) {
				count[0]++;
			}
		});

		return Math.max(count[0], clientProgress.uniqueFoods);
	}

	public static int getHungerPips(ServerClient client) {
		return Math.min(maxHungerPips, startingHungerPips + getUniqueFoodCount(client));
	}

	public static int getHungerPips(Client client) {
		return Math.min(maxHungerPips, startingHungerPips + getUniqueFoodCount(client));
	}

	public static int getCurrentClientHungerPips() {
		if (GlobalData.getCurrentState() instanceof MainGame) {
			Client client = ((MainGame)GlobalData.getCurrentState()).getClient();
			if (client != null) return getHungerPips(client);
		}
		return startingHungerPips;
	}

	public static float scaleHungerAmount(PlayerMob player, float amount) {
		int pips = startingHungerPips;
		if (player != null) {
			if (player.isServerClient()) pips = getHungerPips(player.getServerClient());
			else if (player.isClientClient()) pips = getHungerPips(player.getClientClient().getClient());
		}
		return amount * 10.0F / Math.max(1, pips);
	}

	public static void writeCharacterPacket(PlayerMob player, PacketWriter writer) {
		ProgressData data = getData(player);
		writer.putNextInt(data.hostileKills);
		writer.putNextInt(data.oreMined);
		writer.putNextLong(data.caveTime);
		writer.putNextBoolean(data.hostileKillsRewarded);
		writer.putNextBoolean(data.oreMinedRewarded);
		writer.putNextBoolean(data.uniqueFoodsRewarded);
		writer.putNextBoolean(data.caveTimeRewarded);
	}

	public static void applyCharacterPacket(PlayerMob player, PacketReader reader) {
		ProgressData data = getData(player);
		data.hostileKills = reader.getNextInt();
		data.oreMined = reader.getNextInt();
		data.caveTime = reader.getNextLong();
		data.hostileKillsRewarded = reader.getNextBoolean();
		data.oreMinedRewarded = reader.getNextBoolean();
		data.uniqueFoodsRewarded = reader.getNextBoolean();
		data.caveTimeRewarded = reader.getNextBoolean();
		data.lastWorldTime = -1L;
		data.lastSyncTime = 0L;
	}

	public static void applyClientSync(int hostileKills, int oreMined, int uniqueFoods, long caveTime, long caveTarget, boolean hostileKillsRewarded, boolean oreMinedRewarded, boolean uniqueFoodsRewarded, boolean caveTimeRewarded) {
		ClientProgress data = new ClientProgress();
		data.hostileKills = hostileKills;
		data.oreMined = oreMined;
		data.uniqueFoods = uniqueFoods;
		data.caveTime = caveTime;
		data.caveTarget = caveTarget;
		data.hostileKillsRewarded = hostileKillsRewarded;
		data.oreMinedRewarded = oreMinedRewarded;
		data.uniqueFoodsRewarded = uniqueFoodsRewarded;
		data.caveTimeRewarded = caveTimeRewarded;
		clientProgress = data;
	}

	public static ClientProgress getClientProgress() {
		return clientProgress;
	}

	public static int getGoalCurrent(Client client, Goal goal) {
		ClientProgress data = clientProgress;
		switch (goal) {
			case HOSTILE_KILLS: return data.hostileKills;
			case ORE_MINED: return data.oreMined;
			case UNIQUE_FOODS: return getUniqueFoodCount(client);
			case CAVE_TIME:
				if (data.caveTimeRewarded) return getGoalTarget(Goal.CAVE_TIME);
				return (int)Math.min(Integer.MAX_VALUE, data.caveTime / 1000L);
			default: return 0;
		}
	}

	public static int getGoalTarget(Goal goal) {
		switch (goal) {
			case HOSTILE_KILLS: return hostileKillsTarget;
			case ORE_MINED: return oreMinedTarget;
			case UNIQUE_FOODS: return uniqueFoodsTarget;
			case CAVE_TIME: return (int)Math.max(1L, clientProgress.caveTarget / 1000L);
			default: return 1;
		}
	}

	public static String getGoalProgressText(Client client, Goal goal) {
		int current = getGoalCurrent(client, goal);
		int target = getGoalTarget(goal);
		if (goal == Goal.CAVE_TIME) {
			return formatDuration(current) + " / " + formatDuration(target);
		}
		return Math.min(current, target) + " / " + target;
	}

	public static String formatDuration(int seconds) {
		int safe = Math.max(0, seconds);
		int minutes = safe / 60;
		int remainder = safe % 60;
		return String.format("%d:%02d", minutes, remainder);
	}

	public static boolean isGoalComplete(Client client, Goal goal) {
		ClientProgress data = clientProgress;
		switch (goal) {
			case HOSTILE_KILLS: return data.hostileKillsRewarded;
			case ORE_MINED: return data.oreMinedRewarded;
			case UNIQUE_FOODS: return data.uniqueFoodsRewarded;
			case CAVE_TIME: return data.caveTimeRewarded;
			default: return false;
		}
	}

	private static void checkRewards(ServerClient client, ProgressData data, int uniqueFoods) {
		if (!data.hostileKillsRewarded && data.hostileKills >= hostileKillsTarget) {
			data.hostileKillsRewarded = true;
			awardHealth(client);
		}
		if (!data.oreMinedRewarded && data.oreMined >= oreMinedTarget) {
			data.oreMinedRewarded = true;
			awardHealth(client);
		}
		if (!data.uniqueFoodsRewarded && uniqueFoods >= uniqueFoodsTarget) {
			data.uniqueFoodsRewarded = true;
			awardHealth(client);
		}
		long caveTarget = getCaveTarget(client);
		if (!data.caveTimeRewarded && data.caveTime >= caveTarget) {
			data.caveTimeRewarded = true;
			awardHealth(client);
		}
	}

	private static void awardHealth(ServerClient client) {
		PlayerMob player = client.playerMob;
		player.setMaxHealth(player.getMaxHealthFlat() + healthPerGoal);
		player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + healthPerGoal));
		player.sendHealthPacket(true);
	}

	private static long getCaveTarget(ServerClient client) {
		if (client == null || client.getLevel() == null) return 900000L;
		return (long)client.getLevel().getWorldEntity().getDayTimeMax() * 1000L;
	}

	private static void sendSync(ServerClient client, ProgressData data, int uniqueFoods) {
		if (client == null) return;
		client.sendPacket(new PacketEarlyProgressionSync(
				data.hostileKills,
				data.oreMined,
				uniqueFoods,
				data.caveTime,
				getCaveTarget(client),
				data.hostileKillsRewarded,
				data.oreMinedRewarded,
				data.uniqueFoodsRewarded,
				data.caveTimeRewarded
		));
	}
}
