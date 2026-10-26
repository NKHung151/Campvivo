package vn.campvivo.shipping;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.shipping.dto.ShippingFeeResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Quy tắc phí ship D-27: cân nặng theo số món, miễn phí từ ngưỡng (đúng biên). */
class ShippingServiceTest {

    private final GhnService ghn = mock(GhnService.class);
    private final ShippingService service = new ShippingService(ghn, new Binder(new MapConfigurationPropertySource(Map.of(
            "app.jwt.secret", "test_only_secret_at_least_32_characters_long",
            "app.ghn.default-weight", "500",
            "app.shipping.free-threshold", "1000000"))).bind("app", AppProperties.class).get());

    @Test
    void chargesGhnFeeBelowThresholdWithWeightByQuantity() {
        when(ghn.calculateFee(any())).thenReturn(36300L);

        ShippingFeeResponse quote = service.quote(1442, "20109", 3, 999_999);

        assertThat(quote.fee()).isEqualTo(36300);
        assertThat(quote.carrierFee()).isEqualTo(36300);
        assertThat(quote.freeShipping()).isFalse();
        verify(ghn).calculateFee(new GhnFeeRequest(1442, "20109", 1500, 0));
    }

    @Test
    void freeFromThresholdButStillAsksGhn() {
        when(ghn.calculateFee(any())).thenReturn(36300L);

        ShippingFeeResponse quote = service.quote(1442, "20109", 1, 1_000_000);

        assertThat(quote.fee()).isZero();
        assertThat(quote.carrierFee()).isEqualTo(36300);
        assertThat(quote.freeShipping()).isTrue();
        assertThat(quote.freeShippingThreshold()).isEqualTo(1_000_000);
    }
}
