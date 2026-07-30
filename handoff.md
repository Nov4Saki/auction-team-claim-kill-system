# 📋 GuildCore Handoff & Next Steps Guide: GUI Aesthetic Upgrade

Welcome! This document outlines the architectural rules, compatibility constraints, codebase sitemap, and specific instructions for the next agent working on **GuildCore**.

---

## 🎯 Primary Task for Next Agent: Complete GUI Aesthetic Overhaul

Your primary objective is to **redesign and elevate all Inventory GUIs** in GuildCore to make them visually stunning, cohesive, modern, and high-end. 

### GUIs to Upgrade:
1. **Auction House (`/ah`) & My Listings (`/ah list`)**: Add category selector banners, price tags, shulker preview frames, and pagination buttons.
2. **Server Admin Shop (`/shop`)**: Enhance category grids, item display frames, buy/sell price indicators (`$1,000`), and border fillers.
3. **Modular Choice Crates (`/crate` & `/crate admin`)**: Premium crate inspection menus, choice selection cards, confirmation windows, and admin management hub.
4. **Master Admin Settings Hub (`/settings`)**: Modern control panel with toggle indicators (`[✔ ENABLED]` / `[✖ DISABLED]`), category icons, and sub-GUI navigation (Economy, Combat, Claims, Scoreboard, Auction, Debug).
5. **Team Management Hub (`/team`)**: Team statistics, bank balance indicators, vault page switches, and upgrade cards.

---

## 📜 Critical Rules & Compatibility Constraints

You MUST strictly follow these technical rules when writing or modifying code in this repository:

### 1. Server Environment & Compatibility
- **Target Platform**: Minecraft 1.21.11 / Paper 1.21.11 & Folia 1.21.11 (Canvas server).
- **Dual Compatibility**: All code must run seamlessly on **both Paper and Folia**.
- **`plugin.yml`**: Must retain `folia-supported: true`.

### 2. Folia Threading Rules
- **No Direct Async Bukkit Calls**: Never invoke Bukkit API calls (e.g., `player.openInventory()`, `player.sendMessage()`, `player.getInventory().addItem()`, or entity teleports) directly inside async tasks or database callbacks (`dbManager.executeAsync(...)`).
- **`SchedulerWrapper` Usage**: Always wrap player-facing actions inside `scheduler.runSync(player, () -> { ... })`.
- **Async Teleportation**: Always use `player.teleportAsync(location)` for teleports.
- **Namespaced Commands**: Always match command names via `command.getName().toLowerCase()` (e.g., handles both `/tpa` and `/guildcore:tpa`).

### 3. Scoreboard Safety
- **Scoreboard Objectives**: Always use `Bukkit.getScoreboardManager().getMainScoreboard()` for objective management to ensure Folia thread safety. Never call `getNewScoreboard()` on entity scheduler threads.

### 4. Database & Serialization
- **SQLite Database**: Managed via `DatabaseManager` with HikariCP connection pooling.
- **Item Serialization**: Use `ItemSerializer.serializeItem(ItemStack)` and `ItemSerializer.deserializeItem(String)` (utilizing `item.serializeAsBytes()` / `ItemStack.deserializeBytes()`).

---

## 📁 Key File Sitemap & Architecture

- `src/main/java/com/guildcore/GuildCorePlugin.java`: Plugin main entry point and event/command registration.
- `src/main/java/com/guildcore/gui/`:
  - `GUIManager.java`: Central GUI factory for opening admin settings, teams, auctions, stats.
  - `GUIClickListener.java`: Global click & interaction handler for all GUI InventoryHolders.
  - `GUIItemBuilder.java`: Fluent helper class for creating custom items, lore, and MiniMessage text.
  - `ChatInputListener.java`: Intercepts chat messages for numeric & text setting inputs.
- `src/main/java/com/guildcore/shop/`: `ShopManager`, `ShopCommand`, `ShopGUIHolder`, `ShopAdminGUIHolder`.
- `src/main/java/com/guildcore/crates/`: `CrateManager`, `CrateCommand`, `CrateGUIHolder`, `CrateAdminHubHolder`, `CrateConfirmGUIHolder`.
- `src/main/java/com/guildcore/auction/`: `AuctionManager`, `AuctionCommand`, `AuctionGUIHolder`.
- `src/main/java/com/guildcore/scoreboard/`: `ScoreboardManager.java`.
- `src/main/java/com/guildcore/scheduler/`: `SchedulerWrapper.java`.

---

## 🛠 Build, Deploy & Repository Workflow

### Build Command:
```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.1.1\plugins\maven\lib\maven3\bin\mvn.cmd" clean package
```

### Deployment Copy Command:
```powershell
powershell -Command "Copy-Item -Path 'target\GuildCore-1.0.0.jar' -Destination 'C:\projects\Minecraft\fully mixed plugin\GuildCore-1.0.0.jar' -Force"
```

### GitHub Repository:
- **URL**: `https://github.com/Nov4Saki/auction-team-claim-kill-system.git`
- **Branch**: `main`
- Push updates after building:
```powershell
git add . ; git commit -m "Upgrade GUI aesthetics across all menus" ; git push origin main
```
