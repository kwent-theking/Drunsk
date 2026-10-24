package com.drunsk.utils;

import com.drunsk.DrunskConfig;
import com.drunsk.DrunskClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * TNT-less bedrock breaking via the piston head-clip method.
 * Literal port of rockerle/BedrockMiner Miner.java (MIT), yarn→mojmap only.
 * / Ломание бедрока поршнем. Буквальный порт Miner.java (MIT), только yarn→mojmap.
 */
public class BedrockMiner {

    private static final Logger LOGGER = LoggerFactory.getLogger("drunsk/bedrock-miner");
    private static boolean debugEnabled() {
        return DrunskConfig.util.debug || Boolean.getBoolean("drunsk.debug");
    }

    private Task currentTask;
    private final LocalPlayer player;
    private final ClientPacketListener netHandler;
    private final MultiPlayerGameMode interactionManager;
    private Direction toFace;
    private boolean alreadyRunning;
    private boolean armed;
    private BlockPos bedrockBlock;
    private BlockPos supportBlock;
    private BlockPos torchPos;
    private PistonPlacement pistonPlacement;
    private Item pistonType, pickaxeType;
    private boolean[] checks = {false, false}; //checklist for pistonplacement,torchplacement,
    private int failedCounter = -1;

    private final List<Item> allowedTools = List.of(Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE);
    private List<Item> allowedSupportBlocks = List.of(Items.SLIME_BLOCK, Items.NETHERRACK);
    private List<Block> allowedBlocksToMine = new ArrayList<>(List.of(Blocks.BEDROCK));

    public BedrockMiner(LocalPlayer player) {
        this.player = player;
        this.netHandler = player.connection;
        this.interactionManager = Minecraft.getInstance().gameMode;
        this.alreadyRunning = false;
        this.currentTask = Task.NOTHING;
    }

    /** Bridge used by the MultiPlayerGameMode mixin. / Мост для mixin. */
    public static boolean onClickedBlock(Minecraft mc, BlockPos pos, Direction direction) {
        BedrockMiner miner = DrunskClient.bedrockMiner;
        if (miner == null || mc.player == null || !DrunskConfig.util.bedrockMiner
                || !miner.isArmed() || miner.isRunning()) {
            return false;
        }
        if (!miner.getTargetBlocks().contains(mc.level.getBlockState(pos).getBlock())) {
            return false;
        }
        miner.start(pos, direction);
        return miner.isRunning();
    }

    //-------------------- Miner ---------------------------------------
    public void tick() {
        if (debugEnabled() && currentTask != Task.NOTHING) {
            LOGGER.info("tick task={} armed={} running={} bedrock={} piston={} torch={} support={} failed={}",
                    currentTask, armed, alreadyRunning, bedrockBlock, pistonPlacement, torchPos, supportBlock, failedCounter);
        }
        switch (currentTask) {
            case INIT -> {
                if (failedCounter > 0) {
                    player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.no_spot"));
                    this.reset();
                    break;
                }
                if (pistonPlacement != null)
                    torchPos = findRedstoneTorchPlace(pistonPlacement.pos(), pistonPlacement.dir());
                if (pistonPlacement != null && torchPos != null) {
                    this.toFace = pistonPlacement.dir().getOpposite();
                    this.currentTask = Task.ROTATEPLAYER;
                    break;
                }
                if (pistonPlacement == null || torchPos == null) {
                    failedCounter++;
                }
            }
            case PLACEPISTON -> {
                placePiston(pistonPlacement.pos, pistonPlacement.dir().getOpposite());
                if (torchPos != null && pistonPlacement != null) {
                    this.currentTask = Task.ROTATEPLAYER;
                    toFace = Direction.DOWN;
                    checks[0] = true;
                }
            }
            case REDSTONETORCH -> {
                if (supportBlock != null) {
                    selectItem(null, allowedSupportBlocks);
                    placeBlock(supportBlock);
                }
                if (torchPos != null && checks[0] && !checks[1]) {
                    selectItem(null, Items.REDSTONE_TORCH);
                    placeBlock(torchPos);
                    checks[1] = true;
                    BlockPos dirPos = pistonPlacement.pos.subtract(bedrockBlock);
                    toFace = Direction.getNearest(dirPos.getX(), dirPos.getY(), dirPos.getZ(), player.getDirection());
                    this.currentTask = Task.ROTATEPLAYER;
                }
            }
            case ROTATEPLAYER -> {
                if (toFace != null) {
                    netHandler.send(
                            new ServerboundMovePlayerPacket.Rot(
                                    dirToYaw(toFace),
                                    dirToPitch(toFace),
                                    player.onGround(),
                                    player.horizontalCollision));
                    if (!checks[0] && !checks[1])
                        this.currentTask = Task.PLACEPISTON;
                    if (checks[0] && !checks[1]) {
                        this.currentTask = Task.REDSTONETORCH;
                        this.toFace = Direction.DOWN;
                    }
                    if (checks[0] && checks[1])
                        this.currentTask = Task.SWITCHTOPICK;
                } else {
                    player.sendOverlayMessage(Component.literal("Direction to rotate was null"));
                    this.reset();
                }
            }
            case SWITCHTOPICK -> {
                toFace = pistonPlacement.dir().getOpposite();
                if (selectItem(getEfficiencyHolder(), pickaxeType)) {
                    this.currentTask = Task.MINEPISTON;
                } else {
                    player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.no_pickaxe"));
                    this.reset();
                }
            }
            case MINEPISTON -> {
                if (failedCounter > 0 || this.pistonPlacement == null) {
                    this.reset();
                    break;
                }
                if (player.level().getBlockState(this.pistonPlacement.pos()).getBlock() == Blocks.MOVING_PISTON) {
                    player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.too_late"));
                    failedCounter++;
                    break;
                }

                if (player.level().getBlockState(this.pistonPlacement.pos()).getValue(BlockStateProperties.EXTENDED)) {
                    mineBedrock();
                } else {
                    failedCounter++;
                    break;
                }
                if (this.supportBlock == null)
                    this.reset();
                else
                    this.currentTask = Task.MINESUPPORT;
                checks[0] = checks[1] = false;
            }
            case MINESUPPORT -> {
                breakBlock(supportBlock);
                this.reset();
            }
            case NOTHING -> {
                checks[0] = checks[1] = false;
                this.alreadyRunning = false;
                this.failedCounter = 0;
                this.bedrockBlock = null;
                this.supportBlock = null;
            }
        }
    }

