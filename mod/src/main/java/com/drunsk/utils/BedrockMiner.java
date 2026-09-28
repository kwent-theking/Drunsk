package com.drunsk.utils;

import com.drunsk.DrunskConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
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
 * Full port of rockerle/BedrockMiner Miner.java (MIT) to 26.2 mojmap.
 * / Ломание бедрока поршнем. Полный порт Miner.java из BedrockMiner (MIT).
 */
public final class BedrockMiner {

    private enum Task { INIT, PLACE_PISTON, REDSTONE_TORCH, ROTATE_PLAYER, SWITCH_TO_PICK, MINE_PISTON, MINE_SUPPORT, NOTHING }

    private static final List<Item> ALLOWED_TOOLS = List.of(Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE);
    private static final List<Item> SUPPORT_BLOCKS = List.of(Items.SLIME_BLOCK, Items.NETHERRACK);
    private static final List<Block> TARGET_BLOCKS = new ArrayList<>(List.of(Blocks.BEDROCK));

    private static Task task = Task.NOTHING;
    private static boolean running;
    private static boolean armed;
    private static BlockPos bedrockPos;
    private static BlockPos supportPos;
    private static BlockPos torchPos;
    private static PistonPlacement piston;
    private static Item pistonType;
    private static Item pickaxeType;
    private static Direction toFace;
    private static boolean placedPiston, placedTorch;
    private static int failed;

    private record PistonPlacement(BlockPos pos, Direction dir) {
    }

    private BedrockMiner() {
    }

    public static void reset() {
        task = Task.NOTHING;
        running = false;
        bedrockPos = null;
        supportPos = null;
        torchPos = null;
        piston = null;
        pistonType = null;
        pickaxeType = null;
        toFace = null;
        placedPiston = placedTorch = false;
        failed = 0;
    }

    public static void toggleArmed(LocalPlayer player) {
        if (!DrunskConfig.util.bedrockMiner) {
            player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.disabled_hint"));
            return;
        }
        armed = !armed;
        if (!armed) reset();
        player.sendOverlayMessage(Component.translatable(
                armed ? "drunsk.utils.miner.armed" : "drunsk.utils.miner.disarmed"));
    }

    public static boolean isArmed() {
        return armed;
    }

    public static boolean onClickedBlock(Minecraft mc, BlockPos pos, Direction dir) {
        if (!armed || running) return running;
        if (!DrunskConfig.util.bedrockMiner) return false;
        if (mc.level.getBlockState(pos).getBlock() == Blocks.BEDROCK) {
            start(mc, pos, dir);
            return true;
        }
        return false;
    }

    private static void start(Minecraft mc, BlockPos pos, Direction offsetDir) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        bedrockPos = pos;
        failed = 0;
        placedPiston = placedTorch = false;
        supportPos = null;
        torchPos = null;

        if (!checkPickaxe(mc, player)) {
            player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.no_pickaxe"));
            bedrockPos = null;
            return;
        }
        if (!findPistons(player)) {
            player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.no_pistons"));
            bedrockPos = null;
            return;
        }

