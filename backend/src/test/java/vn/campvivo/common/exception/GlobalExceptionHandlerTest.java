package vn.campvivo.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Controller giả ném từng loại lỗi -> kiểm tra JSON đúng format docs/02-ky-thuat/api.md mục 1. */
class GlobalExceptionHandlerTest {

    record Body(@NotBlank(message = "Email không được để trống") String email) {}

    @RestController
    static class ThrowingController {
        @GetMapping("/t/default-message")
        void defaultMessage() { throw new BusinessException(ErrorCode.CART_EMPTY); }

        @GetMapping("/t/details")
        void details() {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK, "Chỉ còn 3 sản phẩm khả dụng.",
                    Map.of("items", List.of(Map.of("id", 7, "availableQuantity", 3))));
        }

        @GetMapping("/t/legacy")
        @SuppressWarnings("deprecation")
        void legacy() { throw new BusinessException("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm.", HttpStatus.NOT_FOUND); }

        @PostMapping("/t/validate")
        void validate(@Valid @RequestBody Body body) {}

        @GetMapping("/t/forbidden")
        void forbidden() { throw new AccessDeniedException("secret detail"); }

        @GetMapping("/t/boom")
        void boom() { throw new IllegalStateException("kết nối DB: password=hunter2"); }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessExceptionUsesCodeDefaultMessageAndOmitsDetails() throws Exception {
        mvc.perform(get("/t/default-message"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CART_EMPTY"))
                .andExpect(jsonPath("$.message").value("Giỏ hàng đang trống."))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void businessExceptionCarriesCustomMessageAndDetails() throws Exception {
        mvc.perform(get("/t/details"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.message").value("Chỉ còn 3 sản phẩm khả dụng."))
                .andExpect(jsonPath("$.details.items[0].availableQuantity").value(3));
    }

    @Test
    void deprecatedStringConstructorStillWorks() throws Exception {
        mvc.perform(get("/t/legacy"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void invalidBodyReturnsFieldErrors() throws Exception {
        mvc.perform(post("/t/validate").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fieldErrors.email").value("Email không được để trống"));
    }

    @Test
    void malformedJsonIsValidationError() throws Exception {
        mvc.perform(post("/t/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void wrongHttpMethodUsesStandardFormat() throws Exception {
        mvc.perform(post("/t/default-message"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void accessDeniedIsForbiddenWithoutLeakingDetail() throws Exception {
        mvc.perform(get("/t/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(content().string(not(containsString("secret detail"))));
    }

    @Test
    void unexpectedExceptionIsInternalErrorWithoutLeakingInternals() throws Exception {
        mvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
