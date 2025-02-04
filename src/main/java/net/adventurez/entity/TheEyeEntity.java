package net.adventurez.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import net.adventurez.entity.nonliving.TinyEyeEntity;
import net.adventurez.init.EffectInit;
import net.adventurez.init.EntityInit;
import net.adventurez.init.SoundInit;
import net.adventurez.init.TagInit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.FollowTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.FlyingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.voidz.init.BlockInit;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

public class TheEyeEntity extends FlyingEntity {
    private static final TrackedData<Integer> BEAM_TARGET_ID;
    public static final TrackedData<Integer> INVUL_TIMER;
    private int field_7082;
    private final ServerBossBar bossBar;
    private LivingEntity cachedBeamTarget;
    private int deathTimer = 0;
    private boolean gotDamage;
    private int attackTpCounter;

    public TheEyeEntity(EntityType<? extends TheEyeEntity> entityType, World world) {
        super(entityType, world);
        this.bossBar = (ServerBossBar) (new ServerBossBar(this.getDisplayName(), BossBar.Color.PURPLE, BossBar.Style.PROGRESS));
        this.experiencePoints = 80;
        this.moveControl = new TheEyeEntity.EyeMoveControl(this);
    }

    public static DefaultAttributeContainer.Builder createTheEntityAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 800.0D).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35D)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 60.0D).add(EntityAttributes.GENERIC_ARMOR, 5.0D);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new TheEyeEntity.ShootBulletGoal());
        this.goalSelector.add(1, new TheEyeEntity.FireBeamGoal(this));
        this.goalSelector.add(3, new TheEyeEntity.FlyRandomlyGoal(this));
        this.goalSelector.add(2, new TheEyeEntity.LookAtTargetGoal(this));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new FollowTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(INVUL_TIMER, 0);
        this.dataTracker.startTracking(BEAM_TARGET_ID, 0);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound tag) {
        super.writeCustomDataToNbt(tag);
        tag.putInt("Invul", this.getInvulnerableTimer());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound tag) {
        super.readCustomDataFromNbt(tag);
        this.setInvulTimer(tag.getInt("Invul"));
        if (this.hasCustomName()) {
            this.bossBar.setName(this.getDisplayName());
        }

    }

    @Override
    public void setCustomName(@Nullable Text name) {
        super.setCustomName(name);
        this.bossBar.setName(this.getDisplayName());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundInit.EYE_IDLE_EVENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundInit.EYE_HURT_EVENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundInit.EYE_DEATH_EVENT;
    }

    @Override
    public float getActiveEyeHeight(EntityPose pose, EntityDimensions dimensions) {
        return 2.1F;
    }

    @Override
    public void tickMovement() {
        Vec3d vec3d = this.getVelocity().multiply(0.8D, 0.5D, 0.8D);
        if (!this.world.isClient && this.getTarget() != null) {
            Entity entity = this.getTarget();
            double d = vec3d.y;
            if (this.getY() < entity.getY() + 12.0D) {
                d = Math.max(0.0D, d);
                d += 0.3D - d * 0.6D;
                if (this.getY() > entity.getY() + 26.0D) {
                    d -= 0.3D - d * 0.6D;
                }
            }
            vec3d = new Vec3d(vec3d.x, d, vec3d.z);
            Vec3d vec3d2 = new Vec3d(entity.getX() - this.getX(), 0.0D, entity.getZ() - this.getZ());
            if (squaredDistanceTo(vec3d2) > 16.0D) {
                Vec3d vec3d3 = vec3d2.normalize();
                vec3d = vec3d.add(vec3d3.x * 0.05D - vec3d.x * 0.05D, 0.0D, vec3d3.z * 0.05D - vec3d.z * 0.05D);
            }
        }
        this.setVelocity(vec3d);
        super.tickMovement();
    }

    @Override
    public void mobTick() {
        if (!this.hasNoGravity()) {
            this.setNoGravity(true);
        }
        int j;
        if (this.getInvulnerableTimer() > 0) {
            j = this.getInvulnerableTimer() - 1;
            if (j <= 0) {
                Explosion.DestructionType destructionType = this.world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING) ? Explosion.DestructionType.DESTROY : Explosion.DestructionType.NONE;
                this.world.createExplosion(this, this.getX(), this.getEyeY(), this.getZ(), 7.0F, false, destructionType);
                if (!this.isSilent()) {
                    this.world.syncGlobalEvent(1023, this.getBlockPos(), 0);
                }
            }
            this.getNavigation().stop();
            this.setTarget(null);
            this.setInvulTimer(j);
        } else {
            super.mobTick();
            LivingEntity livingEntity = this.getTarget();
            if (livingEntity != null && livingEntity.isAlive()) {
                if (attackTpCounter >= 120 && !this.hasBeamTarget()) {
                    for (int counter = 0; counter < 100; counter++) {
                        float randomFloat = this.world.getRandom().nextFloat() * 6.2831855F;
                        int posX = livingEntity.getBlockPos().getX() + MathHelper.floor(MathHelper.cos(randomFloat) * 9.0F + livingEntity.world.getRandom().nextInt(12));
                        int posZ = livingEntity.getBlockPos().getZ() + MathHelper.floor(MathHelper.sin(randomFloat) * 9.0F + livingEntity.world.getRandom().nextInt(12));
                        int posY = livingEntity.world.getTopY(Heightmap.Type.WORLD_SURFACE, posX, posZ) + 10 + livingEntity.world.getRandom().nextInt(12);
                        BlockPos teleportPos = new BlockPos(posX, posY, posZ);
                        if (livingEntity.world.isRegionLoaded(teleportPos.getX() - 4, teleportPos.getY() - 4, teleportPos.getZ() - 4, teleportPos.getX() + 4, teleportPos.getY() + 4,
                                teleportPos.getZ() + 4) && SpawnHelper.canSpawn(SpawnRestriction.Location.ON_GROUND, livingEntity.world, teleportPos, EntityInit.THE_EYE_ENTITY)) {
                            this.lookControl.lookAt(teleportPos.getX(), teleportPos.getY(), teleportPos.getZ());
                            if (!world.isClient) {
                                livingEntity.teleport(teleportPos.getX(), teleportPos.getY(), teleportPos.getZ());
                            }
                            livingEntity.world.playSound(null, teleportPos, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0F, 1.0F);
                            if (this.world.isClient) {
                                this.despawnParticlesServer(livingEntity);
                            }

                            attackTpCounter = -100;
                            break;
                        }
                    }
                } else {
                    attackTpCounter++;
                }
            }

            int n;
            if (this.field_7082 > 0) {
                --this.field_7082;
                if (this.field_7082 == 0 && this.world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
                    j = MathHelper.floor(this.getY());
                    n = MathHelper.floor(this.getX());
                    int o = MathHelper.floor(this.getZ());
                    boolean bl = false;

                    for (int p = -1; p <= 1; ++p) {
                        for (int q = -1; q <= 1; ++q) {
                            for (int r = 0; r <= 3; ++r) {
                                int s = n + p;
                                int t = j + r;
                                int u = o + q;
                                BlockPos blockPos = new BlockPos(s, t, u);
                                BlockState blockState = this.world.getBlockState(blockPos);
                                if (canDestroy(blockState)) {
                                    bl = this.world.breakBlock(blockPos, true, this) || bl;
                                }
                            }
                        }
                    }

                    if (bl) {
                        this.world.syncWorldEvent((PlayerEntity) null, 1022, this.getBlockPos(), 0);
                    }
                }
            }

            if (this.age % 20 == 0) {
                this.heal(1.0F);
            }

            this.bossBar.setPercent(this.getHealth() / this.getMaxHealth());
        }
    }

    public static boolean canDestroy(BlockState block) {
        return !block.isAir() && !TagInit.UNBREAKABLE_BLOCKS.contains(block.getBlock());
    }

    public void setEyeInvulnerabletime() {
        this.setInvulTimer(220);
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else if (source != DamageSource.DROWN && !(source.getAttacker() instanceof TheEyeEntity)) {
            if (this.getInvulnerableTimer() > 0 && source != DamageSource.OUT_OF_WORLD) {
                return false;
            } else {
                Entity entity2;
                entity2 = source.getAttacker();
                if (entity2 != null && !(entity2 instanceof PlayerEntity) && entity2 instanceof LivingEntity && ((LivingEntity) entity2).getGroup() == this.getGroup()) {
                    return false;
                } else {
                    if (this.field_7082 <= 0) {
                        this.field_7082 = 20;
                    }
                    this.gotDamage = true;
                    return super.damage(source, amount);
                }
            }
        } else {
            return false;
        }
    }

    private boolean gotDamage() {
        if (this.gotDamage) {
            this.gotDamage = false;
            return true;
        }
        return false;
    }

    @Override
    public void checkDespawn() {
        if (this.world.getDifficulty() == Difficulty.PEACEFUL) {
            this.discard();
        }
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean addStatusEffect(StatusEffectInstance effect, Entity entity) {
        return false;
    }

    public int getInvulnerableTimer() {
        return (Integer) this.dataTracker.get(INVUL_TIMER);
    }

    public void setInvulTimer(int ticks) {
        this.dataTracker.set(INVUL_TIMER, ticks);
    }

    @Override
    public EntityGroup getGroup() {
        return EntityGroup.UNDEAD;
    }

    @Override
    protected boolean canStartRiding(Entity entity) {
        return false;
    }

    @Override
    public boolean canUsePortals() {
        return false;
    }

    @Override
    public boolean canHaveStatusEffect(StatusEffectInstance effect) {
        return false;
    }

    private void setBeamTarget(int entityId) {
        this.dataTracker.set(BEAM_TARGET_ID, entityId);
    }

    public boolean hasBeamTarget() {
        return (Integer) this.dataTracker.get(BEAM_TARGET_ID) != 0;
    }

    @Nullable
    public LivingEntity getBeamTarget() {
        if (!this.hasBeamTarget()) {
            return null;
        } else if (this.world.isClient) {
            if (this.cachedBeamTarget != null) {
                return this.cachedBeamTarget;
            } else {
                Entity entity = this.world.getEntityById((Integer) this.dataTracker.get(BEAM_TARGET_ID));
                if (entity instanceof LivingEntity) {
                    this.cachedBeamTarget = (LivingEntity) entity;
                    return this.cachedBeamTarget;
                } else {
                    return null;
                }
            }
        } else {
            return this.getTarget();
        }
    }

    public int getWarmupTime() {
        return 120;
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (BEAM_TARGET_ID.equals(data)) {
            this.cachedBeamTarget = null;
        }

    }

    @Override
    public boolean canImmediatelyDespawn(double num) {
        return false;
    }

    @Override
    public void updatePostDeath() {
        deathTimer++;
        this.move(MovementType.SELF, new Vec3d(0.0D, 0.005D, 0.0D));
        this.setAiDisabled(true);
        this.setTarget(null);
        if (this.world.isClient) {
            despawnParticlesServer(this);

        }
        if (!this.world.isClient) {
            this.bossBar.setPercent(0.0F);
            BlockPos deathPos = new BlockPos(this.getX(), this.getY() - 1, this.getZ());
            if (deathTimer == 20 && !FabricLoader.getInstance().isModLoaded("voidz")) {
                Box box = new Box(this.getBlockPos());
                List<PlayerEntity> list = world.getEntitiesByClass(PlayerEntity.class, box.expand(128D), EntityPredicates.EXCEPT_SPECTATOR);
                for (int i = 0; i < list.size(); ++i) {
                    PlayerEntity playerEntity = (PlayerEntity) list.get(i);
                    if (playerEntity instanceof PlayerEntity) {
                        playerEntity.addStatusEffect(new StatusEffectInstance(EffectInit.FAME, 48000, 0, false, false, true));
                    }
                }
            }
            if (deathTimer == 140) {
                world.playSound(null, deathPos, SoundInit.EYE_DEATH_PLATFORM_EVENT, SoundCategory.HOSTILE, 1F, 1F);
            }
            if (deathTimer >= 200) {
                for (int o = 0; o < 5; o++) {
                    ((ServerWorld) this.world).spawnParticles(ParticleTypes.EXPLOSION, deathPos.getX() - 1 + this.world.random.nextInt(4), deathPos.getY() - 1 + this.world.random.nextInt(4),
                            deathPos.getZ() - 1 + this.world.random.nextInt(4), 0, 0.0D, 0.0D, 0.0D, 0.01D);
                    world.playSound(null, deathPos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1F, 1F);
                }
                for (int i = -1; i < 2; i++) {
                    for (int u = -1; u < 2; u++) {
                        for (int g = 0; g < 6; g++) {
                            if (!world.getBlockState(deathPos.east(i).north(u).up(g)).isAir() && !world.getBlockState(deathPos.east(i).north(u).up(g)).isIn(TagInit.UNBREAKABLE_BLOCKS)) {
                                world.breakBlock(deathPos.east(i).north(u).up(g), false);
                            }
                        }
                        if (!world.getBlockState(deathPos.east(i).north(u)).isIn(TagInit.UNBREAKABLE_BLOCKS)) {
                            world.setBlockState(deathPos.east(i).north(u), Blocks.CRYING_OBSIDIAN.getDefaultState(), 3);
                            world.playSound(null, deathPos, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 1F, 1F);
                        }
                    }
                }
                this.placeExtraBlocks(deathPos);
                if (FabricLoader.getInstance().isModLoaded("voidz")) {
                    world.setBlockState(deathPos.up(), BlockInit.PORTAL_BLOCK.getDefaultState(), 3);
                } else {
                    world.setBlockState(deathPos.up(), Blocks.DRAGON_EGG.getDefaultState(), 3);
                }
                // this.dropItem(ItemInit.PRIME_EYE_ITEM); // mcfunction
                this.discard();
            }

        }

    }

    private void despawnParticlesServer(LivingEntity entity) {
        for (int i = 0; i < 12; ++i) {
            double d = this.random.nextGaussian() * 0.025D;
            double e = this.random.nextGaussian() * 0.025D;
            double f = this.random.nextGaussian() * 0.025D;
            double x = MathHelper.nextDouble(random, entity.getBoundingBox().minX - 1.5D, entity.getBoundingBox().maxX + 1.5D);
            double y = MathHelper.nextDouble(random, entity.getBoundingBox().minY - 1.5D, entity.getBoundingBox().maxY + 1.5D);
            double z = MathHelper.nextDouble(random, entity.getBoundingBox().minZ - 1.5D, entity.getBoundingBox().maxZ + 1.5D);
            entity.world.addParticle(ParticleTypes.PORTAL, x, y, z, d, e, f);
        }
    }

    private void placeExtraBlocks(BlockPos pos) {
        // Could change it to a list
        // List<BlockPos> list = new ArrayList<>();
        // list.add(0, pos.east(2));

        if (!world.getBlockState(pos.east(2)).isIn(TagInit.UNBREAKABLE_BLOCKS)) {
            world.setBlockState(pos.east(2), Blocks.CRYING_OBSIDIAN.getDefaultState(), 3);
        }
        if (!world.getBlockState(pos.west(2)).isIn(TagInit.UNBREAKABLE_BLOCKS)) {
            world.setBlockState(pos.west(2), Blocks.CRYING_OBSIDIAN.getDefaultState(), 3);
        }
        if (!world.getBlockState(pos.north(2)).isIn(TagInit.UNBREAKABLE_BLOCKS)) {
            world.setBlockState(pos.north(2), Blocks.CRYING_OBSIDIAN.getDefaultState(), 3);
        }
        if (!world.getBlockState(pos.south(2)).isIn(TagInit.UNBREAKABLE_BLOCKS)) {
            world.setBlockState(pos.south(2), Blocks.CRYING_OBSIDIAN.getDefaultState(), 3);
        }
        if (!world.getBlockState(pos.down().north()).isIn(TagInit.UNBREAKABLE_BLOCKS)) {
            world.setBlockState(pos.down().north(), Blocks.CRYING_OBSIDIAN.getDefaultState(), 3);
        }
        if (!world.getBlockState(pos.down().south()).isIn(TagInit.UNBREAKABLE_BLOCKS)) {
            world.setBlockState(pos.down().south(), Blocks.CRYING_OBSIDIAN.getDefaultState(), 3);
        }
        for (int i = -1; i < 2; i++) {
            if (!world.getBlockState(pos.down().east(i)).isIn(TagInit.UNBREAKABLE_BLOCKS)) {
                world.setBlockState(pos.down().east(i), Blocks.CRYING_OBSIDIAN.getDefaultState(), 3);
            }
        }

    }

    static {
        INVUL_TIMER = DataTracker.registerData(TheEyeEntity.class, TrackedDataHandlerRegistry.INTEGER);
        BEAM_TARGET_ID = DataTracker.registerData(TheEyeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    }

    static class FireBeamGoal extends Goal {
        private final TheEyeEntity theEye;
        private int beamTicks;

        public FireBeamGoal(TheEyeEntity theEye) {
            this.theEye = theEye;
        }

        @Override
        public boolean canStart() {
            LivingEntity livingEntity = this.theEye.getTarget();
            return livingEntity != null && livingEntity.isAlive();
        }

        @Override
        public boolean shouldContinue() {
            LivingEntity livingEntity = this.theEye.getTarget();
            if (this.theEye.gotDamage()) {
                return false;
            }
            return super.shouldContinue() && this.theEye.squaredDistanceTo(this.theEye.getTarget()) > 8.0D && livingEntity != null && livingEntity.getY() < this.theEye.getY();
        }

        @Override
        public void start() {
            this.beamTicks = -20;
            this.theEye.getNavigation().stop();
            this.theEye.getLookControl().lookAt(this.theEye.getTarget(), 90.0F, 90.0F);
            this.theEye.velocityDirty = true;
        }

        @Override
        public void stop() {
            this.theEye.setBeamTarget(0);
            this.theEye.setTarget((LivingEntity) null);
        }

        @Override
        public void tick() {
            LivingEntity livingEntity = this.theEye.getTarget();
            this.theEye.getLookControl().lookAt(livingEntity, 90.0F, 90.0F);
            if (!this.theEye.canSee(livingEntity)) {
                this.theEye.setTarget((LivingEntity) null);
            } else {
                ++this.beamTicks;
                if (this.beamTicks == 0) {
                    this.theEye.setBeamTarget(this.theEye.getTarget().getId());
                } else if (this.beamTicks >= this.theEye.getWarmupTime()) {
                    float f = 4.0F;
                    if (this.theEye.world.getDifficulty() == Difficulty.HARD) {
                        f += 1.0F;
                    }
                    livingEntity.damage(DamageSource.magic(this.theEye, this.theEye), f);
                    livingEntity.damage(DamageSource.mob(this.theEye), (float) this.theEye.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE));
                    this.theEye.setTarget((LivingEntity) null);
                }

                super.tick();
            }
        }
    }

    static class EyeTargetPredicate implements Predicate<LivingEntity> {

        public EyeTargetPredicate(TheEyeEntity owner) {
        }

        @Override
        public boolean test(@Nullable LivingEntity livingEntity) {
            return livingEntity instanceof PlayerEntity;
        }
    }

    class ShootBulletGoal extends Goal {
        private int counter;

        public ShootBulletGoal() {
            this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
        }

        @Override
        public boolean canStart() {
            this.counter++;
            LivingEntity livingEntity = TheEyeEntity.this.getTarget();
            if (livingEntity != null && livingEntity.isAlive() && TheEyeEntity.this.getHealth() < TheEyeEntity.this.getMaxHealth() / 2 && this.counter >= 400) {
                return TheEyeEntity.this.world.getDifficulty() != Difficulty.PEACEFUL;
            } else {
                return false;
            }
        }

        @Override
        public void tick() {
            LivingEntity livingEntity = TheEyeEntity.this.getTarget();
            if (livingEntity != null) {
                int additions = 0;
                if (TheEyeEntity.this.getHealth() < TheEyeEntity.this.getMaxHealth() / 4) {
                    additions = 1;
                }
                for (int i = 0; i < 3 + additions; i++) {
                    TheEyeEntity.this.world.spawnEntity(new TinyEyeEntity(TheEyeEntity.this.world, TheEyeEntity.this, livingEntity, Direction.Axis.Y));
                    TheEyeEntity.this.playSound(SoundEvents.ENTITY_SHULKER_SHOOT, 2.0F, (TheEyeEntity.this.random.nextFloat() - TheEyeEntity.this.random.nextFloat()) * 0.2F + 1.0F);
                }
                super.tick();
                this.counter = TheEyeEntity.this.random.nextInt(12) * 20;
            }
        }
    }

    static class FlyRandomlyGoal extends Goal {
        private final TheEyeEntity theEyeEntity;

        public FlyRandomlyGoal(TheEyeEntity theEyeEntity) {
            this.theEyeEntity = theEyeEntity;
            this.setControls(EnumSet.of(Goal.Control.MOVE));
        }

        @Override
        public boolean canStart() {
            MoveControl moveControl = this.theEyeEntity.getMoveControl();
            if (!moveControl.isMoving()) {
                return true;
            } else {
                double d = moveControl.getTargetX() - this.theEyeEntity.getX();
                double e = moveControl.getTargetY() - this.theEyeEntity.getY();
                double f = moveControl.getTargetZ() - this.theEyeEntity.getZ();
                double g = d * d + e * e + f * f;
                return g < 1.0D || g > 3600.0D;
            }
        }

        @Override
        public boolean shouldContinue() {
            return false;
        }

        @Override
        public void start() {
            Random random = this.theEyeEntity.getRandom();
            double d = this.theEyeEntity.getX() + (double) ((random.nextFloat() * 2.0F - 1.0F) * 16.0F);
            double e = this.theEyeEntity.getY() + (double) ((random.nextFloat() * 2.0F - 1.0F) * 1.2F);
            double f = this.theEyeEntity.getZ() + (double) ((random.nextFloat() * 2.0F - 1.0F) * 16.0F);
            this.theEyeEntity.getMoveControl().moveTo(d, e, f, 1.0D);
        }
    }

    static class LookAtTargetGoal extends Goal {
        private final TheEyeEntity theEyeEntity;

        public LookAtTargetGoal(TheEyeEntity theEyeEntity) {
            this.theEyeEntity = theEyeEntity;
            this.setControls(EnumSet.of(Goal.Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return theEyeEntity.getInvulnerableTimer() <= 0;
        }

        @Override
        public void tick() {
            if (this.theEyeEntity.getTarget() == null) {
                Vec3d vec3d = this.theEyeEntity.getVelocity();
                this.theEyeEntity.setYaw(-((float) MathHelper.atan2(vec3d.x, vec3d.z)) * 57.295776F);
                this.theEyeEntity.bodyYaw = this.theEyeEntity.getYaw();
            } else {
                LivingEntity livingEntity = this.theEyeEntity.getTarget();
                if (livingEntity.squaredDistanceTo(this.theEyeEntity) < 4096.0D) {
                    double e = livingEntity.getX() - this.theEyeEntity.getX();
                    double f = livingEntity.getZ() - this.theEyeEntity.getZ();
                    this.theEyeEntity.setYaw(-((float) MathHelper.atan2(e, f)) * 57.295776F);
                    this.theEyeEntity.bodyYaw = this.theEyeEntity.getYaw();
                }
            }

        }
    }

    static class EyeMoveControl extends MoveControl {
        private final TheEyeEntity theEyeEntity;
        private int collisionCheckCooldown;

        public EyeMoveControl(TheEyeEntity theEyeEntity) {
            super(theEyeEntity);
            this.theEyeEntity = theEyeEntity;
        }

        @Override
        public void tick() {
            if (this.state == MoveControl.State.MOVE_TO) {
                if (this.collisionCheckCooldown-- <= 0) {
                    this.collisionCheckCooldown += this.theEyeEntity.getRandom().nextInt(5) + 2;
                    Vec3d vec3d = new Vec3d(this.targetX - this.theEyeEntity.getX(), this.targetY - this.theEyeEntity.getY(), this.targetZ - this.theEyeEntity.getZ());
                    double d = vec3d.length();
                    vec3d = vec3d.normalize();
                    if (this.willCollide(vec3d, MathHelper.ceil(d))) {
                        this.theEyeEntity.setVelocity(this.theEyeEntity.getVelocity().add(vec3d.multiply(0.1D)));
                    } else {
                        this.state = MoveControl.State.WAIT;
                    }
                }

            }
        }

        private boolean willCollide(Vec3d direction, int steps) {
            Box box = this.theEyeEntity.getBoundingBox();
            for (int i = 1; i < steps; ++i) {
                box = box.offset(direction);
                if (!this.theEyeEntity.world.isSpaceEmpty(this.theEyeEntity, box)) {
                    return false;
                }
            }
            return true;
        }
    }

}
