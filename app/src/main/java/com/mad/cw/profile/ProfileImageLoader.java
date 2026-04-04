package com.mad.cw.profile;

import com.mad.cw.R;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.mad.cw.assessment.*;
import com.mad.cw.chat.*;
import com.mad.cw.inbox.*;
import com.mad.cw.interests.*;
import com.mad.cw.matching.*;
import com.mad.cw.profile.*;
import com.mad.cw.shell.*;
import com.mad.cw.welcome.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

/**
 * Loads circular profile images from a local file or remote URL, with an initial-letter fallback.
 */
public final class ProfileImageLoader {

    private ProfileImageLoader() {}

    public static void loadPeerAvatar(
            @NonNull ImageView imageView,
            @Nullable String remoteUrl,
            @Nullable String nameForInitial,
            @Nullable TextView initialOverlay) {
        boolean hasUrl = remoteUrl != null && !remoteUrl.trim().isEmpty();
        if (hasUrl) {
            if (initialOverlay != null) {
                initialOverlay.setVisibility(View.GONE);
            }
            Glide.with(imageView)
                    .load(remoteUrl.trim())
                    .circleCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imageView);
            return;
        }
        Glide.with(imageView).clear(imageView);
        imageView.setImageDrawable(
                new ColorDrawable(ContextCompat.getColor(imageView.getContext(), android.R.color.darker_gray)));
        if (initialOverlay != null) {
            initialOverlay.setVisibility(View.VISIBLE);
            String n = nameForInitial != null ? nameForInitial.trim() : "";
            String letter = n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase();
            initialOverlay.setText(letter);
        }
    }

    public static void loadSelfAvatar(
            @NonNull ImageView imageView,
            @NonNull Context context,
            @Nullable String remoteUrl,
            @Nullable String nameForInitial,
            @Nullable TextView initialOverlay) {
        if (AvatarStorage.hasLocalAvatar(context)) {
            if (initialOverlay != null) {
                initialOverlay.setVisibility(View.GONE);
            }
            Glide.with(imageView)
                    .load(AvatarStorage.getFile(context))
                    .circleCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imageView);
            return;
        }
        loadPeerAvatar(imageView, remoteUrl, nameForInitial, initialOverlay);
    }
}
