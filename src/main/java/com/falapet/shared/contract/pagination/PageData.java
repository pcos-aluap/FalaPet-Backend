package com.falapet.shared.contract.pagination;

import java.util.List;

public record PageData<T>(List<T> items, PageMetadata page) {

	public PageData {
		items = List.copyOf(items);
	}
}
