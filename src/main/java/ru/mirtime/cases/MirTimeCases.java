/*
 *    __  __ _        _____ _                
 *   |  \/  (_)_ __  |_   _(_)_ __ ___   ___ 
 *   | |\/| | | '__|   | | | | '_ ` _ \ / _ \
 *   | |  | | | |      | | | | | | | | |  __/
 *   |_|  |_|_|_|      |_| |_|_| |_| |_|\___|
 *                                           
 *   Project: Mir Time | Author: Ars3nBar
 */
package ru.mirtime.cases;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MirTimeCases extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {

    private File keysFile;
    private YamlConfiguration keysDb;
    private final Set<UUID> spinning = new HashSet<>();
    private final Map<Integer, String> slotToCase = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keysFile = new File(getDataFolder(), "keys.yml");
        if (!keysFile.exists()) {
            try { keysFile.createNewFile(); } catch (IOException ignored) {}
        }
        keysDb = YamlConfiguration.loadConfiguration(keysFile);

        if (getCommand("cases") != null) {
            getCommand("cases").setExecutor(this);
            getCommand("cases").setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        spawnHolograms();

        Bukkit.getConsoleSender().sendMessage(color("&#0055ff   __  __ _        _____ _                "));
        Bukkit.getConsoleSender().sendMessage(color("&#0055ff  |  \\/  (_)_ __  |_   _(_)_ __ ___   ___ "));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  | |\\/| | | '__|   | | | | '_ ` _ \\ / _ \\"));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  | |  | | | |      | | | | | | | | |  __/"));
        Bukkit.getConsoleSender().sendMessage(color("&#00FF66  |_|  |_|_|_|      |_| |_|_| |_| |_|\\___|"));
        Bukkit.getConsoleSender().sendMessage(color("&#00E5FF  MirTimeCases v1.0.0 успешно запущен!"));
    }

    private void saveKeys() {
        try { keysDb.save(keysFile); } catch (IOException ignored) {}
    }

    private int getKeys(String player, String caseId) {
        return keysDb.getInt(player.toLowerCase() + "." + caseId.toLowerCase(), 0);
    }

    private void addKeys(String player, String caseId, int amount) {
        String path = player.toLowerCase() + "." + caseId.toLowerCase();
        keysDb.set(path, Math.max(0, keysDb.getInt(path, 0) + amount));
        saveKeys();
    }

    private double getPlaceholderValue(Player p, String placeholder) {
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Method setPlaceholders = papi.getMethod("setPlaceholders", Player.class, String.class);
                String res = (String) setPlaceholders.invoke(null, p, placeholder);
                return Double.parseDouble(res.replaceAll("[^0-9.]", ""));
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    private void removeOldHolograms(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        for (Entity ent : loc.getWorld().getNearbyEntities(loc, 2.5, 4.0, 2.5)) {
            if (ent instanceof ArmorStand as && as.getScoreboardTags().contains("mt_case_holo")) {
                as.remove();
            }
        }
    }

    private void spawnHolograms() {
        String wName = getConfig().getString("chest.world", "");
        World w = Bukkit.getWorld(wName);
        if (w == null) return;
        int x = getConfig().getInt("chest.x");
        int y = getConfig().getInt("chest.y");
        int z = getConfig().getInt("chest.z");
        if (x == 0 && y == 80 && z == 0) return;

        Location base = new Location(w, x + 0.5, y, z + 0.5);
        removeOldHolograms(base);

        createHoloLine(base.clone().add(0, 1.35, 0), "&#0055ff&l⚡ &#00E5FF&lКЕЙСЫ MIR-TIME &#0055ff&l⚡");
        createHoloLine(base.clone().add(0, 1.05, 0), "&#00FF66Нажмите ПКМ&f, чтобы открыть");
    }

    private void createHoloLine(Location loc, String text) {
        ArmorStand as = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setVisible(false);
        as.setGravity(false);
        as.setMarker(true);
        as.setCustomName(color(text));
        as.setCustomNameVisible(true);
        as.addScoreboardTag("mt_case_holo");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String prefix = color(getConfig().getString("prefix", "&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» "));
        if (args.length == 0) {
            if (sender instanceof Player p) openCasesMenu(p);
            return true;
        }
        if (!sender.hasPermission("mirtime.cases.admin")) {
            sender.sendMessage(prefix + color("&cНедостаточно прав!"));
            return true;
        }
        String sub = List.of(args).get(0).toLowerCase();
        switch (sub) {
            case "spawn", "setblock" -> {
                if (!(sender instanceof Player p)) return true;
                String oldWorld = getConfig().getString("chest.world", "");
                if (Bukkit.getWorld(oldWorld) != null) {
                    Location oldLoc = new Location(Bukkit.getWorld(oldWorld), getConfig().getInt("chest.x"), getConfig().getInt("chest.y"), getConfig().getInt("chest.z"));
                    removeOldHolograms(oldLoc);
                    if (oldLoc.getBlock().getType() == Material.ENDER_CHEST) oldLoc.getBlock().setType(Material.AIR);
                }

                Block b = p.getLocation().getBlock();
                b.setType(Material.ENDER_CHEST);
                p.teleport(p.getLocation().add(0, 1.0, 0));

                getConfig().set("chest.world", b.getWorld().getName());
                getConfig().set("chest.x", b.getX());
                getConfig().set("chest.y", b.getY());
                getConfig().set("chest.z", b.getZ());
                saveConfig();
                spawnHolograms();

                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
                p.sendMessage(prefix + color("&#00FF66Сундук кейсов установлен на ваших координатах (&f" + b.getX() + " " + b.getY() + " " + b.getZ() + "&#00FF66)!"));
            }
            case "give" -> {
                if (args.length < 4) {
                    sender.sendMessage(prefix + color("&fИспользование: &#00FF66/cases give <ник> <кейс> <кол-во>"));
                    return true;
                }
                String targetName = List.of(args).get(1);
                String caseId = List.of(args).get(2).toLowerCase();
                if (!getConfig().contains("cases." + caseId)) {
                    sender.sendMessage(prefix + color("&cКейс с таким ID не найден в config.yml!"));
                    return true;
                }
                int amt = 1;
                try { amt = Math.max(1, Integer.parseInt(List.of(args).get(3))); } catch (Exception ignored) {}
                addKeys(targetName, caseId, amt);
                sender.sendMessage(prefix + color("&#00FF66Выдано &f" + amt + " кл. &#00FF66от кейса &#00E5FF" + caseId + " &#00FF66игроку &f" + targetName));
                Player online = Bukkit.getPlayerExact(targetName);
                if (online != null) {
                    online.sendMessage(prefix + color("&#00FF66Вы получили &f" + amt + " ключ(ей) &#00FF66от кейса &#00E5FF" + caseId + "&f! Открыть: &#00FF66/cases"));
                }
            }
            case "reload" -> {
                reloadConfig();
                spawnHolograms();
                sender.sendMessage(prefix + color("&#00FF66Конфиг MirTimeCases перезагружен!"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("mirtime.cases.admin")) return List.of();
        if (args.length == 1) return List.of("spawn", "give", "reload");
        if (args.length == 2 && List.of(args).get(0).equalsIgnoreCase("give")) return null;
        if (args.length == 3 && List.of(args).get(0).equalsIgnoreCase("give")) {
            ConfigurationSection sec = getConfig().getConfigurationSection("cases");
            return (sec != null) ? new ArrayList<>(sec.getKeys(false)) : List.of();
        }
        if (args.length == 4 && List.of(args).get(0).equalsIgnoreCase("give")) return List.of("1", "3", "5", "10");
        return List.of();
    }
    private boolean isCaseChest(Block b) {
        if (b == null) return false;
        String w = getConfig().getString("chest.world", "");
        return b.getWorld().getName().equalsIgnoreCase(w)
                && b.getX() == getConfig().getInt("chest.x")
                && b.getY() == getConfig().getInt("chest.y")
                && b.getZ() == getConfig().getInt("chest.z");
    }

    @EventHandler
    public void onChestClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (isCaseChest(e.getClickedBlock())) {
            e.setCancelled(true);
            openCasesMenu(e.getPlayer());
        }
    }

    @EventHandler
    public void onChestBreak(BlockBreakEvent e) {
        if (isCaseChest(e.getBlock()) && !e.getPlayer().hasPermission("mirtime.cases.admin")) {
            e.setCancelled(true);
        }
    }

    private void openCasesMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, color("&#0055ff&l⚡ &#00E5FF&lКЕЙСЫ MIR-TIME"));
        ItemStack blue = item(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪", null);
        ItemStack cyan = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&#00E5FF▫", null);
        for (int s : new int[]{0,1,7,8,9,17,18,19,25,26}) inv.setItem(s, blue);
        for (int s : new int[]{2,3,4,5,6,20,21,22,23,24}) inv.setItem(s, cyan);

        slotToCase.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("cases");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int slot = sec.getInt(key + ".slot", 13);
                Material mat = Material.matchMaterial(sec.getString(key + ".material", "CHEST"));
                if (mat == null) mat = Material.CHEST;
                int count = getKeys(p.getName(), key);

                List<String> lore = new ArrayList<>();
                for (String line : sec.getStringList(key + ".lore")) lore.add(color(line));
                lore.add("");
                lore.add(color(" &#0055ff● &fВаших ключей: " + (count > 0 ? "&#00FF66&l" + count + " шт." : "&c0 шт.")));

                if (count > 0) {
                    lore.add(color(" &#00FF66➥ ЛКМ &f— Открыть кейс по ключу!"));
                } else if (sec.getBoolean(key + ".token-buy.enabled", false)) {
                    int price = sec.getInt(key + ".token-buy.price", 500);
                    lore.add(color(" &#00E5FF➥ ПКМ &f— Купить открытие за &#00FF66" + price + " токенов&f!"));
                } else {
                    lore.add(color(" &7Купить ключи: &#00E5FFdon.mir-time.ru"));
                }

                inv.setItem(slot, item(mat, sec.getString(key + ".name", key), lore));
                slotToCase.put(slot, key);
            }
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (title.contains("Открытие:") || title.contains("КЕЙСЫ MIR-TIME")) e.setCancelled(true);
        if (!title.contains("КЕЙСЫ MIR-TIME")) return;

        String caseId = slotToCase.get(e.getRawSlot());
        if (caseId == null || spinning.contains(p.getUniqueId())) return;

        String prefix = color(getConfig().getString("prefix", "&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» "));
        int keys = getKeys(p.getName(), caseId);

        if (keys > 0) {
            addKeys(p.getName(), caseId, -1);
            startRoulette(p, caseId);
            return;
        }

        String path = "cases." + caseId + ".token-buy";
        if (e.isRightClick() && getConfig().getBoolean(path + ".enabled", false)) {
            int price = getConfig().getInt(path + ".price", 500);
            String ph = getConfig().getString(path + ".placeholder", "%playerpoints_points%");
            double bal = getPlaceholderValue(p, ph);
            if (bal < price) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                p.sendMessage(prefix + color("&cНедостаточно токенов! Нужно: &#00E5FF" + price + " токенов&c."));
                return;
            }
            String takeCmd = getConfig().getString(path + ".take-command", "p take %player% " + price);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), takeCmd.replace("%player%", p.getName()));
            startRoulette(p, caseId);
            return;
        }

        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        p.sendMessage(prefix + color("&cУ вас нет ключей от этого кейса! Купить: &#00E5FFdon.mir-time.ru"));
    }

    private record RewardEntry(String name, Material mat, int chance, boolean broadcast, List<String> commands) {}

    private void startRoulette(Player p, String caseId) {
        ConfigurationSection sec = getConfig().getConfigurationSection("cases." + caseId + ".rewards");
        if (sec == null) return;

        List<RewardEntry> pool = new ArrayList<>();
        int totalWeight = 0;
        for (String rKey : sec.getKeys(false)) {
            String rName = sec.getString(rKey + ".name", "Награда");
            Material mat = Material.matchMaterial(sec.getString(rKey + ".material", "DIAMOND"));
            if (mat == null) mat = Material.DIAMOND;
            int chance = Math.max(1, sec.getInt(rKey + ".chance", 10));
            boolean bc = sec.getBoolean(rKey + ".broadcast", false);
            List<String> cmds = sec.getStringList(rKey + ".commands");
            pool.add(new RewardEntry(rName, mat, chance, bc, cmds));
            totalWeight += chance;
        }
        if (pool.isEmpty()) return;

        int roll = new Random().nextInt(totalWeight);
        int cur = 0;
        RewardEntry winner = pool.get(0);
        for (RewardEntry r : pool) {
            cur += r.chance;
            if (roll < cur) {
                winner = r;
                break;
            }
        }

        List<RewardEntry> tape = new ArrayList<>();
        Random rnd = new Random();
        for (int i = 0; i < 35; i++) {
            tape.add(i == 28 ? winner : pool.get(rnd.nextInt(pool.size())));
        }

        String caseTitle = getConfig().getString("cases." + caseId + ".name", "Кейс");
        Inventory inv = Bukkit.createInventory(null, 27, color("&#0055ff&l⚡ Открытие: " + caseTitle));
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        inv.setItem(4, item(Material.HOPPER, "&#00FF66&l▼ ВАШ ВЫИГРЫШ ▼", null));
        inv.setItem(22, item(Material.NETHER_STAR, "&#00E5FF&l▲ MIR-TIME ▲", null));

        p.openInventory(inv);
        spinning.add(p.getUniqueId());

        RewardEntry finalWinner = winner;
        new BukkitRunnable() {
            int step = 0;
            int delay = 0;

            @Override
            public void run() {
                if (step > 24) {
                    if (delay < (step - 24)) {
                        delay++;
                        return;
                    }
                    delay = 0;
                }

                for (int slot = 9; slot <= 17; slot++) {
                    RewardEntry re = tape.get(step + (slot - 9));
                    inv.setItem(slot, item(re.mat, re.name, List.of("&7Приз из кейса " + caseTitle)));
                }
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);

                if (step >= 24) {
                    cancel();
                    spinning.remove(p.getUniqueId());
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    p.sendMessage(color(getConfig().getString("prefix") + "&#00FF66Поздравляем! Вы выиграли: " + finalWinner.name));

                    if (finalWinner.broadcast) {
                        Bukkit.broadcastMessage(color("&#0055ff&l[КЕЙСЫ] &#00E5FF" + p.getName() + " &fоткрыл " + caseTitle + " &fи выбил: " + finalWinner.name + "&f!"));
                    }
                    for (String cmd : finalWinner.commands) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
                    }
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (p.getOpenInventory().getTitle().contains("Открытие:")) p.closeInventory();
                        }
                    }.runTaskLater(MirTimeCases.this, 45L);
                }
                step++;
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null) {
                List<String> list = new ArrayList<>();
                for (String s : lore) list.add(color(s));
                meta.setLore(list);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            is.setItemMeta(meta);
        }
        return is;
    }

    public static String color(String text) {
        if (text == null) return "";
        Pattern pattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder rep = new StringBuilder("§x");
            for (char c : hex.toCharArray()) rep.append('§').append(c);
            matcher.appendReplacement(buffer, rep.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