    public void start(BlockPos bp, Direction offsetDir) {
        debug("start target={} offset={} player={} dimension={}", bp, offsetDir, player.blockPosition(), player.level().dimension().identifier());
        this.currentTask = Task.INIT;
        this.bedrockBlock = bp;
        this.alreadyRunning = true;
        this.pistonPlacement = new PistonPlacement(
                bp.relative(offsetDir),
                player.level().getBlockState(bp.relative(offsetDir).relative(Direction.UP)).isAir()
                        ? Direction.UP : canPistonExtend(bp.relative(offsetDir)));
        if (this.pistonPlacement != null && (this.pistonPlacement.pos().equals(player.blockPosition())
                || player.level().isOutsideBuildHeight(this.pistonPlacement.pos())))
            this.pistonPlacement = null;
        if (!checkPickaxe()) {
            player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.no_pickaxe"));
            this.reset();
            return;
        }
        if (this.pistonPlacement == null || this.pistonPlacement.dir() == null) {
            player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.no_spot"));
            this.reset();
            return;
        }
        if (player.getInventory().contains(Items.PISTON.getDefaultInstance())) {
            int s = getSlotWithItem(Items.PISTON);
            if (s > -1) {
                if (player.getInventory().getItem(s).getCount() >= 2) {
                    this.pistonType = Items.PISTON;
                    this.currentTask = Task.INIT;
                    return;
                }
            }
        }
        if (player.getInventory().contains(Items.STICKY_PISTON.getDefaultInstance())) {
            int s = getSlotWithItem(Items.STICKY_PISTON);
            if (s > -1 && player.getInventory().getItem(s).getCount() >= 2) {
                this.pistonType = Items.STICKY_PISTON;
                this.currentTask = Task.INIT;
                return;
            }
        }
        player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.no_pistons"));
        this.reset();
    }

    public boolean isRunning() {
        return this.alreadyRunning;
    }

    public boolean isArmed() {
        return armed;
    }

    public void arm() {
        this.armed = true;
    }

    public void reset() {
        debug("reset task={} target={} failed={}", currentTask, bedrockBlock, failedCounter);
        this.alreadyRunning = false;
        this.armed = false;
        this.bedrockBlock = null;
        this.supportBlock = null;
        this.torchPos = null;
        this.pistonPlacement = null;
        this.pistonType = null;
        this.pickaxeType = null;
        this.toFace = null;
        this.checks[0] = this.checks[1] = false;
        this.failedCounter = -1;
        this.currentTask = Task.NOTHING;
    }

    //-------------------- Inventory Actions -----------------------------------
    private boolean checkPickaxe() {
        var inv = player.getInventory();
        int pickSlot = -1;
        ItemStack tempStack;
        for (int i = 0; i < 36; i++) {
            tempStack = inv.getItem(i);
            if (EnchantmentHelper.getItemEnchantmentLevel(getEfficiencyHolder(), tempStack) == 5
                    && allowedTools.contains(tempStack.getItem())) {
                pickSlot = i;
                break;
            }
        }
        if (pickSlot == -1) {
            player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.no_pickaxe"));
            return false;
        }
        pickaxeType = inv.getItem(pickSlot).getItem();
        return true;
    }

    private boolean selectItem(Holder<Enchantment> e, List<Item> items) {
        for (Item i : items) {
            if (selectItem(e, i))
                return true;
        }
        return false;
    }

