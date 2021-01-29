package com.jzq.http.qd;

public enum QdStatusEnum {
    NEW(0),
    PRE_HEAT(1),
    RUNNING(2),
    SUCCESS(3),
    FAILED(4);

    int statusCode;

    QdStatusEnum(int statusCode) {
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
