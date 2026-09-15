package opusliews.crafting;

import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

public class AnvilCraftingTask {
	public static final int CONDITION_CRAFT_UNITS = 0;
	public static final int CONDITION_KEEP_STOCKED = 1;

	public int itemID;
	public int conditionType;
	public int amount;

	public AnvilCraftingTask(int itemID, int conditionType, int amount) {
		this.itemID = itemID;
		this.conditionType = conditionType;
		this.amount = Math.max(0, amount);
	}

	public AnvilCraftingTask(LoadData data) {
		this(
				data.getInt("itemID", -1),
				data.getInt("conditionType", CONDITION_CRAFT_UNITS),
				data.getInt("amount", 0)
		);
	}

	public AnvilCraftingTask(PacketReader reader) {
		this(reader.getNextInt(), reader.getNextByteUnsigned(), reader.getNextInt());
	}

	public void addSaveData(SaveData data) {
		data.addInt("itemID", itemID);
		data.addInt("conditionType", conditionType);
		data.addInt("amount", amount);
	}

	public void writePacket(PacketWriter writer) {
		writer.putNextInt(itemID);
		writer.putNextByteUnsigned(conditionType);
		writer.putNextInt(amount);
	}

	public AnvilCraftingTask copy() {
		return new AnvilCraftingTask(itemID, conditionType, amount);
	}
}
