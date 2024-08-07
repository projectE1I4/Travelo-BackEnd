package com.mysite.travelo.yeon.user;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mysite.travelo.gil.review.ReviewService;
import com.mysite.travelo.yeon.mail.MailService;
import com.mysite.travelo.yeon.mail.VerifyCodeDto;

import jakarta.servlet.http.HttpSession;

import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	private final ReviewService reviewService;
	private final JWTUtil jwtUtil;
	private final MailService mailService;
	private final TokenBlacklistService tokenBlacklistService;
	private final BCryptPasswordEncoder bCryptPasswordEncoder;
	
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w\\.-]+@[\\w\\.-]+\\.[a-z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*\\d)[a-z\\d]{8,20}$");
	
	@PostMapping("/travelo/join")
    public ResponseEntity<String> join(@RequestParam Map<String, String> map) {
            System.out.println("username" + map.get("username"));
            System.out.println("password" + map.get("password"));
            System.out.println("passwordCheck" + map.get("passwordCheck"));
            System.out.println("tel" + map.get("tel"));

		// Null 체크
        if (!StringUtils.hasText(map.get("username")) || !StringUtils.hasText(map.get("password")) ||
            !StringUtils.hasText(map.get("passwordCheck")) || !StringUtils.hasText(map.get("tel"))) {


        	return new ResponseEntity<>("모든 필드를 채워주세요", HttpStatus.BAD_REQUEST);
        }
		
        // 이메일 형식 체크
        if (!EMAIL_PATTERN.matcher(map.get("username")).matches()) {
        	return new ResponseEntity<>("이메일 형식이 올바르지 않습니다", HttpStatus.BAD_REQUEST);
        }
        
        // 이메일 중복 여부 확인
        if (userService.checkUsernameDuplicate(map.get("username"))) {
        	return new ResponseEntity<>("이미 가입된 이메일입니다", HttpStatus.BAD_REQUEST);
        }
        
        // 비밀번호 형식 체크
        if (!PASSWORD_PATTERN.matcher(map.get("password")).matches()) {
        	return new ResponseEntity<>("비밀번호는 소문자 영문과 숫자를 포함하여 8자 이상 20자 이하여야 합니다", HttpStatus.BAD_REQUEST);
        }

        // 비밀번호 = 비밀번호 체크 여부 확인
        if (!map.get("password").equals(map.get("passwordCheck"))) {
        	return new ResponseEntity<>("비밀번호가 일치하지 않습니다", HttpStatus.BAD_REQUEST);
        }

        userService.joinMail(map);
        
        try {
			mailService.sendJoinMessage(map.get("username"));
		} catch (Exception e) {
			e.printStackTrace();
		}

        return ResponseEntity.ok("가입 되었습니다");
    }
	
	@PostMapping("/travelo/mailConfirm")
	public ResponseEntity<?> mailConfirm(HttpSession session, @RequestParam(value = "username") String username) throws Exception{
        String code = mailService.sendConfirmMessage(username);
        session.setAttribute("username", username);
        session.setAttribute("code", code);

        return ResponseEntity.ok(code);
    }
	
	@PostMapping("/travelo/verifyCode")
	public ResponseEntity<?> verifyCode(HttpSession session, @RequestBody VerifyCodeDto verifyCodeDto) throws Exception{
		boolean result=false;
		
        if(session.getAttribute("username").equals(verifyCodeDto.getUsername()) && session.getAttribute("code").equals(verifyCodeDto.getVerifyCode())){
            result = true;
            session.removeAttribute("code");
        }
        
        return ResponseEntity.ok(result);
    }
	
	@PostMapping("/travelo/login")
	public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest){

		SiteUser user = userService.login(loginRequest);

		if (user == null) {
            return new ResponseEntity<>("이메일 또는 비밀번호가 일치하지 않습니다", HttpStatus.BAD_REQUEST);
        }

		if (reviewService.getBlindReview(user).size() >= 5) {
			return new ResponseEntity<>("지속된 악성 후기로 관리자 측에서 탈퇴 처리한 회원입니다.", HttpStatus.BAD_REQUEST);
		}

		if ("Y".equals(user.getDelYn())) {
			return new ResponseEntity<>("탈퇴한 회원입니다.", HttpStatus.BAD_REQUEST);
		}
		
        String accessToken = jwtUtil.createJwt(user.getUsername(), user.getRole().toString(), 1000 * 60 * 60L);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername(), user.getRole().toString(), 1000 * 60 * 60 * 24 * 7);
        
        AuthResponse authResponse = new AuthResponse(accessToken, refreshToken);

        return ResponseEntity.ok().body(authResponse);
    }
	
	@PreAuthorize("isAuthenticated()")
    @PostMapping("/user/refreshToken")
    public ResponseEntity<?> refreshToken(@RequestHeader("Authorization") String refreshToken) {

        if (refreshToken.startsWith("Bearer ")) {
            refreshToken = refreshToken.substring(7);
        }

        if (jwtUtil.isExpired(refreshToken)) {
            throw new RuntimeException("Refresh token이 만료되었습니다");
        }

        String username = jwtUtil.getUsername(refreshToken);
        SiteUser user = userService.getLoginUserByUsername(username);

        String accessToken = jwtUtil.createJwt(user.getUsername(), user.getRole().toString(), 1000 * 60 * 60L);

        AuthResponse authResponse = new AuthResponse(accessToken, refreshToken);

        return ResponseEntity.ok().body(authResponse);
    }

    // 토큰 값 받아와서 사용자 정보 추출
	@PreAuthorize("isAuthenticated()")
	@GetMapping("/user/info")
    public String memberInfo(Authentication auth) {
		SiteUser loginUser = userService.getLoginUserByUsername(auth.getName());

        return "email : " + loginUser.getUsername() + "\nrole : " + loginUser.getRole();
    }
    
	@PreAuthorize("isAuthenticated()")
    @PostMapping("/user/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String accessToken) {
        if (accessToken.startsWith("Bearer ")) {
        	accessToken = accessToken.substring(7);
        }
        
        tokenBlacklistService.addToken(accessToken);
        
        return ResponseEntity.ok("로그아웃 되었습니다");
    }
	
	@PostMapping("/travelo/check")
    public ResponseEntity<String> checkUser(@RequestParam(value = "username") String username, HttpSession session) {
    	
    	// Null 체크
        if (!StringUtils.hasText(username)) {
            return new ResponseEntity<>("이메일을 입력해주세요", HttpStatus.BAD_REQUEST);
        }
    	
        SiteUser loginUser = userService.getUser(username);
        
        if (loginUser != null) {

        	session.setAttribute("username", loginUser.getUsername());
        	return ResponseEntity.ok("유효한 이메일입니다");
        }
        
        return new ResponseEntity<>("해당하는 정보가 없습니다", HttpStatus.NOT_FOUND);
    }
    
    @PostMapping("/travelo/resetPassword")
    public ResponseEntity<String> resetPassword(@RequestParam Map<String, String> map, HttpSession session) {
        
    	// Null 체크
        if (!StringUtils.hasText(map.get("password")) || !StringUtils.hasText(map.get("passwordCheck"))) {
        	return new ResponseEntity<>("모든 필드를 채워주세요", HttpStatus.BAD_REQUEST);
        }
    	
        String username = (String)session.getAttribute("username");
    	SiteUser loginUser = userService.getUser(username);
    	
    	if (username == null) {
            return new ResponseEntity<>("세션이 만료되었습니다. 다시 시도해주세요.", HttpStatus.UNAUTHORIZED);
        }
    	
    	// 비밀번호 형식 체크
        if (!PASSWORD_PATTERN.matcher(map.get("password")).matches()) {
        	return new ResponseEntity<>("비밀번호는 소문자 영문과 숫자를 포함하여 8자 이상 20자 이하여야 합니다", HttpStatus.BAD_REQUEST);
        }
    	
    	// 비밀번호 = 비밀번호 체크 여부 확인
        if (!map.get("password").equals(map.get("passwordCheck"))) {
        	return new ResponseEntity<>("비밀번호가 일치하지 않습니다", HttpStatus.BAD_REQUEST);
        }
        
        if (bCryptPasswordEncoder.matches(map.get("password"), loginUser.getPassword())) {
        	return new ResponseEntity<>("기존 비밀번호는 사용할 수 없습니다", HttpStatus.BAD_REQUEST);
        }
        
    	userService.resetPassword(map, loginUser);
    	session.invalidate();
    	
    	return ResponseEntity.ok("비밀번호 변경되었습니다");
    }
	
}