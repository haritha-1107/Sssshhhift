package com.example.sssshhift.usage.data;

public class PeakHourCount {
    private String hour;
    private int count;

    public PeakHourCount(String hour, int count) {
        this.hour = hour;
        this.count = count;
    }

    public String getHour() { return hour; }
    public int getCount() { return count; }
} 