package myau.util;

import com.google.common.collect.Multimap;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ItemUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ArrayList<Integer> specialItems = new SpecialItems();

    public static boolean isNotSpecialItem(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        Item item = itemStack.getItem();
        if (item instanceof ItemBlock) {
            return !ItemUtil.isContainerBlock((ItemBlock) item);
        }
        if (item instanceof ItemPotion) {
            return ((ItemPotion) item).getEffects(itemStack).stream().map(PotionEffect::getPotionID).noneMatch(specialItems::contains);
        }
        if (item instanceof ItemEnderPearl) return false;
        if (item instanceof ItemFood) {
            if (item != Items.spider_eye) return false;
        }
        return item != Items.nether_star;
    }

    public static boolean isBlock(ItemStack itemStack) {
        if (itemStack == null || itemStack.stackSize < 1) {
            return false;
        }
        Item item = itemStack.getItem();
        if (item instanceof ItemBlock) {
            return ItemUtil.isContainerBlock((ItemBlock) item);
        }
        return false;
    }

    public static boolean isContainerBlock(ItemBlock itemBlock) {
        Block block = itemBlock.getBlock();
        if (BlockUtil.isInteractable(block)) return false;
        return BlockUtil.isSolid(block);
    }

    public static double getAttackBonus(ItemStack itemStack) {
        double attackBonus = getAttributeAttackDamage(itemStack);
        if (itemStack != null && itemStack.isItemEnchanted()) {
            attackBonus = attackBonus + (double) EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, itemStack) + (double) EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, itemStack) * 1.25;
        }
        return attackBonus;
    }

    private static double getAttributeAttackDamage(ItemStack itemStack) {
        double attackBonus = 0.0;
        if (itemStack == null) {
            return 0.0;
        }
        Multimap<String, AttributeModifier> multimap = itemStack.getAttributeModifiers();
        for (String attributeName : multimap.keySet()) {
            if (!attributeName.equals("generic.attackDamage")) {
                continue;
            }
            Iterator<AttributeModifier> iterator = multimap.get(attributeName).iterator();
            if (!iterator.hasNext()) {
                break;
            }
            attackBonus += iterator.next().getAmount();
            break;
        }
        return attackBonus;
    }

    public static boolean isBestSlotWeapon(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        Item item = itemStack.getItem();
        return item == Items.diamond_sword
                || item == Items.iron_sword
                || item == Items.stone_sword
                || item == Items.wooden_sword
                || item == Items.golden_sword
                || item == Items.diamond_axe
                || item == Items.iron_axe
                || item == Items.stone_axe
                || item == Items.wooden_axe
                || item == Items.golden_axe
                || item == Items.diamond_pickaxe
                || item == Items.iron_pickaxe
                || item == Items.stone_pickaxe
                || item == Items.wooden_pickaxe
                || item == Items.golden_pickaxe
                || item == Items.diamond_shovel
                || item == Items.iron_shovel
                || item == Items.stone_shovel
                || item == Items.wooden_shovel
                || item == Items.golden_shovel;
    }

    private static double getBestSlotDamage(ItemStack itemStack) {
        if (itemStack == null) {
            return 0.0;
        }
        return getAttributeAttackDamage(itemStack) + (double) EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, itemStack) * 1.25;
    }

    private static int compareBestSlotWeapons(ItemStack left, ItemStack right) {
        double leftDamage = getBestSlotDamage(left);
        double rightDamage = getBestSlotDamage(right);
        if (leftDamage > rightDamage + 1.0E-9) {
            return 1;
        }
        if (rightDamage > leftDamage + 1.0E-9) {
            return -1;
        }
        int fire = Integer.compare(enchantLevel(left, Enchantment.fireAspect), enchantLevel(right, Enchantment.fireAspect));
        if (fire != 0) {
            return fire;
        }
        int knockback = Integer.compare(enchantLevel(left, Enchantment.knockback), enchantLevel(right, Enchantment.knockback));
        if (knockback != 0) {
            return knockback;
        }
        int thorns = Integer.compare(enchantLevel(left, Enchantment.thorns), enchantLevel(right, Enchantment.thorns));
        if (thorns != 0) {
            return thorns;
        }
        int smite = Integer.compare(enchantLevel(left, Enchantment.smite), enchantLevel(right, Enchantment.smite));
        if (smite != 0) {
            return smite;
        }
        int bane = Integer.compare(enchantLevel(left, Enchantment.baneOfArthropods), enchantLevel(right, Enchantment.baneOfArthropods));
        if (bane != 0) {
            return bane;
        }
        return Integer.compare(bestSlotKindRank(left.getItem()), bestSlotKindRank(right.getItem()));
    }

    private static int bestSlotKindRank(Item item) {
        if (item instanceof ItemSword) {
            return 1;
        }
        return 0;
    }

    public static int findBestSlotWeapon(int startSlot) {
        int bestSlot = -1;
        ItemStack bestStack = null;
        for (int i = 0; i < 36; ++i) {
            int currentSlot = ((startSlot + i) % 36 + 36) % 36;
            ItemStack itemStack = ItemUtil.mc.thePlayer.inventory.getStackInSlot(currentSlot);
            if (!isBestSlotWeapon(itemStack)) {
                continue;
            }
            if (bestStack == null || compareBestSlotWeapons(itemStack, bestStack) > 0) {
                bestSlot = currentSlot;
                bestStack = itemStack;
            }
        }
        return bestSlot;
    }

    public static float getToolEfficiency(ItemStack itemStack) {
        float efficiency = 1.0f;
        if (itemStack != null) {
            if (itemStack.getItem() instanceof ItemTool) {
                int enchantLevel;
                efficiency = ((ItemTool) itemStack.getItem()).getToolMaterial().getEfficiencyOnProperMaterial();
                if (efficiency > 1.0f && (enchantLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, itemStack)) > 0) {
                    efficiency += (float) (enchantLevel * enchantLevel + 1);
                }
            }
        }
        return efficiency;
    }

    private static int getArmorValue(ItemStack itemStack) {
        if (itemStack != null && itemStack.getItem() instanceof ItemArmor) {
            return ((ItemArmor) itemStack.getItem()).damageReduceAmount;
        }
        return 0;
    }

    private static boolean isArmorForType(ItemStack itemStack, int type) {
        if (itemStack == null) {
            return false;
        }
        Item item = itemStack.getItem();
        if (item instanceof ItemArmor) {
            return ((ItemArmor) item).armorType == type;
        }
        return type == 0 && item instanceof ItemSkull;
    }

    public static boolean canShiftEquipArmor(ItemStack itemStack) {
        return itemStack != null && itemStack.getItem() instanceof ItemArmor;
    }

    private static ItemStack getArmorStack(int slot) {
        if (slot < 0) {
            return null;
        }
        return ItemUtil.mc.thePlayer.inventory.getStackInSlot(slot);
    }

    private static int getProtectionEpf(ItemStack itemStack) {
        if (itemStack == null) {
            return 0;
        }
        int level = EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, itemStack);
        if (level <= 0) {
            return 0;
        }
        return (int) Math.floor((6.0F + (float) (level * level)) / 3.0F * 0.75F);
    }

    private static double expectedDamageFraction(int armor, int epfRaw) {
        if (armor > 25) {
            armor = 25;
        }
        double afterArmor = (25.0 - (double) armor) / 25.0;
        int raw = epfRaw;
        if (raw <= 0) {
            return afterArmor;
        }
        if (raw > 25) {
            raw = 25;
        }
        int minRoll = raw + 1 >> 1;
        int span = raw >> 1;
        double sum = 0.0;
        int n = span + 1;
        for (int i = 0; i < n; i++) {
            int k = minRoll + i;
            if (k > 20) {
                k = 20;
            }
            sum += (25.0 - (double) k) / 25.0;
        }
        return afterArmor * (sum / (double) n);
    }

    public static int[] findBestArmorSet() {
        List<List<Integer>> slotChoices = new ArrayList<List<Integer>>();
        for (int type = 0; type < 4; type++) {
            slotChoices.add(paretoArmorSlots(type));
        }
        int[] locked = new int[]{-1, -1, -1, -1};
        boolean[] open = new boolean[4];
        int openCount = 0;
        for (int type = 0; type < 4; type++) {
            List<Integer> choices = slotChoices.get(type);
            if (choices.size() <= 1) {
                locked[type] = choices.get(0);
            } else {
                open[type] = true;
                openCount++;
            }
        }
        if (openCount == 0) {
            return locked;
        }
        int[] best = locked.clone();
        double bestRemain = Double.POSITIVE_INFINITY;
        int bestAlreadyOn = -1;
        searchArmorSets(slotChoices, locked, open, 0, best, new double[]{bestRemain}, new int[]{bestAlreadyOn, -1, -1, -1, -1, -1, -1});
        return best;
    }

    private static List<Integer> paretoArmorSlots(int type) {
        List<Integer> pool = new ArrayList<Integer>();
        for (int slot = 0; slot < 40; slot++) {
            ItemStack stack = getArmorStack(slot);
            if (!isArmorForType(stack, type)) {
                continue;
            }
            pool.add(slot);
        }
        if (pool.isEmpty()) {
            List<Integer> empty = new ArrayList<Integer>();
            empty.add(-1);
            return empty;
        }
        List<Integer> kept = new ArrayList<Integer>();
        for (int slot : pool) {
            if (isArmorSlotDominated(slot, type, pool)) {
                continue;
            }
            kept.add(slot);
        }
        return kept;
    }

    private static boolean isArmorSlotDominated(int slot, int type, List<Integer> pool) {
        ItemStack stack = getArmorStack(slot);
        int armor = getArmorValue(stack);
        int epf = getProtectionEpf(stack);
        boolean equipped = slot == 39 - type;
        for (int other : pool) {
            if (other == slot) {
                continue;
            }
            ItemStack otherStack = getArmorStack(other);
            int otherArmor = getArmorValue(otherStack);
            int otherEpf = getProtectionEpf(otherStack);
            if (otherArmor > armor && otherEpf >= epf || otherArmor >= armor && otherEpf > epf) {
                return true;
            }
            if (otherArmor == armor && otherEpf == epf) {
                int special = compareSpecialEnchants(otherStack, stack);
                if (special > 0) {
                    return true;
                }
                if (special < 0) {
                    continue;
                }
                boolean otherEquipped = other == 39 - type;
                if (otherEquipped && !equipped) {
                    return true;
                }
                if (otherEquipped == equipped && other < slot) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void searchArmorSets(List<List<Integer>> slotChoices, int[] current, boolean[] open, int type, int[] best, double[] bestRemain, int[] bestMeta) {
        if (type >= 4) {
            int armor = 0;
            int epf = 0;
            int alreadyOn = 0;
            int thorns = 0;
            int projectile = 0;
            int feather = 0;
            int fire = 0;
            int depth = 0;
            int blast = 0;
            for (int i = 0; i < 4; i++) {
                int slot = current[i];
                if (slot == 39 - i) {
                    alreadyOn++;
                }
                if (slot == -1) {
                    continue;
                }
                ItemStack stack = getArmorStack(slot);
                armor += getArmorValue(stack);
                epf += getProtectionEpf(stack);
                thorns += enchantLevel(stack, Enchantment.thorns);
                projectile += enchantLevel(stack, Enchantment.projectileProtection);
                feather += enchantLevel(stack, Enchantment.featherFalling);
                fire += enchantLevel(stack, Enchantment.fireProtection);
                depth += enchantLevel(stack, Enchantment.depthStrider);
                blast += enchantLevel(stack, Enchantment.blastProtection);
            }
            double remain = expectedDamageFraction(armor, epf);
            boolean better = remain < bestRemain[0] - 1.0E-9;
            if (!better && Math.abs(remain - bestRemain[0]) < 1.0E-9) {
                int special = compareSpecialLevels(thorns, projectile, feather, fire, depth, blast, bestMeta[1], bestMeta[2], bestMeta[3], bestMeta[4], bestMeta[5], bestMeta[6]);
                if (special > 0) {
                    better = true;
                } else if (special == 0 && alreadyOn > bestMeta[0]) {
                    better = true;
                }
            }
            if (better) {
                bestRemain[0] = remain;
                bestMeta[0] = alreadyOn;
                bestMeta[1] = thorns;
                bestMeta[2] = projectile;
                bestMeta[3] = feather;
                bestMeta[4] = fire;
                bestMeta[5] = depth;
                bestMeta[6] = blast;
                System.arraycopy(current, 0, best, 0, 4);
            }
            return;
        }
        if (!open[type]) {
            searchArmorSets(slotChoices, current, open, type + 1, best, bestRemain, bestMeta);
            return;
        }
        for (int slot : slotChoices.get(type)) {
            current[type] = slot;
            searchArmorSets(slotChoices, current, open, type + 1, best, bestRemain, bestMeta);
        }
    }

    private static int enchantLevel(ItemStack itemStack, Enchantment enchantment) {
        if (itemStack == null) {
            return 0;
        }
        return EnchantmentHelper.getEnchantmentLevel(enchantment.effectId, itemStack);
    }

    private static int compareSpecialEnchants(ItemStack left, ItemStack right) {
        return compareSpecialLevels(
                enchantLevel(left, Enchantment.thorns),
                enchantLevel(left, Enchantment.projectileProtection),
                enchantLevel(left, Enchantment.featherFalling),
                enchantLevel(left, Enchantment.fireProtection),
                enchantLevel(left, Enchantment.depthStrider),
                enchantLevel(left, Enchantment.blastProtection),
                enchantLevel(right, Enchantment.thorns),
                enchantLevel(right, Enchantment.projectileProtection),
                enchantLevel(right, Enchantment.featherFalling),
                enchantLevel(right, Enchantment.fireProtection),
                enchantLevel(right, Enchantment.depthStrider),
                enchantLevel(right, Enchantment.blastProtection)
        );
    }

    private static int compareSpecialLevels(int thorns, int projectile, int feather, int fire, int depth, int blast, int otherThorns, int otherProjectile, int otherFeather, int otherFire, int otherDepth, int otherBlast) {
        if (thorns != otherThorns) {
            return thorns - otherThorns;
        }
        if (projectile != otherProjectile) {
            return projectile - otherProjectile;
        }
        if (feather != otherFeather) {
            return feather - otherFeather;
        }
        if (fire != otherFire) {
            return fire - otherFire;
        }
        if (depth != otherDepth) {
            return depth - otherDepth;
        }
        if (blast != otherBlast) {
            return blast - otherBlast;
        }
        return 0;
    }

    public static int findSwordInInventorySlot(int startSlot) {
        return findSwordInInventorySlot(startSlot, -1);
    }

    public static int findSwordInInventorySlot(int startSlot, int excludeSlot) {
        int bestSlot = -1;
        double bestAttackBonus = 0.0;
        for (int i = 0; i < 36; ++i) {
            int currentSlot = ((startSlot + i) % 36 + 36) % 36;
            if (currentSlot == excludeSlot) {
                continue;
            }
            ItemStack itemStack = ItemUtil.mc.thePlayer.inventory.getStackInSlot(currentSlot);
            if (itemStack == null) continue;
            if (!(itemStack.getItem() instanceof ItemSword)) continue;
            double attackBonus = ItemUtil.getAttackBonus(itemStack);
            if (!(attackBonus > bestAttackBonus)) continue;
            bestSlot = currentSlot;
            bestAttackBonus = attackBonus;
        }
        return bestSlot;
    }

    public static int findInventorySlot(String toolClass, int startSlot) {
        return findInventorySlot(toolClass, startSlot, -1);
    }

    public static int findInventorySlot(String toolClass, int startSlot, int excludeSlot) {
        int bestSlot = -1;
        float bestEfficiency = 1.0f;
        for (int i = 0; i < 36; ++i) {
            int currentSlot = ((startSlot + i) % 36 + 36) % 36;
            if (currentSlot == excludeSlot) {
                continue;
            }
            ItemStack itemStack = ItemUtil.mc.thePlayer.inventory.getStackInSlot(currentSlot);
            if (itemStack == null) continue;
            if (!(itemStack.getItem() instanceof ItemTool)) continue;
            if (!itemStack.getItem().getToolClasses(itemStack).contains(toolClass)) continue;
            float efficiency = ItemUtil.getToolEfficiency(itemStack);
            if (!(efficiency > bestEfficiency)) continue;
            bestSlot = currentSlot;
            bestEfficiency = efficiency;
        }
        return bestSlot;
    }

    public static int findInventorySlot(int currentSlot, Block block) {
        ItemStack currentItem = ItemUtil.mc.thePlayer.inventory.getStackInSlot(currentSlot);
        int bestSlot = currentSlot;
        float bestStrength = currentItem != null ? currentItem.getStrVsBlock(block) : 1.0f;
        for (int i = 0; i < 9; ++i) {
            ItemStack itemStack = ItemUtil.mc.thePlayer.inventory.getStackInSlot(i);
            if (itemStack == null) continue;
            float strength = itemStack.getStrVsBlock(block);
            if (!(strength > bestStrength)) continue;
            bestSlot = i;
            bestStrength = strength;
        }
        return bestSlot;
    }

    public static int findInventorySlot(int startSlot) {
        int bestSlot = -1;
        int maxStackSize = 0;
        for (int i = 0; i < 36; ++i) {
            int currentSlot = ((startSlot + i) % 36 + 36) % 36;
            ItemStack itemStack = ItemUtil.mc.thePlayer.inventory.getStackInSlot(currentSlot);
            if (itemStack == null) continue;
            if (!ItemUtil.isBlock(itemStack)) continue;
            if (maxStackSize >= itemStack.stackSize) continue;
            bestSlot = currentSlot;
            maxStackSize = itemStack.stackSize;
        }
        return bestSlot;
    }

    public static boolean hasRawUnbreakingEnchant() {
        ItemStack itemStack = ItemUtil.mc.thePlayer.getHeldItem();
        if (itemStack == null) {
            return false;
        }
        if (itemStack.hasTagCompound()) {
            NBTTagCompound tag = itemStack.getTagCompound();
            if (tag.hasKey("ExtraAttributes")) {
                NBTTagCompound extra = tag.getCompoundTag("ExtraAttributes");
                if (extra.hasKey("UHCid")) {
                    long id = extra.getLong("UHCid");
                    if (id == 50006L || id == 50009L) {
                        return true;
                    }
                }
            }
            if (tag.hasKey("HideFlags")
                    && itemStack.getItem() instanceof ItemSpade
                    && ((ItemSpade) itemStack.getItem()).getToolMaterial() == Item.ToolMaterial.EMERALD) {
                return true;
            }
        }
        if (itemStack.getItem() instanceof ItemEnchantedBook) {
            return false;
        }
        if (EnchantmentHelper.getEnchantments(itemStack).containsKey(19)) {
            return true;
        }
        return itemStack.getItem() instanceof ItemSword;
    }

    public static boolean isHoldingSword() {
        ItemStack itemStack = ItemUtil.mc.thePlayer.getHeldItem();
        if (itemStack == null) {
            return false;
        }
        return itemStack.getItem() instanceof ItemSword;
    }

    public static boolean isHoldingTool() {
        ItemStack itemStack = ItemUtil.mc.thePlayer.getHeldItem();
        if (itemStack == null) {
            return false;
        }
        return itemStack.getItem() instanceof ItemTool;
    }

    public static boolean isEating() {
        ItemStack itemStack = ItemUtil.mc.thePlayer.getHeldItem();
        if (itemStack == null) {
            return false;
        }
        if (ItemPotion.isSplash(itemStack.getItem().getMetadata(itemStack))) {
            return false;
        }
        return itemStack.getItemUseAction() == EnumAction.EAT || itemStack.getItemUseAction() == EnumAction.DRINK;
    }

    public static boolean isUsingBow() {
        ItemStack itemStack = ItemUtil.mc.thePlayer.getHeldItem();
        if (itemStack == null) {
            return false;
        }
        return itemStack.getItem() instanceof ItemBow;
    }

    public static boolean isHoldingNonEmpty() {
        ItemStack itemStack = ItemUtil.mc.thePlayer.getHeldItem();
        if (itemStack == null || itemStack.stackSize < 1) {
            return false;
        }
        return itemStack.getItem() instanceof ItemBlock;
    }

    public static boolean isHoldingBlock() {
        return ItemUtil.isBlock(ItemUtil.mc.thePlayer.getHeldItem());
    }

    public static boolean hasHoldItem() {
        ItemStack itemStack = ItemUtil.mc.thePlayer.getHeldItem();
        if (itemStack == null || itemStack.stackSize < 1) {
            return false;
        }
        return itemStack.getItem() instanceof ItemFireball;
    }

    static final class SpecialItems extends ArrayList<Integer> {
        SpecialItems() {
            this.add(1);
            this.add(3);
            this.add(5);
            this.add(6);
            this.add(8);
            this.add(10);
            this.add(11);
            this.add(12);
            this.add(14);
            this.add(21);
            this.add(22);
        }
    }
}
