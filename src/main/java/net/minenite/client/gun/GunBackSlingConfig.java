package net.minenite.client.gun;

import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Offhand gun-on-back transform. Defaults match the WarZ sling pose. */
public final class GunBackSlingConfig {
	public static final GunBackSlingConfig INSTANCE = new GunBackSlingConfig();
	private static final Logger LOG = LoggerFactory.getLogger("MineniteWARZ-gunback");

	public float tx = 0.0f;
	public float ty = 0.15f;
	public float tz = 0.40f;
	public float yaw = 180f;
	public float pitch = -90f;
	public float spin = 180f;
	public float roll = -12f;
	public float scale = 0.95f;

	private GunBackSlingConfig() {
	}

	public Path path() {
		return FMLPaths.CONFIGDIR.get().resolve("minenite-gunback.properties");
	}

	public void load() {
		Path p = path();
		if (!Files.isRegularFile(p)) {
			return;
		}
		Properties props = new Properties();
		try (Reader r = Files.newBufferedReader(p)) {
			props.load(r);
		} catch (IOException e) {
			LOG.warn("Failed to load gunback config: {}", e.toString());
			return;
		}
		tx = f(props, "tx", tx);
		ty = f(props, "ty", ty);
		tz = f(props, "tz", tz);
		yaw = f(props, "yaw", yaw);
		pitch = f(props, "pitch", pitch);
		spin = f(props, "spin", spin);
		roll = f(props, "roll", roll);
		scale = f(props, "scale", scale);
	}

	private static float f(Properties props, String key, float def) {
		String v = props.getProperty(key);
		if (v == null || v.isBlank()) {
			return def;
		}
		try {
			return Float.parseFloat(v.trim());
		} catch (NumberFormatException e) {
			return def;
		}
	}
}
