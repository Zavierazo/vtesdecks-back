package com.vtesdecks.messaging;

public interface MessageDelegate {
    void handleMessage(String message, String channel);
}