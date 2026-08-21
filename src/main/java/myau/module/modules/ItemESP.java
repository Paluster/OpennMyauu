package myau.module.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.util.RegistryParser;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

public class ItemESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final List<ItemTarget> targets = new ArrayList<>();
    private final List<JsonObject> unresolvedTargets = new ArrayList<>();
    public final PercentProperty opacity = new PercentProperty("opacity", 25);
    public final BooleanProperty outline = new BooleanProperty("outline", false);
    public final BooleanProperty itemCount = new BooleanProperty("item-count", true);
    public final BooleanProperty autoScale = new BooleanProperty("auto-scale", true);

    public ItemESP() {
        super("ItemESP");
    }

    public List<ItemTarget> getTargets() {
        return this.targets;
    }

    public ItemTarget findTarget(String name) {
        for (ItemTarget target : this.targets) {
            if (target.name.equalsIgnoreCase(name)) {
                return target;
            }
        }
        return null;
    }

    public ItemTarget findTarget(int itemId, int metadata) {
        for (ItemTarget target : this.targets) {
            if (target.matches(itemId, metadata)) {
                return target;
            }
        }
        return null;
    }

    public boolean addTarget(ItemTarget target) {
        if (this.findTarget(target.name) != null) {
            return false;
        }
        for (ItemTarget existing : this.targets) {
            if (existing.itemId == target.itemId && existing.metadata == target.metadata) {
                return false;
            }
        }
        this.targets.add(target);
        return true;
    }

    public boolean removeTarget(String name) {
        ItemTarget target = this.findTarget(name);
        if (target == null) {
            ItemTarget parsed = parseTarget(name, 0);
            if (parsed != null) {
                for (ItemTarget existing : this.targets) {
                    if (existing.itemId == parsed.itemId && existing.metadata == parsed.metadata) {
                        target = existing;
                        break;
                    }
                }
            }
        }
        if (target == null) {
            return false;
        }
        this.targets.remove(target);
        return true;
    }

    public void clearTargets() {
        this.targets.clear();
        this.unresolvedTargets.clear();
    }

    public static ItemTarget fromItem(Item item, int metadata, int color) {
        return fromParsed(RegistryParser.fromItem(item, metadata), color);
    }

    public static ItemTarget parseTarget(String input, int color) {
        return fromParsed(RegistryParser.parseItem(input), color);
    }

    public static RegistryParser.Parsed parseDetailed(String input) {
        return RegistryParser.parseItem(input);
    }

    private static ItemTarget fromParsed(RegistryParser.Parsed parsed, int color) {
        if (parsed == null || !parsed.ok()) {
            return null;
        }
        if (color < 0) {
            color = randomColor();
        }
        return new ItemTarget(parsed.canonical, parsed.id, parsed.metadata, color & 0xFFFFFF, parsed.requiresData);
    }

    public static Integer parseHexColor(String input) {
        if (input == null) {
            return null;
        }
        String hex = input.startsWith("#") ? input.substring(1) : input;
        if (!hex.matches("(?i)[0-9a-f]{6}")) {
            return null;
        }
        return Integer.parseInt(hex, 16) & 0xFFFFFF;
    }

    public static boolean looksLikeColor(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        if (input.startsWith("#")) {
            return true;
        }
        return input.length() == 6;
    }

    public static int randomColor() {
        float hue = (float) Math.random();
        float saturation = 0.65F + (float) Math.random() * 0.35F;
        float brightness = 0.75F + (float) Math.random() * 0.25F;
        return Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
    }

    public JsonArray writeItems() {
        JsonArray array = new JsonArray();
        for (ItemTarget target : this.targets) {
            array.add(this.writeTarget(target));
        }
        for (JsonObject leftover : this.unresolvedTargets) {
            array.add(leftover);
        }
        return array;
    }

    private JsonObject writeTarget(ItemTarget target) {
        JsonObject object = new JsonObject();
        object.addProperty("item", target.name);
        object.addProperty("id", target.itemId);
        object.addProperty("meta", target.metadata);
        object.addProperty("color", String.format("%06X", target.color));
        return object;
    }

    public void readItems(JsonArray array) {
        this.targets.clear();
        this.unresolvedTargets.clear();
        for (JsonElement element : array) {
            try {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                ItemTarget target = this.readTarget(object);
                if (target != null) {
                    this.targets.add(target);
                } else {
                    this.unresolvedTargets.add(object);
                }
            } catch (Exception ignored) {
                if (element != null && element.isJsonObject()) {
                    this.unresolvedTargets.add(element.getAsJsonObject());
                }
            }
        }
    }

    private ItemTarget readTarget(JsonObject object) {
        int color = -1;
        if (object.has("color") && object.get("color").isJsonPrimitive()) {
            Integer parsed = parseHexColor(object.get("color").getAsString());
            if (parsed != null) {
                color = parsed;
            }
        }
        Integer meta = this.readInt(object, "meta");
        if (object.has("item") && object.get("item").isJsonPrimitive()) {
            ItemTarget byName = fromParsed(RegistryParser.parseItemForConfig(object.get("item").getAsString(), meta), color);
            if (byName != null) {
                return byName;
            }
        }
        Integer id = this.readInt(object, "id");
        if (id != null) {
            Item item = Item.getItemById(id);
            return fromItem(item, meta == null ? 0 : meta, color);
        }
        return null;
    }

    private Integer readInt(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return null;
        }
    }

    private ItemTarget matchStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        return this.findTarget(Item.getIdFromItem(stack.getItem()), stack.getMetadata());
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!this.isEnabled() || this.targets.isEmpty()) {
            return;
        }
        LinkedHashMap<ItemData, Integer> itemMap = new LinkedHashMap<>();
        for (Entity entity : TeamUtil.getLoadedEntitiesSorted()) {
            if (entity.ticksExisted >= 3
                    && (entity.ignoreFrustumCheck || RenderUtil.isInViewFrustum(entity.getEntityBoundingBox(), 0.125))
                    && entity instanceof EntityItem) {
                EntityItem entityItem = (EntityItem) entity;
                ItemStack stack = entityItem.getEntityItem();
                if (stack.stackSize > 0) {
                    ItemTarget target = this.matchStack(stack);
                    if (target != null) {
                        double x = RenderUtil.lerpDouble(entityItem.posX, entityItem.lastTickPosX, event.getPartialTicks());
                        double y = RenderUtil.lerpDouble(entityItem.posY, entityItem.lastTickPosY, event.getPartialTicks());
                        double z = RenderUtil.lerpDouble(entityItem.posZ, entityItem.lastTickPosZ, event.getPartialTicks());
                        ItemData data = new ItemData(Item.getIdFromItem(stack.getItem()), stack.getMetadata(), target.color, x, y, z);
                        Integer id = itemMap.get(data);
                        itemMap.put(data, stack.stackSize + (id == null ? 0 : id));
                    }
                }
            }
        }
        for (Entry<ItemData, Integer> itemEntry : itemMap.entrySet()) {
            Color itemColor = new Color(itemEntry.getKey().color);
            double x = itemEntry.getKey().x - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
            double y = itemEntry.getKey().y - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
            double z = itemEntry.getKey().z - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
            double distance = mc.getRenderViewEntity().getDistance(itemEntry.getKey().x, itemEntry.getKey().y, itemEntry.getKey().z);
            double scale = 0.5 + 0.375 * ((Math.max(6.0, this.autoScale.getValue() ? distance : 6.0) - 6.0) / 28.0);
            AxisAlignedBB axisAlignedBB = new AxisAlignedBB(x - scale * 0.5, y, z - scale * 0.5, x + scale * 0.5, y + scale, z + scale * 0.5);
            RenderUtil.enableRenderState();
            if (this.opacity.getValue() > 0) {
                RenderUtil.drawFilledBox(
                        axisAlignedBB, itemColor.getRed(), itemColor.getGreen(), itemColor.getBlue()
                );
                GlStateManager.resetColor();
            }
            if (this.outline.getValue()) {
                RenderUtil.drawBoundingBox(axisAlignedBB, itemColor.getRed(), itemColor.getGreen(), itemColor.getBlue(), 255, 1.5F);
                GlStateManager.resetColor();
            }
            RenderUtil.disableRenderState();
            if (this.itemCount.getValue()) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(x, y + scale * 0.5, z);
                GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
                float flip = mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F;
                GlStateManager.rotate(mc.getRenderManager().playerViewX, flip, 0.0F, 0.0F);
                double fontScale = -0.04375 - 0.0328125 * ((Math.max(6.0, this.autoScale.getValue() ? distance : 6.0) - 6.0) / 28.0);
                GlStateManager.scale(fontScale, fontScale, 1.0);
                GlStateManager.disableDepth();
                String countText = String.format("%d", itemEntry.getValue());
                RenderUtil.drawOutlinedString(
                        countText,
                        ((float) mc.fontRendererObj.getStringWidth(countText) / 2.0F - 0.5F) * -1.0F,
                        ((float) (mc.fontRendererObj.FONT_HEIGHT / 2) - 0.5F) * -1.0F
                );
                GlStateManager.enableDepth();
                GlStateManager.resetColor();
                GlStateManager.popMatrix();
            }
        }
    }

    public static class ItemTarget {
        public final String name;
        public final int itemId;
        public final int metadata;
        public final boolean requiresData;
        public int color;

        public ItemTarget(String name, int itemId, int metadata, int color, boolean requiresData) {
            this.name = name;
            this.itemId = itemId;
            this.metadata = metadata;
            this.color = color;
            this.requiresData = requiresData;
        }

        public boolean matches(int id, int meta) {
            if (this.itemId != id) {
                return false;
            }
            return !this.requiresData || this.metadata == meta;
        }
    }

    public static class ItemData {
        private final int hashCode;
        public final int itemId;
        public final int metadata;
        public final int color;
        public final double x;
        public final double y;
        public final double z;

        public ItemData(int id, int metadata, int color, double x, double y, double z) {
            this.itemId = id;
            this.metadata = metadata;
            this.color = color;
            this.x = x;
            this.y = y;
            this.z = z;
            this.hashCode = Objects.hash(id, metadata, (int) x, (int) y, (int) z);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (object != null && this.getClass() == object.getClass()) {
                ItemData itemData = (ItemData) object;
                return this.itemId == itemData.itemId
                        && this.metadata == itemData.metadata
                        && (int) this.x == (int) itemData.x
                        && (int) this.y == (int) itemData.y
                        && (int) this.z == (int) itemData.z;
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }
    }
}
