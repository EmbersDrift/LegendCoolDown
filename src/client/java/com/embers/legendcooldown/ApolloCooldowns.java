package com.embers.legendcooldown;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.world.item.component.CustomModelData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.function.Consumer;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemCooldowns;
import com.embers.legendcooldown.mixin.ItemCooldownsAccessor;

/** Independent reader for Apollo's public cooldown messages. */
public final class ApolloCooldowns {
    private static final String PREFIX = "lunarclient.apollo.cooldown.v1.";
    private static final int MAX_PACKET = 1_048_576;
    private static final Map<String, Cooldown> ACTIVE = new LinkedHashMap<>();
    private static final Map<String, Cooldown> SERVER_TEXT = new LinkedHashMap<>();
    private static final Map<String, Double> SERVER_REMAINING = new LinkedHashMap<>();
    private static final Set<Identifier> REGISTERED_TEXTURES = new HashSet<>();
    private static final Map<String, Identifier> RESOLVED_TEXTURES = new LinkedHashMap<>();
    private static final Map<ItemStack, ItemStack> RENDER_ICONS = new IdentityHashMap<>();
    private static final Map<Identifier, NativeCooldown> NATIVE = new LinkedHashMap<>();
    private static ItemCooldowns nativeOwner;
    private static ItemStack recentlyUsed = ItemStack.EMPTY;
    private static long recentlyUsedAt;
    private static long modelRevision;
    private static long lastInventorySignature;
    private static boolean inventorySignatureKnown;
    private static int maintenanceTicks;
    private static final Map<ItemStack, Long> MODEL_REVISIONS = new IdentityHashMap<>();
    private record NativeCooldown(ItemStack icon, int endTick, int durationTicks) {}
    private static List<Cooldown> visibleSnapshot = List.of();
    private static List<Cooldown> previewSnapshot = List.of();
    private static long snapshotTime;
    private static boolean teamviewerAttached;
    private static final CustomPacketPayload.Type<RawPayload> JSON = new CustomPacketPayload.Type<>(Identifier.parse("apollo:json"));
    private static final CustomPacketPayload.Type<RawPayload> PROTO = new CustomPacketPayload.Type<>(Identifier.parse("lunar:apollo"));
    private static final CustomPacketPayload.Type<RawPayload> LEGACY = new CustomPacketPayload.Type<>(Identifier.parse("lunarclient:pm"));
    private static StreamCodec<RegistryFriendlyByteBuf, RawPayload> codec(CustomPacketPayload.Type<RawPayload> channel) {
        return StreamCodec.of((buf, payload) -> buf.writeBytes(payload.bytes()), buf -> {
                int size = buf.readableBytes();
                if (size > MAX_PACKET) throw new IllegalArgumentException("Apollo payload too large");
                byte[] bytes = new byte[size];
                buf.readBytes(bytes);
                return new RawPayload(bytes, channel);
            });
    }

    private ApolloCooldowns() {}
    public static void actionBar(net.minecraft.network.chat.Component message) {
        if (message != null) ServerArtifactCooldowns.receive(message.getString(), true, "gui-action-bar");
    }

