package com.f1telemetry.parser;

import com.f1telemetry.packets.*;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * Low-level binary deserialization for all F1 25 packet types.
 * All reads use Little-Endian byte order matching the F1 game's C-struct output.
 * 
 * Convention:
 *   - uint8  -> readUnsignedByte() returns short
 *   - int8   -> readByte()
 *   - uint16 -> readUnsignedShortLE() returns int
 *   - int16  -> readShortLE()
 *   - uint32 -> readUnsignedIntLE() returns long
 *   - float  -> readFloatLE()
 *   - double -> readDoubleLE()
 *   - uint64 -> readLongLE()
 */
public class PacketDeserializer {

    // ===== HEADER (29 bytes) =====

    public PacketHeader deserializeHeader(ByteBuf buf) {
        PacketHeader h = new PacketHeader();
        h.setPacketFormat(buf.readUnsignedShortLE());       // uint16
        h.setGameYear(buf.readUnsignedByte());              // uint8
        h.setGameMajorVersion(buf.readUnsignedByte());      // uint8
        h.setGameMinorVersion(buf.readUnsignedByte());      // uint8
        h.setPacketVersion(buf.readUnsignedByte());         // uint8
        h.setPacketId(buf.readUnsignedByte());              // uint8
        h.setSessionUID(buf.readLongLE());                  // uint64
        h.setSessionTime(buf.readFloatLE());                // float
        h.setFrameIdentifier(buf.readUnsignedIntLE());      // uint32
        h.setOverallFrameIdentifier(buf.readUnsignedIntLE());// uint32
        h.setPlayerCarIndex(buf.readUnsignedByte());        // uint8
        h.setSecondaryPlayerCarIndex(buf.readUnsignedByte());// uint8
        return h;
    }

    // ===== MOTION (Packet ID: 0) =====

    public PacketMotionData deserializeMotion(PacketHeader header, ByteBuf buf) {
        PacketMotionData packet = new PacketMotionData();
        packet.setHeader(header);
        for (int i = 0; i < 22; i++) {
            packet.getCarMotionData()[i] = deserializeCarMotion(buf);
        }
        return packet;
    }

    private CarMotionData deserializeCarMotion(ByteBuf buf) {
        CarMotionData d = new CarMotionData();
        d.setWorldPositionX(buf.readFloatLE());
        d.setWorldPositionY(buf.readFloatLE());
        d.setWorldPositionZ(buf.readFloatLE());
        d.setWorldVelocityX(buf.readFloatLE());
        d.setWorldVelocityY(buf.readFloatLE());
        d.setWorldVelocityZ(buf.readFloatLE());
        d.setWorldForwardDirX(buf.readShortLE());
        d.setWorldForwardDirY(buf.readShortLE());
        d.setWorldForwardDirZ(buf.readShortLE());
        d.setWorldRightDirX(buf.readShortLE());
        d.setWorldRightDirY(buf.readShortLE());
        d.setWorldRightDirZ(buf.readShortLE());
        d.setGForceLateral(buf.readFloatLE());
        d.setGForceLongitudinal(buf.readFloatLE());
        d.setGForceVertical(buf.readFloatLE());
        d.setYaw(buf.readFloatLE());
        d.setPitch(buf.readFloatLE());
        d.setRoll(buf.readFloatLE());
        return d;
    }

    // ===== SESSION (Packet ID: 1) =====

