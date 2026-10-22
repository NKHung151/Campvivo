package vn.campvivo.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TẠM THỜI (F-01): chỉ để mở Swagger UI khi chưa có security thật.
 * F-10 thay bằng SecurityFilterChain theo path trong docs/02-ky-thuat/authentication.md mục 5
 * (JWT filter, 401/403 JSON) — khi đó XOÁ file này.
 */
@Configuration
public class TemporarySecurityConfig {

    @Bean
    SecurityFilterChain temporaryFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                                "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
