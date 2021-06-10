package com.jzq.http.qd;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;

public class QdTask {
    private Integer id;

    @JsonFormat(pattern="yyyy-MM-dd hh:mm:ss.SSS",timezone = "GMT+8")
    private Date gmtCreate;

    @JsonFormat(pattern="yyyy-MM-dd hh:mm:ss.SSS",timezone = "GMT+8")
    private Date gmtModified;

    private TaskParam param;

    private TaskConfig config;

    private QdStatusEnum status;

    private String result;

    private List<HashMap<String, Object>> results = new ArrayList<>();

    private String message;

    private String originalParam;

    @JsonIgnore
    private Thread thread;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Date getGmtCreate() {
        return gmtCreate;
    }

    public void setGmtCreate(Date gmtCreate) {
        this.gmtCreate = gmtCreate;
    }

    public Date getGmtModified() {
        return gmtModified;
    }

    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }

    public TaskParam getParam() {
        return param;
    }

    public void setParam(TaskParam param) {
        this.param = param;
    }

    public QdStatusEnum getStatus() {
        return status;
    }

    public TaskConfig getConfig() {
        return config;
    }

    public void setConfig(TaskConfig config) {
        this.config = config;
    }

    public void setStatus(QdStatusEnum status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getOriginalParam() {
        return originalParam;
    }

    public void setOriginalParam(String originalParam) {
        this.originalParam = originalParam;
    }

    public Thread getThread() {
        return thread;
    }

    public void setThread(Thread thread) {
        this.thread = thread;
    }

    public List<HashMap<String, Object>> getResults() {
        return results;
    }

    public void setResults(List<HashMap<String, Object>> results) {
        this.results = results;
    }
}
