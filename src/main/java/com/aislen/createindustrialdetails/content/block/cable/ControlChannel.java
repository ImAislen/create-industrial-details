package com.aislen.createindustrialdetails.content.block.cable;

public enum ControlChannel {
    RED(0, "red"),
    YELLOW(4, "yellow"),
    GREEN(8, "green"),
    BLUE(12, "blue");

    private final int shift;
    private final String name;

    ControlChannel(int shift, String name) {
        this.shift = shift;
        this.name = name;
    }

    public int read(int signals) {
        return (signals >> shift) & 15;
    }

    public int write(int signals, int value) {
        return (signals & ~(15 << shift)) | (Math.clamp(value, 0, 15) << shift);
    }

    public String translationKey() {
        return "create_industrial_details.control_box.channel." + name;
    }
}
