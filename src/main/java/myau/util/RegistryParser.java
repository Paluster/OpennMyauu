package myau.util;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RegistryParser {
    public enum Fail {
        OK,
        EMPTY,
        SPACES,
        NUMERIC_ID,
        INVALID_FORMAT,
        UNKNOWN,
        MISSING_DATA,
        INVALID_DATA
    }

    public static final class Parsed {
        public final Fail fail;
        public final String canonical;
        public final int id;
        public final int metadata;
        public final boolean requiresData;
        public final Block block;
        public final Item item;

        private Parsed(Fail fail, String canonical, int id, int metadata, boolean requiresData, Block block, Item item) {
            this.fail = fail;
            this.canonical = canonical;
            this.id = id;
            this.metadata = metadata;
            this.requiresData = requiresData;
            this.block = block;
            this.item = item;
        }

        public boolean ok() {
            return this.fail == Fail.OK;
        }

        private static Parsed fail(Fail fail) {
            return new Parsed(fail, null, 0, 0, false, null, null);
        }

        private static Parsed block(String canonical, Block block, int metadata, boolean requiresData) {
            return new Parsed(Fail.OK, canonical, Block.getIdFromBlock(block), metadata, requiresData, block, null);
        }

        private static Parsed item(String canonical, Item item, int metadata, boolean requiresData) {
            return new Parsed(Fail.OK, canonical, Item.getIdFromItem(item), metadata, requiresData, null, item);
        }
    }

    private RegistryParser() {
    }

    public static Parsed parseBlock(String input) {
        Split split = split(input);
        if (split.fail != Fail.OK) {
            return Parsed.fail(split.fail);
        }
        Block block = blockByPath(split.path);
        if (block == null || block == Blocks.air) {
            return Parsed.fail(Fail.UNKNOWN);
        }
        return resolve(split, Item.getItemFromBlock(block), true, block, null);
    }

    public static Parsed parseItem(String input) {
        Split split = split(input);
        if (split.fail != Fail.OK) {
            return Parsed.fail(split.fail);
        }
        Item item = itemByPath(split.path);
        if (item == null || Item.getIdFromItem(item) == 0) {
            return Parsed.fail(Fail.UNKNOWN);
        }
        return resolve(split, item, false, null, item);
    }

    public static Parsed parseBlockForConfig(String name, Integer storedMeta) {
        return parseForConfig(name, storedMeta, true);
    }

    public static Parsed parseItemForConfig(String name, Integer storedMeta) {
        return parseForConfig(name, storedMeta, false);
    }

    private static Parsed parseForConfig(String name, Integer storedMeta, boolean blockMode) {
        Parsed parsed = blockMode ? parseBlock(name) : parseItem(name);
        if (parsed.ok() || name == null || storedMeta == null || storedMeta < 0) {
            return parsed;
        }
        if (parsed.fail != Fail.MISSING_DATA) {
            return parsed;
        }
        String trimmed = name.trim();
        if (trimmed.indexOf(' ') >= 0) {
            return parsed;
        }
        int colon = trimmed.lastIndexOf(':');
        if (colon >= 0) {
            String suffix = trimmed.substring(colon + 1);
            if (suffix.matches("\\d+")) {
                return parsed;
            }
        }
        String combined = trimmed + ":" + storedMeta;
        return blockMode ? parseBlock(combined) : parseItem(combined);
    }

    public static Parsed fromBlock(Block block, int metadata) {
        if (block == null || block == Blocks.air) {
            return Parsed.fail(Fail.UNKNOWN);
        }
        String path = registryPath(Block.blockRegistry.getNameForObject(block));
        if (path == null) {
            return Parsed.fail(Fail.UNKNOWN);
        }
        boolean requiresData = requiresData(Item.getItemFromBlock(block));
        int meta = normalizeMeta(requiresData, metadata);
        if (requiresData && metadata < 0) {
            return Parsed.fail(Fail.MISSING_DATA);
        }
        if (requiresData && !validMeta(Item.getItemFromBlock(block), meta)) {
            return Parsed.fail(Fail.INVALID_DATA);
        }
        return Parsed.block(canonical(path, meta, requiresData), block, meta, requiresData);
    }

    public static Parsed fromItem(Item item, int metadata) {
        if (item == null || Item.getIdFromItem(item) == 0) {
            return Parsed.fail(Fail.UNKNOWN);
        }
        String path = registryPath(Item.itemRegistry.getNameForObject(item));
        if (path == null) {
            return Parsed.fail(Fail.UNKNOWN);
        }
        boolean requiresData = requiresData(item);
        int meta = normalizeMeta(requiresData, metadata);
        if (requiresData && metadata < 0) {
            return Parsed.fail(Fail.MISSING_DATA);
        }
        if (requiresData && !validMeta(item, meta)) {
            return Parsed.fail(Fail.INVALID_DATA);
        }
        return Parsed.item(canonical(path, meta, requiresData), item, meta, requiresData);
    }

    public static int variantMeta(Block block, int packedMeta) {
        if (block == null || block == Blocks.air) {
            return packedMeta;
        }
        try {
            return block.damageDropped(block.getStateFromMeta(packedMeta));
        } catch (Exception ignored) {
            return packedMeta;
        }
    }

    public static String failMessage(String input) {
        return String.format("Invalid (&o%s&r)", input);
    }

    private static Parsed resolve(Split split, Item item, boolean blockMode, Block block, Item parsedItem) {
        boolean requiresData = requiresData(item);
        if (requiresData && split.meta == null) {
            return Parsed.fail(Fail.MISSING_DATA);
        }
        if (!requiresData && split.meta != null) {
            return Parsed.fail(Fail.INVALID_DATA);
        }
        int metadata = requiresData ? split.meta : 0;
        if (requiresData && !validMeta(item, metadata)) {
            return Parsed.fail(Fail.INVALID_DATA);
        }
        String canonical = canonical(split.path, metadata, requiresData);
        if (blockMode) {
            return Parsed.block(canonical, block, metadata, requiresData);
        }
        return Parsed.item(canonical, parsedItem, metadata, requiresData);
    }

    private static class Split {
        private final Fail fail;
        private final String path;
        private final Integer meta;

        private Split(Fail fail, String path, Integer meta) {
            this.fail = fail;
            this.path = path;
            this.meta = meta;
        }
    }

    private static Split split(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new Split(Fail.EMPTY, null, null);
        }
        String name = input.trim().toLowerCase(Locale.ROOT);
        if (name.indexOf(' ') >= 0) {
            return new Split(Fail.SPACES, null, null);
        }
        if (name.startsWith("minecraft:")) {
            name = name.substring("minecraft:".length());
        }
        Integer meta = null;
        String path = name;
        int colon = name.lastIndexOf(':');
        if (colon >= 0) {
            String suffix = name.substring(colon + 1);
            if (suffix.matches("\\d+")) {
                try {
                    meta = Integer.parseInt(suffix);
                    path = name.substring(0, colon);
                } catch (NumberFormatException ignored) {
                    return new Split(Fail.INVALID_FORMAT, null, null);
                }
            }
        }
        if (path.isEmpty() || path.charAt(0) == ':' || path.charAt(path.length() - 1) == ':') {
            return new Split(Fail.INVALID_FORMAT, null, null);
        }
        if (path.matches("\\d+")) {
            return new Split(Fail.NUMERIC_ID, null, null);
        }
        if (!path.matches("[a-z0-9_]+(?::[a-z0-9_]+)?")) {
            return new Split(Fail.INVALID_FORMAT, null, null);
        }
        if (meta != null && meta < 0) {
            return new Split(Fail.INVALID_DATA, null, null);
        }
        return new Split(Fail.OK, path, meta);
    }

    private static Block blockByPath(String path) {
        Block block = Block.getBlockFromName(path);
        if (block != null && block != Blocks.air) {
            return block;
        }
        if (!path.contains(":")) {
            return Block.getBlockFromName("minecraft:" + path);
        }
        return null;
    }

    private static Item itemByPath(String path) {
        Item item = Item.getByNameOrId(path);
        if (item != null && Item.getIdFromItem(item) != 0) {
            return item;
        }
        if (!path.contains(":")) {
            item = Item.getByNameOrId("minecraft:" + path);
            if (item != null && Item.getIdFromItem(item) != 0) {
                return item;
            }
        }
        return null;
    }

    private static boolean requiresData(Item item) {
        if (item == null || Item.getIdFromItem(item) == 0) {
            return false;
        }
        if (item.getHasSubtypes()) {
            return true;
        }
        return variantMetas(item).size() > 1;
    }

    private static boolean validMeta(Item item, int metadata) {
        if (metadata < 0) {
            return false;
        }
        Set<Integer> variants = variantMetas(item);
        if (variants.size() > 1) {
            return variants.contains(metadata);
        }
        return item != null && item.getHasSubtypes();
    }

    private static Set<Integer> variantMetas(Item item) {
        Set<Integer> metas = new LinkedHashSet<Integer>();
        if (item == null) {
            metas.add(0);
            return metas;
        }
        List<ItemStack> variants = new ArrayList<ItemStack>();
        try {
            item.getSubItems(item, item.getCreativeTab(), variants);
        } catch (Exception ignored) {
        }
        if (variants.isEmpty()) {
            variants.add(new ItemStack(item, 1, 0));
        }
        for (ItemStack stack : variants) {
            if (stack != null) {
                metas.add(stack.getMetadata());
            }
        }
        if (metas.isEmpty()) {
            metas.add(0);
        }
        return metas;
    }

    private static int normalizeMeta(boolean requiresData, int metadata) {
        if (!requiresData) {
            return 0;
        }
        return metadata;
    }

    private static String canonical(String path, int metadata, boolean requiresData) {
        if (requiresData) {
            return path + ":" + metadata;
        }
        return path;
    }

    private static String registryPath(Object registry) {
        if (registry == null) {
            return null;
        }
        String name = registry.toString().toLowerCase(Locale.ROOT);
        if (name.startsWith("minecraft:")) {
            name = name.substring("minecraft:".length());
        }
        if (name.isEmpty() || name.matches("\\d+")) {
            return null;
        }
        return name;
    }
}
