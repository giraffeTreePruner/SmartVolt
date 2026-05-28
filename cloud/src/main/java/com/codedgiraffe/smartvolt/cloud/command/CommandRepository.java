package com.codedgiraffe.smartvolt.cloud.command;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CommandRepository extends JpaRepository<Command, String> {

    List<Command> findByDeviceIdAndStatus(String deviceId, CommandStatus status);

    List<Command> findByStatusAndExpiresAtBefore(CommandStatus status, Instant cutoff);
}
