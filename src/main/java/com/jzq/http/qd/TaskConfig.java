package com.jzq.http.qd;

public class TaskConfig {
    private String cookie;


    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    @Override
    public String toString() {
        return "TaskConfig{" +
                "cookie='" + cookie + '\'' +
                '}';
    }
}
