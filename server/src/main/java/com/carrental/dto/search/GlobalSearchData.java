package com.carrental.dto.search;

import java.util.List;

public record GlobalSearchData(
        String query,
        List<SearchResultDto> results
) {
}
