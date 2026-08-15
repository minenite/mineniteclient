package net.minenite.client.gun;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Latest beam per shooter; expires if the server stops sending. */
public final class LaserBeamStore {
	private static final LaserBeamStore INSTANCE = new LaserBeamStore();
	private static final int TTL_TICKS = 4;
	private static final UUID LOCAL = new UUID(0L, 1L);

	private final Map<UUID, Active> beams = new ConcurrentHashMap<>();

	public static LaserBeamStore get() {
		return INSTANCE;
	}

	public void accept(LaserWire.Beam beam) {
		beams.put(beam.shooter(), new Active(beam, TTL_TICKS));
	}

	public void acceptLocal(LaserWire.Beam beam) {
		if (hasServer(beam.shooter())) {
			return;
		}
		beams.put(LOCAL, new Active(beam, TTL_TICKS));
	}

	public boolean hasServer(UUID shooter) {
		Active a = beams.get(shooter);
		return a != null && !a.beam.local();
	}

	public void clear(UUID shooter) {
		beams.remove(shooter);
	}

	public void clearLocal() {
		beams.remove(LOCAL);
	}

	public void clearAll() {
		beams.clear();
	}

	public void tick() {
		Iterator<Map.Entry<UUID, Active>> it = beams.entrySet().iterator();
		while (it.hasNext()) {
			Active a = it.next().getValue();
			a.ttl--;
			if (a.ttl <= 0) {
				it.remove();
			}
		}
	}

	public List<LaserWire.Beam> snapshot() {
		List<LaserWire.Beam> out = new ArrayList<>(beams.size());
		for (Active a : beams.values()) {
			out.add(a.beam);
		}
		return out;
	}

	private static final class Active {
		final LaserWire.Beam beam;
		int ttl;

		Active(LaserWire.Beam beam, int ttl) {
			this.beam = beam;
			this.ttl = ttl;
		}
	}
}
