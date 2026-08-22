package com.votri.combatkeepinv.bukkit;

import com.votri.combatkeepinv.bukkit.combat.CombatManager;
import com.votri.combatkeepinv.bukkit.command.CombatCommand;
import com.votri.combatkeepinv.bukkit.hook.WorldGuardHook;
import com.votri.combatkeepinv.bukkit.listener.CombatListener;
import com.votri.combatkeepinv.bukkit.platform.BukkitPlatformDetector;
import com.votri.combatkeepinv.core.api.CombatKeepInventoryAPI;
import com.votri.combatkeepinv.core.api.PlatformInfo;
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

    private PlatformInfo platformInfo;

    private File messageFile;
    private FileConfiguration messages;

    private boolean pvpEnabled;

    private String selectedPlatform;

    @Override
    public void onEnable() {

        /*
         * Configuration
         */
        migrateConfigIfRequired();
        saveDefaultConfig();

        /*
         * Messages
         */
        saveDefaultMessages();
        loadMessages();

        /*
         * Platform detection must happen before
         * platform validation and API registration.
         */
        detectPlatform();

        /*
         * Read configured platform mode.
         */
        loadPlatformSelection();

        /*
         * Validate selected platform if strict mode
         * is enabled.
         */
        if (!checkPlatform()) {

            getLogger().severe(
                    "Selected platform does not match "
                            + "the detected server platform."
            );

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);

            return;
        }

        /*
         * Bukkit-side components.
         */
        initializeComponents();

        /*
         * Public CKI API.
         */
        registerApi();

        /*
         * Bukkit listeners.
         */
        registerCombatListeners();

        /*
         * Commands.
         */
        registerCommand();

        /*
         * Startup information.
         */
        logStartupInformation();
    }

    @Override
    public void onDisable() {

        /*
         * Unregister the public API first.
         */
        unregisterApi();

        /*
         * Clear combat state.
         */
        if (combatManager != null) {
            combatManager.clear();
        }

        combatListener = null;
        worldGuardHook = null;
        combatManager = null;

        getLogger().info(
                "CombatKeepInventory "
                        + PLUGIN_VERSION
                        + " disabled."
        );
    }

    private void detectPlatform() {

        platformInfo =
                BukkitPlatformDetector.detect();
    }

    private void loadPlatformSelection() {

        selectedPlatform =
                getConfig().getString(
                        "platform.mode",
                        "auto"
                );

        if (selectedPlatform == null
                || selectedPlatform.isBlank()) {

            selectedPlatform = "auto";
        }

        selectedPlatform =
                selectedPlatform
                        .trim()
                        .toLowerCase(Locale.ROOT);
    }

    private String getDetectedPlatformName() {

        if (platformInfo == null) {
            return "unknown";
        }

        return platformInfo
                .getType()
                .name()
                .toLowerCase(Locale.ROOT);
    }

    private boolean checkPlatform() {

        boolean strict =
                getConfig().getBoolean(
                        "platform.strict",
                        false
                );

        if (!strict) {
            return true;
        }

        if ("auto".equals(selectedPlatform)) {
            return true;
        }

        return selectedPlatform.equals(
                getDetectedPlatformName()
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

    private void registerApi() {

        /*
         * Do not register twice.
         *
         * BukkitCombatKeepInventoryAPI is responsible
         * for adapting this plugin to the core API.
         */
        try {

            CombatKeepInventoryAPI.Provider.register(
                    new com.votri.combatkeepinv.bukkit.api
                            .BukkitCombatKeepInventoryAPI(this)
            );

        } catch (IllegalStateException exception) {

            getLogger().warning(
                    "CombatKeepInventory API was already registered."
            );
        }
    }

    private void unregisterApi() {

        try {

            CombatKeepInventoryAPI api =
                    CombatKeepInventoryAPI.get();

            if (api instanceof
                    com.votri.combatkeepinv.bukkit.api
                            .BukkitCombatKeepInventoryAPI) {

                CombatKeepInventoryAPI.Provider.unregister(
                        api
                );
            }

        } catch (IllegalStateException ignored) {

            /*
             * API was never registered.
             */
        }
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

                    if (event instanceof
                            EntityDamageByEntityEvent damage) {

                        combatListener
                                .onEntityDamageByEntity(
                                        damage
                                );
                    }
                };

        EventExecutor deathExecutor =
                (registeredListener, event) -> {

                    if (event instanceof
                            PlayerDeathEvent death) {

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

        if (value == null
                || value.isBlank()) {

            return EventPriority.HIGHEST;
        }

        try {

            return EventPriority.valueOf(
                    value
                            .trim()
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

    private void registerCommand() {

        PluginCommand command =
                getCommand("cki");

        if (command == null) {

            getLogger().severe(
                    "Command 'cki' is missing "
                            + "from plugin.yml!"
            );

            return;
        }

        CombatCommand handler =
                new CombatCommand(this);

        command.setExecutor(handler);
        command.setTabCompleter(handler);
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

    private void saveDefaultMessages() {

        if (!getDataFolder().exists()
                && !getDataFolder().mkdirs()) {

            getLogger().warning(
                    "Could not create plugin data folder."
            );
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

        /*
         * Platform itself does not change while
         * the Bukkit server is running.
         *
         * Only configuration selection is reloaded.
         */
        loadPlatformSelection();

        if (!checkPlatform()) {

            getLogger().warning(
                    "Configured platform mode does not "
                            + "match the detected platform."
            );
        }

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

        if (messages == null) {
            return color(fallback);
        }

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

        if (messages == null) {
            return List.of();
        }

        List<String> values =
                messages.getStringList(
                        path
                );

        List<String> result =
                new ArrayList<>(
                        values.size()
                );

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

    /*
     * ==========================================================
     * Public platform API
     * ==========================================================
     */

    public PlatformInfo getPlatform() {

        return platformInfo;
    }

    /*
     * Backwards-compatible getter.
     */
    @Deprecated(forRemoval = false)
    public String getDetectedPlatform() {

        return getDetectedPlatformName();
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

    private void logStartupInformation() {

        getLogger().info(
                "CombatKeepInventory "
                        + PLUGIN_VERSION
                        + " enabled."
        );

        getLogger().info(
                "Detected platform: "
                        + getDetectedPlatformName()
        );

        getLogger().info(
                "Minecraft version: "
                        + platformInfo.getMinecraftVersion()
        );

        getLogger().info(
                "Implementation version: "
                        + platformInfo.getImplementationVersion()
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
                        + (
                        pvpEnabled
                                ? "ENABLED"
                                : "DISABLED"
                )
        );

        getLogger().info(
                "Listener priority: "
                        + getListenerPriority()
        );

        getLogger().info(
                "WorldGuard: "
                        + (
                        worldGuardHook != null
                                && worldGuardHook.isAvailable()
                                ? "ENABLED"
                                : "NOT INSTALLED"
                )
        );
    }
}