    public PacketSessionData deserializeSession(PacketHeader header, ByteBuf buf) {
        PacketSessionData p = new PacketSessionData();
        p.setHeader(header);
        p.setWeather(buf.readUnsignedByte());
        p.setTrackTemperature(buf.readByte());
        p.setAirTemperature(buf.readByte());
        p.setTotalLaps(buf.readUnsignedByte());
        p.setTrackLength(buf.readUnsignedShortLE());
        p.setSessionType(buf.readUnsignedByte());
        p.setTrackId(buf.readByte());
        p.setFormula(buf.readUnsignedByte());
        p.setSessionTimeLeft(buf.readUnsignedShortLE());
        p.setSessionDuration(buf.readUnsignedShortLE());
        p.setPitSpeedLimit(buf.readUnsignedByte());
        p.setGamePaused(buf.readUnsignedByte());
        p.setIsSpectating(buf.readUnsignedByte());
        p.setSpectatorCarIndex(buf.readUnsignedByte());
        p.setSliProNativeSupport(buf.readUnsignedByte());
        p.setNumMarshalZones(buf.readUnsignedByte());

        for (int i = 0; i < 21; i++) {
            MarshalZone mz = new MarshalZone();
            mz.setZoneStart(buf.readFloatLE());
            mz.setZoneFlag(buf.readByte());
            p.getMarshalZones()[i] = mz;
        }

        p.setSafetyCarStatus(buf.readUnsignedByte());
        p.setNetworkGame(buf.readUnsignedByte());
        p.setNumWeatherForecastSamples(buf.readUnsignedByte());

        for (int i = 0; i < 64; i++) {
            WeatherForecastSample wfs = new WeatherForecastSample();
            wfs.setSessionType(buf.readUnsignedByte());
            wfs.setTimeOffset(buf.readUnsignedByte());
            wfs.setWeather(buf.readUnsignedByte());
            wfs.setTrackTemperature(buf.readByte());
            wfs.setTrackTemperatureChange(buf.readByte());
            wfs.setAirTemperature(buf.readByte());
            wfs.setAirTemperatureChange(buf.readByte());
            wfs.setRainPercentage(buf.readUnsignedByte());
            p.getWeatherForecastSamples()[i] = wfs;
        }

        p.setForecastAccuracy(buf.readUnsignedByte());
        p.setAiDifficulty(buf.readUnsignedByte());
        p.setSeasonLinkIdentifier(buf.readUnsignedIntLE());
        p.setWeekendLinkIdentifier(buf.readUnsignedIntLE());
        p.setSessionLinkIdentifier(buf.readUnsignedIntLE());
        p.setPitStopWindowIdealLap(buf.readUnsignedByte());
        p.setPitStopWindowLatestLap(buf.readUnsignedByte());
        p.setPitStopRejoinPosition(buf.readUnsignedByte());
        p.setSteeringAssist(buf.readUnsignedByte());
        p.setBrakingAssist(buf.readUnsignedByte());
        p.setGearboxAssist(buf.readUnsignedByte());
        p.setPitAssist(buf.readUnsignedByte());
        p.setPitReleaseAssist(buf.readUnsignedByte());
        p.setErsAssist(buf.readUnsignedByte());
        p.setDrsAssist(buf.readUnsignedByte());
        p.setDynamicRacingLine(buf.readUnsignedByte());
        p.setDynamicRacingLineType(buf.readUnsignedByte());
        p.setGameMode(buf.readUnsignedByte());
        p.setRuleSet(buf.readUnsignedByte());
        p.setTimeOfDay(buf.readUnsignedIntLE());
        p.setSessionLength(buf.readUnsignedByte());
        p.setSpeedUnitsLeadPlayer(buf.readUnsignedByte());
        p.setTemperatureUnitsLeadPlayer(buf.readUnsignedByte());
        p.setSpeedUnitsSecondaryPlayer(buf.readUnsignedByte());
        p.setTemperatureUnitsSecondaryPlayer(buf.readUnsignedByte());
        p.setNumSafetyCarPeriods(buf.readUnsignedByte());
        p.setNumVirtualSafetyCarPeriods(buf.readUnsignedByte());
        p.setNumRedFlagPeriods(buf.readUnsignedByte());
        p.setEqualCarPerformance(buf.readUnsignedByte());
        p.setRecoveryMode(buf.readUnsignedByte());
        p.setFlashbackLimit(buf.readUnsignedByte());
        p.setSurfaceType(buf.readUnsignedByte());
        p.setLowFuelMode(buf.readUnsignedByte());
        p.setRaceStarts(buf.readUnsignedByte());
        p.setTyreTemperature(buf.readUnsignedByte());
        p.setPitLaneTyreSim(buf.readUnsignedByte());
        p.setCarDamage(buf.readUnsignedByte());
        p.setCarDamageRate(buf.readUnsignedByte());
        p.setCollisions(buf.readUnsignedByte());
        p.setCollisionsOffForFirstLapOnly(buf.readUnsignedByte());
        p.setMpUnsafePitRelease(buf.readUnsignedByte());
        p.setMpOffForGriefing(buf.readUnsignedByte());
        p.setCornerCuttingStringency(buf.readUnsignedByte());
        p.setParcFermeRules(buf.readUnsignedByte());
        p.setPitStopExperience(buf.readUnsignedByte());
        p.setSafetyCar(buf.readUnsignedByte());
        p.setSafetyCarExperience(buf.readUnsignedByte());
        p.setFormationLap(buf.readUnsignedByte());
        p.setFormationLapExperience(buf.readUnsignedByte());
        p.setRedFlags(buf.readUnsignedByte());
        p.setAffectsLicenceLevelSolo(buf.readUnsignedByte());
        p.setAffectsLicenceLevelMP(buf.readUnsignedByte());
        p.setNumSessionsInWeekend(buf.readUnsignedByte());

        short[] weekendStructure = new short[12];
        for (int i = 0; i < 12; i++) {
            weekendStructure[i] = buf.readUnsignedByte();
        }
        p.setWeekendStructure(weekendStructure);

        p.setSector2LapDistanceStart(buf.readFloatLE());
        p.setSector3LapDistanceStart(buf.readFloatLE());

        return p;
    }

