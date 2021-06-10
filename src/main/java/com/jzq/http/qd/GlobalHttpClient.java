package com.jzq.http.qd;


import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;

public class GlobalHttpClient {
    public static final HttpClient client = HttpClients.createDefault();
}
