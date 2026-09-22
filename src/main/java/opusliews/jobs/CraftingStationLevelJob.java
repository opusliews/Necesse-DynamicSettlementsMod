package opusliews.jobs;

import necesse.engine.localization.message.StaticMessage;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import necesse.engine.util.GameRandom;
import necesse.entity.mobs.friendly.human.HumanMob;
import necesse.entity.mobs.job.*;
import necesse.entity.mobs.job.activeJob.ActiveJobResult;
import necesse.entity.mobs.job.activeJob.TileActiveJob;
import necesse.inventory.recipe.Recipe;
import necesse.level.maps.levelData.jobs.JobMoveToTile;
import necesse.level.maps.levelData.jobs.TileLevelJob;
import opusliews.crafting.CraftingTaskLogic;
import opusliews.crafting.CraftingTask;
import opusliews.crafting.CraftingTaskRecipe;
import opusliews.crafting.CraftingStoragePool;
import opusliews.forge.ForgeTaskSystem;
import opusliews.forge.ForgeRequirementSystem;
import opusliews.logging.Logging;
import opusliews.object.CraftingTaskBoardObjectEntity;
import opusliews.object.DynamicCraftingStationObjectEntity;

public class CraftingStationLevelJob extends TileLevelJob {
	private final int boardX;
	private final int boardY;

	public CraftingStationLevelJob(int tileX, int tileY, int boardX, int boardY) {
		super(tileX, tileY);
		this.boardX = boardX;
		this.boardY = boardY;
	}

