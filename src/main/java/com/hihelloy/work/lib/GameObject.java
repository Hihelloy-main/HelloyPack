package com.hihelloy.work.lib;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class GameObject {

    protected Player player;
    protected GameObject parent;

    protected Location location;
    protected Vector scale;
    protected Vector rotation;
    protected Vector translation;

    protected Matrix4f matrix;

    public enum DisplayMode {
        BLOCK_DISPLAY,
        TEXT_DISPLAY,
        NONE
    }
    protected DisplayMode displayMode;
    protected boolean renderBothSides;

    public enum BlockRotationMode {
        CENTER,
        EDGE
    }
    protected BlockRotationMode rotationMode;

    protected BlockDisplay blockDisplay;
    protected TextDisplay textDisplay;
    protected TextDisplay textDisplayBack;

    public GameObject(Player player, DisplayMode mode, Location location, Vector scale, Vector rotation, Vector translation) {
        this(player, mode, null, location, scale, rotation, translation);
    }

    public GameObject(Player player, DisplayMode mode, GameObject parent, Vector scale, Vector rotation, Vector translation) {
        this(player, mode, parent, null, scale, rotation, translation);
    }

    private GameObject(Player player, DisplayMode mode, GameObject parent, Location location, Vector scale, Vector rotation, Vector translation) {
        this.player = player;
        this.displayMode = mode;
        this.parent = parent;
        this.renderBothSides = false;
        this.rotationMode = BlockRotationMode.CENTER;

        if (this.parent == null)
            this.location = location.clone().setDirection(new Vector(0, 0, 1));
        else
            this.location = parent.getLocation();
        this.scale = scale.clone();
        this.rotation = rotation.clone();
        this.translation = translation.clone();

        if (this.displayMode == DisplayMode.BLOCK_DISPLAY) {
            this.blockDisplay = (BlockDisplay) player.getWorld().spawnEntity(this.location, EntityType.BLOCK_DISPLAY);
            this.blockDisplay.setBlock(Material.GLASS.createBlockData());
            this.blockDisplay.setPersistent(false);
            this.blockDisplay.setInterpolationDelay(0);
            this.blockDisplay.setInterpolationDuration(1);
            this.blockDisplay.setTeleportDuration(1);
        } else if (this.displayMode == DisplayMode.TEXT_DISPLAY) {
            this.textDisplay = (TextDisplay) player.getWorld().spawnEntity(this.location, EntityType.TEXT_DISPLAY);
            this.textDisplay.setBackgroundColor(Color.fromARGB(128, 0, 255, 0));
            this.textDisplay.setText(" ");
            this.textDisplay.setLineWidth(Integer.MAX_VALUE);
            this.textDisplay.setPersistent(false);
            this.textDisplay.setInterpolationDelay(0);
            this.textDisplay.setInterpolationDuration(1);
            this.textDisplay.setTeleportDuration(1);
            if (this.renderBothSides) {
                this.textDisplayBack = (TextDisplay) player.getWorld().spawnEntity(this.location, EntityType.TEXT_DISPLAY);
                this.textDisplayBack.setBackgroundColor(Color.fromARGB(128, 0, 255, 0));
                this.textDisplayBack.setText(" ");
                this.textDisplayBack.setLineWidth(Integer.MAX_VALUE);
                this.textDisplayBack.setPersistent(false);
                this.textDisplayBack.setInterpolationDelay(0);
                this.textDisplayBack.setInterpolationDuration(1);
                this.textDisplayBack.setTeleportDuration(1);
            }
        }
    }

    public void updateAndDisplay() {
        Matrix4f fixedDisplayOffsets = new Matrix4f();
        if (this.displayMode == DisplayMode.BLOCK_DISPLAY) {
            float z = this.rotationMode == BlockRotationMode.CENTER ? -0.5f : 0f;
            fixedDisplayOffsets = new Matrix4f()
                    .translate(-0.5f, -0.5f, z);            
        } else if (this.displayMode == DisplayMode.TEXT_DISPLAY) {
            fixedDisplayOffsets = new Matrix4f()
                    .translate(-0.5f, -0.5f, 0f)                
                    .translate(-0.1f + 0.5f, -0.5f + 0.5f, 0)   
                    .scale(8, 4, 1);                            
        }

        Quaternionf rot = new Quaternionf()
                .rotateY((float) this.rotation.getY())
                .rotateX((float) this.rotation.getX())
                .rotateZ((float) this.rotation.getZ());

        Matrix4f parentMatrix = new Matrix4f();
        if (this.parent != null) {
            this.location = parent.getLocation();
            parentMatrix = new Matrix4f(this.parent.getMatrix());

            
            if (parent.displayMode == DisplayMode.BLOCK_DISPLAY) {
                float z = parent.rotationMode == BlockRotationMode.CENTER ? 0.5f : 0f;
                parentMatrix.translate(0.5f, 0.5f, z);
            } else if (parent.displayMode == DisplayMode.TEXT_DISPLAY) {
                parentMatrix
                        .scale(1/8f, 1/4f, 1f)
                        .translate(0.1f - 0.5f, 0.5f - 0.5f, 0)
                        .translate(0.5f, 0.5f, 0f);
            }
        }

        
        this.matrix = new Matrix4f(parentMatrix)
                .rotate(rot)
                .translate((float) this.translation.getX(), (float) this.translation.getY(), (float) this.translation.getZ())
                .scale((float) this.scale.getX(), (float) this.scale.getY(), (float) this.scale.getZ())
                .mul(fixedDisplayOffsets);

        if (this.displayMode == DisplayMode.BLOCK_DISPLAY) {
            Transformation oldTransform = this.blockDisplay.getTransformation();
            this.blockDisplay.setTransformationMatrix(matrix);
            if (!oldTransform.equals(this.blockDisplay.getTransformation()))
                this.blockDisplay.setInterpolationDelay(0);
            this.blockDisplay.teleport(this.location);
        } else if (this.displayMode == DisplayMode.TEXT_DISPLAY) {
            Transformation oldTransform = this.textDisplay.getTransformation();
            this.textDisplay.setTransformationMatrix(matrix);
            if (!oldTransform.equals(this.textDisplay.getTransformation()))
                this.textDisplay.setInterpolationDelay(0);
            this.textDisplay.teleport(this.location);
            if (this.renderBothSides) {
                Matrix4f backMatrix = new Matrix4f(parentMatrix)
                        .rotate(rot)
                        .scale((float) this.scale.getX(), (float) this.scale.getY(), (float) this.scale.getZ())
                        .translate((float) this.translation.getX(), (float) this.translation.getY(), (float) this.translation.getZ())
                        .rotateY((float) Math.PI)
                        .mul(fixedDisplayOffsets);

                oldTransform = this.textDisplayBack.getTransformation();
                this.textDisplayBack.setTransformationMatrix(backMatrix);
                if (!oldTransform.equals(this.textDisplayBack.getTransformation()))
                    this.textDisplayBack.setInterpolationDelay(0);
                this.textDisplayBack.teleport(this.location);
            }
        }
        
    }

    public Location getLocation() {
        return this.location.clone();
    }

    public Matrix4f getMatrix() {
        return this.matrix;
    }

    public Vector getRotation() {
        return this.rotation.clone();
    }

    public void setRotation(Vector rotation) {
        this.rotation = rotation.clone();
    }

    public void setTranslation(Vector translation) {
        this.translation = translation.clone();
    }

    public void setScale(Vector scale) {
        this.scale = scale.clone();
    }

    public Vector getScale() {
        return this.scale.clone();
    }

    public void setLocation(Location location) {
        if (this.parent == null)
            this.location = location.clone().setDirection(new Vector(0, 0, 1));
    }

    public void setBlockRotationMode(BlockRotationMode mode) {
        this.rotationMode = mode;
    }

    public void setBlockMaterial(Material mat) {
        this.blockDisplay.setBlock(mat.createBlockData());
    }

    public void destroy() {
        if (this.blockDisplay != null)
            this.blockDisplay.remove();
        if (this.textDisplay != null)
            this.textDisplay.remove();
        if (this.textDisplayBack != null)
            this.textDisplayBack.remove();
    }

}
