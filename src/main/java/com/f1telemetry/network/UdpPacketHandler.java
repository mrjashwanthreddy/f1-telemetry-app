package com.f1telemetry.network;

import com.f1telemetry.packets.*;
import com.f1telemetry.parser.PacketParser;
import com.f1telemetry.state.CarState;
import com.f1telemetry.state.LiveSessionState;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles incoming UDP DatagramPackets from the F1 game.
 * Parses raw bytes into typed packet objects and logs key values.
 * Throttled logging: logs once every 60 packets to avoid console flood at 60Hz.
 */
@Slf4j
@Sharable
@Component
public class UdpPacketHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final PacketParser packetParser;
    private final LiveSessionState liveSessionState;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final com.f1telemetry.service.EventBroadcastService eventBroadcastService;
    private final com.f1telemetry.engine.RuleEvaluationEngine ruleEngine;
    private final AtomicLong packetCount = new AtomicLong(0);
    private final java.util.concurrent.ConcurrentHashMap<Short, AtomicLong> typeCounters = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long LOG_INTERVAL = 60; // Log once every 60 packets per type

    public UdpPacketHandler(PacketParser packetParser,
                            LiveSessionState liveSessionState,
                            org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate,
                            com.f1telemetry.service.EventBroadcastService eventBroadcastService,
                            com.f1telemetry.engine.RuleEvaluationEngine ruleEngine) {
        this.packetParser = packetParser;
        this.liveSessionState = liveSessionState;
        this.messagingTemplate = messagingTemplate;
        this.eventBroadcastService = eventBroadcastService;
        this.ruleEngine = ruleEngine;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
        ByteBuf content = packet.content();
        try {
            long count = packetCount.incrementAndGet();
            Object parsedPacket = packetParser.parse(content);
            if (parsedPacket == null) return;

            liveSessionState.setLastUpdateTime(System.currentTimeMillis());

            // Extract session UID from the packet header
            PacketHeader header = getHeader(parsedPacket);
            if (header != null && header.getSessionUID() != 0) {
                String sessionUIDStr = String.valueOf(header.getSessionUID());
                if (!sessionUIDStr.equals(liveSessionState.getSessionId())) {
                    liveSessionState.setSessionId(sessionUIDStr);
                }
            }

            // Update in-memory live state
            updateState(parsedPacket);

            // Get packet type ID for per-type throttling
            short packetId = getPacketId(parsedPacket);
            long typeCount = typeCounters
                    .computeIfAbsent(packetId, k -> new AtomicLong(0))
                    .incrementAndGet();

            // Throttled logging: log the first of every 60 packets PER TYPE
            if (typeCount % LOG_INTERVAL == 1) {
                logParsedPacket(parsedPacket, typeCount);
            }
        } catch (Exception e) {
            long count = packetCount.get();
            if (count % LOG_INTERVAL == 1) {
                log.error("Error parsing packet #{}: {}", count, e.getMessage());
            }
        }
    }

    private PacketHeader getHeader(Object parsed) {
        return switch (parsed) {
            case PacketCarTelemetryData p -> p.getHeader();
            case PacketLapData p -> p.getHeader();
            case PacketCarStatusData p -> p.getHeader();
            case PacketCarDamageData p -> p.getHeader();
            case PacketSessionData p -> p.getHeader();
            case PacketMotionData p -> p.getHeader();
            case PacketEventData p -> p.getHeader();
            case PacketParticipantsData p -> p.getHeader();
            default -> null;
        };
    }

    private short getPacketId(Object parsed) {
        return switch (parsed) {
            case PacketCarTelemetryData p -> (short) PacketCarTelemetryData.PACKET_ID;
            case PacketLapData p -> (short) PacketLapData.PACKET_ID;
            case PacketCarStatusData p -> (short) PacketCarStatusData.PACKET_ID;
            case PacketCarDamageData p -> (short) PacketCarDamageData.PACKET_ID;
            case PacketSessionData p -> (short) PacketSessionData.PACKET_ID;
            case PacketMotionData p -> (short) PacketMotionData.PACKET_ID;
            case PacketEventData p -> (short) PacketEventData.PACKET_ID;
            case PacketParticipantsData p -> (short) PacketParticipantsData.PACKET_ID;
            default -> -1;
        };
    }

    private void updateState(Object parsed) {
        switch (parsed) {
            case PacketCarTelemetryData telemetry -> {
                liveSessionState.setPlayerCarIndex(telemetry.getHeader().getPlayerCarIndex());
                for (int i = 0; i < 22; i++) {
                    CarTelemetryData ct = telemetry.getCarTelemetryData()[i];
                    CarState state = liveSessionState.getCars()[i];
                    state.setSpeed(ct.getSpeed());
                    state.setEngineRPM(ct.getEngineRPM());
                    state.setGear(ct.getGear());
                    state.setThrottle(ct.getThrottle());
                    state.setBrake(ct.getBrake());
                    state.setSteer(ct.getSteer());
                    System.arraycopy(ct.getTyresSurfaceTemperature(), 0, state.getTyreSurfaceTemps(), 0, 4);
                }
            }
            case PacketLapData lapData -> {
                liveSessionState.setPlayerCarIndex(lapData.getHeader().getPlayerCarIndex());
                for (int i = 0; i < 22; i++) {
                    LapData lap = lapData.getLapData()[i];
                    CarState carState = liveSessionState.getCars()[i];
                    carState.setPosition(lap.getCarPosition());
                    carState.setCurrentLapNum(lap.getCurrentLapNum());
                    carState.setSector(lap.getSector());
                    carState.setLastLapTimeInMS(lap.getLastLapTimeInMS());
                    carState.setCurrentLapTimeInMS(lap.getCurrentLapTimeInMS());
                    carState.setSector1TimeInMS(lap.getSector1TimeMinutesPart() * 60000 + lap.getSector1TimeMSPart());
                    carState.setSector2TimeInMS(lap.getSector2TimeMinutesPart() * 60000 + lap.getSector2TimeMSPart());
                }
            }
            case PacketCarStatusData status -> {
                liveSessionState.setPlayerCarIndex(status.getHeader().getPlayerCarIndex());
                for (int i = 0; i < 22; i++) {
                    CarStatusData cs = status.getCarStatusData()[i];
                    CarState state = liveSessionState.getCars()[i];
                    state.setFuelInTank(cs.getFuelInTank());
                    state.setErsStoreEnergy(cs.getErsStoreEnergy());
                    state.setVisualTyreCompound(cs.getVisualTyreCompound());
                }
            }
            case PacketCarDamageData damage -> {
                liveSessionState.setPlayerCarIndex(damage.getHeader().getPlayerCarIndex());
                for (int i = 0; i < 22; i++) {
                    CarDamageData cd = damage.getCarDamageData()[i];
                    CarState state = liveSessionState.getCars()[i];
                    System.arraycopy(cd.getTyresWear(), 0, state.getTyreWear(), 0, 4);
                    state.setFrontLeftWingDamage(cd.getFrontLeftWingDamage());
                    state.setFrontRightWingDamage(cd.getFrontRightWingDamage());
                }
            }
            case PacketSessionData session -> {
                liveSessionState.setTrackId(session.getTrackId());
                liveSessionState.setSessionType(session.getSessionType());
                liveSessionState.setWeather(session.getWeather());
                liveSessionState.setTotalLaps(session.getTotalLaps());
                liveSessionState.setSafetyCarStatus(session.getSafetyCarStatus());
                liveSessionState.setWeekendLinkIdentifier(session.getWeekendLinkIdentifier());
                liveSessionState.setGameMode(session.getGameMode());
                liveSessionState.setNetworkGame(session.getNetworkGame());
            }
            // Route event packets to EventBroadcastService for real-time UI notifications
            case PacketEventData event -> {
                eventBroadcastService.handleEvent(event);
                if (PacketEventData.SESSION_ENDED.equals(event.getEventStringCode())) {
                    ruleEngine.endSession();
                }
            }
            default -> {}
        }
    }

    private void logParsedPacket(Object parsed, long count) {
        switch (parsed) {
            case PacketCarTelemetryData telemetry -> {
                int playerIdx = telemetry.getHeader().getPlayerCarIndex();
                CarTelemetryData car = telemetry.getCarTelemetryData()[playerIdx];
                log.info("[Telemetry] Speed: {} km/h | RPM: {} | Gear: {} | Throttle: {} | Brake: {} (packet #{})",
                        car.getSpeed(), car.getEngineRPM(), car.getGear(),
                        car.getThrottle(), car.getBrake(), count);
            }
            case PacketLapData lapData -> {
                int playerIdx = lapData.getHeader().getPlayerCarIndex();
                LapData lap = lapData.getLapData()[playerIdx];
                log.info("[LapData] Lap: {} | Position: P{} | Sector: {} | LapTime: {}ms (packet #{})",
                        lap.getCurrentLapNum(), lap.getCarPosition(), 
                        lap.getSector() + 1, lap.getCurrentLapTimeInMS(), count);
            }
            case PacketCarStatusData status -> {
                int playerIdx = status.getHeader().getPlayerCarIndex();
                CarStatusData car = status.getCarStatusData()[playerIdx];
                log.info("[CarStatus] Fuel: {}L | FuelLaps: {} | ERS: {}J | TyreCompound: {} (packet #{})",
                        car.getFuelInTank(), car.getFuelRemainingLaps(),
                        car.getErsStoreEnergy(), car.getVisualTyreCompound(), count);
            }
            case PacketCarDamageData damage -> {
                int playerIdx = damage.getHeader().getPlayerCarIndex();
                CarDamageData car = damage.getCarDamageData()[playerIdx];
                log.info("[CarDamage] TyreWear: [{}%, {}%, {}%, {}%] | FLWing: {}% | FRWing: {}% (packet #{})",
                        car.getTyresWear()[0], car.getTyresWear()[1],
                        car.getTyresWear()[2], car.getTyresWear()[3],
                        car.getFrontLeftWingDamage(), car.getFrontRightWingDamage(), count);
            }
            case PacketSessionData session -> {
                log.info("[Session] Track: {} | Weather: {} | Laps: {} | SafetyCar: {} (packet #{})",
                        session.getTrackId(), session.getWeather(),
                        session.getTotalLaps(), session.getSafetyCarStatus(), count);
            }
            case PacketMotionData motion -> {
                int playerIdx = motion.getHeader().getPlayerCarIndex();
                CarMotionData car = motion.getCarMotionData()[playerIdx];
                log.info("[Motion] Pos: ({}, {}, {}) | G-Force: Lat={} Long={} (packet #{})",
                        car.getWorldPositionX(), car.getWorldPositionY(), car.getWorldPositionZ(),
                        car.getGForceLateral(), car.getGForceLongitudinal(), count);
            }
            case PacketEventData event -> {
                log.info("[Event] Code: {} (packet #{})", event.getEventStringCode(), count);
            }
            case PacketParticipantsData participants -> {
                log.info("[Participants] Active cars: {} (packet #{})", participants.getNumActiveCars(), count);
            }
            default -> {
                log.debug("[Unknown] Packet type received (packet #{})", count);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Error in UDP packet handler", cause);
    }

    public long getPacketCount() {
        return packetCount.get();
    }
}
