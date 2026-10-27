package com.darkbit.bakerysales.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiMeController {

	@GetMapping("/api/me")
	public ApiMeResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
		return new ApiMeResponse(jwt.getSubject(), jwt.getClaimAsString("email"));
	}

	public record ApiMeResponse(String id, String email) {
	}
}
