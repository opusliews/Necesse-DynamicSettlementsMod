package opusliews.jobs;

import necesse.engine.localization.message.StaticMessage;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.friendly.human.humanShop.BlacksmithHumanMob;
import necesse.entity.mobs.job.*;
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.TileActiveJob;
import necesse.inventory.recipe.Recipe;
import necesse.level.maps.levelData.jobs.JobMoveToTile;
import necesse.level.maps.levelData.jobs.TileLevelJob;
import opusliews.crafting.AnvilCraftingLogic;
import opusliews.crafting.AnvilCraftingTask;
import opusliews.network.PacketAnvilCraftingSound;
import opusliews.object.AnvilCraftingTaskBoardObjectEntity;

public class AnvilCraftingLevelJob extends TileLevelJob {
	private final int boardX;
	private final int boardY;

	public AnvilCraftingLevelJob(int tileX, int tileY, int boardX, int boardY) {
		super(tileX, tileY);
		this.boardX = boardX;
		this.boardY = boardY;
	}

	public AnvilCraftingLevelJob(LoadData save) {
		super(save);
		boardX = save.getInt("boardX", -1);
		boardY = save.getInt("boardY", -1);
	}

	@Override
	public void addSaveData(SaveData save) {
		super.addSaveData(save);
		save.addInt("boardX", boardX);
		save.addInt("boardY", boardY);
	}

	@Override
	public boolean shouldSave() {
		return false;
	}

	public AnvilCraftingTaskBoardObjectEntity getBoard() {
		if (getLevel() == null || boardX < 0 || boardY < 0) {
			return null;
		}
		Object object = getLevel().entityManager.getObjectEntity(boardX, boardY);
		return object instanceof AnvilCraftingTaskBoardObjectEntity
				? (AnvilCraftingTaskBoardObjectEntity)object
				: null;
	}

	@Override
	public boolean isValid() {
		if (!super.isValid()) {
			return false;
		}

		AnvilCraftingTaskBoardObjectEntity board = getBoard();
		if (board == null || board.getTaskCount() == 0) {
			return false;
		}

		java.awt.Point anvil = board.getLinkedAnvil();
		return anvil != null && anvil.x == tileX && anvil.y == tileY && board.getValidLinkedAnvilObject() != null;
	}

	@Override
	public boolean estimateCanMoveTo(EntityJobWorker worker) {
		AnvilCraftingTaskBoardObjectEntity board = getBoard();
		if (board == null || getLevel().getWorldEntity().isNight()) {
			return false;
		}

		int uniqueID = worker.getMobWorker().getUniqueID();
		if (!board.canBeClaimedBy(uniqueID)) {
			return false;
		}
		if (!board.hasActionableTasks()) {
			return false;
		}
		return worker.getMobWorker().estimateCanMoveTo(tileX, tileY, true);
	}

	@Override
	public int getSameTypePriority(FoundJob foundJob) {
		AnvilCraftingTaskBoardObjectEntity board = getBoard();
		if (board == null) {
			return 1000;
		}

		int uniqueID = foundJob.worker.getMobWorker().getUniqueID();
		if (board.isAssignedTo(uniqueID)) {
			return 100000;
		}

		int day = getLevel().getWorldEntity().getDay();
		int hash = uniqueID * 31 + boardX * 73856093 + boardY * 19349663 + day * 83492791;
		return 10000 + Math.floorMod(hash, 1000);
	}

