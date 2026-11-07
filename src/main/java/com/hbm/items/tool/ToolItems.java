package com.hbm.items.tool;

import static com.hbm.inventory.OreDictManager.*;

import java.util.ArrayList;
import java.util.List;

import com.hbm.inventory.material.MaterialShapes;
import com.hbm.items.ModItems;
import com.hbm.lib.RefStrings;
import com.hbm.main.CraftingManager;

import api.hbm.block.IToolable.ToolType;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Every crafting tool is one item, the material is its item damage. Recipes are taken from GTNH */
public class ToolItems {

	private static final List<Item> TOOLS = new ArrayList<Item>();

	private static final List<ToolMat> HARD = new ArrayList<ToolMat>();
	private static final ToolMat[] SOFT = {ToolMat.WOOD, ToolMat.RUBBER, ToolMat.DESH};
	private static final ToolMat[] MORTAR = {ToolMat.FLINT, ToolMat.IRON, ToolMat.STEEL};

	public static void init() {

		ModItems.screwdriver = add(new ItemTooling(ToolType.SCREWDRIVER, "screwdriver", true));
		ModItems.hand_drill = add(new ItemTooling(ToolType.HAND_DRILL, "hand_drill", true));
		ModItems.mallet = add(new ItemTooling(ToolType.MALLET, "mallet", true));
		ModItems.saw = add(new ItemTooling(ToolType.SAW, "saw", true));
		ModItems.hammer = add(new ItemTooling(ToolType.HAMMER, "hammer", true));
		ModItems.file = add(new ItemTooling(ToolType.FILE, "file", true));
		ModItems.c_wrench = add(new ItemToolingWrench(ToolType.WRENCH, "c_wrench"));
		ModItems.wire_cutter = add(new ItemTooling(ToolType.WIRE_CUTTER, "wire_cutter", true));
		ModItems.mortar = add(new ItemTooling(ToolType.MORTAR, "mortar", true));
		//ItemMultitool reads the fields above when it is first loaded
		ModItems.pocket_multitool = add(new ItemMultitool());

		for(ToolMat mat : ToolMat.HARD) HARD.add(mat);
		for(ToolMat mat : ToolMat.MODDED) if(mat.enabled()) HARD.add(mat);
	}

	private static Item add(Item tool) {
		TOOLS.add(tool);
		return tool;
	}

	public static void register() {
		for(Item tool : TOOLS) GameRegistry.registerItem(tool, tool.getUnlocalizedName());
	}

	public static void registerOreDict() {
		for(Item tool : TOOLS) {
			if(tool instanceof ItemMultitool) {
				for(String key : new String[] {KEY_TOOL_SCREWDRIVER, KEY_TOOL_HANDDRILL, KEY_TOOL_SAW, KEY_TOOL_HAMMER, KEY_TOOL_FILE, KEY_TOOL_WIRE_CUTTER, KEY_TOOL_C_WRENCH}) {
					OreDictionary.registerOre(key, new ItemStack(tool, 1, OreDictionary.WILDCARD_VALUE));
				}
			} else {
				OreDictionary.registerOre(oreKey(((ItemTooling) tool).getToolType()), new ItemStack(tool, 1, OreDictionary.WILDCARD_VALUE));
			}
		}
	}

	private static String oreKey(ToolType type) {
		switch(type) {
		case SCREWDRIVER: return KEY_TOOL_SCREWDRIVER;
		case HAND_DRILL: return KEY_TOOL_HANDDRILL;
		case MALLET: return KEY_TOOL_MALLET;
		case SAW: return KEY_TOOL_SAW;
		case HAMMER: return KEY_TOOL_HAMMER;
		case FILE: return KEY_TOOL_FILE;
		case WIRE_CUTTER: return KEY_TOOL_WIRE_CUTTER;
		case MORTAR: return KEY_TOOL_MORTAR;
		default: return KEY_TOOL_C_WRENCH;
		}
	}

