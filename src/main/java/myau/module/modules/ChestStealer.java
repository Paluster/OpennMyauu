package myau.module.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.UpdateEvent;
import myau.events.WindowClickEvent;
import myau.module.Module;
import myau.util.ChatUtil;
import myau.util.RegistryParser;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.RandomUtils;

import java.util.ArrayList;
import java.util.List;

public class ChestStealer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int clickDelay = 0;
    private int oDelay = 0;
    private boolean inChest = false;
    private boolean warnedFull = false;
    private final List<SkipItem> skipItems = new ArrayList<>();
    private final List<JsonObject> unresolvedSkipItems = new ArrayList<>();
    public final IntProperty minDelay = new IntProperty("min-delay", 1, 0, 20);
    public final IntProperty maxDelay = new IntProperty("max-delay", 2, 0, 20);
    public final IntProperty openDelay = new IntProperty("open-delay", 1, 0, 20);
    public final BooleanProperty autoClose = new BooleanProperty("auto-close", false);
    public final BooleanProperty nameCheck = new BooleanProperty("name-check", true);

    private boolean isValidGameMode() {
        GameType gameType = mc.playerController.getCurrentGameType();
        return gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE;
    }

    private void shiftClick(int windowId, int slotId) {
        mc.playerController.windowClick(windowId, slotId, 0, 1, mc.thePlayer);
    }

    public ChestStealer() {
        super("ChestStealer");
    }

    public List<SkipItem> getSkipItems() {
        return this.skipItems;
    }

    public boolean addSkip(SkipItem item) {
        if (this.findSkip(item) != null) {
            return false;
        }
        this.skipItems.add(item);
        return true;
    }

    public SkipItem findSkip(SkipItem parsed) {
        for (SkipItem existing : this.skipItems) {
            if (existing.itemId == parsed.itemId && existing.metadata == parsed.metadata) {
                return existing;
            }
            if (existing.name.equalsIgnoreCase(parsed.name)) {
                return existing;
            }
        }
        return null;
    }

    public boolean removeSkip(String name) {
        SkipItem parsed = parseItem(name);
        SkipItem target = parsed == null ? null : this.findSkip(parsed);
        if (target == null) {
            for (SkipItem existing : this.skipItems) {
                if (existing.name.equalsIgnoreCase(name)) {
                    target = existing;
                    break;
                }
            }
        }
        if (target == null) {
            return false;
        }
        this.skipItems.remove(target);
        return true;
    }

    public void clearSkipItems() {
        this.skipItems.clear();
        this.unresolvedSkipItems.clear();
    }

    public boolean shouldSkip(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        int itemId = Item.getIdFromItem(stack.getItem());
        int metadata = stack.getMetadata();
        for (SkipItem skip : this.skipItems) {
            if (skip.matches(itemId, metadata)) {
                return true;
            }
        }
        return false;
    }

    public static SkipItem parseItem(String input) {
        return fromParsed(RegistryParser.parseItem(input));
    }

    public static RegistryParser.Parsed parseDetailed(String input) {
        return RegistryParser.parseItem(input);
    }

    private static SkipItem fromItem(Item item, int metadata) {
        return fromParsed(RegistryParser.fromItem(item, metadata));
    }

    private static SkipItem fromParsed(RegistryParser.Parsed parsed) {
        if (parsed == null || !parsed.ok()) {
            return null;
        }
        return new SkipItem(parsed.canonical, parsed.id, parsed.metadata, parsed.requiresData);
    }

    public JsonArray writeSkipItems() {
        JsonArray array = new JsonArray();
        for (SkipItem skip : this.skipItems) {
            array.add(this.writeSkip(skip));
        }
        for (JsonObject leftover : this.unresolvedSkipItems) {
            array.add(leftover);
        }
        return array;
    }

    private JsonObject writeSkip(SkipItem skip) {
        JsonObject object = new JsonObject();
        object.addProperty("item", skip.name);
        object.addProperty("id", skip.itemId);
        object.addProperty("meta", skip.metadata);
        return object;
    }

    public void readSkipItems(JsonArray array) {
        this.skipItems.clear();
        this.unresolvedSkipItems.clear();
        for (JsonElement element : array) {
            try {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                SkipItem skip = this.readSkip(object);
                if (skip != null) {
                    this.skipItems.add(skip);
                } else {
                    this.unresolvedSkipItems.add(object);
                }
            } catch (Exception ignored) {
                if (element != null && element.isJsonObject()) {
                    this.unresolvedSkipItems.add(element.getAsJsonObject());
                }
            }
        }
    }

    private SkipItem readSkip(JsonObject object) {
        Integer meta = this.readInt(object, "meta");
        if (object.has("item") && object.get("item").isJsonPrimitive()) {
            SkipItem parsed = fromParsed(RegistryParser.parseItemForConfig(object.get("item").getAsString(), meta));
            if (parsed != null) {
                return parsed;
            }
        }
        Integer id = this.readInt(object, "id");
        if (id != null) {
            Item item = Item.getItemById(id);
            if (item != null && Item.getIdFromItem(item) != 0) {
                return fromItem(item, meta == null ? 0 : meta);
            }
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

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.clickDelay > 0) {
                this.clickDelay--;
            }
            if (this.oDelay > 0) {
                this.oDelay--;
            }
            if (!(mc.currentScreen instanceof GuiChest)) {
                this.inChest = false;
            } else {
                Container container = ((GuiChest) mc.currentScreen).inventorySlots;
                if (!(container instanceof ContainerChest)) {
                    this.inChest = false;
                } else {
                    if (!this.inChest) {
                        this.inChest = true;
                        this.warnedFull = false;
                        this.oDelay = this.openDelay.getValue() + 1;
                    }
                    if (this.oDelay <= 0 && this.clickDelay <= 0) {
                        if (this.isEnabled() && this.isValidGameMode()) {
                            IInventory inventory = ((ContainerChest) container).getLowerChestInventory();
                            if (this.nameCheck.getValue()) {
                                String inventoryName = inventory.getName();
                                if (!inventoryName.equals(I18n.format("container.chest")) && !inventoryName.equals(I18n.format("container.chestDouble"))) {
                                    return;
                                }
                            }
                            if (mc.thePlayer.inventory.getFirstEmptyStack() == -1) {
                                if (!this.warnedFull) {
                                    ChatUtil.sendFormatted(String.format("%s%s: &cYour inventory is full!&r", Myau.clientName, this.getName()));
                                    this.warnedFull = true;
                                }
                                if (this.autoClose.getValue()) {
                                    mc.thePlayer.closeScreen();
                                }
                            } else {
                                for (int i = 0; i < inventory.getSizeInventory(); i++) {
                                    if (container.getSlot(i).getHasStack()) {
                                        ItemStack stack = container.getSlot(i).getStack();
                                        if (!this.shouldSkip(stack)) {
                                            this.shiftClick(container.windowId, i);
                                            return;
                                        }
                                    }
                                }
                                if (this.autoClose.getValue()) {
                                    mc.thePlayer.closeScreen();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onWindowClick(WindowClickEvent event) {
        this.clickDelay = RandomUtils.nextInt(this.minDelay.getValue() + 1, this.maxDelay.getValue() + 2);
    }

    @Override
    public void verifyValue(String mode) {
        switch (mode) {
            case "min-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.maxDelay.setValue(this.minDelay.getValue());
                }
                break;
            case "max-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.minDelay.setValue(this.maxDelay.getValue());
                }
        }
    }

    public static class SkipItem {
        public final String name;
        public final int itemId;
        public final int metadata;
        public final boolean requiresData;

        public SkipItem(String name, int itemId, int metadata, boolean requiresData) {
            this.name = name;
            this.itemId = itemId;
            this.metadata = metadata;
            this.requiresData = requiresData;
        }

        public boolean matches(int id, int meta) {
            if (this.itemId != id) {
                return false;
            }
            return !this.requiresData || this.metadata == meta;
        }
    }
}
