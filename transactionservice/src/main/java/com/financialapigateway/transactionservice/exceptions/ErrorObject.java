package com.financialapigateway.transactionservice.exceptions;

import lombok.Data;

import java.util.Date;

@Data
public class ErrorObject {
    private String message;
    private Integer statusCode;
    private Date timestamp;
}
