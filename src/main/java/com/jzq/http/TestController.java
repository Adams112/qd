package com.jzq.http;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;

@Controller
@RequestMapping("/home")
public class TestController {
    private HttpClient clientRec = HttpClients.createDefault();
    @RequestMapping("/test")
    @ResponseBody
    public Object test(String method) throws IOException {
        long stamp1 = System.currentTimeMillis(), stamp2 = 0, stamp3 = 0;

        if (method.equals("1")) {
            HttpPost httpPost = new HttpPost("http://localhost:8050/captcharecognize");
            RequestConfig requestConfig = RequestConfig.custom()
                    .setSocketTimeout(3000)
                    .setConnectTimeout(3000)
                    .build();
            stamp2 = System.currentTimeMillis();
            httpPost.setConfig(requestConfig);
            httpPost.setHeader("Content-Type","application/json;charset=utf-8");
            JSONObject object = new JSONObject();
            object.put("path", "/root/pics/0176.jpg");
            httpPost.setEntity(new StringEntity(object.toJSONString(), "utf-8"));
            HttpResponse response = clientRec.execute(httpPost);
            String content = EntityUtils.toString(response.getEntity());
            stamp3 = System.currentTimeMillis();
            System.out.println(content);
        } else {
            HttpGet httpGet = new HttpGet("https://www.sh.msa.gov.cn/zwzx/applyVtsDeclare1/getSeconds");
            RequestConfig requestConfig = RequestConfig.custom()
                    .setSocketTimeout(3000)
                    .setConnectTimeout(3000)
                    .build();
            httpGet.setConfig(requestConfig);
            httpGet.setHeader("Cookie", "JSESSIONID=E6F6248B2D63D1A553BFC33BA2E1C1F7; type=; id=; phone=; _site_id_cookie=1; JSESSIONID=5eee9d7f-2595-419d-aea0-fea964fb1cac; clientlanguage=zh_CN; SF_cookie_1=84260979; Hm_lvt_ba12bfebfd95b229531c5712ef4bc097=1611382643; Hm_lpvt_ba12bfebfd95b229531c5712ef4bc097=1611382643");
            HttpResponse response = clientRec.execute(httpGet);
            String content = EntityUtils.toString(response.getEntity());
            stamp3 = System.currentTimeMillis();
            System.out.println(content);
        }

        System.out.println("init httpPost: " + (stamp2 - stamp1));
        System.out.println("execute: " + (stamp3 - stamp2));
        System.out.println("total: " + (stamp3 - stamp1));
        return "OK";
    }
}