        BlockPos bodyPos = pos.relative(offsetDir);
        if (bodyPos.equals(player.blockPosition()) || mc.level.isOutsideBuildHeight(bodyPos)) {
            piston = null;
        } else {
            Direction dir = mc.level.getBlockState(bodyPos.above()).isAir()
                    ? Direction.UP : pistonExtendDir(mc.level, bodyPos);
            piston = dir == null ? null : new PistonPlacement(bodyPos, dir);
        }
        running = true;
        task = Task.INIT;
    }

    public static void tick(Minecraft mc) {
        if (!running || mc.player == null || mc.level == null) return;
        try {
            step(mc);
        } catch (Exception e) {
            reset();
        }
    }

    private static void step(Minecraft mc) {
        LocalPlayer player = mc.player;
        switch (task) {
            case INIT -> {
                if (failed > 0) {
                    player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.no_spot"));
                    reset();
                    break;
                }
                if (piston != null) {
                    torchPos = findTorchSpot(mc, player, piston.pos(), piston.dir());
                }
                if (piston != null && torchPos != null) {
                    toFace = piston.dir().getOpposite();
                    task = Task.ROTATE_PLAYER;
                } else if (piston == null || torchPos == null) {
                    failed++;
                }
            }
            case PLACE_PISTON -> {
                placePiston(mc, player, piston.pos(), piston.dir().getOpposite());
                if (torchPos != null && piston != null) {
                    task = Task.ROTATE_PLAYER;
                    toFace = Direction.DOWN;
                    placedPiston = true;
                }
            }
            case REDSTONE_TORCH -> {
                if (supportPos != null) {
                    selectItem(player, SUPPORT_BLOCKS);
                    placeBlock(mc, player, supportPos);
                }
                if (torchPos != null && placedPiston && !placedTorch) {
                    selectItem(player, Items.REDSTONE_TORCH);
                    placeBlock(mc, player, torchPos);
                    placedTorch = true;
                    BlockPos dirPos = piston.pos().subtract(bedrockPos);
                    toFace = Direction.getNearest(dirPos.getX(), dirPos.getY(), dirPos.getZ(), player.getDirection());
                    task = Task.ROTATE_PLAYER;
                }
            }
            case ROTATE_PLAYER -> {
                if (toFace != null) {
                    player.connection.send(new ServerboundMovePlayerPacket.Rot(
                            dirToYaw(toFace, player), dirToPitch(toFace, player),
                            player.onGround(), player.horizontalCollision));
                    if (!placedPiston && !placedTorch) {
                        task = Task.PLACE_PISTON;
                    } else if (placedPiston && !placedTorch) {
                        task = Task.REDSTONE_TORCH;
                        toFace = Direction.DOWN;
                    } else {
                        task = Task.SWITCH_TO_PICK;
                    }
                } else {
                    reset();
                }
            }
            case SWITCH_TO_PICK -> {
                toFace = piston.dir().getOpposite();
                if (selectPickaxe(mc, player)) {
                    task = Task.MINE_PISTON;
                } else {
                    player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.no_pickaxe"));
                    reset();
                }
            }
            case MINE_PISTON -> {
                if (failed > 0 || piston == null) {
                    reset();
                    break;
                }
                BlockState st = mc.level.getBlockState(piston.pos());
                if (st.getBlock() == Blocks.MOVING_PISTON) {
                    player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.too_late"));
                    failed++;
                    break;
                }
                if (st.hasProperty(BlockStateProperties.EXTENDED) && st.getValue(BlockStateProperties.EXTENDED)) {
                    mineBedrock(mc, player);
                } else {
                    failed++;
                    break;
                }
                if (supportPos == null) reset();
                else task = Task.MINE_SUPPORT;
                placedPiston = placedTorch = false;
            }
            case MINE_SUPPORT -> {
                breakBlock(mc, player, supportPos);
                reset();
            }
            case NOTHING -> reset();
        }
    }

    private static void mineBedrock(Minecraft mc, LocalPlayer player) {
        breakBlock(mc, player, torchPos);
        breakBlock(mc, player, piston.pos());
        replacePiston(mc, player);
        if (supportPos != null) {
            breakBlock(mc, player, supportPos);
            supportPos = null;
        }
        breakBlock(mc, player, piston.pos());
        piston = null;
        torchPos = null;
    }

    private static void placePiston(Minecraft mc, LocalPlayer player, BlockPos pos, Direction dir) {
        if (pos == null || dir == null) return;
        selectItem(player, pistonType);
        mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), dir, pos, true));
    }

    private static void replacePiston(Minecraft mc, LocalPlayer player) {
        if (piston == null) return;
        int oldSlot = player.getInventory().getSelectedSlot();
        selectItem(player, pistonType);
        mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(piston.pos()), piston.dir().getOpposite(), piston.pos(), true));
        player.getInventory().setSelectedSlot(oldSlot);
    }

    private static void placeBlock(Minecraft mc, LocalPlayer player, BlockPos pos) {
        mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, true));
    }

    private static void breakBlock(Minecraft mc, LocalPlayer player, BlockPos pos) {
        if (pos == null) return;
        Direction face = player.getDirection();
        player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, face));
        player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, face));
    }

    private static Direction pistonExtendDir(Level level, BlockPos bodyPos) {
        for (Direction d : Direction.values()) {
            if (d == Direction.UP) continue;
            if (level.getBlockState(bodyPos.relative(d)).canBeReplaced()) {
                return d;
            }
        }
        return null;
    }

    private static BlockPos findTorchSpot(Minecraft mc, LocalPlayer player, BlockPos bodyPos, Direction facing) {
        Level level = mc.level;
        for (Direction d : Direction.values()) {
            if (d == facing || d == Direction.UP || bodyPos.relative(d).equals(bedrockPos)) continue;
            BlockPos probe = bodyPos.relative(d);
            if (!level.getBlockState(probe).isAir()) continue;
            BlockPos below = probe.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return probe;
            }
            if (level.getBlockState(below).isAir()
                    && !below.equals(player.blockPosition().above())
                    && !below.equals(player.blockPosition())
                    && !level.isOutsideBuildHeight(below)) {
                supportPos = below;
                return probe;
            }
        }
        return null;
    }

    private static boolean checkPickaxe(Minecraft mc, LocalPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ItemTags.PICKAXES) && efficiencyLevel(mc, s) >= 5) {
                pickaxeType = s.getItem();
                return true;
            }
        }
        return false;
    }

    private static boolean selectPickaxe(Minecraft mc, LocalPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ItemTags.PICKAXES) && efficiencyLevel(mc, s) >= 5) {
                selectSlot(mc, player, i);
                return true;
            }
        }
        return false;
    }

    private static int efficiencyLevel(Minecraft mc, ItemStack stack) {
        Holder<Enchantment> eff = mc.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
        return EnchantmentHelper.getItemEnchantmentLevel(eff, stack);
    }

    private static boolean findPistons(LocalPlayer player) {
        var inv = player.getInventory();
        for (Item type : List.of(Items.PISTON, Items.STICKY_PISTON)) {
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(type) && s.getCount() >= 2) {
                    pistonType = type;
                    return true;
                }
            }
        }
        return false;
    }

    private static void selectItem(LocalPlayer player, Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).is(item)) {
                selectSlot(Minecraft.getInstance(), player, i);
                return;
            }
        }
    }

    private static void selectItem(LocalPlayer player, List<Item> items) {
        for (Item it : items) {
            var inv = player.getInventory();
            for (int i = 0; i < 36; i++) {
                if (inv.getItem(i).is(it)) {
                    selectSlot(Minecraft.getInstance(), player, i);
                    return;
                }
            }
        }
    }

    private static void selectSlot(Minecraft mc, LocalPlayer player, int slot) {
        if (slot < 9) {
            player.getInventory().setSelectedSlot(slot);
        } else {
            int hotbar = player.getInventory().getSuitableHotbarSlot();
            Utils.swapSlots(mc, slot, hotbar, true);
            player.getInventory().setSelectedSlot(hotbar);
        }
    }

    private static float dirToYaw(Direction d, LocalPlayer player) {
        return switch (d) {
            case NORTH -> 180.0f;
            case EAST -> 270.0f;
            case SOUTH -> 0.0f;
            case WEST -> 90.0f;
            default -> player.getYRot();
        };
    }

    private static float dirToPitch(Direction d, LocalPlayer player) {
        return switch (d) {
            case UP -> -90.0f;
            case DOWN -> 90.0f;
            default -> player.getXRot();
        };
    }
}
