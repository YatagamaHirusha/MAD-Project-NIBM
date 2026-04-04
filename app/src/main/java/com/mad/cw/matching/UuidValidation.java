package com.mad.cw.matching;

import androidx.annotation.Nullable;
import com.mad.cw.assessment.*;
import com.mad.cw.chat.*;
import com.mad.cw.inbox.*;
import com.mad.cw.interests.*;
import com.mad.cw.matching.*;
import com.mad.cw.profile.*;
import com.mad.cw.shell.*;
import com.mad.cw.welcome.*;

/** Helpers for recognizing Supabase {@code uuid} strings. */
public final class UuidValidation {

    private UuidValidation() {}

    public static boolean isUuid(@Nullable String s) {
        if (s == null) {
            return false;
        }
        return s.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }
}