    // ===== LAP DATA (Packet ID: 2) =====

    public PacketLapData deserializeLapData(PacketHeader header, ByteBuf buf) {
        PacketLapData packet = new PacketLapData();
        packet.setHeader(header);
        for (int i = 0; i < 22; i++) {
            packet.getLapData()[i] = deserializeLap(buf);
        }
        packet.setTimeTrialPBCarIdx(buf.readUnsignedByte());
        packet.setTimeTrialRivalCarIdx(buf.readUnsignedByte());
        return packet;
    }

    private LapData deserializeLap(ByteBuf buf) {
        LapData d = new LapData();
        d.setLastLapTimeInMS(buf.readUnsignedIntLE());
        d.setCurrentLapTimeInMS(buf.readUnsignedIntLE());
        d.setSector1TimeMSPart(buf.readUnsignedShortLE());
        d.setSector1TimeMinutesPart(buf.readUnsignedByte());
        d.setSector2TimeMSPart(buf.readUnsignedShortLE());
        d.setSector2TimeMinutesPart(buf.readUnsignedByte());
        d.setDeltaToCarInFrontMSPart(buf.readUnsignedShortLE());
        d.setDeltaToCarInFrontMinutesPart(buf.readUnsignedByte());
        d.setDeltaToRaceLeaderMSPart(buf.readUnsignedShortLE());
        d.setDeltaToRaceLeaderMinutesPart(buf.readUnsignedByte());
        d.setLapDistance(buf.readFloatLE());
        d.setTotalDistance(buf.readFloatLE());
        d.setSafetyCarDelta(buf.readFloatLE());
        d.setCarPosition(buf.readUnsignedByte());
        d.setCurrentLapNum(buf.readUnsignedByte());
        d.setPitStatus(buf.readUnsignedByte());
        d.setNumPitStops(buf.readUnsignedByte());
        d.setSector(buf.readUnsignedByte());
        d.setCurrentLapInvalid(buf.readUnsignedByte());
        d.setPenalties(buf.readUnsignedByte());
        d.setTotalWarnings(buf.readUnsignedByte());
        d.setCornerCuttingWarnings(buf.readUnsignedByte());
        d.setNumUnservedDriveThroughPens(buf.readUnsignedByte());
        d.setNumUnservedStopGoPens(buf.readUnsignedByte());
        d.setGridPosition(buf.readUnsignedByte());
        d.setDriverStatus(buf.readUnsignedByte());
        d.setResultStatus(buf.readUnsignedByte());
        d.setPitLaneTimerActive(buf.readUnsignedByte());
        d.setPitLaneTimeInLaneInMS(buf.readUnsignedShortLE());
        d.setPitStopTimerInMS(buf.readUnsignedShortLE());
        d.setPitStopShouldServePen(buf.readUnsignedByte());
        d.setSpeedTrapFastestSpeed(buf.readFloatLE());
        d.setSpeedTrapFastestLap(buf.readUnsignedByte());
        return d;
    }

