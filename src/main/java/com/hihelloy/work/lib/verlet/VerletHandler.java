package com.hihelloy.work.lib.verlet;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedList;

public class VerletHandler {

    private final LinkedList<VerletObject> verletObjects;
    private final ArrayList<Link> links;
    private final Player player;

    private final int subSteps;
    private final double sqrCollisiunRadius;

    private Vector gravity;

    public VerletHandler(Player player) {
        this.verletObjects = new LinkedList<>();
        this.links = new ArrayList<>();
        this.player = player;
        this.gravity = new Vector(0, 0, 0);

        this.subSteps = 12;
        double collusionRadius = 0.1;
        this.sqrCollisiunRadius = collusionRadius * collusionRadius;
    }

    public void update() {
        this.updatePositions(1);
        for (int i = 0; i < subSteps; i++) {
            
            
            this.applyLinkConstraints();
            this.applyBlockConstraints();
        }
        
    }

    public void display() {
        this.verletObjects.forEach(vo -> vo.display());
        this.links.forEach(link -> link.display());
    }

    private void updatePositions(double dt) {
        this.verletObjects.forEach(object -> object.updatePosition(dt));
    }

    private void applyLinkConstraints() {
        this.links.forEach(link -> link.apply());
    }

    private void applyBlockConstraints() {
        for (VerletObject vo : this.verletObjects) {
            if (vo.loc.getBlock().isSolid()) {
                this.solveBlockConstraint(vo);
            }
        }
    }

    private void solveBlockConstraint(VerletObject vo) {
        if (vo.previousLoc.equals(vo.loc)) {
            return;
        }

        Vector dir = vo.previousLoc.toVector().subtract(vo.loc.toVector()).normalize().multiply(0.1);
        Location tmpLoc = vo.loc.clone();
        int i = 0;
        Block currentBlock = tmpLoc.getBlock();
        Block prevBlock;
        do {
            prevBlock = currentBlock;
            tmpLoc.add(dir);
            currentBlock = tmpLoc.getBlock();
            i++;
        } while (i < 50 && (currentBlock.isSolid()));

        if (i >= 50) {
            vo.previousLoc = vo.loc.clone();
            return;
        }

        
        double offset = 0.025;
        if (currentBlock.getY() < prevBlock.getY()) {
            
            vo.loc.setY(prevBlock.getY() - offset);
            
        } else if (currentBlock.getY() > prevBlock.getY()) {
            
            vo.loc.setY(prevBlock.getY() + (1 + offset));
            
        }
        else if (currentBlock.getX() < prevBlock.getX()) {
            
            vo.loc.setX(prevBlock.getX() - offset);
            
        } else if (currentBlock.getX() > prevBlock.getX()) {
            
            vo.loc.setX(prevBlock.getX() + (1 + offset));
            
        }
        else if (currentBlock.getZ() < prevBlock.getZ()) {
            
            vo.loc.setZ(prevBlock.getZ() - offset);
            
        } else if (currentBlock.getZ() > prevBlock.getZ()) {
            
            vo.loc.setZ(prevBlock.getZ() + (1 + offset));
            
        }
    }

    private void solveObjectCollisions() {
        this.verletObjects.forEach(vo -> this.solveObjectCollision(vo));
    }

    private void solveObjectCollision(VerletObject current) {
        if (current.isPinned)
            return;

        if (current.loc.getBlock().isSolid())
            return;

        for (VerletObject vo : this.verletObjects) {
            if (!vo.equals(current) && vo.loc.distanceSquared(current.loc) < this.sqrCollisiunRadius) {
                if (vo.loc.equals(current.loc)) {
                    vo.loc.add(0.001, 0, 0);
                    continue;
                }
                Vector delta = vo.loc.toVector().subtract(current.loc.toVector());
                double multiplier = this.sqrCollisiunRadius / (delta.lengthSquared() + this.sqrCollisiunRadius) - 0.5;
                delta.multiply(multiplier);
                current.loc.subtract(delta);
                vo.loc.add(delta);
            }
        }
    }

    public void setForceAll(Vector force) {
        this.verletObjects.forEach(vo -> vo.setForce(force.clone().add(this.gravity)));
    }

    public void setForce(VerletObject vo, Vector force) {
        if (!this.verletObjects.contains(vo))
            return;

        this.verletObjects.get(this.verletObjects.indexOf(vo)).setForce(force.clone().add(this.gravity));
    }

    public void setForce(int i, Vector force) {
        if (this.verletObjects.size() <= i)
            return;

        this.verletObjects.get(i).setForce(force.clone().add(this.gravity));
    }

    public void setLocation(int i, Location loc) {
        if (this.verletObjects.size() <= i)
            return;

        this.verletObjects.get(i).setLocation(loc);
    }

    public void addVerletObject(VerletObject vo) {
        if (this.verletObjects.contains(vo))
            return;
        this.verletObjects.add(vo);
    }

    public void removeVerletObject(VerletObject vo) {
        this.verletObjects.remove(vo);
    }

    public void addLink(Link link) {
        if (this.links.contains(link))
            return;
        this.links.add(link);
    }

    public void removeLink(Link link) {
        link.destroy();
        this.links.remove(link);
    }

    public void removeLink(VerletObject o1, VerletObject o2) {
        for (int i = this.links.size() - 1; i >= 0; i--) {
            Link link = this.links.get(i);
            if ((link.object1.equals(o1) && link.object2.equals(o2)) || (link.object1.equals(o2) && link.object2.equals(o1))) {
                this.removeLink(link);
                return;
            }
        }
    }

    public void removeLinks(VerletObject vo) {
        for (int i = this.links.size() - 1; i >= 0; i--) {
            Link link = this.links.get(i);
            if (link.object1.equals(vo) || link.object2.equals(vo)) {
                this.removeLink(link);
            }
        }
    }

    public Vector getGravity() {
        return this.gravity.clone();
    }
    public void setGravity(Vector gravity) {
        this.gravity = gravity;
    }
}
