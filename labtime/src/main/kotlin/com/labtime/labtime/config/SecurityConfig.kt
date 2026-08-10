package com.labtime.labtime.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Reglas de labtime (rooms + time_slots + bookings + equipment_requests
 * fusionados en un solo microservicio, ver ADR en el README raiz):
 *
 *  - GET /rooms, GET /rooms/{id}, GET /rooms/{id}/slots/available, GET /equipment-catalog -> PUBLICO
 *  - Resto de /rooms y /slots (POST/PUT/DELETE) -> rol STAFF
 *  - POST /bookings, GET /bookings/me, PUT/DELETE /bookings/{id}, POST /equipment-requests
 *    -> cualquier usuario autenticado (REQUESTER o STAFF); la propiedad especifica
 *       del recurso se valida DENTRO del service (BookingService.findOwnedOrThrow),
 *       no aqui.
 *  - GET /bookings, PATCH /bookings/{id}/approve, PATCH /bookings/{id}/attended -> rol STAFF
 *  - GET /bookings/{id} -> cualquiera de los dos roles (el service decide si puede
 *    ver ESA reserva especifica).
 *
 * Sin token -> 401 (Spring Security al validar el JWT).
 * Token valido pero rol equivocado -> 403 (Spring Security via hasRole).
 * Token y rol correctos pero recurso ajeno -> 403 (lo lanza el service).
 *
 * El rol sale del claim "cognito:groups" del JWT y se traduce a un GrantedAuthority
 * "ROLE_STAFF" / "ROLE_REQUESTER" via el converter de abajo.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity, mdcSubFilter: MdcSubFilter): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            // Solo lo publico se decide aqui (GET de catalogo). El resto de la
            // autorizacion por ROL vive en los controllers via @PreAuthorize
            // (Criterio 8): mas cerca del endpoint que protege, mas facil de auditar.
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.GET, "/rooms/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/slots/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/equipment-catalog/**").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt -> jwt.jwtAuthenticationConverter(cognitoGroupsConverter()) }
            }
            // Se ejecuta justo despues de validar el JWT: mete el "sub" en el MDC para
            // que el patron de logback lo estampe en TODAS las lineas de este request,
            // sin tener que pasarlo a mano por cada service/controller.
            .addFilterAfter(mdcSubFilter, BearerTokenAuthenticationFilter::class.java)

        return http.build()
    }

    // Traduce el claim "cognito:groups": ["STAFF"] -> authority "ROLE_STAFF".
    private fun cognitoGroupsConverter(): Converter<Jwt, AbstractAuthenticationToken> {
        val delegate = JwtGrantedAuthoritiesConverter()
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter { jwt ->
            val groups: List<String> = jwt.getClaimAsStringList("cognito:groups") ?: emptyList()
            val fromGroups: Collection<GrantedAuthority> =
                groups.map { SimpleGrantedAuthority("ROLE_$it") }
            fromGroups + delegate.convert(jwt).orEmpty()
        }
        return converter
    }

    // Permite que la app movil en desarrollo (localhost:5173, Vite) o empaquetada
    // (capacitor://localhost) llame directo a este servicio sin pasar por nginx.
    private fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = listOf("http://localhost:5173", "capacitor://localhost")
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        config.allowedHeaders = listOf("Authorization", "Content-Type")
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