    // ===== EVENT (Packet ID: 3) =====

    public PacketEventData deserializeEvent(PacketHeader header, ByteBuf buf) {
        PacketEventData packet = new PacketEventData();
        packet.setHeader(header);

        // Read 4-byte event string code
        byte[] codeBytes = new byte[4];
        buf.readBytes(codeBytes);
        packet.setEventStringCode(new String(codeBytes, StandardCharsets.UTF_8));

        EventDataDetails details = new EventDataDetails();
        String code = packet.getEventStringCode();

        switch (code) {
            case PacketEventData.FASTEST_LAP -> {
                details.setFastestLapVehicleIdx(buf.readUnsignedByte());
                details.setFastestLapTime(buf.readFloatLE());
            }
            case PacketEventData.RETIREMENT -> {
                details.setRetirementVehicleIdx(buf.readUnsignedByte());
                details.setRetirementReason(buf.readUnsignedByte());
            }
            case PacketEventData.DRS_DISABLED -> {
                details.setDrsDisabledReason(buf.readUnsignedByte());
            }
            case PacketEventData.TEAM_MATE_IN_PITS -> {
                details.setTeamMateVehicleIdx(buf.readUnsignedByte());
            }
            case PacketEventData.RACE_WINNER -> {
                details.setRaceWinnerVehicleIdx(buf.readUnsignedByte());
            }
            case PacketEventData.PENALTY_ISSUED -> {
                details.setPenaltyType(buf.readUnsignedByte());
                details.setInfringementType(buf.readUnsignedByte());
                details.setPenaltyVehicleIdx(buf.readUnsignedByte());
                details.setPenaltyOtherVehicleIdx(buf.readUnsignedByte());
                details.setPenaltyTime(buf.readUnsignedByte());
                details.setPenaltyLapNum(buf.readUnsignedByte());
                details.setPenaltyPlacesGained(buf.readUnsignedByte());
            }
            case PacketEventData.SPEED_TRAP -> {
                details.setSpeedTrapVehicleIdx(buf.readUnsignedByte());
                details.setSpeedTrapSpeed(buf.readFloatLE());
                details.setSpeedTrapIsOverallFastest(buf.readUnsignedByte());
                details.setSpeedTrapIsDriverFastest(buf.readUnsignedByte());
                details.setSpeedTrapFastestVehicleIdx(buf.readUnsignedByte());
                details.setSpeedTrapFastestSpeed(buf.readFloatLE());
            }
            case PacketEventData.START_LIGHTS -> {
                details.setNumLights(buf.readUnsignedByte());
            }
            case PacketEventData.DRIVE_THROUGH_SERVED -> {
                details.setDriveThroughVehicleIdx(buf.readUnsignedByte());
            }
            case PacketEventData.STOP_GO_SERVED -> {
                details.setStopGoVehicleIdx(buf.readUnsignedByte());
                details.setStopGoTime(buf.readFloatLE());
            }
            case PacketEventData.FLASHBACK -> {
                details.setFlashbackFrameIdentifier(buf.readUnsignedIntLE());
                details.setFlashbackSessionTime(buf.readFloatLE());
            }
            case PacketEventData.BUTTON_STATUS -> {
                details.setButtonStatus(buf.readUnsignedIntLE());
            }
            case PacketEventData.OVERTAKE -> {
                details.setOvertakingVehicleIdx(buf.readUnsignedByte());
                details.setBeingOvertakenVehicleIdx(buf.readUnsignedByte());
            }
            case PacketEventData.SAFETY_CAR -> {
                details.setSafetyCarType(buf.readUnsignedByte());
                details.setSafetyCarEventType(buf.readUnsignedByte());
            }
            case PacketEventData.COLLISION -> {
                details.setCollisionVehicle1Idx(buf.readUnsignedByte());
                details.setCollisionVehicle2Idx(buf.readUnsignedByte());
            }
            default -> {
                // Events with no extra data: SSTA, SEND, DRSE, CHQF, LGOT, RDFL
            }
        }

        packet.setEventDetails(details);
        return packet;
    }

    // ===== PARTICIPANTS (Packet ID: 4) =====

