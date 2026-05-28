package com.codedgiraffe.smartvolt.cloud.command;

import com.codedgiraffe.smartvolt.shared.dto.CommandAckRequest;
import com.codedgiraffe.smartvolt.shared.dto.CommandDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/commands")
public class CommandController {

    private final CommandService commandService;

    public CommandController(CommandService commandService) {
        this.commandService = commandService;
    }

    @GetMapping("/pending")
    public List<CommandDto> getPending(@RequestParam String deviceId) {
        return commandService.findPending(deviceId);
    }

    @PostMapping("/{id}/ack")
    public ResponseEntity<Map<String, String>> acknowledge(
            @PathVariable String id,
            @RequestBody CommandAckRequest request) {
        commandService.acknowledge(id, request.getStatus());
        return ResponseEntity.ok(Map.of("commandId", id, "status", request.getStatus()));
    }
}
