package com.codedgiraffe.smartvolt.cloud;

import com.codedgiraffe.smartvolt.cloud.model.Device;
import com.codedgiraffe.smartvolt.cloud.model.TelemetryReading;
import com.codedgiraffe.smartvolt.cloud.repository.DeviceRepository;
import com.codedgiraffe.smartvolt.cloud.repository.TelemetryRepository;
import com.codedgiraffe.smartvolt.cloud.session.ChargingSession;
import com.codedgiraffe.smartvolt.cloud.session.ChargingSessionRepository;
import com.codedgiraffe.smartvolt.cloud.session.SessionStatus;
import com.codedgiraffe.smartvolt.shared.util.KeyHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

/**
 * Seeds the H2 database with realistic EV charging telemetry for dashboard preview.
 * Only active with the "dev" profile.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final DeviceRepository deviceRepo;
    private final TelemetryRepository telemetryRepo;
    private final ChargingSessionRepository sessionRepo;
    private final Random random = new Random(42);

    public DevDataSeeder(DeviceRepository deviceRepo,
                         TelemetryRepository telemetryRepo,
                         ChargingSessionRepository sessionRepo) {
        this.deviceRepo = deviceRepo;
        this.telemetryRepo = telemetryRepo;
        this.sessionRepo = sessionRepo;
    }

    @Override
    public void run(String... args) {
        log.info("Seeding dev data...");

        Device device = new Device();
        device.setDeviceId("kauf-01");
        device.setDeviceKeyHash(KeyHashUtil.hash("dev-device-key"));
        device.setName("Garage Outlet");
        device.setLocation("Garage");
        device.setOnline(true);
        deviceRepo.save(device);

        // Generate 24 hours of telemetry at 10-second intervals
        Instant now = Instant.now();
        Instant start = now.minus(24, ChronoUnit.HOURS);
        double totalKwh = 100.0;
        int count = 0;

        // Simulate: idle overnight, charging session 2am-6am, idle, short charge 2pm-3pm, idle to now
        for (Instant t = start; t.isBefore(now); t = t.plusSeconds(10)) {
            int hourOfDay = (int) ((t.getEpochSecond() / 3600) % 24);
            double wattage;
            double voltage = 120.0 + jitter(2.0);
            double amperage;

            if (hourOfDay >= 2 && hourOfDay < 6) {
                // Charging session 1: ~1400W Level 1 charging
                wattage = 1380.0 + jitter(40.0);
                amperage = wattage / voltage;
                totalKwh += (wattage / 1000.0) * (10.0 / 3600.0);
            } else if (hourOfDay >= 14 && hourOfDay < 15) {
                // Charging session 2: shorter top-up ~1200W
                wattage = 1200.0 + jitter(30.0);
                amperage = wattage / voltage;
                totalKwh += (wattage / 1000.0) * (10.0 / 3600.0);
            } else {
                // Idle: ~2-5W standby
                wattage = 3.0 + jitter(2.0);
                if (wattage < 0) wattage = 0;
                amperage = wattage / voltage;
            }

            TelemetryReading reading = new TelemetryReading();
            reading.setDeviceId("kauf-01");
            reading.setTimestamp(t);
            reading.setWattage(Math.round(wattage * 10.0) / 10.0);
            reading.setVoltage(Math.round(voltage * 10.0) / 10.0);
            reading.setAmperage(Math.round(amperage * 100.0) / 100.0);
            reading.setTotalKwh(Math.round(totalKwh * 1000.0) / 1000.0);
            telemetryRepo.save(reading);
            count++;
        }
        log.info("Seeded {} telemetry readings over 24 hours", count);

        // Charging session 1: 2am-6am today
        Instant session1Start = now.minus(22, ChronoUnit.HOURS);
        Instant session1End = now.minus(18, ChronoUnit.HOURS);
        ChargingSession s1 = new ChargingSession();
        s1.setDeviceId("kauf-01");
        s1.setStartedAt(session1Start);
        s1.setEndedAt(session1End);
        s1.setStartKwh(100.0);
        s1.setEndKwh(105.52);
        s1.setEnergyUsedKwh(5.52);
        s1.setEstimatedCost(0.66);
        s1.setStatus(SessionStatus.COMPLETED);
        sessionRepo.save(s1);

        // Charging session 2: 2pm-3pm today
        Instant session2Start = now.minus(10, ChronoUnit.HOURS);
        Instant session2End = now.minus(9, ChronoUnit.HOURS);
        ChargingSession s2 = new ChargingSession();
        s2.setDeviceId("kauf-01");
        s2.setStartedAt(session2Start);
        s2.setEndedAt(session2End);
        s2.setStartKwh(105.52);
        s2.setEndKwh(106.72);
        s2.setEnergyUsedKwh(1.20);
        s2.setEstimatedCost(0.18);
        s2.setStatus(SessionStatus.COMPLETED);
        sessionRepo.save(s2);

        // A historical session from 3 days ago
        Instant session3Start = now.minus(3, ChronoUnit.DAYS).minus(5, ChronoUnit.HOURS);
        Instant session3End = now.minus(3, ChronoUnit.DAYS);
        ChargingSession s3 = new ChargingSession();
        s3.setDeviceId("kauf-01");
        s3.setStartedAt(session3Start);
        s3.setEndedAt(session3End);
        s3.setStartKwh(93.0);
        s3.setEndKwh(100.0);
        s3.setEnergyUsedKwh(7.0);
        s3.setEstimatedCost(0.84);
        s3.setStatus(SessionStatus.COMPLETED);
        sessionRepo.save(s3);

        log.info("Seeded 3 charging sessions");
        log.info("Dev data ready. Login with admin/admin at http://localhost:8080/dashboard");
    }

    private double jitter(double range) {
        return (random.nextDouble() - 0.5) * 2 * range;
    }
}
