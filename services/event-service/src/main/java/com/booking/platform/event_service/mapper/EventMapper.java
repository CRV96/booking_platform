package com.booking.platform.event_service.mapper;

import com.booking.platform.common.grpc.event.EventInfo;
import com.booking.platform.common.grpc.event.OrganizerInfo;
import com.booking.platform.common.grpc.event.SeatCategoryInfo;
import com.booking.platform.common.grpc.event.VenueInfo;
import com.booking.platform.event_service.document.EventDocument;
import org.mapstruct.Mapper;

import java.util.List;

import static com.booking.platform.event_service.util.NullSafetyUtil.*;

/**
 * MapStruct mapper for converting between EventDocument (MongoDB) and EventInfo (Protobuf).
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
                .setPrice(orZero(sc.getPrice()))
                .setCurrency(nullToEmpty(sc.getCurrency()))
                .setTotalSeats(orZero(sc.getTotalSeats()))
                .setAvailableSeats(orZero(sc.getAvailableSeats()))
                .build();
    }

}