	public CraftingStationLevelJob(LoadData save) {
		super(save);
		boardX = save.getInt("boardX", Integer.MIN_VALUE);
		boardY = save.getInt("boardY", Integer.MIN_VALUE);
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

	public CraftingTaskBoardObjectEntity getBoard() {
		if (getLevel() == null || boardX == Integer.MIN_VALUE || boardY == Integer.MIN_VALUE) {
			return null;
		}
		Object object = getLevel().entityManager.getObjectEntity(boardX, boardY);
		return object instanceof CraftingTaskBoardObjectEntity
				? (CraftingTaskBoardObjectEntity)object
				: null;
	}

	@Override
	public boolean isValid() {
		if (!super.isValid()) {
			return false;
		}

		CraftingTaskBoardObjectEntity board = getBoard();
		if (board == null || board.getTaskCount() == 0) {
			return false;
		}

		java.awt.Point station = board.getLinkedStation();
		return station != null && station.x == tileX && station.y == tileY && board.getValidLinkedStationObject() != null;
	}

	@Override
	public boolean estimateCanMoveTo(EntityJobWorker worker) {
		CraftingTaskBoardObjectEntity board = getBoard();
		int uniqueID = worker.getMobWorker().getUniqueID();
		if (board == null) {
			Logging.logMessage("[CraftingJob] Worker " + uniqueID + " rejected job " + tileX + "," + tileY + ": board missing");
			return false;
		}
		if (getLevel().getWorldEntity().isNight()) {
			Logging.logMessage("[CraftingJob] Worker " + uniqueID + " rejected board " + boardX + "," + boardY + ": night");
			return false;
		}
		if (!board.canBeClaimedBy(uniqueID)) {
			Logging.logMessage("[CraftingJob] Worker " + uniqueID + " rejected board " + boardX + "," + boardY + ": claimed by another worker");
			return false;
		}
		if (!board.hasActionableTasks()) {
			Logging.logMessage("[CraftingJob] Worker " + uniqueID + " rejected board " + boardX + "," + boardY + ": no actionable tasks");
			return false;
		}
		if (!(worker.getMobWorker() instanceof HumanMob)) return false;
		DynamicCraftingStationObjectEntity station = board.getLinkedStationEntity();
		if (station == null || !station.supportsSettlerCraftingTasks()
				|| !station.canSettlerPerformCrafting((HumanMob)worker.getMobWorker())) {
			return false;
		}
		boolean canMove = worker.getMobWorker().estimateCanMoveTo(tileX, tileY, true);
		Logging.logMessage("[CraftingJob] Worker " + uniqueID + " estimate move to station " + tileX + "," + tileY + " = " + canMove);
		return canMove;
	}

	@Override
	public int getSameTypePriority(FoundJob foundJob) {
		CraftingTaskBoardObjectEntity board = getBoard();
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
		CraftingStationLevelJob job = (CraftingStationLevelJob)foundJob.job;
		CraftingTaskBoardObjectEntity board = job.getBoard();
		int uniqueID = worker.getMobWorker().getUniqueID();
		Logging.logMessage("[CraftingJob] Worker " + uniqueID + " building sequence for station " + job.tileX + "," + job.tileY
				+ " board=" + job.boardX + "," + job.boardY);
		if (board == null) {
			Logging.logMessage("[CraftingJob] Sequence rejected: board missing");
			return null;
		}

		if (!(worker.getMobWorker() instanceof HumanMob)) return null;
		HumanMob human = (HumanMob)worker.getMobWorker();
		DynamicCraftingStationObjectEntity station = board.getLinkedStationEntity();
		if (station == null || !station.supportsSettlerCraftingTasks() || !station.canSettlerPerformCrafting(human)) {
			Logging.logMessage("[CraftingJob] Sequence rejected: worker " + uniqueID + " is not valid for linked station");
			return null;
		}
		if (job.getLevel().getWorldEntity().isNight()) {
			Logging.logMessage("[CraftingJob] Sequence rejected: night");
			return null;
		}
		if (!board.hasActionableTasks()) {
			Logging.logMessage("[CraftingJob] Sequence rejected: no actionable tasks");
			return null;
		}

		if (!board.claimWorker(uniqueID)) {
			Logging.logMessage("[CraftingJob] Sequence rejected: board claim failed for worker " + uniqueID);
			return null;
		}
		Logging.logMessage("[CraftingJob] Board claimed by worker " + uniqueID);

		LinkedListJobSequence sequence = new LinkedListJobSequence(new StaticMessage(station.getSettlerCraftingActivityText()), false);
		sequence.add(new TileActiveJob(worker, foundJob.priority, job.tileX, job.tileY) {
			private int currentTaskIndex = -1;
			private long actionCompleteTime;
			private long nextSoundTime;
			private Recipe currentRecipe;
			private boolean currentRecipeRequiresForge;
			private boolean waitingForForgeHeat;

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
				if (waitingForForgeHeat) return;

				String workItemStringID = station.getSettlerCraftingWorkItemStringID();
				if (workItemStringID != null) {
					worker.showWorkAnimation(
							tileX * 32 + 16,
							tileY * 32 + 16,
							ItemRegistry.getItem(workItemStringID),
							1000,
							true
					);
				}

				long now = job.getLevel().getTime();
				if (now >= nextSoundTime) {
					nextSoundTime = now + GameRandom.globalRandom.getIntBetween(700, 1300);
					station.playSettlerCraftingWorkEffect();
				}
			}

			@Override
			public boolean isValid(boolean isCurrent) {
				CraftingTaskBoardObjectEntity currentBoard = job.getBoard();
				DynamicCraftingStationObjectEntity currentStation = currentBoard == null ? null : currentBoard.getLinkedStationEntity();
				return job.isValid()
						&& currentBoard != null
						&& currentBoard.isAssignedTo(uniqueID)
						&& currentStation != null
						&& currentStation.canSettlerPerformCrafting(human)
						&& !job.getLevel().getWorldEntity().isNight();
			}

			@Override
			public void onCancelled(boolean becauseOfInvalid, boolean isCurrent, boolean isMovingTo) {
				super.onCancelled(becauseOfInvalid, isCurrent, isMovingTo);
				Logging.logMessage("[CraftingJob] Worker " + uniqueID + " cancelled station " + tileX + "," + tileY
						+ " invalid=" + becauseOfInvalid + " current=" + isCurrent + " moving=" + isMovingTo);
				CraftingTaskBoardObjectEntity currentBoard = job.getBoard();
				if (currentBoard != null && currentBoard.isAssignedTo(uniqueID)) currentBoard.releaseWorker();
			}

			@Override
			public ActiveJobResult perform() {
				CraftingTaskBoardObjectEntity currentBoard = job.getBoard();
				if (currentBoard == null || job.getLevel().getWorldEntity().isNight()) {
					if (currentBoard != null) {
						currentBoard.releaseWorker();
					}
					return ActiveJobResult.FINISHED;
				}

				if (currentTaskIndex < 0) {
					currentTaskIndex = currentBoard.findNextActionableTask();
					if (currentTaskIndex < 0) {
						Logging.logMessage("[CraftingJob] Worker " + uniqueID + " found no actionable task; finishing");
						currentBoard.releaseWorker();
						return ActiveJobResult.FINISHED;
					}

					CraftingTask selectedTask = currentBoard.getTask(currentTaskIndex);
					CraftingTaskRecipe selectedTaskRecipe = selectedTask == null
							? null
							: CraftingTaskLogic.getTaskRecipe(currentBoard, selectedTask);
					if (selectedTaskRecipe == null) {
						currentBoard.refreshTaskStatusesNow();
						currentBoard.advanceAfterTask(currentTaskIndex);
						currentTaskIndex = -1;
						return ActiveJobResult.PERFORMING;
					}

					if (selectedTaskRecipe.isForgeRecipe()) {
						ForgeTaskSystem.QueueResult forgeResult = ForgeTaskSystem.queueOne(currentBoard, selectedTaskRecipe);
						Logging.logMessage("[CraftingForgeJob] Worker " + uniqueID + " queue item="
								+ selectedTask.itemID + " success=" + forgeResult.success
								+ (forgeResult.problem == null ? "" : " problem=" + forgeResult.problem));
						currentBoard.refreshTaskStatusesNow();
						currentBoard.advanceAfterTask(currentTaskIndex);
						currentTaskIndex = -1;
						if (!currentBoard.hasActionableTasks()) {
							currentBoard.releaseWorker();
							return ActiveJobResult.FINISHED;
						}
						return ActiveJobResult.PERFORMING;
					}

					Recipe selectedRecipe = selectedTaskRecipe.recipe;
					int actionDelay = station.getSettlerCraftingActionDelay(human, selectedRecipe);
					currentRecipe = selectedRecipe;
					currentRecipeRequiresForge = ForgeRequirementSystem.requiresRunningForge(selectedRecipe);
					waitingForForgeHeat = false;

					if (currentRecipeRequiresForge) {
						CraftingStoragePool storagePool = CraftingTaskLogic.getStoragePool(currentBoard);
						if (!ForgeRequirementSystem.ensureRunningForge(station, selectedRecipe, storagePool, actionDelay)) {
							Logging.logMessage("[CraftingFRRJob] Worker " + uniqueID + " could not start forge heat for item="
									+ selectedTask.itemID);
							currentRecipe = null;
							currentRecipeRequiresForge = false;
							currentBoard.refreshTaskStatusesNow();
							currentBoard.advanceAfterTask(currentTaskIndex);
							currentTaskIndex = -1;
							if (!currentBoard.hasActionableTasks()) {
								currentBoard.releaseWorker();
								return ActiveJobResult.FINISHED;
							}
							return ActiveJobResult.PERFORMING;
						}
						Logging.logMessage("[CraftingFRRJob] Worker " + uniqueID + " secured forge heat for item="
								+ selectedTask.itemID + " duration=" + actionDelay + "ms");
					}

					actionCompleteTime = job.getLevel().getTime() + actionDelay;
					station.onSettlerCraftStarted(human, selectedRecipe);
					Logging.logMessage("[CraftingJob] Worker " + uniqueID + " started task index=" + currentTaskIndex
							+ " item=" + selectedTask.itemID + " delay=" + actionDelay + "ms");
					return ActiveJobResult.PERFORMING;
				}

				if (job.getLevel().getTime() < actionCompleteTime) {
					return ActiveJobResult.PERFORMING;
				}

				if (currentRecipeRequiresForge && currentRecipe != null) {
					CraftingStoragePool storagePool = CraftingTaskLogic.getStoragePool(currentBoard);
					ForgeRequirementSystem.Status forgeStatus = ForgeRequirementSystem.getStatus(station, currentRecipe, storagePool);
					if (forgeStatus == ForgeRequirementSystem.Status.NO_LINKED_FORGE) {
						CraftingTask interruptedTask = currentBoard.getTask(currentTaskIndex);
						Logging.logMessage("[CraftingFRRJob] Worker " + uniqueID + " lost linked forge for item="
								+ (interruptedTask == null ? "unknown" : interruptedTask.itemID));
						currentRecipe = null;
						currentRecipeRequiresForge = false;
						waitingForForgeHeat = false;
						currentBoard.refreshTaskStatusesNow();
						currentBoard.advanceAfterTask(currentTaskIndex);
						currentTaskIndex = -1;
						if (!currentBoard.hasActionableTasks()) {
							currentBoard.releaseWorker();
							return ActiveJobResult.FINISHED;
						}
						return ActiveJobResult.PERFORMING;
					}

					if (!ForgeRequirementSystem.ensureRunningForge(station, currentRecipe, storagePool, 250L)) {
						if (!waitingForForgeHeat) {
							Logging.logMessage("[CraftingFRRJob] Worker " + uniqueID + " waiting for forge fuel after finishing work");
						}
						waitingForForgeHeat = true;
						return ActiveJobResult.PERFORMING;
					}

					if (waitingForForgeHeat) {
						Logging.logMessage("[CraftingFRRJob] Worker " + uniqueID + " forge heat restored; completing craft");
					}
					waitingForForgeHeat = false;
				}

				currentBoard.refreshTaskStatusesNow();
				CraftingTask task = currentBoard.getTask(currentTaskIndex);
				if (task == null || task.status != CraftingTask.STATUS_IN_PROGRESS) {
					currentBoard.advanceAfterTask(currentTaskIndex);
					currentTaskIndex = -1;
					currentRecipe = null;
					currentRecipeRequiresForge = false;
					waitingForForgeHeat = false;
					return ActiveJobResult.PERFORMING;
				}

				CraftingTaskRecipe taskRecipe = CraftingTaskLogic.getTaskRecipe(currentBoard, task);
				Recipe recipe = taskRecipe == null || taskRecipe.isForgeRecipe() ? null : taskRecipe.recipe;
				if (recipe == null) {
					currentBoard.refreshTaskStatusesNow();
					currentBoard.advanceAfterTask(currentTaskIndex);
					currentTaskIndex = -1;
					currentRecipe = null;
					currentRecipeRequiresForge = false;
					waitingForForgeHeat = false;
					return ActiveJobResult.PERFORMING;
				}

				CraftingTaskLogic.CraftResult result = CraftingTaskLogic.craftOne(currentBoard, recipe);
				if (result.success) station.onSettlerCraftCompleted(human, recipe, result.resultItem == null ? null : result.resultItem.copy());
				Logging.logMessage("[CraftingJob] Worker " + uniqueID + " craftOne item=" + task.itemID
						+ " success=" + result.success + " produced=" + result.producedAmount);
				if (result.success && task.conditionType == CraftingTask.CONDITION_CRAFT_UNITS) {
					currentBoard.decrementCraftAmount(currentTaskIndex, result.producedAmount);
				} else {
					currentBoard.refreshTaskStatusesNow();
				}

				currentBoard.advanceAfterTask(currentTaskIndex);
				currentTaskIndex = -1;
				currentRecipe = null;
				currentRecipeRequiresForge = false;
				waitingForForgeHeat = false;

				if (!currentBoard.hasActionableTasks()) {
					currentBoard.releaseWorker();
					return ActiveJobResult.FINISHED;
				}
				return ActiveJobResult.PERFORMING;
			}
		});
		return sequence;
	}

