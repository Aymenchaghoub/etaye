package fr.etaye.infrastructure.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/probes")
public class ReadinessProbeController {

    private final DataSource dataSource;

    public ReadinessProbeController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping(value = "/readiness", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> checkReadiness() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return ResponseEntity.ok(Map.of(
                        "status", "UP",
                        "database", "UP"
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "status", "DOWN",
                    "database", "DOWN",
                    "error", "Database connection unavailable"
            ));
        }

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", "DOWN",
                "database", "DOWN"
        ));
    }
}
