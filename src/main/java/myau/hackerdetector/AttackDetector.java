package myau.hackerdetector;

import myau.hackerdetector.data.AttackInfo;
import myau.hackerdetector.data.IEntityPlayerSamples;
import myau.hackerdetector.data.PlayerDataSamples;
import myau.mixin.IAccessorS19PacketEntityStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.util.Vec3;

public class AttackDetector {
    private final HackerDetectorEngine hackerDetector;
    private boolean lastPacketWasSwing;
    private long lastSwingTime;
    private int attackerID;
    private boolean lastPacketWasHurt;
    private long lastHurtTime;
    private int lastHurtID;
    private boolean consecutiveSwingHurt;

    public AttackDetector(HackerDetectorEngine hackerDetector) {
        this.hackerDetector = hackerDetector;
    }

    public void lookForAttacks(Packet<?> packet) {
        if (packet instanceof S0BPacketAnimation) {
            final S0BPacketAnimation packetAnimation = (S0BPacketAnimation) packet;
            final int animationType = packetAnimation.getAnimationType();
            if (animationType == 0) {
                this.lastPacketWasHurt = false;
                this.consecutiveSwingHurt = false;
                this.lastPacketWasSwing = true;
                this.lastSwingTime = System.currentTimeMillis();
                this.attackerID = packetAnimation.getEntityID();
                this.onEntitySwing(this.attackerID);
                return;
            }
            if (animationType == 4 || animationType == 5) {
                if (System.currentTimeMillis() - this.lastSwingTime < 2) {
                    if (this.lastPacketWasSwing) {
                        this.checkPlayerAttack(this.attackerID, packetAnimation.getEntityID(), animationType == 4 ? AttackType.DIRECT_CRITICAL : AttackType.DIRECT_SHARPNESS, null);
                    } else {
                        this.checkPlayerAttack(this.attackerID, packetAnimation.getEntityID(), animationType == 4 ? AttackType.CRITICAL : AttackType.SHARPNESS, null);
                    }
                }
            }
        } else if (packet instanceof S12PacketEntityVelocity) {
            if (System.currentTimeMillis() - this.lastSwingTime < 2) {
                final S12PacketEntityVelocity packetVelo = (S12PacketEntityVelocity) packet;
                if (packetVelo.getMotionX() != 0 || packetVelo.getMotionY() != 0 || packetVelo.getMotionZ() != 0) {
                    if (this.lastPacketWasSwing) {
                        this.checkPlayerAttack(this.attackerID, packetVelo.getEntityID(), AttackType.DIRECT_VELOCITY, null);
                    } else {
                        this.checkPlayerAttack(this.attackerID, packetVelo.getEntityID(), AttackType.VELOCITY, null);
                    }
                }
            }
        } else if (packet instanceof IAccessorS19PacketEntityStatus) {
            if (((S19PacketEntityStatus) packet).getOpCode() == 2) {
                if (this.lastPacketWasSwing) {
                    this.consecutiveSwingHurt = true;
                }
                this.lastPacketWasSwing = false;
                this.lastPacketWasHurt = true;
                this.lastHurtTime = System.currentTimeMillis();
                this.lastHurtID = ((IAccessorS19PacketEntityStatus) packet).getEntityId();
                return;
            }
        } else if (packet instanceof S29PacketSoundEffect) {
            final S29PacketSoundEffect soundPacket = (S29PacketSoundEffect) packet;
            if (this.lastPacketWasHurt && System.currentTimeMillis() - this.lastSwingTime < 2 && System.currentTimeMillis() - this.lastHurtTime < 2) {
                if ("game.player.hurt".equals(soundPacket.getSoundName())) {
                    this.checkPlayerAttack(this.attackerID, this.lastHurtID, this.consecutiveSwingHurt ? AttackType.DIRECTHURTSOUND : AttackType.HURTSOUND, new Vec3(soundPacket.getX(), soundPacket.getY(), soundPacket.getZ()));
                } else if ("game.player.die".equals(soundPacket.getSoundName())) {
                    this.checkPlayerAttack(this.attackerID, this.lastHurtID, this.consecutiveSwingHurt ? AttackType.DIRECTDEATHSOUND : AttackType.DEATHSOUND, new Vec3(soundPacket.getX(), soundPacket.getY(), soundPacket.getZ()));
                }
            }
        }
        this.lastPacketWasHurt = false;
        this.lastPacketWasSwing = false;
        this.consecutiveSwingHurt = false;
    }