	private static boolean hasAvailableCraftingWork(HumanMob workerMob) {
		if (workerMob.getLevel() == null || workerMob.getLevel().getWorldEntity().isNight()) return false;

		int uniqueID = workerMob.getUniqueID();
		for (Object object : workerMob.getLevel().entityManager.objectEntities) {
			if (!(object instanceof CraftingTaskBoardObjectEntity)) continue;

			CraftingTaskBoardObjectEntity board = (CraftingTaskBoardObjectEntity)object;
			DynamicCraftingStationObjectEntity station = board.getLinkedStationEntity();
			if (station == null || !station.supportsSettlerCraftingTasks() || !station.canSettlerPerformCrafting(workerMob)) continue;
			if (!board.hasActionableTasksCached() || !board.canBeClaimedBy(uniqueID)) continue;

			java.awt.Point stationPoint = board.getLinkedStation();
			if (stationPoint != null && workerMob.estimateCanMoveTo(stationPoint.x, stationPoint.y, true)) return true;
		}
		return false;
	}

	public static JobTypeHandler.SubHandler handler(EntityJobWorker worker, JobTypeHandler handler) {
		JobTypeHandler.SubHandler craftingHandler = handler.setJobHandler(
				CraftingStationLevelJob.class,
				foundJob -> getJobSequence(worker, (FoundJob)foundJob)
		);

		if (!(worker.getMobWorker() instanceof HumanMob)) {
			return craftingHandler.setPredicate(() -> false);
		}

		HumanMob human = (HumanMob)worker.getMobWorker();
		craftingHandler.setPredicate(() -> !human.isOnStrike()
				&& !human.hasCompletedMission()
				&& human.isSettlerWithinSettlement());

		// Crafting-station jobs suppress vanilla/other crafting handlers only when
		// this worker is eligible for a linked station that currently has work.
		for (Object object : new java.util.ArrayList<>(handler.getJobHandlers())) {
			JobTypeHandler.SubHandler other = (JobTypeHandler.SubHandler)object;
			if (other != craftingHandler
					&& other.priority != null
					&& "crafting".equals(other.priority.type.getStringID())) {
				other.addPredicate(() -> !hasAvailableCraftingWork(human));
			}
		}

		return craftingHandler;
	}
}
