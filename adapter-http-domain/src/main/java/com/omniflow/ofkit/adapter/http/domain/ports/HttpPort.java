package com.omniflow.ofkit.adapter.http.domain.ports;

import com.omniflow.ofkit.adapter.http.domain.model.HttpRequest;
import com.omniflow.ofkit.adapter.http.domain.model.HttpResponse;

public interface HttpPort {
    HttpResponse execute(HttpRequest request) throws Exception;
}

