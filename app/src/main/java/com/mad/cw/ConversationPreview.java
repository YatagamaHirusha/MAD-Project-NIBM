package com.mad.cw;

/**
 * One row in the inbox (mock match thread until API exists).
 */
public class ConversationPreview {

    public final String peerId;
    public final String peerName;
    public final String lastMessagePreview;
    public final String timeLabel;

    public ConversationPreview(String peerId, String peerName, String lastMessagePreview, String timeLabel) {
        this.peerId = peerId;
        this.peerName = peerName;
        this.lastMessagePreview = lastMessagePreview;
        this.timeLabel = timeLabel;
    }
}
