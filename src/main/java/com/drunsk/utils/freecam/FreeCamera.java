package com.drunsk.utils.freecam;

import com.drunsk.DrunskConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec2;

import java.util.UUID;

/**
 * Fake player entity that carries the detached camera.
 * 1:1 port of net.xolt.freecam.util.FreeCamera (MIT), trimmed of stonecutter
 * branches for MC 26.2. / Фейковый игрок, несущий отсоединённую камеру.
 * Порт net.xolt.freecam.util.FreeCamera (MIT) под 26.2.
 */
public class FreeCamera extends AbstractClientPlayer {

    private static final Minecraft MC = Minecraft.getInstance();

    public ClientInput input;
    public float yBob;
    public float xBob;
    public float yBobO;
    public float xBobO;

    public FreeCamera(int id) {
        super(MC.level, new GameProfile(UUID.randomUUID(), "FreeCamera"));

        setId(id);
        setPose(Pose.SWIMMING);
        getAbilities().flying = true;
        input = new KeyboardInput(MC.options);
    }

    @Override
    public void tick() {
        input.tick();
        doMotion();
        super.tick();
    }

    @Override
    public void copyPosition(Entity entity) {
        applyPosition(new FreecamPosition(entity));
    }

    public void applyPosition(FreecamPosition position) {
        snapTo(position.x, position.y, position.z, position.yaw, position.pitch);
        xBob = getXRot();
        yBob = getYRot();
        // prevents the camera from rotating upon entering freecam
        // / без этого камера дёргается при входе во фрикам
        xBobO = xBob;
        yBobO = yBob;
    }

    /** Mutate position/rotation based on perspective; stop before collision if asked. */
    public void applyPerspective(Perspective perspective, boolean checkCollision) {
        FreecamPosition position = new FreecamPosition(this);

        switch (perspective) {
            case INSIDE:
                break;
            case FIRST_PERSON:
                moveForwardUntilCollision(position, 0.4, checkCollision);
                break;
            case THIRD_PERSON_MIRROR:
                position.mirrorRotation();
                // fallthrough / проваливаемся
            case THIRD_PERSON:
                moveForwardUntilCollision(position, -4.0, checkCollision);
                break;
        }
    }

    private boolean moveForwardUntilCollision(FreecamPosition position, double distance, boolean checkCollision) {
        if (!checkCollision) {
            position.moveForward(distance);
            applyPosition(position);
            return true;
        }
        return moveForwardUntilCollision(position, distance);
    }

    private boolean moveForwardUntilCollision(FreecamPosition position, double maxDistance) {
        boolean negative = maxDistance < 0;
        maxDistance = negative ? -1 * maxDistance : maxDistance;
        double increment = 0.1;

        for (double distance = 0.0; distance < maxDistance; distance += increment) {
            FreecamPosition oldPosition = new FreecamPosition(this);

            position.moveForward(negative ? -1 * increment : increment);
            applyPosition(position);

            if (!wouldNotSuffocateAtTargetPose(getPose())) {
                applyPosition(oldPosition);
                return distance > 0;
            }
        }

        return true;
    }

    private ClientLevel getClientLevel() {
        return (ClientLevel) level();
    }

    public void spawn() {
        getClientLevel().addEntity(this);
    }

    public void despawn() {
        getClientLevel().removeEntity(getId(), RemovalReason.DISCARDED);
    }

    // no fall-damage sound when touching ground with noClip disabled
    @Override
    protected void checkFallDamage(double heightDifference, boolean onGround, BlockState landedState, BlockPos landedPosition) {
    }

    // hand-swing animations follow the real player
    @Override
    public float getAttackAnim(float partialTick) {
        return MC.player == null ? 0 : MC.player.getAttackAnim(partialTick);
    }

    // item-use animations follow the real player
    @Override
    public int getUseItemRemainingTicks() {
        return MC.player == null ? 0 : MC.player.getUseItemRemainingTicks();
    }

    @Override
    public boolean isUsingItem() {
        return MC.player != null && MC.player.isUsingItem();
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public boolean isInWater() {
        return false;
    }

    // night vision applies to the camera entity too (Iris compat in xolt)
    @Override
    public MobEffectInstance getEffect(Holder<MobEffect> effect) {
        return MC.player == null ? null : MC.player.getEffect(effect);
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return false;
    }

    @Override
    public void setPose(Pose pose) {
        super.setPose(Pose.SWIMMING);
    }

    @Override
    protected boolean updateIsUnderwater() {
        this.wasUnderwater = this.isEyeInFluid(FluidTags.WATER);
        return this.wasUnderwater;
    }

    @Override
    protected void doWaterSplashEffect() {
    }

    private void doMotion() {
        // DEFAULT flight mode: direct motion, no abilities speed
        // / режим полёта DEFAULT: прямая скорость, без abilities
        getAbilities().setFlyingSpeed(0);
        Motion.doMotion(this, DrunskConfig.util.freecamSpeedH, DrunskConfig.util.freecamSpeedV);
        getAbilities().flying = true;
        setOnGround(false);
    }

    @Override
    public float getViewXRot(float partialTick) {
        return this.getXRot();
    }

    @Override
    public float getViewYRot(float partialTick) {
        return this.getYRot();
    }

    @Override
    public boolean isEffectiveAi() {
        return true;
    }

    // enables movement ticking on the client / включает клиентский тик движения
    @Override
    public boolean canSimulateMovement() {
        return true;
    }

    @Override
    protected void applyInput() {
        Vec2 vec2 = this.input.getMoveVector();
        if (vec2.lengthSquared() != 0.0F) {
            vec2 = vec2.scale(0.98F);
        }
        applyInputHelper(vec2, this.input.keyPresses.jump());
    }

    private void applyInputHelper(Vec2 moveVector, boolean jumping) {
        this.xxa = moveVector.x;
        this.zza = moveVector.y;
        this.jumping = jumping;
        this.setSprinting((MC.options.keySprint.isDown() && this.input.keyPresses.forward())
                || (this.input.keyPresses.forward() && this.isSprinting()));
        this.yBobO = this.yBob;
        this.xBobO = this.xBob;
        this.xBob = this.xBob + (this.getXRot() - this.xBob) * 0.5F;
        this.yBob = this.yBob + (this.getYRot() - this.yBob) * 0.5F;
    }
}