    private void onEntitySwing(int entityID) {
        this.hackerDetector.addScheduledTask(() -> {
            if (Minecraft.getMinecraft().theWorld == null) {
                return;
            }
            final Entity attacker = Minecraft.getMinecraft().theWorld.getEntityByID(entityID);
            if (attacker instanceof IEntityPlayerSamples) {
                ((IEntityPlayerSamples) attacker).getPlayerDataSamples().hasSwung = true;
            }
        });
    }

    private void checkPlayerAttack(int attackerEntityId, int targetEntityId, AttackType attackType, Vec3 soundPos) {
        this.hackerDetector.addScheduledTask(() -> {
            final Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null || mc.thePlayer == null) {
                return;
            }
            final Entity attacker = mc.theWorld.getEntityByID(attackerEntityId);
            final Entity target = mc.theWorld.getEntityByID(targetEntityId);
            if (!(attacker instanceof EntityPlayer) || !(target instanceof EntityPlayer) || attacker == target) {
                return;
            }
            final double xDiff = Math.abs(mc.thePlayer.posX - target.posX);
            final double zDiff = Math.abs(mc.thePlayer.posZ - target.posZ);
            if (xDiff > 56D || zDiff > 56D) {
                return;
            }
            if (attacker.getDistanceSqToEntity(target) > 64d) {
                return;
            }
            switch (attackType) {
                case HURTSOUND:
                case DEATHSOUND:
                case DIRECTHURTSOUND:
                case DIRECTDEATHSOUND:
                    if (soundPos != null && Math.abs(soundPos.xCoord - target.posX) < 1d && Math.abs(soundPos.yCoord - target.posY) < 1d && Math.abs(soundPos.zCoord - target.posZ) < 1d) {
                        this.onPlayerAttack((EntityPlayer) attacker, (EntityPlayer) target, attackType);
                    }
                    break;
                case DIRECT_VELOCITY:
                case VELOCITY:
                    if (mc.thePlayer == target) {
                        this.onPlayerAttack((EntityPlayer) attacker, mc.thePlayer, attackType);
                    }
                    break;
                case CRITICAL:
                case DIRECT_CRITICAL:
                    if (attacker.ridingEntity == null) {
                        this.onPlayerAttack((EntityPlayer) attacker, (EntityPlayer) target, attackType);
                    }
                    break;
                case DIRECT_SHARPNESS:
                case SHARPNESS:
                    final ItemStack heldItem = ((EntityPlayer) attacker).getHeldItem();
                    if (heldItem != null) {
                        final Item item = heldItem.getItem();
                        if ((item instanceof ItemSword || item instanceof ItemTool) && heldItem.isItemEnchanted()) {
                            this.onPlayerAttack((EntityPlayer) attacker, (EntityPlayer) target, attackType);
                        }
                    }
                    break;
                default:
                    break;
            }
        });
    }

    private void onPlayerAttack(EntityPlayer attacker, EntityPlayer target, AttackType attackType) {
        final PlayerDataSamples data = ((IEntityPlayerSamples) attacker).getPlayerDataSamples();
        if (data.attackInfo == null) {
            data.attackInfo = new AttackInfo(target, attackType);
        } else if (data.attackInfo.target == null) {
            data.attackInfo.setTarget(target);
        } else if (data.attackInfo.target != target) {
            data.attackInfo.multiTarget = true;
        }
    }

    public enum AttackType {
        CRITICAL,
        DEATHSOUND,
        DIRECTDEATHSOUND,
        DIRECTHURTSOUND,
        DIRECT_CRITICAL,
        DIRECT_SHARPNESS,
        DIRECT_VELOCITY,
        DREADLORDHIT,
        HURTSOUND,
        SHAMANHIT,
        SHARPNESS,
        VELOCITY
    }
}