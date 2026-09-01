package com.booking.platform.booking_service.service.impl;

import com.booking.platform.booking_service.entity.FavoriteEntity;
import com.booking.platform.booking_service.repository.FavoriteRepository;
import com.booking.platform.booking_service.service.LovelistService;
import com.booking.platform.common.logging.ApplicationLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Per-user lovelist (favorites) persistence. All reads and writes are scoped by {@code userId}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LovelistServiceImpl implements LovelistService {

    private final FavoriteRepository favoriteRepository;

    @Override
    @Transactional(readOnly = true)
    public List<FavoriteEntity> getFavorites(String userId) {
        final List<FavoriteEntity> favoriteEntities = favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId);

        ApplicationLogger.logMessage(log, Level.DEBUG,
                "Lovelist getFavorites: user='{}', Favorite items = '{}", userId, favoriteEntities.size());

        return favoriteEntities;
    }

    @Override
    @Transactional
    public FavoriteEntity addFavorite(String userId, String eventId) {
        ApplicationLogger.logMessage(log, Level.DEBUG,
                "Lovelist addFavorite: user='{}', event='{}'", userId, eventId);

        return favoriteRepository.findByUserIdAndEventId(userId, eventId)
                .orElseGet(() -> insertOrGetExisting(userId, eventId));
    }

    /**
     * Inserts a new favorite, falling back to the existing row if a concurrent add won the race
     * (the unique constraint fires). Keeps "add favorite" idempotent under double-clicks.
     */
    private FavoriteEntity insertOrGetExisting(String userId, String eventId) {
        try {
            FavoriteEntity saved = favoriteRepository.saveAndFlush(FavoriteEntity.builder()
                    .userId(userId)
                    .eventId(eventId)
                    .build());

            ApplicationLogger.logMessage(log, Level.DEBUG,
                    "Favorite added: user='{}', event='{}'", userId, eventId);
            return saved;

        } catch (DataIntegrityViolationException e) {
            // Another request inserted the same (user, event) first — return that row.
            return favoriteRepository.findByUserIdAndEventId(userId, eventId).orElseThrow(() -> e);
        }
    }

    @Override
    @Transactional
    public void removeFavorite(String userId, String eventId) {
        long deleted = favoriteRepository.deleteByUserIdAndEventId(userId, eventId);
        if (deleted == 0) {
            ApplicationLogger.logMessage(log, Level.DEBUG,
                    "Favorite removeFavorite no-op: user='{}', event='{}' not favorited", userId, eventId);
        }else{
            ApplicationLogger.logMessage(log, Level.DEBUG,
                    "Favorite removed: user='{}', event='{}'", userId, eventId);
        }
    }
}
