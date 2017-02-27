/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.modules.message.handlers;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import io.github.nucleuspowered.nucleus.Nucleus;
import io.github.nucleuspowered.nucleus.Util;
import io.github.nucleuspowered.nucleus.api.service.NucleusPrivateMessagingService;
import io.github.nucleuspowered.nucleus.dataservices.loaders.UserDataManager;
import io.github.nucleuspowered.nucleus.internal.CommandPermissionHandler;
import io.github.nucleuspowered.nucleus.internal.text.NucleusTextTemplateFactory;
import io.github.nucleuspowered.nucleus.internal.text.NucleusTextTemplateImpl;
import io.github.nucleuspowered.nucleus.internal.text.TextParsingUtils;
import io.github.nucleuspowered.nucleus.modules.message.MessageModule;
import io.github.nucleuspowered.nucleus.modules.message.commands.MessageCommand;
import io.github.nucleuspowered.nucleus.modules.message.commands.SocialSpyCommand;
import io.github.nucleuspowered.nucleus.modules.message.config.MessageConfig;
import io.github.nucleuspowered.nucleus.modules.message.config.MessageConfigAdapter;
import io.github.nucleuspowered.nucleus.modules.message.datamodules.MessageUserDataModule;
import io.github.nucleuspowered.nucleus.modules.message.events.InternalNucleusMessageEvent;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.util.Tristate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MessageHandler implements NucleusPrivateMessagingService {

    private final MessageConfigAdapter mca;
    private final UserDataManager ucl;
    private final TextParsingUtils textParsingUtils;
    private MessageConfig messageConfig;
    private boolean useLevels = false;
    private boolean sameLevel = false;
    private int serverLevel = 0;
    private final CommandPermissionHandler messagepermissions;
    private final CommandPermissionHandler socialspypermissions;

    private final Map<String[], Function<String, String>> replacements = createReplacements();
    private final Map<UUID, UUID> messagesReceived = Maps.newHashMap();
    public static final String socialSpyOption = "nucleus.socialspy.level";

    public MessageHandler(Nucleus nucleus) throws Exception {
        textParsingUtils = nucleus.getTextParsingUtils();
        ucl = nucleus.getUserDataManager();
        mca = nucleus.getModuleContainer().getConfigAdapterForModule(MessageModule.ID, MessageConfigAdapter.class);
        messagepermissions = nucleus.getPermissionRegistry().getPermissionsForNucleusCommand(MessageCommand.class);
        socialspypermissions = nucleus.getPermissionRegistry().getPermissionsForNucleusCommand(SocialSpyCommand.class);
        onReload();
    }

    public void onReload() {
        messageConfig = mca.getNodeOrDefault();
        useLevels = messageConfig.isSocialSpyLevels();
        sameLevel = messageConfig.isSocialSpySameLevel();
        serverLevel = messageConfig.getServerLevel();
    }

    @Override
    public boolean isSocialSpy(User user) {
        Tristate ts = forcedSocialSpyState(user);
        if (ts == Tristate.UNDEFINED) {
            return ucl.getUnchecked(user).get(MessageUserDataModule.class).isSocialSpy();
        }

        return ts.asBoolean();
    }

    @Override public boolean isUsingSocialSpyLevels() {
        return this.useLevels;
    }

    @Override public boolean canSpySameLevel() {
        return this.sameLevel;
    }

    @Override public int getServerLevel() {
        return getSocialSpyLevelForSource(Sponge.getServer().getConsole());
    }

    @Override public int getSocialSpyLevel(User user) {
        return useLevels ? Util.getPositiveIntOptionFromSubject(user, socialSpyOption).orElse(0) : 0;
    }

    @Override public Tristate forcedSocialSpyState(User user) {
        if (socialspypermissions.testSuffix(user, "base")) {
            if (socialspypermissions.testSuffix(user, "force")) {
                return Tristate.TRUE;
            }

            return Tristate.UNDEFINED;
        }

        return Tristate.FALSE;
    }

    @Override
    public boolean setSocialSpy(User user, boolean isSocialSpy) {
        if (forcedSocialSpyState(user) != Tristate.UNDEFINED) {
            return false;
        }

        ucl.getUnchecked(user).get(MessageUserDataModule.class).setSocialSpy(isSocialSpy);
        return true;
    }

    @Override public boolean canSpyOn(User spyingUser, CommandSource... sourceToSpyOn) throws IllegalArgumentException {
        if (sourceToSpyOn.length == 0) {
            throw new IllegalArgumentException("sourceToSpyOn must have at least one CommandSource");
        }

        if (isSocialSpy(spyingUser)) {
            if (Arrays.stream(sourceToSpyOn).anyMatch(x -> x instanceof User && spyingUser.getUniqueId().equals(((User)x).getUniqueId()))) {
                return false;
            }

            if (useLevels) {
                int target = Arrays.stream(sourceToSpyOn).mapToInt(this::getSocialSpyLevelForSource).max().orElse(0);
                if (sameLevel) {
                    return target <= getSocialSpyLevel(spyingUser);
                } else {
                    return target < getSocialSpyLevel(spyingUser);
                }
            }

            return true;
        }

        return false;
    }

    @Override public Set<CommandSource> onlinePlayersCanSpyOn(boolean includeConsole, CommandSource... sourceToSpyOn)
            throws IllegalArgumentException {
        if (sourceToSpyOn.length == 0) {
            throw new IllegalArgumentException("sourceToSpyOn must have at least one CommandSource");
        }

        // Get the users to scan.
        List<CommandSource> toSpyOn = Arrays.asList(sourceToSpyOn);
        Set<UUID> uuidsToSpyOn = toSpyOn.stream().map(x -> x instanceof User ? ((User)x).getUniqueId() : Util.consoleFakeUUID)
                .collect(Collectors.toSet());

        // Get those who aren't the subjects and have social spy on.
        Set<CommandSource> sources = Sponge.getServer().getOnlinePlayers().stream()
                .filter(x -> !uuidsToSpyOn.contains(x.getUniqueId()))
                .filter(this::isSocialSpy)
                .collect(Collectors.toSet());

        if (!useLevels) {
            if (includeConsole) {
                sources.add(Sponge.getServer().getConsole());
            }

            return sources;
        }

        // Get the highest level from the sources to spy on.
        int highestLevel = toSpyOn.stream().mapToInt(this::getSocialSpyLevelForSource).max().orElse(0);
        sources = sources.stream()
            .filter(x -> sameLevel ? getSocialSpyLevelForSource(x) >= highestLevel : getSocialSpyLevelForSource(x) > highestLevel)
            .collect(Collectors.toSet());

        if (includeConsole) {
            sources.add(Sponge.getServer().getConsole());
        }

        return sources;
    }

    @Override
    public boolean sendMessage(CommandSource sender, CommandSource receiver, String message) {
        // Message is about to be sent. Send the event out. If canceled, then that's that.
        boolean isCancelled = Sponge.getEventManager().post(new InternalNucleusMessageEvent(sender, receiver, message));
        if (isCancelled) {
            sender.sendMessage(Nucleus.getNucleus().getMessageProvider().getTextMessageWithFormat("message.cancel"));

            // Only continue to show Social Spy messages if the subject is muted.
            if (!messageConfig.isShowMessagesInSocialSpyWhileMuted()) {
                return false;
            }
        }

        // Social Spies.
        final UUID uuidSender = getUUID(sender);
        final UUID uuidReceiver = getUUID(receiver);

        final Map<String, Object> variables = Maps.newHashMap();
        variables.put("from", sender);
        variables.put("to", receiver);

        // Create the tokens.
        Map<String, Function<CommandSource, Optional<Text>>> tokens = Maps.newHashMap();
        tokens.put("from", cs -> Optional.of(textParsingUtils.addCommandToName(sender)));
        tokens.put("to", cs -> Optional.of(textParsingUtils.addCommandToName(receiver)));
        tokens.put("fromdisplay", cs -> Optional.of(textParsingUtils.addCommandToDisplayName(sender)));
        tokens.put("todisplay", cs -> Optional.of(textParsingUtils.addCommandToDisplayName(receiver)));

        Text tm = useMessage(sender, message);

        if (!isCancelled) {
            sender.sendMessage(constructMessage(sender, tm, messageConfig.getMessageSenderPrefix(), tokens, variables));
            receiver.sendMessage(constructMessage(sender, tm, messageConfig.getMessageReceiverPrefix(), tokens, variables));
        }

        NucleusTextTemplateImpl prefix = messageConfig.getMessageSocialSpyPrefix();
        if (isCancelled) {
            prefix = NucleusTextTemplateFactory.createFromAmpersandString(messageConfig.getMutedTag() + prefix.getRepresentation());
        }

        // Always if it's a subject who does the sending, if subject only is disabled in the config, to all.
        if (!messageConfig.isOnlyPlayerSocialSpy() || sender instanceof Player) {
            Set<CommandSource> lm = onlinePlayersCanSpyOn(
                !uuidSender.equals(Util.consoleFakeUUID) && !uuidReceiver.equals(Util.consoleFakeUUID), sender, receiver
            );

            MessageChannel mc = MessageChannel.fixed(lm);
            if (!mc.getMembers().isEmpty()) {
                mc.send(constructMessage(sender, tm, prefix, tokens, variables));
            }
        }

        // Add the UUIDs to the reply list - the receiver will now reply to the sender.
        if (!isCancelled) {
            messagesReceived.put(uuidReceiver, uuidSender);
        }

        return !isCancelled;
    }

    public boolean replyMessage(CommandSource sender, String message) {
        Optional<CommandSource> cs = getLastMessageFrom(getUUID(sender));
        if (cs.isPresent()) {
            return sendMessage(sender, cs.get(), message);
        }

        sender.sendMessage(Nucleus.getNucleus().getMessageProvider().getTextMessageWithFormat("message.noreply"));
        return false;
    }

    @Override public Optional<CommandSource> getConsoleReplyTo() {
        return getLastMessageFrom(Util.consoleFakeUUID);
    }

    @Override public Optional<CommandSource> getReplyTo(User user) {
        return getLastMessageFrom(user.getUniqueId());
    }

    @Override public void setReplyTo(User user, CommandSource toReplyTo) {
        messagesReceived.put(user.getUniqueId(), getUUID(Preconditions.checkNotNull(toReplyTo)));
    }

    @Override public void setConsoleReplyTo(CommandSource toReplyTo) {
        messagesReceived.put(Util.consoleFakeUUID, getUUID(Preconditions.checkNotNull(toReplyTo)));
    }

    @Override public void clearReplyTo(User user) {
        messagesReceived.remove(user.getUniqueId());
    }

    @Override public void clearConsoleReplyTo() {
        messagesReceived.remove(Util.consoleFakeUUID);
    }

    public Optional<CommandSource> getLastMessageFrom(UUID from) {
        Preconditions.checkNotNull(from);
        UUID to = messagesReceived.get(from);
        if (to == null) {
            return Optional.empty();
        }

        if (to.equals(Util.consoleFakeUUID)) {
            return Optional.of(Sponge.getServer().getConsole());
        }

        return Sponge.getServer().getOnlinePlayers().stream().filter(x -> x.getUniqueId().equals(to)).map(y -> (CommandSource) y).findFirst();
    }

    private UUID getUUID(CommandSource sender) {
        return sender instanceof Player ? ((Player) sender).getUniqueId() : Util.consoleFakeUUID;
    }

    @SuppressWarnings("unchecked")
    private Text constructMessage(CommandSource sender, Text message, NucleusTextTemplateImpl template, Map<String, Function<CommandSource,
            Optional<Text>>> tokens, Map<String, Object> variables) {
        return TextParsingUtils.joinTextsWithColoursFlowing(template.getForCommandSource(sender, tokens, variables), message);
    }

    private Map<String[], Function<String, String>> createReplacements() {
        Map<String[], Function<String, String>> t = new HashMap<>();

        t.put(new String[] { "colour", "color" }, s -> s.replaceAll("&[0-9a-fA-F]", ""));
        t.put(new String[] { "style" }, s -> s.replaceAll("&[l-oL-O]", ""));
        t.put(new String[] { "magic" }, s -> s.replaceAll("&[kK]", ""));

        return t;
    }

    private Text useMessage(CommandSource player, String m) {
        for (Map.Entry<String[],  Function<String, String>> r : replacements.entrySet()) {
            // If we don't have the required permission...
            if (Arrays.stream(r.getKey()).noneMatch(x -> messagepermissions.testSuffix(player, x))) {
                // ...strip the codes.
                m = r.getValue().apply(m);
            }
        }

        Text result;
        if (messagepermissions.testSuffix(player, "url")) {
            result = TextParsingUtils.addUrls(m);
        } else {
            result = TextSerializers.FORMATTING_CODE.deserialize(m);
        }

        return result;
    }

    private int getSocialSpyLevelForSource(CommandSource source) {
        if (useLevels) {
            return source instanceof User ? getSocialSpyLevel((User) source) : serverLevel;
        }

        return 0;
    }
}
