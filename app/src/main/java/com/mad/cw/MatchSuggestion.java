package com.mad.cw.matching;

import androidx.annotation.NonNull;
import com.mad.cw.assessment.*;
import com.mad.cw.chat.*;
import com.mad.cw.inbox.*;
import com.mad.cw.interests.*;
import com.mad.cw.matching.*;
import com.mad.cw.profile.*;
import com.mad.cw.shell.*;
import com.mad.cw.welcome.*;

/**
 * One ranked match for display (local rule-based scorer over a demo candidate pool).
 */
public final class MatchSuggestion {

    public final int rank;
    @NonNull public final String peerId;
    @NonNull public final String displayName;
    @NonNull public final String location;
    @NonNull public final String occupation;
    public final double anxietyMean;
    public final double avoidanceMean;
    /** 0–100 for UI */
    public final int matchPercent;
    /** Raw hybrid score used for ordering */
    public final double score;
    /** Placeholder or remote URL for list avatars */
    @NonNull public final String photoUrl;

    public MatchSuggestion(
            int rank,
            @NonNull String peerId,
            @NonNull String displayName,
            @NonNull String location,
            @NonNull String occupation,
            double anxietyMean,
            double avoidanceMean,
            int matchPercent,
            double score,
            @NonNull String photoUrl) {
        this.rank = rank;
        this.peerId = peerId;
        this.displayName = displayName;
        this.location = location;
        this.occupation = occupation;
        this.anxietyMean = anxietyMean;
        this.avoidanceMean = avoidanceMean;
        this.matchPercent = matchPercent;
        this.score = score;
        this.photoUrl = photoUrl;
    }
}
