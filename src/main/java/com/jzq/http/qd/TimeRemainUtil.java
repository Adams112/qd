package com.jzq.http.qd;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class TimeRemainUtil {
    private static final Logger logger = LoggerFactory.getLogger(TimeRemainUtil.class);

    private final QdTask qdTask;
    private final HttpClient client;

    private static long expect;
    private static String predictDate = null;

    public TimeRemainUtil(QdTask qdTask, HttpClient client) {
        this.qdTask = qdTask;
        this.client = client;
    }

    /**
     * 获取剩余时间，返回时间，或者long.MAX_VALUE表示获取失败
     * @return
     */
    public long getExpectTime() {
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        if (predictDate == null || !predictDate.equals(today)) {
            logger.info("predict expect time");
            long expect = TimeRemainUtil.expect = Long.MAX_VALUE;
            for  (String cookie : qdTask.getConfig().getCookie()) {
                try {
                    long start = System.currentTimeMillis();
                    long time = timeRemainInternal(cookie);
                    long end = System.currentTimeMillis();
                    if (time != Long.MAX_VALUE) {
                        if (time < 0) {
                            // time < 0 表示已经可以提交，expect返回当前时间前一秒
                            expect = System.currentTimeMillis() - 1000;
                        } else {
                            expect = (start + end) / 2 + time * 1000;
                        }
                        TimeRemainUtil.expect = expect;
                        TimeRemainUtil.predictDate = today;
                    }
                } catch (Throwable ignore) {
                }
                if (expect != Long.MAX_VALUE) {
                    break;
                }
            }
        } else {
            logger.info("time predicted");
        }
        logger.info("getExpectTime return: {}", expect);
        return expect;
    }


    private long timeRemainInternal(String cookie) throws IOException {
        logger.info("start to get remain time, id: {}", qdTask.getId());
        long start = System.currentTimeMillis();
        HttpGet getTimeMethod = createGetTimeMethod(cookie);
        HttpResponse execute = client.execute(getTimeMethod);
        String time = EntityUtils.toString(execute.getEntity());
        long end = System.currentTimeMillis();
        try {
            long result = Long.parseLong(time);
            logger.info("get remain time finished, result: {}, time used: {}, id: {}", result, (end - start), qdTask.getId());
            return result;
        } catch (Throwable ignore) {
        }

        logger.error("parse time error");
        return Long.MAX_VALUE;
    }

    private HttpGet createGetTimeMethod(String cookie) {
        HttpGet httpGet = new HttpGet("https://www.sh.msa.gov.cn/zwzx/applyVtsDeclare1/getSeconds");
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(3000)
                .setConnectTimeout(3000)
                .build();
        httpGet.setConfig(requestConfig);
        httpGet.setHeader("Cookie", cookie);
        return httpGet;
    }
}
