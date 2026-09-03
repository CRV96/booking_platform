package com.booking.platform.user_service.mapper;

import com.booking.platform.common.grpc.user.BillingAddress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillingAddressCodecTest {

    private BillingAddress sample() {
        return BillingAddress.newBuilder()
                .setFullName("Jane Doe").setLine1("123 Main St").setLine2("Apt 4")
                .setCity("NYC").setState("NY").setPostalCode("10001").setCountry("US")
                .build();
    }

    @Test
    void toJson_thenFromJson_roundTripsAllFields() {
        BillingAddress result = BillingAddressCodec.fromJson(BillingAddressCodec.toJson(sample()));

        assertThat(result.getFullName()).isEqualTo("Jane Doe");
        assertThat(result.getLine1()).isEqualTo("123 Main St");
        assertThat(result.getLine2()).isEqualTo("Apt 4");
        assertThat(result.getCity()).isEqualTo("NYC");
        assertThat(result.getState()).isEqualTo("NY");
        assertThat(result.getPostalCode()).isEqualTo("10001");
        assertThat(result.getCountry()).isEqualTo("US");
    }

    @Test
    void toJson_producesJsonContainingFields() {
        String json = BillingAddressCodec.toJson(sample());
        assertThat(json).contains("\"line1\":\"123 Main St\"").contains("\"country\":\"US\"");
    }

    @Test
    void fromJson_blankOrNull_returnsDefaultInstance() {
        assertThat(BillingAddressCodec.fromJson("")).isEqualTo(BillingAddress.getDefaultInstance());
        assertThat(BillingAddressCodec.fromJson("   ")).isEqualTo(BillingAddress.getDefaultInstance());
        assertThat(BillingAddressCodec.fromJson(null)).isEqualTo(BillingAddress.getDefaultInstance());
    }

    @Test
    void fromJson_legacyPlainString_goesToLine1() {
        BillingAddress result = BillingAddressCodec.fromJson("123 Admin Street, New York, NY 10001");

        assertThat(result.getLine1()).isEqualTo("123 Admin Street, New York, NY 10001");
        assertThat(result.getCity()).isEmpty();
    }

    @Test
    void fromJson_partialJson_fillsPresentFieldsAndLeavesRestEmpty() {
        BillingAddress result = BillingAddressCodec.fromJson("{\"city\":\"Berlin\",\"country\":\"DE\"}");

        assertThat(result.getCity()).isEqualTo("Berlin");
        assertThat(result.getCountry()).isEqualTo("DE");
        assertThat(result.getLine1()).isEmpty();
    }
}
