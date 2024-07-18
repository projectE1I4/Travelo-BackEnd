package com.mysite.travelo.yeon.oauth;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import com.mysite.travelo.yeon.user.AuthResponse;
import com.mysite.travelo.yeon.user.JWTUtil;
import com.mysite.travelo.yeon.user.SiteUser;
import com.mysite.travelo.yeon.user.UserService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class GoogleController {

	private final UserService userService;
	private final JWTUtil jwtUtil;
	
	@Value("${GOOGLE_CLIENT_ID}")
	private String clientId;
	
	@Value("${GOOGLE_SECRET_KEY}")
	private String scecretKey;
	
	@GetMapping("/travelo/googleCallback")
	public ResponseEntity<?> googleCallback(@RequestParam("code") String code) {
		
		// RestTemplate 생성
	    RestTemplate restTemplate = new RestTemplate();

	    // 헤더 설정
	    HttpHeaders headers1 = new HttpHeaders();
	    headers1.add("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
	    
	    // OAuth 토큰 요청 파라미터 설정
	    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
	    params.add("code", code);
	    params.add("client_id", clientId); // Google 개발자 콘솔에서 발급한 클라이언트 ID 입력
	    params.add("client_secret", scecretKey); // Google 개발자 콘솔에서 발급한 클라이언트 시크릿 입력
	    params.add("redirect_uri", "http://localhost:8080/travelo/googleCallback");
	    params.add("grant_type", "authorization_code");

	    // 헤더 + 바디 결합
	    HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers1);
	    
	    // OAuth 토큰 요청
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "https://oauth2.googleapis.com/token",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Map<String, Object>>() {});

	    // 액세스 토큰
	    String accessToken = (String) response.getBody().get("access_token");

	    // 사용자 정보 요청
	    HttpHeaders headers = new HttpHeaders();
	    headers.setBearerAuth(accessToken);
	    HttpEntity<?> userInfoRequest = new HttpEntity<>(headers);

	    ResponseEntity<Map<String, Object>> userInfoResponse = restTemplate.exchange(
	            "https://www.googleapis.com/oauth2/v3/userinfo",
	            HttpMethod.POST,
	            userInfoRequest,
	            new ParameterizedTypeReference<Map<String, Object>>() {});

	    // 사용자 정보에서 이메일 추출
	    String email = (String) userInfoResponse.getBody().get("email");

	    // 사용자 정보로 회원 조회
	    SiteUser oldUser = userService.getUser(email);

	    // 회원이 존재하지 않으면 회원 가입 처리
	    if (oldUser == null) {
	        userService.joinGoogle(email);
	        oldUser = userService.getUser(email);
	    }

	    // 탈퇴한 회원인 경우 처리
	    if ("Y".equals(oldUser.getDelYn())) {
	        return new ResponseEntity<>("탈퇴한 회원입니다", HttpStatus.BAD_REQUEST);
	    }

	    // JWT 토큰 생성 및 반환
	    String jwtToken = jwtUtil.createJwt(oldUser.getUsername(), oldUser.getRole().toString(), 1000 * 60 * 60L);
	    String refreshToken = jwtUtil.generateRefreshToken(oldUser.getUsername(), oldUser.getRole().toString(), 1000 * 60 * 60 * 24 * 7);

	    AuthResponse authResponse = new AuthResponse(jwtToken, refreshToken);

	    return ResponseEntity.ok().body(authResponse);
	}
	
}
