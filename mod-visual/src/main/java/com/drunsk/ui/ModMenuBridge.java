package com.drunsk.ui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu integration: opens the YACL config screen from the mods list.
 * / Интеграция ModMenu: открывает YACL-конфиг из списка модов.
 */
public final class ModMenuBridge implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> DrunskYaclScreen.create(parent);
    }
}
