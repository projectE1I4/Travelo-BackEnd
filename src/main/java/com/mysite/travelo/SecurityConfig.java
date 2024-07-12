package com.mysite.travelo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.mysite.travelo.yeon.user.JWTFilter;
import com.mysite.travelo.yeon.user.JWTUtil;
import com.mysite.travelo.yeon.user.LoginFilter;
import com.mysite.travelo.yeon.user.TokenBlacklistService;

import lombok.RequiredArgsConstructor;



@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

	private final AuthenticationConfiguration configuration;
    private final JWTUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
	
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {

        return configuration.getAuthenticationManager();
    }
    
	@Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
	        .csrf((auth) -> auth.disable())
	        .formLogin((auth) -> auth.disable())
	        .httpBasic((auth -> auth.disable()))
	        .authorizeHttpRequests((auth) -> auth
	                .requestMatchers("/**").permitAll()
	                .requestMatchers("/user/admin").hasRole("ADMIN")
	                .anyRequest().authenticated()
	        )
	        .sessionManagement((session) -> session
	                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
	        .addFilterAt(new LoginFilter(authenticationManager(configuration), jwtUtil), UsernamePasswordAuthenticationFilter.class)
	        .addFilterBefore(new JWTFilter(jwtUtil, tokenBlacklistService), LoginFilter.class); // 수정
	
	    return http.build();
    }   

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
	
}