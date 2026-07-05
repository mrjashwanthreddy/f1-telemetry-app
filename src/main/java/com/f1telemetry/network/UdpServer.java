package com.f1telemetry.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Netty UDP Server - binds to configurable host:port to receive F1 telemetry packets.
 * Non-blocking, event-loop driven. Starts on application boot, shuts down gracefully.
 */
@Slf4j
@Component
public class UdpServer {

    @Value("${f1.udp.host:127.0.0.1}")
    private String host;

    @Value("${f1.udp.port:20777}")
    private int port;

    private EventLoopGroup group;
    private Channel channel;

    private final UdpPacketHandler udpPacketHandler;

    public UdpServer(UdpPacketHandler udpPacketHandler) {
        this.udpPacketHandler = udpPacketHandler;
    }

    @PostConstruct
    public void start() {
        group = new NioEventLoopGroup(1); // Single event-loop thread for UDP

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .option(ChannelOption.SO_RCVBUF, 1024 * 1024) // 1MB receive buffer
                    .handler(udpPacketHandler);

            channel = bootstrap.bind(host, port).sync().channel();
            log.info("🏎️  UDP server started on {}:{}", host, port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to start UDP server", e);
        } catch (Exception e) {
            log.error("Failed to bind UDP server to {}:{}", host, port, e);
        }
    }

    @PreDestroy
    public void stop() {
        log.info("Shutting down UDP server...");
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        log.info("UDP server stopped.");
    }
}
