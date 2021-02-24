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
import java.util.Base64;

public class SubmitUtil {
    public static final Logger logger = LoggerFactory.getLogger(SubmitUtil.class);

    private final QdTask qdTask;
    private final HttpClient client;

    public SubmitUtil(QdTask qdTask, HttpClient client) {
        this.qdTask = qdTask;
        this.client = client;
    }

    public void submit(String code, String cookie) throws IOException {
        logger.info("start to submit, id: {}", qdTask.getId());
        HttpPost saveMethod = createSaveMethod(code, cookie);
        HttpResponse response = client.execute(saveMethod);
        String content = EntityUtils.toString(response.getEntity());
        JSONObject object = JSON.parseObject(content);
        logger.info("submit result: {}", content);

        if (qdTask.getStatus() != QdStatusEnum.SUCCESS) {
            qdTask.setResult(content);
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
        }

        logger.info("submit success: {}, id: {}", qdTask.getStatus(), qdTask.getId());
    }

    private HttpPost createSaveMethod(String code, String cookie) {
        HttpPost httpPost = new HttpPost("https://www.sh.msa.gov.cn/zwzx/applyVtsDeclare1/save/");
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(30000)
                .setConnectTimeout(30000)
                .build();
        httpPost.setConfig(requestConfig);
        httpPost.setHeader("Cookie", cookie);
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

    private static String encode64(String original) {
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(original.getBytes());
    }
}
