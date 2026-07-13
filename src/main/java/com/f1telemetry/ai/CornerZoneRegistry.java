package com.f1telemetry.ai;

import java.util.List;
import java.util.Map;

/**
 * Static registry of corner zones per F1 circuit, defined by lap distance ranges (metres).
 *
 * Mapped for all 33 tracks in the F1 25 game specification.
 * Distances are calibrated to match F1 25 track telemetry lengths.
 */
public class CornerZoneRegistry {

    public record CornerZone(String name, float startMetres, float endMetres) {
        public boolean contains(float lapDistance) {
            return lapDistance >= startMetres && lapDistance <= endMetres;
        }
    }

    private static final Map<Integer, Float> TRACK_LENGTHS = Map.ofEntries(
        Map.entry(0, 5278f),   // Melbourne
        Map.entry(1, 5842f),   // Paul Ricard
        Map.entry(2, 5451f),   // Shanghai
        Map.entry(3, 5412f),   // Sakhir (Bahrain)
        Map.entry(4, 4675f),   // Catalunya
        Map.entry(5, 3337f),   // Monaco
        Map.entry(6, 4361f),   // Montreal
        Map.entry(7, 5891f),   // Silverstone
        Map.entry(8, 4574f),   // Hockenheim
        Map.entry(9, 4381f),   // Hungaroring
        Map.entry(10, 7004f),  // Spa
        Map.entry(11, 5793f),  // Monza
        Map.entry(12, 4940f),  // Singapore
        Map.entry(13, 5807f),  // Suzuka
        Map.entry(14, 5281f),  // Abu Dhabi
        Map.entry(15, 5513f),  // Texas (COTA)
        Map.entry(16, 4309f),  // Brazil
        Map.entry(17, 4318f),  // Austria
        Map.entry(18, 5848f),  // Sochi
        Map.entry(19, 4304f),  // Mexico City
        Map.entry(20, 6003f),  // Baku
        Map.entry(21, 3500f),  // Sakhir Short
        Map.entry(22, 3200f),  // Silverstone Short
        Map.entry(23, 3100f),  // Texas Short
        Map.entry(24, 2900f),  // Suzuka Short
        Map.entry(25, 5607f),  // Hanoi
        Map.entry(26, 4259f),  // Zandvoort
        Map.entry(27, 4909f),  // Imola
        Map.entry(28, 4653f),  // Portimao
        Map.entry(29, 6174f),  // Jeddah
        Map.entry(30, 5412f),  // Miami
        Map.entry(31, 6201f),  // Las Vegas
        Map.entry(32, 5419f)   // Losail
    );

