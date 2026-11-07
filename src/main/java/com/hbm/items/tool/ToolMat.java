package com.hbm.items.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hbm.inventory.OreDictManager;
import com.hbm.inventory.OreDictManager.DictFrame;
import com.hbm.util.Compat;

import api.hbm.block.IToolable.ToolType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

public class ToolMat {

	public static final List<ToolMat> ALL = new ArrayList<ToolMat>();
	private static final ToolMat[] BY_ID = new ToolMat[32];

	private static final String MOD_AVARITIA = "Avaritia";
	private static final String MOD_DRACONIC = "DraconicEvolution";

	public static final ToolMat NULL =		new ToolMat(0, "null",			0xFFFFFF, 0,	null,						null,	null);
	public static final ToolMat WOOD =		new ToolMat(1, "wood",			0x643200, 0,	null,						null,	null);
	public static final ToolMat RUBBER =	new ToolMat(2, "rubber",		0x2A2A2A, 0,	null,						null,	null);
	public static final ToolMat FLINT =		new ToolMat(3, "flint",		0x002040, 1,	null,						null,	null);
	public static final ToolMat IRON =		new ToolMat(4, "iron",			0xC8C8C8, 2,	OreDictManager.IRON,		OreDictManager.KEY_STICK,	null);
	public static final ToolMat STEEL =		new ToolMat(5, "steel",		0x808080, 3,	OreDictManager.STEEL,		OreDictManager.KEY_STICK,	null);
	public static final ToolMat BRONZE =	new ToolMat(6, "bronze",		0xFF8000, 2,	OreDictManager.BRONZE,		OreDictManager.KEY_STICK,	null);
	public static final ToolMat ALUMINIUM =	new ToolMat(7, "aluminium",	0x80C8F0, 2,	OreDictManager.AL,			OreDictManager.KEY_STICK,	null);
	public static final ToolMat TITANIUM =	new ToolMat(8, "titanium",		0xDCA0F0, 3,	OreDictManager.TI,			OreDictManager.KEY_STICK,	null);
	public static final ToolMat STAINLESS =	new ToolMat(9, "stainless",	0xC8C8DC, 4,	OreDictManager.STAINLESS,	OreDictManager.KEY_STICK,	null);
	public static final ToolMat LEAD =		new ToolMat(10, "lead",			0x8C648C, 1,	OreDictManager.PB,			OreDictManager.KEY_STICK,	null);
	public static final ToolMat TUNGSTEN =	new ToolMat(11, "tungsten",		0x323232, 3,	OreDictManager.W,			OreDictManager.KEY_STICK,	null);
	public static final ToolMat DESH =		new ToolMat(12, "desh",			0xFF6D6D, 2,	OreDictManager.DESH,		OreDictManager.ANY_PLASTIC.ingot(),	null);

	public static final ToolMat INFINITY =	new ToolMat(13, "infinity",	0xF0F0FF, 8,	new DictFrame("Infinity"),	OreDictManager.KEY_STICK,	MOD_AVARITIA);
	public static final ToolMat DRACONIUM =	new ToolMat(14, "draconium",	0x7B3FA0, 5,	new DictFrame("Draconium"),	OreDictManager.KEY_STICK,	MOD_DRACONIC);

	public static final ToolMat NEUTRONIUM =new ToolMat(15, "neutronium",	0x2B2B3C, 7,	null,						null,	null);

	public static final ToolMat[] HARD = {IRON, STEEL, BRONZE, ALUMINIUM, TITANIUM, STAINLESS, LEAD, TUNGSTEN, DESH};
	public static final ToolMat[] MODDED = {INFINITY, DRACONIUM};

	public static final ToolMat LISTED = NEUTRONIUM;

	public static final String MULTITOOL = "pocket_multitool";
	public static final int DAMAGE_UNIT = 50;
	public static final int CRAFT_WEAR_MULTITOOL = 16;