    public PacketParticipantsData deserializeParticipants(PacketHeader header, ByteBuf buf) {
        PacketParticipantsData packet = new PacketParticipantsData();
        packet.setHeader(header);
        packet.setNumActiveCars(buf.readUnsignedByte());

        for (int i = 0; i < 22; i++) {
            ParticipantData pd = new ParticipantData();
            pd.setAiControlled(buf.readUnsignedByte());
            pd.setDriverId(buf.readUnsignedByte());
            pd.setNetworkId(buf.readUnsignedByte());
            pd.setTeamId(buf.readUnsignedByte());
            pd.setMyTeam(buf.readUnsignedByte());
            pd.setRaceNumber(buf.readUnsignedByte());
            pd.setNationality(buf.readUnsignedByte());
            // Dynamic name length: 48 bytes in F1 24, 32 bytes in F1 23/25
            int nameLength = (header.getGameYear() == 24) ? 48 : 32;
            byte[] nameBytes = new byte[nameLength];
            buf.readBytes(nameBytes);
            int nameLen = 0;
            while (nameLen < nameBytes.length && nameBytes[nameLen] != 0) {
                nameLen++;
            }
            pd.setName(new String(nameBytes, 0, nameLen, StandardCharsets.UTF_8));
            pd.setYourTelemetry(buf.readUnsignedByte());
            pd.setShowOnlineNames(buf.readUnsignedByte());
            pd.setTechLevel(buf.readUnsignedShortLE());
            pd.setPlatform(buf.readUnsignedByte());
            pd.setNumColours(buf.readUnsignedByte());

            // LiveryColour[4] - 3 bytes each (RGB)
            short[][] colours = new short[4][3];
            for (int c = 0; c < 4; c++) {
                colours[c][0] = buf.readUnsignedByte(); // red
                colours[c][1] = buf.readUnsignedByte(); // green
                colours[c][2] = buf.readUnsignedByte(); // blue
            }
            pd.setLiveryColours(colours);

            packet.getParticipants()[i] = pd;
        }
        return packet;
    }

    // ===== CAR SETUPS (Packet ID: 5) =====

    public PacketCarSetupData deserializeCarSetups(PacketHeader header, ByteBuf buf) {
        PacketCarSetupData packet = new PacketCarSetupData();
        packet.setHeader(header);

        for (int i = 0; i < 22; i++) {
            CarSetupData cs = new CarSetupData();
            cs.setFrontWing(buf.readUnsignedByte());
            cs.setRearWing(buf.readUnsignedByte());
            cs.setOnThrottle(buf.readUnsignedByte());
            cs.setOffThrottle(buf.readUnsignedByte());
            cs.setFrontCamber(buf.readFloatLE());
            cs.setRearCamber(buf.readFloatLE());
            cs.setFrontToe(buf.readFloatLE());
            cs.setRearToe(buf.readFloatLE());
            cs.setFrontSuspension(buf.readUnsignedByte());
            cs.setRearSuspension(buf.readUnsignedByte());
            cs.setFrontAntiRollBar(buf.readUnsignedByte());
            cs.setRearAntiRollBar(buf.readUnsignedByte());
            cs.setFrontSuspensionHeight(buf.readUnsignedByte());
            cs.setRearSuspensionHeight(buf.readUnsignedByte());
            cs.setBrakePressure(buf.readUnsignedByte());
            cs.setBrakeBias(buf.readUnsignedByte());
            cs.setEngineBraking(buf.readUnsignedByte());
            cs.setRearLeftTyrePressure(buf.readFloatLE());
            cs.setRearRightTyrePressure(buf.readFloatLE());
            cs.setFrontLeftTyrePressure(buf.readFloatLE());
            cs.setFrontRightTyrePressure(buf.readFloatLE());
            cs.setBallast(buf.readUnsignedByte());
            cs.setFuelLoad(buf.readFloatLE());
            packet.getCarSetups()[i] = cs;
        }

        packet.setNextFrontWingValue(buf.readFloatLE());
        return packet;
    }

    // ===== CAR TELEMETRY (Packet ID: 6) =====

