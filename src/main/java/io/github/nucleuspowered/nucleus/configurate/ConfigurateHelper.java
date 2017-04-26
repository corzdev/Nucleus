/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.configurate;

import com.flowpowered.math.vector.Vector3d;
import com.google.common.reflect.TypeToken;
import io.github.nucleuspowered.neutrino.objectmapper.NeutrinoObjectMapperFactory;
import io.github.nucleuspowered.neutrino.typeserialisers.ByteArrayTypeSerialiser;
import io.github.nucleuspowered.neutrino.typeserialisers.IntArrayTypeSerialiser;
import io.github.nucleuspowered.neutrino.typeserialisers.PatternTypeSerialiser;
import io.github.nucleuspowered.neutrino.typeserialisers.SetTypeSerialiser;
import io.github.nucleuspowered.neutrino.typeserialisers.ShortArrayTypeSerialiser;
import io.github.nucleuspowered.nucleus.Nucleus;
import io.github.nucleuspowered.nucleus.configurate.typeserialisers.InstantTypeSerialiser;
import io.github.nucleuspowered.nucleus.configurate.typeserialisers.NucleusItemStackSnapshotSerialiser;
import io.github.nucleuspowered.nucleus.configurate.typeserialisers.NucleusTextTemplateTypeSerialiser;
import io.github.nucleuspowered.nucleus.configurate.typeserialisers.Vector3dTypeSerialiser;
import io.github.nucleuspowered.nucleus.configurate.wrappers.NucleusItemStackSnapshot;
import io.github.nucleuspowered.nucleus.internal.text.NucleusTextTemplateImpl;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.commented.SimpleCommentedConfigurationNode;
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializerCollection;
import org.spongepowered.api.GameState;
import org.spongepowered.api.Sponge;

import java.time.Instant;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigurateHelper {

    private ConfigurateHelper() {}

    private static TypeSerializerCollection typeSerializerCollection = null;
    private final static NeutrinoObjectMapperFactory objectMapperFactory;
    private static final Pattern commentPattern = Pattern.compile("^(loc:)?(?<key>([a-zA-Z0-9_-]+\\.?)+)$");

    static {
        objectMapperFactory = NeutrinoObjectMapperFactory.builder()
            .setCommentProcessor(setting -> {
                String comment = setting.comment();
                if (comment.contains(".") && !comment.contains(" ")) {
                    Matcher matcher = commentPattern.matcher(comment);

                    if (matcher.matches()) {
                        return Nucleus.getNucleus().getMessageProvider().getMessageWithFormat(matcher.group("key"));
                    }
                }

                return comment;
        }).build(true);
    }

    public static CommentedConfigurationNode getNewNode() {
        return SimpleCommentedConfigurationNode.root(setOptions(ConfigurationOptions.defaults()));
    }

    /**
     * Set NucleusPlugin specific options on the {@link ConfigurationOptions}
     *
     * @param options The {@link ConfigurationOptions} to alter.
     * @return The {@link ConfigurationOptions}, for easier inline use of this function.
     */
    public static ConfigurationOptions setOptions(ConfigurationOptions options) {
        TypeSerializerCollection tsc = getNucleusTypeSerialiserCollection();

        // Allows us to use localised comments and @ProcessSetting annotations
        return options.setSerializers(tsc).setObjectMapperFactory(objectMapperFactory);
    }

    private static TypeSerializerCollection getNucleusTypeSerialiserCollection() {
        if (typeSerializerCollection != null) {
            return typeSerializerCollection;
        }

        TypeSerializerCollection tsc = ConfigurationOptions.defaults().getSerializers();

        // Custom type serialisers for Nucleus
        tsc.registerType(TypeToken.of(Vector3d.class), new Vector3dTypeSerialiser());
        tsc.registerType(TypeToken.of(NucleusItemStackSnapshot.class), new NucleusItemStackSnapshotSerialiser());
        tsc.registerType(TypeToken.of(Pattern.class), new PatternTypeSerialiser());
        tsc.registerType(TypeToken.of(NucleusTextTemplateImpl.class), new NucleusTextTemplateTypeSerialiser());
        tsc.registerPredicate(
                typeToken -> Set.class.isAssignableFrom(typeToken.getRawType()),
                new SetTypeSerialiser()
        );

        tsc.registerType(new TypeToken<byte[]>(){}, new ByteArrayTypeSerialiser());
        tsc.registerType(new TypeToken<short[]>(){}, new ShortArrayTypeSerialiser());
        tsc.registerType(new TypeToken<int[]>(){}, new IntArrayTypeSerialiser());
        tsc.registerType(TypeToken.of(Instant.class), new InstantTypeSerialiser());

        if (Sponge.getGame().getState() == GameState.SERVER_STARTED) {
            typeSerializerCollection = tsc;
        }

        return tsc;
    }
}