	public static JobSequence getJobSequence(EntityJobWorker worker, FoundJob foundJob) {
		AnvilCraftingLevelJob job = (AnvilCraftingLevelJob)foundJob.job;
		AnvilCraftingTaskBoardObjectEntity board = job.getBoard();
		if (board == null || job.getLevel().getWorldEntity().isNight() || !board.hasActionableTasks()) {
			return null;
		}

		int uniqueID = worker.getMobWorker().getUniqueID();
		if (!board.claimBlacksmith(uniqueID)) {
			return null;
		}

		LinkedListJobSequence sequence = new LinkedListJobSequence(new StaticMessage("Working at anvil"), false);
		sequence.add(new TileActiveJob(worker, foundJob.priority, job.tileX, job.tileY) {
			private int currentTaskIndex = -1;
			private long actionCompleteTime;
			private long nextSoundTime;

			@Override
			public JobMoveToTile getMoveToTile(JobMoveToTile lastTile) {
				return new JobMoveToTile(tileX, tileY, true);
			}

			@Override
			public int getCompleteRange() {
				return 8;
			}

			@Override
			public void tick(boolean isCurrent, boolean isMovingTo) {
				if (!isCurrent || isMovingTo) {
					return;
				}

				worker.showWorkAnimation(
						tileX * 32 + 16,
						tileY * 32 + 16,
						ItemRegistry.getItem("constructionhammer"),
						1000,
						true
				);

				long now = job.getLevel().getTime();
				if (now >= nextSoundTime) {
					nextSoundTime = now + GameRandom.globalRandom.getIntBetween(700, 1300);
					job.getLevel().getServer().network.sendToClientsWithTile(
							new PacketAnvilCraftingSound(tileX, tileY),
							job.getLevel(),
							tileX,
							tileY
					);
				}
			}

			@Override
			public boolean isValid(boolean isCurrent) {
				AnvilCraftingTaskBoardObjectEntity currentBoard = job.getBoard();
				return job.isValid()
						&& currentBoard != null
						&& currentBoard.isAssignedTo(uniqueID)
						&& !job.getLevel().getWorldEntity().isNight();
			}

			@Override
			public void onCancelled(boolean becauseOfInvalid, boolean isCurrent, boolean isMovingTo) {
				super.onCancelled(becauseOfInvalid, isCurrent, isMovingTo);
				AnvilCraftingTaskBoardObjectEntity currentBoard = job.getBoard();
				if (currentBoard == null) {
					return;
				}
				if (job.getLevel().getWorldEntity().isNight() || !job.isValid() || !currentBoard.hasActionableTasks()) {
					currentBoard.releaseBlacksmith();
				}
			}

			@Override
			public ActiveJobResult perform() {
				AnvilCraftingTaskBoardObjectEntity currentBoard = job.getBoard();
				if (currentBoard == null || job.getLevel().getWorldEntity().isNight()) {
					if (currentBoard != null) {
						currentBoard.releaseBlacksmith();
					}
					return ActiveJobResult.FINISHED;
				}

				if (currentTaskIndex < 0) {
					currentTaskIndex = currentBoard.findNextActionableTask();
					if (currentTaskIndex < 0) {
						currentBoard.releaseBlacksmith();
						return ActiveJobResult.FINISHED;
					}

					int happiness = Math.max(0, Math.min(100, ((HumanMob)worker.getMobWorker()).getSettlerHappiness()));
					int actionDelay = 5000 - happiness * 45;
					actionCompleteTime = job.getLevel().getTime() + actionDelay;
					return ActiveJobResult.PERFORMING;
				}

				if (job.getLevel().getTime() < actionCompleteTime) {
					return ActiveJobResult.PERFORMING;
				}

				currentBoard.refreshTaskStatusesNow();
				AnvilCraftingTask task = currentBoard.getTask(currentTaskIndex);
				if (task == null || task.status != AnvilCraftingTask.STATUS_IN_PROGRESS) {
					currentBoard.advanceAfterTask(currentTaskIndex);
					currentTaskIndex = -1;
					return ActiveJobResult.PERFORMING;
				}

				Recipe recipe = AnvilCraftingLogic.getRecipe(currentBoard, task.itemID);
				if (recipe == null) {
					currentBoard.refreshTaskStatusesNow();
					currentBoard.advanceAfterTask(currentTaskIndex);
					currentTaskIndex = -1;
					return ActiveJobResult.PERFORMING;
				}

				AnvilCraftingLogic.CraftResult result = AnvilCraftingLogic.craftOne(currentBoard, recipe);
				if (result.success && task.conditionType == AnvilCraftingTask.CONDITION_CRAFT_UNITS) {
					currentBoard.decrementCraftAmount(currentTaskIndex, result.producedAmount);
				} else {
					currentBoard.refreshTaskStatusesNow();
				}

				currentBoard.advanceAfterTask(currentTaskIndex);
				currentTaskIndex = -1;

				if (!currentBoard.hasActionableTasks()) {
					currentBoard.releaseBlacksmith();
					return ActiveJobResult.FINISHED;
				}
				return ActiveJobResult.PERFORMING;
			}
		});
		return sequence;
	}

	private static boolean hasAvailableAnvilWork(BlacksmithHumanMob blacksmith) {
		if (blacksmith.getLevel() == null || blacksmith.getLevel().getWorldEntity().isNight()) {
			return false;
		}

		int uniqueID = blacksmith.getUniqueID();
		for (Object object : blacksmith.getLevel().entityManager.objectEntities) {
			if (!(object instanceof AnvilCraftingTaskBoardObjectEntity)) {
				continue;
			}

			AnvilCraftingTaskBoardObjectEntity board = (AnvilCraftingTaskBoardObjectEntity)object;
			if (!board.hasActionableTasksCached() || !board.canBeClaimedBy(uniqueID)) {
				continue;
			}

			java.awt.Point anvil = board.getLinkedAnvil();
			if (anvil != null && blacksmith.estimateCanMoveTo(anvil.x, anvil.y, true)) {
				return true;
			}
		}
		return false;
	}

	public static JobTypeHandler.SubHandler handler(EntityJobWorker worker, JobTypeHandler handler) {
		if (!(worker instanceof BlacksmithHumanMob)) {
			return null;
		}

		BlacksmithHumanMob blacksmith = (BlacksmithHumanMob)worker;
		JobTypeHandler.SubHandler anvilHandler = handler.setJobHandler(
				AnvilCraftingLevelJob.class,
				foundJob -> getJobSequence(blacksmith, (FoundJob)foundJob)
		).setPredicate(() -> !blacksmith.isOnStrike()
				&& !blacksmith.hasCompletedMission()
				&& blacksmith.isSettlerWithinSettlement());

		// Suppress the other crafting handlers only while this blacksmith has an
		// available task-board anvil job. Other job types keep their normal
		// priorities and can still interrupt this work.
		for (Object object : new java.util.ArrayList<>(handler.getJobHandlers())) {
			JobTypeHandler.SubHandler other = (JobTypeHandler.SubHandler)object;
			if (other != anvilHandler
					&& other.priority != null
					&& "crafting".equals(other.priority.type.getStringID())) {
				other.addPredicate(() -> !hasAvailableAnvilWork(blacksmith));
			}
		}

		return anvilHandler;
	}
}
