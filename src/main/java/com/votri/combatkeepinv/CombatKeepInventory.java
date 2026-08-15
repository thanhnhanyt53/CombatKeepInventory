package com.votri.combatkeepinv;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Locale;

public final class CombatKeepInventory extends JavaPlugin {

    private CombatManager combatManager;
    private WorldGuardHook worldGuardHook;
    private PvPManagerHook pvpManagerHook;
    private InventoryJournalManager inventoryJournalManager;

    private File messageFile;
    private FileConfiguration messages;

    private boolean pvpEnabled;

    private String detectedPlatform;
    private String selectedPlatform;

    @Override
    public void onEnable() {

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

        /*
         * ============================================================
         * COMBAT LISTENER
         * ============================================================
         */

        getServer()
                .getPluginManager()
                .registerEvents(
                        new CombatListener(
                                this,
                                combatManager,
                                worldGuardHook,
                                pvpManagerHook,
                                inventoryJournalManager
                        ),
                        this
                );

        /*
         * ============================================================
         * COMBAT LOG LISTENER
         * ============================================================
         */

        getServer()
                .getPluginManager()
                .registerEvents(
                        new CombatLogListener(
                                this,
                                combatManager,
                                pvpManagerHook,
                                inventoryJournalManager
                        ),
                        this
                );

        registerCommand();

        getLogger().info(
                "CombatKeepInventory v1.1.0-SNAPSHOT-build1 enabled."
        );

        getLogger().info(
                "Detected platform: " +
                        detectedPlatform
        );

        getLogger().info(
                "Selected platform: " +
                        selectedPlatform
        );

        getLogger().info(
                "Combat duration: " +
                        getCombatDurationSeconds() +
                        " seconds"
        );

        getLogger().info(
                "PvP: " +
                        (pvpEnabled
                                ? "ENABLED"
                                : "DISABLED")
        );

        getLogger().info(
                "WorldGuard: " +
                        (worldGuardHook.isAvailable()
                                ? "ENABLED"
                                : "NOT INSTALLED")
        );

        getLogger().info(
                "PvPManager: " +
                        (pvpManagerHook.isAvailable()
                                ? "ENABLED"
                                : "NOT INSTALLED")
        );

        getLogger().info(
                "Inventory journal: ENABLED"
        );
    }

    private void initializeComponents() {

        long seconds =
                getCombatDurationSeconds();

        combatManager =
                new CombatManager(
                        seconds * 1000L
                );

        worldGuardHook =
                new WorldGuardHook(this);

        /*
         * PvPManagerHook uses reflection at runtime.
         * PvPManager is NOT a mandatory Maven dependency.
         */
        pvpManagerHook =
                new PvPManagerHook(
                        this,
                        combatManager
                );

        /*
         * Journal manager MUST be created before
         * CombatListener / CombatLogListener.
         */
        inventoryJournalManager =
                new InventoryJournalManager(
                        this
                );

        pvpEnabled =
                getConfig().getBoolean(
                        "pvp.enabled",
                        true
                );
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

        if (combatManager != null) {
            combatManager.clear();
        }

        initializeComponents();

        getLogger().info(
                "CombatKeepInventory configuration reloaded."
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

    public java.util.List<String> getMessageList(
            String path
    ) {

        java.util.List<String> values =
                messages.getStringList(
                        path
                );

        java.util.List<String> result =
                new java.util.ArrayList<>();

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

    public PvPManagerHook getPvpManagerHook() {
        return pvpManagerHook;
    }

    public InventoryJournalManager getInventoryJournalManager() {
        return inventoryJournalManager;
    }

    @Override
    public void onDisable() {

        /*
         * IMPORTANT:
         *
         * Journal files are intentionally NOT deleted.
         * An unfinished combat-logout transaction must survive
         * a server restart.
         */

        if (inventoryJournalManager != null) {
            inventoryJournalManager.shutdown();
        }

        if (combatManager != null) {
            combatManager.clear();
        }

        getLogger().info(
                "CombatKeepInventory v1.1.0-SNAPSHOT-build1 disabled."
        );
    }
}