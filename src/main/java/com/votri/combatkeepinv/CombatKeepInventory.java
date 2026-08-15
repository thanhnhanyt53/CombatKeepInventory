package com.votri.combatkeepinv;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventExecutor;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CombatKeepInventory extends JavaPlugin {

    /*
     * ============================================================
     * VERSION
     * ============================================================
     */

    public static final String PLUGIN_VERSION =
            "1.1.0-SNAPSHOT-build2";

    private static final int CONFIG_VERSION = 2;

    /*
     * ============================================================
     * COMPONENTS
     * ============================================================
     */

    private CombatManager combatManager;
    private WorldGuardHook worldGuardHook;

    /*
     * ============================================================
     * MESSAGES
     * ============================================================
     */

    private File messageFile;
    private FileConfiguration messages;

    /*
     * ============================================================
     * STATE
     * ============================================================
     */

    private boolean pvpEnabled;

    private String detectedPlatform;
    private String selectedPlatform;

    /*
     * ============================================================
     * ENABLE
     * ============================================================
     */

    @Override
    public void onEnable() {

        /*
         * Migrate old configuration BEFORE saveDefaultConfig().
         *
         * If the installed config is old, it is renamed and a
         * completely fresh config from the JAR is created.
         */
        migrateConfigIfRequired();

        saveDefaultConfig();

        saveDefaultMessages();
        loadMessages();

        loadPlatform();

        if (!checkPlatform()) {

            getLogger().severe(
                    "Selected platform does not match the detected "
                            + "server platform."
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

        getLogger().info(
                "PvPManager integration: REMOVED"
        );

        getLogger().info(
                "Combat logout punishment: REMOVED"
        );
    }

    /*
     * ============================================================
     * INITIALIZE COMPONENTS
     * ============================================================
     */

    private void initializeComponents() {

        long seconds =
                getCombatDurationSeconds();

        combatManager =
                new CombatManager(
                        seconds * 1000L
                );

        worldGuardHook =
                new WorldGuardHook(this);

        pvpEnabled =
                getConfig().getBoolean(
                        "pvp.enabled",
                        true
                );
    }

    /*
     * ============================================================
     * EVENT REGISTRATION
     * ============================================================
     *
     * Bukkit's @EventHandler annotation cannot have a dynamic
     * EventPriority. Therefore listeners are registered manually
     * so listener-priority can actually be controlled by config.yml.
     */

    private void registerCombatListeners() {

        CombatListener listener =
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
                        listener.onEntityDamageByEntity(damage);
                    }
                };

        EventExecutor deathExecutor =
                (registeredListener, event) -> {

                    if (event instanceof PlayerDeathEvent death) {
                        listener.onPlayerDeath(death);
                    }
                };

        getServer()
                .getPluginManager()
                .registerEvent(
                        EntityDamageByEntityEvent.class,
                        listener,
                        priority,
                        damageExecutor,
                        this
                );

        getServer()
                .getPluginManager()
                .registerEvent(
                        PlayerDeathEvent.class,
                        listener,
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

    /*
     * ============================================================
     * CONFIGURABLE EVENT PRIORITY
     * ============================================================
     */

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
                            .toUpperCase(Locale.ROOT)
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

    /*
     * ============================================================
     * CONFIG MIGRATION
     * ============================================================
     */

    private void migrateConfigIfRequired() {

        File configFile =
                new File(
                        getDataFolder(),
                        "config.yml"
                );

        /*
         * No old config exists.
         */
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

        /*
         * Already current.
         */
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
                    "Old configuration has been backed up to: "
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

            /*
             * Do not overwrite the user's configuration if
             * migration failed.
             */
            throw new IllegalStateException(
                    "Configuration migration failed.",
                    exception
            );
        }
    }

    /*
     * ============================================================
     * COMMAND
     * ============================================================
     */

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

    /*
     * ============================================================
     * MESSAGES
     * ============================================================
     */

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
                YamlConfiguration.loadConfiguration(
                        messageFile
                );
    }

    /*
     * ============================================================
     * RELOAD
     * ============================================================
     */

    public void reloadPlugin() {

        /*
         * /cki reload intentionally does NOT rename the current
         * config. Automatic migration only happens during startup.
         */
        reloadConfig();

        loadMessages();
        loadPlatform();

        if (combatManager != null) {
            combatManager.clear();
        }

        initializeComponents();

        getLogger().info(
                "CombatKeepInventory configuration reloaded."
        );

        getLogger().info(
                "Listener priority: "
                        + getListenerPriority()
        );
    }

    /*
     * ============================================================
     * MESSAGES API
     * ============================================================
     */

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
                messages.getStringList(path);

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

    /*
     * ============================================================
     * PLATFORM
     * ============================================================
     */

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

    /*
     * ============================================================
     * WORLD
     * ============================================================
     */

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

    /*
     * ============================================================
     * COMBAT
     * ============================================================
     */

    public long getCombatDurationSeconds() {

        return Math.max(
                1L,
                getConfig().getLong(
                        "combat.duration-seconds",
                        10L
                )
        );
    }

    /*
     * ============================================================
     * PVP
     * ============================================================
     */

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

    /*
     * ============================================================
     * GETTERS
     * ============================================================
     */

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

    /*
     * ============================================================
     * DISABLE
     * ============================================================
     */

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