package com.booking.platform.booking_service.service.impl;

import com.booking.platform.booking_service.entity.CartItemEntity;
import com.booking.platform.booking_service.exception.CartItemNotFoundException;
import com.booking.platform.booking_service.repository.CartItemRepository;
import com.booking.platform.booking_service.service.CartService;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Per-user cart persistence. All reads and writes are scoped by {@code userId}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemRepository cartItemRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CartItemEntity> getCart(String userId) {
        final List<CartItemEntity> cartItems = cartItemRepository.findByUserIdOrderByCreatedAtAsc(userId);

        ApplicationLogger.logMessage(log, Level.DEBUG,
                "Cart getCart: user='{}', lines={}", userId, cartItems.size());

        return cartItems;
    }

    @Override
    @Transactional
    public CartItemEntity addItem(String userId, String eventId, String eventTitle,
                                  String seatCategory, int quantity, BigDecimal unitPrice, String currency) {
        // Upsert on (userId, eventId, seatCategory): update the existing line, or insert a new one.
        return cartItemRepository.findByUserIdAndEventIdAndSeatCategory(userId, eventId, seatCategory)
                .map(existing -> {
                    existing.setQuantity(quantity);
                    existing.setEventTitle(eventTitle);
                    existing.setUnitPrice(unitPrice);
                    existing.setCurrency(currency);

                    ApplicationLogger.logMessage(log, Level.DEBUG,
                            "Cart addItem (update): user='{}', event='{}', category='{}', qty={}",
                            userId, eventId, seatCategory, quantity);

                    return existing;
                })
                .orElseGet(() -> {
                    CartItemEntity saved = cartItemRepository.save(CartItemEntity.builder()
                            .userId(userId)
                            .eventId(eventId)
                            .eventTitle(eventTitle)
                            .seatCategory(seatCategory)
                            .quantity(quantity)
                            .unitPrice(unitPrice)
                            .currency(currency)
                            .build());

                    ApplicationLogger.logMessage(log, Level.DEBUG,
                            "Cart addItem (insert): user='{}', event='{}', category='{}', qty={}",
                            userId, eventId, seatCategory, quantity);

                    return saved;
                });
    }

    @Override
    @Transactional
    public CartItemEntity updateItemQuantity(String userId, UUID cartItemId, int quantity) {
        CartItemEntity item = cartItemRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> new CartItemNotFoundException(String.valueOf(cartItemId)));

        ApplicationLogger.logMessage(log, Level.DEBUG,
                "Cart updateItemQuantity: user='{}', item='{}', newQty={}", userId, cartItemId, quantity);

        item.setQuantity(quantity);

        return item;
    }

    @Override
    @Transactional
    public void removeItem(String userId, UUID cartItemId) {
        long deleted = cartItemRepository.deleteByIdAndUserId(cartItemId, userId);

        if (deleted == 0) {
            ApplicationLogger.logMessage(log, Level.DEBUG,
                    "Cart removeItem no-op: user='{}', item='{}' not found", userId, cartItemId);
        }
    }

    @Override
    @Transactional
    public void clearCart(String userId) {
        long deleted = cartItemRepository.deleteByUserId(userId);
        ApplicationLogger.logMessage(log, Level.DEBUG,
                "Cart cleared: user='{}', linesRemoved={}", userId, deleted);
    }
}
