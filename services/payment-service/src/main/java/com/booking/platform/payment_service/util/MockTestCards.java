package com.booking.platform.payment_service.util;

/**
 * Maps a (fake) test card number to a simulated payment outcome, mirroring Stripe's test cards
 * so the habit transfers 1:1. Used only by the mock gateway's confirm flow — see
 * {@code docs/payment-test-cards.md}.
 */
public final class MockTestCards {

    private MockTestCards() {
        // Utility class — not instantiable
    }

    /** Simulated outcome selected by the card number. */
    public enum Outcome {
        SUCCESS,
        REQUIRES_AUTH,       // 3-D Secure challenge — the mock auto-authenticates, then succeeds
        DECLINED,            // generic decline (card_declined)
        INSUFFICIENT_FUNDS   // decline with a specific reason
    }

    /**
     * Classifies a card number into an {@link Outcome} against the configured test cards.
     * Spaces are ignored. Any number that doesn't match a configured success/3DS/insufficient-funds
     * card is a generic decline (this includes Stripe's {@code 4000 0000 0000 0002} card and junk input).
     *
     * <p><b>Empty config never matches.</b> A configured card that is blank/empty (e.g. left empty in
     * production) is skipped — so with all cards empty, every input declines. This is why a blank card
     * input can't accidentally "match" an empty success card.
     *
     * @param cardNumber            the submitted (fake) card number
     * @param successCard           configured card that succeeds (empty = disabled)
     * @param requiresAuthCard      configured 3-D Secure card (empty = disabled)
     * @param insufficientFundsCard configured insufficient-funds card (empty = disabled)
     */
    public static Outcome classify(String cardNumber, String successCard,
                                   String requiresAuthCard, String insufficientFundsCard) {
        String digits = normalize(cardNumber);
        if (matches(successCard, digits)) return Outcome.SUCCESS;
        if (matches(requiresAuthCard, digits)) return Outcome.REQUIRES_AUTH;
        if (matches(insufficientFundsCard, digits)) return Outcome.INSUFFICIENT_FUNDS;
        return Outcome.DECLINED;
    }

    private static boolean matches(String configuredCard, String inputDigits) {
        String configured = normalize(configuredCard);
        return !configured.isEmpty() && configured.equals(inputDigits);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s", "");
    }
}
