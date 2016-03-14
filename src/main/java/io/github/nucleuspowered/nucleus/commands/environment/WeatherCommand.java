/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.commands.environment;

import com.google.inject.Inject;
import io.github.nucleuspowered.nucleus.Util;
import io.github.nucleuspowered.nucleus.api.PluginModule;
import io.github.nucleuspowered.nucleus.api.data.NucleusWorld;
import io.github.nucleuspowered.nucleus.argumentparsers.TimespanParser;
import io.github.nucleuspowered.nucleus.argumentparsers.WeatherParser;
import io.github.nucleuspowered.nucleus.config.loaders.WorldConfigLoader;
import io.github.nucleuspowered.nucleus.internal.CommandBase;
import io.github.nucleuspowered.nucleus.internal.annotations.Modules;
import io.github.nucleuspowered.nucleus.internal.annotations.Permissions;
import io.github.nucleuspowered.nucleus.internal.annotations.RegisterCommand;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.source.LocatedSource;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.storage.WorldProperties;
import org.spongepowered.api.world.weather.Weather;

import java.util.Optional;

@Permissions
@Modules(PluginModule.ENVIRONMENT)
@RegisterCommand("weather")
public class WeatherCommand extends CommandBase<CommandSource> {
    private final String world = "world";
    private final String weather = "weather";
    private final String duration = "duration";
    private final String timespan = "timespan";

    @Inject private WorldConfigLoader loader;

    @Override
    public CommandSpec createSpec() {
        return CommandSpec.builder().executor(this).arguments(
                GenericArguments.optionalWeak(GenericArguments.onlyOne(GenericArguments.world(Text.of(world)))),
                GenericArguments.onlyOne(new WeatherParser(Text.of(weather))), // More flexible with the arguments we can use.
                GenericArguments.firstParsing(
                    GenericArguments.onlyOne(GenericArguments.optional(GenericArguments.integer(Text.of(duration)))),
                    GenericArguments.onlyOne(GenericArguments.optional(new TimespanParser(Text.of(timespan))))
                )
        ).description(Text.of("Sets the weather")).build();
    }

    @Override
    public CommandResult executeCommand(CommandSource src, CommandContext args) throws Exception {
        // We can predict the weather on multiple worlds now!
        WorldProperties wp = args.<WorldProperties>getOne(world).orElse(null);
        World w;
        if (wp != null) {
            w = Sponge.getServer().getWorld(wp.getUniqueId()).get();
        } else {
            // Actually, we just care about where we are.
            if (src instanceof LocatedSource) {
                w = ((LocatedSource) src).getWorld();
            } else {
                // As supreme overlord of the worlds... you have to specify one.
                src.sendMessage(Util.getTextMessageWithFormat("command.specifyworld"));
                return CommandResult.empty();
            }
        }

        // Get whether we locked the weather.
        NucleusWorld ew = loader.getWorld(w);
        if (ew.isLockWeather()) {
            // Tell the user to unlock first.
            src.sendMessage(Util.getTextMessageWithFormat("command.weather.locked", w.getName()));
            return CommandResult.empty();
        }

        // Houston, we have a world! Now, what was the forecast?
        Weather we = args.<Weather>getOne(weather).get();

        // Have we gotten an accurate forecast? Do we know how long this weather spell will go on for?
        Optional<Long> oi = args.<Long>getOne(timespan);
        if (!oi.isPresent()) {
            Optional<Integer> i = args.<Integer>getOne(duration);
            oi =  i.isPresent() ? Optional.of((long)i.get()) : Optional.empty();
        }

        if (oi.isPresent()) {
            // YES! I should get a job at the weather service and show them how it's done!
            w.setWeather(we, oi.get());
            src.sendMessage(Util.getTextMessageWithFormat("command.weather.time", we.getName(), w.getName(), Util.getTimeStringFromSeconds(oi.get())));
        } else {
            // No, probably because I've already gotten a job at the weather service...
            w.setWeather(we);
            src.sendMessage(Util.getTextMessageWithFormat("command.weather", we.getName(), w.getName()));
        }

        // The weather control device has been activated!
        return CommandResult.success();
    }
}