    public PacketCarTelemetryData deserializeCarTelemetry(PacketHeader header, ByteBuf buf) {
        PacketCarTelemetryData packet = new PacketCarTelemetryData();
        packet.setHeader(header);

        for (int i = 0; i < 22; i++) {
            CarTelemetryData ct = new CarTelemetryData();
            ct.setSpeed(buf.readUnsignedShortLE());
            ct.setThrottle(buf.readFloatLE());
            ct.setSteer(buf.readFloatLE());
            ct.setBrake(buf.readFloatLE());
            ct.setClutch(buf.readUnsignedByte());
            ct.setGear(buf.readByte());
            ct.setEngineRPM(buf.readUnsignedShortLE());
            ct.setDrs(buf.readUnsignedByte());
            ct.setRevLightsPercent(buf.readUnsignedByte());
            ct.setRevLightsBitValue(buf.readUnsignedShortLE());

            int[] brakesTemp = new int[4];
            for (int j = 0; j < 4; j++) brakesTemp[j] = buf.readUnsignedShortLE();
            ct.setBrakesTemperature(brakesTemp);

            short[] tyreSurface = new short[4];
            for (int j = 0; j < 4; j++) tyreSurface[j] = buf.readUnsignedByte();
            ct.setTyresSurfaceTemperature(tyreSurface);

            short[] tyreInner = new short[4];
            for (int j = 0; j < 4; j++) tyreInner[j] = buf.readUnsignedByte();
            ct.setTyresInnerTemperature(tyreInner);

            ct.setEngineTemperature(buf.readUnsignedShortLE());

            float[] tyrePressure = new float[4];
            for (int j = 0; j < 4; j++) tyrePressure[j] = buf.readFloatLE();
            ct.setTyresPressure(tyrePressure);

            short[] surface = new short[4];
            for (int j = 0; j < 4; j++) surface[j] = buf.readUnsignedByte();
            ct.setSurfaceType(surface);

            packet.getCarTelemetryData()[i] = ct;
        }

        packet.setMfdPanelIndex(buf.readUnsignedByte());
        packet.setMfdPanelIndexSecondaryPlayer(buf.readUnsignedByte());
        packet.setSuggestedGear(buf.readByte());

        return packet;
    }

    // ===== CAR STATUS (Packet ID: 7) =====

    public PacketCarStatusData deserializeCarStatus(PacketHeader header, ByteBuf buf) {
        PacketCarStatusData packet = new PacketCarStatusData();
        packet.setHeader(header);

        for (int i = 0; i < 22; i++) {
            CarStatusData cs = new CarStatusData();
            cs.setTractionControl(buf.readUnsignedByte());
            cs.setAntiLockBrakes(buf.readUnsignedByte());
            cs.setFuelMix(buf.readUnsignedByte());
            cs.setFrontBrakeBias(buf.readUnsignedByte());
            cs.setPitLimiterStatus(buf.readUnsignedByte());
            cs.setFuelInTank(buf.readFloatLE());
            cs.setFuelCapacity(buf.readFloatLE());
            cs.setFuelRemainingLaps(buf.readFloatLE());
            cs.setMaxRPM(buf.readUnsignedShortLE());
            cs.setIdleRPM(buf.readUnsignedShortLE());
            cs.setMaxGears(buf.readUnsignedByte());
            cs.setDrsAllowed(buf.readUnsignedByte());
            cs.setDrsActivationDistance(buf.readUnsignedShortLE());
            cs.setActualTyreCompound(buf.readUnsignedByte());
            cs.setVisualTyreCompound(buf.readUnsignedByte());
            cs.setTyresAgeLaps(buf.readUnsignedByte());
            cs.setVehicleFiaFlags(buf.readByte());
            cs.setEnginePowerICE(buf.readFloatLE());
            cs.setEnginePowerMGUK(buf.readFloatLE());
            cs.setErsStoreEnergy(buf.readFloatLE());
            cs.setErsDeployMode(buf.readUnsignedByte());
            cs.setErsHarvestedThisLapMGUK(buf.readFloatLE());
            cs.setErsHarvestedThisLapMGUH(buf.readFloatLE());
            cs.setErsDeployedThisLap(buf.readFloatLE());
            cs.setNetworkPaused(buf.readUnsignedByte());
            packet.getCarStatusData()[i] = cs;
        }

        return packet;
    }

