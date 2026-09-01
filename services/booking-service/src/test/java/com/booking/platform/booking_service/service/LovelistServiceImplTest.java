package com.booking.platform.booking_service.service;

import com.booking.platform.booking_service.entity.FavoriteEntity;
import com.booking.platform.booking_service.repository.FavoriteRepository;
import com.booking.platform.booking_service.service.impl.LovelistServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LovelistServiceImplTest {

    @Mock private FavoriteRepository favoriteRepository;
    @InjectMocks private LovelistServiceImpl lovelistService;

    private static final String USER_ID = "user-1";
    private static final String EVENT_ID = "event-1";

    private FavoriteEntity favorite() {
        return FavoriteEntity.builder()
                .id(UUID.randomUUID()).userId(USER_ID).eventId(EVENT_ID).build();
    }

    @Test
    void getFavorites_returnsRepositoryResult() {
        List<FavoriteEntity> favorites = List.of(favorite());
        when(favoriteRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(favorites);

        assertThat(lovelistService.getFavorites(USER_ID)).isSameAs(favorites);
    }

    @Test
    void addFavorite_alreadyExists_returnsExistingWithoutSaving() {
        FavoriteEntity existing = favorite();
        when(favoriteRepository.findByUserIdAndEventId(USER_ID, EVENT_ID)).thenReturn(Optional.of(existing));

        FavoriteEntity result = lovelistService.addFavorite(USER_ID, EVENT_ID);

        assertThat(result).isSameAs(existing);
        verify(favoriteRepository, never()).saveAndFlush(any());
    }

    @Test
    void addFavorite_new_savesAndReturns() {
        when(favoriteRepository.findByUserIdAndEventId(USER_ID, EVENT_ID)).thenReturn(Optional.empty());
        when(favoriteRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        FavoriteEntity result = lovelistService.addFavorite(USER_ID, EVENT_ID);

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getEventId()).isEqualTo(EVENT_ID);
        verify(favoriteRepository).saveAndFlush(any(FavoriteEntity.class));
    }

    @Test
    void addFavorite_concurrentInsert_fallsBackToExisting() {
        FavoriteEntity winner = favorite();
        when(favoriteRepository.findByUserIdAndEventId(USER_ID, EVENT_ID))
                .thenReturn(Optional.empty())          // first check: absent
                .thenReturn(Optional.of(winner));      // after conflict: the row the other request inserted
        when(favoriteRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        FavoriteEntity result = lovelistService.addFavorite(USER_ID, EVENT_ID);

        assertThat(result).isSameAs(winner);
    }

    @Test
    void addFavorite_integrityViolationButStillMissing_rethrows() {
        when(favoriteRepository.findByUserIdAndEventId(USER_ID, EVENT_ID)).thenReturn(Optional.empty());
        when(favoriteRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> lovelistService.addFavorite(USER_ID, EVENT_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removeFavorite_deletesByUserAndEvent() {
        when(favoriteRepository.deleteByUserIdAndEventId(USER_ID, EVENT_ID)).thenReturn(1L);

        lovelistService.removeFavorite(USER_ID, EVENT_ID);

        verify(favoriteRepository).deleteByUserIdAndEventId(USER_ID, EVENT_ID);
    }

    @Test
    void removeFavorite_missing_isNoOp() {
        when(favoriteRepository.deleteByUserIdAndEventId(USER_ID, EVENT_ID)).thenReturn(0L);

        lovelistService.removeFavorite(USER_ID, EVENT_ID);

        verify(favoriteRepository).deleteByUserIdAndEventId(USER_ID, EVENT_ID);
    }
}
