package opusliews.crafting;

import java.util.ArrayList;
import java.util.List;
import necesse.engine.network.PacketReader;
import necesse.engine.network.PacketWriter;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;

public class AnvilCraftingTask {
	public static final int CONDITION_CRAFT_UNITS = 0;
	public static final int CONDITION_KEEP_STOCKED = 1;

	public static final int STATUS_FINISHED = 0;
	public static final int STATUS_IN_PROGRESS = 1;
	public static final int STATUS_PROBLEM = 2;
	public static final int STATUS_PAUSED = 3;

	public int itemID;
	public int conditionType;
	public int amount;
	public boolean paused;
	public int status = STATUS_FINISHED;
	public final ArrayList<String> problemDetails = new ArrayList<>();

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
		paused = data.getBoolean("paused", false, false);
	}

	public AnvilCraftingTask(PacketReader reader) {
		this(reader.getNextInt(), reader.getNextByteUnsigned(), reader.getNextInt());
		paused = reader.getNextBoolean();
		status = reader.getNextByteUnsigned();
		int problemCount = reader.getNextByteUnsigned();
		for (int i = 0; i < problemCount; i++) {
			problemDetails.add(reader.getNextString());
		}
	}

	public void addSaveData(SaveData data) {
		data.addInt("itemID", itemID);
		data.addInt("conditionType", conditionType);
		data.addInt("amount", amount);
		data.addBoolean("paused", paused);
	}

	public void writePacket(PacketWriter writer) {
		writer.putNextInt(itemID);
		writer.putNextByteUnsigned(conditionType);
		writer.putNextInt(amount);
		writer.putNextBoolean(paused);
		writer.putNextByteUnsigned(status);
		writer.putNextByteUnsigned(Math.min(255, problemDetails.size()));
		for (int i = 0; i < problemDetails.size() && i < 255; i++) {
			writer.putNextString(problemDetails.get(i));
		}
	}

	public boolean setRuntimeStatus(int status, List<String> details) {
		boolean changed = this.status != status || !problemDetails.equals(details);
		this.status = status;
		problemDetails.clear();
		problemDetails.addAll(details);
		return changed;
	}

	public AnvilCraftingTask copy() {
		AnvilCraftingTask copy = new AnvilCraftingTask(itemID, conditionType, amount);
		copy.paused = paused;
		copy.status = status;
		copy.problemDetails.addAll(problemDetails);
		return copy;
	}
}
