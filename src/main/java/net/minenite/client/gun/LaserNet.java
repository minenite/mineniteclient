package net.minenite.client.gun;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Companion channel for WarzPlugin lasers / NVG / FX (pvpgunminus:*). Optional so
 * vanilla / missing-mod servers still connect.
 */
public final class LaserNet {
	private static final Logger LOG = LoggerFactory.getLogger("MineniteWARZ-laser");
	private static final Identifier HELLO_ID = Identifier.fromNamespaceAndPath("pvpgunminus", "hello");
	private static final Identifier LASER_ID = Identifier.fromNamespaceAndPath("pvpgunminus", "laser");
	private static final Identifier CLEAR_ID = Identifier.fromNamespaceAndPath("pvpgunminus", "laser_clear");
	private static final Identifier FLASH_ID = Identifier.fromNamespaceAndPath("pvpgunminus", "laser_flash");
	private static final Identifier FX_ID = Identifier.fromNamespaceAndPath("pvpgunminus", "fx");
	private static final Identifier SCOPE_ID = Identifier.fromNamespaceAndPath("pvpgunminus", "scope");
	private static final Identifier BLOOD_ID = Identifier.fromNamespaceAndPath("pvpgunminus", "blood");
	private static final Identifier PRONE_ID = Identifier.fromNamespaceAndPath("pvpgunminus", "prone");
	private static final Identifier PRONE_REQ_ID = Identifier.fromNamespaceAndPath("pvpgunminus", "prone_req");
	private static final Identifier PEQ_REQ_ID = Identifier.fromNamespaceAndPath("pvpgunminus", "peq_req");
	private static final Identifier GUN_POSE_ID = Identifier.fromNamespaceAndPath("pvpgunminus", "gun_pose");
	private static int helloTicks;
	private static boolean hWasDown;
	private static boolean zWasDown;

