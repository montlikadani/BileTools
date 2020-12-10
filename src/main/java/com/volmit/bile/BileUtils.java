package com.volmit.bile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.UnknownDependencyException;

import com.google.common.io.Files;

public class BileUtils {

	public static void delete(Plugin p) {
		File f = getPluginFile(p);

		backup(p);
		unload(p);
		f.delete();
	}

	public static void delete(File f) {
		if (getPlugin(f) != null) {
			delete(getPlugin(f));
			return;
		}

		PluginDescriptionFile fx = getPluginDescription(f);
		copy(f, new File(getBackupLocation(fx.getName()), fx.getVersion() + ".jar"));
		f.delete();
	}

	public static void reload(Plugin p) {
		File f = getPluginFile(p);

		backup(p);
		unload(p).forEach(BileUtils::load);
		load(f);
	}

	public static void stp(String s) {
		Bukkit.getConsoleSender().sendMessage(
				ChatColor.GREEN + "[" + ChatColor.DARK_GRAY + "Bile" + ChatColor.GREEN + "]: " + ChatColor.GRAY + s);
	}

	public static void load(File file) {
		if (getPlugin(file) != null) {
			return;
		}

		stp("Loading " + getPluginName(file) + " " + getPluginVersion(file));
		PluginDescriptionFile f = getPluginDescription(file);

		for (String i : f.getDepend()) {
			if (Bukkit.getPluginManager().getPlugin(i) == null) {
				stp(getPluginName(file) + " depends on " + i);
				File fx = getPluginFile(i);
				if (fx == null) {
					return;
				}

				load(fx);
			}
		}

		for (String i : f.getSoftDepend()) {
			if (Bukkit.getPluginManager().getPlugin(i) == null) {
				File fx = getPluginFile(i);
				if (fx != null) {
					stp(getPluginName(file) + " soft depends on " + i);
					load(fx);
				}
			}
		}

		Plugin target = null;
		try {
			target = Bukkit.getPluginManager().loadPlugin(file);
		} catch (UnknownDependencyException | InvalidPluginException | InvalidDescriptionException e) {
			e.printStackTrace();
		}

		if (target == null) {
			return;
		}

		target.onLoad();
		Bukkit.getPluginManager().enablePlugin(target);
	}

