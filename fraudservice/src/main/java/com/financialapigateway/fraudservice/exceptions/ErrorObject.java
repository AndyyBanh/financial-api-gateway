package com.financialapigateway.fraudservice.exceptions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
public class ErrorObject {
    private String message;
    private Integer statusCode;
    private Date timestamp;
}
