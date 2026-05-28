package com.codedgiraffe.smartvolt.cloud.command;

import com.codedgiraffe.smartvolt.shared.dto.CommandDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class CommandService {
    private static final Logger log = LoggerFactory.getLogger(CommandService.class);

    private final CommandRepository commandRepo;

    public CommandService(CommandRepository commandRepo) {
        this.commandRepo = commandRepo;
    }

    public Command createCommand(String deviceId, CommandType type) {
        Command cmd = new Command();
        cmd.setDeviceId(deviceId);
        cmd.setCommandType(type);
        cmd.setStatus(CommandStatus.PENDING);
        Command saved = commandRepo.save(cmd);
        log.info("Created {} command for device {} (id={})", type, deviceId, saved.getId());
        return saved;
    }

    public List<CommandDto> findPending(String deviceId) {
        return commandRepo.findByDeviceIdAndStatus(deviceId, CommandStatus.PENDING)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public void acknowledge(String commandId, String status) {
        commandRepo.findById(commandId).ifPresentOrElse(cmd -> {
            if ("ACKNOWLEDGED".equals(status)) {
                cmd.setStatus(CommandStatus.ACKNOWLEDGED);
                cmd.setAcknowledgedAt(Instant.now());
            } else {
                cmd.setStatus(CommandStatus.EXPIRED);
            }
            commandRepo.save(cmd);
            log.info("Command {} status updated to {}", commandId, cmd.getStatus());
        }, () -> log.warn("Acknowledge called for unknown command {}", commandId));
    }

    @Scheduled(fixedRate = 60000)
    public void expireStaleCommands() {
        List<Command> stale = commandRepo.findByStatusAndExpiresAtBefore(
                CommandStatus.PENDING, Instant.now());
        for (Command cmd : stale) {
            cmd.setStatus(CommandStatus.EXPIRED);
            commandRepo.save(cmd);
            log.info("Expired stale command {} for device {}", cmd.getId(), cmd.getDeviceId());
        }
    }

    private CommandDto toDto(Command cmd) {
        CommandDto dto = new CommandDto();
        dto.setId(cmd.getId());
        dto.setDeviceId(cmd.getDeviceId());
        dto.setCommandType(cmd.getCommandType().name());
        dto.setPayload(cmd.getPayload());
        dto.setExpiresAt(cmd.getExpiresAt());
        return dto;
    }
}
