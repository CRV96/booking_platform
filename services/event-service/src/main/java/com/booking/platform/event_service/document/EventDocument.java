package com.booking.platform.event_service.document;

import com.booking.platform.event_service.constants.DocumentConst;
import com.booking.platform.event_service.document.enums.EventCategory;
import com.booking.platform.event_service.document.enums.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.TextScore;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = DocumentConst.Event.COLLECTION_NAME)
@CompoundIndexes({
        @CompoundIndex(
                name = "category_status_dateTime",
                def = "{'category': 1, 'status': 1, 'dateTime': 1}"
        )
})
public class EventDocument {

    @Id
    private String id;

    @TextIndexed(weight = 3)
    private String title;

    @TextIndexed
    private String description;

    private EventCategory category;
    private EventStatus status;

    private VenueInfo venue;

    private Instant dateTime;
    private Instant endDateTime;
    private String timezone;

    private OrganizerInfo organizer;

    private List<SeatCategory> seatCategories;

    private List<String> images;
    private List<String> tags;

    @TextScore
    private Float score;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
