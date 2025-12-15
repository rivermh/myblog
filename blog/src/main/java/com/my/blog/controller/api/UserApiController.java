package com.my.blog.controller.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.my.blog.config.auth.PrincipalDetail;
import com.my.blog.dto.ResponseDto;
import com.my.blog.model.User;
import com.my.blog.service.UserService;

@RestController
// 회원가입 회원수정 요청을 받아서 Service에 넘기고 JSON으로 결과만 돌려주는 역할
public class UserApiController {

	@Autowired
	private UserService userService;

	@PostMapping("/auth/joinProc")
	public ResponseDto<Integer> save(@RequestBody User user) { // username, password, email
		System.out.println("UserApiController : save 호출됨");
		userService.회원가입(user);
		return new ResponseDto<Integer>(HttpStatus.OK.value(), 1); // 자바오브젝트를 JSON으로 변환해서 리턴 (Jackson)
	}

	@PutMapping("/user")
	public ResponseDto<Integer> update(@RequestBody User user,
			@AuthenticationPrincipal PrincipalDetail principalDetail) { // key = value, x-www.form-urlencoded
		userService.회원수정(user);
		// 여기서는 트랜잭션이 종료되기 때문에 DB에 값은 변경이 됐음.
		// 하지만 세션값은 변경되지 않은 상태이기 때문에 우리가 직접 세션값을 변경해줄 것임.
		// 세션 등록

		// 세션 변경은 principalDetail 값만 변경하면 된다.
		principalDetail.setUser(user);

		return new ResponseDto<Integer>(HttpStatus.OK.value(), 1);
	}
}