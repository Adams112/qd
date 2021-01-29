package com.jzq.http.qd;

import org.springframework.util.ObjectUtils;

public enum ProjectTypeEnum {
    JINKOU("进口申报", "1"),
    CUHKOU("出口申报", "2"),
    MAOPO("锚泊申报", "3"),
    HUHANG("护航申请", "4"),
    TUODAI("拖带申报", "5"),
    SHIHANG("试航申报", "6");

    private String name;
    private String value;

    ProjectTypeEnum(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public static ProjectTypeEnum of(String name) {
        if (ObjectUtils.isEmpty(name)) {
            return null;
        }
        for (ProjectTypeEnum type : ProjectTypeEnum.values()) {
            if (name.equals(type.name)) {
                return type;
            }
        }
        return null;
    }
}
