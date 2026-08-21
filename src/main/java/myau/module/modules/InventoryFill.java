package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.mixin.IAccessorGuiContainer;
import myau.module.Module;
import myau.property.properties.IntProperty;
import myau.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class InventoryFill extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil clickTimer = new TimerUtil();
    private int lastClickedSlot = -1;
    public final IntProperty cps = new IntProperty("cps", 15, 1, 20);

    public InventoryFill() {
        super("InventoryFill");
    }

    private boolean rollChance(double chance) {
        return Math.random() <= Math.max(Math.min(chance, 1.0), 0.0);
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            this.lastClickedSlot = -1;
            return;
        }
        if (!(mc.currentScreen instanceof GuiContainer) || mc.thePlayer == null) {
            this.lastClickedSlot = -1;
            return;
        }
        if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            return;
        }
        long delay = 1000L / Math.max(1, this.cps.getValue());
        if (!this.clickTimer.hasTimeElapsed(delay) && this.cps.getValue() < 20) {
            return;
        }
        this.clickHoveredSlot((GuiContainer) mc.currentScreen);
    }

    private void clickHoveredSlot(GuiContainer gui) {
        if (mc.thePlayer.inventory.getItemStack() != null) {
            return;
        }
        int mouseX = Mouse.getX() * gui.width / mc.displayWidth;
        int mouseY = gui.height - Mouse.getY() * gui.height / mc.displayHeight - 1;
        Slot slot = ((IAccessorGuiContainer) gui).invokeGetSlotAtPosition(mouseX, mouseY);
        if (slot == null || !slot.getHasStack()) {
            return;
        }
        int slotNumber = slot.slotNumber;
        if (this.lastClickedSlot == slotNumber) {
            return;
        }
        mc.playerController.windowClick(gui.inventorySlots.windowId, slotNumber, 0, 1, mc.thePlayer);
        if (this.rollChance(0.8)) {
            this.lastClickedSlot = slotNumber;
        }
        this.clickTimer.reset();
    }

    @Override
    public void onEnabled() {
        this.lastClickedSlot = -1;
        this.clickTimer.reset();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.cps.getValue().toString()};
    }
}