    private boolean selectItem(Holder<Enchantment> e, Item item) {
        var inv = player.getInventory();
        ItemStack iS;
        int slot = -1;
        for (int i = 0; i < 36; i++) {
            iS = inv.getItem(i);
            if (iS.is(item)) {
                if (e == null) {
                    slot = i;
                    break;
                } else {
                    if (EnchantmentHelper.getItemEnchantmentLevel(e, iS) == 5) {
                        slot = i;
                        break;
                    }
                }
            }
        }
        if (slot < 0)
            return false;
        else if (slot < 9)
            inv.setSelectedSlot(slot);
        else {
            // pickFromInventory → swap to hotbar / свап в хотбар
            int hotbar = inv.getSuitableHotbarSlot();
            Utils.swapSlots(Minecraft.getInstance(), slot, hotbar, true);
            inv.setSelectedSlot(hotbar);
        }
        return true;
    }

    private int getSlotWithItem(Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).is(item)) return i;
        }
        return -1;
    }

    private Holder<Enchantment> getEfficiencyHolder() {
        return player.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
    }

    //-------------------- Placement Checks ------------------------------------
    private Direction canPistonExtend(BlockPos pistonBody) {
        for (Direction d : Direction.values()) {
            if (d.equals(Direction.UP))
                continue;
            if (player.level().getBlockState(pistonBody.relative(d)).canBeReplaced()) {
                return d;
            }
        }
        return null;
    }

    private BlockPos findRedstoneTorchPlace(BlockPos pistonBody, Direction facing) {
        for (Direction d : Direction.values()) {
            if (d.equals(facing) || d.equals(Direction.UP) || pistonBody.relative(d).equals(bedrockBlock)) {
                continue;
            }
            BlockPos probePos = pistonBody.relative(d);
            BlockState probeState = player.level().getBlockState(probePos);
            if (probeState.isAir()) {
                if (player.level().getBlockState(probePos.below())
                        .isFaceSturdy(player.level(), probePos.below(), Direction.UP))
                    return probePos;
                else if (player.level().getBlockState(probePos.relative(Direction.DOWN)).isAir()
                        && !probePos.relative(Direction.DOWN).equals(player.blockPosition().above())
                        && !probePos.relative(Direction.DOWN).equals(player.blockPosition())
                        && probePos.getY() > -63
                        && !player.level().isOutsideBuildHeight(probePos)) {
                    supportBlock = probePos.relative(Direction.DOWN);
                    return probePos;
                }
            }
        }
        return null;
    }

    //-------------------- World Interactions ----------------------------------
    private void mineBedrock() {
        breakBlock(this.torchPos);
        breakBlock(this.pistonPlacement.pos());
        replacePiston(this.pistonPlacement.pos());
        if (this.supportBlock != null) {
            breakBlock(this.supportBlock);
            this.supportBlock = null;
        }
        breakBlock(this.pistonPlacement.pos());
        this.pistonPlacement = null;
        this.torchPos = null;
    }

    private void placePiston(BlockPos pistonPos, Direction dir) {
        if (pistonPos == null || dir == null)
            return;
        selectItem(null, pistonType);
        interactionManager.useItemOn(
                player,
                player.getUsedItemHand(),
                new BlockHitResult(Vec3.atCenterOf(pistonPos), dir, pistonPos, true));
    }

    private void replacePiston(BlockPos pistonPos) {
        if (pistonPos == null)
            return;
        int oldSlot = player.getInventory().getSelectedSlot();
        selectItem(null, pistonType);
        interactionManager.useItemOn(
                player,
                player.getUsedItemHand(),
                new BlockHitResult(Vec3.atCenterOf(pistonPos),
                        pistonPlacement.dir().getOpposite(),
                        pistonPos,
                        true));
        player.getInventory().setSelectedSlot(oldSlot);
    }

    private void placeBlock(BlockPos bp) {
        interactionManager.useItemOn(
                player,
                player.getUsedItemHand(),
                new BlockHitResult(Vec3.atCenterOf(bp), Direction.UP, bp, true));
    }

    private void breakBlock(BlockPos blockPos) {
        netHandler.send(
                new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, blockPos, player.getDirection()));
        netHandler.send(
                new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, blockPos, player.getDirection()));
    }

    //-------------------- Util ------------------------------------------------
    public List<Block> getTargetBlocks() {
        return allowedBlocksToMine;
    }

    public void addTargetBlock(Block b) {
        allowedBlocksToMine.add(b);
    }

    public void removeTargetBlock(Block b) {
        allowedBlocksToMine.remove(b);
    }

    private float dirToYaw(Direction d) {
        return switch (d) {
            case NORTH -> 180.0f;
            case EAST -> 270.0f;
            case SOUTH -> 0.0f;
            case WEST -> 90.0f;
            default -> player.getYRot();
        };
    }

    private float dirToPitch(Direction d) {
        return switch (d) {
            case UP -> -90.0f;
            case DOWN -> 90.0f;
            default -> player.getXRot();
        };
    }

    private void debug(String message, Object... args) {
        if (debugEnabled()) LOGGER.info(message, args);
    }

    private record PistonPlacement(BlockPos pos, Direction dir) {
    }

    private enum Task {
        INIT,
        PLACEPISTON,
        REDSTONETORCH,
        ROTATEPLAYER,
        SWITCHTOPICK,
        MINEPISTON,
        MINESUPPORT,
        NOTHING
    }
}
