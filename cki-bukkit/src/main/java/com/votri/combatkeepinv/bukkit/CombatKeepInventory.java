package com.votri.combatkeepinv.bukkit;

import com.votri.combatkeepinv.bukkit.api.BukkitCombatKeepInventoryAPI;
import com.votri.combatkeepinv.bukkit.bridge.CombatStateBridge;
import com.votri.combatkeepinv.bukkit.combat.BukkitCombatService;
import com.votri.combatkeepinv.bukkit.combat.CombatManager;
import com.votri.combatkeepinv.bukkit.command.CombatCommand;
import com.votri.combatkeepinv.bukkit.detector.PvPManagerDetector;
import com.votri.combatkeepinv.bukkit.hook.WorldGuardHook;
import com.votri.combatkeepinv.bukkit.listener.CombatListener;
import com.votri.combatkeepinv.bukkit.platform.BukkitPlatformDetector;
import com.votri.combatkeepinv.core.api.CombatKeepInventoryAPI;
import com.votri.combatkeepinv.core.api.CombatService;
import com.votri.combatkeepinv.core.platform.PlatformInfo;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CombatKeepInventory extends JavaPlugin {

    public static final String PLUGIN_VERSION =
            "1.1.0-SNAPSHOT-build5";

    private static final int CONFIG_VERSION = 3;

    private CombatManager combatManager;
    private BukkitCombatService combatService;
    private CombatStateBridge combatStateBridge;

    private PvPManagerDetector pvpManagerDetector;
    private WorldGuardHook worldGuardHook;
    private CombatListener combatListener;

    private PlatformInfo platformInfo;

    private File messageFile;
    private FileConfiguration messages;

    private String selectedPlatform;

    private BukkitTask combatCleanupTask;

    @Override
    public void onEnable() {

        /*
         * ==========================================================
         * CONFIGURATION
         * ==========================================================
         */

        migrateConfigIfRequired();
        saveDefaultConfig();

        /*
         * ==========================================================
         * MESSAGES
         * ==========================================================
         */

        saveDefaultMessages();
        loadMessages();

        /*
         * ==========================================================
         * PLATFORM
         * ==========================================================
         */

        detectPlatform();
        loadPlatformSelection();

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
         * ==========================================================
         * COMBAT STATE BRIDGE
         * ==========================================================
         *
         * CombatManager depends on CombatStateBridge.
         *
         * Therefore the initialization order MUST be:
         *
         * CombatStateBridge
         *        ↓
         * CombatManager
         *        ↓
         * BukkitCombatService
         */

        initializeCombatStateBridge();

        /*
         * ==========================================================
         * COMPONENTS
         * ==========================================================
         */

        initializeComponents();

        /*
         * ==========================================================
         * OPTIONAL DETECTORS
         * ==========================================================
         */

        if (pvpManagerDetector != null) {

            pvpManagerDetector.logWarningIfDetected();
        }

        /*
         * ==========================================================
         * CORE API
         * ==========================================================
         */

        registerApi();

        /*
         * ==========================================================
         * LISTENERS
         * ==========================================================
         */

        registerCombatListeners();

        /*
         * ==========================================================
         * COMBAT CLEANUP
         * ==========================================================
         *
         * Runs every second.
         *
         * This is important because an expired CombatTag must
         * generate END for Velocity even when nobody queries
         * that player's state.
         */

        startCombatCleanupTask();

        /*
         * ==========================================================
         * COMMAND
         * ==========================================================
         */

        registerCommand();

        /*
         * ==========================================================
         * STARTUP
         * ==========================================================
         */

        logStartupInformation();
    }

    @Override
    public void onDisable() {

        /*
         * Stop cleanup task first.
         */

        if (combatCleanupTask != null) {

            combatCleanupTask.cancel();
            combatCleanupTask = null;
        }

        /*
         * Clear combat state.
         *
         * CombatManager sends FORCE_END for active players.
         */

        if (combatManager != null) {

            combatManager.clear();
        }

        /*
         * Unregister public API.
         */

        unregisterApi();

        /*
         * Shutdown plugin messaging bridge.
         */

        if (combatStateBridge != null) {

            combatStateBridge.shutdown();
        }

        /*
         * Release references.
         */

        combatListener = null;
        pvpManagerDetector = null;
        worldGuardHook = null;
        combatService = null;
        combatManager = null;
        combatStateBridge = null;

        getLogger().info(
                "CombatKeepInventory "
                        + PLUGIN_VERSION
                        + " disabled."
        );
    }

    /*
     * ==========================================================
     * PLATFORM
     * ==========================================================
     */

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

    /*
     * ==========================================================
     * COMBAT STATE BRIDGE
     * ==========================================================
     */

    private void initializeCombatStateBridge() {

        if (combatStateBridge != null) {

            return;
        }

        combatStateBridge =
                new CombatStateBridge(this);

        getLogger().info(
                "CombatStateBridge initialized."
        );

        if (getConfig().getBoolean(
                "debug.combat",
                false
        )) {

            getLogger().info(
                    "[CombatBridge] channel="
                            + CombatStateBridge.CHANNEL
                            + " protocol="
                            + CombatStateBridge.PROTOCOL_VERSION
            );
        }
    }

    /*
     * ==========================================================
     * COMPONENTS
     * ==========================================================
     */

    private void initializeComponents() {

        long durationMillis =
                getCombatDurationSeconds()
                        * 1000L;

        /*
         * Bridge must exist before CombatManager.
         */

        if (combatStateBridge == null) {

            combatStateBridge =
                    new CombatStateBridge(this);
        }

        /*
         * CombatManager.
         */

        if (combatManager == null) {

            combatManager =
                    new CombatManager(
                            durationMillis,
                            combatStateBridge
                    );

        } else {

            combatManager.setDurationMillis(
                    durationMillis
            );
        }

        /*
         * BukkitCombatService.
         */

        if (combatService == null) {

            combatService =
                    new BukkitCombatService(
                            this,
                            combatManager
                    );
        }

        /*
         * WorldGuard.
         */

        if (worldGuardHook == null) {

            worldGuardHook =
                    new WorldGuardHook(
                            this
                    );
        }

        /*
         * PvPManager detection only.
         */

        if (pvpManagerDetector == null) {

            pvpManagerDetector =
                    new PvPManagerDetector(
                            this
                    );
        }
    }

    /*
     * ==========================================================
     * COMBAT CLEANUP
     * ==========================================================
     */

    private void startCombatCleanupTask() {

        if (combatCleanupTask != null) {

            combatCleanupTask.cancel();
        }

        combatCleanupTask =
                getServer()
                        .getScheduler()
                        .runTaskTimer(
                                this,
                                () -> {

                                    if (combatManager != null) {

                                        combatManager
                                                .cleanupExpired();
                                    }
                                },
                                20L,
                                20L
                        );
    }

    /*
     * ==========================================================
     * CORE API
     * ==========================================================
     */

    private void registerApi() {

        try {

            CombatKeepInventoryAPI.Provider.register(
                    new BukkitCombatKeepInventoryAPI(
                            this
                    )
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

            if (api instanceof BukkitCombatKeepInventoryAPI) {

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

    /*
     * ==========================================================
     * LISTENERS
     * ==========================================================
     */

    private void registerCombatListeners() {

        if (combatListener != null) {

            return;
        }

        combatListener =
                new CombatListener(
                        this,
                        combatService,
                        worldGuardHook
                );

        EventPriority priority =
                getListenerPriority();

        EventExecutor damageExecutor =
                (registeredListener, event) -> {

                    if (event
                            instanceof EntityDamageByEntityEvent damage) {

                        combatListener
                                .onEntityDamageByEntity(
                                        damage
                                );
                    }
                };

        EventExecutor deathExecutor =
                (registeredListener, event) -> {

                    if (event
                            instanceof PlayerDeathEvent death) {

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

    /*
     * ==========================================================
     * ACCESSORS
     * ==========================================================
     */

    public CombatService getCombatService() {

        if (combatService == null) {

            throw new IllegalStateException(
                    "Combat service has not been initialized."
            );
        }

        return combatService;
    }

    public CombatManager getCombatManager() {

        return combatManager;
    }

    public CombatStateBridge getCombatStateBridge() {

        if (combatStateBridge == null) {

            throw new IllegalStateException(
                    "CombatStateBridge has not been initialized."
            );
        }

        return combatStateBridge;
    }

    public WorldGuardHook getWorldGuardHook() {

        return worldGuardHook;
    }

    public PvPManagerDetector getPvPManagerDetector() {

        return pvpManagerDetector;
    }

    /*
     * ==========================================================
     * LISTENER PRIORITY
     * ==========================================================
     */

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

    /*
     * ==========================================================
     * COMMAND
     * ==========================================================
     */

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

    /*
     * ==========================================================
     * CONFIG MIGRATION
     * ==========================================================
     */

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

    /*
     * ==========================================================
     * MESSAGES
     * ==========================================================
     */

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

    /*
     * ==========================================================
     * RELOAD
     * ==========================================================
     */

    public void reloadPlugin() {

        reloadConfig();

        loadMessages();

        loadPlatformSelection();

        if (!checkPlatform()) {

            getLogger().warning(
                    "Configured platform mode does not "
                            + "match the detected platform."
            );
        }

        initializeComponents();

        if (pvpManagerDetector != null) {

            pvpManagerDetector.logWarningIfDetected();
        }

        getLogger().info(
                "CombatKeepInventory configuration reloaded."
        );

        getLogger().info(
                "Listener priority configured as "
                        + getListenerPriority()
        );
    }

    /*
     * ==========================================================
     * WORLD CONFIGURATION
     * ==========================================================
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
     * ==========================================================
     * COMBAT CONFIGURATION
     * ==========================================================
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
     * ==========================================================
     * PLATFORM API
     * ==========================================================
     */

    public PlatformInfo getPlatform() {

        return platformInfo;
    }

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

    /*
     * ==========================================================
     * STARTUP
     * ==========================================================
     */

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

        if (platformInfo != null) {

            getLogger().info(
                    "Minecraft version: "
                            + platformInfo.getMinecraftVersion()
            );

            getLogger().info(
                    "Implementation version: "
                            + platformInfo.getImplementationVersion()
            );
        }

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
                "Listener priority: "
                        + getListenerPriority()
        );

        getLogger().info(
                "CombatStateBridge: "
                        + (
                        combatStateBridge != null
                                ? "ENABLED"
                                : "DISABLED"
                )
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