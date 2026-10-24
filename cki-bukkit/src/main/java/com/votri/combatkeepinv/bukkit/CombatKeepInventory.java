package com.votri.combatkeepinv.bukkit;

import com.votri.combatkeepinv.bukkit.api.BukkitCombatKeepInventoryAPI;
import com.votri.combatkeepinv.bukkit.bridge.CombatStateBridge;
import com.votri.combatkeepinv.bukkit.combat.BukkitCombatService;
import com.votri.combatkeepinv.bukkit.combat.CombatManager;
import com.votri.combatkeepinv.bukkit.command.CombatCommand;
import com.votri.combatkeepinv.bukkit.debug.DebugManager;
import com.votri.combatkeepinv.bukkit.detector.PvPManagerDetector;
import com.votri.combatkeepinv.bukkit.version.VersionAdapter;
import com.votri.combatkeepinv.bukkit.version.VersionAdapterFactory;
import com.votri.combatkeepinv.bukkit.hook.WorldGuardHook;
import com.votri.combatkeepinv.bukkit.listener.CombatListener;
import com.votri.combatkeepinv.bukkit.platform.BukkitPlatformDetector;
import com.votri.combatkeepinv.core.api.CombatAPI;
import com.votri.combatkeepinv.core.api.CombatService;
import com.votri.combatkeepinv.core.api.DamageAPI;
import com.votri.combatkeepinv.core.api.DeathAPI;
import com.votri.combatkeepinv.core.api.InventoryAPI;
import com.votri.combatkeepinv.core.api.PlatformAPI;
import com.votri.combatkeepinv.core.internal.DefaultCombatAPI;
import com.votri.combatkeepinv.core.internal.DefaultDamageAPI;
import com.votri.combatkeepinv.core.internal.DefaultDeathAPI;
import com.votri.combatkeepinv.core.internal.DefaultInventoryAPI;
import com.votri.combatkeepinv.core.internal.DefaultPlatformAPI;
import com.votri.combatkeepinv.core.platform.PlatformInfo;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
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
            "1.2.5";

    private static final int CONFIG_VERSION = 4;

    private CombatManager combatManager;
    private BukkitCombatService combatService;
    private CombatStateBridge combatStateBridge;

    private PvPManagerDetector pvpManagerDetector;
    private WorldGuardHook worldGuardHook;
    private CombatListener combatListener;
    private DebugManager debugManager;
    private VersionAdapter versionAdapter;

    private PlatformInfo platformInfo;

    private CombatAPI combatAPI;
    private DamageAPI damageAPI;
    private DeathAPI deathAPI;
    private InventoryAPI inventoryAPI;
    private PlatformAPI platformAPI;

    private File messageFile;
    private FileConfiguration messages;

    private String selectedPlatform;

    private BukkitTask combatCleanupTask;

    @Override
    public void onEnable() {

        /*
         * ==========================================================
        int pluginId = 34903; // Thay bằng ID thật
        new Metrics(this, pluginId);
         

         /* CONFIGURATION
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
         * VERSION ADAPTER
         * ==========================================================
         */

        initializeVersionAdapter();

        /*
         * ==========================================================
         * COMBAT STATE BRIDGE
         * ==========================================================
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
         * DEBUG MANAGER
         * ==========================================================
         */

        initializeDebugManager();

        /*
         * ==========================================================
         * CORE API FACADES
         * ==========================================================
         */

        initializeCoreAPIFacades();

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

        if (combatCleanupTask != null) {
            combatCleanupTask.cancel();
            combatCleanupTask = null;
        }

        if (combatManager != null) {
            combatManager.clear();
        }

        unregisterApi();

        if (combatStateBridge != null) {
            combatStateBridge.shutdown();
        }

        if (debugManager != null) {
            debugManager.clear();
        }

        combatListener = null;
        pvpManagerDetector = null;
        worldGuardHook = null;
        combatService = null;
        combatManager = null;
        combatStateBridge = null;
        debugManager = null;
        versionAdapter = null;

        combatAPI = null;
        damageAPI = null;
        deathAPI = null;
        inventoryAPI = null;
        platformAPI = null;

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

        if (isDebugBridge()) {

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

        if (combatStateBridge == null) {

            combatStateBridge =
                    new CombatStateBridge(this);
        }

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
         * CombatManager may replace its core session manager during
         * reload. BukkitCombatService captures that manager, so the
         * adapter must be recreated after every component rebuild.
         */
        combatService =
                new BukkitCombatService(
                        this,
                        combatManager
                );

        if (worldGuardHook == null) {

            worldGuardHook =
                    new WorldGuardHook(this);
        }

        if (pvpManagerDetector == null) {

            pvpManagerDetector =
                    new PvPManagerDetector(this);
        }
    }

    /*
     * ==========================================================
     * DEBUG MANAGER
     * ==========================================================
     */

    private void initializeDebugManager() {

        /*
         * Intentionally NOT recreated on reload: this would
         * silently drop every admin who had live debug watch
         * mode turned on via /cki debug.
         */
        if (debugManager == null) {

            debugManager =
                    new DebugManager(this);
        }
    }

    public DebugManager getDebugManager() {

        if (debugManager == null) {

            debugManager =
                    new DebugManager(this);
        }

        return debugManager;
    }

    /*
     * ==========================================================
     * VERSION ADAPTER
     * ==========================================================
     */

    private void initializeVersionAdapter() {

        if (versionAdapter == null) {

            versionAdapter =
                    VersionAdapterFactory.create(
                            getServer().getBukkitVersion(),
                            getLogger()
                    );
        }
    }

    public VersionAdapter getVersionAdapter() {

        if (versionAdapter == null) {

            /*
             * Should only happen if something calls this before
             * onEnable() reaches initializeVersionAdapter(); keep
             * combat processing from NPEing on a race by probing
             * on demand instead.
             */
            versionAdapter =
                    VersionAdapterFactory.create(
                            getServer().getBukkitVersion(),
                            getLogger()
                    );
        }

        return versionAdapter;
    }

    /*
     * ==========================================================
     * CORE API FACADES INITIALIZATION
     * ==========================================================
     */

    private void initializeCoreAPIFacades() {

        if (combatService == null) {
            throw new IllegalStateException(
                    "Combat service has not been initialized."
            );
        }

        if (platformInfo == null) {
            throw new IllegalStateException(
                    "Platform information has not been initialized."
            );
        }

        this.combatAPI = new DefaultCombatAPI(
                combatService.getSessionManager()
        );

        this.damageAPI = new DefaultDamageAPI(
                combatService.getDamageAttributionService()
        );

        this.deathAPI = new DefaultDeathAPI(
                combatService.getDeathService()
        );

        this.inventoryAPI = new DefaultInventoryAPI(
                combatService.getDefaultInventoryPolicy()
        );

        this.platformAPI = new DefaultPlatformAPI(
                platformInfo
        );
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

                                    if (combatManager == null) {
                                        return;
                                    }

                                    for (java.util.UUID expired
                                            : combatManager.cleanupExpired()) {

                                        org.bukkit.entity.Player player =
                                                getServer().getPlayer(
                                                        expired
                                                );

                                        if (player != null
                                                && player.isOnline()) {

                                            player.sendMessage(
                                                    getPrefixedMessage(
                                                            "combat.combat-ended",
                                                            "&aYour combat tag "
                                                                    + "has expired."
                                                    )
                                            );
                                        }
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

    private BukkitCombatKeepInventoryAPI registeredApi;

    private void registerApi() {

        if (registeredApi != null) {
            return;
        }

        BukkitCombatKeepInventoryAPI api =
                new BukkitCombatKeepInventoryAPI(this);

        try {
            BukkitCombatKeepInventoryAPI.register(api);
            registeredApi = api;
        } catch (IllegalStateException exception) {
            getLogger().warning(
                    "CombatKeepInventory API was already registered."
            );
        }
    }

    private void unregisterApi() {

        if (registeredApi == null) {
            return;
        }

        BukkitCombatKeepInventoryAPI.unregister(
                registeredApi
        );

        registeredApi = null;
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

        /*
         * NOTE: onEntityExplode() carries @EventHandler but, like
         * the two listeners above, is wired manually rather than
         * via registerEvents(combatListener, this) -- doing both
         * would double-register onEntityDamageByEntity/onPlayerDeath
         * (they also carry @EventHandler) and process every hit and
         * death twice. Without this explicit registerEvent call,
         * onEntityExplode() is never invoked by Bukkit at all, which
         * silently disables all player-TNT death attribution.
         */
        EventExecutor explodeExecutor =
                (registeredListener, event) -> {

                    if (event instanceof EntityExplodeEvent explode) {

                        combatListener
                                .onEntityExplode(
                                        explode
                                );
                    }
                };

        /*
         * Bukkit keeps EntityDamageEvent and
         * EntityDamageByEntityEvent on separate handler lists
         * (the subclass overrides getHandlers()), so this does
         * NOT also receive EntityDamageByEntityEvent instances
         * and will not double-process melee/projectile hits.
         * It exists specifically to catch explosion damage that
         * Bukkit delivers without a "by entity" damager, so that
         * non-lethal TNT hits still start/refresh CombatTag.
         */
        EventExecutor plainDamageExecutor =
                (registeredListener, event) -> {

                    if (event instanceof EntityDamageEvent damage) {

                        combatListener
                                .onEntityDamage(
                                        damage
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
                        EntityDamageEvent.class,
                        combatListener,
                        priority,
                        plainDamageExecutor,
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

        getServer()
                .getPluginManager()
                .registerEvent(
                        EntityExplodeEvent.class,
                        combatListener,
                        priority,
                        explodeExecutor,
                        this
                );

        getLogger().info(
                "Combat listeners registered at "
                        + priority
                        + " priority (including TNT explosion "
                        + "attribution)."
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
     * CONFIGURATION GETTERS
     * ==========================================================
     */

    /**
     * Returns whether CKI combat processing is enabled.
     */
    public boolean isCombatEnabled() {

        return isEnabled()
                && getConfig().getBoolean(
                        "combat.enabled",
                        true
                );
    }

    /**
     * Debug for CombatTag START/REFRESH/result state.
     */
    public boolean isDebugCombat() {

        return getConfig().getBoolean(
                "debug.combat",
                false
        );
    }

    /**
     * Debug for raw damage filtering.
     */
    public boolean isDebugDamage() {

        return getConfig().getBoolean(
                "debug.damage",
                false
        );
    }

    /**
     * Debug for death evaluation.
     */
    public boolean isDebugDeath() {

        return getConfig().getBoolean(
                "debug.death",
                false
        );
    }

    /**
     * Debug for Bukkit -> Velocity bridge.
     */
    public boolean isDebugBridge() {

        return getConfig().getBoolean(
                "debug.bridge",
                false
        );
    }

    /**
     * Experience policy for deaths without active CombatTag.
     */
    public boolean shouldKeepDeathExperience() {

        return getConfig().contains("inventory.policies.pve-death.keep-experience")
                ? getConfig().getBoolean(
                        "inventory.policies.pve-death.keep-experience",
                        true
                )
                : getConfig().getBoolean(
                        "death.keep-experience",
                        true
                );
    }

    /**
     * Level-keep policy for deaths without active CombatTag
     * (used by the bypass-permission path, which has no
     * resolved InventoryPolicy of its own).
     */
    public boolean shouldKeepDeathLevels() {

        return getConfig().contains("inventory.policies.pve-death.keep-levels")
                ? getConfig().getBoolean(
                        "inventory.policies.pve-death.keep-levels",
                        true
                )
                : getConfig().getBoolean(
                        "inventory.keep-levels",
                        true
                );
    }

    /**
     * Experience policy specifically for deaths while an active
     * CombatTag exists.
     */
    public boolean shouldKeepCombatDeathExperience() {

        return getConfig().getBoolean(
                "inventory.policies.combat-death.keep-experience",
                true
        );
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

    /**
     * Same as {@link #getMessage(String, String)} but prepends
     * the configured {@code prefix} key from message.yml.
     *
     * <p>Used for direct player-facing notifications (combat
     * tagged/ended, inventory kept/dropped/partially kept) so the
     * {@code prefix} key actually has an effect. Command output
     * such as {@code /cki info} intentionally keeps using the
     * un-prefixed {@link #getMessage(String, String)} instead --
     * repeating a prefix on every line of a multi-line panel would
     * look wrong.</p>
     */
    public String getPrefixedMessage(
            String path,
            String fallback
    ) {

        String prefix =
                messages == null
                        ? ""
                        : messages.getString(
                                "prefix",
                                ""
                        );

        return color(prefix)
                + getMessage(
                        path,
                        fallback
                );
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

        /*
         * CombatManager có thể thay đổi session manager
         * khi reload duration.
         *
         * Vì vậy phải rebuild các API facade.
         */
        initializeCoreAPIFacades();

        if (pvpManagerDetector != null) {
            pvpManagerDetector.logWarningIfDetected();
        }

        getLogger().info(
                "CombatKeepInventory configuration reloaded."
        );

        getLogger().info(
                "Combat enabled: "
                        + isCombatEnabled()
        );

        getLogger().info(
                "Combat duration: "
                        + getCombatDurationSeconds()
                        + " seconds"
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
     * PUBLIC PLATFORM API
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
     * API GETTERS
     * ==========================================================
     */

    /**
     * Returns the public Combat API.
     *
     * @return combat API facade
     */
    public CombatAPI getCombatAPI() {
        return combatAPI;
    }

    /**
     * Returns the public Damage API.
     *
     * @return damage API facade
     */
    public DamageAPI getDamageAPI() {
        return damageAPI;
    }

    /**
     * Returns the public Death API.
     *
     * @return death API facade
     */
    public DeathAPI getDeathAPI() {
        return deathAPI;
    }

    /**
     * Returns the public Inventory API.
     *
     * @return inventory API facade
     */
    public InventoryAPI getInventoryAPI() {
        return inventoryAPI;
    }

    /**
     * Returns the public Platform API.
     *
     * @return platform API facade
     */
    public PlatformAPI getPlatformAPI() {
        return platformAPI;
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
                "Combat enabled: "
                        + isCombatEnabled()
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
