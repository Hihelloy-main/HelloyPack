package com.hihelloy.work.lib.verlet;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.LinkedList;

public class VerletRope {

    private LinkedList<VerletObject> objects;
    private Player player;
    private VerletHandler vh;

    private Location startLocation;
    protected int verletObjectAmount;
    private double linkLenght;
    private int linkParticleAmount;
    private double ropeLength;
    private Color color;
    private float particleSize;

    private boolean isStartPinned;
    private boolean isEndPinned;
    protected Vector maxScale;
    protected Vector minScale;
    private Material mat;

    public VerletRope(VerletHandler vh, Player player, Location startLocation, double ropeLength,
                      int verletObjectAmount, int linkParticleAmount, Color color, float particleSize,
                      boolean isStartPinned, boolean isEndPinned, Vector maxScale, Vector minScale, Material mat) {
        this.objects = new LinkedList<>();
        this.player = player;
        this.vh = vh;
        this.isStartPinned = isStartPinned;
        this.isEndPinned = isEndPinned;
        this.maxScale = maxScale;
        this.minScale = minScale;
        this.mat = mat;

        this.startLocation = startLocation;
        this.ropeLength = ropeLength;
        verletObjectAmount = (verletObjectAmount < 2) ? 2 : verletObjectAmount;
        this.linkLenght = this.ropeLength / verletObjectAmount;
        this.linkParticleAmount = linkParticleAmount;

        this.color = color;
        this.particleSize = particleSize;

        VerletObject prev = new VerletObject(player, this.startLocation, 1, this.isStartPinned, 0);
        prev.setColorAndSize(color.getRed(), color.getGreen(), color.getBlue(), this.particleSize);
        this.addVerletObject(prev, false);
        Vector offset = new Vector(0, 0.0005, 0);
        Vector inc = offset.clone();
        for (int i = 1; i < verletObjectAmount; i++) {
            VerletObject current = new VerletObject(player, this.startLocation.clone().add(offset), 1, false, i);
            current.setColorAndSize(color.getRed(), color.getGreen(), color.getBlue(), this.particleSize);
            this.addVerletObject(current, false);
            this.vh.addLink(new Link(player, prev, current, this.linkLenght, this.linkParticleAmount, this, this.mat));
            prev = current;
            offset.add(inc);
        }
        this.objects.getLast().setIsPinned(this.isEndPinned);
    }

    public void moveEndPoint(Location loc) {
        VerletObject vo = this.objects.getLast();
        if (!vo.isPinned)
            return;
        vo.setLocation(loc);
    }

    public void moveStartPoint(Location loc) {
        VerletObject vo = this.objects.getFirst();
        if (!vo.isPinned)
            return;
        vo.setLocation(loc);
    }

