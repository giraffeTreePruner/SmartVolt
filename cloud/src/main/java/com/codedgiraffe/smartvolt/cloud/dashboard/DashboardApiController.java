package com.codedgiraffe.smartvolt.cloud.dashboard;

import com.codedgiraffe.smartvolt.cloud.model.Device;
import com.codedgiraffe.smartvolt.cloud.model.TelemetryReading;
import com.codedgiraffe.smartvolt.cloud.repository.TelemetryRepository;
import com.codedgiraffe.smartvolt.cloud.service.DeviceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/dashboard/api")
public class DashboardApiController {

    private final DeviceService deviceService;
    private final TelemetryRepository telemetryRepo;

    public DashboardApiController(DeviceService deviceService,
                                  TelemetryRepository telemetryRepo) {
        this.deviceService = deviceService;
        this.telemetryRepo = telemetryRepo;
    }

    @GetMapping("/live")
    public String liveReadings(Model model) {
        List<Device> devices = deviceService.findAll();
        Map<String, TelemetryReading> latestReadings = new LinkedHashMap<>();
        for (Device device : devices) {
            telemetryRepo.findTopByDeviceIdOrderByTimestampDesc(device.getDeviceId())
                    .ifPresent(r -> latestReadings.put(device.getDeviceId(), r));
        }
        model.addAttribute("devices", devices);
        model.addAttribute("latestReadings", latestReadings);
        return "fragments/live-readings";
    }

    @GetMapping("/history/data")
    @ResponseBody
    public List<HistoryPoint> historyData(@RequestParam String deviceId,
                                          @RequestParam Instant from,
                                          @RequestParam Instant to) {
        return telemetryRepo.findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(deviceId, from, to)
                .stream()
                .map(r -> new HistoryPoint(
                        r.getTimestamp().toString(),
                        r.getWattage(),
                        r.getVoltage(),
                        r.getAmperage()))
                .toList();
    }

    public record HistoryPoint(String timestamp, Double wattage, Double voltage, Double amperage) {}
}