    public static void init() {
        ServerArtifactCooldowns.init();
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(
                Identifier.fromNamespaceAndPath(LegendCooldown.ID, "hud_texture_cache"),
                (ResourceManagerReloadListener) manager -> Minecraft.getInstance().execute(() -> {
                    RESOLVED_TEXTURES.clear();
                    RENDER_ICONS.entrySet().removeIf(entry -> entry.getKey()==entry.getValue());
                    MODEL_REVISIONS.keySet().retainAll(RENDER_ICONS.keySet());
                    modelRevision++;


                }));
        PayloadTypeRegistry.playS2C().register(JSON, codec(JSON));
        ClientPlayNetworking.registerGlobalReceiver(JSON, (payload, context) ->
                context.client().execute(() -> readJson(payload.bytes())));
        PayloadTypeRegistry.playS2C().register(LEGACY, codec(LEGACY));
        ClientPlayNetworking.registerGlobalReceiver(LEGACY, (payload, context) ->
                context.client().execute(() -> readLegacy(payload.bytes())));
        if (!FabricLoader.getInstance().isModLoaded("teamviewer")) {
            PayloadTypeRegistry.playS2C().register(PROTO, codec(PROTO));
            ClientPlayNetworking.registerGlobalReceiver(PROTO, (payload, context) ->
                    context.client().execute(() -> readProto(payload.bytes())));
        }
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CooldownDiagnostics.stop();
            ACTIVE.clear(); SERVER_TEXT.clear(); SERVER_REMAINING.clear(); ServerArtifactCooldowns.clear();
            NATIVE.clear(); nativeOwner=null; recentlyUsed = ItemStack.EMPTY;
            visibleSnapshot = List.of();
            snapshotTime = 0;
            RENDER_ICONS.clear();
            MODEL_REVISIONS.clear(); inventorySignatureKnown = false;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide()) rememberUsed(player.getItemInHand(hand));
            return InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide()) rememberUsed(player.getItemInHand(hand));
            return InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, face) -> {
            if (world.isClientSide()) rememberUsed(player.getItemInHand(hand));
            return InteractionResult.PASS;
        });
    }

    /** TeamViewer registers its Apollo payload during mod initialization; attach after all entrypoints finish. */
    public static void attachTeamviewer() {
        if (teamviewerAttached || !FabricLoader.getInstance().isModLoaded("teamviewer")) return;
        try {
            Class<?> receiver = Class.forName("net.kztmc.mc.common.ApolloCommonReceiver");
            Class<?> any = Class.forName("com.google.protobuf.Any");
            Method typeUrl = any.getMethod("getTypeUrl"), serialize = any.getMethod("toByteArray");
            Consumer<Object> listener = message -> {
                try {
                    if (!cooldownType((String) typeUrl.invoke(message))) return;
                    byte[] bytes = (byte[]) serialize.invoke(message);
                    Minecraft.getInstance().execute(() -> readProto(bytes));
                } catch (ReflectiveOperationException error) {
                    LegendCooldown.LOGGER.warn("Could not read TeamViewer Apollo message", error);
                }
            };
            receiver.getMethod("addListener", Consumer.class).invoke(null, listener);
            teamviewerAttached = true;
            LegendCooldown.LOGGER.info("Cooldowns attached to TeamViewer Apollo receiver");
        } catch (ReflectiveOperationException error) {
            LegendCooldown.LOGGER.warn("Could not attach cooldowns to TeamViewer Apollo receiver", error);
            teamviewerAttached = true;
        }
    }

    private record RawPayload(byte[] bytes, CustomPacketPayload.Type<RawPayload> channel) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return channel; }
    }

    public record Cooldown(String name, ItemStack icon, Identifier texture,
                           float u0, float u1, float v0, float v1, long endNanos, long durationNanos,
                           String texturePath, boolean vanilla) {
        public Cooldown(String name, ItemStack icon, Identifier texture, float u0, float u1, float v0, float v1, long endNanos, long durationNanos) {
            this(name,icon,texture,u0,u1,v0,v1,endNanos,durationNanos,"",false);
        }
        public double secondsLeft(long now) { return Math.max(0, (endNanos - now) / 1_000_000_000.0); }
        public double fraction(long now) { return Math.clamp((endNanos-now)/(double)Math.max(1,durationNanos),0,1); }
    }

    private record Icon(ItemStack item, String texturePath, float u0, float u1, float v0, float v1) {}

    public static List<Cooldown> visible(boolean preview) {
        long now = System.nanoTime();
        if (snapshotTime == 0 || now - snapshotTime >= 50_000_000L) {
            ACTIVE.values().removeIf(c -> c.endNanos() <= now);
            SERVER_TEXT.values().removeIf(c -> c.endNanos() <= now);
            SERVER_REMAINING.keySet().retainAll(SERVER_TEXT.keySet());
            List<Cooldown> rows = new ArrayList<>(ACTIVE.values());
            Minecraft client = Minecraft.getInstance();
            if (LegendCooldown.config().cooldownServerText) {
                for (Cooldown fallback : SERVER_TEXT.values()) {
                    if (ACTIVE.values().stream().noneMatch(c -> sameArtifact(c, fallback, client))) rows.add(fallback);
                }
            }
            if (client.player != null && LegendCooldown.config().cooldownVanilla) {
                ItemCooldowns source = client.player.getCooldowns();
                if(nativeOwner!=source) { NATIVE.clear();nativeOwner=source; }
                int ticks = ((ItemCooldownsAccessor) source).legendcooldown$tickCount();
                NATIVE.entrySet().removeIf(entry -> entry.getValue().endTick <= ticks);
                NATIVE.forEach((group, nativeCooldown) -> {
                    boolean duplicate = rows.stream().anyMatch(c -> !c.icon.isEmpty()
                            && group.equals(source.getCooldownGroup(iconForRender(c, client))));
                    if (duplicate) return;
                    ItemStack icon = nativeCooldown.icon;
                    if (icon.isEmpty()) {
                        icon = inventoryForGroup(group, client);
                        if(!icon.isEmpty())NATIVE.put(group,new NativeCooldown(icon,nativeCooldown.endTick,nativeCooldown.durationTicks));
                    }
                    if (icon.isEmpty()) return;
                    long duration = nativeCooldown.durationTicks * 50_000_000L;
                    rows.add(new Cooldown("vanilla:"+group,icon,null,0,1,0,1,
                            now+(nativeCooldown.endTick-ticks)*50_000_000L,duration,"",true));
                });
            }
            if (LegendCooldown.config().cooldownSortRemaining) rows.sort(java.util.Comparator.comparingLong(Cooldown::endNanos));
            visibleSnapshot = List.copyOf(rows);
            snapshotTime = now;
        }
        if (!visibleSnapshot.isEmpty()) return visibleSnapshot;
        if (!preview || Minecraft.getInstance().level == null) return List.of();
        if (previewSnapshot.isEmpty() || previewSnapshot.get(0).endNanos() <= now) previewSnapshot = List.of(
                new Cooldown("Pearl", new ItemStack(Items.ENDER_PEARL), null, 0, 1, 0, 1,
                        now + 12_000_000_000L, 20_000_000_000L),
                new Cooldown("Artifact", new ItemStack(Items.NETHER_STAR), null, 0, 1, 0, 1,
                        now + 29_000_000_000L, 40_000_000_000L));
        return previewSnapshot;
    }

    /** Use the server supplied model on a held item when Apollo only names its base item. */
    public static ItemStack iconForRender(Cooldown cooldown, Minecraft client) {
        ItemStack base = cooldown.icon();
        if (base.isEmpty() || client.player == null) return base;
        ItemStack cached = RENDER_ICONS.get(base);
        // A matched stack is a snapshot: moving/consuming an artifact cannot replace its icon.
        if (cached != null && (cached != base || Objects.equals(MODEL_REVISIONS.get(base),modelRevision))) return cached;
        ItemStack resolved = findInventoryModel(base, cooldown.name, client);
        if (RENDER_ICONS.size() < 128) { RENDER_ICONS.put(base, resolved); MODEL_REVISIONS.put(base,modelRevision); }
        return resolved;
    }

    static void tick() {
        Minecraft client = Minecraft.getInstance();
        CooldownDiagnostics.tick();
        ServerArtifactCooldowns.tick(client);
        if(++maintenanceTicks%20==0){
            long now=System.nanoTime();
            ACTIVE.values().removeIf(c->c.endNanos()<=now);SERVER_TEXT.values().removeIf(c->c.endNanos()<=now);
            SERVER_REMAINING.keySet().retainAll(SERVER_TEXT.keySet());
            Set<ItemStack> keep=java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            for(var c:ACTIVE.values())keep.add(c.icon());
            for(var c:SERVER_TEXT.values())keep.add(c.icon());
            for(var c:NATIVE.values())keep.add(c.icon());
            RENDER_ICONS.keySet().retainAll(keep);MODEL_REVISIONS.keySet().retainAll(RENDER_ICONS.keySet());
        }
        if (client.player == null) return;
        boolean unresolved=false;
        for(var cooldown:ACTIVE.values()){
            ItemStack base=cooldown.icon(),bound=RENDER_ICONS.get(base);
            if(!base.isEmpty() && (bound==null || bound==base)){unresolved=true;break;}
        }
        // Server-text and native icons are already snapshots; only unresolved Apollo icons need inventory hashing.
        if(!unresolved)return;
        long signature=1;
        var inventory=client.player.getInventory();
        for(int i=0;i<inventory.getContainerSize();i++) {
            ItemStack stack=inventory.getItem(i);
            signature=31*signature+ItemStack.hashItemAndComponents(stack);
        }
        if (!inventorySignatureKnown || signature!=lastInventorySignature) {
            inventorySignatureKnown=true;lastInventorySignature=signature;modelRevision++;snapshotTime=0;
        }
    }

    static void rememberUsed(ItemStack stack) {
        ServerArtifactCooldowns.used(stack);
        if (!stack.isEmpty()) {
            if(stack.getCount()!=recentlyUsed.getCount() || !ItemStack.isSameItemSameComponents(stack,recentlyUsed)){
                recentlyUsed=stack.copy();modelRevision++;
            }
            recentlyUsedAt=System.nanoTime();
        }
    }

    public static void vanillaStarted(ItemCooldowns source, Identifier group, int durationTicks) {
        Minecraft client=Minecraft.getInstance();
        if (client==null || client.player==null || source!=client.player.getCooldowns()) return;
        CooldownDiagnostics.record("native",group.toString(),null,"durationTicks="+durationTicks);
        ServerArtifactCooldowns.report("native:" + group, "Native cooldown signal for " + group + ": " + durationTicks + " ticks");
        if(nativeOwner!=source) { NATIVE.clear();nativeOwner=source; }
        if (durationTicks<=0) NATIVE.remove(group);
        else {
            NATIVE.put(group,new NativeCooldown(inventoryForGroup(group,client),
                    ((ItemCooldownsAccessor) source).legendcooldown$tickCount()+Math.min(durationTicks,1_728_000),Math.min(durationTicks,1_728_000)));
            while(NATIVE.size()>32)NATIVE.remove(NATIVE.keySet().iterator().next());
        }
        snapshotTime=0;
    }

    public static void vanillaEnded(ItemCooldowns source, Identifier group) {
        Minecraft client=Minecraft.getInstance();
        if(client!=null && client.player!=null && source==client.player.getCooldowns()) {NATIVE.remove(group);snapshotTime=0;}
    }

    private static ItemStack inventoryForGroup(Identifier group, Minecraft client) {
        var source=client.player.getCooldowns();
        if (!recentlyUsed.isEmpty() && System.nanoTime()-recentlyUsedAt<2_000_000_000L
                && group.equals(source.getCooldownGroup(recentlyUsed))) return recentlyUsed.copy();
        for(var stack:List.of(client.player.getMainHandItem(),client.player.getOffhandItem()))
            if(!stack.isEmpty() && group.equals(source.getCooldownGroup(stack)))return stack.copy();
        var inventory=client.player.getInventory();
        for(int i=0;i<inventory.getContainerSize();i++) {var stack=inventory.getItem(i);
            if(!stack.isEmpty() && group.equals(source.getCooldownGroup(stack)))return stack.copy();}
        return ItemStack.EMPTY;
    }

    private static ItemStack findInventoryModel(ItemStack base, String name, Minecraft client) {
        if (!recentlyUsed.isEmpty() && System.nanoTime()-recentlyUsedAt<2_000_000_000L && matchesModel(base,recentlyUsed))
            return recentlyUsed.copy();
        ItemStack match = ItemStack.EMPTY;
        String label=CooldownFormat.labelKey(name);
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = client.player.getInventory().getItem(slot);
            if (!matchesModel(base,candidate)) continue;
            if (!label.isEmpty() && label.equals(CooldownFormat.labelKey(candidate.getHoverName().getString()))) return candidate.copy();
            if (!match.isEmpty() && (!Objects.equals(match.get(DataComponents.CUSTOM_MODEL_DATA),
                    candidate.get(DataComponents.CUSTOM_MODEL_DATA))
                    || !Objects.equals(match.get(DataComponents.ITEM_MODEL), candidate.get(DataComponents.ITEM_MODEL))))
                return base;
            match = candidate;
        }
        return match.isEmpty() ? base : match.copy();
    }

    private static boolean matchesModel(ItemStack base, ItemStack candidate) {
        if(candidate.isEmpty() || candidate.getItem()!=base.getItem())return false;
        if(base.has(DataComponents.CUSTOM_MODEL_DATA) && !Objects.equals(base.get(DataComponents.CUSTOM_MODEL_DATA),candidate.get(DataComponents.CUSTOM_MODEL_DATA)))return false;
        if(base.has(DataComponents.PROFILE) && !Objects.equals(base.get(DataComponents.PROFILE),candidate.get(DataComponents.PROFILE)))return false;
        if(base.getComponentsPatch().entrySet().stream().anyMatch(entry -> entry.getKey()==DataComponents.ITEM_MODEL) && !Objects.equals(base.get(DataComponents.ITEM_MODEL),candidate.get(DataComponents.ITEM_MODEL)))return false;
        return candidate.has(DataComponents.CUSTOM_MODEL_DATA)
                || !Objects.equals(candidate.get(DataComponents.ITEM_MODEL),candidate.getItem().components().get(DataComponents.ITEM_MODEL))
                || candidate.has(DataComponents.CUSTOM_NAME) || candidate.has(DataComponents.PROFILE);
    }

    public static Identifier textureForRender(Cooldown cooldown) {
        return cooldown.texturePath().isBlank() ? cooldown.texture() : resolveTexture(cooldown.texturePath());
    }
    public static String labelForRender(Cooldown cooldown) {
        ItemStack icon=iconForRender(cooldown,Minecraft.getInstance());
        return cooldown.vanilla() && !icon.isEmpty() ? icon.getHoverName().getString() : cooldown.name();
    }
    private static boolean cooldownType(String type) {
        String name=type.substring(type.lastIndexOf('/')+1);
        return name.equals(PREFIX+"DisplayCooldownMessage") || name.equals(PREFIX+"RemoveCooldownMessage") || name.equals(PREFIX+"ResetCooldownsMessage");
    }

    private static boolean sameArtifact(Cooldown left, Cooldown right, Minecraft client) {
        if (CooldownFormat.labelKey(left.name()).equals(CooldownFormat.labelKey(right.name()))) return true;
        ItemStack a = iconForRender(left, client), b = iconForRender(right, client);
        return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameComponents(a, b);
    }

    static void serverCooldown(String name, double seconds, ItemStack icon) {
        serverCooldown(name, seconds, icon, false);
    }
    static boolean serverTimerActive(String name){
        Cooldown cooldown=SERVER_TEXT.get(CooldownFormat.labelKey(name));
        return cooldown!=null && cooldown.endNanos()>System.nanoTime();
    }

    static void serverCooldown(String name, double seconds, ItemStack icon, boolean activation) {
        serverCooldown(name, seconds, icon, activation, 0);
    }

    static void serverCooldown(String name, double seconds, ItemStack icon, boolean activation, double totalSeconds) {
        if (!Double.isFinite(seconds) || seconds < 0 || seconds > 86_400 || name.length() > 128 || icon.isEmpty()) return;
        String key = CooldownFormat.labelKey(name);
        if (key.isBlank()) return;
        if (seconds == 0) { SERVER_TEXT.remove(key); SERVER_REMAINING.remove(key); snapshotTime = 0; return; }
        long now = System.nanoTime(), duration = (long)(seconds * 1_000_000_000L);
        if (Double.isFinite(totalSeconds) && totalSeconds > 0 && totalSeconds <= 86_400)
            duration = Math.max(duration, (long)(totalSeconds * 1_000_000_000L));
        Cooldown previous = SERVER_TEXT.get(key);
        if(previous!=null && previous.endNanos()<=now)previous=null;
        if (!activation && previous != null && Objects.equals(SERVER_REMAINING.get(key), seconds)) return;
        if (activation) previous = null;
        if (previous != null && previous.endNanos() > now) duration = Math.max(duration, previous.durationNanos());
        ItemStack copy = previous == null ? icon.copy() : previous.icon();
        SERVER_TEXT.put(key, new Cooldown(name, copy, null, 0, 1, 0, 1, now + (long)(seconds * 1_000_000_000L), duration));
        SERVER_REMAINING.put(key, seconds);
        // This is already the exact server-supplied stack, including profile and model components.
        if (!RENDER_ICONS.containsKey(copy)) RENDER_ICONS.put(copy, copy.copy());
        MODEL_REVISIONS.put(copy, modelRevision);
        while (SERVER_TEXT.size() > 32) SERVER_TEXT.remove(SERVER_TEXT.keySet().iterator().next());
        SERVER_REMAINING.keySet().retainAll(SERVER_TEXT.keySet());
        snapshotTime = 0;
    }

    static void removeServerCooldown(String name) {
        String key = CooldownFormat.labelKey(name);
        SERVER_TEXT.values().removeIf(c -> CooldownFormat.labelKey(c.name()).equals(key)
                || CooldownFormat.labelKey(c.icon().getHoverName().getString()).equals(key));
        SERVER_REMAINING.keySet().retainAll(SERVER_TEXT.keySet());
        ACTIVE.values().removeIf(c -> CooldownFormat.labelKey(c.name()).equals(key));
        snapshotTime = 0;
    }

    public static void readLegacy(byte[] bytes) {
        var packet = LegacyCooldownPacket.decode(bytes);
        CooldownDiagnostics.record("legacy",packet==null?"":packet.name(),null,packet==null?"unrecognized-packet":"duration="+packet.seconds());
        if (packet == null) return;
        ItemStack icon = switch (packet.itemId()) {
            case 261 -> new ItemStack(Items.BOW);
            case 262 -> new ItemStack(Items.ARROW);
            case 267 -> new ItemStack(Items.IRON_SWORD);
            case 276 -> new ItemStack(Items.DIAMOND_SWORD);
            case 283 -> new ItemStack(Items.GOLDEN_SWORD);
            case 322 -> new ItemStack(Items.GOLDEN_APPLE);
            case 368 -> new ItemStack(Items.ENDER_PEARL);
            case 369 -> new ItemStack(Items.BLAZE_ROD);
            case 397 -> new ItemStack(Items.PLAYER_HEAD);
            default -> new ItemStack(Items.NETHER_STAR);
        };
        ItemStack exact = ServerArtifactCooldowns.iconForName(packet.name());
        display(packet.name(), packet.seconds(), plainIcon(exact.isEmpty() ? icon : exact.copy()));
        Cooldown created = ACTIVE.get(packet.name());
        if (created != null && !exact.isEmpty()) {
            RENDER_ICONS.put(created.icon(), exact.copy()); MODEL_REVISIONS.put(created.icon(), modelRevision);
        }
        ServerArtifactCooldowns.report("legacy:" + packet.name(), "Legacy Lunar cooldown packet for " + packet.name());
    }

    public static void readJson(byte[] bytes) {
        try {
            if(bytes==null || bytes.length>MAX_PACKET)return;
            JsonObject message = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            String type = string(message, "@type");
            CooldownDiagnostics.record("apollo-json",type,null,cooldownType(type)?"cooldown-type":"other-type");
            if (!cooldownType(type)) return;
            if (type.endsWith("ResetCooldownsMessage")) { ACTIVE.clear(); SERVER_TEXT.clear(); SERVER_REMAINING.clear(); snapshotTime = 0; return; }
            String name = string(message, "name");
            if (name.isBlank()) return;
            if (type.endsWith("RemoveCooldownMessage")) { removeServerCooldown(name); return; }
            if (!type.endsWith("DisplayCooldownMessage")) return;
            double seconds = durationSeconds(message.get("duration"));
            Icon icon = iconFromJson(object(message, "icon"));
            display(name, seconds, icon);
        } catch (RuntimeException e) {
            LegendCooldown.LOGGER.debug("Ignored malformed Apollo JSON cooldown", e);
        }
    }

    public static void readProto(byte[] bytes) {
        try {
            if(bytes==null || bytes.length>MAX_PACKET)return;
            Wire envelope = new Wire(bytes);
            String type = envelope.string(1);
            CooldownDiagnostics.record("apollo-proto",type,null,cooldownType(type)?"cooldown-type":"other-type");
            if (!cooldownType(type)) return;
            Wire message = new Wire(envelope.bytes(2));
            if (type.endsWith("ResetCooldownsMessage")) { ACTIVE.clear(); SERVER_TEXT.clear(); SERVER_REMAINING.clear(); snapshotTime = 0; return; }
            String name = message.string(1);
            if (name.isBlank()) return;
            if (type.endsWith("RemoveCooldownMessage")) { removeServerCooldown(name); return; }
            if (!type.endsWith("DisplayCooldownMessage")) return;
            Wire duration = new Wire(message.bytes(2));
            double seconds = duration.number(1) + duration.number(2) / 1_000_000_000.0;
            Icon icon = iconFromProto(new Wire(message.bytes(3)));
            display(name, seconds, icon);
        } catch (RuntimeException e) {
            LegendCooldown.LOGGER.debug("Ignored malformed Apollo protobuf cooldown", e);
        }
    }

    private static void display(String name, double seconds, Icon icon) {
        if (seconds==0) { removeServerCooldown(name);return; }
        if (!Double.isFinite(seconds) || seconds < 0 || seconds > 86_400 || name.length() > 128) return;
        long duration = (long) (seconds * 1_000_000_000L);
        Cooldown cooldown=new Cooldown(name, icon.item(), null, icon.u0(), icon.u1(), icon.v0(), icon.v1(),
                System.nanoTime() + duration, duration,icon.texturePath(),false);
        Cooldown previous=ACTIVE.get(name);
        if(previous!=null) {
            ItemStack bound=RENDER_ICONS.get(previous.icon());
            if(bound!=null && bound!=previous.icon() && matchesModel(cooldown.icon(),bound)) {
                RENDER_ICONS.put(cooldown.icon(),bound);MODEL_REVISIONS.put(cooldown.icon(),modelRevision);
            }
        }
        ACTIVE.put(name,cooldown);
        ServerArtifactCooldowns.report("apollo:" + name, "Apollo cooldown packet for " + name + ": " + seconds + "s");
        iconForRender(cooldown,Minecraft.getInstance());
        snapshotTime = 0;
        while (ACTIVE.size() > 32) ACTIVE.remove(ACTIVE.keySet().iterator().next());
    }

    private static Icon iconFromJson(JsonObject icon) {
        for (String kind : List.of("simple_resource_location", "resource_location", "advanced_resource_location")) {
            JsonObject resource = object(icon, kind);
            if (!resource.isEmpty()) {
                String texture = string(resource, "resource_location");
                boolean advanced = kind.equals("advanced_resource_location");
                return new Icon(ItemStack.EMPTY, texture,
                        advanced ? number(resource, "min_u") : 0,
                        advanced ? positiveOrOne(number(resource, "max_u")) : 1,
                        advanced ? number(resource, "min_v") : 0,
                        advanced ? positiveOrOne(number(resource, "max_v")) : 1);
            }
        }
        JsonObject item = object(icon, "item_stack");
        if (item.isEmpty()) return plainIcon(new ItemStack(Items.NETHER_STAR));
        ItemStack stack = item(string(item, "item_name"));
        JsonObject model = object(item, "custom_model_data_object");
        List<Float> floats = new ArrayList<>();
        List<Boolean> flags = new ArrayList<>();
        List<String> strings = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (JsonElement value : array(model, "floats")) if (floats.size() < 128) floats.add(value.getAsFloat());
        for (JsonElement value : array(model, "flags")) if (flags.size() < 128) flags.add(value.getAsBoolean());
        for (JsonElement value : array(model, "strings")) if (strings.size() < 128) strings.add(value.getAsString());
        for (JsonElement value : array(model, "colors")) if (colors.size() < 128) colors.add(value.getAsInt());
        if (floats.isEmpty() && model.isEmpty() && item.has("custom_model_data"))
            floats.add(number(item, "custom_model_data"));
        if (!floats.isEmpty() || !flags.isEmpty() || !strings.isEmpty() || !colors.isEmpty())
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(floats, flags, strings, colors));
        String modelPath=string(item,"item_model");
        Identifier itemModel=modelPath.isBlank()?null:Identifier.tryParse(modelPath);
        if(itemModel!=null)stack.set(DataComponents.ITEM_MODEL,itemModel);
        return plainIcon(stack);
    }

    private static Icon iconFromProto(Wire icon) {
        for (int field : new int[] {2, 4, 3}) {
            if (icon.bytes(field).length > 0) {
                Wire resource = new Wire(icon.bytes(field));
                String texture = resource.string(1);
                boolean advanced = field == 3;
                return new Icon(ItemStack.EMPTY, texture,
                        advanced ? resource.firstFloat(4) : 0,
                        advanced ? positiveOrOne(resource.firstFloat(5)) : 1,
                        advanced ? resource.firstFloat(6) : 0,
                        advanced ? positiveOrOne(resource.firstFloat(7)) : 1);
            }
        }
        Wire item = new Wire(icon.bytes(1));
        if (item.empty()) return plainIcon(new ItemStack(Items.NETHER_STAR));
        ItemStack stack = item(item.string(2));
        Wire model = new Wire(item.bytes(5));
        List<Float> floats = model.floats(1);
        List<Boolean> flags = model.booleans(2);
        List<String> strings = model.strings(3);
        List<Integer> colors = model.ints(4);
        if (floats.isEmpty() && model.empty() && item.number(3) != 0)
            floats = List.of((float) item.number(3));
        if (!floats.isEmpty() || !flags.isEmpty() || !strings.isEmpty() || !colors.isEmpty())
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(floats, flags, strings, colors));
        return plainIcon(stack);
    }

    private static Icon plainIcon(ItemStack stack) {
        return new Icon(stack, "", 0, 1, 0, 1);
    }

    private static float positiveOrOne(float value) {
        return Float.isFinite(value) && value > 0 ? value : 1;
    }

    static Identifier resolveTexture(String raw) {
        if (RESOLVED_TEXTURES.containsKey(raw)) return RESOLVED_TEXTURES.get(raw);
        // Bound the cache when a server sends many distinct icon paths.
        if (RESOLVED_TEXTURES.size() >= 512) RESOLVED_TEXTURES.clear();
        Identifier resolved = findTexture(raw);
        RESOLVED_TEXTURES.put(raw, resolved);
        return resolved;
    }

    private static Identifier findTexture(String raw) {
        Identifier requested = Identifier.tryParse(raw);
        if (requested == null) return null;
        Minecraft client = Minecraft.getInstance();
        String pathRaw=requested.getPath();
        String prefixed=pathRaw.startsWith("textures/") ? pathRaw : "textures/"+pathRaw;
        for (String path : new java.util.LinkedHashSet<>(List.of(pathRaw,prefixed,prefixed.endsWith(".png")?prefixed:prefixed+".png"))) {
            Identifier candidate = Identifier.fromNamespaceAndPath(requested.getNamespace(), path);
            if (client.getResourceManager().getResource(candidate).isEmpty()) continue;
            if (REGISTERED_TEXTURES.add(candidate))
                client.getTextureManager().registerAndLoad(candidate, new SimpleTexture(candidate));
            return candidate;
        }
        return null;
    }

    private static ItemStack item(String raw) {
        String id = raw.toLowerCase(Locale.ROOT).replaceFirst("^legacy_","");
        id=switch(id) {
            case "gold_sword" -> "golden_sword";
            case "gold_axe" -> "golden_axe";
            case "iron_spade" -> "iron_shovel";
            case "wood_sword" -> "wooden_sword";
            case "wood_axe" -> "wooden_axe";
            case "watch" -> "clock";
            case "skull_item", "skull" -> "player_head";
            case "firework" -> "firework_rocket";
            case "firework_charge" -> "firework_star";
            case "exp_bottle" -> "experience_bottle";
            default -> id;
        };
        Identifier key = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        return key != null && BuiltInRegistries.ITEM.containsKey(key)
                ? new ItemStack(BuiltInRegistries.ITEM.getValue(key)) : new ItemStack(Items.NETHER_STAR);
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static float number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? 0 : value.getAsFloat();
    }

    private static double durationSeconds(JsonElement value) {
        if (value == null || value.isJsonNull()) return 0;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            JsonElement seconds=object.get("seconds"),nanos=object.get("nanos");
            return (seconds==null?0:seconds.getAsDouble())+(nanos==null?0:nanos.getAsDouble())/1_000_000_000.0;
        }
        String raw = value.getAsString().trim();
        if (raw.endsWith("s")) raw = raw.substring(0, raw.length() - 1);
        return Double.parseDouble(raw);
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    /** Minimal protobuf wire reader; only fields required by Apollo cooldowns are retained. */
    private static final class Wire {
        private final Map<Integer, List<byte[]>> bytes = new LinkedHashMap<>();
        private final Map<Integer, List<Long>> numbers = new LinkedHashMap<>();
        private final Map<Integer, List<Integer>> fixed32 = new LinkedHashMap<>();

        Wire(byte[] data) {
            if (data == null) return;
            int[] offset = {0};
            while (offset[0] < data.length) {
                long tag = varint(data, offset);
                int field = (int) (tag >>> 3), wire = (int) (tag & 7);
                if (field == 0) throw new IllegalArgumentException("Invalid protobuf field");
                switch (wire) {
                    case 0 -> numbers.computeIfAbsent(field, unused -> new ArrayList<>()).add(varint(data, offset));
                    case 1 -> offset[0] += 8;
                    case 2 -> {
                        long length = varint(data, offset);
                        if (length < 0 || length > data.length - offset[0]) throw new IllegalArgumentException("Invalid protobuf length");
                        bytes.computeIfAbsent(field, unused -> new ArrayList<>()).add(
                                java.util.Arrays.copyOfRange(data, offset[0], offset[0] + (int) length));
                        offset[0] += (int) length;
                    }
                    case 5 -> {
                        if (offset[0] + 4 > data.length) throw new IllegalArgumentException("Truncated protobuf float");
                        int bits = (data[offset[0]] & 255) | ((data[offset[0] + 1] & 255) << 8)
                                | ((data[offset[0] + 2] & 255) << 16) | ((data[offset[0] + 3] & 255) << 24);
                        fixed32.computeIfAbsent(field, unused -> new ArrayList<>()).add(bits);
                        offset[0] += 4;
                    }
                    default -> throw new IllegalArgumentException("Unsupported protobuf wire type");
                }
                if (offset[0] > data.length) throw new IllegalArgumentException("Truncated protobuf field");
            }
        }

        boolean empty() { return bytes.isEmpty() && numbers.isEmpty() && fixed32.isEmpty(); }
        byte[] bytes(int field) {
            List<byte[]> values = bytes.get(field);
            return values == null || values.isEmpty() ? new byte[0] : values.get(values.size() - 1);
        }
        String string(int field) { return new String(bytes(field), StandardCharsets.UTF_8); }
        long number(int field) {
            List<Long> values = numbers.get(field);
            return values == null || values.isEmpty() ? 0 : values.get(values.size() - 1);
        }
        float firstFloat(int field) {
            List<Float> values = floats(field);
            return values.isEmpty() ? 0 : values.get(0);
        }
        List<Float> floats(int field) {
            List<Float> values = new ArrayList<>();
            for (int bits : fixed32.getOrDefault(field, List.of()))
                if (values.size() < 128) values.add(Float.intBitsToFloat(bits));
            for (byte[] packed : bytes.getOrDefault(field, List.of())) {
                if (packed.length % 4 != 0) continue;
                for (int i = 0; i < packed.length && values.size() < 128; i += 4) {
                    int bits = (packed[i] & 255) | ((packed[i + 1] & 255) << 8)
                            | ((packed[i + 2] & 255) << 16) | ((packed[i + 3] & 255) << 24);
                    values.add(Float.intBitsToFloat(bits));
                }
            }
            return values;
        }
        List<Boolean> booleans(int field) {
            List<Boolean> values = new ArrayList<>();
            for (long value : numbers.getOrDefault(field, List.of()))
                if (values.size() < 128) values.add(value != 0);
            for (long value : packedNumbers(field))
                if (values.size() < 128) values.add(value != 0);
            return values;
        }
        List<Integer> ints(int field) {
            List<Integer> values = new ArrayList<>();
            for (long value : numbers.getOrDefault(field, List.of()))
                if (values.size() < 128) values.add((int) value);
            for (long value : packedNumbers(field))
                if (values.size() < 128) values.add((int) value);
            return values;
        }
        List<String> strings(int field) {
            List<String> values = new ArrayList<>();
            for (byte[] raw : bytes.getOrDefault(field, List.of()))
                if (values.size() < 128) values.add(new String(raw, StandardCharsets.UTF_8));
            return values;
        }
        private List<Long> packedNumbers(int field) {
            List<Long> values = new ArrayList<>();
            for (byte[] packed : bytes.getOrDefault(field, List.of())) {
                int[] offset = {0};
                while (offset[0] < packed.length && values.size() < 128) values.add(varint(packed, offset));
            }
            return values;
        }

        private static long varint(byte[] data, int[] offset) {
            long value = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (offset[0] >= data.length) throw new IllegalArgumentException("Truncated protobuf varint");
                int b = data[offset[0]++] & 255;
                value |= (long) (b & 127) << shift;
                if ((b & 128) == 0) return value;
            }
            throw new IllegalArgumentException("Invalid protobuf varint");
        }
    }
}
