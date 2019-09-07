package com.dsi.ant.antplusdemo;

public class ChannelConfiguration {
    public short deviceNumber;
    public byte deviceType;
    public byte TransmissionType;
    public short period;
    public byte freq;
    public byte proxSearch;

    public boolean isInitializing = false;
    public boolean isDeinitializing = false;
}
