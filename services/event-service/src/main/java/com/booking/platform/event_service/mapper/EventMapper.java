package com.booking.platform.event_service.mapper;

import com.booking.platform.common.grpc.event.EventInfo;
import com.booking.platform.common.grpc.event.OrganizerInfo;
import com.booking.platform.common.grpc.event.SeatCategoryInfo;
import com.booking.platform.common.grpc.event.VenueInfo;
import com.booking.platform.event_service.document.EventDocument;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.util.List;

/**
 * MapStruct mapper for converting between EventDocument (MongoDB) and EventInfo (Protobuf).
 * <p>
 * Protobuf classes use builders and are immutable — MapStruct can't set fields directly,
 * so we use default methods with manual builder calls instead of the standard annotation-based mapping.
 */
@Mapper(componentModel = "spring")
public interface EventMapper {

    default EventInfo toProto(EventDocument doc) {
        if (doc == null) return null;

        EventInfo.Builder builder = EventInfo.newBuilder()
                .setId(nullToEmpty(doc.getId()))
                .setTitle(nullToEmpty(doc.getTitle()))
                .setDescription(nullToEmpty(doc.getDescription()))
                .setCategory(doc.getCategory() != null ? doc.getCategory().name() : "")
                .setStatus(doc.getStatus() != null ? doc.getStatus().name() : "")
                .setTimezone(nullToEmpty(doc.getTimezone()))
                .setDateTime(instantToString(doc.getDateTime()))
                .setEndDateTime(instantToString(doc.getEndDateTime()))
                .setCreatedAt(instantToString(doc.getCreatedAt()))
                .setUpdatedAt(instantToString(doc.getUpdatedAt()));

        if (doc.getVenue() != null) {
            builder.setVenue(toVenueProto(doc.getVenue()));
        }

        if (doc.getOrganizer() != null) {
            builder.setOrganizer(toOrganizerProto(doc.getOrganizer()));
        }

        if (doc.getSeatCategories() != null) {
            doc.getSeatCategories().forEach(sc -> builder.addSeatCategories(toSeatCategoryProto(sc)));
        }

        if (doc.getImages() != null) {
            builder.addAllImages(doc.getImages());
        }

        if (doc.getTags() != null) {
            builder.addAllTags(doc.getTags());
        }

        return builder.build();
    }

    default List<EventInfo> toProtoList(List<EventDocument> docs) {
        if (docs == null) return List.of();
        return docs.stream().map(this::toProto).toList();
    }

    default VenueInfo toVenueProto(com.booking.platform.event_service.document.VenueInfo venue) {
        if (venue == null) return VenueInfo.getDefaultInstance();

        VenueInfo.Builder builder = VenueInfo.newBuilder()
                .setName(nullToEmpty(venue.getName()))
                .setAddress(nullToEmpty(venue.getAddress()))
                .setCity(nullToEmpty(venue.getCity()))
                .setCountry(nullToEmpty(venue.getCountry()));

        if (venue.getLatitude() != null)  builder.setLatitude(venue.getLatitude());
        if (venue.getLongitude() != null) builder.setLongitude(venue.getLongitude());
        if (venue.getCapacity() != null)  builder.setCapacity(venue.getCapacity());

        return builder.build();
    }

    default OrganizerInfo toOrganizerProto(com.booking.platform.event_service.document.OrganizerInfo organizer) {
        if (organizer == null) return OrganizerInfo.getDefaultInstance();

        return OrganizerInfo.newBuilder()
                .setUserId(nullToEmpty(organizer.getUserId()))
                .setName(nullToEmpty(organizer.getName()))
                .setEmail(nullToEmpty(organizer.getEmail()))
                .build();
    }

    default SeatCategoryInfo toSeatCategoryProto(com.booking.platform.event_service.document.SeatCategory sc) {
        if (sc == null) return SeatCategoryInfo.getDefaultInstance();

        return SeatCategoryInfo.newBuilder()
                .setName(nullToEmpty(sc.getName()))
                .setPrice(sc.getPrice() != null ? sc.getPrice() : 0.0)
                .setCurrency(nullToEmpty(sc.getCurrency()))
                .setTotalSeats(sc.getTotalSeats() != null ? sc.getTotalSeats() : 0)
                .setAvailableSeats(sc.getAvailableSeats() != null ? sc.getAvailableSeats() : 0)
                .build();
    }

    default String instantToString(Instant instant) {
        return instant != null ? instant.toString() : "";
    }

    default String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
