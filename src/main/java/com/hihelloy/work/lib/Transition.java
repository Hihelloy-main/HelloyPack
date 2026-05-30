package com.hihelloy.work.lib;

import org.bukkit.util.Vector;

public class Transition {

    private double x;
    private double y;
    private double z;
    private Vector steps;
    private Vector ticks;
    private Vector delays;

    public Transition(Vector from, Vector to, Vector ticks, Vector delays) {
        this.ticks = ticks;
        this.delays = delays;

        this.x = from.getX();
        this.y = from.getY();
        this.z = from.getZ();

        this.steps = new Vector((to.getX() - from.getX()) / this.ticks.getX(),
                (to.getY() - from.getY()) / this.ticks.getY(),
                (to.getZ() - from.getZ()) / this.ticks.getZ());
    }

    public void update() {
        if (this.delays.getX() > 0) {
            this.delays.setX(this.delays.getX() - 1);
        } else if (this.ticks.getX() > 0) {
            this.x += this.steps.getX();
            this.ticks.setX(this.ticks.getX() - 1);
        }

        if (this.delays.getY() > 0) {
            this.delays.setY(this.delays.getY() - 1);
        } else if (this.ticks.getY() > 0) {
            this.y += this.steps.getY();
            this.ticks.setY(this.ticks.getY() - 1);
        }

        if (this.delays.getZ() > 0) {
            this.delays.setZ(this.delays.getZ() - 1);
        } else if (this.ticks.getZ() > 0) {
            this.z += this.steps.getZ();
            this.ticks.setZ(this.ticks.getZ() - 1);
        }
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

}
