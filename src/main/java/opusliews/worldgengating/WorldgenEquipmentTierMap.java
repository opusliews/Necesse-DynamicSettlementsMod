package opusliews.worldgengating;

import java.util.HashMap;
import java.util.Map;

public final class WorldgenEquipmentTierMap {
	private static final Map<String, WorldgenLootTier> tiers = new HashMap<>();

	static {
		put(WorldgenLootTier.DEMONIC,
				"arachnidchestplate", "arachnidhelmet", "arachnidlegs", "bashybush", "batcage", "bloodbolt",
				"bloodplateboots", "bloodplatechestplate", "bloodplatecowl", "bloodvolley", "boxingglovegun", "brainonastick",
				"clothboots", "clothhat", "clothrobe", "copperaxe", "copperboots", "copperbow",
				"copperchestplate", "copperhelmet", "copperpickaxe", "copperpitchfork", "coppershovel", "copperspear",
				"coppersword", "demonicaxe", "demonicboots", "demonicbow", "demonicchestplate", "demonichelmet",
				"demonicpickaxe", "demonicshovel", "demonicspear", "demonicsword", "frenchhorn", "frostaxe",
				"frostboomerang", "frostboots", "frostbow", "frostchestplate", "frostglaive", "frostgreatsword",
				"frosthat", "frosthelmet", "frosthood", "frostpickaxe", "frostpiercer", "frostshovel",
				"frostspear", "froststaff", "frostsword", "goldaxe", "goldboots", "goldbow",
				"goldchestplate", "goldcrown", "goldglaive", "goldgreatbow", "goldhelmet", "goldpickaxe",
				"goldshovel", "goldspear", "goldsword", "handgun", "heavyhammer", "hivescepter",
				"hook", "ironaxe", "ironboots", "ironbow", "ironchestplate", "irongreatsword",
				"ironhelmet", "ironpickaxe", "ironshovel", "ironspear", "ironsword", "katana",
				"leatherboots", "leatherhood", "leathershirt", "lightninghammer", "machinegun", "magicbranch",
				"mousebeam", "mousetest", "ninjastar", "nunchucks", "rollingpin", "sapphirerevolver",
				"sapphirestaff", "sharkarmorboots", "sharkarmorchestplate", "sharkarmorhelmet", "sickle", "snowlauncher",
				"soldierboots", "soldiercap", "soldierchestplate", "soldierhelmet", "sparkler", "spiderboomerang",
				"spiderboots", "spiderchestplate", "spiderclaw", "spiderhelmet", "spiderstaff", "sprinkler",
				"stabbybush", "survivorwhip", "syndicatebat", "thiefsboots", "thiefscloak", "thiefscowl",
				"venomstaff", "voidboomerang", "voidboots", "voidgreatbow", "voidhat", "voidmask",
				"voidrobe", "voidspear", "webbedgun", "woodaxe", "woodboomerang", "woodbow",
				"woodpickaxe", "woodshovel", "woodspear", "woodstaff", "woodsword"
		);

		put(WorldgenLootTier.RUNIC,
				"brutesbattleaxe", "captorsshortbow", "farmingscythe", "ivyaxe", "ivypickaxe", "ivyshovel",
				"quartzpickaxe", "runeboundscepter", "runicaxe", "runicboots", "runicchestplate", "runiccrown",
				"runichat", "runichelmet", "runichood", "runicpickaxe", "runicshovel", "sentientsword",
				"shotgun", "tungstenpickaxe", "voidmissile", "voidstaff"
		);

		put(WorldgenLootTier.IVY,
				"boulderstaff", "dredgingstaff", "dryadpickaxe", "glacialpickaxe", "ivyboots", "ivybow",
				"ivychestplate", "ivycirclet", "ivygreatbow", "ivygreatsword", "ivyhat", "ivyhelmet",
				"ivyhood", "ivyspear", "ivysword", "necroticbow", "necroticflask", "necroticgreatsword",
				"quartzaxe", "quartzshovel", "razorbladeboomerang", "sixshooter", "slimecanister", "swamptome",
				"witchhat", "witchrobe", "witchshoes"
		);

		put(WorldgenLootTier.QUARTZ,
				"amethyststaff", "amethystsword", "ancientfossilpickaxe", "cutlass", "dryadaxe", "dryadshovel",
				"flintlock", "genielamp", "glacialaxe", "glacialshovel", "gunslingerboots", "gunslingerhat",
				"gunslingervest", "handcannon", "icepickaxe", "multitool", "myceliumpickaxe", "pharaohsheaddress",
				"pharaohsrobe", "pharaohssandals", "piratehook", "quartzboots", "quartzchestplate", "quartzcrown",
				"quartzglaive", "quartzgreatsword", "quartzhelmet", "quartzstaff", "sniperrifle", "tungstenaxe",
				"tungstenshovel", "vampiriclamp", "vulturesburst", "vulturestaff", "vulturestalon"
		);

		put(WorldgenLootTier.TUNGSTEN,
				"ancientfossilaxe", "ancientfossilshovel", "chromaticspellbook", "deathripper", "elderlywand", "galvanichammer",
				"hexedbladegreatsword", "myceliumaxe", "myceliumshovel", "reanimationbow", "reaperscall", "reaperscythe",
				"reinforcedkatana", "rubyshields", "rubystaff", "seedgun", "shadowbeam", "shadowbolt",
				"shadowboots", "shadowhat", "shadowhood", "shadowmantle", "soulseedboots", "soulseedchestplate",
				"soulseedcrown", "tungstenboomerang", "tungstenboots", "tungstenbow", "tungstenchestplate", "tungstengreatbow",
				"tungstenhelmet", "tungstenspear", "tungstensword"
		);

		put(WorldgenLootTier.GLACIAL,
				"cryoblaster", "cryoglaive", "cryoquake", "cryospear", "cryostaff", "cryowitchhat",
				"cryowitchrobe", "cryowitchshoes", "deepfrostboots", "deepfrostchestplate", "deepfrosthood", "glacialboomerang",
				"glacialboots", "glacialbow", "glacialchestplate", "glacialcirclet", "glacialgreatsword", "glacialhelmet",
				"icejavelin", "iciclestaff", "ninjahood", "ninjarobe", "ninjashoes", "phoenixboots",
				"phoenixcloak", "phoenixmask"
		);

		put(WorldgenLootTier.DRYAD,
				"barkblade", "dryadbarrage", "dryadboots", "dryadbow", "dryadbranch", "dryadchestplate",
				"dryadcrown", "dryadgreathammer", "dryadhat", "dryadhelmet", "dryadscarf", "thesoulstorm",
				"topazstaff"
		);

		put(WorldgenLootTier.MYCELIUM,
				"agedchampionchestplate", "agedchampiongreaves", "agedchampionhelmet", "agedchampionsword", "anchorandchain", "butcherscleaver",
				"chefsspecial", "druidsgreatbow", "emeraldstaff", "livingshotty", "myceliumboots", "myceliumchestplate",
				"myceliumgreatbow", "myceliumhood", "myceliumscarf", "swampdwellerstaff", "swampsgrasp", "unlabeledpotion",
				"venomshower", "venomslasher", "widowboots", "widowchestplate", "widowhelmet"
		);

		put(WorldgenLootTier.FOSSIL,
				"amethysthelmet", "ancestorsboots", "ancestorshat", "ancestorsrobe", "ancestorsword", "ancestorwand",
				"ancientdredgingstaff", "ancientfossilboots", "ancientfossilchestplate", "ancientfossilhelmet", "ancientfossilmask", "antiquebow",
				"antiquerifle", "antiquesword", "arachnidwebbow", "arcanicboots", "arcanicchestplate", "arcanichelmet",
				"ascendedbow", "ascendedstaff", "battlechefboots", "battlechefchestplate", "battlechefhat", "bloodclaw",
				"bloodgrimoire", "bowofdualism", "carapacedagger", "causticexecutioner", "chargebeam", "chargeshower",
				"crystalboots", "crystalchestplate", "crystallizedskull", "dawnboots", "dawnchestplate", "dawnhelmet",
				"dragonlance", "dragonsrebound", "duskboots", "duskchestplate", "duskhelmet", "emeraldmask",
				"emeraldwand", "empresscommand", "eyeofthevoid", "gemstonelongsword", "goldenarachnidwebbow", "goldencausticexecutioner",
				"goldenwebweaver", "grizzboltsphere", "ignitionkey", "kineticcharger", "makeshiftboots", "makeshiftchestplate",
				"makeshiftcrown", "makeshifteyepiece", "makeshiftfaceplating", "makeshifthat", "mlg1", "mlg2",
				"nightpiercer", "nightrazorboomerang", "nightsteelboots", "nightsteelchestplate", "nightsteelcirclet", "nightsteelhelmet",
				"nightsteelmask", "nightsteelveil", "orbofslimes", "patchworkgrenadier", "perfectstorm", "phantomcaller",
				"phantompopper", "pyromancy", "ravenbeakspear", "ravenlordsboots", "ravenlordschestplate", "ravenlordsheaddress",
				"ravenwinggreatsword", "refractor", "revsword", "rubycrown", "sandknife", "sapphireeyepatch",
				"shardcannon", "sharpshooterboots", "sharpshootercoat", "sharpshooterhat", "skeletonstaff", "slimeboots",
				"slimechestplate", "slimeglaive", "slimegreatbow", "slimegreatsword", "slimehat", "slimehelmet",
				"slimestaff", "spideritechestplate", "spideritecrown", "spideritegreaves", "spideritehat", "spideritehelmet",
				"spideritehood", "stopsignsword", "thecrimsonsky", "theravensnest", "voidclaw", "webweaver"
		);

	}

	private WorldgenEquipmentTierMap() {
	}

	public static WorldgenLootTier getTier(String itemStringID) {
		return itemStringID == null ? null : tiers.get(itemStringID);
	}

	private static void put(WorldgenLootTier tier, String... itemStringIDs) {
		for (String itemStringID : itemStringIDs) tiers.put(itemStringID, tier);
	}
}