	@SuppressWarnings("unchecked")
	public static Set<File> unload(Plugin plugin) {
		if (plugin == null) {
			return new HashSet<>();
		}

		File file = getPluginFile(plugin);
		stp("Unloading " + plugin.getName());
		Set<File> deps = new HashSet<>();

		for (Plugin i : Bukkit.getPluginManager().getPlugins()) {
			if (i.equals(plugin)) {
				continue;
			}

			if (i.getDescription().getSoftDepend().contains(plugin.getName())) {
				stp(i.getName() + " soft depends on " + plugin.getName() + ". Playing it safe.");
				deps.add(getPluginFile(i));
			}

			if (i.getDescription().getDepend().contains(plugin.getName())) {
				stp(i.getName() + " depends on " + plugin.getName() + ". Playing it safe.");
				deps.add(getPluginFile(i));
			}
		}

		if (plugin.getName().equals("WorldEdit")) {
			Plugin fa = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
			if (fa != null) {
				stp(fa.getName() + " (kind of) depends on " + plugin.getName() + ". Playing it safe.");
				deps.add(getPluginFile(fa));
			}
		}

		new HashSet<File>(deps).stream().map(i -> unload(getPlugin(i))).forEach(deps::addAll);

		PluginManager pluginManager = Bukkit.getPluginManager();
		SimpleCommandMap commandMap = null;
		List<Plugin> plugins = null;
		Map<String, Plugin> names = null;
		Map<String, Command> commands = null;
		Map<Event, SortedSet<RegisteredListener>> listeners = null;
		boolean reloadlisteners = true;

		try {
			Field pluginsField = pluginManager.getClass().getDeclaredField("plugins");
			Field lookupNamesField = pluginManager.getClass().getDeclaredField("lookupNames");
			pluginsField.setAccessible(true);
			plugins = (List<Plugin>) pluginsField.get(pluginManager);
			lookupNamesField.setAccessible(true);
			names = (Map<String, Plugin>) lookupNamesField.get(pluginManager);

			try {
				Field listenersField = pluginManager.getClass().getDeclaredField("listeners");
				listenersField.setAccessible(true);
				listeners = (Map<Event, SortedSet<RegisteredListener>>) listenersField.get(pluginManager);
			} catch (Exception e) {
				reloadlisteners = false;
			}

			Field commandMapField = pluginManager.getClass().getDeclaredField("commandMap");
			Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
			commandMapField.setAccessible(true);
			commandMap = (SimpleCommandMap) commandMapField.get(pluginManager);
			knownCommandsField.setAccessible(true);
			commands = (Map<String, Command>) knownCommandsField.get(commandMap);
		} catch (Throwable e) {
			e.printStackTrace();
			return new HashSet<>();
		}

		Bukkit.getScheduler().cancelTasks(plugin);
		HandlerList.unregisterAll(plugin);

		pluginManager.disablePlugin(plugin);

		if (plugins != null && plugins.contains(plugin)) {
			plugins.remove(plugin);
		}

		if (names != null && names.containsKey(plugin.getName())) {
			names.remove(plugin.getName());
		}

		if (listeners != null && reloadlisteners) {
			for (SortedSet<RegisteredListener> set : listeners.values()) {
				for (Iterator<RegisteredListener> it = set.iterator(); it.hasNext();) {
					RegisteredListener value = it.next();
					if (value.getPlugin() == plugin) {
						it.remove();
					}
				}
			}
		}

		if (commandMap != null && commands != null) {
			for (Iterator<Map.Entry<String, Command>> it = commands.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, Command> entry = it.next();

				if (entry.getValue() instanceof PluginCommand) {
					PluginCommand c = (PluginCommand) entry.getValue();
					if (c.getPlugin() == plugin) {
						c.unregister(commandMap);
						it.remove();
					}
				}
			}
		}

		ClassLoader cl = plugin.getClass().getClassLoader();
		if (cl instanceof URLClassLoader) {
			try {
				((URLClassLoader) cl).close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

		String idx = UUID.randomUUID().toString();
		File ff = new File(new File(BileTools.bile.getDataFolder(), "temp"), idx);
		System.gc();

		copy(file, ff);
		file.delete();
		copy(ff, file);
		BileTools.bile.reset(file);
		ff.deleteOnExit();

		return deps;
	}

	public static File getBackupLocation(Plugin p) {
		return new File(new File(BileTools.bile.getDataFolder(), "library"), p.getName());
	}

	public static File getBackupLocation(String n) {
		return new File(new File(BileTools.bile.getDataFolder(), "library"), n);
	}

	public List<String> getBackedUpVersions(Plugin p) {
		List<String> s = new ArrayList<>();

		if (getBackupLocation(p).exists()) {
			for (File i : getBackupLocation(p).listFiles()) {
				s.add(i.getName().replace(".jar", ""));
			}
		}

		return s;
	}

	public static void backup(Plugin p) {
		if (p == null) {
			return;
		}

		System.out.println("Backed up " + p.getName() + " " + p.getDescription().getVersion());
		copy(getPluginFile(p), new File(getBackupLocation(p), p.getDescription().getVersion() + ".jar"));
	}

	public static void copy(File a, File b) {
		b.getParentFile().mkdirs();
		try {
			Files.copy(a, b);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static long hash(File file) {
		ByteBuffer buf = null;

		try {
			buf = ByteBuffer.wrap(
					MessageDigest.getInstance("MD5").digest((file.lastModified() + "" + file.length()).getBytes()));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		return buf == null ? 0L : buf.getLong() + buf.getLong();
	}

	public static Plugin getPlugin(File file) {
		if (file == null) {
			return null;
		}

		for (Plugin i : Bukkit.getPluginManager().getPlugins()) {
			if (file.equals(getPluginFile(i))) {
				return i;
			}
		}

		return null;
	}

	public static File getPluginFile(Plugin plugin) {
		if (plugin == null) {
			return null;
		}

		for (File i : getPluginsFolder().listFiles()) {
			if (isPluginJar(i) && plugin.getName().equals(getPluginName(i))) {
				return i;
			}
		}

		return null;
	}

	public static File getPluginFile(String name) {
		for (File i : getPluginsFolder().listFiles()) {
			if (isPluginJar(i) && i.isFile() && i.getName().toLowerCase().equals(name.toLowerCase())) {
				return i;
			}
		}

		for (File i : getPluginsFolder().listFiles()) {
			if (isPluginJar(i) && i.isFile() && getPluginName(i).toLowerCase().equals(name.toLowerCase())) {
				return i;
			}
		}

		return null;
	}

	public static boolean isPluginJar(File f) {
		return f != null && f.exists() && f.isFile() && f.getName().toLowerCase().endsWith(".jar");
	}

	public static File getPluginsFolder() {
		return BileTools.bile.getDataFolder().getParentFile();
	}

	public static List<String> getDependencies(File file) {
		PluginDescriptionFile f = getPluginDescription(file);
		return f != null ? f.getDepend() : new ArrayList<>();
	}

	public static List<String> getSoftDependencies(File file) {
		PluginDescriptionFile f = getPluginDescription(file);
		return f != null ? f.getSoftDepend() : new ArrayList<>();
	}

	public static String getPluginVersion(File file) {
		PluginDescriptionFile f = getPluginDescription(file);
		return f != null ? f.getVersion() : "";
	}

	public static String getPluginName(File file) {
		PluginDescriptionFile f = getPluginDescription(file);
		return f != null ? f.getName() : "";
	}

	public static PluginDescriptionFile getPluginDescription(File file) {
		if (file == null) {
			return null;
		}

		PluginDescriptionFile f = null;

		try {
			PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.zip");
			boolean extensionCorrect = matcher.matches(file.toPath());
			InputStream is = null;
			ZipFile z = null;
			try {
				if (extensionCorrect) {
					z = new ZipFile(file);
					is = z.getInputStream(z.getEntry("plugin.yml"));
				} else {
					z = new JarFile(file);
					is = z.getInputStream(z.getEntry("plugin.yml"));
				}
			} catch (ZipException e) { // ignore
			}

			if (is != null) {
				f = new PluginDescriptionFile(is);
			}

			if (z != null) {
				z.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return f;
	}

	public static Plugin getPluginByName(String string) {
		for (Plugin i : Bukkit.getPluginManager().getPlugins()) {
			if (i.getName().equalsIgnoreCase(string)) {
				return i;
			}
		}

		return null;
	}
}