	static {
		NULL.row("screwdriver", 3200, 1.5F);
		NULL.row("hand_drill", 3200, 1.5F);
		NULL.row("saw", 3200, 1.75F);
		NULL.row("hammer", 3200, 3F);
		NULL.row("file", 3200, 1.5F);
		NULL.row("c_wrench", 3200, 3F);
		NULL.row("wire_cutter", 3200, 1.25F);
		NULL.row(MULTITOOL, 12800, 0F);
		NULL.row("mallet", 3200, 3F);
		NULL.row("mortar", 3200, 2F);

		WOOD.row("mallet", 12800, 3F);

		RUBBER.row("mallet", 25600, 3F);

		FLINT.row("mortar", 12800, 3F);

		IRON.row("screwdriver", 25600, 3.5F);
		IRON.row("hand_drill", 25600, 3.5F);
		IRON.row("saw", 25600, 3.75F);
		IRON.row("hammer", 25600, 5F);
		IRON.row("file", 25600, 3.5F);
		IRON.row("c_wrench", 25600, 5F);
		IRON.row("wire_cutter", 25600, 3.25F);
		IRON.row(MULTITOOL, 102400, 2F);
		IRON.row("mortar", 25600, 4F);

		STEEL.row("screwdriver", 51200, 4.5F);
		STEEL.row("hand_drill", 51200, 4.5F);
		STEEL.row("saw", 51200, 4.75F);
		STEEL.row("hammer", 51200, 6F);
		STEEL.row("file", 51200, 4.5F);
		STEEL.row("c_wrench", 51200, 6F);
		STEEL.row("wire_cutter", 51200, 4.25F);
		STEEL.row(MULTITOOL, 204800, 3F);
		STEEL.row("mortar", 51200, 5F);

		BRONZE.row("screwdriver", 19200, 3.5F);
		BRONZE.row("hand_drill", 19200, 3.5F);
		BRONZE.row("saw", 19200, 3.75F);
		BRONZE.row("hammer", 19200, 5F);
		BRONZE.row("file", 19200, 3.5F);
		BRONZE.row("c_wrench", 19200, 5F);
		BRONZE.row("wire_cutter", 19200, 3.25F);
		BRONZE.row(MULTITOOL, 76800, 2F);

		ALUMINIUM.row("screwdriver", 12800, 3.5F);
		ALUMINIUM.row("hand_drill", 12800, 3.5F);
		ALUMINIUM.row("saw", 12800, 3.75F);
		ALUMINIUM.row("hammer", 12800, 5F);
		ALUMINIUM.row("file", 12800, 3.5F);
		ALUMINIUM.row("c_wrench", 12800, 5F);
		ALUMINIUM.row("wire_cutter", 12800, 3.25F);
		ALUMINIUM.row(MULTITOOL, 51200, 2F);

		TITANIUM.row("screwdriver", 160000, 4.5F);
		TITANIUM.row("hand_drill", 160000, 4.5F);
		TITANIUM.row("saw", 160000, 4.75F);
		TITANIUM.row("hammer", 160000, 6F);
		TITANIUM.row("file", 160000, 4.5F);
		TITANIUM.row("c_wrench", 160000, 6F);
		TITANIUM.row("wire_cutter", 160000, 4.25F);
		TITANIUM.row(MULTITOOL, 640000, 3F);

		STAINLESS.row("screwdriver", 48000, 5.5F);
		STAINLESS.row("hand_drill", 48000, 5.5F);
		STAINLESS.row("saw", 48000, 5.75F);
		STAINLESS.row("hammer", 48000, 7F);
		STAINLESS.row("file", 48000, 5.5F);
		STAINLESS.row("c_wrench", 48000, 7F);
		STAINLESS.row("wire_cutter", 48000, 5.25F);
		STAINLESS.row(MULTITOOL, 192000, 4F);

		LEAD.row("screwdriver", 6400, 2.5F);
		LEAD.row("hand_drill", 6400, 2.5F);
		LEAD.row("saw", 6400, 2.75F);
		LEAD.row("hammer", 6400, 4F);
		LEAD.row("file", 6400, 2.5F);
		LEAD.row("c_wrench", 6400, 4F);
		LEAD.row("wire_cutter", 6400, 2.25F);
		LEAD.row(MULTITOOL, 25600, 1F);

		TUNGSTEN.row("screwdriver", 256000, 4.5F);
		TUNGSTEN.row("hand_drill", 256000, 4.5F);
		TUNGSTEN.row("saw", 256000, 4.75F);
		TUNGSTEN.row("hammer", 256000, 6F);
		TUNGSTEN.row("file", 256000, 4.5F);
		TUNGSTEN.row("c_wrench", 256000, 6F);
		TUNGSTEN.row("wire_cutter", 256000, 4.25F);
		TUNGSTEN.row(MULTITOOL, 1024000, 3F);

		DESH.row("screwdriver", 0, 3.5F);
		DESH.row("hand_drill", 0, 3.5F);
		DESH.row("saw", 0, 3.75F);
		DESH.row("hammer", 0, 5F);
		DESH.row("file", 0, 3.5F);
		DESH.row("c_wrench", 0, 5F);
		DESH.row("wire_cutter", 0, 3.25F);
		DESH.row(MULTITOOL, 0, 2F);
		DESH.row("mallet", 0, 5F);

		INFINITY.row("screwdriver", 0, 9.5F);
		INFINITY.row("hand_drill", 0, 9.5F);
		INFINITY.row("saw", 0, 9.75F);
		INFINITY.row("hammer", 0, 11F);
		INFINITY.row("file", 0, 9.5F);
		INFINITY.row("c_wrench", 0, 11F);
		INFINITY.row("wire_cutter", 0, 9.25F);
		INFINITY.row(MULTITOOL, 0, 8F);

		DRACONIUM.row("screwdriver", 409600, 6.5F);
		DRACONIUM.row("hand_drill", 409600, 6.5F);
		DRACONIUM.row("saw", 409600, 6.75F);
		DRACONIUM.row("hammer", 409600, 8F);
		DRACONIUM.row("file", 409600, 6.5F);
		DRACONIUM.row("c_wrench", 409600, 8F);
		DRACONIUM.row("wire_cutter", 409600, 6.25F);
		DRACONIUM.row(MULTITOOL, 1638400, 5F);

		NEUTRONIUM.row("screwdriver", 0, 8.5F);
		NEUTRONIUM.row("hand_drill", 0, 8.5F);
		NEUTRONIUM.row("saw", 0, 8.75F);
		NEUTRONIUM.row("hammer", 0, 10F);
		NEUTRONIUM.row("file", 0, 8.5F);
		NEUTRONIUM.row("c_wrench", 0, 10F);
		NEUTRONIUM.row("wire_cutter", 0, 8.25F);
		NEUTRONIUM.row(MULTITOOL, 0, 7F);
		NEUTRONIUM.row("mallet", 0, 10F);
		NEUTRONIUM.row("mortar", 0, 9F);
	}

