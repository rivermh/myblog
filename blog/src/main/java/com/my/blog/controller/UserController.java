package com.my.blog.controller;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.my.blog.config.auth.PrincipalDetail;
import com.my.blog.model.KakaoProfile;
import com.my.blog.model.OAuthToken;
import com.my.blog.model.User;
import com.my.blog.service.UserService;

import jakarta.servlet.http.HttpSession;

// 인증이 안된 사용자들이 출입할 수 있는 경로를 /auth/** 허용
// 그냥 주소가 / 이면 index.jsp 허용
// staic 이하에 있는 /js/**, /css/**, /image/**
// 카카오가 준 code를 받는다 > code로 카카오 사용자 정보를 가져온다 > 그 사용자로 사이트에 로그인시킨다
@Controller
public class UserController {
 
    @Value("${minho.key}")
    private String minhoKey;
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private UserService userService;

    @Autowired
    private HttpSession session;
    
    @GetMapping("/auth/joinForm")
    public String joinForm() {
        return "user/joinForm";
    }

    @GetMapping("/auth/loginForm")
    public String loginForm() {
        return "user/loginForm";
    }
    
    // 카카오는 GET 요청 code는 카카오가 준 인가코드 이걸로 Access Token 교환
    @GetMapping("/auth/kakao/callback")
    public String kakaoCallback(String code) {
        
    	System.out.println("== kakao callback 진입. code=" + code);
    	
        RestTemplate rt = new RestTemplate(); // 인가 코드(토큰 요청) Http 통신용 객체
        
        HttpHeaders headers = new HttpHeaders(); // 카카오 API 요구 사항 form 방식
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>(); // OAuth2 정석 스펙
        params.add("grant_type", "authorization_code");
        params.add("client_id", "daa8bb6ae719bc137adc27fb3186f50d");
        params.add("redirect_uri", "http://localhost:8080/auth/kakao/callback");
        params.add("code", code);
        
        HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest = new HttpEntity<>(params, headers); // header + body 합친 요청 객체
        
        ResponseEntity<String> response = rt.exchange(
                "https://kauth.kakao.com/oauth/token",
                HttpMethod.POST,
                kakaoTokenRequest,
                String.class
        ); // JSOT 코드

        ObjectMapper objectMapper = new ObjectMapper();
        OAuthToken oauthToken = null;
        try {
            oauthToken = objectMapper.readValue(response.getBody(), OAuthToken.class); // DTO 역할, Jackson이 JSON > Java 객체 변환
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        
        RestTemplate rt2 = new RestTemplate();
        
        HttpHeaders headers2 = new HttpHeaders();
        headers2.add("Authorization", "Bearer " + oauthToken.getAccess_token()); // OAuth 핵심 : Bearer 토큰 방식
        headers2.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");
        
        HttpEntity<MultiValueMap<String, String>> kakaoProfileRequest2 = new HttpEntity<>(headers2);
        
        ResponseEntity<String> response2 = rt2.exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.POST,
                kakaoProfileRequest2,
                String.class
        ); // 여기서 카카오 사용자 정보 JSON 받음
        System.out.println(response2.getBody());
        
     ObjectMapper objectMapper2 = new ObjectMapper();
        KakaoProfile kakaoProfile = null;
        try {
            kakaoProfile = objectMapper2.readValue(response2.getBody(), KakaoProfile.class); // id email profile 정보
        } catch (JsonMappingException e) {
            e.printStackTrace(); 
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        
        System.out.println("카카오 아이디(번호) :"+kakaoProfile.getId());
        System.out.println("카카오 아이디(번호) :"+kakaoProfile.getKakao_account().getEmail());
        
        System.out.println("블로그 유저네임 : " +kakaoProfile.getKakao_account().getEmail()+"_"+kakaoProfile.getId());
        System.out.println("블로그 이메일 :"+kakaoProfile.getKakao_account().getEmail());
        UUID garbagePassword = UUID.randomUUID();
        System.out.println("블로그서버 패스워드 : "+minhoKey);
   
        User kakaoUser = User.builder()
                .username(kakaoProfile.getKakao_account().getEmail() + "_" + kakaoProfile.getId())
                .password(minhoKey)
                .email(kakaoProfile.getKakao_account().getEmail())
                .oauth("kakao")
                .build(); // username 충돌 방지, password는 실제 로그인용 아님 oauth 필드로 일반 로그인과 구분

        // 가입자 혹은 비가입자 체크 해서 처리
        User originUser = userService.회원찾기(kakaoUser.getUsername());

        if (originUser.getUsername() == null) {
            System.out.println("기존 회원이 아니기에 자동 회원가입을 진행합니다");
            userService.회원가입(kakaoUser);
        }

        // originUser 를 다시 로드 (회원가입했으면 originUser 가 null일 수 있으므로)
        originUser = userService.회원찾기(kakaoUser.getUsername());

        System.out.println("자동 로그인을 진행합니다.");
        
        /* 여기 핵심
        1. AuthenticationManage 2. PrincipalDetatilService.loadUserByUsernname 3. DB 조회
        4. PassworrdEncoder 비교 5. 인증성공 */
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        originUser.getUsername(),
                        minhoKey   // kakaoUser의 패스워드(우리 서버의 고정키)
                )
        );

        // SecurityContext 에 저장
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(authentication);

        // 세션에 SecurityContext 넣기
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                securityContext
        );

        return "redirect:/";
    }
     	@GetMapping("/user/updateForm")
     	public String updateForm() {	
     	  return "user/updateForm";
     	}
     }