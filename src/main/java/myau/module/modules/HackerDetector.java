package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.TickEvent;
import myau.hackerdetector.HackerDetectorEngine;
import myau.hackerdetector.data.IEntityPlayerSamples;
import myau.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S22PacketMultiBlockChange.BlockUpdateData;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S25PacketBlockBreakAnim;

public class HackerDetector extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final HackerDetectorEngine engine = new HackerDetectorEngine();

    public HackerDetector() {
        super("HackerDetector");
    }

    public static HackerDetector get() {
        if (Myau.moduleManager == null) {
            return null;
        }
        return (HackerDetector) Myau.moduleManager.modules.get(HackerDetector.class);
    }

    public static boolean isActive() {
        HackerDetector module = get();
        return module != null && module.isEnabled();
    }

    public HackerDetectorEngine getEngine() {
        return this.engine;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            this.engine.onTickStart();
        } else if (event.getType() == EventType.POST) {
            this.engine.onTickEnd();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        try {
            if (event.getType() == EventType.RECEIVE) {
                this.engine.lookForAttacks(event.getPacket());
                if (event.getPacket() instanceof S23PacketBlockChange) {
                    final S23PacketBlockChange packet = (S23PacketBlockChange) event.getPacket();
                    this.engine.addScheduledTask(() -> this.engine.addPlacedBlock(packet.getBlockPosition(), packet.getBlockState()));
                } else if (event.getPacket() instanceof S22PacketMultiBlockChange) {
                    final S22PacketMultiBlockChange packet = (S22PacketMultiBlockChange) event.getPacket();
                    this.engine.addScheduledTask(() -> {
                        for (BlockUpdateData data : packet.getChangedBlocks()) {
                            this.engine.addPlacedBlock(data.getPos(), data.getBlockState());
                        }
                    });
                } else if (event.getPacket() instanceof S25PacketBlockBreakAnim) {
                    final S25PacketBlockBreakAnim packet = (S25PacketBlockBreakAnim) event.getPacket();
                    this.engine.addScheduledTask(() -> this.handleBreakAnim(packet));
                }
            } else if (event.getType() == EventType.SEND && event.getPacket() instanceof C08PacketPlayerBlockPlacement) {
                final C08PacketPlayerBlockPlacement packet = (C08PacketPlayerBlockPlacement) event.getPacket();
                if (packet.getPlacedBlockDirection() == 255) {
                    return;
                }
                final ItemStack stack = packet.getStack();
                if (packet.getPosition() == null || stack == null || !(stack.getItem() instanceof ItemBlock)) {
                    return;
                }
                this.engine.onPlayerBlockPacket(packet.getPosition(), packet.getPlacedBlockDirection(), ((ItemBlock) stack.getItem()).getBlock());
            }
        } catch (Throwable ignored) {
        }
    }

    private void handleBreakAnim(S25PacketBlockBreakAnim packet) {
        if (mc.theWorld == null) {
            return;
        }
        final int progress = packet.getProgress();
        if (progress < 0 || progress >= 255) {
            return;
        }
        final Entity entity = mc.theWorld.getEntityByID(packet.getBreakerId());
        if (entity instanceof EntityOtherPlayerMP) {
            final EntityOtherPlayerMP player = (EntityOtherPlayerMP) entity;
            if (player.getHeldItem() != null && player.getHeldItem().getItem() instanceof ItemTool) {
                ((IEntityPlayerSamples) player).getPlayerDataSamples().blockTouched = packet.getPosition();
            }
        }
    }
}