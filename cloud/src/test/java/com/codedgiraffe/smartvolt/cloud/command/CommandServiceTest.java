package com.codedgiraffe.smartvolt.cloud.command;

import com.codedgiraffe.smartvolt.shared.dto.CommandDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandServiceTest {

    @Mock
    private CommandRepository commandRepo;

    @InjectMocks
    private CommandService commandService;

    @Test
    void createCommand_savesPendingCommand() {
        when(commandRepo.save(any())).thenAnswer(i -> {
            Command cmd = i.getArgument(0);
            cmd.setId("generated-id");
            return cmd;
        });

        Command result = commandService.createCommand("kauf-01", CommandType.POWER_ON);

        ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
        verify(commandRepo).save(captor.capture());

        Command saved = captor.getValue();
        assertThat(saved.getDeviceId()).isEqualTo("kauf-01");
        assertThat(saved.getCommandType()).isEqualTo(CommandType.POWER_ON);
        assertThat(saved.getStatus()).isEqualTo(CommandStatus.PENDING);
    }

    @Test
    void findPending_returnsDtos() {
        Command cmd = new Command();
        cmd.setId("cmd-1");
        cmd.setDeviceId("kauf-01");
        cmd.setCommandType(CommandType.POWER_OFF);
        cmd.setStatus(CommandStatus.PENDING);
        cmd.setExpiresAt(Instant.now().plusSeconds(300));

        when(commandRepo.findByDeviceIdAndStatus("kauf-01", CommandStatus.PENDING))
                .thenReturn(List.of(cmd));

        List<CommandDto> result = commandService.findPending("kauf-01");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("cmd-1");
        assertThat(result.get(0).getCommandType()).isEqualTo("POWER_OFF");
    }

    @Test
    void acknowledge_setsAcknowledgedStatus() {
        Command cmd = new Command();
        cmd.setId("cmd-1");
        cmd.setStatus(CommandStatus.PENDING);
        when(commandRepo.findById("cmd-1")).thenReturn(Optional.of(cmd));
        when(commandRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        commandService.acknowledge("cmd-1", "ACKNOWLEDGED");

        assertThat(cmd.getStatus()).isEqualTo(CommandStatus.ACKNOWLEDGED);
        assertThat(cmd.getAcknowledgedAt()).isNotNull();
        verify(commandRepo).save(cmd);
    }

    @Test
    void acknowledge_failedStatus_setsExpired() {
        Command cmd = new Command();
        cmd.setId("cmd-2");
        cmd.setStatus(CommandStatus.PENDING);
        when(commandRepo.findById("cmd-2")).thenReturn(Optional.of(cmd));
        when(commandRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        commandService.acknowledge("cmd-2", "FAILED");

        assertThat(cmd.getStatus()).isEqualTo(CommandStatus.EXPIRED);
        verify(commandRepo).save(cmd);
    }

    @Test
    void expireStaleCommands_marksExpired() {
        Command stale = new Command();
        stale.setId("cmd-stale");
        stale.setDeviceId("kauf-01");
        stale.setStatus(CommandStatus.PENDING);

        when(commandRepo.findByStatusAndExpiresAtBefore(eq(CommandStatus.PENDING), any()))
                .thenReturn(List.of(stale));
        when(commandRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        commandService.expireStaleCommands();

        assertThat(stale.getStatus()).isEqualTo(CommandStatus.EXPIRED);
        verify(commandRepo).save(stale);
    }
}