    public void setRopeLength(double ropeLength, boolean editFromStart) {
        if (ropeLength < 2 * this.linkLenght)
            ropeLength = 2 * this.linkLenght;

        int targetVerletObjectAmount = (int) (ropeLength / this.linkLenght);
        int verletObjectChangeAmount = targetVerletObjectAmount - this.verletObjectAmount;

        if (verletObjectChangeAmount > 0) {
            Vector offset;
            Vector inc = new Vector(0, 0.0005, 0);
            if (editFromStart) {
                offset = inc.clone();
                if (this.isStartPinned)
                    this.objects.getFirst().setIsPinned(false);
                while (verletObjectChangeAmount > 0) {
                    VerletObject terminal = this.objects.getFirst();
                    VerletObject vo = new VerletObject(player, terminal.loc.clone().add(offset), 1, false, terminal.ropeId - 1);
                    if (this.vh.getGravity().lengthSquared() != 0)
                        vo.setForce(this.vh.getGravity());
                    this.addVerletObject(vo, true);
                    this.vh.addLink(new Link(player, vo, terminal, this.linkLenght, this.linkParticleAmount,this, this.mat));
                    offset.add(inc);
                    verletObjectChangeAmount--;
                }
                int ropeIdShift = -this.objects.getFirst().ropeId;
                for (VerletObject vo : this.objects) {
                    vo.ropeId += ropeIdShift;
                }
                if (this.isStartPinned)
                    this.objects.getFirst().setIsPinned(true);
            } else {
                offset = inc.clone();
                if (this.isEndPinned)
                    this.objects.getLast().setIsPinned(false);
                while (verletObjectChangeAmount > 0) {
                    VerletObject terminal = this.objects.getLast();
                    VerletObject vo = new VerletObject(player, terminal.loc.clone().add(offset), 1, false, terminal.ropeId + 1);
                    if (this.vh.getGravity().lengthSquared() != 0)
                        vo.setForce(this.vh.getGravity());
                    this.vh.addLink(new Link(player, terminal, vo, this.linkLenght, this.linkParticleAmount, this, this.mat));
                    this.addVerletObject(vo, false);
                    offset.add(inc);
                    verletObjectChangeAmount--;
                }
                if (this.isEndPinned)
                    this.objects.getLast().setIsPinned(true);
            }
        } else {
            if (editFromStart) {
                while (verletObjectChangeAmount < 0) {
                    this.removeVerletObject(this.objects.getFirst());
                    verletObjectChangeAmount++;
                }
                int ropeIdShift = -this.objects.getFirst().ropeId;
                for (VerletObject vo : this.objects) {
                    vo.ropeId += ropeIdShift;
                }
                if (this.isStartPinned)
                    this.objects.getFirst().setIsPinned(true);
            } else {
                while (verletObjectChangeAmount < 0) {
                    this.removeVerletObject(this.objects.getLast());
                    verletObjectChangeAmount++;
                }
                if (this.isEndPinned)
                    this.objects.getLast().setIsPinned(true);
            }
        }

        this.ropeLength = ropeLength;
    }

    public double getRopeLength() {
        return this.ropeLength;
    }

    public double getSqrRopeLength() {
        return this.ropeLength * this.ropeLength;
    }

    private void addVerletObject(VerletObject vo, boolean addToStart) {
        vo.setColorAndSize(this.color.getRed(), this.color.getGreen(), this.color.getBlue(), this.particleSize);
        if (addToStart)
            this.objects.addFirst(vo);
        else
            this.objects.addLast(vo);
        this.vh.addVerletObject(vo);
        this.verletObjectAmount++;
    }

    private void removeVerletObject(VerletObject vo) {
        this.objects.remove(vo);
        this.vh.removeLinks(vo);
        this.vh.removeVerletObject(vo);
        this.verletObjectAmount--;
    }

    
    public void unfurl(double unfurlSpeed, double maxLength, boolean editFromStart) {
        double ropeLength = this.getRopeLength();
        if (ropeLength < maxLength) {
            ropeLength += unfurlSpeed;
            ropeLength = Math.min(ropeLength, maxLength);
            this.setRopeLength(ropeLength, editFromStart);
        }
    }

    
    public void furl(double furlSpeed, double minLength, boolean editFromStart) {
        double ropeLength = this.getRopeLength();
        if (ropeLength > minLength) {
            ropeLength -= furlSpeed;
            ropeLength = Math.max(ropeLength, minLength);
            this.setRopeLength(ropeLength, editFromStart);
        }
    }

    public VerletObject getSecondLastObject() {
        return this.objects.get(this.objects.size() - 2);
    }
    public VerletObject getLastObject() {
        return this.objects.getLast();
    }

    public VerletObject getFirstObject() {
        return this.objects.getFirst();
    }

    public Location getStartLocation() {
        return this.objects.getFirst().getLocationClone();
    }

    public Location getEndLocation() {
        return this.objects.getLast().getLocationClone();
    }

    public boolean getIsEndPinned() {
        return this.isEndPinned;
    }

    public Vector getMinScale() {
        return this.minScale;
    }

    public void destroy() {
        for (int i = this.objects.size() - 1; i >= 0; i--) {
            VerletObject vo = this.objects.get(i);
            this.removeVerletObject(vo);
        }
    }

}
