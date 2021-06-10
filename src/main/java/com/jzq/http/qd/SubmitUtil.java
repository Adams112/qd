package com.jzq.http.qd;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SubmitUtil {
    public static final Logger logger = LoggerFactory.getLogger(SubmitUtil.class);
    private static final HttpClient client = GlobalHttpClient.client;
    private final CodePredictUtil codePredictUtil = new CodePredictUtil();

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(20, 20,
            100L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100));

    private final AtomicBoolean submitStatus = new AtomicBoolean();



    public String start(List<QdTask> tasks) {
        boolean updated = submitStatus.compareAndSet(false, true);
        logger.info("任务启动: {}", updated);
        if (updated) {
            executor.execute(() -> {
                logger.info("submit thread started");
                submit(tasks);
                logger.info("submit thread exit");
            });
        }
        return updated ? "提交任务启动成功，开始提交" : "提交任务运行中";
    }

    public String stop() {
        boolean updated = submitStatus.compareAndSet(true, false);
        logger.info("任务停止: {}", updated);
        return updated
                ? "提交任务停止成功"
                : "提交任务已停止或未启动";
    }

    private void submit(List<QdTask> tasks) {
        while (submitStatus.get()) {
            for (QdTask qdTask : tasks) {
                if (!submitStatus.get()) {
                    return;
                }
                if (qdTask.getStatus() != QdStatusEnum.NEW) {
                    continue;
                }

                for (String cookie : qdTask.getConfig().getCookie()) {
                    if (!submitStatus.get()) {
                        return;
                    }
                    if (qdTask.getStatus() != QdStatusEnum.NEW) {
                        continue;
                    }

                    try {
                        String code = codePredictUtil.getCode(qdTask, cookie);
                        submitInternal(code, cookie, qdTask);
                    } catch (Throwable t) {
                        logger.error("submit task {} error", qdTask.getId(), t);
                    }
                }
            }
        }
    }

    private void submitInternal(String code, String cookie, QdTask qdTask) throws IOException {
        String content;
        try {
            HttpPost saveMethod = createSaveMethod(code, cookie, qdTask);
            HttpResponse response = client.execute(saveMethod);
            content = EntityUtils.toString(response.getEntity());
            logger.info("submit result: {}", content);
        } catch (Throwable t) {
            logger.info("submit failed, " + t.getMessage());
            throw t;
        }

        JSONObject object = JSON.parseObject(content);
        HashMap<String, Object> submitResult = new HashMap<>();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd hh:mm:ss.SSS");
        submitResult.put("submitTime", format.format(new Date()));
        submitResult.put("submitResult", content);
        submitResult.put("cookie", cookie);
        qdTask.getResults().add(submitResult);

        if (qdTask.getStatus() != QdStatusEnum.SUCCESS) {
            String result = object.getString("resultDesc");
            if ("操作成功".equals(result)) {
                qdTask.setStatus(QdStatusEnum.SUCCESS);
            } else if ("当前时间点已被申报，是否选择排队？".equals(result)
                    || "申报参数填写错误，请仔细核查申报信息。".equals(result)
                    || "当前船舶已经申报成功,请勿重新提交！".equals(result)) {
                if (qdTask.getStatus() != QdStatusEnum.SUCCESS) {
                    qdTask.setStatus(QdStatusEnum.FAILED);
                }
            }
        }

        logger.info("submit success: {}, id: {}", qdTask.getStatus(), qdTask.getId());
    }

    private HttpPost createSaveMethod(String code, String cookie, QdTask qdTask) {
        HttpPost httpPost = new HttpPost("https://www.sh.msa.gov.cn/zwzx/applyVtsDeclare1/save/");
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(30000)
                .setConnectTimeout(30000)
                .build();
        httpPost.setConfig(requestConfig);
        httpPost.setHeader("Cookie", cookie);
        String boundary = "----WebKitFormBoundaryinIIzQYX8Ulb5B4z";
        httpPost.setHeader("Content-Type","multipart/form-data; boundary="+boundary);
        httpPost.setEntity(getSaveEntity2(code, boundary, qdTask));
        return httpPost;
    }

    private HttpEntity getSaveEntity2(String code, String boundary, QdTask qdTask) {
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

    private static String encode64(String original) {
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(original.getBytes());
    }

    private static class Task {
        String code;
        String cookie;
        QdTask qdTask;
        HttpClient client;

        public Task(String code, String cookie, QdTask qdTask, HttpClient client) {
            this.code = code;
            this.cookie = cookie;
            this.qdTask = qdTask;
            this.client = client;
        }
    }
}
