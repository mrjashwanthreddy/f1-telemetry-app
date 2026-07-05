package com.f1telemetry.packets;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event Data Details - union type for different event kinds.
 * The correct fields depend on the event string code.
 */
@Data
@NoArgsConstructor
public class EventDataDetails {
    // FastestLap
    private short fastestLapVehicleIdx;
    private float fastestLapTime;

    // Retirement
    private short retirementVehicleIdx;
    private short retirementReason;

    // DRSDisabled
    private short drsDisabledReason;

    // TeamMateInPits
    private short teamMateVehicleIdx;

    // RaceWinner
    private short raceWinnerVehicleIdx;

    // Penalty
    private short penaltyType;
    private short infringementType;
    private short penaltyVehicleIdx;
    private short penaltyOtherVehicleIdx;
    private short penaltyTime;
    private short penaltyLapNum;
    private short penaltyPlacesGained;

    // SpeedTrap
    private short speedTrapVehicleIdx;
    private float speedTrapSpeed;
    private short speedTrapIsOverallFastest;
    private short speedTrapIsDriverFastest;
    private short speedTrapFastestVehicleIdx;
    private float speedTrapFastestSpeed;

    // StartLights
    private short numLights;

    // DriveThroughPenaltyServed
    private short driveThroughVehicleIdx;

    // StopGoPenaltyServed
    private short stopGoVehicleIdx;
    private float stopGoTime;

    // Flashback
    private long flashbackFrameIdentifier;
    private float flashbackSessionTime;

    // Buttons
    private long buttonStatus;

    // Overtake
    private short overtakingVehicleIdx;
    private short beingOvertakenVehicleIdx;

    // SafetyCar
    private short safetyCarType;
    private short safetyCarEventType;

    // Collision
    private short collisionVehicle1Idx;
    private short collisionVehicle2Idx;
}