	private LaserNet() {}

	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar reg = event.registrar("1").optional();
		reg.playToClient(BytesPayload.typeOf(LASER_ID), BytesPayload.codec(LASER_ID), LaserNet::onLaser);
		reg.playToClient(BytesPayload.typeOf(CLEAR_ID), BytesPayload.codec(CLEAR_ID), LaserNet::onClear);
		reg.playToClient(BytesPayload.typeOf(FLASH_ID), BytesPayload.codec(FLASH_ID), LaserNet::onFlash);
		reg.playToClient(BytesPayload.typeOf(FX_ID), BytesPayload.codec(FX_ID), LaserNet::onFx);
		reg.playToClient(BytesPayload.typeOf(SCOPE_ID), BytesPayload.codec(SCOPE_ID), LaserNet::onScope);
		reg.playToClient(BytesPayload.typeOf(BLOOD_ID), BytesPayload.codec(BLOOD_ID), LaserNet::onBlood);
		reg.playToClient(BytesPayload.typeOf(PRONE_ID), BytesPayload.codec(PRONE_ID), LaserNet::onProne);
		reg.playToClient(BytesPayload.typeOf(GUN_POSE_ID), BytesPayload.codec(GUN_POSE_ID), LaserNet::onGunPose);
		reg.playToServer(BytesPayload.typeOf(HELLO_ID), BytesPayload.codec(HELLO_ID), (p, ctx) -> {});
		reg.playToServer(BytesPayload.typeOf(PRONE_REQ_ID), BytesPayload.codec(PRONE_REQ_ID), (p, ctx) -> {});
		reg.playToServer(BytesPayload.typeOf(PEQ_REQ_ID), BytesPayload.codec(PEQ_REQ_ID), (p, ctx) -> {});
		for (String path : new String[]{
				"features", "blast", "chainlink", "workbench",
				"glass", "weather", "flare", "smoke", "anomaly_vis",
				"drone_strike_fx", "drone_optic", "drone_zoom", "drone_adjust",
				"drone_hud", "drone_vis", "drone_hit", "drone_mesh_pose",
				"javelin_lock", "creative_give"}) {
			Identifier extra = Identifier.fromNamespaceAndPath("pvpgunminus", path);
			try {
				reg.playToClient(BytesPayload.typeOf(extra), BytesPayload.codec(extra), (p, ctx) -> {});
			} catch (Throwable ignored) {
			}
			try {
				reg.playToServer(BytesPayload.typeOf(extra), BytesPayload.codec(extra), (p, ctx) -> {});
			} catch (Throwable ignored) {
			}
		}
	}

	/** Incoming S2C that bypassed NeoForge negotiation (CardForge plugin messages). */
	public static void handleIncoming(Identifier id, byte[] raw) {
		if (id == null || raw == null || !"pvpgunminus".equals(id.getNamespace())) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> dispatch(id.getPath(), raw));
	}

	private static void dispatch(String path, byte[] raw) {
		try {
			switch (path) {
				case "laser" -> LaserBeamStore.get().accept(LaserWire.readBeam(raw));
				case "laser_clear" -> LaserBeamStore.get().clear(LaserWire.readClear(raw));
				case "laser_flash" -> {
					java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(raw));
					in.readUnsignedByte();
					LaserEyeFlash.accept(in.readInt(), in.readFloat());
				}
				case "fx" -> FxStore.get().accept(LaserWire.readFx(raw));
				case "scope" -> onScope(new BytesPayload(SCOPE_ID, raw), null);
				case "blood" -> onBlood(new BytesPayload(BLOOD_ID, raw), null);
				case "prone" -> onProne(new BytesPayload(PRONE_ID, raw), null);
				case "gun_pose" -> onGunPose(new BytesPayload(GUN_POSE_ID, raw), null);
				case "features" -> WarzFeatures.accept(raw);
				case "chainlink" -> ChainlinkClient.accept(raw);
				default -> {
				}
			}
		} catch (Exception e) {
			LOG.debug("bad {} packet: {}", path, e.toString());
		}
	}

	@SubscribeEvent
	public static void onTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		LaserBeamStore.get().tick();
		FxStore.get().tick();
		ScopeOverlay.tick();
		BloodOverlay.tick();
		LaserEyeFlash.tick();
		NvgVision.tickToast();
		ThermalOverlay.tickToast();
		if (mc.player == null || mc.getConnection() == null) {
			helloTicks = 0;
			hWasDown = false;
			zWasDown = false;
			ScopeOverlay.clear();
			BloodOverlay.clear();
			LaserEyeFlash.clear();
			LaserBeamStore.get().clearAll();
			FxStore.get().clearAll();
			NvgAgc.get().reset();
			net.minenite.client.gun.vision.TemperatureField.get().reset();
			ProneClient.reset();
			GunPoseClient.reset();
			GunReloadAnimator.reset();
			ChainlinkClient.clear();
			WarzFeatures.clear();
			return;
		}
		ProneClient.tick(mc);
		GunReloadAnimator.clientTick();
		tickKeys(mc);
		net.minenite.client.gun.vision.TemperatureField.get().tick(mc, mc.player, 0.05f);
		if (ThermalVision.isWearing(mc.player)) {
			NvgAgc.get().reset();
		} else if (NvgVision.isWearing(mc.player)) {
			NvgAgc.get().tickWorld(mc, mc.player, 0.05f);
		} else {
			NvgAgc.get().reset();
		}
		tickLocalLaser(mc);
		helloTicks++;
		if (helloTicks == 20 || helloTicks % 400 == 0) {
			sendHello();
		}
	}

	private static void tickKeys(Minecraft mc) {
		if (mc.gui.screen() != null) {
			hWasDown = false;
			zWasDown = false;
			return;
		}
		long window = mc.getWindow().handle();
		boolean hDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_H) == GLFW.GLFW_PRESS;
		if (hDown && !hWasDown) {
			if (ThermalVision.hasHelmet(mc.player)) {
				ThermalOverlay.toast(ThermalVision.handleH(mc.player));
			} else {
				NvgVision.handleH(mc.player);
			}
		}
		hWasDown = hDown;
		boolean zDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
		if (zDown && !zWasDown) {
			sendPeqReq();
		}
		zWasDown = zDown;
	}

	private static void tickLocalLaser(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			LaserBeamStore.get().clearLocal();
			return;
		}
		ItemStack hand = player.getMainHandItem();
		GunAttachmentVisuals.LaserSight sight = GunAttachmentVisuals.laserSight(hand);
		if (!sight.active() || !GunItemPose.isGun(hand)) {
			LaserBeamStore.get().clearLocal();
			return;
		}
		if (sight.infrared() && !NvgVision.isWearing(player) && !ThermalVision.isWearing(player)) {
			LaserBeamStore.get().clearLocal();
			return;
		}
		if (LaserBeamStore.get().hasServer(player.getUUID())) {
			LaserBeamStore.get().clearLocal();
			return;
		}
		Vec3 eye = player.getEyePosition(1f);
		Vec3 look = player.getViewVector(1f);
		Vec3 end = eye.add(look.scale(64));
		HitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 tip = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
		int flags = sight.infrared() ? LaserWire.TIP_GUN_IR : 0;
		LaserBeamStore.get().acceptLocal(new LaserWire.Beam(
				player.getUUID(), sight.rgb(), 0.22f,
				(float) tip.x, (float) tip.y, (float) tip.z, flags,
				java.util.List.of(new LaserWire.Segment(
						(float) eye.x, (float) eye.y, (float) eye.z,
						(float) tip.x, (float) tip.y, (float) tip.z,
						1f, 1f, 0)),
				true));
	}

	@SubscribeEvent
	public static void onGui(RenderGuiEvent.Post event) {
		NightWorldWash.render(event.getGuiGraphics());
		FlashlightOverlay.render(event.getGuiGraphics());
		ThermalOverlay.render(event.getGuiGraphics());
		NvgOverlay.render(event.getGuiGraphics());
		BloodOverlay.render(event.getGuiGraphics());
		ScopeOverlay.render(event.getGuiGraphics());
		ProneClient.render(event.getGuiGraphics());
		LaserEyeFlash.render(event.getGuiGraphics());
	}

	private static void sendHello() {
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bos);
			out.writeByte(LaserWire.PROTOCOL);
			byte[] ver = "1.0.31".getBytes(java.nio.charset.StandardCharsets.UTF_8);
			out.writeShort(ver.length);
			out.write(ver);
			Minecraft.getInstance().getConnection().send(
					new BytesPayload(HELLO_ID, bos.toByteArray()));
		} catch (Exception e) {
			LOG.debug("laser hello failed: {}", e.toString());
		}
	}

	private static void sendPeqReq() {
		try {
			if (Minecraft.getInstance().getConnection() == null) {
				return;
			}
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bos);
			out.writeByte(LaserWire.PROTOCOL);
			out.writeByte(0);
			Minecraft.getInstance().getConnection().send(new BytesPayload(PEQ_REQ_ID, bos.toByteArray()));
		} catch (Exception e) {
			LOG.debug("peq req failed: {}", e.toString());
		}
	}

	private static void onLaser(BytesPayload payload, IPayloadContext ctx) {
		try {
			LaserBeamStore.get().accept(LaserWire.readBeam(payload.raw()));
		} catch (Exception e) {
			LOG.debug("bad laser packet: {}", e.toString());
		}
	}

	private static void onClear(BytesPayload payload, IPayloadContext ctx) {
		try {
			LaserBeamStore.get().clear(LaserWire.readClear(payload.raw()));
		} catch (Exception ignored) {
		}
	}

	private static void onFlash(BytesPayload payload, IPayloadContext ctx) {
		try {
			java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(payload.raw()));
			in.readUnsignedByte();
			LaserEyeFlash.accept(in.readInt(), in.readFloat());
		} catch (Exception ignored) {
		}
	}

	private static void onFx(BytesPayload payload, IPayloadContext ctx) {
		try {
			FxStore.get().accept(LaserWire.readFx(payload.raw()));
		} catch (Exception e) {
			LOG.debug("bad fx packet: {}", e.toString());
		}
	}

	private static void onScope(BytesPayload payload, IPayloadContext ctx) {
		try {
			java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(payload.raw()));
			int proto = in.readUnsignedByte();
			boolean active = in.readUnsignedByte() != 0;
			int zero = in.readUnsignedShort();
			String gunId = readUtf(in, 64);
			byte flags = 0;
			int breath = 100;
			float speed = 4.5f;
			float fall = 0.014f;
			byte hud = ScopeOverlay.HUD_IRONS;
			String opticId = "irons";
			int rgb = 0xFF2828;
			String gripId = "";
			float fov = 0.92f;
			if (proto >= 2) {
				flags = in.readByte();
				breath = in.readUnsignedByte();
				speed = in.readUnsignedShort() / 100f;
				fall = in.readUnsignedShort() / 10000f;
			}
			if (proto >= 3) {
				hud = in.readByte();
				opticId = readUtf(in, 32);
				rgb = in.readInt();
				gripId = readUtf(in, 24);
				fov = in.readUnsignedShort() / 1000f;
			}
			ScopeOverlay.markServerPacket();
			ScopeOverlay.accept(active, zero, gunId, flags, breath, speed, fall, hud, opticId, rgb, gripId, fov);
		} catch (Exception e) {
			LOG.debug("bad scope packet: {}", e.toString());
		}
	}

	public static void sendProneReq(byte[] raw) {
		if (raw == null || raw.length == 0) {
			return;
		}
		try {
			if (Minecraft.getInstance().getConnection() == null) {
				return;
			}
			Minecraft.getInstance().getConnection().send(new BytesPayload(PRONE_REQ_ID, raw));
		} catch (Exception e) {
			LOG.debug("prone req failed: {}", e.toString());
		}
	}

	private static void onGunPose(BytesPayload payload, IPayloadContext ctx) {
		try {
			java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(payload.raw()));
			in.readUnsignedByte();
			long hi = in.readLong();
			long lo = in.readLong();
			byte flags = (byte) in.readUnsignedByte();
			GunPoseClient.accept(new java.util.UUID(hi, lo), flags);
		} catch (Exception ignored) {
		}
	}

	private static void onProne(BytesPayload payload, IPayloadContext ctx) {
		try {
			java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(payload.raw()));
			in.readUnsignedByte();
			long hi = in.readLong();
			long lo = in.readLong();
			boolean prone = in.readUnsignedByte() != 0;
			ProneClient.accept(new java.util.UUID(hi, lo), prone);
		} catch (Exception ignored) {
		}
	}

	private static void onBlood(BytesPayload payload, IPayloadContext ctx) {
		try {
			java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(payload.raw()));
			in.readUnsignedByte();
			float severity = in.readFloat();
			boolean pulse = in.readUnsignedByte() != 0;
			BloodOverlay.accept(severity, pulse);
		} catch (Exception ignored) {
		}
	}

	private static String readUtf(java.io.DataInputStream in, int max) throws java.io.IOException {
		int n = in.readUnsignedShort();
		if (n <= 0) {
			return "";
		}
		int take = Math.min(max, n);
		byte[] id = new byte[take];
		in.readFully(id);
		if (n > take) {
			in.skipBytes(n - take);
		}
		return new String(id, java.nio.charset.StandardCharsets.UTF_8);
	}

	public record BytesPayload(Identifier id, byte[] raw) implements CustomPacketPayload {
		public static CustomPacketPayload.Type<BytesPayload> typeOf(Identifier id) {
			return new CustomPacketPayload.Type<>(id);
		}

		public static StreamCodec<FriendlyByteBuf, BytesPayload> codec(Identifier id) {
			return StreamCodec.of(
					(buf, p) -> buf.writeBytes(p.raw()),
					buf -> {
						byte[] raw = new byte[buf.readableBytes()];
						buf.readBytes(raw);
						return new BytesPayload(id, raw);
					});
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return typeOf(id);
		}
	}
}
