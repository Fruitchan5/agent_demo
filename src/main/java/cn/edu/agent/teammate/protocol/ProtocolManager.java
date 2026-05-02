package cn.edu.agent.teammate.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class ProtocolManager {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Map<String, Request> requests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Path protocolDir = Paths.get(".team/protocols");

    public ProtocolManager() throws IOException {
        Files.createDirectories(protocolDir);
        loadPersistedRequests();
    }

    public String initiateRequest(String type, String target,
                                   Map<String, Object> data,
                                   Duration timeout) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        Request req = new Request(requestId, type, target, "pending", data);
        requests.put(requestId, req);

        persistRequest(req);

        scheduler.schedule(() -> {
            if ("pending".equals(req.getStatus())) {
                req.setStatus("timeout");
                req.setReason("No response within " + timeout);
                persistRequest(req);
                log.warn("Request {} timed out after {}", requestId, timeout);
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        return requestId;
    }

    public void handleResponse(String requestId, boolean approve, String reason) {
        Request req = requests.get(requestId);
        if (req != null) {
            req.setStatus(approve ? "approved" : "rejected");
            req.setReason(reason);
            persistRequest(req);
            log.info("Request {} {}: {}", requestId, req.getStatus(), reason);
        } else {
            log.warn("Received response for unknown request: {}", requestId);
        }
    }

    public void cancelRequest(String requestId) {
        Request req = requests.get(requestId);
        if (req != null && "pending".equals(req.getStatus())) {
            req.setStatus("cancelled");
            persistRequest(req);
            log.info("Request {} cancelled", requestId);
        }
    }

    public Request getRequest(String requestId) {
        return requests.get(requestId);
    }

    public List<Request> getPendingRequests() {
        return requests.values().stream()
            .filter(r -> "pending".equals(r.getStatus()))
            .collect(Collectors.toList());
    }

    private void persistRequest(Request req) {
        Path file = protocolDir.resolve(req.getType() + "_requests.jsonl");
        try (BufferedWriter writer = Files.newBufferedWriter(file,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(MAPPER.writeValueAsString(req));
            writer.newLine();
        } catch (IOException e) {
            log.error("Failed to persist request: {}", e.getMessage());
        }
    }

    private void loadPersistedRequests() throws IOException {
        if (!Files.exists(protocolDir)) {
            return;
        }

        Files.list(protocolDir)
            .filter(p -> p.toString().endsWith(".jsonl"))
            .forEach(this::loadRequestsFromFile);
    }

    private void loadRequestsFromFile(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                Request req = MAPPER.readValue(line, Request.class);
                if ("pending".equals(req.getStatus())) {
                    requests.put(req.getId(), req);
                }
            }
            log.info("Loaded {} pending requests from {}",
                requests.size(), file.getFileName());
        } catch (IOException e) {
            log.error("Failed to load requests from {}: {}", file, e.getMessage());
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Data
    public static class Request {
        private String id;
        private String type;
        private String target;
        private String status;
        private Map<String, Object> data;
        private String reason;
        private long timestamp;

        public Request() {}

        public Request(String id, String type, String target,
                      String status, Map<String, Object> data) {
            this.id = id;
            this.type = type;
            this.target = target;
            this.status = status;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
