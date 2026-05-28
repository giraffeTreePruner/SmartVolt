package com.codedgiraffe.smartvolt.cloud.scheduler;

import com.codedgiraffe.smartvolt.cloud.command.CommandService;
import com.codedgiraffe.smartvolt.cloud.command.CommandType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ChargingScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChargingScheduler.class);
    private final CommandService commandService;
    private final String deviceId;

    public ChargingScheduler(CommandService commandService,
                             @Value("${smartvolt.device.id:kauf-01}") String deviceId) {
        this.commandService = commandService;
        this.deviceId = deviceId;
    }

    @Scheduled(cron = "0 0 22 * * *")
    public void startCharging() {
        log.info("Creating scheduled POWER_ON command for device {}", deviceId);
        commandService.createCommand(deviceId, CommandType.POWER_ON);
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void stopCharging() {
        log.info("Creating scheduled POWER_OFF command for device {}", deviceId);
        commandService.createCommand(deviceId, CommandType.POWER_OFF);
    }
}