    // ===== FINAL CLASSIFICATION (Packet ID: 8) =====

    public PacketFinalClassificationData deserializeFinalClassification(PacketHeader header, ByteBuf buf) {
        PacketFinalClassificationData packet = new PacketFinalClassificationData();
        packet.setHeader(header);
        packet.setNumCars(buf.readUnsignedByte());

        for (int i = 0; i < 22; i++) {
            FinalClassificationData fc = new FinalClassificationData();
            fc.setPosition(buf.readUnsignedByte());
            fc.setNumLaps(buf.readUnsignedByte());
            fc.setGridPosition(buf.readUnsignedByte());
            fc.setPoints(buf.readUnsignedByte());
            fc.setNumPitStops(buf.readUnsignedByte());
            fc.setResultStatus(buf.readUnsignedByte());
            fc.setResultReason(buf.readUnsignedByte());
            fc.setBestLapTimeInMS(buf.readUnsignedIntLE());
            fc.setTotalRaceTime(buf.readDoubleLE());
            fc.setPenaltiesTime(buf.readUnsignedByte());
            fc.setNumPenalties(buf.readUnsignedByte());
            fc.setNumTyreStints(buf.readUnsignedByte());

            short[] actual = new short[8];
            for (int j = 0; j < 8; j++) actual[j] = buf.readUnsignedByte();
            fc.setTyreStintsActual(actual);

            short[] visual = new short[8];
            for (int j = 0; j < 8; j++) visual[j] = buf.readUnsignedByte();
            fc.setTyreStintsVisual(visual);

            short[] endLaps = new short[8];
            for (int j = 0; j < 8; j++) endLaps[j] = buf.readUnsignedByte();
            fc.setTyreStintsEndLaps(endLaps);

            packet.getClassificationData()[i] = fc;
        }

        return packet;
    }

    // ===== CAR DAMAGE (Packet ID: 10) =====

    public PacketCarDamageData deserializeCarDamage(PacketHeader header, ByteBuf buf) {
        PacketCarDamageData packet = new PacketCarDamageData();
        packet.setHeader(header);

        for (int i = 0; i < 22; i++) {
            CarDamageData cd = new CarDamageData();

            float[] tyreWear = new float[4];
            for (int j = 0; j < 4; j++) tyreWear[j] = buf.readFloatLE();
            cd.setTyresWear(tyreWear);

            short[] tyreDmg = new short[4];
            for (int j = 0; j < 4; j++) tyreDmg[j] = buf.readUnsignedByte();
            cd.setTyresDamage(tyreDmg);

            short[] brakeDmg = new short[4];
            for (int j = 0; j < 4; j++) brakeDmg[j] = buf.readUnsignedByte();
            cd.setBrakesDamage(brakeDmg);

            short[] tyreBlisters = new short[4];
            for (int j = 0; j < 4; j++) tyreBlisters[j] = buf.readUnsignedByte();
            cd.setTyresBlisters(tyreBlisters);

            cd.setFrontLeftWingDamage(buf.readUnsignedByte());
            cd.setFrontRightWingDamage(buf.readUnsignedByte());
            cd.setRearWingDamage(buf.readUnsignedByte());
            cd.setFloorDamage(buf.readUnsignedByte());
            cd.setDiffuserDamage(buf.readUnsignedByte());
            cd.setSidepodDamage(buf.readUnsignedByte());
            cd.setDrsFault(buf.readUnsignedByte());
            cd.setErsFault(buf.readUnsignedByte());
            cd.setGearBoxDamage(buf.readUnsignedByte());
            cd.setEngineDamage(buf.readUnsignedByte());
            cd.setEngineMGUHWear(buf.readUnsignedByte());
            cd.setEngineESWear(buf.readUnsignedByte());
            cd.setEngineCEWear(buf.readUnsignedByte());
            cd.setEngineICEWear(buf.readUnsignedByte());
            cd.setEngineMGUKWear(buf.readUnsignedByte());
            cd.setEngineTCWear(buf.readUnsignedByte());
            cd.setEngineBlown(buf.readUnsignedByte());
            cd.setEngineSeized(buf.readUnsignedByte());

            packet.getCarDamageData()[i] = cd;
        }

        return packet;
    }
}
