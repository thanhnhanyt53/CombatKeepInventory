# CombatKeepInventory 1.2.0 — Universal Distribution

The `cki-universal` module packages the Bukkit/Paper and Velocity implementations into one distributable JAR.

## Runtime

- Paper/Bukkit loads `com.votri.combatkeepinv.bukkit.CombatKeepInventory` from `plugin.yml`.
- Velocity loads `com.votri.combatkeepinv.velocity.CombatKeepInventoryVelocity` from `velocity-plugin.json`.
- The two runtime descriptors coexist in the same JAR.
- Velocity resources use `velocity-config.yml` and `velocity-message.yml` so they cannot collide with Bukkit `config.yml` and `message.yml`.

## Build

```bash
mvn clean package -U
```

Universal artifact:

```text
cki-universal/target/cki-universal-1.2.0.jar
```

For Modrinth, upload the universal JAR as the single primary file for the release rather than putting separate Bukkit and Velocity JARs in Additional Files.
