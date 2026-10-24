package vn.campvivo.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.user.UserRepository;

import java.util.List;

/**
 * Phân quyền theo path — docs/02-ky-thuat/authentication.md mục 2 và 5. Endpoint chỉ dành Admin còn phải
 * đánh thêm @PreAuthorize("hasRole('ADMIN')") ở controller (bật @EnableMethodSecurity).
 * Quy ước: thứ tự requestMatchers quan trọng — luật đứng trước thắng.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                            UserRepository userRepository, ObjectMapper objectMapper)
            throws Exception {
        JsonSecurityErrorHandler errorHandler = new JsonSecurityErrorHandler(objectMapper);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> { })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(auth -> auth
                        // Hạ tầng / tài liệu API / WebSocket (STOMP tự xác thực JWT khi CONNECT — N3-08)
                        .requestMatchers("/error", "/actuator/health",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/ws/**").permitAll()
                        // Auth: đăng xuất cần đăng nhập, còn lại công khai
                        .requestMatchers("/api/auth/logout").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        // VNPay IPN/return: công khai nhưng service vẫn bắt buộc kiểm chữ ký (BR-12)
                        .requestMatchers("/api/payments/vnpay/**").permitAll()
                        // Nội dung công khai (chỉ GET)
                        .requestMatchers(HttpMethod.GET,
                                "/api/categories", "/api/brands", "/api/search",
                                "/api/products/**", "/api/combos/**", "/api/articles/**", "/api/campaigns/**")
                        .permitAll()
                        // Nhân viên: EMPLOYEE hoặc ADMIN
                        .requestMatchers("/api/admin/**").hasAnyRole("EMPLOYEE", "ADMIN")
                        // Khách hàng: chỉ CUSTOMER
                        .requestMatchers("/api/cart/**", "/api/orders/**", "/api/reviews/**",
                                "/api/favorites/**", "/api/support/**", "/api/vouchers/**",
                                "/api/users/me/reviews", "/api/users/me/favorites").hasRole("CUSTOMER")
                        // Còn lại (vd. /api/users/me, /api/notifications, /api/auth/logout): mọi role đã đăng nhập
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository, errorHandler),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Chỉ cho phép origin frontend (app.cors.allowed-origins). Token nằm ở header nên không cần cookie. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
