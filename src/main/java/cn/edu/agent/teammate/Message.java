package cn.edu.agent.teammate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final Set<String> VALID_TYPES = Set.of(
        "MESSAGE",
        "BROADCAST",
        "SHUTDOWN_REQUEST:v1",
        "SHUTDOWN_RESPONSE:v1",
        "PLAN_REQUEST:v1",
        "PLAN_RESPONSE:v1",
        "REQUEST_CANCELLED:v1"
    );

    @JsonProperty("type")
    private String type;

    @JsonProperty("from")
    private String from;

    @JsonProperty("content")
    private String content;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("protocol_version")
    private String protocolVersion = "1.0";

    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Message", e);
        }
    }
    
    public static Message fromJson(String json) {
        try {
            return MAPPER.readValue(json, Message.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize Message: " + json, e);
        }
    }
}
