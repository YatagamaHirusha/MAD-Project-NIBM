package com.mad.cw.inbox;

import androidx.annotation.Nullable;
import com.mad.cw.assessment.*;
import com.mad.cw.chat.*;
import com.mad.cw.inbox.*;
import com.mad.cw.interests.*;
import com.mad.cw.matching.*;
import com.mad.cw.profile.*;
import com.mad.cw.shell.*;
import com.mad.cw.welcome.*;

/**
 * One row in the inbox (mock match thread until API exists).
 */
public class ConversationPreview {

    public final String peerId;
    public final String peerName;
    public final String lastMessagePreview;
    public final String timeLabel;
    /** Optional HTTPS URL for peer avatar. */
    @Nullable public final String peerPhotoUrl;

    public ConversationPreview(
            String peerId,
            String peerName,
            String lastMessagePreview,
            String timeLabel,
            @Nullable String peerPhotoUrl) {
        this.peerId = peerId;
        this.peerName = peerName;
        this.lastMessagePreview = lastMessagePreview;
        this.timeLabel = timeLabel;
        this.peerPhotoUrl = peerPhotoUrl;
    }
}
