package com.booking.platform.booking_service.service;

import com.booking.platform.booking_service.dto.AddCartItemDto;
import com.booking.platform.booking_service.entity.CartItemEntity;
import com.booking.platform.booking_service.exception.CartItemNotFoundException;
import com.booking.platform.booking_service.repository.CartItemRepository;
import com.booking.platform.booking_service.service.impl.CartServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock private CartItemRepository cartItemRepository;
    @InjectMocks private CartServiceImpl cartService;

    private static final String USER_ID = "user-1";
    private static final String EVENT_ID = "event-1";
    private static final String CATEGORY = "VIP";
    private static final UUID ITEM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private AddCartItemDto dto(int quantity) {
        return AddCartItemDto.builder()
                .userId(USER_ID)
                .eventId(EVENT_ID)
                .eventTitle("Rock Fest")
                .seatCategory(CATEGORY)
                .quantity(quantity)
                .unitPrice(new BigDecimal("49.99"))
                .currency("USD")
                .build();
    }

    private CartItemEntity existingLine() {
        return CartItemEntity.builder()
                .id(ITEM_ID).userId(USER_ID).eventId(EVENT_ID).eventTitle("Old Title")
                .seatCategory(CATEGORY).quantity(1).unitPrice(new BigDecimal("10.00")).currency("EUR")
                .build();
    }

    @Test
    void getCart_returnsRepositoryResult() {
        List<CartItemEntity> lines = List.of(existingLine());
        when(cartItemRepository.findByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(lines);

        assertThat(cartService.getCart(USER_ID)).isSameAs(lines);
    }

    @Test
    void addItem_existingLine_updatesInPlaceWithoutInsert() {
        CartItemEntity existing = existingLine();
        when(cartItemRepository.findByUserIdAndEventIdAndSeatCategory(USER_ID, EVENT_ID, CATEGORY))
                .thenReturn(Optional.of(existing));

        CartItemEntity result = cartService.addItem(dto(3));

        assertThat(result).isSameAs(existing);
        assertThat(result.getQuantity()).isEqualTo(3);
        assertThat(result.getEventTitle()).isEqualTo("Rock Fest");
        assertThat(result.getUnitPrice()).isEqualByComparingTo("49.99");
        assertThat(result.getCurrency()).isEqualTo("USD");
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItem_newLine_insertsAndReturnsSaved() {
        when(cartItemRepository.findByUserIdAndEventIdAndSeatCategory(USER_ID, EVENT_ID, CATEGORY))
                .thenReturn(Optional.empty());
        when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CartItemEntity result = cartService.addItem(dto(2));

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getEventId()).isEqualTo(EVENT_ID);
        assertThat(result.getSeatCategory()).isEqualTo(CATEGORY);
        assertThat(result.getQuantity()).isEqualTo(2);
        assertThat(result.getUnitPrice()).isEqualByComparingTo("49.99");
        verify(cartItemRepository).save(any(CartItemEntity.class));
    }

    @Test
    void updateItemQuantity_found_updatesQuantity() {
        CartItemEntity existing = existingLine();
        when(cartItemRepository.findByIdAndUserId(ITEM_ID, USER_ID)).thenReturn(Optional.of(existing));

        CartItemEntity result = cartService.updateItemQuantity(USER_ID, ITEM_ID, 5);

        assertThat(result.getQuantity()).isEqualTo(5);
    }

    @Test
    void updateItemQuantity_notFound_throws() {
        when(cartItemRepository.findByIdAndUserId(ITEM_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateItemQuantity(USER_ID, ITEM_ID, 5))
                .isInstanceOf(CartItemNotFoundException.class)
                .hasMessageContaining(ITEM_ID.toString());
    }

    @Test
    void removeItem_deletesById() {
        when(cartItemRepository.deleteByIdAndUserId(ITEM_ID, USER_ID)).thenReturn(1L);

        cartService.removeItem(USER_ID, ITEM_ID);

        verify(cartItemRepository).deleteByIdAndUserId(ITEM_ID, USER_ID);
    }

    @Test
    void removeItem_missing_isNoOp() {
        when(cartItemRepository.deleteByIdAndUserId(ITEM_ID, USER_ID)).thenReturn(0L);

        cartService.removeItem(USER_ID, ITEM_ID);

        verify(cartItemRepository).deleteByIdAndUserId(ITEM_ID, USER_ID);
    }

    @Test
    void clearCart_deletesByUser() {
        when(cartItemRepository.deleteByUserId(USER_ID)).thenReturn(3L);

        cartService.clearCart(USER_ID);

        verify(cartItemRepository).deleteByUserId(USER_ID);
    }
}
