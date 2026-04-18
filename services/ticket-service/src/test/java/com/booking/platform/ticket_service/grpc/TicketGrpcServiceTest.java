package com.booking.platform.ticket_service.grpc;

import com.booking.platform.common.enums.Roles;
import com.booking.platform.common.exceptions.PermissionDeniedException;
import com.booking.platform.common.grpc.context.GrpcUserContext;
import com.booking.platform.common.grpc.ticket.*;
import com.booking.platform.ticket_service.document.TicketDocument;
import com.booking.platform.ticket_service.document.enums.TicketStatus;
import com.booking.platform.ticket_service.exception.InvalidTicketOperationException;
import com.booking.platform.ticket_service.grpc.server.TicketGrpcService;
import com.booking.platform.ticket_service.mapper.TicketProtoMapper;
import com.booking.platform.ticket_service.service.TicketService;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketGrpcServiceTest {

    @Mock private TicketService ticketService;
    @Mock private TicketProtoMapper ticketProtoMapper;

    @InjectMocks private TicketGrpcService grpcService;

    @SuppressWarnings("unchecked")
    private <T> StreamObserver<T> observer() {
        return mock(StreamObserver.class);
    }

    private TicketDocument ticket(String number) {
        return TicketDocument.builder().id("id-1").ticketNumber(number).status(TicketStatus.VALID).build();
    }

    /** Runs {@code action} inside a gRPC context with the given userId and roles. */
    private void withContext(String userId, List<String> roles, Runnable action) {
        Context ctx = Context.current()
                .withValue(GrpcUserContext.USER_ID, userId)
                .withValue(GrpcUserContext.ROLES, roles);
        Context previous = ctx.attach();
        try {
            action.run();
        } finally {
            ctx.detach(previous);
        }
    }

    // ── getMyTickets ──────────────────────────────────────────────────────────

    @Test
    void getMyTickets_authenticated_returnsTickets() {
        Page<TicketDocument> page = new PageImpl<>(List.of(ticket("TKT-A")));
        when(ticketService.getTicketsByUserId(eq("user-123"), any())).thenReturn(page);
        when(ticketProtoMapper.toProtoList(anyList())).thenReturn(List.of());

        StreamObserver<GetMyTicketsResponse> obs = observer();
        withContext("user-123", List.of("customer"), () ->
                grpcService.getMyTickets(GetMyTicketsRequest.newBuilder().setPage(0).setPageSize(10).build(), obs));

        verify(obs).onNext(any());
        verify(obs).onCompleted();
        verify(obs, never()).onError(any());
    }

    @Test
    void getMyTickets_noUserId_throwsInvalidTicketOperation() {
        StreamObserver<GetMyTicketsResponse> obs = observer();
        withContext(null, List.of("customer"), () ->
                assertThatThrownBy(() ->
                        grpcService.getMyTickets(GetMyTicketsRequest.getDefaultInstance(), obs))
                        .isInstanceOf(InvalidTicketOperationException.class));
    }

    @Test
    void getMyTickets_blankUserId_throwsInvalidTicketOperation() {
        StreamObserver<GetMyTicketsResponse> obs = observer();
        withContext("  ", List.of("customer"), () ->
                assertThatThrownBy(() ->
                        grpcService.getMyTickets(GetMyTicketsRequest.getDefaultInstance(), obs))
                        .isInstanceOf(InvalidTicketOperationException.class));
    }

    @Test
    void getMyTickets_zeroPageSize_defaultsTo20() {
        when(ticketService.getTicketsByUserId(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(ticketProtoMapper.toProtoList(any())).thenReturn(List.of());

        StreamObserver<GetMyTicketsResponse> obs = observer();
        withContext("user-1", List.of(), () ->
                grpcService.getMyTickets(GetMyTicketsRequest.newBuilder().setPage(0).setPageSize(0).build(), obs));

        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(ticketService).getTicketsByUserId(eq("user-1"), pageCaptor.capture());
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void getMyTickets_pageSizeOver100_clampedTo100() {
        when(ticketService.getTicketsByUserId(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(ticketProtoMapper.toProtoList(any())).thenReturn(List.of());

        StreamObserver<GetMyTicketsResponse> obs = observer();
        withContext("user-1", List.of(), () ->
                grpcService.getMyTickets(GetMyTicketsRequest.newBuilder().setPage(0).setPageSize(200).build(), obs));

        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(ticketService).getTicketsByUserId(eq("user-1"), pageCaptor.capture());
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void getMyTickets_negativePage_clampedToZero() {
        when(ticketService.getTicketsByUserId(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(ticketProtoMapper.toProtoList(any())).thenReturn(List.of());

        StreamObserver<GetMyTicketsResponse> obs = observer();
        withContext("user-1", List.of(), () ->
                grpcService.getMyTickets(GetMyTicketsRequest.newBuilder().setPage(-5).setPageSize(10).build(), obs));

        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(ticketService).getTicketsByUserId(eq("user-1"), pageCaptor.capture());
        assertThat(pageCaptor.getValue().getPageNumber()).isEqualTo(0);
    }

    // ── getTicketsByBooking ───────────────────────────────────────────────────

    @Test
    void getTicketsByBooking_withEmployeeRole_delegatesToService() {
        when(ticketService.getTicketsByBooking("booking-1")).thenReturn(List.of(ticket("TKT-A")));
        when(ticketProtoMapper.toProtoList(anyList())).thenReturn(List.of());

        StreamObserver<GetTicketsByBookingResponse> obs = observer();
        withContext("emp-1", List.of(Roles.EMPLOYEE.getValue()), () ->
                grpcService.getTicketsByBooking(
                        GetTicketsByBookingRequest.newBuilder().setBookingId("booking-1").build(), obs));

        verify(obs).onNext(any());
        verify(obs).onCompleted();
    }

    @Test
    void getTicketsByBooking_missingEmployeeRole_throwsPermissionDenied() {
        StreamObserver<GetTicketsByBookingResponse> obs = observer();
        withContext("user-1", List.of("customer"), () ->
                assertThatThrownBy(() ->
                        grpcService.getTicketsByBooking(
                                GetTicketsByBookingRequest.newBuilder().setBookingId("b").build(), obs))
                        .isInstanceOf(PermissionDeniedException.class));
    }

    // ── getTicketsByUser ──────────────────────────────────────────────────────

    @Test
    void getTicketsByUser_withEmployeeRole_delegatesToService() {
        when(ticketService.getTicketsByUserId(eq("user-2"), any())).thenReturn(new PageImpl<>(List.of()));
        when(ticketProtoMapper.toProtoList(any())).thenReturn(List.of());

        StreamObserver<GetTicketsByUserResponse> obs = observer();
        withContext("emp-1", List.of(Roles.EMPLOYEE.getValue()), () ->
                grpcService.getTicketsByUser(
                        GetTicketsByUserRequest.newBuilder().setUserId("user-2").setPage(0).setPageSize(10).build(), obs));

        verify(obs).onNext(any());
        verify(obs).onCompleted();
    }

    @Test
    void getTicketsByUser_missingRole_throwsPermissionDenied() {
        StreamObserver<GetTicketsByUserResponse> obs = observer();
        withContext("user-1", List.of(), () ->
                assertThatThrownBy(() ->
                        grpcService.getTicketsByUser(
                                GetTicketsByUserRequest.newBuilder().setUserId("u").build(), obs))
                        .isInstanceOf(PermissionDeniedException.class));
    }

    // ── getTicketByNumber ─────────────────────────────────────────────────────

    @Test
    void getTicketByNumber_withEmployeeRole_delegatesToService() {
        TicketDocument t = ticket("TKT-A");
        when(ticketService.getByTicketNumber("TKT-A")).thenReturn(t);
        when(ticketProtoMapper.toProto(t)).thenReturn(TicketInfo.getDefaultInstance());

        StreamObserver<TicketResponse> obs = observer();
        withContext("emp-1", List.of(Roles.EMPLOYEE.getValue()), () ->
                grpcService.getTicketByNumber(
                        GetTicketByNumberRequest.newBuilder().setTicketNumber("TKT-A").build(), obs));

        verify(obs).onNext(any());
        verify(obs).onCompleted();
    }

    @Test
    void getTicketByNumber_missingRole_throwsPermissionDenied() {
        StreamObserver<TicketResponse> obs = observer();
        withContext("user-1", List.of(), () ->
                assertThatThrownBy(() ->
                        grpcService.getTicketByNumber(
                                GetTicketByNumberRequest.newBuilder().setTicketNumber("TKT-A").build(), obs))
                        .isInstanceOf(PermissionDeniedException.class));
    }

    // ── validateTicket ────────────────────────────────────────────────────────

    @Test
    void validateTicket_withEmployeeRole_marksAsUsed() {
        TicketDocument t = ticket("TKT-A");
        when(ticketService.validateTicket("TKT-A")).thenReturn(t);
        when(ticketProtoMapper.toProto(t)).thenReturn(TicketInfo.getDefaultInstance());

        StreamObserver<TicketResponse> obs = observer();
        withContext("emp-1", List.of(Roles.EMPLOYEE.getValue()), () ->
                grpcService.validateTicket(
                        ValidateTicketRequest.newBuilder().setTicketNumber("TKT-A").build(), obs));

        verify(ticketService).validateTicket("TKT-A");
        verify(obs).onNext(any());
        verify(obs).onCompleted();
    }

    @Test
    void validateTicket_missingRole_throwsPermissionDenied() {
        StreamObserver<TicketResponse> obs = observer();
        withContext("user-1", List.of(), () ->
                assertThatThrownBy(() ->
                        grpcService.validateTicket(
                                ValidateTicketRequest.newBuilder().setTicketNumber("TKT-A").build(), obs))
                        .isInstanceOf(PermissionDeniedException.class));
    }

    // ── cancelTicket ──────────────────────────────────────────────────────────

    @Test
    void cancelTicket_withEmployeeRole_cancelsTicket() {
        TicketDocument t = ticket("TKT-A");
        t.setStatus(TicketStatus.CANCELLED);
        when(ticketService.cancelTicket("TKT-A")).thenReturn(t);
        when(ticketProtoMapper.toProto(t)).thenReturn(TicketInfo.getDefaultInstance());

        StreamObserver<TicketResponse> obs = observer();
        withContext("emp-1", List.of(Roles.EMPLOYEE.getValue()), () ->
                grpcService.cancelTicket(
                        CancelTicketRequest.newBuilder().setTicketNumber("TKT-A").build(), obs));

        verify(ticketService).cancelTicket("TKT-A");
        verify(obs).onNext(any());
        verify(obs).onCompleted();
    }

    @Test
    void cancelTicket_missingRole_throwsPermissionDenied() {
        StreamObserver<TicketResponse> obs = observer();
        withContext("user-1", List.of("customer"), () ->
                assertThatThrownBy(() ->
                        grpcService.cancelTicket(
                                CancelTicketRequest.newBuilder().setTicketNumber("TKT-A").build(), obs))
                        .isInstanceOf(PermissionDeniedException.class));
    }
}