	private static ItemStack stack(Item tool, ToolMat mat) {
		return new ItemStack(tool, 1, mat.id);
	}

	private static String shape(MaterialShapes shape, ToolMat mat) {
		return mat.mod != null ? mat.frame.ingot() : shape.name() + mat.frame.mats[0];
	}
	
	private static Object plate(ToolMat mat) {
		if(mat.mod != null) return mat.frame.ingot();
		if(mat == ToolMat.DESH) return new ItemStack(ModItems.plate_c_desh);
		Item compound = GameRegistry.findItem(RefStrings.MODID, "item.plate_c_" + mat.name);
		return compound != null ? new ItemStack(compound) : mat.frame.plate();
	}

	public static void registerRecipes() {

		for(ToolMat mat : HARD) {
			String ingot = mat.frame.ingot();
			Object plate = plate(mat);
			String rod = shape(MaterialShapes.C_ROD, mat);
			String screw = shape(MaterialShapes.SCREW, mat);
			Object handle = mat.handle;

			CraftingManager.addRecipeAuto(stack(ModItems.hammer, mat), new Object[] { "XX ", "XXS", "XX ", 'X', ingot, 'S', handle });
			CraftingManager.addRecipeAuto(stack(ModItems.file, mat), new Object[] { "P", "P", "S", 'P', plate, 'S', handle });
			//the saw head recipe and the head + handle recipe are combined
			CraftingManager.addRecipeAuto(stack(ModItems.saw, mat), new Object[] { "PP ", "fhS", 'P', plate, 'S', handle, 'f', KEY_TOOL_FILE, 'h', KEY_TOOL_HAMMER });
			CraftingManager.addRecipeAuto(stack(ModItems.c_wrench, mat), new Object[] { "IhI", "III", " I ", 'I', ingot, 'h', KEY_TOOL_HAMMER });
			CraftingManager.addRecipeAuto(stack(ModItems.screwdriver, mat), new Object[] { " fS", " Sh", "W  ", 'S', rod, 'W', handle, 'f', KEY_TOOL_FILE, 'h', KEY_TOOL_HAMMER });
			CraftingManager.addRecipeAuto(stack(ModItems.wire_cutter, mat), new Object[] { "PfP", "hPd", "STS", 'S', rod, 'P', plate, 'T', screw, 'f', KEY_TOOL_FILE, 'h', KEY_TOOL_HAMMER, 'd', KEY_TOOL_SCREWDRIVER });
			CraftingManager.addRecipeAuto(stack(ModItems.hand_drill, mat), new Object[] { " X", "S ", " S", 'X', ingot, 'S', handle });
			//the saw and file heads are replaced by plates
			CraftingManager.addRecipeAuto(stack(ModItems.pocket_multitool, mat), new Object[] { "ABC", "DEF", "CFG", 'A', shape(MaterialShapes.LONG_ROD, mat), 'B', plate, 'C', shape(MaterialShapes.RING, mat), 'D', plate, 'E', plate, 'F', shape(MaterialShapes.SPRING, mat), 'G', KEY_BLUE });
		}

		for(ToolMat mat : SOFT) {
			Object head = mat == ToolMat.WOOD ? KEY_PLANKS : mat == ToolMat.RUBBER ? ANY_RUBBER.ingot() : mat.frame.ingot();
			CraftingManager.addRecipeAuto(stack(ModItems.mallet, mat), new Object[] { "XX ", "XXS", "XX ", 'X', head, 'S', mat.handle != null ? mat.handle : KEY_STICK });
		}

		for(ToolMat mat : MORTAR) {
			Object head = mat == ToolMat.FLINT ? Items.flint : mat.frame.ingot();
			CraftingManager.addRecipeAuto(stack(ModItems.mortar, mat), new Object[] { " I ", "SIS", "SSS", 'I', head, 'S', "stone" });
		}
	}
}
