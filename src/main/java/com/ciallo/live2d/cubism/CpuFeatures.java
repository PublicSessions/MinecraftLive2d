package com.ciallo.live2d.cubism;

public class CpuFeatures {
    public static boolean isAvx2Supported() {

        String osArch = System.getProperty("os.arch");
        return osArch != null && (osArch.contains("64") || osArch.contains("amd64"));
    }
}