package com.example.panoviewer.core;

import androidx.annotation.NonNull;

import com.example.panoviewer.net.HttpHelper;

/**
 * 内存 fake {@link HttpHelper}：注入可控的响应，便于纯 JVM 单测（无需真实网络）。
 */
public class FakeHttpHelper extends HttpHelper {

    private String htmlToReturn = "";
    private byte[] bytesToReturn = new byte[0];

    public void setStringResponse(@NonNull String html) {
        this.htmlToReturn = html;
    }

    public void setBytesResponse(@NonNull byte[] bytes) {
        this.bytesToReturn = bytes;
    }

    @NonNull
    @Override
    public String getString(@NonNull String url) {
        return htmlToReturn;
    }

    @NonNull
    @Override
    public byte[] getBytes(@NonNull String url) {
        return bytesToReturn;
    }
}
