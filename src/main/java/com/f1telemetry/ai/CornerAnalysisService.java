package com.f1telemetry.ai;

import com.f1telemetry.domain.TelemetryRecord;
import com.f1telemetry.repository.TelemetryRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 10 Enhancement: Corner-by-Corner Performance Analyzer.
 * Reads telemetry records for a lap and summarizes driver behavior
 * (minimum speed, exit speed, maximum G-Force, steering input) in named corners.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CornerAnalysisService {

    private final TelemetryRecordRepository telemetryRepository;

    /**
     * Represents the calculated driver performance profile inside a specific corner zone.
     */
    @lombok.Data
    public static class CornerPerformance {
        private String cornerName;
        private float startMetres;
        private float endMetres;
        private int minSpeed = Integer.MAX_VALUE;
        private int maxSpeed = 0;
        private float maxGForce = 0.0f;
        private float avgSteer = 0.0f;
        private int entrySpeed = 0;
        private int exitSpeed = 0;
        private int sampleCount = 0;
    }

    /**
     * Analyzes all telemetry records for a single lap and groups them by named corner.
     *
     * @param sessionId database session UID
     * @param lapNum    lap number to analyze
     * @param trackName track name (e.g. "Austria", "Silverstone", "Monaco")
     * @return map of Corner Name -> CornerPerformance
     */
    public Map<String, CornerPerformance> analyzeLap(String sessionId, int lapNum, String trackName) {
        int trackId = getTrackIdByName(trackName);
        if (trackId == -1 || !CornerZoneRegistry.hasZoneData(trackId)) {
            return Collections.emptyMap();
        }

        List<TelemetryRecord> records = telemetryRepository
            .findBySessionIdAndCurrentLapNumOrderByTimestampAsc(sessionId, lapNum);

        if (records.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<TelemetryRecord>> groupedRecords = new HashMap<>();
        for (TelemetryRecord record : records) {
            CornerZoneRegistry.CornerZone zone = CornerZoneRegistry.getZone(trackId, record.getLapDistance());
            if (zone != null) {
                groupedRecords.computeIfAbsent(zone.name(), k -> new ArrayList<>()).add(record);
            }
        }

        Map<String, CornerPerformance> performanceMap = new LinkedHashMap<>();

        // Sort by the corner's track sequence order
        List<CornerZoneRegistry.CornerZone> allTrackZones = CornerZoneRegistry.getZones(trackId);
        for (CornerZoneRegistry.CornerZone zone : allTrackZones) {
            List<TelemetryRecord> zoneRecords = groupedRecords.get(zone.name());
            if (zoneRecords == null || zoneRecords.isEmpty()) continue;

            CornerPerformance perf = new CornerPerformance();
            perf.setCornerName(zone.name());
            perf.setStartMetres(zone.startMetres());
            perf.setEndMetres(zone.endMetres());
            perf.setSampleCount(zoneRecords.size());

            float steerSum = 0.0f;
            for (int i = 0; i < zoneRecords.size(); i++) {
                TelemetryRecord r = zoneRecords.get(i);
                int speed = r.getSpeed();
                float gForce = Math.abs(r.getGForceLateral());
                steerSum += Math.abs(r.getSteer());

                if (speed < perf.getMinSpeed()) perf.setMinSpeed(speed);
                if (speed > perf.getMaxSpeed()) perf.setMaxSpeed(speed);
                if (gForce > perf.getMaxGForce()) perf.setMaxGForce(gForce);

                if (i == 0) {
                    perf.setEntrySpeed(speed);
                }
                if (i == zoneRecords.size() - 1) {
                    perf.setExitSpeed(speed);
                }
            }
            perf.setAvgSteer(steerSum / zoneRecords.size());
            performanceMap.put(zone.name(), perf);
        }

        return performanceMap;
    }

    /**
     * Builds a detailed comparison string of two laps, corner by corner,
     * to be injected as grounded data context into AI prompts.
     */
    public String buildComparisonSummary(String sessionId, int bestLapNum, int compareLapNum, String trackName) {
        Map<String, CornerPerformance> bestLap = analyzeLap(sessionId, bestLapNum, trackName);
        Map<String, CornerPerformance> compLap = analyzeLap(sessionId, compareLapNum, trackName);

        if (bestLap.isEmpty() || compLap.isEmpty()) {
            return "No granular corner telemetry comparison data available (zones only mapped for Austria, Silverstone, Monaco).";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Corner-by-corner analysis (Lap ").append(compareLapNum).append(" vs Best Lap ").append(bestLapNum).append("):\n");

        for (String cornerName : bestLap.keySet()) {
            CornerPerformance bestPerf = bestLap.get(cornerName);
            CornerPerformance compPerf = compLap.get(cornerName);

            if (compPerf != null) {
                int minSpeedDelta = compPerf.getMinSpeed() - bestPerf.getMinSpeed();
                float gForceDelta = compPerf.getMaxGForce() - bestPerf.getMaxGForce();

                sb.append(String.format("  - %s: Min Speed %d km/h vs %d km/h (%s%d km/h) | Max G-Force %.2fG vs %.2fG (%s%.2fG)\n",
                    cornerName,
                    compPerf.getMinSpeed(), bestPerf.getMinSpeed(),
                    minSpeedDelta >= 0 ? "+" : "", minSpeedDelta,
                    compPerf.getMaxGForce(), bestPerf.getMaxGForce(),
                    gForceDelta >= 0 ? "+" : "", gForceDelta
                ));
            }
        }
        return sb.toString();
    }

    public int getTrackIdByName(String trackName) {
        if (trackName == null) return -1;
        String name = trackName.toLowerCase();
        if (name.contains("melbourne") || name.contains("australia")) return 0;
        if (name.contains("paul ricard") || name.contains("france")) return 1;
        if (name.contains("shanghai") || name.contains("china")) return 2;
        if (name.contains("bahrain") || name.contains("sakhir")) {
            if (name.contains("short")) return 21;
            return 3;
        }
        if (name.contains("catalunya") || name.contains("barcelona") || name.contains("spain")) return 4;
        if (name.contains("monaco") || name.contains("monte carlo")) return 5;
        if (name.contains("montreal") || name.contains("canada") || name.contains("gilles")) return 6;
        if (name.contains("silverstone") || name.contains("britain") || name.contains("uk")) {
            if (name.contains("short")) return 22;
            return 7;
        }
        if (name.contains("hockenheim") || name.contains("germany")) return 8;
        if (name.contains("hungaroring") || name.contains("hungary")) return 9;
        if (name.contains("spa") || name.contains("belgium")) return 10;
        if (name.contains("monza") || name.contains("italy")) return 11;
        if (name.contains("singapore") || name.contains("marina bay")) return 12;
        if (name.contains("suzuka") || name.contains("japan")) {
            if (name.contains("short")) return 24;
            return 13;
        }
        if (name.contains("abu dhabi") || name.contains("yas marina")) return 14;
        if (name.contains("texas") || name.contains("cota") || name.contains("americas") || name.contains("austin")) {
            if (name.contains("short")) return 23;
            return 15;
        }
        if (name.contains("brazil") || name.contains("interlagos")) return 16;
        if (name.contains("austria") || name.contains("red bull ring")) return 17;
        if (name.contains("sochi") || name.contains("russia")) return 18;
        if (name.contains("mexico") || name.contains("hermanos rodriguez")) return 19;
        if (name.contains("baku") || name.contains("azerbaijan")) return 20;
        if (name.contains("hanoi") || name.contains("vietnam")) return 25;
        if (name.contains("zandvoort") || name.contains("netherlands") || name.contains("dutch")) return 26;
        if (name.contains("imola") || name.contains("emilia")) return 27;
        if (name.contains("portimao") || name.contains("portugal")) return 28;
        if (name.contains("jeddah") || name.contains("saudi")) return 29;
        if (name.contains("miami")) return 30;
        if (name.contains("las vegas") || name.contains("vegas")) return 31;
        if (name.contains("losail") || name.contains("qatar")) return 32;
        return -1;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class LapCornerAnalysisResponse {
        private String trackName;
        private float trackLength;
        private int trackId;
        private Map<String, CornerPerformance> corners;
    }
}
