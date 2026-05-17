package com.codedgiraffe.smartvolt.scheduler;

import com.codedgiraffe.smartvolt.mqtt.CommandPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ChargingScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(ChargingScheduler.class);
    private final CommandPublisher commandPublisher;

    public ChargingScheduler(CommandPublisher cmdPub) {
        this.commandPublisher = cmdPub;
    }

    @Scheduled(cron = "0 0 22 * * *") // Every day at 10pm. Default schedule, can be made configurable later.
    public void startCharging() {
        log.info("Starting scheduled charging session");
        commandPublisher.setPower(true);
    }

    @Scheduled(cron = "0 0 8 * * *") // Every day at 8am. Default schedule, can be made configurable later.
    public void stopCharging() {
        log.info("Stopping scheduled charging session");
        commandPublisher.setPower(false);
    }
}
