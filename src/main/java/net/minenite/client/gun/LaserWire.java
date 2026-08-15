package net.minenite.client.gun;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Paper plugin-channel laser payload (PROTOCOL.md). */
public final class LaserWire {
	public static final int PROTOCOL = 1;
	public static final byte FLAG_UNDERWATER = 1;
	public static final byte FLAG_REFLECTION = 2;
	public static final byte FLAG_TIP = 4;
	public static final int TIP_UNDERWATER = 1;
	public static final int TIP_DRONE_IR = 2;
	public static final int TIP_GUN_IR = 4;
	public static final int TIP_SUPPRESSED = 8;

	public record Segment(float x0, float y0, float z0, float x1, float y1, float z1,
			float intensity, float widthScale, int flags) {
	}

	public record Beam(UUID shooter, int rgb, float baseWidth,
			float tipX, float tipY, float tipZ, int tipFlags,
			List<Segment> segments, boolean local) {
		public boolean gunIr() {
			return (tipFlags & TIP_GUN_IR) != 0;
		}

		public boolean droneIr() {
			return (tipFlags & TIP_DRONE_IR) != 0;
		}

		public boolean suppressed() {
			return (tipFlags & TIP_SUPPRESSED) != 0;
		}
	}

	private LaserWire() {
	}

	public static Beam readBeam(byte[] raw) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
		int proto = in.readUnsignedByte();
		if (proto != PROTOCOL) {
			throw new IOException("laser protocol " + proto);
		}
		UUID shooter = new UUID(in.readLong(), in.readLong());
		int rgb = in.readInt();
		float width = in.readFloat();
		float tipX = in.readFloat();
		float tipY = in.readFloat();
		float tipZ = in.readFloat();
		int tipFlags = in.readUnsignedByte();
		int count = in.readUnsignedShort();
		List<Segment> segs = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			segs.add(new Segment(
					in.readFloat(), in.readFloat(), in.readFloat(),
					in.readFloat(), in.readFloat(), in.readFloat(),
					in.readFloat(), in.readFloat(), in.readUnsignedByte()));
		}
		return new Beam(shooter, rgb, width, tipX, tipY, tipZ, tipFlags, segs, false);
	}

	public static UUID readClear(byte[] raw) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
		in.readUnsignedByte();
		return new UUID(in.readLong(), in.readLong());
	}

	public static FxStore.Payload readFx(byte[] raw) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
		int proto = in.readUnsignedByte();
		if (proto != PROTOCOL) {
			throw new IOException("fx protocol " + proto);
		}
		byte fxType = in.readByte();
		UUID shooter = new UUID(in.readLong(), in.readLong());
		int rgb = in.readInt();
		float x0 = in.readFloat();
		float y0 = in.readFloat();
		float z0 = in.readFloat();
		float x1 = in.readFloat();
		float y1 = in.readFloat();
		float z1 = in.readFloat();
		float scale = in.readFloat();
		int ttl = 4;
		boolean suppressed = false;
		if (fxType == FxStore.FX_TRACER) {
			ttl = in.readUnsignedByte();
		} else if (fxType == FxStore.FX_MUZZLE) {
			ttl = 3;
			if (in.available() > 0) {
				suppressed = (in.readUnsignedByte() & FxStore.FX_FLAG_SUPPRESSED) != 0;
			}
		} else if (fxType == FxStore.FX_THERMAL_BLAST || fxType == FxStore.FX_THERMAL_SMOKE) {
			ttl = in.available() > 0 ? in.readUnsignedByte() : 8;
		}
		return new FxStore.Payload(fxType, shooter, rgb, x0, y0, z0, x1, y1, z1, scale, ttl, suppressed);
	}
}
