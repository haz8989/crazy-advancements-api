package eu.endercentral.crazy_advancements;

import eu.endercentral.crazy_advancements.events.AdvancementScreenCloseEvent;
import eu.endercentral.crazy_advancements.events.AdvancementTabChangeEvent;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.MessageToMessageDecoder;
import net.minecraft.server.v1_16_R3.NetworkManager;
import net.minecraft.server.v1_16_R3.Packet;
import net.minecraft.server.v1_16_R3.PacketPlayInAdvancements;
import net.minecraft.server.v1_16_R3.PacketPlayInAdvancements.Status;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;

public class AdvancementPacketReceiver {

    private static final HashMap<String, ChannelHandler> handlers = new HashMap<>();
    private static Field channelField;

    static {
        for (Field f : NetworkManager.class.getDeclaredFields()) {
            if (f.getType().isAssignableFrom(Channel.class)) {
                channelField = f;
                channelField.setAccessible(true);
                break;
            }
        }
    }

    interface PacketReceivingHandler {
        boolean handle(Player p, PacketPlayInAdvancements packet);
    }

    public ChannelHandler listen(final Player p, final PacketReceivingHandler handler) {
        Channel ch = getNettyChannel(p);
        ChannelPipeline pipe = ch.pipeline();

        ChannelHandler handle = new MessageToMessageDecoder<Packet>() {
            @Override
            protected void decode(ChannelHandlerContext chc, Packet packet, List<Object> out) throws Exception {

                if (packet instanceof PacketPlayInAdvancements) {
                    if (!handler.handle(p, (PacketPlayInAdvancements) packet)) {
                        out.add(packet);
                    }
                    return;
                }

                out.add(packet);
            }
        };
        pipe.addAfter("decoder", "endercentral_crazy_advancements_listener_" + handler.hashCode(), handle);


        return handle;
    }

    public Channel getNettyChannel(Player p) {
        NetworkManager manager = ((CraftPlayer) p).getHandle().playerConnection.networkManager;
        Channel channel = null;
        try {
            channel = (Channel) channelField.get(manager);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return channel;
    }

    public boolean close(Player p, ChannelHandler handler) {
        try {
            ChannelPipeline pipe = getNettyChannel(p).pipeline();
            pipe.remove(handler);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public HashMap<String, ChannelHandler> getHandlers() {
        return handlers;
    }

    public void initPlayer(Player p) {
        handlers.put(p.getName(), listen(p, new PacketReceivingHandler() {

            @Override
            public boolean handle(Player p, PacketPlayInAdvancements packet) {

                if (packet.c() == Status.OPENED_TAB) {
                    NameKey name = new NameKey(packet.d());
                    AdvancementTabChangeEvent event = new AdvancementTabChangeEvent(p, name);
                    Bukkit.getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        CrazyAdvancements.clearActiveTab(p);
                        return false;
                    } else {
                        if (!event.getTabAdvancement().equals(name)) {
                            CrazyAdvancements.setActiveTab(p, event.getTabAdvancement());
                        } else {
                            CrazyAdvancements.setActiveTab(p, name, false);
                        }
                    }
                } else {
                    AdvancementScreenCloseEvent event = new AdvancementScreenCloseEvent(p);
                    Bukkit.getPluginManager().callEvent(event);
                }


                return true;
            }
        }));
    }

}