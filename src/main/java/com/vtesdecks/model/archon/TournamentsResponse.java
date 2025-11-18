package com.vtesdecks.model.archon;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonDeserialize(using = TournamentsResponse.TournamentDataDeserializer.class)
public class TournamentsResponse {
    private String date;
    private String uid;

    private List<TournamentEvent> events;

    public static class TournamentDataDeserializer extends JsonDeserializer {
        @Override
        public Object deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
            JsonNode node = mapper.readTree(jsonParser);

            if (!node.isArray() || node.isEmpty()) {
                return null;
            }

            JsonNode meta = node.get(0);
            TournamentsResponse data = TournamentsResponse.builder()
                    .date(meta.has("date") ? meta.get("date").asText() : null)
                    .uid(meta.has("uid") ? meta.get("uid").asText() : null)
                    .build();

            if (node.size() > 1 && node.get(1).isArray()) {
                List<TournamentEvent> events = mapper.readerFor(new TypeReference<List<TournamentEvent>>() {
                        })
                        .readValue(node.get(1));
                data.setEvents(events);
            }

            return data;
        }
    }
}
