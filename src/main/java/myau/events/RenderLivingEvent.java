package myau.events;

import myau.event.events.Event;
import myau.event.types.EventType;
import net.minecraft.entity.EntityLivingBase;

public class RenderLivingEvent implements Event {
    private final EventType type;
    private final EntityLivingBase entity;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float partialTicks;

    public RenderLivingEvent(EventType type, EntityLivingBase entityLivingBase) {
        this(type, entityLivingBase, 0.0, 0.0, 0.0, 0.0F, 0.0F);
    }

    public RenderLivingEvent(EventType type, EntityLivingBase entityLivingBase, double x, double y, double z, float yaw, float partialTicks) {
        this.type = type;
        this.entity = entityLivingBase;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.partialTicks = partialTicks;
    }

    public EventType getType() {
        return this.type;
    }

    public EntityLivingBase getEntity() {
        return this.entity;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPartialTicks() {
        return this.partialTicks;
    }
}
