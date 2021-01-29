package com.jzq.http.qd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class QdRunnable implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(QdRunnable.class);

    private static final int MAX_RETRY_COUNT = 5;
    private int retry = 0;
    private String code = null;
    private final QdTask qdTask;

    private final AtomicInteger picIndex = new AtomicInteger(0);
    private final String tempFilePrefix;
    private String tempFileName;

    private final HttpClient client = HttpClients.createDefault();

    private volatile long timeRemainMillis = Long.MAX_VALUE;
    private volatile long getTimeRemainMillisTimestamp = 0;
    private static final long stopGetRemainTimeThresholdMillis = 50;

    public QdRunnable(QdTask qdTask) {
        this.qdTask = qdTask;
        tempFilePrefix = "/root/temp/pics/" + qdTask.getId() + "/";
        if (!new File(tempFilePrefix).exists()) {
            boolean mkdirs = new File(tempFilePrefix).mkdirs();
            if (!mkdirs) {
                throw new RuntimeException("mkdir error");
            }
        }
    }

    @Override
    public void run() {
        qdTask.setThread(Thread.currentThread());
        boolean interrupted = false;
        try {
            while (!shouldBreak() && !(interrupted = Thread.interrupted())) {
                long estimatedTimeRemainMillis = 0;
                try {
                    estimatedTimeRemainMillis = timeRemainMillis();
                } catch (IOException e) {
                    // 时间获取出错，直接重试
                    logger.error("get time error, {}", e.getMessage(), e);
                    retry++;
                    continue;
                }

                long timeToSleep;
                if (estimatedTimeRemainMillis > 6000) {
                    timeToSleep = estimatedTimeRemainMillis - 6000;
                    logger.info("time remain {}ms, sleep for {}ms, id: {}", estimatedTimeRemainMillis,
                            timeToSleep, qdTask.getId());
                    sleep(timeToSleep);
                    continue;
                }

                if  (estimatedTimeRemainMillis > stopGetRemainTimeThresholdMillis) {
                    if (code == null) {
                        logger.info("time remain {}ms, pre get image and code, id: {}", estimatedTimeRemainMillis,
                                qdTask.getId());
                        try {
                            long start = System.currentTimeMillis();
                            getImage();
                            code = getValidationCode();
                            long end = System.currentTimeMillis();
                            estimatedTimeRemainMillis = estimatedTimeRemainMillis - (end - start);
                        } catch (Exception e) {
                            logger.error("pre get image and code error, retry, {}, id: {}", e.getMessage(), qdTask.getId());
                            // 图片预取出错，可重试
                            retry++;
                            continue;
                        }
                    }
                }

                if (estimatedTimeRemainMillis > 2000) {
                        sleep(estimatedTimeRemainMillis - 2000);
                } else if (estimatedTimeRemainMillis > stopGetRemainTimeThresholdMillis) {
                    logger.info("time remain {}ms, sleep for {}ms, id: {}", estimatedTimeRemainMillis,
                            estimatedTimeRemainMillis, qdTask.getId());
                    sleep(estimatedTimeRemainMillis);
                    runTask();
                } else {
                    runTask();
                }
            }
        } catch (Exception e) {
            logger.error("run error, {}", e.getMessage(), e);
            qdTask.setStatus(QdStatusEnum.FAILED);
        } finally {
            if (interrupted) {
                logger.info("interrupted, exit");
            }
            qdTask.setThread(null);
        }
        logger.info("result: {}, id: {}", qdTask.getStatus(), qdTask.getId());
    }

    private void runTask() {
        logger.info("time to start, id: {}", qdTask.getId());
        if (qdTask.getStatus() == QdStatusEnum.NEW) {
            qdTask.setStatus(QdStatusEnum.RUNNING);
            qdTask.setGmtModified(new Date());
        }

        long t1 = 0, t2 = 0, t3 = 0, t4 = 0;
        try {
            t1 = System.currentTimeMillis();
            if (code == null) {
                getImage();
            }
            t2 = System.currentTimeMillis();
            if (code == null) {
                code = getValidationCode();
            }
            t3 = System.currentTimeMillis();
            submit(code);
            code = null;
            t4 = System.currentTimeMillis();
        } catch (Exception e) {
            logger.error("qd throws exception, {}, id: {}", e.getMessage(), qdTask.getId(), e);
            qdTask.setMessage(e.getMessage());
            qdTask.setGmtModified(new Date());
        } finally {
            retry += 1;
            logger.info("getImageTime: {}, getValidationCodeTime: {}, submitTime: {}, success: {}, id: {}"
                    , (t2 - t1), (t3 - t2), (t4 - t3), qdTask.getStatus(), qdTask.getId());
        }
    }


    private long timeRemainMillis() throws IOException {
        long cur = System.currentTimeMillis();
        long thisTimeRemain = timeRemainMillis - (cur - getTimeRemainMillisTimestamp);

        if (thisTimeRemain > stopGetRemainTimeThresholdMillis) {
            long start = System.currentTimeMillis();
            thisTimeRemain = timeRemainMillisInternal(qdTask.getConfig().getCookie()) + 1;
            long end = System.currentTimeMillis();
            getTimeRemainMillisTimestamp = end;
            if (thisTimeRemain >= 0) {
                thisTimeRemain = thisTimeRemain * 1000 - (end - start) / 2;
            } else {
                thisTimeRemain = thisTimeRemain - (end - start) / 2;
            }
        }
        return thisTimeRemain;
    }

    private long timeRemainMillisInternal(String cookie) throws IOException {
        logger.info("start to get remain time, id: {}", qdTask.getId());
        long start = System.currentTimeMillis();
        HttpGet getTimeMethod = createGetTimeMethod(cookie);
        HttpResponse execute = client.execute(getTimeMethod);
        String time = EntityUtils.toString(execute.getEntity());
        long end = System.currentTimeMillis();
        logger.info("get remain time finished: {}, time used: {}, id: {}", time, (end - start), qdTask.getId());
        return Long.parseLong(time);
    }

    private void getImage() throws IOException {
        tempFileName = tempFilePrefix + picIndex.getAndIncrement() + ".jpg";
        logger.info("start to getImage, fileName: {}, id: {}", tempFileName, qdTask.getId());
        HttpGet getImageMethod = createGetImageMethod();
        HttpResponse response = client.execute(getImageMethod);
        byte[] bytes = EntityUtils.toByteArray(response.getEntity());
        FileOutputStream outputStream = new FileOutputStream(tempFileName);
        outputStream.write(bytes);
        outputStream.flush();
        outputStream.close();
        logger.info("getImage success, fileName: {}, id: {}", tempFileName, qdTask.getId());
    }

    private String getValidationCode() throws IOException {
        logger.info("start to recognize pic {}, id: {}", tempFileName, qdTask.getId());
        HttpPost getValidationCodeMethod = createGetValidationCodeMethod();
        HttpResponse response = client.execute(getValidationCodeMethod);
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
        logger.info("recognize pic {} finished, result: {}, id: {}", tempFileName, result, qdTask.getId());
        return result;
    }

    private void submit(String code) throws IOException {
        logger.info("start to submit, id: {}", qdTask.getId());
        HttpPost saveMethod = createSaveMethod(code);
        HttpResponse response = client.execute(saveMethod);
        String content = EntityUtils.toString(response.getEntity());
        qdTask.setResult(content);
        JSONObject object = JSON.parseObject(content);
        logger.info("submit result: {}", content);

        String result = object.getString("resultDesc");

        if ("验证码错误！".equals(result)) {
            // retry
        } else if ("当前时间点已被申报，是否选择排队？".equals(result)) {
            qdTask.setStatus(QdStatusEnum.FAILED);
        } else if ("当前船舶已经申报成功,请勿重新提交！".equals(result)) {
            qdTask.setStatus(QdStatusEnum.FAILED);
        } else if ("申报参数填写错误，请仔细核查申报信息。".equals(result)) {
            qdTask.setStatus(QdStatusEnum.FAILED);
        } else if ("请在申报时间内进行申报".equals(result)) {
            // retry
        } else if ("操作成功".equals(result)) {
            qdTask.setStatus(QdStatusEnum.SUCCESS);
        } else {
            qdTask.setStatus(QdStatusEnum.FAILED);
        }
        logger.info("submit success: {}, id: {}", qdTask.getStatus(), qdTask.getId());
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

    private HttpGet createGetImageMethod() {
        HttpGet httpGet = new HttpGet("https://www.sh.msa.gov.cn/zwzx/views/image.jsp");
        httpGet.setConfig(
                RequestConfig.custom()
                        .setSocketTimeout(3000)
                        .setConnectTimeout(3000)
                        .build()
        );
        httpGet.setHeader("Cookie", qdTask.getConfig().getCookie());
        return httpGet;
    }

    private HttpPost createGetValidationCodeMethod() {
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

    private HttpPost createSaveMethod(String code) {
        HttpPost httpPost = new HttpPost("https://www.sh.msa.gov.cn/zwzx/applyVtsDeclare1/save/");
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(3000)
                .setConnectTimeout(3000)
                .build();
        httpPost.setConfig(requestConfig);
        httpPost.setHeader("Cookie", qdTask.getConfig().getCookie());
        String boundary = "----WebKitFormBoundaryinIIzQYX8Ulb5B4z";
        httpPost.setHeader("Content-Type","multipart/form-data; boundary="+boundary);
        httpPost.setEntity(getSaveEntity2(code, boundary));
        return httpPost;
    }

    private HttpEntity getSaveEntity2(String code, String boundary) {
        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
        ContentType contentType = ContentType.create("text/plain", StandardCharsets.UTF_8);
        TaskParam param = qdTask.getParam();
        ShipTypeEnum type = ShipTypeEnum.of(param.getShipType());
        entityBuilder.addPart("shipType", new StringBody(type == null ? "" : type.getValue(), contentType));
        entityBuilder.addPart("name1", new StringBody("1", contentType));
        entityBuilder.addPart("name2", new StringBody("1", contentType));
        entityBuilder.addPart("name3", new StringBody("1", contentType));
        entityBuilder.addPart("name4", new StringBody("1", contentType));
        entityBuilder.addPart("name5", new StringBody("1", contentType));

        entityBuilder.addPart("speed", new StringBody(param.getSpeed(), contentType));
        entityBuilder.addPart("maximumDraught", new StringBody(param.getDeep(), contentType));
        entityBuilder.addPart("totalPeople1", new StringBody(param.getCrewNumber(), contentType));
        entityBuilder.addPart("eta", new StringBody(param.getEta(), contentType));
        entityBuilder.addPart("startPort", new StringBody(param.getStartHarbor().split("-")[1], contentType));
        entityBuilder.addPart("beyondBreadth", new StringBody(param.getWidthLeft(), contentType));
        entityBuilder.addPart("beyondBreadthRight", new StringBody(param.getWidthRight(), contentType));
        entityBuilder.addPart("endPort", new StringBody(param.getDestHarbor().split("-")[1], contentType));
        entityBuilder.addPart("berthingPosition", new StringBody(param.getPosition(), contentType));
        entityBuilder.addPart("goodsType", new StringBody(param.getGoodsType().split("-")[0], contentType));
        entityBuilder.addPart("goodsName", new StringBody(param.getGoodsType().split("-")[1], contentType));
        entityBuilder.addPart("totalCargoWeight", new StringBody(param.getTotalWeight(), contentType));
        entityBuilder.addPart("remark", new StringBody(param.getRemark(), contentType));

        entityBuilder.addPart("startPosition", new StringBody("", contentType));
        entityBuilder.addPart("endPosition", new StringBody("", contentType));
        entityBuilder.addPart("maximumDraught1", new StringBody("", contentType));
        entityBuilder.addPart("startPort1", new StringBody("", contentType));
        entityBuilder.addPart("endPort1", new StringBody("", contentType));
        entityBuilder.addPart("leavePosition1", new StringBody("", contentType));
        entityBuilder.addPart("berthingPosition1", new StringBody("", contentType));
        entityBuilder.addPart("goodsType1", new StringBody("", contentType));
        entityBuilder.addPart("totalCargoWeight1", new StringBody("", contentType));
        entityBuilder.addPart("remark1", new StringBody("", contentType));
        entityBuilder.addPart("maximumDraught2", new StringBody("", contentType));
        entityBuilder.addPart("startPort2", new StringBody("", contentType));
        entityBuilder.addPart("endPort2", new StringBody("", contentType));
        entityBuilder.addPart("leavePosition2", new StringBody("", contentType));
        entityBuilder.addPart("berthingPosition2", new StringBody("", contentType));
        entityBuilder.addPart("attachmentPath", new StringBody("", contentType));
        entityBuilder.addPart("remark2", new StringBody("", contentType));

        entityBuilder.addPart("saveCode", new StringBody(code, contentType));
        entityBuilder.addPart("vtsStatus", new StringBody("0", contentType));

        JSONObject savePosition = new JSONObject(), importPosition = new JSONObject();
        // 业务项目
        savePosition.put("id", "");
        savePosition.put("type", encode64(param.getName()));
        savePosition.put("shipId", encode64(param.getShip()));
        savePosition.put("declareShipType", param.getShipType());
        savePosition.put("passTime", encode64(param.getTime()));
        savePosition.put("speed", param.getSpeed());
        savePosition.put("maximumDraught", param.getDeep());
        savePosition.put("beyondBreadth", param.getWidthLeft());
        savePosition.put("beyondBreadthRight", param.getWidthRight());
        savePosition.put("endPort", param.getDestHarbor());
        savePosition.put("startPort", param.getStartHarbor());
        savePosition.put("goodsType", param.getGoodsType());
        savePosition.put("berthingPosition", param.getPosition());
        // 离港
        savePosition.put("leavePosition", "");
        savePosition.put("totalCargoWeight", param.getTotalWeight());

        if("1".equals(param.getIsDay())) {
            // 夜航
            savePosition.put("nightCheck1", "1");
            savePosition.put("nightCheck2", "1");
            savePosition.put("nightCheck3", "1");
            savePosition.put("nightCheck4", "1");
            savePosition.put("nightCheck5", "1");
            savePosition.put("nightCheckResult", Integer.parseInt(param.getIsDay()));
        } else {
            savePosition.put("nightCheck1", "");
            savePosition.put("nightCheck2", "");
            savePosition.put("nightCheck3", "");
            savePosition.put("nightCheck4", "");
            savePosition.put("nightCheck5", "");
            savePosition.put("nightCheckResult", "");
        }

        savePosition.put("expectArriveTime", param.getEta());
        savePosition.put("remark", param.getRemark());

        entityBuilder.addPart("savePostion", new StringBody(JSON.toJSONString(savePosition), contentType));

        entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        entityBuilder.setBoundary(boundary);
        return entityBuilder.build();
    }

    private HttpEntity getSaveEntity1(String code) throws UnsupportedEncodingException {
        String boundary = "----WebKitFormBoundaryinIIzQYX8Ulb5B4z";
        StringBuffer buffer = new StringBuffer();
        TaskParam param = qdTask.getParam();
        ShipTypeEnum type = ShipTypeEnum.of(param.getShipType());
        writeField(buffer, boundary, "shipType", type == null ? "" : type.getValue());

        writeField(buffer, boundary, "name1", "1");
        writeField(buffer, boundary, "name2", "1");
        writeField(buffer, boundary, "name3", "1");
        writeField(buffer, boundary, "name4", "1");
        writeField(buffer, boundary, "name5", "1");

        writeField(buffer, boundary, "speed", param.getSpeed());
        writeField(buffer, boundary, "maximumDraught", param.getDeep());
        writeField(buffer, boundary, "totalPeople1", param.getCrewNumber());
        writeField(buffer, boundary, "eta", param.getEta());
        writeField(buffer, boundary, "startPort", param.getStartHarbor().split("-")[1]);
        writeField(buffer, boundary, "beyondBreadth", param.getWidthLeft());
        writeField(buffer, boundary, "beyondBreadthRight", param.getWidthRight());
        writeField(buffer, boundary, "endPort", param.getDestHarbor().split("-")[1]);
        writeField(buffer, boundary, "berthingPosition", param.getPosition());
        writeField(buffer, boundary, "goodsType", param.getGoodsType().split("-")[0]);
        writeField(buffer, boundary, "goodsName", param.getGoodsType().split("-")[1]);
        writeField(buffer, boundary, "totalCargoWeight", param.getTotalWeight());
        writeField(buffer, boundary, "remark", param.getRemark());

        writeField(buffer, boundary, "startPosition", "");
        writeField(buffer, boundary, "endPosition", "");
        writeField(buffer, boundary, "maximumDraught1", "");
        writeField(buffer, boundary, "startPort1", "");
        writeField(buffer, boundary, "endPort1", "");
        writeField(buffer, boundary, "leavePosition1", "");
        writeField(buffer, boundary, "berthingPosition1", "");
        writeField(buffer, boundary, "goodsType1", "");
        writeField(buffer, boundary, "totalCargoWeight1", "");
        writeField(buffer, boundary, "remark1", "");
        writeField(buffer, boundary, "maximumDraught2", "");
        writeField(buffer, boundary, "startPort2", "");
        writeField(buffer, boundary, "endPort2", "");
        writeField(buffer, boundary, "leavePosition2", "");
        writeField(buffer, boundary, "berthingPosition2", "");
        writeField(buffer, boundary, "attachmentPath", "");
        writeField(buffer, boundary, "remark2", "");

        writeField(buffer, boundary, "saveCode", code);
        writeField(buffer, boundary, "vtsStatus", "0");

        JSONObject savePosition = new JSONObject(), importPosition = new JSONObject();
        // 业务项目
        savePosition.put("id", "");
        savePosition.put("type", encode64(param.getName()));
        savePosition.put("shipId", encode64(param.getShip()));
        savePosition.put("declareShipType", param.getShipType());
        savePosition.put("passTime", encode64(param.getTime()));
        savePosition.put("speed", param.getSpeed());
        savePosition.put("maximumDraught", param.getDeep());
        savePosition.put("beyondBreadth", param.getWidthLeft());
        savePosition.put("beyondBreadthRight", param.getWidthRight());
        savePosition.put("endPort", param.getDestHarbor());
        savePosition.put("startPort", param.getStartHarbor());
        savePosition.put("goodsType", param.getGoodsType());
        savePosition.put("berthingPosition", param.getPosition());
        // 离港
        savePosition.put("leavePosition", "");
        savePosition.put("totalCargoWeight", param.getTotalWeight());

        if("1".equals(param.getIsDay())) {
            // 夜航
            savePosition.put("nightCheck1", "1");
            savePosition.put("nightCheck2", "1");
            savePosition.put("nightCheck3", "1");
            savePosition.put("nightCheck4", "1");
            savePosition.put("nightCheck5", "1");
            savePosition.put("nightCheckResult", Integer.parseInt(param.getIsDay()));
        } else {
            savePosition.put("nightCheck1", "");
            savePosition.put("nightCheck2", "");
            savePosition.put("nightCheck3", "");
            savePosition.put("nightCheck4", "");
            savePosition.put("nightCheck5", "");
            savePosition.put("nightCheckResult", "");
        }

        savePosition.put("expectArriveTime", param.getEta());
        savePosition.put("remark", param.getRemark());

        writeField(buffer, boundary, "savePostion", JSON.toJSONString(savePosition));
        writeEnd(buffer, boundary);
        String content = buffer.toString();

        logger.info("save method content: {}", content);
        return new StringEntity(content);
    }

    private void writeField(StringBuffer buffer, String boundary, String key, String value) {
        buffer.append("--");
        buffer.append(boundary);
        buffer.append("\r\n");
        buffer.append("Content-Disposition: form-data; name=\"").append(key).append("\"\r\n\r\n");
        buffer.append(value);
        buffer.append("\r\n");
    }

    private void writeEnd(StringBuffer buffer, String boundary) {
        buffer.append("--");
        buffer.append(boundary);
        buffer.append("--\r\n");
    }

    private boolean shouldBreak() {
        return retry >= MAX_RETRY_COUNT || qdTask.getStatus() == QdStatusEnum.FAILED || qdTask.getStatus() == QdStatusEnum.SUCCESS;
    }

    private static void sleep(Long timeMillis) {
        try {
            Thread.sleep(timeMillis);
        } catch (InterruptedException ignored) {
        }
    }

    // TODO
    private static String encode64(String original) {
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(original.getBytes());
    }
}
