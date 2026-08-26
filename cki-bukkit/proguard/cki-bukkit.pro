# ================================================================
# CombatKeepInventory Bukkit
# Production ProGuard configuration
#
# Version: 1.1.0
# Java: 21
#
# Repository:
# https://github.com/thanhnhanyt53/CombatKeepInventory
#
# Purpose:
# - Protect Bukkit implementation classes
# - Preserve Bukkit/Paper reflective entry points
# - Preserve CKI public API
# - Preserve Bukkit event listeners
# - Preserve command handlers
# - Preserve Velocity bridge
# - Preserve plugin metadata/resources
#
# IMPORTANT:
# This file intentionally does NOT use:
#   -ignorewarnings
#   -keepresourcefiles
#
# ================================================================


# ================================================================
# GENERAL
# ================================================================

-dontusemixedcaseclassnames

-dontskipnonpubliclibraryclasses

-dontpreverify

-renamesourcefileattribute SourceFile

-keepattributes SourceFile,LineNumberTable


# ================================================================
# JVM / CLASS METADATA
# ================================================================

-keepattributes Exceptions

-keepattributes Signature

-keepattributes InnerClasses,EnclosingMethod

-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations

-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

-keepattributes AnnotationDefault


# ================================================================
# MAIN BUKKIT PLUGIN
#
# plugin.yml loads this class directly as:
#
# main: com.votri.combatkeepinv.bukkit.CombatKeepInventory
#
# Therefore the class name and constructor MUST remain unchanged.
# ================================================================

-keep public class com.votri.combatkeepinv.bukkit.CombatKeepInventory {
    public <init>();
    public void onEnable();
    public void onDisable();
}


# ================================================================
# BUKKIT API ADAPTER
#
# External plugins may obtain:
#
# BukkitCombatKeepInventoryAPI
#
# and use the public API exposed by CKI.
#
# Keep the adapter class and public members.
# ================================================================

-keep public class com.votri.combatkeepinv.bukkit.api.BukkitCombatKeepInventoryAPI {
    public <init>(...);
    public *;
}


# ================================================================
# CORE API
#
# cki-core is bundled into the Bukkit production JAR.
#
# These classes are part of CKI's public integration contract.
#
# External plugins may compile against:
#
# CombatKeepInventoryAPI
# CombatService
# CombatTag
# CombatResult
# CombatRefreshEvent
# CombatState
# DeathContext
# DeathResult
# PlatformInfo
# PlatformCapability
# etc.
#
# DO NOT OBFUSCATE THESE PUBLIC CONTRACT CLASSES.
# ================================================================

-keep public interface com.votri.combatkeepinv.core.api.** {
    public *;
}

-keep public class com.votri.combatkeepinv.core.api.** {
    public *;
}

-keep public enum com.votri.combatkeepinv.core.api.** {
    public *;
}

-keep public interface com.votri.combatkeepinv.core.platform.** {
    public *;
}

-keep public class com.votri.combatkeepinv.core.platform.** {
    public *;
}

-keep public enum com.votri.combatkeepinv.core.platform.** {
    public *;
}


# ================================================================
# CORE EVENT CLASSES
#
# If CombatRefreshEvent is an event class rather than a simple
# interface, retain its complete public contract.
# ================================================================

-keep public class com.votri.combatkeepinv.core.event.** {
    public *;
}

-keep public interface com.votri.combatkeepinv.core.event.** {
    public *;
}


# ================================================================
# COMBAT STATE BRIDGE
#
# Bukkit -> Velocity communication.
#
# CombatStateBridge may be created/referenced directly by the
# Bukkit plugin and communicates through Velocity plugin messaging.
#
# Keep its class name and public members.
# ================================================================

-keep public class com.votri.combatkeepinv.bukkit.bridge.CombatStateBridge {
    public <init>(...);
    public *;
}


# ================================================================
# BUKKIT LISTENERS
#
# Bukkit discovers @EventHandler methods reflectively.
#
# Keep listener classes and their event methods.
# ================================================================

-keep class com.votri.combatkeepinv.bukkit.listener.** {
    *;
}


# ================================================================
# EXPLICIT EVENT HANDLERS
#
# Additional protection for methods annotated with Bukkit's
# EventHandler annotation.
# ================================================================

-keepclasseswithmembers,includedescriptorclasses class * {
    @org.bukkit.event.EventHandler <methods>;
}


# ================================================================
# BUKKIT COMMANDS
#
# plugin.yml / Bukkit command system may instantiate or access
# command executors.
# ================================================================

-keep public class com.votri.combatkeepinv.bukkit.command.CombatCommand {
    public <init>(...);
    public *;
}


# ================================================================
# BUKKIT PLATFORM DETECTOR
#
# This class uses Bukkit server APIs to determine implementation
# and version.
#
# It is directly referenced by CombatKeepInventory, so its members
# do not strictly require keep rules, but keeping public API makes
# the platform information contract stable.
# ================================================================

