package com.falapet.auth.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.falapet.shared.contract.http.ApiMediaTypes;
import com.falapet.shared.contract.http.ApiResponse;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = ApiMediaTypes.APPLICATION_JSON_VALUE)
class AuthCapabilitiesController {

	@GetMapping("/capabilities")
	ApiResponse<AuthCapabilities> capabilities() {
		return ApiResponse.of(new AuthCapabilities(false, false, false));
	}

	record AuthCapabilities(boolean passwordLogin, boolean googleLogin, boolean passwordRecovery) {
	}
}
