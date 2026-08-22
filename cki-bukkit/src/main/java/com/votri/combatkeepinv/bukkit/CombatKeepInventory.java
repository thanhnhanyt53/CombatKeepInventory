package com.votri.combatkeepinv;

import com.votri.combatkeepinv.command.CombatCommand;
import com.votri.combatkeepinv.combat.CombatManager;
import com.votri.combatkeepinv.hook.WorldGuardHook;
import com.votri.combatkeepinv.listener.CombatListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CombatKeepInventory extends JavaPlugin {

    public static final String PLUGIN_VERSION =
            "1.1.0-SNAPSHOT-build2";

    private static final int CONFIG_VERSION = 2;

    private CombatManager combatManager;
    private WorldGuardHook worldGuardHook;
    private CombatListener combatListener;

    private File messageFile;
    private FileConfiguration messages;

    private boolean pvpEnabled;

    private String detectedPlatform;
    private String selectedPlatform;

    @Override
    public void onEnable() {

        migrateConfigIfRequired();

        saveDefaultConfig();

        saveDefaultMessages();
        loadMessages();

        loadPlatform();

        if (!checkPlatform()) {

            getLogger().severe(
                    "Selected platform does not match the detected server platform."
            );

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);

            return;
        }

        initializeComponents();
        registerCombatListeners();
        registerCommand();

        getLogger().info(
                "CombatKeepInventory "
                        + PLUGIN_VERSION
                        + " enabled."
        );

        getLogger().info(
                "Detected platform: "
                        + detectedPlatform
        );

        getLogger().info(
                "Selected platform: "
                        + selectedPlatform
        );

        getLogger().info(
                "Combat duration: "
                        + getCombatDurationSeconds()
                        + " seconds"
        );

        getLogger().info(
                "PvP: "
                        + (pvpEnabled
                        ? "ENABLED"
                        : "DISABLED")
        );

        getLogger().info(
                "Listener priority: "
                        + getListenerPriority()
        );

        getLogger().info(
                "WorldGuard: "
                        + (
                        worldGuardHook.isAvailable()
                                ? "ENABLED"
                                : "NOT INSTALLED"
                )
        );
    }

    private void initializeComponents() {

        long durationMillis =
                getCombatDurationSeconds()
                        * 1000L;

        if (combatManager == null) {

            combatManager =
                    new CombatManager(
                            durationMillis
                    );

        } else {

            /*
             * Keep the existing CombatManager instance.
             *
             * This is important because CombatListener holds
             * a reference to this exact object.
             */
            combatManager.setDurationMillis(
                    durationMillis
            );
        }

        if (worldGuardHook == null) {
            worldGuardHook =
                    new WorldGuardHook(this);
        }

        pvpEnabled =
                getConfig().getBoolean(
                        "pvp.enabled",
                        true
                );
    }

    private void registerCombatListeners() {

        if (combatListener != null) {
            return;
        }

        combatListener =
                new CombatListener(
                        this,
                        combatManager,
                        worldGuardHook
                );

        EventPriority priority =
                getListenerPriority();

        EventExecutor damageExecutor =
                (registeredListener, event) -> {

                    if (event instanceof EntityDamageByEntityEvent damage) {

                        combatListener
                                .onEntityDamageByEntity(
                                        damage
                                );
                    }
                };

        EventExecutor deathExecutor =
                (registeredListener, event) -> {

                    if (event instanceof PlayerDeathEvent death) {

                        combatListener
                                .onPlayerDeath(
                                        death
                                );
                    }
                };

        getServer()
                .getPluginManager()
                .registerEvent(
                        EntityDamageByEntityEvent.class,
                        combatListener,
                        priority,
                        damageExecutor,
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvent(
                        PlayerDeathEvent.class,
                        combatListener,
                        priority,
                        deathExecutor,
                        this
                );

        getLogger().info(
                "Combat listeners registered at "
                        + priority
                        + " priority."
        );
    }

    public EventPriority getListenerPriority() {

        String value =
                getConfig().getString(
                        "listener-priority",
                        "HIGHEST"
                );

        if (value == null) {
            return EventPriority.HIGHEST;
        }

        try {

            return EventPriority.valueOf(
                    value.trim()
                            .toUpperCase(
                                    Locale.ROOT
                            )
            );

        } catch (IllegalArgumentException exception) {

            getLogger().warning(
                    "Invalid listener-priority '"
                            + value
                            + "'. Using HIGHEST."
            );

            return EventPriority.HIGHEST;
        }
    }

    private void migrateConfigIfRequired() {

        File configFile =
                new File(
                        getDataFolder(),
                        "config.yml"
                );

        if (!configFile.exists()) {
            return;
        }

        YamlConfiguration oldConfig =
                YamlConfiguration.loadConfiguration(
                        configFile
                );

        int oldVersion =
                oldConfig.getInt(
                        "config-version",
                        0
                );

        if (oldVersion >= CONFIG_VERSION) {
            return;
        }

        long timestamp =
                System.currentTimeMillis();

        File backup =
                new File(
                        getDataFolder(),
                        "config.yml.backup-"
                                + timestamp
                );

        try {

            Files.move(
                    configFile.toPath(),
                    backup.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            getLogger().warning(
                    "Old config.yml detected "
                            + "(version "
                            + oldVersion
                            + ")."
            );

            getLogger().warning(
                    "Old configuration backed up to: "
                            + backup.getName()
            );

            getLogger().info(
                    "A new configuration will now be created."
            );

        } catch (IOException exception) {

            getLogger().severe(
                    "Could not migrate old config.yml: "
                            + exception.getMessage()
            );

            throw new IllegalStateException(
                    "Configuration migration failed.",
                    exception
            );
        }
    }

    private void registerCommand() {

        PluginCommand command =
                getCommand("cki");

        if (command == null) {

            getLogger().severe(
                    "Command 'cki' is missing from plugin.yml!"
            );

            return;
        }

        CombatCommand handler =
                new CombatCommand(this);

        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    private void saveDefaultMessages() {

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        messageFile =
                new File(
                        getDataFolder(),
                        "message.yml"
                );

        if (!messageFile.exists()) {

            saveResource(
                    "message.yml",
                    false
            );
        }
    }

    public void loadMessages() {

        messageFile =
                new File(
                        getDataFolder(),
                        "message.yml"
                );

        if (!messageFile.exists()) {

            saveResource(
                    "message.yml",
                    false
            );
        }

        messages =
                YamlConfiguration
                        .loadConfiguration(
                                messageFile
                        );
    }

    public void reloadPlugin() {

        reloadConfig();

        loadMessages();
        loadPlatform();

        /*
         * Keep the same CombatManager object.
         * Only update its duration.
         */
        initializeComponents();

        getLogger().info(
                "CombatKeepInventory configuration reloaded."
        );

        getLogger().info(
                "Listener priority configured as "
                        + getListenerPriority()
        );
    }

    public String getMessage(
            String path
    ) {

        return getMessage(
                path,
                path
        );
    }

    public String getMessage(
            String path,
            String fallback
    ) {

        String value =
                messages.getString(
                        path,
                        fallback
                );

        if (value == null) {
            value = fallback;
        }

        return color(value);
    }

    public List<String> getMessageList(
            String path
    ) {

        List<String> values =
                messages.getStringList(
                        path
                );

        List<String> result =
                new ArrayList<>();

        for (String value : values) {
            result.add(
                    color(value)
            );
        }

        return result;
    }

    public String color(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return ChatColor.translateAlternateColorCodes(
                '&',
                text
        );
    }

    private void loadPlatform() {

        detectedPlatform =
                detectPlatform();

        selectedPlatform =
                getConfig().getString(
                        "platform.mode",
                        "auto"
                );

        if (selectedPlatform == null) {
            selectedPlatform = "auto";
        }

        selectedPlatform =
                selectedPlatform.toLowerCase(
                        Locale.ROOT
                );
    }

    private String detectPlatform() {

        String name =
                Bukkit.getName();

        if (name == null) {
            return "unknown";
        }

        String lower =
                name.toLowerCase(
                        Locale.ROOT
                );

        if (lower.contains("purpur")) {
            return "purpur";
        }

        if (lower.contains("paper")) {
            return "paper";
        }

        if (lower.contains("spigot")) {
            return "spigot";
        }

        if (lower.contains("bukkit")) {
            return "bukkit";
        }

        return "unknown";
    }

    private boolean checkPlatform() {

        if (!getConfig().getBoolean(
                "platform.strict",
                false
        )) {
            return true;
        }

        if ("auto".equals(
                selectedPlatform
        )) {
            return true;
        }

        return selectedPlatform.equals(
                detectedPlatform
        );
    }

    public boolean isWorldDisabled(
            World world
    ) {

        if (world == null) {
            return true;
        }

        if (!getConfig().getBoolean(
                "worlds.enabled",
                true
        )) {
            return false;
        }

        return getConfig()
                .getStringList(
                        "worlds.disabled-worlds"
                )
                .stream()
                .anyMatch(
                        world.getName()::equalsIgnoreCase
                );
    }

    public long getCombatDurationSeconds() {

        return Math.max(
                1L,
                getConfig().getLong(
                        "combat.duration-seconds",
                        10L
                )
        );
    }

    public boolean isPvPEnabled() {
        return pvpEnabled;
    }

    public void setPvPEnabled(
            boolean enabled
    ) {

        pvpEnabled = enabled;

        getConfig().set(
                "pvp.enabled",
                enabled
        );

        saveConfig();
    }

    public boolean canTogglePvP(
            CommandSender sender
    ) {

        boolean requirePermission =
                getConfig().getBoolean(
                        "pvp.command.require-permission",
                        true
                );

        if (!requirePermission) {
            return true;
        }

        String permission =
                getConfig().getString(
                        "pvp.command.permission",
                        "combatkeepinventory.pvp"
                );

        return sender.hasPermission(
                permission
        );
    }

    public String getDetectedPlatform() {
        return detectedPlatform;
    }

    public String getSelectedPlatform() {
        return selectedPlatform;
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public CombatManager getCombatManager() {
        return combatManager;
    }

    public WorldGuardHook getWorldGuardHook() {
        return worldGuardHook;
    }

    @Override
    public void onDisable() {

        if (combatManager != null) {
            combatManager.clear();
        }

        getLogger().info(
                "CombatKeepInventory "
                        + PLUGIN_VERSION
                        + " disabled."
        );
    }
}