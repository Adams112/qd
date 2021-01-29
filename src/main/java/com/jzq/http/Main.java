package com.jzq.http;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.BufferedInputStream;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet get = new HttpGet("https://www.sh.msa.gov.cn/zwzx/applyVtsDeclare1/isAllowToApply?shipType=%E8%B6%85%E8%A7%84%E8%8C%83%E8%88%B9");
        get.setConfig(
                RequestConfig.custom()
                        .setConnectTimeout(10000)
                        .build()
        );
        get.setHeader("Cookie", "JSESSIONID=85C2E7520641C634444F1F2E90AF8F37; isRead=; type=; id=659; phone=15051518830");

        for (int i = 0; i < 10; i++) {
            long start = System.currentTimeMillis();
            BufferedInputStream bufferedInputStream = new BufferedInputStream(client.execute(get).getEntity().getContent());
            int len;
            byte[] data = new byte[1024];
            while ((len = bufferedInputStream.read(data)) != -1) {
                System.out.print(new String(data, 0, len));
            }
            System.out.println();
            System.out.println("index: " + i + ", time: " + (System.currentTimeMillis() - start));
        }
    }
}
