package com.jzq.http.qd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Controller
@RequestMapping("/")
public class QdController {
    private static final Logger logger = LoggerFactory.getLogger(QdController.class);

    private Set<QdTask> tasks = new HashSet<>(100);

    private ThreadPoolExecutor executor = new ThreadPoolExecutor(20, 20, 100L
            , TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(100));
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);
    private AtomicInteger index = new AtomicInteger(1);

    public QdController() {
        Runnable runnable = () -> {
            Iterator<QdTask> iterator = tasks.iterator();
            while (iterator.hasNext()) {
                QdTask task = iterator.next();
                QdStatusEnum taskStatus = task.getStatus();
                if (taskStatus == QdStatusEnum.SUCCESS || taskStatus == QdStatusEnum.FAILED) {
                    logger.info("remove task: {}", task);
                    iterator.remove();
                }

            }
        };
        scheduledThreadPoolExecutor.scheduleAtFixedRate(runnable, 12L, 24L, TimeUnit.HOURS);
    }

    @RequestMapping("/addTask")
    @ResponseBody
    public Object addTask(HttpServletRequest request) throws IOException {
        StringBuilder buffer = new StringBuilder();
        request.getReader().lines().forEach(buffer::append);
        String originalParam = buffer.toString();
        JSONObject object = JSON.parseObject(originalParam);
        TaskConfig config = object.getObject("config", TaskConfig.class);
        QdTask task = new QdTask();
        task.setOriginalParam(originalParam);
        try {
            task.setParam(constructParam(object));
        } catch (Exception e) {
            return e.getMessage();
        }

        Integer id = index.getAndIncrement();
        task.setId(id);
        task.setConfig(config);
        task.setGmtCreate(new Date());
        task.setStatus(QdStatusEnum.NEW);
        tasks.add(task);
        executor.execute(new QdRunnable(task));
        return id;
    }

    @RequestMapping("/deleteTask")
    @ResponseBody
    public Object deleteTask(Integer id) {
        boolean removed = false;
        Iterator<QdTask> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            QdTask next = iterator.next();
            if (next.getId().equals(id)) {
                next.getThread().interrupt();
                iterator.remove();
                removed = true;
            }
        }
        return removed;
    }

    @RequestMapping("getTask")
    @ResponseBody
    public Object getTask() {
        List<QdTask> result = new ArrayList<>(tasks);
        result.sort((o1, o2) -> o2.getId() - o1.getId());
        return result;
    }

    private TaskParam constructParam(JSONObject object) {
        TaskParam param = new TaskParam();
        JSONObject paramObj = object.getJSONObject("param");
        String projectType = getOrDefault(paramObj, "业务项目", "");
        ProjectTypeEnum type = ProjectTypeEnum.of(projectType);
        if (type == null) {
            throw new RuntimeException("业务项目错误");
        }
        param.setName(type.getValue());

        String shipId = getOrDefault(paramObj, "船舶", "");
        if (shipId == null) {
            throw new RuntimeException("船舶填写id");
        }
        param.setShip(shipId);

        String shipType = getOrDefault(paramObj, "申报船舶类型", "");
        param.setShipType(shipType);

        param.setTime(getOrDefault(paramObj, "过D3灯浮时间", ""));

        String day = getOrDefault(paramObj, "日/夜", "");
        if ("日".equals(day)) {
            param.setIsDay("0");
        } else if ("夜".equals(day)) {
            param.setIsDay("1");
        } else {
            throw new RuntimeException("日/夜填写错误");
        }

        param.setSpeed(getOrDefault(paramObj, "航速", ""));
        param.setDeep(getOrDefault(paramObj, "本航次最大吃水", ""));
        param.setCrewNumber(getOrDefault(paramObj, "载客及船员人数", ""));
        param.setEta(getOrDefault(paramObj, "ETA", ""));
        param.setStartHarbor(getOrDefault(paramObj, "始发港", ""));
        param.setWidthLeft(getOrDefault(paramObj, "超出左舷外宽度", ""));
        param.setWidthRight(getOrDefault(paramObj, "超出右舷外宽度", ""));
        param.setDestHarbor(getOrDefault(paramObj, "目的港", ""));
        param.setPosition(getOrDefault(paramObj, "靠泊位置", ""));
        param.setGoodsType(getOrDefault(paramObj, "货物种类", ""));
        param.setTotalWeight(getOrDefault(paramObj, "货物总重量", ""));
        param.setRemark(getOrDefault(paramObj, "备注", ""));
        return param;
    }

    private String getOrDefault(JSONObject obj, String key, String defaultValue) {
        if (obj.containsKey(key)) {
            return obj.getString(key);
        } else {
            return defaultValue;
        }
    }
}
