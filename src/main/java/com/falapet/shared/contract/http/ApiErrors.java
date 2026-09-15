package com.falapet.shared.contract.http;

import java.util.List;
import java.util.Map;

public final class ApiErrors {

	private static final String GENERIC_MESSAGE = "Não foi possível concluir a solicitação.";

	private ApiErrors() {
	}

	public static ApiErrorResponse of(ErrorCode code) {
		return of(code, null, null);
	}

	public static ApiErrorResponse of(
			ErrorCode code,
			List<ApiFieldError> fieldErrors,
			Map<String, Object> details) {
		return new ApiErrorResponse(new ApiError(
			code,
			message(code),
			code.retryable(),
			fieldErrors == null || fieldErrors.isEmpty() ? null : List.copyOf(fieldErrors),
			details == null || details.isEmpty() ? null : Map.copyOf(details)));
	}

	private static String message(ErrorCode code) {
		return switch (code) {
			case RESOURCE_NOT_FOUND -> "Recurso não encontrado.";
			case FORBIDDEN -> "Acesso não permitido.";
			case UNSUPPORTED_MEDIA_TYPE -> "Tipo de mídia não suportado.";
			case PAYLOAD_TOO_LARGE -> "O conteúdo enviado excede o limite permitido.";
			case CAPABILITY_NOT_AVAILABLE -> "Esta capacidade não está disponível.";
			case SERVICE_UNAVAILABLE -> "Serviço temporariamente indisponível.";
			default -> GENERIC_MESSAGE;
		};
	}
}
