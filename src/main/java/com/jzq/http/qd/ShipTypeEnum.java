package com.jzq.http.qd;

import org.springframework.util.ObjectUtils;

public enum  ShipTypeEnum {
    NORMAL("普通船", "0"),
    SUPER_STANDARD_SHIP("超规范船", "1"),
    SHIP("游轮", "2"),
    DANGER("危险品船", "3"),
    ELSE(" 无需审批船", "4");

    private final String name;
    private final String value;

    ShipTypeEnum(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public static ShipTypeEnum of(String name) {
        if (ObjectUtils.isEmpty(name)) {
            return null;
        }
        for (ShipTypeEnum typeEnum : ShipTypeEnum.values()) {
            if (name.equals(typeEnum.name)) {
                return typeEnum;
            }
        }
        return null;
    }
}