-keep public class com.votri.combatkeepinv.bukkit.platform.BukkitPlatformDetector {
    public <init>(...);
    public *;
}


# ================================================================
# WORLDGUARD HOOK
#
# Optional integration.
#
# Keep class name because optional integrations may use reflection
# or class-presence detection.
# ================================================================

-keep public class com.votri.combatkeepinv.bukkit.hook.WorldGuardHook {
    public <init>(...);
    public *;
}


# ================================================================
# PVPMANAGER DETECTOR
#
# Optional detection integration.
# ================================================================

-keep public class com.votri.combatkeepinv.bukkit.detector.PvPManagerDetector {
    public <init>(...);
    public *;
}


# ================================================================
# COMBAT SERVICE
#
# BukkitCombatService is the platform implementation of the core
# CombatService contract.
#
# Its public API is potentially consumed through Bukkit API access,
# but its private implementation can remain obfuscated.
# ================================================================

-keep public class com.votri.combatkeepinv.bukkit.combat.BukkitCombatService {
    public <init>(...);
    public *;
}


# ================================================================
# COMBAT MANAGER
#
# CombatManager is internally referenced by BukkitCombatService.
#
# Keep public members for API compatibility where exposed.
# Private implementation may still be optimized/renamed.
# ================================================================

-keep public class com.votri.combatkeepinv.bukkit.combat.CombatManager {
    public <init>(...);
    public *;
}


# ================================================================
# ENUMS
#
# Preserve enum values and valueOf/values methods.
# ================================================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# ================================================================
# SERIALIZATION / REFLECTION SUPPORT
#
# Preserve classes that may be instantiated reflectively.
# ================================================================

-keepclassmembers class * {
    public <init>();
}


# ================================================================
# BUKKIT / PAPER ANNOTATIONS
#
# Preserve annotation metadata used by Bukkit/Paper.
# ================================================================

-keepattributes *Annotation*


# ================================================================
# PAPER / BUKKIT EXTERNAL API
#
# Paper API is provided by the server and MUST NOT be included
# or obfuscated.
#
# These are library classes in the Maven build.
# ================================================================

-dontwarn org.bukkit.**

-dontwarn io.papermc.paper.**

-dontwarn net.kyori.adventure.**


# ================================================================
# OPTIONAL INTEGRATIONS
#
# WorldGuard / related APIs may not exist on every server.
# These warnings are expected when the dependency is optional.
# ================================================================

-dontwarn com.sk89q.worldguard.**

-dontwarn com.sk89q.worldedit.**

-dontwarn me.NoChance.PvPManager.**


# ================================================================
# JAVA / JETBRAINS ANNOTATIONS
# ================================================================

-dontwarn org.jetbrains.annotations.**

-dontwarn org.intellij.lang.annotations.**


# ================================================================
# GSON / CONFIGURATION LIBRARIES
#
# Some transitive classes may be referenced by Paper or optional
# libraries without being required by CKI at runtime.
# ================================================================

-dontwarn com.google.gson.**

-dontwarn org.spongepowered.configurate.**


# ================================================================
# SERVICE PROVIDERS
# ================================================================

-keep class * implements java.util.ServiceLoader {
    *;
}


# ================================================================
# PLUGIN RESOURCE FILES
#
# ProGuard normally copies resources from the input JAR.
#
# DO NOT use:
#
# -keepresourcefiles
#
# because the ProGuard version used by the current build rejects
# that option.
#
# plugin.yml/config.yml/message.yml therefore remain ordinary
# resources and are preserved by the JAR processing pipeline.
# ================================================================


# ================================================================
# KEEP PLUGIN DESCRIPTOR REFERENCES
#
# These classes are referenced from plugin.yml.
# ================================================================

-keepnames class com.votri.combatkeepinv.bukkit.CombatKeepInventory

-keepnames class com.votri.combatkeepinv.bukkit.command.CombatCommand


# ================================================================
# KEEP CORE API NAMES
#
# This is important for third-party plugin compilation/runtime
# compatibility.
# ================================================================

-keepnames public class com.votri.combatkeepinv.core.api.**

-keepnames public interface com.votri.combatkeepinv.core.api.**

-keepnames public enum com.votri.combatkeepinv.core.api.**

-keepnames public class com.votri.combatkeepinv.core.platform.**

-keepnames public interface com.votri.combatkeepinv.core.platform.**

-keepnames public enum com.votri.combatkeepinv.core.platform.**


# ================================================================
# WARNINGS
#
# DO NOT globally suppress all warnings.
#
# If ProGuard reports unresolved PROGRAM CLASS MEMBERS, that is a
# build inconsistency and must be fixed rather than hidden.
# ================================================================

# Intentionally no:
#
# -ignorewarnings
#
# Intentionally no:
#
# -dontwarn com.votri.combatkeepinv.**
#
# because CKI's own unresolved references must fail the production
# build.