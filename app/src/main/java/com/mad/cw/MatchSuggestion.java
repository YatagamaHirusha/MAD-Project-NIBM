package com.mad.cw;

import androidx.annotation.NonNull;

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

    public MatchSuggestion(
            int rank,
            @NonNull String peerId,
            @NonNull String displayName,
            @NonNull String location,
            @NonNull String occupation,
            double anxietyMean,
            double avoidanceMean,
            int matchPercent,
            double score) {
        this.rank = rank;
        this.peerId = peerId;
        this.displayName = displayName;
        this.location = location;
        this.occupation = occupation;
        this.anxietyMean = anxietyMean;
        this.avoidanceMean = avoidanceMean;
        this.matchPercent = matchPercent;
        this.score = score;
    }
}
