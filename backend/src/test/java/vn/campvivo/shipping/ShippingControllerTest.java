package vn.campvivo.shipping;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.shipping.dto.ProvinceResponse;
import vn.campvivo.support.PostgresTestContainerConfig;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API /api/shipping (docs api.md 4.12): công khai, validate đầu vào, lỗi GHN → 502 SHIPPING_GATEWAY_ERROR. */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.locations=classpath:db/migration",
        "app.jwt.secret=test_only_secret_at_least_32_characters_long",
        "app.shipping.free-threshold=1000000"
})
class ShippingControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private GhnService ghn;

    @Test
    void provincesArePublic() throws Exception {
        when(ghn.getProvinces()).thenReturn(List.of(new ProvinceResponse(202, "Hồ Chí Minh")));

        mvc.perform(get("/api/shipping/provinces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(202))
                .andExpect(jsonPath("$.data[0].name").value("Hồ Chí Minh"));
    }

    @Test
    void districtsRejectInvalidProvinceId() throws Exception {
        mvc.perform(get("/api/shipping/districts").param("provinceId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void feeAppliesFreeShippingThreshold() throws Exception {
        when(ghn.calculateFee(any())).thenReturn(36300L);

        mvc.perform(post("/api/shipping/fee").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toDistrictId\":1442,\"toWardCode\":\"20109\",\"quantity\":2,\"orderValue\":500000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fee").value(36300))
                .andExpect(jsonPath("$.data.freeShipping").value(false));

        mvc.perform(post("/api/shipping/fee").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toDistrictId\":1442,\"toWardCode\":\"20109\",\"quantity\":2,\"orderValue\":1200000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fee").value(0))
                .andExpect(jsonPath("$.data.carrierFee").value(36300))
                .andExpect(jsonPath("$.data.freeShipping").value(true));
    }

    @Test
    void feeValidatesBody() throws Exception {
        mvc.perform(post("/api/shipping/fee").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toDistrictId\":1442,\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fieldErrors.toWardCode").exists())
                .andExpect(jsonPath("$.details.fieldErrors.quantity").exists());
    }

    @Test
    void ghnFailureIsBadGateway() throws Exception {
        when(ghn.calculateFee(any())).thenThrow(new BusinessException(ErrorCode.SHIPPING_GATEWAY_ERROR));

        mvc.perform(post("/api/shipping/fee").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toDistrictId\":1442,\"toWardCode\":\"99999\",\"quantity\":1}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("SHIPPING_GATEWAY_ERROR"));
    }
}
