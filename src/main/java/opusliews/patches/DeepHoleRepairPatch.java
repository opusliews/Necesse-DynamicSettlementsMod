//// UNCOMMENT TO FIX HOLE CORRUPTION
//package opusliews.patches;
//
//import necesse.engine.modLoader.annotations.ModMethodPatch;
//import necesse.engine.registries.TileRegistry;
//import necesse.level.maps.Level;
//import necesse.level.maps.regionSystem.Region;
//import net.bytebuddy.asm.Advice;
//import opusliews.tile.DeepHoleTile;
//import opusliews.tile.ShallowHoleTile;
//
//@ModMethodPatch(target = Level.class, name = "serverTick", arguments = {})
//public class DeepHoleRepairPatch {
//
//	public static boolean hasRun = false;
//
//	@Advice.OnMethodEnter
//	static void onEnter(@Advice.This Level level) {
//		if (!level.isServer() || hasRun) {
//			return;
//		}
//
//		hasRun = true;
//
//		int deepHoleID = TileRegistry.getTileID(DeepHoleTile.stringID);
//		int shallowHoleID = TileRegistry.getTileID(ShallowHoleTile.stringID);
//
//		int repaired = 0;
//
//		for (Region region : level.regionManager.collectLoadedRegions()) {
//			int startX = level.regionManager.getTileCoordByRegion(region.regionX);
//			int startY = level.regionManager.getTileCoordByRegion(region.regionY);
//
//			int width = level.regionManager.getRegionTileWidth(region.regionX);
//			int height = level.regionManager.getRegionTileHeight(region.regionY);
//
//			for (int x = startX; x < startX + width; x++) {
//				for (int y = startY; y < startY + height; y++) {
//					if (level.getTileID(x, y) == deepHoleID) {
//						level.setTile(x, y, shallowHoleID);
//						level.sendTileUpdatePacket(x, y);
//						repaired++;
//					}
//				}
//			}
//		}
//
//		System.out.println("Deep hole repair: " + repaired + " converted");
//	}
//}