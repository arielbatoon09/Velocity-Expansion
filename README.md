# Velocity Expansion for PlaceholderAPI

Fast, optimized expansion for getting player counts from Velocity proxy.

## ✨ Features

- ⚡ **Ultra-fast updates** - 3 second default (configurable down to 1s)
- 🚀 **Instant startup** - Starts checking in 1 tick (0.05 seconds)
- 🔧 **Folia-compatible** - Auto-detects and uses appropriate scheduler
- 💾 **Instant result** - Always shows real-time counts

## 📋 Requirements

- Paper/Folia 1.21+
- Java 21+
- PlaceholderAPI 2.11.5+
- Velocity proxy

## 🔧 Installation

1. Place `Velocity-Expansion.jar` in `plugins/PlaceholderAPI/expansions/`
2. Restart server or `/papi reload`
3. Done!

## 📝 Placeholders

| Placeholder | Description |
|------------|-------------|
| `%velocity_<server>%` | Players on specific server |
| `%velocity_total%` | Total network players |
| `%velocity_all%` | Same as total |

## 🚀 Why This Expansion?

- ✅ Clean, production-ready code
- ✅ Folia-compatible with auto-detection
- ✅ Minimal logging overhead
- ✅ Optimized for Velocity modern mode
- ✅ Thread-safe implementation

## 📦 Build

```bash
mvn clean package
```

Output: `target/Velocity-Expansion.jar`