package com.polygraph.erp.shared.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(String mensaje, HttpStatus status) {
        super(mensaje);
        this.status = status;
    }
}