	public final int id;
	public final String name;
	public final int color;

	public final int level;
	public final DictFrame frame;
	public final Object handle;
	public final String mod;
	private final Map<String, Stats> tools = new HashMap<String, Stats>();

	private ToolMat(int id, String name, int color, int level, DictFrame frame, Object handle, String mod) {
		this.id = id;
		this.name = name;
		this.color = color;
		this.level = level;
		this.frame = frame;
		this.handle = handle;
		this.mod = mod;
		ALL.add(this);
		BY_ID[id] = this;
	}

	public static ToolMat of(String tool, int meta) {
		ToolMat mat = meta >= 0 && meta < BY_ID.length ? BY_ID[meta] : null;
		return mat != null && mat.has(tool) ? mat : NULL;
	}

	public boolean has(String tool) {
		return tools.containsKey(tool);
	}
	
	public boolean enabled() {
		return mod == null || Compat.isModLoaded(mod);
	}

	public boolean exists(String tool) {
		return this != NULL && enabled() && has(tool);
	}

	private void row(String tool, int durability, float damage) {
		tools.put(tool, new Stats(durability, damage));
	}

	private Stats stats(String tool) {
		Stats stats = tools.get(tool);
		if(stats == null) throw new IllegalStateException(name + " has no " + tool + " in the tool table");
		return stats;
	}

	public int maxDamage(String tool) {
		return stats(tool).durability / DAMAGE_UNIT;
	}

	public float damage(String tool) {
		return stats(tool).damage;
	}
	
	public static int useCost(ToolType type) {
		switch(type) {
		case SCREWDRIVER:
		case HAND_DRILL: return 4;
		case WIRE_CUTTER: return 2;
		default: return 1;
		}
	}
	
	public static int craftWear(ToolType type) {
		switch(type) {
		case SAW: return 4;
		case SCREWDRIVER:
		case HAND_DRILL:
		case HAMMER:
		case FILE:
		case WIRE_CUTTER:
		case MORTAR: return 8;
		default: return 16;
		}
	}

	public void addTooltip(ItemStack stack, List list, String tool) {
		int max = maxDamage(tool);
		if(max > 0) list.add(StatCollector.translateToLocalFormatted("tooltip.tool.durability", (max - ItemCraftingDegradation.wear(stack)) * DAMAGE_UNIT, max * DAMAGE_UNIT));
		list.add(StatCollector.translateToLocalFormatted("tooltip.tool.level", StatCollector.translateToLocal("toolmat." + name), level));
		float damage = damage(tool);
		list.add(StatCollector.translateToLocalFormatted("tooltip.tool.damage", damage == (int) damage ? String.valueOf((int) damage) : String.valueOf(damage)));
	}

	private static class Stats {
		final int durability;
		final float damage;

		Stats(int durability, float damage) {
			this.durability = durability;
			this.damage = damage;
		}
	}
}