    private static final Map<Integer, List<CornerZone>> ZONES = Map.ofEntries(
        // 0: Melbourne
        Map.entry(0, List.of(
            new CornerZone("T1-2 Jones", 200, 500),
            new CornerZone("T3 Brabham", 700, 950),
            new CornerZone("T4-5 Marina", 1000, 1300),
            new CornerZone("T6-7 White", 1550, 1850),
            new CornerZone("T9-10 Clark", 2300, 2600),
            new CornerZone("T11-12 Waite S-bend", 3200, 3550),
            new CornerZone("T13 Hill", 3900, 4200),
            new CornerZone("T14 Prost", 4400, 4750),
            new CornerZone("T15-16 Pit exit chicane", 4850, 5150)
        )),
        // 1: Paul Ricard
        Map.entry(1, List.of(
            new CornerZone("T1-2 S-bend", 250, 550),
            new CornerZone("T3-4 Sainte-Beaume", 800, 1150),
            new CornerZone("T6 Chicane Entry", 1750, 2050),
            new CornerZone("T8-9 Chicane", 2400, 2750),
            new CornerZone("T10 Signes", 3600, 3950),
            new CornerZone("T11 Double Droite du Beausset", 4100, 4550),
            new CornerZone("T12 Bendor", 4700, 5000),
            new CornerZone("T14-15 Lac/Pont", 5200, 5650)
        )),
        // 2: Shanghai
        Map.entry(2, List.of(
            new CornerZone("T1-4 Grand Loop", 200, 750),
            new CornerZone("T6 Hairpin", 1100, 1350),
            new CornerZone("T7-8 Sweeper", 1500, 1950),
            new CornerZone("T9-10 S-bend", 2200, 2550),
            new CornerZone("T11-13 Banking Exit", 3100, 3600),
            new CornerZone("T14 Hairpin", 4500, 4900),
            new CornerZone("T16 Final Turn", 5050, 5350)
        )),
        // 3: Sakhir (Bahrain)
        Map.entry(3, List.of(
            new CornerZone("T1 Michael Schumacher Hairpin", 250, 580),
            new CornerZone("T4 Right hander", 950, 1250),
            new CornerZone("T5-7 Chicane", 1450, 1750),
            new CornerZone("T8 Hairpin", 1950, 2200),
            new CornerZone("T9-10 Off-camber Left", 2500, 2850),
            new CornerZone("T11 Sweeper", 3300, 3600),
            new CornerZone("T12-13 Double Right", 3900, 4350),
            new CornerZone("T14-15 Entry Straight", 4800, 5200)
        )),
        // 4: Catalunya
        Map.entry(4, List.of(
            new CornerZone("T1-2 Elf", 300, 620),
            new CornerZone("T3 Renault", 750, 1050),
            new CornerZone("T4 Repsol", 1200, 1450),
            new CornerZone("T5 Seat", 1600, 1850),
            new CornerZone("T7-8 Wurz", 2150, 2450),
            new CornerZone("T9 Campsa", 2650, 2900),
            new CornerZone("T10 La Caixa", 3300, 3600),
            new CornerZone("T12 Banc de Sabadell", 3800, 4100),
            new CornerZone("T13-14 New Layout Exit", 4250, 4550)
        )),
        // 5: Monaco
        Map.entry(5, List.of(
            new CornerZone("T1 Sainte-Devote", 100, 310),
            new CornerZone("T2 Massenet", 310, 560),
            new CornerZone("T3 Casino", 560, 780),
            new CornerZone("T4 Mirabeau Haute", 780, 990),
            new CornerZone("T5 Mirabeau Bas", 990, 1180),
            new CornerZone("T6 Grand Hotel", 1180, 1370),
            new CornerZone("T7 Loews Hairpin", 1370, 1560),
            new CornerZone("T8 Portier", 1560, 1750),
            new CornerZone("T9 Tunnel Exit", 1750, 1940),
            new CornerZone("T10 Nouvelle", 1940, 2130),
            new CornerZone("T11 Tabac", 2130, 2300),
            new CornerZone("T12 Swimming Pool 1", 2300, 2480),
            new CornerZone("T13 Swimming Pool 2", 2480, 2650),
            new CornerZone("T14 La Rascasse", 2650, 2820),
            new CornerZone("T15 Anthony Noghes", 2820, 3050),
            new CornerZone("T16 Start-Finish", 3050, 3337)
        )),
        // 6: Montreal
        Map.entry(6, List.of(
            new CornerZone("T1-2 Senna S-bend", 150, 450),
            new CornerZone("T3-4 Chicane", 750, 1050),
            new CornerZone("T5-6 Chicane", 1200, 1500),
            new CornerZone("T8-9 Bridges Chicane", 1850, 2150),
            new CornerZone("T10 L'Epingle Hairpin", 2600, 2950),
            new CornerZone("T13-14 Wall of Champions", 3800, 4200)
        )),
        // 7: Silverstone
        Map.entry(7, List.of(
            new CornerZone("T1 Abbey", 150, 380),
            new CornerZone("T2 Farm", 380, 620),
            new CornerZone("T3 Village", 620, 880),
            new CornerZone("T4-5 Loop", 880, 1180),
            new CornerZone("T6 Aintree", 1180, 1420),
            new CornerZone("T7 Wellington", 1420, 1700),
            new CornerZone("T8 Brooklands", 1700, 1950),
            new CornerZone("T9 Luffield", 1950, 2220),
            new CornerZone("T10 Woodcote", 2220, 2500),
            new CornerZone("T11 Copse", 2500, 2730),
            new CornerZone("T12 Maggotts", 2730, 2960),
            new CornerZone("T13 Becketts", 2960, 3220),
            new CornerZone("T14 Chapel", 3220, 3470),
            new CornerZone("T15 Hangar Straight", 3470, 3750),
            new CornerZone("T16 Stowe", 3750, 4000),
            new CornerZone("T17 Vale", 4000, 4280),
            new CornerZone("T18 Club", 4280, 4570),
            new CornerZone("T19 Hamilton Straight", 4570, 4850)
        )),
        // 8: Hockenheim
        Map.entry(8, List.of(
            new CornerZone("T1 Nordkurve", 150, 420),
            new CornerZone("T2 Hairpin", 1200, 1550),
            new CornerZone("T6 Spitzkehre", 1950, 2250),
            new CornerZone("T8-11 Stadium Section", 2900, 3500),
            new CornerZone("T12-16 Sachskurve / Opel", 3800, 4350)
        )),
        // 9: Hungaroring
        Map.entry(9, List.of(
            new CornerZone("T1 Hairpin", 250, 550),
            new CornerZone("T2-3 Downhill Left", 750, 1050),
            new CornerZone("T4 Blind Sweeper", 1250, 1500),
            new CornerZone("T5 Long Right", 1600, 1900),
            new CornerZone("T6-7 Chicane", 2100, 2400),
            new CornerZone("T8-11 S-Bends", 2600, 3100),
            new CornerZone("T12 Right Hander", 3400, 3700),
            new CornerZone("T13-14 Final Corners", 3850, 4250)
        )),
        // 10: Spa
        Map.entry(10, List.of(
            new CornerZone("T1 La Source", 150, 400),
            new CornerZone("T2-4 Eau Rouge / Radillon", 750, 1150),
            new CornerZone("T5-7 Les Combes", 1950, 2300),
            new CornerZone("T8 Bruxelles", 2500, 2800),
            new CornerZone("T9 Speaker's Corner", 2900, 3200),
            new CornerZone("T10-11 Pouhon", 3550, 4000),
            new CornerZone("T12-13 Fagnes", 4350, 4650),
            new CornerZone("T14-15 Stavelot", 4850, 5250),
            new CornerZone("T18-19 Bus Stop Chicane", 6400, 6850)
        )),
        // 11: Monza
        Map.entry(11, List.of(
            new CornerZone("T1-2 Variante del Rettifilo", 350, 750),
            new CornerZone("T3 Curva Grande", 950, 1350),
            new CornerZone("T4-5 Variante della Roggia", 1650, 2050),
            new CornerZone("T6-7 Curva di Lesmo", 2300, 2750),
            new CornerZone("T8-10 Curva del Serraglio / Ascari", 3700, 4350),
            new CornerZone("T11 Curva Parabolica", 5000, 5550)
        )),
        // 12: Singapore
        Map.entry(12, List.of(
            new CornerZone("T1-3 Sheares", 150, 550),
            new CornerZone("T5 Right hander", 800, 1050),
            new CornerZone("T7-9 Memorial Chicane", 1400, 1850),
            new CornerZone("T10-12 Singapore Sling", 2200, 2600),
            new CornerZone("T13 Esplanade Hairpin", 2800, 3100),
            new CornerZone("T14-17 Bayfront", 3300, 3850),
            new CornerZone("T18-19 Grandstand Underpass", 4000, 4350),
            new CornerZone("T20-21 Final Turn", 4500, 4800)
        )),
        // 13: Suzuka
        Map.entry(13, List.of(
            new CornerZone("T1-2 S-Curves entry", 200, 580),
            new CornerZone("T3-6 S-Curves", 700, 1350),
            new CornerZone("T7 Dunlop Curve", 1450, 1750),
            new CornerZone("T8-9 Degner Corners", 1950, 2250),
            new CornerZone("T11 Hairpin", 2600, 2900),
            new CornerZone("T12 200R Sweeper", 3100, 3400),
            new CornerZone("T13-14 Spoon Curve", 3650, 4200),
            new CornerZone("T15 130R", 4800, 5200),
            new CornerZone("T16-18 Casio Triangle Chicane", 5300, 5650)
        )),
        // 14: Abu Dhabi
        Map.entry(14, List.of(
            new CornerZone("T1-3 Chicane", 150, 500),
            new CornerZone("T5 Hairpin", 900, 1250),
            new CornerZone("T6-7 Chicane Exit", 1400, 1700),
            new CornerZone("T9 Curved Banking", 2800, 3150),
            new CornerZone("T12-14 Marina Bay Yacht Club", 3600, 4150),
            new CornerZone("T15-16 W-Hotel Sweeper", 4350, 4850)
        )),
        // 15: Texas (COTA)
        Map.entry(15, List.of(
            new CornerZone("T1 Big Hill Hairpin", 150, 450),
            new CornerZone("T2-6 Ess-Curves", 650, 1550),
            new CornerZone("T11 Hairpin", 2400, 2750),
            new CornerZone("T12-15 Stadium Section", 3650, 4300),
            new CornerZone("T16-18 Quadruple Apex", 4450, 4950),
            new CornerZone("T19-20 Exit Turns", 5050, 5400)
        )),
        // 16: Brazil
        Map.entry(16, List.of(
            new CornerZone("T1-2 Senna S", 150, 450),
            new CornerZone("T4 Descida do Lago", 950, 1250),
            new CornerZone("T6-7 Ferradura", 1550, 1950),
            new CornerZone("T8 Laranjinha", 2050, 2300),
            new CornerZone("T9 Pinheirinho", 2400, 2650),
            new CornerZone("T10 Bico de Pato", 2750, 3050),
            new CornerZone("T12 Mergulho", 3200, 3500),
            new CornerZone("T13 Juncao", 3600, 3950)
        )),
        // 17: Austria
        Map.entry(17, List.of(
            new CornerZone("T1 Castrol", 190, 420),
            new CornerZone("T2 Remus", 420, 680),
            new CornerZone("T3 Schlossgold", 1350, 1580),
            new CornerZone("T4 Rindt", 1580, 1820),
            new CornerZone("T5 Rauch", 2100, 2380),
            new CornerZone("T6 Flow", 2380, 2620),
            new CornerZone("T7 Power Horse", 2900, 3180),
            new CornerZone("T8 Jochen Rindt", 3180, 3480),
            new CornerZone("T9 Methanol", 3650, 3900),
            new CornerZone("T10 Final Hairpin", 3900, 4150)
        )),
        // 18: Sochi
        Map.entry(18, List.of(
            new CornerZone("T2-3 Long Radial Left", 450, 950),
            new CornerZone("T4-5 Chicane", 1200, 1550),
            new CornerZone("T7-8 Right handers", 1850, 2200),
            new CornerZone("T10 Hairpin", 2600, 2900),
            new CornerZone("T13-14 Right hander", 3800, 4200),
            new CornerZone("T17-18 Hotel Corners", 4900, 5500)
        )),
        // 19: Mexico City
        Map.entry(19, List.of(
            new CornerZone("T1-3 Moises Solana S-bend", 300, 650),
            new CornerZone("T4-5 Chicane", 1100, 1450),
            new CornerZone("T6 Sweeper", 1600, 1850),
            new CornerZone("T7-11 S-Curves", 2100, 2800),
            new CornerZone("T12-16 Foro Sol Baseball Stadium", 3500, 4100)
        )),
        // 20: Baku
        Map.entry(20, List.of(
            new CornerZone("T1 Left Hander", 250, 500),
            new CornerZone("T2 Right exit", 600, 850),
            new CornerZone("T3 Long Left", 1000, 1300),
            new CornerZone("T5-6 Left-Right", 1650, 1950),
            new CornerZone("T8-11 Castle Section", 2200, 2600),
            new CornerZone("T12-15 Downhill S-Bends", 2950, 3450),
            new CornerZone("T16 Hairpin", 3750, 4050)
        )),
        // 21: Sakhir Short
        Map.entry(21, List.of(
            new CornerZone("T1 Michael Schumacher Hairpin", 250, 580),
            new CornerZone("T4 Right hander", 950, 1250),
            new CornerZone("T5-6 Cut-through Link", 1300, 1700),
            new CornerZone("T7-8 Final Sweepers", 2500, 3200)
        )),
        // 22: Silverstone Short
        Map.entry(22, List.of(
            new CornerZone("T1 Abbey", 150, 380),
            new CornerZone("T2 Farm", 380, 620),
            new CornerZone("T3 Short-link Chicane", 700, 1100),
            new CornerZone("T5 Stowe", 1600, 1950),
            new CornerZone("T6 Vale / Club", 2100, 2700)
        )),
        // 23: Texas Short
        Map.entry(23, List.of(
            new CornerZone("T1 Big Hill Hairpin", 150, 450),
            new CornerZone("T2 Short-link Cut", 700, 1100),
            new CornerZone("T4 Quadruple Sweeper", 1500, 2100),
            new CornerZone("T5 Exit Hairpin", 2300, 2800)
        )),
        // 24: Suzuka Short
        Map.entry(24, List.of(
            new CornerZone("T1-2 S-Curves entry", 200, 580),
            new CornerZone("T3 Short-link loop", 700, 1100),
            new CornerZone("T5 Casio Triangle Chicane", 1900, 2400)
        )),
        // 25: Hanoi
        Map.entry(25, List.of(
            new CornerZone("T1-2 Giant Loop", 200, 650),
            new CornerZone("T3-5 S-bend", 850, 1200),
            new CornerZone("T6-9 Chicane", 1750, 2200),
            new CornerZone("T11 Hairpin", 3600, 3950),
            new CornerZone("T12-22 Ess-Curves Stadium", 4300, 5300)
        )),
        // 26: Zandvoort
        Map.entry(26, List.of(
            new CornerZone("T1 Tarzan Hairpin", 180, 450),
            new CornerZone("T2 Gerlachbocht", 450, 680),
            new CornerZone("T3 Hugenholtz Banked", 680, 950),
            new CornerZone("T7 Scheivlak Gravel", 1450, 1750),
            new CornerZone("T8-10 S-bend", 1900, 2300),
            new CornerZone("T11-12 Hans Ernst Chicane", 2950, 3350),
            new CornerZone("T13-14 Arie Luyendyk Banking", 3700, 4100)
        )),
        // 27: Imola
        Map.entry(27, List.of(
            new CornerZone("T2-4 Tamburello Chicane", 350, 680),
            new CornerZone("T7-8 Villeneuve Chicane", 1100, 1400),
            new CornerZone("T9 Tosa Hairpin", 1450, 1750),
            new CornerZone("T11-13 Acque Minerali", 2200, 2600),
            new CornerZone("T14-15 Variante Alta", 3100, 3450),
            new CornerZone("T17-18 Rivazza", 3900, 4350)
        )),
        // 28: Portimao
        Map.entry(28, List.of(
            new CornerZone("T1-2 Right Hander", 200, 550),
            new CornerZone("T3 Hairpin", 700, 1000),
            new CornerZone("T5 Hairpin Downhill", 1300, 1600),
            new CornerZone("T7-8 Right handers", 2000, 2400),
            new CornerZone("T10-12 Blind Crest Left", 2800, 3300),
            new CornerZone("T13-14 Galp Sweeper", 3700, 4150),
            new CornerZone("T15 Final Banking", 4250, 4550)
        )),
        // 29: Jeddah
        Map.entry(29, List.of(
            new CornerZone("T1-2 Left-Right S-bend", 250, 550),
            new CornerZone("T4-10 High-speed S-Curves", 750, 1750),
            new CornerZone("T13 Banked Hairpin", 2200, 2550),
            new CornerZone("T16-17 Chicane", 2850, 3200),
            new CornerZone("T22-24 High-speed S-Curves", 3800, 4600),
            new CornerZone("T27 Final Hairpin", 5400, 5850)
        )),
        // 30: Miami
        Map.entry(30, List.of(
            new CornerZone("T1 Right Hander", 250, 520),
            new CornerZone("T2-5 Marina S-bend", 650, 1150),
            new CornerZone("T6-8 Sweepers", 1300, 1750),
            new CornerZone("T11-13 Yacht Club Hairpin", 2200, 2600),
            new CornerZone("T14-16 Underpass Chicane", 2850, 3150),
            new CornerZone("T17 Hairpin", 4300, 4650),
            new CornerZone("T19 Final Left", 4950, 5250)
        )),
        // 31: Las Vegas
        Map.entry(31, List.of(
            new CornerZone("T1-2 Left Hander", 200, 550),
            new CornerZone("T3-4 Right-Left", 650, 950),
            new CornerZone("T5-8 Sphere S-bend", 1200, 1750),
            new CornerZone("T9 Left turn", 2200, 2500),
            new CornerZone("T12 Chicane Entry", 3300, 3650),
            new CornerZone("T14 Koval Left Hander", 5200, 5550),
            new CornerZone("T16-17 Final turns", 5800, 6150)
        )),
        // 32: Losail
        Map.entry(32, List.of(
            new CornerZone("T1 Right Hander", 250, 550),
            new CornerZone("T2-3 Left handers", 700, 1050),
            new CornerZone("T4-5 Right-Left", 1200, 1550),
            new CornerZone("T6 Long Sweeper", 1750, 2100),
            new CornerZone("T7-9 Triple Right", 2400, 2900),
            new CornerZone("T12-14 High-speed S-bend", 3600, 4350),
            new CornerZone("T15-16 Final turn", 4700, 5150)
        ))
    );

    public static CornerZone getZone(int trackId, float lapDistance) {
        List<CornerZone> zones = ZONES.get(trackId);
        if (zones == null) return null;
        return zones.stream()
            .filter(z -> z.contains(lapDistance))
            .findFirst()
            .orElse(null);
    }

    public static List<CornerZone> getZones(int trackId) {
        return ZONES.getOrDefault(trackId, List.of());
    }

    public static boolean hasZoneData(int trackId) {
        return ZONES.containsKey(trackId);
    }

    public static float getTrackLength(int trackId) {
        return TRACK_LENGTHS.getOrDefault(trackId, -1f);
    }
}
