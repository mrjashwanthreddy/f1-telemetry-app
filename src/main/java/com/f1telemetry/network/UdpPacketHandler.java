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
        processBuffer(content);
    }

    /**
     * Entry point for the HTTP relay path (desktop .exe → OCI server).
     * Wraps raw bytes in a Netty buffer and runs the same parse pipeline
     * as direct UDP reception.
     */
    public void processRawBytes(byte[] bytes) {
        ByteBuf buf = io.netty.buffer.Unpooled.wrappedBuffer(bytes);
        try {
            processBuffer(buf);
        } finally {
            buf.release();
        }
    }

    private void processBuffer(ByteBuf content) {
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
                    log.info("New session detected: [{}]. Resetting live state cache.", sessionUIDStr);
                    liveSessionState.reset();
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
            case PacketSessionHistoryData p -> p.getHeader();
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
            case PacketSessionHistoryData p -> (short) PacketSessionHistoryData.PACKET_ID;
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
                    float throttle = ct.getThrottle();
                    if (throttle >= 0.97f) {
                        throttle = 1.0f;
                    }
                    state.setThrottle(throttle);
                    state.setBrake(ct.getBrake());
                    state.setSteer(ct.getSteer());
                    state.setDrs(ct.getDrs());
                    System.arraycopy(ct.getTyresSurfaceTemperature(), 0, state.getTyreSurfaceTemps(), 0, 4);
                    System.arraycopy(ct.getBrakesTemperature(), 0, state.getBrakesTemperature(), 0, 4);
                }
            }
            case PacketLapData lapData -> {
                liveSessionState.setPlayerCarIndex(lapData.getHeader().getPlayerCarIndex());
                for (int i = 0; i < 22; i++) {
                    LapData lap = lapData.getLapData()[i];
                    CarState carState = liveSessionState.getCars()[i];
                    
                    short oldLap = carState.getCurrentLapNum();
                    short newLap = lap.getCurrentLapNum();
                    int s1 = lap.getSector1TimeMinutesPart() * 60000 + lap.getSector1TimeMSPart();
                    int s2 = lap.getSector2TimeMinutesPart() * 60000 + lap.getSector2TimeMSPart();

                    // Real-time deltas
                    int deltaLeader = lap.getDeltaToRaceLeaderMinutesPart() * 60000 + lap.getDeltaToRaceLeaderMSPart();
                    int deltaFront = lap.getDeltaToCarInFrontMinutesPart() * 60000 + lap.getDeltaToCarInFrontMSPart();
                    carState.setDeltaToLeaderInMS(deltaLeader);
                    carState.setDeltaToCarInFrontInMS(deltaFront);
                    
                    // Track sector times on lap completion using accumulated sectors
                    if (newLap > oldLap && oldLap > 0) {
                        long lastLapTime = lap.getLastLapTimeInMS();
                        int prevS1 = carState.getSector1TimeInMS();
                        int prevS2 = carState.getSector2TimeInMS();
                        if (lastLapTime > 0 && prevS1 > 0 && prevS2 > 0) {
                            int s3 = (int) lastLapTime - prevS1 - prevS2;
                            if (s3 > 0) {
                                carState.setLastLapSector1TimeInMS(prevS1);
                                carState.setLastLapSector2TimeInMS(prevS2);
                                carState.setLastLapSector3TimeInMS(s3);
                                
                                // Update driver's personal best sector times
                                if (carState.getBestSector1TimeInMS() == 0 || prevS1 < carState.getBestSector1TimeInMS()) {
                                    carState.setBestSector1TimeInMS(prevS1);
                                }
                                if (carState.getBestSector2TimeInMS() == 0 || prevS2 < carState.getBestSector2TimeInMS()) {
                                    carState.setBestSector2TimeInMS(prevS2);
                                }
                                if (carState.getBestSector3TimeInMS() == 0 || s3 < carState.getBestSector3TimeInMS()) {
                                    carState.setBestSector3TimeInMS(s3);
                                }
                            }
                        }
                        // Reset live sector cache for the new lap
                        carState.setSector1TimeInMS(0);
                        carState.setSector2TimeInMS(0);
                    }
                    
                    carState.setPosition(lap.getCarPosition());
                    carState.setCurrentLapNum(newLap);
                    carState.setSector(lap.getSector());
                    carState.setLastLapTimeInMS(lap.getLastLapTimeInMS());
                    carState.setCurrentLapTimeInMS(lap.getCurrentLapTimeInMS());
                    if (lap.getLastLapTimeInMS() > 0) {
                        if (carState.getBestLapTimeInMS() == 0 || lap.getLastLapTimeInMS() < carState.getBestLapTimeInMS()) {
                            carState.setBestLapTimeInMS(lap.getLastLapTimeInMS());
                        }
                    }
                    
                    // Only update the live sector times if they are greater than 0
                    if (s1 > 0) {
                        carState.setSector1TimeInMS(s1);
                    }
                    if (s2 > 0) {
                        carState.setSector2TimeInMS(s2);
                    }
                    
                    carState.setLapDistance(lap.getLapDistance());
                    carState.setResultStatus(lap.getResultStatus());
                }
            }
            case PacketCarStatusData status -> {
                liveSessionState.setPlayerCarIndex(status.getHeader().getPlayerCarIndex());
                for (int i = 0; i < 22; i++) {
                    CarStatusData cs = status.getCarStatusData()[i];
                    CarState state = liveSessionState.getCars()[i];
                    state.setFuelInTank(cs.getFuelInTank());
                    state.setFuelRemainingLaps(cs.getFuelRemainingLaps());
                    state.setErsStoreEnergy(cs.getErsStoreEnergy());
                    state.setErsDeployMode(cs.getErsDeployMode());
                    state.setDrsAllowed(cs.getDrsAllowed());
                    state.setVisualTyreCompound(cs.getVisualTyreCompound());
                    state.setTyresAgeLaps(cs.getTyresAgeLaps());
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
                liveSessionState.setTrackLength(session.getTrackLength());
                liveSessionState.setSessionType(session.getSessionType());
                liveSessionState.setWeather(session.getWeather());
                liveSessionState.setTrackTemperature(session.getTrackTemperature());
                liveSessionState.setAirTemperature(session.getAirTemperature());
                liveSessionState.setTotalLaps(session.getTotalLaps());
                liveSessionState.setSafetyCarStatus(session.getSafetyCarStatus());
                liveSessionState.setWeekendLinkIdentifier(session.getWeekendLinkIdentifier());
                liveSessionState.setGameMode(session.getGameMode());
                liveSessionState.setNetworkGame(session.getNetworkGame());
                liveSessionState.setSector2LapDistanceStart(session.getSector2LapDistanceStart());
                liveSessionState.setSector3LapDistanceStart(session.getSector3LapDistanceStart());

                // Inspect marshal zones for track flags
                short activeFlag = 1; // 1 = Green flag
                if (session.getMarshalZones() != null) {
                    for (int m = 0; m < session.getNumMarshalZones() && m < 21; m++) {
                        var mz = session.getMarshalZones()[m];
                        if (mz != null && mz.getZoneFlag() == 3) {
                            activeFlag = 3; // Yellow flag
                            break;
                        }
                    }
                }
                liveSessionState.setTrackFlag(activeFlag);

                // Populate weather forecast timeline
                int numSamples = session.getNumWeatherForecastSamples();
                if (numSamples > 0 && session.getWeatherForecastSamples() != null) {
                    var sample0 = session.getWeatherForecastSamples()[0];
                    if (sample0 != null) {
                        liveSessionState.setRainPercentage(sample0.getRainPercentage());
                    }
                    java.util.List<com.f1telemetry.dto.WeatherForecastDTO> forecasts = new java.util.ArrayList<>();
                    for (int s = 0; s < numSamples && s < 64; s++) {
                        var sample = session.getWeatherForecastSamples()[s];
                        if (sample != null) {
                            forecasts.add(new com.f1telemetry.dto.WeatherForecastDTO(
                                sample.getTimeOffset(),
                                sample.getWeather(),
                                sample.getTrackTemperature(),
                                sample.getAirTemperature(),
                                sample.getRainPercentage()
                            ));
                        }
                    }
                    liveSessionState.setWeatherForecasts(forecasts);
                }
            }
            case PacketParticipantsData participants -> {
                liveSessionState.setPlayerCarIndex(participants.getHeader().getPlayerCarIndex());
                for (int i = 0; i < 22; i++) {
                    ParticipantData pd = participants.getParticipants()[i];
                    CarState state = liveSessionState.getCars()[i];
                    if (pd != null) {
                        String name = pd.getName();
                        if (name != null && !name.isBlank()) {
                            state.setName(name);
                        }
                        state.setTeamId(pd.getTeamId());
                        state.setDriverId(pd.getDriverId());
                    }
                }
            }
            case PacketSessionHistoryData history -> {
                int carIdx = history.getCarIdx();
                if (carIdx >= 0 && carIdx < 22) {
                    CarState car = liveSessionState.getCars()[carIdx];
                    int bestLapNum = history.getBestLapTimeLapNum();
                    int bestS1Num = history.getBestSector1LapNum();
                    int bestS2Num = history.getBestSector2LapNum();
                    int bestS3Num = history.getBestSector3LapNum();
                    LapHistoryData[] laps = history.getLapHistoryData();

                    if (bestLapNum > 0 && bestLapNum <= 100 && laps[bestLapNum - 1] != null) {
                        long pb = laps[bestLapNum - 1].getLapTimeInMS();
                        if (pb > 0) car.setBestLapTimeInMS(pb);
                    }
                    if (bestS1Num > 0 && bestS1Num <= 100 && laps[bestS1Num - 1] != null) {
                        int s1 = laps[bestS1Num - 1].getSector1TimeInMS();
                        if (s1 > 0) car.setBestSector1TimeInMS(s1);
                    }
                    if (bestS2Num > 0 && bestS2Num <= 100 && laps[bestS2Num - 1] != null) {
                        int s2 = laps[bestS2Num - 1].getSector2TimeInMS();
                        if (s2 > 0) car.setBestSector2TimeInMS(s2);
                    }
                    if (bestS3Num > 0 && bestS3Num <= 100 && laps[bestS3Num - 1] != null) {
                        int s3 = laps[bestS3Num - 1].getSector3TimeInMS();
                        if (s3 > 0) car.setBestSector3TimeInMS(s3);
                    }

                    int numLaps = history.getNumLaps();
                    if (numLaps > 1) {
                        int lastCompletedIdx = numLaps - 2;
                        if (lastCompletedIdx >= 0 && lastCompletedIdx < 100 && laps[lastCompletedIdx] != null) {
                            LapHistoryData lastLap = laps[lastCompletedIdx];
                            if (lastLap.getLapTimeInMS() > 0) {
                                car.setLastLapTimeInMS(lastLap.getLapTimeInMS());
                            }
                            if (lastLap.getSector1TimeInMS() > 0) {
                                car.setLastLapSector1TimeInMS(lastLap.getSector1TimeInMS());
                            }
                            if (lastLap.getSector2TimeInMS() > 0) {
                                car.setLastLapSector2TimeInMS(lastLap.getSector2TimeInMS());
                            }
                            if (lastLap.getSector3TimeInMS() > 0) {
                                car.setLastLapSector3TimeInMS(lastLap.getSector3TimeInMS());
                            }
                        }
                    }
                }
            }
            // Phase 10: Motion data — capture lateral G-force for corner zone analysis
            case PacketMotionData motion -> {
                liveSessionState.setPlayerCarIndex(motion.getHeader().getPlayerCarIndex());
                for (int i = 0; i < 22; i++) {
                    CarMotionData cm = motion.getCarMotionData()[i];
                    liveSessionState.getCars()[i].setGForceLateral(cm.getGForceLateral());
                }
            }
            // Route event packets to EventBroadcastService for real-time UI notifications
            case PacketEventData event -> {
                eventBroadcastService.handleEvent(event);
                if (PacketEventData.SESSION_ENDED.equals(event.getEventStringCode())) {
                    ruleEngine.endSession(liveSessionState);
                } else if ("CHQF".equals(event.getEventStringCode())) {
                    liveSessionState.setChequeredFlag(true);
                } else if ("RDFL".equals(event.getEventStringCode())) {
                    liveSessionState.setRedFlag(true);
                } else if ("SSTA".equals(event.getEventStringCode())) {
                    liveSessionState.setChequeredFlag(false);
                    liveSessionState.setRedFlag(false);
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
                for (int i = 0; i < 22; i++) {
                    ParticipantData pd = participants.getParticipants()[i];
                    CarState state = liveSessionState.getCars()[i];
                    if (pd != null) {
                        state.setName(pd.getName());
                        state.setTeamId(pd.getTeamId());
                    }
                }
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
