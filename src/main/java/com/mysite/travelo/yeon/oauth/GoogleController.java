package com.mysite.travelo.yeon.oauth;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import com.mysite.travelo.yeon.user.AuthResponse;
import com.mysite.travelo.yeon.user.JWTUtil;
import com.mysite.travelo.yeon.user.SiteUser;
import com.mysite.travelo.yeon.user.TokenBlacklistService;
import com.mysite.travelo.yeon.user.UserService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class GoogleController {

	private final UserService userService;
	private final OAuthTokenService oAuthTokenService;
	private final TokenBlacklistService tokenBlacklistService;
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
	    params.add("client_id", clientId); 
	    params.add("client_secret", scecretKey); 
	    params.add("redirect_uri", "http://localhost:5173/travelo/googleCallback");
	    params.add("grant_type", "authorization_code");
	    params.add("access_type", "offline"); 

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
	            HttpMethod.GET,
	            userInfoRequest,
	            new ParameterizedTypeReference<Map<String, Object>>() {});

	    // 사용자 정보에서 이메일 추출
	    String email = (String) userInfoResponse.getBody().get("email");

	    // 사용자 정보로 회원 조회
	    SiteUser oldUser = userService.getUser(email);

	    if (oldUser != null && oldUser.getDelYn().equals("N") && oldUser.getUsername().equals(email) && oldUser.getOauthType() == null) {
	    	String error = "이메일이 중복되어 해당 social 계정으로 가입이 불가합니다. 기존에 가입된 이메일 계정(" + email + ")으로 로그인해주세요.";
	    	
	    	Map<String, Object> map = new HashMap<>();
	    	map.put("username", oldUser.getUsername());
	    	map.put("error", error);
	    
	    	return new ResponseEntity<>(map, HttpStatus.BAD_REQUEST);
	    }

	    // 탈퇴한 회원인 경우 처리
	    if (oldUser != null && "Y".equals(oldUser.getDelYn())) {
	        return new ResponseEntity<>("탈퇴한 회원입니다", HttpStatus.BAD_REQUEST);
	    }
	    
	    // 회원이 존재하지 않으면 회원 가입 처리
	    if (oldUser == null) {
	        userService.joinGoogle(email);
	        oldUser = userService.getUser(email);
	    }

	    if (oldUser.getOauthType() != null && !oldUser.getOauthType().equals("google")) {
	    	String error = "사용자가 " + oldUser.getOauthType() + " 소셜 로그인을 이용해서 해당 이메일로 가입한 적이 있습니다.";
	    	
	    	Map<String, Object> map = new HashMap<>();
	    	map.put("username", oldUser.getUsername());
	    	map.put("error", error);
	    
	    	return new ResponseEntity<>(map, HttpStatus.BAD_REQUEST);
	    }
	    
	    OAuthToken oAuthToken = oAuthTokenService.getToken(oldUser);
	    
	    Map<String, Object> map = new HashMap<>();
    	map.put("accessToken", accessToken);
    	map.put("user", oldUser);
    	
	    // 토큰이 저장되어 있는 경우 기존 걸 수정
	    if (oAuthToken != null) {
	    	oAuthTokenService.modifyToken(map);
	    } else {
	    	oAuthTokenService.saveToken(map);
	    }

	    // JWT 토큰 생성 및 반환
	    String jwtToken = jwtUtil.createJwt(oldUser.getUsername(), oldUser.getRole().toString(), 1000 * 60 * 60L);
	    String refreshToken = jwtUtil.generateRefreshToken(oldUser.getUsername(), oldUser.getRole().toString(), 1000 * 60 * 60 * 24 * 7);

	    AuthResponse authResponse = new AuthResponse(jwtToken, refreshToken);

	    return ResponseEntity.ok().body(authResponse);
	}
	
	@GetMapping("/user/googleUnlink")
	public ResponseEntity<String> googleUnlink(Authentication auth, @RequestHeader("Authorization") String accessToken) {

		SiteUser user = userService.getLoginUserByUsername(auth.getName());
		OAuthToken token = oAuthTokenService.getToken(user);
		
    	RestTemplate restTemplate = new RestTemplate();

	    // 헤더
	    HttpHeaders headers = new HttpHeaders();
	    headers.add("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
	    
	    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("token", token.getAccessToken());
	    
	    HttpEntity<MultiValueMap<String, String>> googleInfo = new HttpEntity<>(params, headers);
	    ResponseEntity<String> response = restTemplate.exchange(
	            "https://oauth2.googleapis.com/revoke",
	            HttpMethod.POST,
	            googleInfo,
	            String.class);
	    
	    oAuthTokenService.deleteToken(user);
	    
	    userService.resign(user);
        
        if (accessToken.startsWith("Bearer ")) {
        	accessToken = accessToken.substring(7);
        }
        
        tokenBlacklistService.addToken(accessToken);

	    return response;
	}
	
	@GetMapping("/travelo/integratedGoogle")
	public ResponseEntity<AuthResponse> integratedGoogle(@RequestParam("code") String code) {
		
		// RestTemplate 생성
	    RestTemplate restTemplate = new RestTemplate();

	    // 헤더 설정
	    HttpHeaders headers1 = new HttpHeaders();
	    headers1.add("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");

	    // OAuth 토큰 요청 파라미터 설정
	    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
	    params.add("code", code);
	    params.add("client_id", clientId); 
	    params.add("client_secret", scecretKey); 
	    params.add("redirect_uri", "http://localhost:5173/travelo/integratedGoogle");
	    params.add("grant_type", "authorization_code");
	    params.add("access_type", "offline"); 

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
	            HttpMethod.GET,
	            userInfoRequest,
	            new ParameterizedTypeReference<Map<String, Object>>() {});

	    // 사용자 정보에서 이메일 추출
	    String username = (String) userInfoResponse.getBody().get("email");
	    
	    SiteUser user = userService.getUser(username);

		Map<String, String> map = new HashMap<>();
		map.put("oauthType", "google");
		
    	userService.modifyOauth(map, user);
	    
	    OAuthToken oAuthToken = oAuthTokenService.getToken(user);
	    
	    Map<String, Object> token = new HashMap<>();
	    token.put("accessToken", accessToken);
	    token.put("user", user);
    	
	    // 토큰이 저장되어 있는 경우 기존 걸 수정
	    if (oAuthToken != null) {
	    	oAuthTokenService.modifyToken(token);
	    } else {
	    	oAuthTokenService.saveToken(token);
	    }
	    
    	accessToken = jwtUtil.createJwt(user.getUsername(), user.getRole().toString(), 1000 * 60 * 60L);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername(), user.getRole().toString(), 1000 * 60 * 60 * 24 * 7);
	    
        AuthResponse authResponse = new AuthResponse(accessToken, refreshToken);

        return ResponseEntity.ok().body(authResponse);
	}
	
}
