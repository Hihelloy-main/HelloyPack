package com.hihelloy.work.lib.verlet;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class VerletObject {
    private Player player;
    private Particle.DustOptions dustOptions;
    private float size;
    private Color color;

    protected Location loc;
    protected Location previousLoc;
    private Vector force;
    protected double mass;
    protected boolean isPinned;
    protected int ropeId = Integer.MIN_VALUE;

    public VerletObject(Player player, Location startLoc, double mass, boolean isPinned, int ropeId) {
        this.size = 0.5f;
        
        this.color = Color.fromRGB(0, 255, 0);
        this.dustOptions = new Particle.DustOptions(this.color, this.size);

        this.player = player;
        this.loc = startLoc;
        this.previousLoc = loc.clone();
        this.force = new Vector(0, 0, 0);
        this.mass = mass;
        this.isPinned = isPinned;
        this.ropeId = ropeId;
    }

    public VerletObject(Player player, Location startLoc, double mass, boolean isPinned) {
        this.size = 0.5f;
        
        this.color = Color.fromRGB(0, 255, 0);
        this.dustOptions = new Particle.DustOptions(this.color, this.size);

        this.player = player;
        this.loc = startLoc;
        this.previousLoc = loc.clone();
        this.force = new Vector(0, 0, 0);
        this.mass = mass;
        this.isPinned = isPinned;
    }

    protected void updatePosition(double dt) {
        if (this.isPinned)
            return;

        Vector vel = this.loc.toVector().subtract(this.previousLoc.toVector());

        Vector dampingForce = vel.clone().multiply(-0.05);
        Vector tmpForce = this.force.clone().add(dampingForce);

        Vector acc = tmpForce.multiply(1 / mass);
        this.previousLoc = this.loc.clone();
        this.loc.add(vel.add(acc.multiply(dt * dt)));
    }

    protected void setForce(Vector force) {
        this.force = force;
    }

    protected void display() {
        
    }

    public void setSize(float size) {
        if (this.size == size)
            return;

        this.setShape(this.color, size);
    }

    public float getSize() {
        return this.size;
    }

    protected void setColor(int r, int g, int b) {
        if (this.color == Color.fromRGB(r, g, b))
            return;

        this.setShape(Color.fromRGB(r, g, b), this.size);
    }

    protected void setColorAndSize(int r, int g, int b, float size) {
        if (this.color == Color.fromRGB(r, g, b) && this.size == size)
            return;

        this.setShape(Color.fromRGB(r, g, b), size);
    }

    private void setShape(Color color, float size) {
        this.color = color;
        this.size = size;
        this.dustOptions = new Particle.DustOptions(color, size);
    }

    public Location getLocationClone() {
        return this.loc.clone();
    }

    protected void setLocation(Location loc) {
        this.loc = loc.clone();
        this.previousLoc = loc.clone();
    }

    public void setIsPinned(boolean isPinned) {
        this.isPinned = isPinned;
    }

    public Color getColor() {
        return this.color;
    }
}
