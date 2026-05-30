package com.hihelloy.work.lib.verlet;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Link {

    private Player player;
    private Particle.DustOptions dustOptions;
    private float particleSize;
    private Color color;
    private int particleAmount;

    protected VerletObject object1;
    protected VerletObject object2;
    private double targetDistance;
    private double sqrTargetDistance;

    private BlockDisplay display = null;
    private VerletRope rope = null;
    private Material mat;

    public Link(Player player, VerletObject o1, VerletObject o2, double targetDistance, int particleAmount, VerletRope rope, Material mat) {
        this.particleAmount = particleAmount;
        this.player = player;

        this.object1 = o1;
        this.color = o1.getColor();
        this.particleSize = o1.getSize();
        this.dustOptions = new Particle.DustOptions(this.color, this.particleSize);
        this.object2 = o2;
        this.targetDistance = targetDistance;
        this.sqrTargetDistance = this.targetDistance * this.targetDistance;
        this.rope = rope;
        this.mat = mat;
    }

    public Link(Player player, VerletObject o1, VerletObject o2, double targetDistance, int particleAmount) {
        this.particleAmount = particleAmount;
        this.player = player;

        this.object1 = o1;
        this.color = o1.getColor();
        this.particleSize = o1.getSize();
        this.dustOptions = new Particle.DustOptions(this.color, this.particleSize);
        this.object2 = o2;
        this.targetDistance = targetDistance;
        this.sqrTargetDistance = this.targetDistance * this.targetDistance;
    }

    public void apply() {
        if (object1.loc.equals(object2.loc))
            object1.loc.add(0, 0.0005, 0);

        double mass1 = object1.mass;
        double mass2 = object2.mass;

        Vector delta = object2.loc.toVector().subtract(object1.loc.toVector());
        double multiplier = this.sqrTargetDistance / (delta.lengthSquared() + this.sqrTargetDistance) - 0.5;
        delta.multiply(multiplier * 2 / (mass1 + mass2));

        if (!object1.isPinned)
            object1.loc.subtract(delta.clone().multiply(mass2));
        if (!object2.isPinned)
            object2.loc.add(delta.clone().multiply(mass1));
    }

    
    private void apply2() {
        Vector delta = this.object2.loc.toVector().subtract(this.object1.loc.toVector());
        double distance = delta.length();
        double diff = this.targetDistance - distance;
        double percent = (diff / distance) / 2;

        Vector offset = delta.clone().multiply(percent);
        if (!this.object1.isPinned)
            this.object1.loc.subtract(offset);

        if (!this.object2.isPinned)
            this.object2.loc.add(offset);
    }

    
    public void display2() {
        if (this.particleAmount < 2)
            return;

        Vector dir = object2.loc.toVector().subtract(object1.loc.toVector());
        double length = dir.length();
        dir.multiply(1.0 / this.particleAmount);
        Location tmpLoc = object1.loc.clone();
        for (int i = 0; i < this.particleAmount - 1; i++) {
            tmpLoc.add(dir);
            player.getWorld().spawnParticle(Particle.DUST, tmpLoc, 1, 0, 0, 0, this.dustOptions);
        }
    }

    public void display() {
        if (this.display == null) {
            this.display = (BlockDisplay) player.getWorld().spawnEntity(this.object1.loc, EntityType.BLOCK_DISPLAY);
            this.display.setBlock(this.mat.createBlockData());
            this.display.setPersistent(false);
            
        }

        Vector dir = object2.loc.toVector().subtract(object1.loc.toVector());
        
        
        
        
        

        Location tmpLoc = object1.loc.clone().setDirection(dir);
        float pitch = (float) Math.toRadians(tmpLoc.getPitch() + 90);
        float yaw = (float) Math.toRadians(-tmpLoc.getYaw());

        Vector3f scale = new Vector3f(1, 0.5f, 1);
        Vector scaleVal = new Vector(1, 1, 1);
        if (this.rope != null) {
            float len = (float) this.rope.verletObjectAmount;
            Vector step = this.rope.maxScale.clone().subtract(this.rope.minScale).multiply(1.0 / len);
            scaleVal = this.rope.maxScale.clone().subtract(step.clone().multiply(this.object1.ropeId));

            scaleVal.setY(this.object2.loc.toVector().subtract(this.object1.loc.toVector()).length());
            
            
            scale = scaleVal.toVector3f();
        }

        this.display.setInterpolationDelay(0);
        this.display.setInterpolationDuration(1);
        this.display.setTeleportDuration(1);
        Transformation transformation = new Transformation(
                centerChainOffset(pitch, yaw, scaleVal),
                new Quaternionf().rotateLocalX(pitch).rotateLocalY(yaw),
                scale,
                new Quaternionf()
        );

        this.display.setTransformation(transformation);
        this.object1.loc.setDirection(new Vector(-0, -0, 1));
        this.display.teleport(this.object1.loc);
    }

    public static Vector3f centerChainOffset(double pitch, double yaw, Vector scale) {
        Vector vector = new Vector(0.5 * scale.getX(), 0, 0.5 * scale.getZ());

        double oldX = vector.getX();
        double oldY = vector.getY();
        double oldZ = vector.getZ();
        vector.setY(oldY * Math.cos(pitch) - oldZ * Math.sin(pitch));
        vector.setZ(oldY * Math.sin(pitch) + oldZ * Math.cos(pitch));
        oldY = vector.getY();
        oldZ = vector.getZ();
        vector.setX(oldX * Math.cos(yaw) + oldZ * Math.sin(yaw));
        vector.setZ(-oldX * Math.sin(yaw) + oldZ * Math.cos(yaw));

        return vector.multiply(-1).toVector3f();
    }

    public void setSize(float size) {
        if (this.particleSize == size)
            return;

        this.setShape(this.color, size);
    }

    public float getSize() {
        return this.particleSize;
    }

    protected void setColor(int r, int g, int b) {
        if (this.color == Color.fromRGB(r, g, b))
            return;

        this.setShape(Color.fromRGB(r, g, b), this.particleSize);
    }

    protected void setColorAndSize(int r, int g, int b, float size) {
        if (this.color == Color.fromRGB(r, g, b) && this.particleSize == size)
            return;

        this.setShape(Color.fromRGB(r, g, b), size);
    }

    private void setShape(Color color, float size) {
        this.color = color;
        this.particleSize = size;
        this.dustOptions = new Particle.DustOptions(color, size);
    }

    protected void destroy() {
        if (this.display != null)
            this.display.remove();
    }

}
