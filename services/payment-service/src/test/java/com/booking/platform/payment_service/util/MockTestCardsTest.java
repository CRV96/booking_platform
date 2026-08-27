package com.booking.platform.payment_service.util;

import org.junit.jupiter.api.Test;

import static com.booking.platform.payment_service.util.MockTestCards.Outcome;
import static org.assertj.core.api.Assertions.assertThat;

class MockTestCardsTest {

    private static final String SUCCESS = "4242424242424242";
    private static final String REQUIRES_AUTH = "4000002500003155";
    private static final String INSUFFICIENT = "4000000000009995";

    /** Classify against the standard (dev) configured cards. */
    private Outcome classify(String cardNumber) {
        return MockTestCards.classify(cardNumber, SUCCESS, REQUIRES_AUTH, INSUFFICIENT);
    }

    @Test
    void successCard_isSuccess() {
        assertThat(classify(SUCCESS)).isEqualTo(Outcome.SUCCESS);
    }

    @Test
    void requiresAuthCard_isRequiresAuth() {
        assertThat(classify(REQUIRES_AUTH)).isEqualTo(Outcome.REQUIRES_AUTH);
    }

    @Test
    void insufficientFundsCard_isInsufficientFunds() {
        assertThat(classify(INSUFFICIENT)).isEqualTo(Outcome.INSUFFICIENT_FUNDS);
    }

    @Test
    void declineCard_isDeclined() {
        assertThat(classify("4000000000000002")).isEqualTo(Outcome.DECLINED);
    }

    @Test
    void unknownCard_defaultsToDeclined() {
        assertThat(classify("1234123412341234")).isEqualTo(Outcome.DECLINED);
    }

    @Test
    void spacesAreIgnored() {
        assertThat(classify("4242 4242 4242 4242")).isEqualTo(Outcome.SUCCESS);
    }

    @Test
    void nullOrBlank_defaultsToDeclined() {
        assertThat(classify(null)).isEqualTo(Outcome.DECLINED);
        assertThat(classify("")).isEqualTo(Outcome.DECLINED);
    }

    // ── Defence in depth: empty configured cards (e.g. production) ────────────────

    @Test
    void emptyConfig_declinesEvenTheSuccessCard() {
        assertThat(MockTestCards.classify(SUCCESS, "", "", "")).isEqualTo(Outcome.DECLINED);
    }

    @Test
    void emptyConfig_blankInputDoesNotMatchEmptyCard() {
        // A blank card input must NOT match an empty configured card.
        assertThat(MockTestCards.classify("", "", "", "")).isEqualTo(Outcome.DECLINED);
        assertThat(MockTestCards.classify(null, null, null, null)).isEqualTo(Outcome.DECLINED);
    }
}
