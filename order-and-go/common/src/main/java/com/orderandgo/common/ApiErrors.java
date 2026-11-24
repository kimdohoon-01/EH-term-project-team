package com.orderandgo.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import static com.orderandgo.common.Contracts.*;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> business(ApiException e) {
        return ResponseEntity.status(e.status).body(new ApiError(e.code,e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
        IllegalArgumentException.class, MissingRequestHeaderException.class})
    ResponseEntity<ApiError> invalid(Exception e) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_INPUT","입력값을 확인해 주세요."));
    }
    @ExceptionHandler(RestClientException.class)
    ResponseEntity<ApiError> unavailable(RestClientException e) {
        if(e instanceof RestClientResponseException response && response.getStatusCode().is4xxClientError()) {
            try {
                ApiError body=response.getResponseBodyAs(ApiError.class);
                if(body!=null) return ResponseEntity.status(response.getStatusCode()).body(body);
            } catch(Exception ignored) { }
        }
        return ResponseEntity.status(503).body(new ApiError("SERVICE_UNAVAILABLE","연결할 서비스가 응답하지 않습니다. 잠시 후 다시 시도해 주세요."));
    }
}
