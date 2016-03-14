/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.listeners;

import com.google.inject.Inject;
import io.github.nucleuspowered.nucleus.api.PluginModule;
import io.github.nucleuspowered.nucleus.api.data.NucleusWorld;
import io.github.nucleuspowered.nucleus.config.loaders.WorldConfigLoader;
import io.github.nucleuspowered.nucleus.internal.ListenerBase;
import io.github.nucleuspowered.nucleus.internal.annotations.Modules;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.world.ChangeWorldWeatherEvent;

@Modules(PluginModule.ENVIRONMENT)
public class EnvironmentListener extends ListenerBase {

    @Inject private WorldConfigLoader loader;

    @Listener
    public void onWeatherChange(ChangeWorldWeatherEvent event) {
        try {
            NucleusWorld ew = loader.getWorld(event.getTargetWorld());
            event.setCancelled(ew.isLockWeather());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
