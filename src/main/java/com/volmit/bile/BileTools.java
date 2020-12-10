package com.volmit.bile;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import com.volmit.volume.cluster.DataCluster;
import com.volmit.volume.lang.collections.GList;
import com.volmit.volume.lang.collections.YAMLClusterPort;

public class BileTools extends JavaPlugin implements Listener {

	private SlaveBileServer srv;

	public static BileTools bile;

	private static HashMap<File, Long> mod, las;

	private File folder, backoff;

	public String tag;

	private int cd = 10;

	public static DataCluster cfg;

	public static void l(String s) {
		System.out.println("[Bile]: " + s);
	}

	public static void main(String[] a) {
		l("Init Standalone");

		File ff = new File("plugins");
		File fb = new File(ff, "BileTools");
		if (!fb.exists()) {
			fb.mkdirs();
		}
		l("===========================================");
		l("Plugins Folder: " + ff.getAbsolutePath());
		l("Bile Folder: " + fb.getAbsolutePath());

		File cf = new File(fb, "config.yml");
		l("Init Ghost Plugin");
		l("Load config: " + cf.getAbsolutePath());

		l("Start Pool");
		ExecutorService svc = Executors.newWorkStealingPool(8);
		try {
			readTheConfig(fb);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		if (!cfg.getBoolean("remote-deploy.master.master-enabled")) {
			l("Nothing to do. Master is not enabled");
			System.exit(0);
		}

		l("Service Start");
		l("===========================================");
		mod = new HashMap<>();
		las = new HashMap<>();

		for (File i : ff.listFiles()) {
			if (i.isFile() && i.getName().endsWith(".jar")) {
				mod.put(i, i.lastModified());
				las.put(i, i.length());
				l("Now tracking: " + i.getName());
			}
		}

		new Thread(() -> {
			while (!Thread.interrupted()) {
				try {
					for (File i : ff.listFiles()) {
						if (i.isDirectory() || !i.getName().endsWith(".jar")) {
							continue;
						}

						boolean send = false;

						if (!mod.containsKey(i)) {
							send = true;
							mod.put(i, i.lastModified());
							las.put(i, i.length());
						} else if (mod.get(i) != i.lastModified() || las.get(i) != i.length()) {
							send = true;
							mod.put(i, i.lastModified());
							las.put(i, i.length());
						}

						if (send) {
							boolean al = false;

							for (String j : cfg.getStringList("remote-deploy.master.master-deploy-signatures")) {
								if (i.getName().toLowerCase().startsWith(j.toLowerCase())) {
									al = true;
									break;
								}
							}

							if (al) {
								for (String j : cfg.getStringList("remote-deploy.master.master-deploy-to")) {
									svc.submit(() -> {
										try {
											streamFile(i, j.split(":")[0], Integer.valueOf(j.split(":")[1]),
													j.split(":")[2]);
											l(i.getName() + " -> " + j.split(":")[0] + ":" + j.split(":")[1]);
										} catch (Exception e) {
											e.printStackTrace();
										}
									});
								}
							}
						}
					}

					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}, "File Tracker").start();
		l("File Threads Started: 1t/1000ms");
		l("===========================================");
	}

	public static void streamFile(File f, String address, int port, String password)
			throws UnknownHostException, IOException {
		Socket s = new Socket(address, port);
		DataOutputStream dos = new DataOutputStream(s.getOutputStream());
		dos.writeUTF(password);
		dos.writeUTF(f.getName());

		FileInputStream fin = new FileInputStream(f);
		byte[] buffer = new byte[8192];
		int read = 0;

		while ((read = fin.read(buffer)) != -1) {
			dos.write(buffer, 0, read);
		}

		fin.close();
		dos.flush();
		s.close();
	}

	private static void readTheConfig(File df) throws IOException, Exception {
		DataCluster cc = new DataCluster();
		cc.set("remote-deploy.slave.slave-enabled", false);
		cc.set("remote-deploy.slave.slave-port", 9876);
		cc.set("remote-deploy.slave.slave-payload", "pickapassword");
		cc.set("remote-deploy.master.master-enabled", false);
		cc.set("remote-deploy.master.master-deploy-to", new GList<String>().qadd("yourserver.com:9876:password"));
		cc.set("remote-deploy.master.master-deploy-signatures",
				new GList<String>().qadd("MyPlugin").qadd("AnotherPlugin"));
		cfg = cc;

		File f = new File(df, "config.yml");
		f.getParentFile().mkdirs();

		if (!f.exists()) {
			new YAMLClusterPort().fromCluster(cc).save(f);
		}

		FileConfiguration fc = new YamlConfiguration();
		fc.load(f);
		cfg = new YAMLClusterPort().toCluster(fc);
	}

	@Override
	public void onEnable() {
		try {
			readTheConfig(getDataFolder());
		} catch (Exception e) {
			System.out.println("Unable to read the config...");
			e.printStackTrace();
		}

		if (cfg.getBoolean("remote-deploy.slave.slave-enabled")) {
			getLogger().info("Starting Remote Slave Server on *:" + cfg.getInt("remote-deploy.slave.slave-port"));

			try {
				srv = new SlaveBileServer();
				srv.start();
				getLogger().info("Remote Slave Server online!");
			} catch (Exception e) {
				getLogger()
						.warning("Starting Remote Slave Server on *:" + cfg.getInt("remote-deploy.slave.slave-port"));
				e.printStackTrace();
			}
		}

		cd = 10;
		bile = this;
		tag = ChatColor.GREEN + "[" + ChatColor.DARK_GRAY + "Bile" + ChatColor.GREEN + "]: " + ChatColor.GRAY;
		mod = new HashMap<>();
		las = new HashMap<>();

		folder = getDataFolder().getParentFile();
		backoff = new File(getDataFolder(), "backoff");
		backoff.mkdirs();

		PluginCommand cmd = getCommand("bile");
		cmd.setExecutor(this);
		cmd.setTabCompleter(this);

		Bukkit.getPluginManager().registerEvents(this, this);

		getServer().getScheduler().scheduleSyncRepeatingTask(this, this::onTick, 10, 0);
	}

	public boolean isBackoff(Player p) {
		return new File(backoff, p.getUniqueId().toString()).exists();
	}

	public void toggleBackoff(Player p) {
		File file = new File(backoff, p.getUniqueId().toString());
		if (file.exists()) {
			file.delete();
		} else {
			file.mkdirs();
		}
	}

	@Override
	public void onDisable() {
		if (srv != null && srv.isAlive()) {
			srv.interrupt();

			try {
				srv.join();
				System.out.println("Bile Slave Server shut down.");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void reset(File f) {
		mod.put(f, f.length());
		las.put(f, f.lastModified());
	}

	public void onTick() {
		if (cd > 0) {
			cd--;
		}

		for (File i : folder.listFiles()) {
			if (!i.getName().toLowerCase().endsWith(".jar") || !i.isFile()) {
				continue;
			}

			if (!mod.containsKey(i)) {
				getLogger().log(Level.INFO, "Now Tracking: " + i.getName());

				CompletableFuture.supplyAsync(() -> {
					BileUtils.backup(BileUtils.getPlugin(i));
					return null;
				});

				mod.put(i, i.length());
				las.put(i, i.lastModified());

				if (cd == 0) {
					Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
						try {
							BileUtils.load(i);

							for (Player k : Bukkit.getOnlinePlayers()) {
								if (k.hasPermission("bile.use")) {
									k.sendMessage(tag + "Hot Dropped " + ChatColor.WHITE + i.getName());
								}
							}
						} catch (Throwable e) {
							for (Player k : Bukkit.getOnlinePlayers()) {
								if (k.hasPermission("bile.use")) {
									k.sendMessage(tag + "Failed to hot drop " + ChatColor.RED + i.getName());
								}
							}
						}
					}, 5);
				}
			}

			if (mod.get(i) != i.length() || las.get(i) != i.lastModified()) {
				mod.put(i, i.length());
				las.put(i, i.lastModified());

				for (Plugin j : Bukkit.getServer().getPluginManager().getPlugins()) {
					if (BileUtils.getPluginFile(j) == null
							|| !BileUtils.getPluginFile(j).getName().equals(i.getName())) {
						continue;
					}

					getLogger().log(Level.INFO, "File change detected: " + i.getName());
					getLogger().log(Level.INFO, "Identified Plugin: " + j.getName() + " <-> " + i.getName());
					getLogger().log(Level.INFO, "Reloading: " + j.getName());

					try {
						if (!cfg.getBoolean("remote-deploy.master.master-enabled")) {
							BileUtils.reload(j);

							for (Player k : Bukkit.getOnlinePlayers()) {
								if (k.hasPermission("bile.use")) {
									k.sendMessage(tag + "Reloaded " + ChatColor.WHITE + j.getName());
								}
							}

							continue;
						}

						if (!cfg.getStringList("remote-deploy.master.master-deploy-signatures").contains(j.getName())) {
							continue;
						}

						CompletableFuture.supplyAsync(() -> {
							for (String g : cfg.getStringList("remote-deploy.master.master-deploy-to")) {
								try {
									streamFile(i, g.split(":")[0], Integer.valueOf(g.split(":")[1]), g.split(":")[2]);
								} catch (NumberFormatException e) {
									e.printStackTrace();
									System.out.println("Invalid format");
								} catch (UnknownHostException e) {
									e.printStackTrace();
									System.out.println("Invalid host");
								} catch (IOException e) {
									e.printStackTrace();
									System.out.println("Invalid connection");
								}
							}

							for (Player k : Bukkit.getOnlinePlayers()) {
								if (k.hasPermission("bile.use")) {
									k.sendMessage(tag + "Deployed " + ChatColor.WHITE + j.getName() + ChatColor.GRAY
											+ " to " + cfg.getStringList("remote-deploy.master.master-deploy-to").size()
											+ " remote server(s)");
								}
							}

							Bukkit.getScheduler().scheduleSyncDelayedTask(BileTools.bile, () -> {
								try {
									BileUtils.reload(j);

									for (Player k : Bukkit.getOnlinePlayers()) {
										if (k.hasPermission("bile.use")) {
											k.sendMessage(tag + "Reloaded " + ChatColor.WHITE + j.getName());
										}
									}
								} catch (Throwable e) {
									e.printStackTrace();

									for (Player k : Bukkit.getOnlinePlayers()) {
										if (k.hasPermission("bile.use")) {
											k.sendMessage(tag + "Failed to Reload " + ChatColor.RED + j.getName());
										}
									}
								}
							}, 5);

							return true;
						});
					} catch (Throwable e) {
						e.printStackTrace();

						for (Player k : Bukkit.getOnlinePlayers()) {
							if (k.hasPermission("bile.use")) {
								k.sendMessage(tag + "Failed to Reload " + ChatColor.RED + j.getName());
							}
						}

					}

					break;
				}
			}
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (command.getName().equalsIgnoreCase("biletools")) {
			if (!sender.hasPermission("bile.use")) {
				sender.sendMessage(tag + "You need bile.use or OP.");
				return true;
			}

			if (args.length == 0) {
				sender.sendMessage(tag + "/// - Ingame dev mode toggle");
				sender.sendMessage(tag + "/bile load <plugin>");
				sender.sendMessage(tag + "/bile unload <plugin>");
				sender.sendMessage(tag + "/bile reload <plugin>");
				sender.sendMessage(tag + "/bile install <plugin> [version]");
				sender.sendMessage(tag + "/bile uninstall <plugin>");
				sender.sendMessage(tag + "/bile library [plugin]");
				return true;
			}

			if (args[0].equalsIgnoreCase("load")) {
				if (args.length < 1) {
					sender.sendMessage(tag + "/bile load <PLUGIN>");
					return true;
				}

				for (int i = 1; i < args.length; i++) {
					try {
						File s = BileUtils.getPluginFile(args[i]);
						if (s == null) {
							sender.sendMessage(tag + "Couldn't find \"" + args[i] + "\".");
							continue;
						}

						Plugin plugin = BileUtils.getPluginByName(args[i]);
						if (plugin == null) {
							continue;
						}

						BileUtils.load(s);
						String n = plugin.getName();
						sender.sendMessage(tag + "Loaded " + ChatColor.WHITE + n + ChatColor.GRAY + " from "
								+ ChatColor.WHITE + s.getName());
					} catch (Exception e) {
						e.printStackTrace();
						sender.sendMessage(tag + "Couldn't load or find \"" + args[i] + "\".");
					}
				}

				return true;
			}

			if (args[0].equalsIgnoreCase("uninstall")) {
				if (args.length < 1) {
					sender.sendMessage(tag + "/bile uninstall <PLUGIN>");
					return true;
				}

				for (int i = 1; i < args.length; i++) {
					try {
						File s = BileUtils.getPluginFile(args[i]);
						if (s == null) {
							sender.sendMessage(tag + "Couldn't find \"" + args[i] + "\".");
							continue;
						}

						String n = BileUtils.getPluginName(s);
						BileUtils.delete(s);

						if (!s.exists()) {
							sender.sendMessage(tag + "Uninstalled " + ChatColor.WHITE + n + ChatColor.GRAY + " from "
									+ ChatColor.WHITE + s.getName());
						} else {
							sender.sendMessage(tag + "Uninstalled " + ChatColor.WHITE + n + ChatColor.GRAY + " from "
									+ ChatColor.WHITE + s.getName());
							sender.sendMessage(tag + "But it looks like we can't delete it. You may need to delete "
									+ ChatColor.RED + s.getName() + ChatColor.GRAY + " before installing it again.");
						}
					} catch (Exception e) {
						e.printStackTrace();
						sender.sendMessage(tag + "Couldn't uninstall or find \"" + args[i] + "\".");
					}
				}

				return true;
			}

			if (args[0].equalsIgnoreCase("install")) {
				if (args.length < 1) {
					sender.sendMessage(tag + "/bile install <PLUGIN> [VERSION]");
					return true;
				}

				try {
					for (File i : new File(getDataFolder(), "library").listFiles()) {
						if (!i.getName().toLowerCase().equals(args[1].toLowerCase())) {
							continue;
						}

						if (args.length == 2) {
							long highest = -100000;
							File latest = null;

							for (File j : i.listFiles()) {
								String v = j.getName().replace(".jar", "");

								List<Integer> d = new ArrayList<>();
								for (char k : v.toCharArray()) {
									if (Character.isDigit(k)) {
										d.add(Integer.valueOf(k + ""));
									}
								}

								Collections.reverse(d);

								long g = 0;
								for (int k = 0; k < d.size(); k++) {
									g += (Math.pow(d.get(k), (k + 2)));
								}

								if (g > highest) {
									highest = g;
									latest = j;
								}
							}

							if (latest != null) {
								File ff = new File(BileUtils.getPluginsFolder(), i.getName() + "-" + latest.getName());

								BileUtils.copy(latest, ff);
								BileUtils.load(ff);

								sender.sendMessage(tag + "Installed " + ChatColor.WHITE + ff.getName() + ChatColor.GRAY
										+ " from library.");
							}
						} else {
							for (File j : i.listFiles()) {
								String v = j.getName().replace(".jar", "");

								if (v.equals(args[2])) {
									File ff = new File(BileUtils.getPluginsFolder(), i.getName() + "-" + v);

									BileUtils.copy(j, ff);
									BileUtils.load(ff);

									sender.sendMessage(tag + "Installed " + ChatColor.WHITE + ff.getName()
											+ ChatColor.GRAY + " from library.");
								}
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					sender.sendMessage(tag + "Couldn't install or find \"" + args[1] + "\".");
				}

				return true;
			}

			if (args[0].equalsIgnoreCase("library")) {
				if (args.length == 1) {
					try {
						for (File i : new File(getDataFolder(), "library").listFiles()) {
							long highest = -100000;
							File latest = null;

							for (File j : i.listFiles()) {
								String v = j.getName().replace(".jar", "");

								List<Integer> d = new ArrayList<>();
								for (char k : v.toCharArray()) {
									if (Character.isDigit(k)) {
										d.add(Integer.valueOf(k + ""));
									}
								}

								Collections.reverse(d);

								long g = 0;
								for (int k = 0; k < d.size(); k++) {
									g += (Math.pow(d.get(k), (k + 2)));
								}

								if (g > highest) {
									highest = g;
									latest = j;
								}
							}

							if (latest != null) {
								boolean inst = false;
								String v = null;

								for (File k : BileUtils.getPluginsFolder().listFiles()) {
									if (BileUtils.isPluginJar(k)
											&& i.getName().equalsIgnoreCase(BileUtils.getPluginName(k))) {
										v = BileUtils.getPluginVersion(k);
										inst = true;
										break;
									}
								}

								if (inst) {
									sender.sendMessage(tag + i.getName() + " " + ChatColor.GREEN + "(" + v
											+ " installed) " + ChatColor.WHITE + latest.getName().replace(".jar", "")
											+ ChatColor.GRAY + " (latest)");
								} else {
									sender.sendMessage(tag + i.getName() + " " + ChatColor.WHITE
											+ latest.getName().replace(".jar", "") + ChatColor.GRAY + " (latest)");
								}
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
						sender.sendMessage(tag + "Couldn't list library.");
					}
				} else {
					if (args.length < 1) {
						sender.sendMessage(tag + "/bile library [PLUGIN]");
						return true;
					}

					try {
						boolean dx = false;

						for (File i : new File(getDataFolder(), "library").listFiles()) {
							if (!i.getName().equalsIgnoreCase(args[1])) {
								continue;
							}

							dx = true;
							long highest = -100000;
							File latest = null;

							for (File j : i.listFiles()) {
								String v = j.getName().replace(".jar", "");

								List<Integer> d = new ArrayList<>();
								for (char k : v.toCharArray()) {
									if (Character.isDigit(k)) {
										d.add(Integer.valueOf(k + ""));
									}
								}

								Collections.reverse(d);

								long g = 0;
								for (int k = 0; k < d.size(); k++) {
									g += (Math.pow(d.get(k), (k + 2)));
								}

								if (g > highest) {
									highest = g;
									latest = j;
								}
							}

							if (latest != null) {
								for (File j : i.listFiles()) {
									sender.sendMessage(tag + j.getName().replace(".jar", ""));
								}

								sender.sendMessage(tag + i.getName() + " " + ChatColor.WHITE
										+ latest.getName().replace(".jar", "") + ChatColor.GRAY + " (latest)");
							}
						}

						if (!dx) {
							sender.sendMessage(tag + "Couldn't find " + args[1] + " in library.");
						}
					} catch (Exception e) {
						e.printStackTrace();
						sender.sendMessage(tag + "Couldn't list library.");
					}
				}
			} else if (args[0].equalsIgnoreCase("unload")) {
				if (args.length < 1) {
					sender.sendMessage(tag + "/bile unload <PLUGIN>");
					return true;
				}

				for (int i = 1; i < args.length; i++) {
					try {
						Plugin s = BileUtils.getPluginByName(args[i]);
						if (s == null) {
							sender.sendMessage(tag + "Couldn't find \"" + args[i] + "\".");
							continue;
						}

						String sn = s.getName();
						BileUtils.unload(s);
						File n = BileUtils.getPluginFile(args[i]);
						sender.sendMessage(tag + "Unloaded " + ChatColor.WHITE + sn + ChatColor.GRAY + " ("
								+ ChatColor.WHITE + n.getName() + ChatColor.GRAY + ")");
					} catch (Exception e) {
						e.printStackTrace();
						sender.sendMessage(tag + "Couldn't unload \"" + args[i] + "\".");
					}
				}
			} else if (args[0].equalsIgnoreCase("reload")) {
				if (args.length < 1) {
					sender.sendMessage(tag + "/bile reload <PLUGIN>");
					return true;
				}

				for (int i = 1; i < args.length; i++) {
					try {
						Plugin s = BileUtils.getPluginByName(args[i]);
						if (s == null) {
							sender.sendMessage(tag + "Couldn't find \"" + args[i] + "\".");
							continue;
						}

						String sn = s.getName();
						BileUtils.reload(s);
						File n = BileUtils.getPluginFile(args[i]);
						sender.sendMessage(tag + "Reloaded " + ChatColor.WHITE + sn + ChatColor.GRAY + " ("
								+ ChatColor.WHITE + n.getName() + ChatColor.GRAY + ")");
					} catch (Throwable e) {
						e.printStackTrace();
						sender.sendMessage(tag + "Couldn't reload or find \"" + args[i] + "\".");
					}
				}
			}

			return true;
		}

		return false;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> finalList = new ArrayList<>();

		if (args.length > 1) {
			List<String> list = new ArrayList<>();

			for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
				list.add(plugin.getName());
			}

			StringUtil.copyPartialMatches(args[1], list, finalList);
			Collections.sort(finalList);
		} else if (args.length > 0) {
			List<String> list = new ArrayList<>();

			Arrays.asList("load", "unload", "reload", "uninstall", "library", "install").forEach(list::add);

			StringUtil.copyPartialMatches(args[0], list, finalList);
			Collections.sort(finalList);
		}

		return finalList;
	}
}
