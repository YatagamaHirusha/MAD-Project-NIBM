package com.mad.cw.models;

public class Message {
    private String text;
    private String time;
    private boolean isSent;
    private int avatarResId;
    private String senderName; // For header or group chats, but useful for reference

    public Message(String text, String time, boolean isSent, int avatarResId, String senderName) {
        this.text = text;
        this.time = time;
        this.isSent = isSent;
        this.avatarResId = avatarResId;
        this.senderName = senderName;
    }

    public String getText() { return text; }
    public String getTime() { return time; }
    public boolean isSent() { return isSent; }
    public int getAvatarResId() { return avatarResId; }
    public String getSenderName() { return senderName; }
}
