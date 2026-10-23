package vn.campvivo.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import vn.campvivo.common.dto.ApiErrorResponse;
import vn.campvivo.common.exception.ErrorCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Lỗi 401 / 403 phát sinh trong filter chain (trước khi tới controller nên GlobalExceptionHandler không bắt
 * được) vẫn trả đúng ApiErrorResponse của api.md mục 1. Không tiết lộ lý do chi tiết.
 */
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        write(response, ErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(code));
    }
}
