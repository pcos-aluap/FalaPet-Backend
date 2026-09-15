package com.falapet.shared.contract.pagination;

import java.util.List;

import com.falapet.shared.contract.http.ApiFieldError;
import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;

public record CursorPageRequest(String cursor, int limit) {

	public static final int DEFAULT_LIMIT = 30;
	public static final int MIN_LIMIT = 1;
	public static final int MAX_LIMIT = 100;

	public static CursorPageRequest of(String cursor, Integer limit) {
		int resolvedLimit = limit == null ? DEFAULT_LIMIT : limit;
		if (resolvedLimit < MIN_LIMIT || resolvedLimit > MAX_LIMIT) {
			throw new ContractException(
				ErrorCode.VALIDATION_ERROR,
				List.of(new ApiFieldError("limit", ErrorCode.VALIDATION_ERROR.name())),
				null);
		}
		return new CursorPageRequest(cursor, resolvedLimit);
	}
}
