package com.beachassistant.source.contract;

import com.beachassistant.common.enums.SourceType;

public interface SourceAdapter<T> {

    SourceType sourceType();

    FetchResult<T> fetch(SourceRequest request);
}
