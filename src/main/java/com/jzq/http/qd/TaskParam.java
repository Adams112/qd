package com.jzq.http.qd;

public class TaskParam {
    /**
     * 业务项目
     */
    private String name;

    /**
     * 船舶
     */
    private String ship;

    /**
     * 船舶类型
     */
    private String shipType;

    /**
     * 过D3灯浮时间
     */
    private String time;

    /**
     * 夜航申报，日/夜
     */
    private String isDay;

    /**
     * 航速
     */
    private String speed;

    /**
     * 本航次最大吃水
     */
    private String deep;

    /**
     * 载客及船员人数
     */
    private String crewNumber;

    /**
     * ETA
     */
    private String eta;

    /**
     * 始发港, 国外-越南
     */
    private String startHarbor;

    /**
     * 超出左舷外宽度
     */
    private String widthLeft;

    /**
     * 超出右舷外宽度
     */
    private String widthRight;

    /**
     * 目的港, 长江-南京
     */
    private String destHarbor;

    /**
     * 靠泊位置
     */
    private String position;

    /**
     * 货物种类 其他散货-木片
     */
    private String goodsType;

    /**
     *  货物总重量
     */
    private String totalWeight;

    /**
     * 备注
     */
    private String remark;

    @Override
    public String toString() {
        return "TaskParam{" +
                "name='" + name + '\'' +
                ", ship='" + ship + '\'' +
                ", shipType='" + shipType + '\'' +
                ", time='" + time + '\'' +
                ", isDay='" + isDay + '\'' +
                ", speed='" + speed + '\'' +
                ", deep='" + deep + '\'' +
                ", crewNumber='" + crewNumber + '\'' +
                ", eta='" + eta + '\'' +
                ", startHarbor='" + startHarbor + '\'' +
                ", widthLeft='" + widthLeft + '\'' +
                ", widthRight='" + widthRight + '\'' +
                ", destHarbor='" + destHarbor + '\'' +
                ", position='" + position + '\'' +
                ", goodsType='" + goodsType + '\'' +
                ", totalWeight='" + totalWeight + '\'' +
                ", remark='" + remark + '\'' +
                '}';
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShip() {
        return ship;
    }

    public void setShip(String ship) {
        this.ship = ship;
    }

    public String getShipType() {
        return shipType;
    }

    public void setShipType(String shipType) {
        this.shipType = shipType;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getIsDay() {
        return isDay;
    }

    public void setIsDay(String isDay) {
        this.isDay = isDay;
    }

    public String getSpeed() {
        return speed;
    }

    public void setSpeed(String speed) {
        this.speed = speed;
    }

    public String getDeep() {
        return deep;
    }

    public void setDeep(String deep) {
        this.deep = deep;
    }

    public String getCrewNumber() {
        return crewNumber;
    }

    public void setCrewNumber(String crewNumber) {
        this.crewNumber = crewNumber;
    }

    public String getEta() {
        return eta;
    }

    public void setEta(String eta) {
        this.eta = eta;
    }

    public String getStartHarbor() {
        return startHarbor;
    }

    public void setStartHarbor(String startHarbor) {
        this.startHarbor = startHarbor;
    }

    public String getWidthLeft() {
        return widthLeft;
    }

    public void setWidthLeft(String widthLeft) {
        this.widthLeft = widthLeft;
    }

    public String getWidthRight() {
        return widthRight;
    }

    public void setWidthRight(String widthRight) {
        this.widthRight = widthRight;
    }

    public String getDestHarbor() {
        return destHarbor;
    }

    public void setDestHarbor(String destHarbor) {
        this.destHarbor = destHarbor;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getGoodsType() {
        return goodsType;
    }

    public void setGoodsType(String goodsType) {
        this.goodsType = goodsType;
    }

    public String getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(String totalWeight) {
        this.totalWeight = totalWeight;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}