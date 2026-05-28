package com.codedgiraffe.smartvolt.cloud.dashboard;

import com.codedgiraffe.smartvolt.cloud.command.CommandService;
import com.codedgiraffe.smartvolt.cloud.command.CommandType;
import com.codedgiraffe.smartvolt.cloud.model.Device;
import com.codedgiraffe.smartvolt.cloud.model.TelemetryReading;
import com.codedgiraffe.smartvolt.cloud.repository.TelemetryRepository;
import com.codedgiraffe.smartvolt.cloud.service.DeviceService;
import com.codedgiraffe.smartvolt.cloud.session.ChargingSession;
import com.codedgiraffe.smartvolt.cloud.session.ChargingSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    private final DeviceService deviceService;
    private final TelemetryRepository telemetryRepo;
    private final CommandService commandService;
    private final ChargingSessionRepository sessionRepo;

    public DashboardController(DeviceService deviceService,
                               TelemetryRepository telemetryRepo,
                               CommandService commandService,
                               ChargingSessionRepository sessionRepo) {
        this.deviceService = deviceService;
        this.telemetryRepo = telemetryRepo;
        this.commandService = commandService;
        this.sessionRepo = sessionRepo;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Device> devices = deviceService.findAll();
        Map<String, TelemetryReading> latestReadings = new LinkedHashMap<>();
        for (Device device : devices) {
            telemetryRepo.findTopByDeviceIdOrderByTimestampDesc(device.getDeviceId())
                    .ifPresent(r -> latestReadings.put(device.getDeviceId(), r));
        }
        model.addAttribute("devices", devices);
        model.addAttribute("latestReadings", latestReadings);
        return "dashboard";
    }

    @GetMapping("/dashboard/history")
    public String history(Model model) {
        List<Device> devices = deviceService.findAll();
        model.addAttribute("devices", devices);
        return "history";
    }

    @GetMapping("/dashboard/sessions")
    public String sessions(Model model) {
        List<ChargingSession> sessions = sessionRepo.findAll(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "startedAt"));
        model.addAttribute("sessions", sessions);
        return "sessions";
    }

    @PostMapping("/dashboard/devices/{deviceId}/power")
    public String togglePower(@PathVariable String deviceId, @RequestParam boolean on) {
        deviceService.findByDeviceId(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        commandService.createCommand(deviceId, on ? CommandType.POWER_ON : CommandType.POWER_OFF);
        return "redirect:/dashboard";
    }
}
