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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class QdRunnable implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(QdRunnable.class);

    private static final int MAX_RETRY_COUNT = 5;
    private int retry = 0;
    private final List<String> codes = new ArrayList<>();
    private boolean codesGot = false;
    private final List<String> cookies;
    private final QdTask qdTask;
    private final ThreadPoolExecutor executor;

    private final AtomicInteger picIndex = new AtomicInteger(0);
    private final String tempFilePrefix;
    private final HttpClient client = HttpClients.createDefault();

    // 预估服务器时间相关，只有一个线程会预估时间
    private static int threadCount = 0;
    private static final Object lock = new Object();
    private static int estimateTimeRetry = 0;
    private volatile static boolean estimated = false;
    private volatile static boolean runBySelf = false;
    private volatile static int taskId = -1;
    private volatile static long estimatedLow = Long.MAX_VALUE;
    private volatile static long estimatedHigh = Long.MAX_VALUE;
    private static final List<String> allCookies = new ArrayList<>();
    private static final List<TimeEstimationNode> nodes = new ArrayList<>();
    private static final Object nodeLock = new Object();
    private static final List<Thread> waitingThreads = new ArrayList<>();

    private static long estimateExpectTime = 0;
    private static boolean estimatedExpect = false;
    private static final Object estimateLock2 = new Object();


    public QdRunnable(QdTask qdTask, ThreadPoolExecutor executor) {
        this.qdTask = qdTask;
        this.executor = executor;
        this.cookies = qdTask.getConfig().getCookie();
        synchronized (lock) {
            allCookies.addAll(cookies);
            threadCount++;
            if (taskId == -1) {
                logger.info("time predicate by task: {}", qdTask.getId());
                taskId = qdTask.getId();
            }
        }
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
                boolean localEstimated = estimated;
                boolean localRunBySelf = runBySelf;
                if (localEstimated) {
                    long timeRemainMillis = estimatedLow - System.currentTimeMillis();
                    long time1 = 15000, time2 = 1000;
                    if (timeRemainMillis > time1) {
                        logger.info("time remain {}ms, sleep, id: {}", timeRemainMillis,
                                qdTask.getId());
                        sleep(timeRemainMillis - time1);
                    } else if (timeRemainMillis > time2) {
                        if(!codesGot) {
                            preGetCodes(timeRemainMillis);
                        } else {
                            sleep(timeRemainMillis - time2);
                        }
                    } else {
                        long low = estimatedLow - time2, high = estimatedHigh + 100;
                        long span = (high - low) / cookies.size();
                        runTask(span);
                        return;
                    }
                } else if (localRunBySelf) {
                    if (!estimatedExpect) {
                        try {
                            long timeRemain = timeRemain();
                            synchronized (estimateLock2) {
                                estimateExpectTime = System.currentTimeMillis() + timeRemain * 1000;
                                estimatedExpect = true;
                            }
                        } catch (Exception e) {
                            // 时间获取出错，直接重试
                            logger.error("get time error, {}", e.getMessage(), e);
                            sleep(random.nextInt(2000));
                            retry++;
                            continue;
                        }
                    }

                    long expect = estimateExpectTime;
                    long cur;
                    cur = System.currentTimeMillis();
                    if (expect - cur > 15 * 1000) {
                        sleep(expect - cur - 15 * 1000);
                    }
                    cur = System.currentTimeMillis();
                    if (!codesGot) {
                        preGetCodes(expect - cur);
                    }
                    if (!codesGot) {
                        logger.info("retry getCodes");
                        preGetCodes(expect - cur);
                    }
                    if (!codesGot) {
                        logger.info("retry getCodes");
                        preGetCodes(expect - cur);
                    }

                    cur = System.currentTimeMillis();
                    sleep(expect - cur);
                    runTask(200);
                    return;
                } else {
                    long localTaskId = taskId;
                    if (localTaskId == qdTask.getId()) {
                        // 线程保证无论如何，都要唤醒其他线程
                        try {
                            long timeRemain = timeRemain();
                            synchronized (estimateLock2) {
                                estimateExpectTime = System.currentTimeMillis() + timeRemain * 1000;
                                estimatedExpect = true;
                            }
                        } catch (Throwable e) {
                            // 时间获取出错，直接重试
                            logger.error("get time error, {}", e.getMessage(), e);
                            sleep(random.nextInt(2000));
                            retry++;
                            continue;
                        }

                        long expect = estimateExpectTime;
                        long cur = System.currentTimeMillis();
                        long timeToSleep;
                        long threshold = 55 * 60;

                        if (expect - cur < 10000 || estimateTimeRetry >= MAX_RETRY_COUNT) {
                            runBySelf = true;
                            wakeUpThreads();
                        }

                        while (expect - cur > threshold * 1000) {
                            timeToSleep = expect - cur - threshold * 1000;
                            logger.info("sleep for {}ms, id: {}", timeToSleep, qdTask.getId());
                            sleep(timeToSleep);
                            cur = System.currentTimeMillis();
                        }

                        estimateTimeRetry++;
                        try {
                            estimateTime();
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                        }
                        localEstimated = estimated;
                        if (localEstimated) {
                            wakeUpThreads();
                        } else {
                            sleep(20000L);
                        }
                    } else {
                        // 未估计过时间，且不是由自己来估计时间，无期限休眠，等待唤醒
                        logger.info("park, id: {}", qdTask.getId());
                        waitingThreads.add(Thread.currentThread());
                        LockSupport.park(this);
                        logger.info("wake up, id: {}", qdTask.getId());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("run error, {}", e.getMessage(), e);
            qdTask.setStatus(QdStatusEnum.FAILED);
        } finally {
            if (interrupted) {
                logger.info("interrupted, exit");
            }
            if (qdTask.getStatus() != QdStatusEnum.SUCCESS && qdTask.getStatus() != QdStatusEnum.FAILED) {
                qdTask.setStatus(QdStatusEnum.FAILED);
            }
            qdTask.setThread(null);

            // 估计时间的线程意外退出，还未估计时间，切换到自己提交任务模式，不依赖估计的时间
            if (!estimated) {
                runBySelf = true;
                wakeUpThreads();
            }

            // 最后一个推出的线程清理静态变量
            if (--threadCount == 0) {
                estimateTimeRetry = 0;
                estimated = false;
                estimatedExpect = false;
                runBySelf = false;
                taskId = -1;
                estimatedLow = Long.MAX_VALUE;
                estimatedHigh = Long.MAX_VALUE;
                allCookies.clear();
                nodes.clear();
                waitingThreads.clear();
            }

        }
        logger.info("result: {}, id: {}", qdTask.getStatus(), qdTask.getId());
    }

    private void preGetCodes(long timeRemainMillis) {
        logger.info("time remain {}ms, pre get image and code, id: {}", timeRemainMillis,
                qdTask.getId());
        try {
            codes.clear();
            for (String cookie : cookies) {
                String image = getImage(cookie);
                String code = getValidationCode(image);
                codes.add(code);
            }
            codesGot = true;
        } catch (Exception e) {
            logger.error("pre get image and code error, retry, {}, id: {}", e.getMessage(), qdTask.getId());
            // 图片预取出错，可重试
            retry++;
        }
    }

    private void wakeUpThreads() {
        for (Thread thread : waitingThreads) {
            LockSupport.unpark(thread);
        }
    }

    private void estimateTime() {
        synchronized (lock) {
            if (estimated) {
                return;
            }

            nodes.clear();
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                String cookie = allCookies.get(i % allCookies.size());
                Future<?> future = executor.submit(() -> {
                    TimeEstimationNode node = new TimeEstimationNode();
                    node.start = System.currentTimeMillis();
                    long timeRemain = Long.MAX_VALUE;
                    try {
                        timeRemain = timeRemainInternal(cookie);
                    } catch (Exception ignore) {
                    }
                    node.end = System.currentTimeMillis();
                    if (timeRemain != Long.MAX_VALUE && timeRemain > 0) {
                        node.time = timeRemain;
                        synchronized (nodeLock) {
                            nodes.add(node);
                        }
                    }
                });
                futures.add(future);
                sleep(50L);
            }

            for (Future<?> future : futures) {
                try {
                    future.get(5000L, TimeUnit.MILLISECONDS);
                } catch (Exception ignore) {
                }
            }

            for (int i = 0; i < nodes.size() - 1; i++) {
                for (int j = 1; j < nodes.size(); j++) {
                    TimeEstimationNode n1 = nodes.get(i);
                    TimeEstimationNode n2 = nodes.get(j);
                    if (n2.time - n1.time == 1) {
                        TimeEstimationNode temp = n1;
                        n1 = n2;
                        n2 = temp;
                    }

                    if (n1.time - n2.time == 1) {
                        long timeToAdd = n1.time * 1000;
                        long high = n2.end + timeToAdd, low = n1.start + timeToAdd;
                        if (estimatedHigh == Long.MAX_VALUE || (estimatedHigh - estimatedLow) > (high - low)) {
                            estimatedHigh = high;
                            estimatedLow = low;
                        }
                    }
                }
            }
            logger.info("estimated low: {}, high: {}", timestampToDate(estimatedLow), timestampToDate(estimatedHigh));
            estimated = true;
        }
    }

    private String timestampToDate(long timestamp) {
        return new SimpleDateFormat("yyyyMMdd hh:mm:ss.SSS").format(new Date(timestamp));
    }

    private void runTask(long span) {
        logger.info("time to start, id: {}", qdTask.getId());
        if (qdTask.getStatus() == QdStatusEnum.NEW) {
            qdTask.setStatus(QdStatusEnum.RUNNING);
            qdTask.setGmtModified(new Date());
        }

        try {
            if (codesGot) {
                for (int i = 0; i < cookies.size(); i++) {
                    String code = codes.get(i);
                    String cookie = cookies.get(i);
                    executor.execute(() -> {
                        long t1 = 0, t2 = 0, t3 = 0, t4 = 0;
                        try {
                            t3 = System.currentTimeMillis();
                            submit(code, cookie);
                            t4 = System.currentTimeMillis();
                        } catch (IOException e) {
                            logger.error("execute error", e);
                        } finally {
                            logger.info("getImageTime: {}, getValidationCodeTime: {}, submitTime: {}, success: {}, id: {}"
                                    , (t2 - t1), (t3 - t2), (t4 - t3), qdTask.getStatus(), qdTask.getId());
                        }
                    });
                    sleep(span);
                }
                codesGot = false;
            } else {
                for (int i = 0; i < cookies.size(); i++) {
                    if (qdTask.getStatus() == QdStatusEnum.FAILED || qdTask.getStatus() == QdStatusEnum.SUCCESS) {
                        break;
                    }
                    final int index = i;
                    executor.execute(() -> {
                        long t1 = 0, t2 = 0, t3 = 0, t4 = 0;
                        try {
                            t1 = System.currentTimeMillis();
                            String image = getImage(cookies.get(index));
                            t2 = System.currentTimeMillis();
                            String code = getValidationCode(image);
                            t3 = System.currentTimeMillis();
                            submit(code, cookies.get(index));
                            t4 = System.currentTimeMillis();
                        } catch (IOException e) {
                            logger.error("execute error", e);
                        } finally {
                            logger.info("getImageTime: {}, getValidationCodeTime: {}, submitTime: {}, success: {}, id: {}"
                                    , (t2 - t1), (t3 - t2), (t4 - t3), qdTask.getStatus(), qdTask.getId());
                        }
                    });
                    sleep(span);
                }
            }
        } catch (Exception e) {
            logger.error("qd throws exception, {}, id: {}", e.getMessage(), qdTask.getId(), e);
            qdTask.setMessage(e.getMessage());
            qdTask.setGmtModified(new Date());
        }
    }

    private final Random random = new Random(System.currentTimeMillis());
    private long timeRemain() throws IOException {
        int cookieCount = qdTask.getConfig().getCookie().size();
        String cookie = qdTask.getConfig().getCookie().get(random.nextInt(cookieCount));
        return timeRemainInternal(cookie);
    }

    private long timeRemainInternal(String cookie) throws IOException {
        logger.info("start to get remain time, id: {}", qdTask.getId());
        long start = System.currentTimeMillis();
        HttpGet getTimeMethod = createGetTimeMethod(cookie);
        HttpResponse execute = client.execute(getTimeMethod);
        String time = EntityUtils.toString(execute.getEntity());
        long end = System.currentTimeMillis();
        logger.info("get remain time finished, time used: {}, id: {}", (end - start), qdTask.getId());
        try {
            return Long.parseLong(time);
        } catch (Throwable t) {
            throw new RuntimeException("parse time error");
        }
    }

    private String getImage(String cookie) throws IOException {
        String tempFileName = tempFilePrefix + picIndex.getAndIncrement() + ".jpg";
        logger.info("start to getImage, fileName: {}, id: {}", tempFileName, qdTask.getId());
        HttpGet getImageMethod = createGetImageMethod(cookie);
        HttpResponse response = client.execute(getImageMethod);
        byte[] bytes = EntityUtils.toByteArray(response.getEntity());
        FileOutputStream outputStream = new FileOutputStream(tempFileName);
        outputStream.write(bytes);
        outputStream.flush();
        outputStream.close();
        logger.info("getImage success, fileName: {}, id: {}", tempFileName, qdTask.getId());
        return tempFileName;
    }

    private String getValidationCode(String tempFileName) throws IOException {
        logger.info("start to recognize pic {}, id: {}", tempFileName, qdTask.getId());
        HttpPost getValidationCodeMethod = createGetValidationCodeMethod(tempFileName);
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

    private void submit(String code, String cookie) throws IOException {
        logger.info("start to submit, id: {}", qdTask.getId());
        HttpPost saveMethod = createSaveMethod(code, cookie);
        HttpResponse response = client.execute(saveMethod);
        String content = EntityUtils.toString(response.getEntity());
        JSONObject object = JSON.parseObject(content);
        logger.info("submit result: {}", content);

        String result = object.getString("resultDesc");
        if (qdTask.getStatus() != QdStatusEnum.SUCCESS && qdTask.getStatus() != QdStatusEnum.FAILED) {
            qdTask.setResult(content);
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

    private HttpPost createSaveMethod(String code, String cookie) {
        HttpPost httpPost = new HttpPost("https://www.sh.msa.gov.cn/zwzx/applyVtsDeclare1/save/");
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(3000)
                .setConnectTimeout(3000)
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

    private boolean shouldBreak() {
        return retry >= MAX_RETRY_COUNT || qdTask.getStatus() == QdStatusEnum.FAILED || qdTask.getStatus() == QdStatusEnum.SUCCESS;
    }

    private static void sleep(long timeMillis) {
        try {
            Thread.sleep(timeMillis);
        } catch (InterruptedException ignored) {
        }
    }

    private static String encode64(String original) {
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(original.getBytes());
    }

    private static class TimeEstimationNode {
        long start;
        long end;
        long time;
    }
}
