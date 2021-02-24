package com.jzq.http.qd;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class QdRunnable implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(QdRunnable.class);

    private final QdTask qdTask;
    private final ThreadPoolExecutor executor;

    private final TimeRemainUtil timeRemainUtil;
    private final CodePredictUtil codePredictUtil;
    private final SubmitUtil submitUtil;

    private static final HttpClient client = HttpClients.createDefault();
    private static final List<Thread> waitingThreads = new ArrayList<>();

    public QdRunnable(QdTask qdTask, ThreadPoolExecutor executor) {
        this.qdTask = qdTask;
        this.executor = executor;
        this.timeRemainUtil = new TimeRemainUtil(qdTask, client);
        this.codePredictUtil = new CodePredictUtil(qdTask, client);
        this.submitUtil = new SubmitUtil(qdTask, client);
    }

    @Override
    public void run() {
        qdTask.setThread(Thread.currentThread());
        try {
            /*
             * 每个线程进入，获取剩余时间，获取失败加入waitingThreads，等待获取时间成功的线程唤醒
             */
            long expect = timeRemainUtil.getExpectTime();
            if (expect == Long.MAX_VALUE) {
                waitingThreads.add(Thread.currentThread());
                LockSupport.park(this);
                expect = timeRemainUtil.getExpectTime();
            } else {
                List<Thread> threads = new ArrayList<>(waitingThreads);
                waitingThreads.clear();
                for (Thread t : threads) {
                    LockSupport.unpark(t);
                }
            }

            /*
             * 如果剩余时间 < 30s，预先获取验证码，否则休眠
             */
            qdTask.setStatus(QdStatusEnum.RUNNING);
            long timeRemain = expect - System.currentTimeMillis();
            if (timeRemain > 30000) {
                sleep(timeRemain - 30000);
            }
            Map<String, String> codes = getCodes();

            /*
             * 如果剩余时间 < 1s，开始提交
             */

            timeRemain = expect - System.currentTimeMillis();
            if (timeRemain > 2000) {
                sleep(timeRemain - 2000);
            }
            int cookieCount = codes.size();
            long span = 2000 / cookieCount;
            if (span <= 0 || span > 1000) {
                span = 100;
            }
            for (Map.Entry<String, String> entry : codes.entrySet()) {
                submit(entry.getValue(), entry.getKey());
                sleep(span);
            }
        } catch (Exception e) {
            logger.error("run error, {}", e.getMessage(), e);
            qdTask.setStatus(QdStatusEnum.FAILED);
        } finally {
            qdTask.setThread(null);
            if (qdTask.getStatus() != QdStatusEnum.SUCCESS) {
                qdTask.setStatus(QdStatusEnum.FAILED);
            }
        }
        logger.info("result: {}, id: {}", qdTask.getStatus(), qdTask.getId());
    }

    private Map<String, String> getCodes() {
        Map<String, String> codes = new HashMap<>();
        Map<String, Future<String>> futures= new HashMap<>();
        for (String cookie : qdTask.getConfig().getCookie()) {
            try {
                futures.put(cookie, executor.submit(() -> codePredictUtil.getCode(cookie)));
            } catch (Throwable e) {
                logger.error(e.getMessage(), e);
            }
        }

        for (Map.Entry<String, Future<String>> entry : futures.entrySet()) {
            String code = null;
            try {
                code = entry.getValue().get(3000, TimeUnit.MILLISECONDS);
            } catch (Throwable ignore) {
            }
            if (code != null) {
                codes.put(entry.getKey(), code);
            }
        }
        return codes;
    }

    private void submit(String code, String cookie) {
        executor.execute(() -> {
            try {
                submitUtil.submit(code, cookie);
            } catch (Throwable e) {
                logger.error(e.getMessage(), e);
            }
        });
    }

    private static void sleep(long timeMillis) {
        try {
            if (timeMillis > 0) {
                Thread.sleep(timeMillis);
            }
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
