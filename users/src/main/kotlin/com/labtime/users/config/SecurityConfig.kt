package com.labtime.users.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
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
 * Reglas de users:
 *  - POST /users/me, GET /users/me, PUT /users/me -> cualquier usuario autenticado
 *    (crea/lee/edita SU PROPIO perfil; el sub sale del JWT, nunca del body).
 *  - GET /users -> solo STAFF (listado completo).
 *
 * Mismo patron de conversion de rol que labtime: el mismo issuer de
 * Cognito, el mismo claim "cognito:groups" -> ROLE_STAFF/ROLE_REQUESTER.
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
            // Autorizacion por ROL: ahora vive en los controllers via @PreAuthorize
            // (Criterio 8), no aqui. Aqui solo se decide que necesita token.
            .authorizeHttpRequests { auth ->
                auth
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt -> jwt.jwtAuthenticationConverter(cognitoGroupsConverter()) }
            }
            .addFilterAfter(mdcSubFilter, BearerTokenAuthenticationFilter::class.java)

        return http.build()
    }

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
