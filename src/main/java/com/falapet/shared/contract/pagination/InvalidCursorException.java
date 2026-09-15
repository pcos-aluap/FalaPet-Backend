package com.falapet.shared.contract.pagination;

import com.falapet.shared.contract.http.ContractException;
import com.falapet.shared.contract.http.ErrorCode;

public final class InvalidCursorException extends ContractException {

	public InvalidCursorException() {
		super(ErrorCode.INVALID_CURSOR);
	}
}
