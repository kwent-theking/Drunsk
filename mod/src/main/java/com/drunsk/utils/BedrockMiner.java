package com.drunsk.utils;

import com.drunsk.DrunskConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * TNT-less bedrock breaking via the piston head-clip method.
 * Port of rockerle/BedrockMiner Miner.java (MIT) to 26.2 mojmap: the player
 * looks at bedrock and presses the miner key; the state machine places a
 * piston + redstone torch, extends the head into the bedrock and breaks it.
 * Packet mode sends raw ServerboundPlayerActionPacket START/STOP pairs.
 * / Ломание бедрока поршнем (порт BedrockMiner, MIT): смотришь на бедрок,
 * жмёшь клавишу — машина состояний ставит поршень и редстоун-факел, выдвигает
 * голову в бедрок и ломает его. Пакетный режим шлёт сырые action-пакеты.
 */
public final class BedrockMiner {

    private enum Task { INIT, PLACE_PISTON, REDSTONE_TORCH, ROTATE_PLAYER, SWITCH_TO_PICK, MINE_PISTON, MINE_SUPPORT, NOTHING }

    private static final List<Item> SUPPORT_BLOCKS = List.of(Items.SLIME_BLOCK, Items.NETHERRACK);

    private static Task task = Task.NOTHING;
    private static boolean running;
    private static boolean armed; // key-armed: next bedrock click starts / взведено: клик по бедроку стартует
    private static BlockPos bedrockPos;
    private static BlockPos supportPos;
    private static BlockPos torchPos;
    private static PistonPlacement piston;
    private static Item pistonType;
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

    /** Call from a MultiPlayerGameMode.startDestroyBlock mixin: intercept bedrock clicks. */
    public static boolean onClickedBlock(Minecraft mc, BlockPos pos, Direction dir) {
        if (!armed || running) return running;
        if (!DrunskConfig.util.bedrockMiner) return false;
        // 26.2: BlockStateBase has no is(Block); compare getBlock()
        // / 26.2: у BlockStateBase нет is(Block) — сравниваем getBlock()
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

        if (!findPickaxe(player)) {
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
                    if (!selectItem(player, SUPPORT_BLOCKS)) {
                        reset();
                        break;
                    }
                    placeBlock(mc, player, supportPos);
                }
                if (torchPos != null && placedPiston && !placedTorch) {
                    if (!selectItem(player, Items.REDSTONE_TORCH)) {
                        reset();
                        break;
                    }
                    placeBlock(mc, player, torchPos);
                    placedTorch = true;
                    Vec3i d = piston.pos().subtract(bedrockPos);
                    toFace = Direction.getNearest(d.getX(), d.getY(), d.getZ(), player.getDirection());
                    task = Task.ROTATE_PLAYER;
                }
            }
            case ROTATE_PLAYER -> {
                if (toFace == null) {
                    reset();
                    break;
                }
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
            }
            case SWITCH_TO_PICK -> {
                toFace = piston.dir().getOpposite();
                if (!selectPickaxe(mc.player)) {
                    mc.player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.no_pickaxe"));
                    reset();
                } else {
                    task = Task.MINE_PISTON;
                }
            }
            case MINE_PISTON -> {
                if (failed > 0 || piston == null) {
                    reset();
                    break;
                }
                BlockState st = mc.level.getBlockState(piston.pos());
                if (st.getBlock() == Blocks.MOVING_PISTON) {
                    mc.player.sendOverlayMessage(Component.translatable("drunsk.utils.miner.too_late"));
                    failed++;
                    break;
                }
                if (st.hasProperty(BlockStateProperties.EXTENDED) && st.getValue(BlockStateProperties.EXTENDED)) {
                    mineBedrock(mc);
                } else {
                    failed++;
                    break;
                }
                if (supportPos == null) reset();
                else task = Task.MINE_SUPPORT;
                placedPiston = placedTorch = false;
            }
            case MINE_SUPPORT -> {
                breakBlock(mc, supportPos);
                reset();
            }
            case NOTHING -> reset();
        }
    }

    private static void mineBedrock(Minecraft mc) {
        breakBlock(mc, torchPos);
        breakBlock(mc, piston.pos());
        replacePiston(mc);
        if (supportPos != null) {
            breakBlock(mc, supportPos);
            supportPos = null;
        }
        breakBlock(mc, piston.pos());
        piston = null;
        torchPos = null;
    }

    private static void placePiston(Minecraft mc, LocalPlayer player, BlockPos pos, Direction dir) {
        if (pos == null || dir == null) return;
        if (!selectItem(player, pistonType)) return;
        mc.gameMode.useItemOn(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), dir, pos, true));
    }

    private static void replacePiston(Minecraft mc) {
        if (piston == null) return;
        LocalPlayer player = mc.player;
        int oldSlot = player.getInventory().getSelectedSlot();
        if (selectItem(player, pistonType)) {
            mc.gameMode.useItemOn(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(piston.pos()), piston.dir().getOpposite(), piston.pos(), true));
        }
        player.getInventory().setSelectedSlot(oldSlot);
    }

    private static void placeBlock(Minecraft mc, LocalPlayer player, BlockPos pos) {
        mc.gameMode.useItemOn(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, true));
    }

    private static void breakBlock(Minecraft mc, BlockPos pos) {
        if (pos == null) return;
        LocalPlayer player = mc.player;
        Direction face = player.getDirection();
        if (DrunskConfig.util.bedrockPacket) {
            player.connection.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, face));
            player.connection.send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, face));
        } else {
            mc.gameMode.startDestroyBlock(pos, face);
            mc.gameMode.stopDestroyBlock();
        }
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

    private static boolean findPickaxe(LocalPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (isEfficientPickaxe(player, s)) return true;
        }
        return false;
    }

    private static boolean selectPickaxe(LocalPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (isEfficientPickaxe(player, s)) {
                selectSlot(player, i);
                return true;
            }
        }
        return false;
    }

    private static boolean isEfficientPickaxe(LocalPlayer player, ItemStack s) {
        return s.is(ItemTags.PICKAXES) && efficiencyLevel(player, s) >= 5;
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

    private static int efficiencyLevel(LocalPlayer player, ItemStack stack) {
        net.minecraft.core.Holder<Enchantment> eff = player.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
        return EnchantmentHelper.getItemEnchantmentLevel(eff, stack);
    }

    private static boolean selectItem(LocalPlayer player, Item item) {
        return selectItem(player, List.of(item));
    }

    private static boolean selectItem(LocalPlayer player, List<Item> items) {
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            for (Item it : items) {
                if (inv.getItem(i).is(it)) {
                    selectSlot(player, i);
                    return true;
                }
            }
        }
        return false;
    }

    private static void selectSlot(LocalPlayer player, int slot) {
        if (slot < 9) {
            player.getInventory().setSelectedSlot(slot);
        } else {
            // move to a free hotbar slot through the menu / через меню в свободный слот хотбара
            Minecraft mc = Minecraft.getInstance();
            int hotbar = player.getInventory().getSuitableHotbarSlot();
            Utils.swapSlots(mc, slot, hotbar, DrunskConfig.util.bedrockPacket);
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
