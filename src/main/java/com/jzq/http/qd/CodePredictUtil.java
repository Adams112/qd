package com.jzq.http.qd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class CodePredictUtil {
    public static final Logger logger = LoggerFactory.getLogger(CodePredictUtil.class);
    private static final AtomicInteger picIndex = new AtomicInteger(0);

    private final String tempFilePrefix = "/root/temp/pics/"
            + new SimpleDateFormat("yyyyMMdd").format(new Date()) + "/";

    public CodePredictUtil() {
        if (!new File(tempFilePrefix).exists()) {
            boolean mkdirs = new File(tempFilePrefix).mkdirs();
            if (!mkdirs) {
                throw new RuntimeException("mkdir error");
            }
        }
    }

    public String getCode(QdTask qdTask, String cookie) {
        String image = null;
        int n = 3;
        while (n-- > 0 && image == null) {
            try {
                image = getImage(qdTask, cookie);
            } catch (Throwable ignore) {
                try {
                    Thread.sleep(50);
                } catch (Throwable e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
        if (image == null) {
            throw new RuntimeException("get Image error, id: " + qdTask.getId());
        }

        String code = null;
        n = 3;
        while (n-- > 0 && code == null) {
            try {
                code = getValidationCode(qdTask, image);
            } catch (Throwable ignore) {
                try {
                    Thread.sleep(50);
                } catch (Throwable ignore2) {
                }
            }
        }
        if (code == null) {
            throw new RuntimeException("get code error, id: " + qdTask.getId());
        }

        return code;
    }

    private String getImage(QdTask qdTask, String cookie) throws IOException {
        long start = System.currentTimeMillis();
        String tempFileName = tempFilePrefix + picIndex.getAndIncrement() + ".jpg";
        try {
            HttpGet getImageMethod = createGetImageMethod(cookie);
            HttpResponse response = GlobalHttpClient.client.execute(getImageMethod);
            byte[] bytes = EntityUtils.toByteArray(response.getEntity());
            FileOutputStream outputStream = new FileOutputStream(tempFileName);
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.close();
            logger.info("getImage success, fileName: {}, id: {}, timeUse: {}ms",
                    tempFileName, qdTask.getId(), (System.currentTimeMillis() - start));
            return tempFileName;
        } catch (Throwable t) {
            logger.info("getImage failed, fileName: {}, id: {}, timeUse: {}, message: {}ms",
                    tempFileName, qdTask.getId(), (System.currentTimeMillis() - start), t.getMessage());
            throw t;
        }
    }

    private String getValidationCode(QdTask qdTask, String tempFileName) throws IOException {
        long start = System.currentTimeMillis();
        try {
            HttpPost getValidationCodeMethod = createGetValidationCodeMethod(tempFileName);
            HttpResponse response = GlobalHttpClient.client.execute(getValidationCodeMethod);
            String content = EntityUtils.toString(response.getEntity());
            String result = JSON.parseObject(content).getString("result");

            File f = new File(tempFileName);
            int n = 1;
            File newFile = new File(tempFilePrefix + "result-" + result + ".jpg");
            while (newFile.exists()) {
                newFile = new File(tempFilePrefix + "result-" + result + "-" + n + ".jpg");
                n++;
            }
            f.renameTo(newFile);
            logger.info("recognize pic {} finished, result: {}, id: {}, timeUse: {}ms",
                    tempFileName, result, qdTask.getId(), (System.currentTimeMillis() - start));
            return result;
        } catch (Throwable t) {
            logger.info("recognize pic {} finished, message: {}, id: {}, timeUse: {}ms",
                    tempFileName, t.getMessage(), qdTask.getId(), (System.currentTimeMillis() - start));
            throw t;
        }

    }


    private HttpGet createGetImageMethod(String cookie) {
        HttpGet httpGet = new HttpGet("https://www.sh.msa.gov.cn/zwzx/views/image.jsp");
        httpGet.setConfig(
                RequestConfig.custom()
                        .setSocketTimeout(3000)
                        .setConnectTimeout(3000)
                        .build()
        );
        httpGet.setHeader("Cookie", cookie);
        return httpGet;
    }

    private HttpPost createGetValidationCodeMethod(String tempFileName) {
        HttpPost httpPost = new HttpPost("http://localhost:8050/captcharecognize");
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(3000)
                .setConnectTimeout(3000)
                .build();
        httpPost.setConfig(requestConfig);
        httpPost.setHeader("Content-Type","application/json;charset=utf-8");
        JSONObject object = new JSONObject();
        object.put("path", tempFileName);
        httpPost.setEntity(new StringEntity(object.toJSONString(), "utf-8"));
        return httpPost;
    }
}
