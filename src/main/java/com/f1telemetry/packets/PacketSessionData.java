package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Session Packet - details about the current session.
 * Packet ID: 1
 * Size: 753 bytes
 * Frequency: 2 per second
 */
@Data
@NoArgsConstructor
public class PacketSessionData {
    private PacketHeader header;

    private short weather;                      // uint8
    private byte trackTemperature;              // int8  - celsius
    private byte airTemperature;                // int8  - celsius
    private short totalLaps;                    // uint8
    private int trackLength;                    // uint16 - metres
    private short sessionType;                  // uint8 - see appendix
    private byte trackId;                       // int8  - -1 for unknown
    private short formula;                      // uint8 - 0=F1Modern,1=F1Classic,2=F2,etc.
    private int sessionTimeLeft;                // uint16 - seconds
    private int sessionDuration;                // uint16 - seconds
    private short pitSpeedLimit;                // uint8 - km/h
    private short gamePaused;                   // uint8
    private short isSpectating;                 // uint8
    private short spectatorCarIndex;            // uint8
    private short sliProNativeSupport;          // uint8
    private short numMarshalZones;              // uint8
    private MarshalZone[] marshalZones = new MarshalZone[21];
    private short safetyCarStatus;              // uint8 - 0=no,1=full,2=virtual,3=formation
    private short networkGame;                  // uint8 - 0=offline,1=online
    private short numWeatherForecastSamples;    // uint8
    private WeatherForecastSample[] weatherForecastSamples = new WeatherForecastSample[64];
    private short forecastAccuracy;             // uint8 - 0=Perfect,1=Approximate
    private short aiDifficulty;                 // uint8 - 0-110
    private long seasonLinkIdentifier;          // uint32
    private long weekendLinkIdentifier;         // uint32
    private long sessionLinkIdentifier;         // uint32
    private short pitStopWindowIdealLap;        // uint8
    private short pitStopWindowLatestLap;       // uint8
    private short pitStopRejoinPosition;        // uint8
    private short steeringAssist;               // uint8
    private short brakingAssist;                // uint8
    private short gearboxAssist;                // uint8
    private short pitAssist;                    // uint8
    private short pitReleaseAssist;             // uint8
    private short ersAssist;                    // uint8
    private short drsAssist;                    // uint8
    private short dynamicRacingLine;            // uint8
    private short dynamicRacingLineType;        // uint8
    private short gameMode;                     // uint8
    private short ruleSet;                      // uint8
    private long timeOfDay;                     // uint32 - minutes since midnight
    private short sessionLength;                // uint8
    private short speedUnitsLeadPlayer;         // uint8 - 0=MPH,1=KPH
    private short temperatureUnitsLeadPlayer;   // uint8 - 0=Celsius,1=Fahrenheit
    private short speedUnitsSecondaryPlayer;    // uint8
    private short temperatureUnitsSecondaryPlayer; // uint8
    private short numSafetyCarPeriods;          // uint8
    private short numVirtualSafetyCarPeriods;   // uint8
    private short numRedFlagPeriods;            // uint8
    private short equalCarPerformance;          // uint8
    private short recoveryMode;                 // uint8
    private short flashbackLimit;               // uint8
    private short surfaceType;                  // uint8
    private short lowFuelMode;                  // uint8
    private short raceStarts;                   // uint8
    private short tyreTemperature;              // uint8
    private short pitLaneTyreSim;               // uint8
    private short carDamage;                    // uint8
    private short carDamageRate;                // uint8
    private short collisions;                   // uint8
    private short collisionsOffForFirstLapOnly; // uint8
    private short mpUnsafePitRelease;           // uint8
    private short mpOffForGriefing;             // uint8
    private short cornerCuttingStringency;      // uint8
    private short parcFermeRules;               // uint8
    private short pitStopExperience;            // uint8
    private short safetyCar;                    // uint8
    private short safetyCarExperience;          // uint8
    private short formationLap;                 // uint8
    private short formationLapExperience;       // uint8
    private short redFlags;                     // uint8
    private short affectsLicenceLevelSolo;      // uint8
    private short affectsLicenceLevelMP;        // uint8
    private short numSessionsInWeekend;         // uint8
    private short[] weekendStructure = new short[12]; // uint8[12]
    private float sector2LapDistanceStart;      // float
    private float sector3LapDistanceStart;      // float

    public static final int PACKET_ID = 1;
}
