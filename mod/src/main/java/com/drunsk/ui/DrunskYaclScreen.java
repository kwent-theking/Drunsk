package com.drunsk.ui;

import com.drunsk.DrunskConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * YACL config screen: all utility toggles, modes and thresholds.
 * Opened via ModMenu or /drunsk utils.
 * / YACL-конфиг: все переключатели, режимы и пороги утилит.
 * Открывается через ModMenu или /drunsk utils.
 */
public final class DrunskYaclScreen {

    private DrunskYaclScreen() {
    }

    public static Screen create(Screen parent) {
        DrunskConfig.Util u = DrunskConfig.util;

        ConfigCategory general = ConfigCategory.createBuilder()
                .name(Component.translatable("drunsk.screen.utils"))
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("drunsk.utils.autoFish"))
                        .binding(false, () -> u.autoFish, v -> u.autoFish = v)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("drunsk.utils.autoTotem"))
                        .binding(false, () -> u.autoTotem, v -> u.autoTotem = v)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("drunsk.utils.gamma"))
                        .binding(false, () -> u.gamma, v -> u.gamma = v)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("drunsk.utils.freecam"))
                        .binding(false, () -> u.freecam, v -> u.freecam = v)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("drunsk.utils.bedrockMiner"))
                        .binding(false, () -> u.bedrockMiner, v -> u.bedrockMiner = v)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("drunsk.utils.autoEat"))
                        .binding(false, () -> u.autoEat, v -> u.autoEat = v)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("drunsk.utils.autoTool"))
                        .binding(false, () -> u.autoTool, v -> u.autoTool = v)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("drunsk.utils.autoBlock"))
                        .binding(false, () -> u.autoBlock, v -> u.autoBlock = v)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .build();

        ConfigCategory modes = ConfigCategory.createBuilder()
                .name(Component.translatable("drunsk.utils.modes"))
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("drunsk.utils.autoFish")
                                .append(" — ").append(Component.translatable("drunsk.utils.mode.packet")))
                        .binding(true, () -> u.autoFishPacket, v -> u.autoFishPacket = v)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("drunsk.utils.autoTotem")
                                .append(" — ").append(Component.translatable("drunsk.utils.mode.packet")))
                        .binding(true, () -> u.autoTotemPacket, v -> u.autoTotemPacket = v)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("drunsk.utils.autoEat")
                                .append(" — ").append(Component.translatable("drunsk.utils.mode.packet")))
                        .binding(true, () -> u.autoEatPacket, v -> u.autoEatPacket = v)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("drunsk.utils.autoTool")
                                .append(" — ").append(Component.translatable("drunsk.utils.mode.packet")))
                        .binding(true, () -> u.autoToolPacket, v -> u.autoToolPacket = v)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Component.translatable("drunsk.utils.autoBlock")
                                .append(" — ").append(Component.translatable("drunsk.utils.mode.packet")))
                        .binding(true, () -> u.autoBlockPacket, v -> u.autoBlockPacket = v)
                        .controller(BooleanControllerBuilder::create)
                        .build())
                .build();

        ConfigCategory thresholds = ConfigCategory.createBuilder()
                .name(Component.translatable("drunsk.utils.thresholds"))
                .option(Option.<Double>createBuilder()
                        .name(Component.translatable("drunsk.utils.autoTotemHealth"))
                        .binding(8.0, () -> u.autoTotemHealth, v -> u.autoTotemHealth = v)
                        .controller(opt -> DoubleSliderControllerBuilder.create(opt)
                                .range(2.0, 20.0).step(1.0))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Component.translatable("drunsk.utils.autoEatFood"))
                        .binding(14, () -> u.autoEatFood, v -> u.autoEatFood = v)
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                .range(1, 19).step(1))
                        .build())
                .option(Option.<Double>createBuilder()
                        .name(Component.translatable("drunsk.utils.autoToolDurability"))
                        .binding(0.08, () -> u.autoToolDurability, v -> u.autoToolDurability = v)
                        .controller(opt -> DoubleSliderControllerBuilder.create(opt)
                                .range(0.01, 0.5).step(0.01))
                        .build())
                .option(Option.<Double>createBuilder()
                        .name(Component.translatable("drunsk.utils.freecamSpeedH"))
                        .binding(1.0, () -> u.freecamSpeedH, v -> u.freecamSpeedH = v)
                        .controller(opt -> DoubleSliderControllerBuilder.create(opt)
                                .range(0.1, 5.0).step(0.1))
                        .build())
                .option(Option.<Double>createBuilder()
                        .name(Component.translatable("drunsk.utils.freecamSpeedV"))
                        .binding(0.8, () -> u.freecamSpeedV, v -> u.freecamSpeedV = v)
                        .controller(opt -> DoubleSliderControllerBuilder.create(opt)
                                .range(0.1, 5.0).step(0.1))
                        .build())
                .build();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("drunsk.screen.utils"))
                .category(general)
                .category(modes)
                .category(thresholds)
                .save(DrunskConfig::save)
                .build()
                .generateScreen(parent);
    }
}
