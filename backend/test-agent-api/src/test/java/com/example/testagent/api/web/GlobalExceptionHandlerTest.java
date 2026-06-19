package com.example.testagent.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.testagent.common.api.ApiErrorResponse;
import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.observability.TraceConstants;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

class GlobalExceptionHandlerTest {

    @Test
    void handlerConvertsPlatformExceptionToUnifiedErrorResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/runs/run_1"));
        exchange.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, "trace_1234567890abcdef");

        ResponseEntity<ApiErrorResponse> response = handler.handlePlatformException(
                new PlatformException(ErrorCode.NOT_FOUND, "Run 不存在"),
                exchange);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Run 不存在");
        assertThat(response.getBody().traceId()).isEqualTo("trace_1234567890abcdef");
    }

    @Test
    void handlerConvertsValidationExceptionToUnifiedErrorResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/runs"));
        exchange.getAttributes().put(TraceConstants.TRACE_ID_ATTRIBUTE, "trace_1234567890abcdef");

        ResponseEntity<ApiErrorResponse> response = handler.handleValidationException(
                new ServerWebInputException("bad request"),
                exchange);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().traceId()).isEqualTo("trace_1234567890abcdef");
    }
}
