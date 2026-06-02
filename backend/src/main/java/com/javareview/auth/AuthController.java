package com.javareview.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.javareview.auth.AuthDtos.BootstrapAdminRequest;
import com.javareview.auth.AuthDtos.BootstrapStatusResponse;
import com.javareview.auth.AuthDtos.CurrentUserResponse;
import com.javareview.auth.AuthDtos.LoginRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final AuthenticationManager authenticationManager;
	private final RememberMeServices rememberMeServices;

	public AuthController(
			AuthService authService,
			AuthenticationManager authenticationManager,
			RememberMeServices rememberMeServices) {
		this.authService = authService;
		this.authenticationManager = authenticationManager;
		this.rememberMeServices = rememberMeServices;
	}

	@GetMapping("/bootstrap-status")
	public BootstrapStatusResponse bootstrapStatus() {
		return new BootstrapStatusResponse(authService.isInitialized());
	}

	@PostMapping("/bootstrap-admin")
	@ResponseStatus(HttpStatus.CREATED)
	public CurrentUserResponse bootstrapAdmin(@Valid @RequestBody BootstrapAdminRequest request) {
		return authService.bootstrapAdmin(request);
	}

	@PostMapping("/login")
	public CurrentUserResponse login(
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		User user = authService.requireUserByIdentifier(request.identifier());
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(user.getUsername(), request.password()));

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		servletRequest.getSession(true)
				.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
		if (request.rememberMe()) {
			rememberMeServices.loginSuccess(servletRequest, servletResponse, authentication);
		}

		return CurrentUserResponse.from(user);
	}

	@GetMapping("/me")
	public CurrentUserResponse me(@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {
		if (principal == null) {
			throw new BadCredentialsException("Not authenticated.");
		}
		return CurrentUserResponse.from(authService.requireUserByIdentifier(principal.getUsername()));
	}
}
