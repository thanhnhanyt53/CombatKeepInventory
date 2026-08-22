package com.votri.combatkeepinv.core.platform;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable description of the runtime platform.
 */
public final class PlatformInfo {

    private final PlatformType type;
    private final String implementationName;
    private final String implementationVersion;
    private final String minecraftVersion;
    private final String apiVersion;

    public PlatformInfo(
            PlatformType type,
            String implementationName,
            String implementationVersion,
            String minecraftVersion,
            String apiVersion
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.implementationName =
                Objects.requireNonNullElse(
                        implementationName,
                        "Unknown"
                );
        this.implementationVersion =
                Objects.requireNonNullElse(
                        implementationVersion,
                        "Unknown"
                );
        this.minecraftVersion =
                Objects.requireNonNullElse(
                        minecraftVersion,
                        "Unknown"
                );
        this.apiVersion =
                Objects.requireNonNullElse(
                        apiVersion,
                        "Unknown"
                );
    }

    public PlatformType getType() {
        return type;
    }

    public String getImplementationName() {
        return implementationName;
    }

    public String getImplementationVersion() {
        return implementationVersion;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public Optional<String> getApiVersion() {
        if ("Unknown".equalsIgnoreCase(apiVersion)) {
            return Optional.empty();
        }

        return Optional.of(apiVersion);
    }

    public boolean is(PlatformType platformType) {
        return type == platformType;
    }

    public boolean isBukkitFamily() {
        return type.isBukkitFamily();
    }

    public boolean isProxy() {
        return type.isProxy();
    }

    public boolean supports(
            PlatformCapability capability
    ) {
        Objects.requireNonNull(
                capability,
                "capability"
        );

        return switch (capability) {
            case COMBAT,
                 DEATH,
                 INVENTORY,
                 DAMAGE_ATTRIBUTION ->
                    isBukkitFamily();

            case PROXY_SESSION,
                 SERVER_SWITCH,
                 CLUSTER_EXIT,
                 PLUGIN_MESSAGING ->
                    isProxy();
        };
    }

    @Override
    public String toString() {
        return "PlatformInfo{" +
                "type=" + type +
                ", implementationName='" +
                implementationName + '\'' +
                ", implementationVersion='" +
                implementationVersion + '\'' +
                ", minecraftVersion='" +
                minecraftVersion + '\'' +
                ", apiVersion='" +
                apiVersion + '\'' +
                '}';
    }
}