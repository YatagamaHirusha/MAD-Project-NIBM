package com.mad.cw;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Rule-based match scoring aligned with {@code Model/v4.ipynb} (hard filters + weighted Jaccard +
 * psychology heuristic). Uses a fixed demo candidate pool until server-side matching exists.
 */
public final class MatchScoring {

    /** Same keys / relative weights as notebook CATEGORY_WEIGHTS (max sum ≈ 107). */
    private static final Map<String, Double> CATEGORY_WEIGHTS = new HashMap<>();

    static {
        CATEGORY_WEIGHTS.put("Relationship Intent", 40.0);
        CATEGORY_WEIGHTS.put("Personality & Values", 20.0);
        CATEGORY_WEIGHTS.put("Lifestyle", 15.0);
        CATEGORY_WEIGHTS.put("Intellectual & Learning", 5.0);
        CATEGORY_WEIGHTS.put("Food & Drinks", 5.0);
        CATEGORY_WEIGHTS.put("Travel & Culture", 5.0);
        CATEGORY_WEIGHTS.put("Gaming & Digital", 5.0);
        CATEGORY_WEIGHTS.put("Sports & Outdoor", 5.0);
        CATEGORY_WEIGHTS.put("Arts & Creativity", 3.0);
        CATEGORY_WEIGHTS.put("Music", 2.0);
        CATEGORY_WEIGHTS.put("Movies & Shows", 2.0);
    }

    private static final double MAX_ROUGH_TOTAL = 107.0 + 70.0;

    private MatchScoring() {}

    @NonNull
    public static List<MatchSuggestion> computeTopMatches(@NonNull Context context, int limit) {
        Seeker seeker = loadSeeker(context);
        if (seeker == null) {
            return Collections.emptyList();
        }

        List<Scored> scored = new ArrayList<>();
        for (DemoPerson c : DEMO_PEOPLE) {
            if (!sameLocation(seeker.location, c.location)) {
                continue;
            }
            if (!mutualGenderOk(seeker.gender, seeker.targetGender, c.gender, c.targetGender)) {
                continue;
            }
            double interest = weightedJaccard(seeker.interestsByColumn, c.interestsByColumn);
            double psych = psychReward(seeker.anxiety, seeker.avoidance, c.anxiety, c.avoidance);
            double total = interest + psych;
            scored.add(new Scored(c, total));
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));

        List<MatchSuggestion> out = new ArrayList<>();
        int n = Math.min(limit, scored.size());
        for (int i = 0; i < n; i++) {
            Scored s = scored.get(i);
            int pct = toMatchPercent(s.score);
            out.add(
                    new MatchSuggestion(
                            i + 1,
                            s.person.id,
                            s.person.displayName,
                            s.person.location,
                            s.person.occupation,
                            s.person.anxiety,
                            s.person.avoidance,
                            pct,
                            s.score));
        }
        return out;
    }

    private static int toMatchPercent(double rawScore) {
        int p = (int) Math.round(100.0 * rawScore / MAX_ROUGH_TOTAL);
        return Math.min(99, Math.max(52, p));
    }

    private static boolean sameLocation(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * Seeker accepts candidate’s gender and candidate accepts seeker’s gender.
     * {@code any} on target side means no filter on partner gender for that person.
     */
    private static boolean mutualGenderOk(
            String seekerGender, String seekerTarget, String candGender, String candTarget) {
        boolean seekerOk =
                "any".equalsIgnoreCase(seekerTarget)
                        || (seekerTarget != null && seekerTarget.equalsIgnoreCase(candGender));
        boolean candOk =
                "any".equalsIgnoreCase(candTarget)
                        || (candTarget != null && candTarget.equalsIgnoreCase(seekerGender));
        return seekerOk && candOk;
    }

    private static double jaccard(@NonNull List<String> a, @NonNull List<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        Set<String> sa = new HashSet<>();
        for (String s : a) {
            if (s != null && !s.isEmpty()) {
                sa.add(s);
            }
        }
        Set<String> sb = new HashSet<>();
        for (String s : b) {
            if (s != null && !s.isEmpty()) {
                sb.add(s);
            }
        }
        if (sa.isEmpty() && sb.isEmpty()) {
            return 0.0;
        }
        int inter = 0;
        for (String x : sa) {
            if (sb.contains(x)) {
                inter++;
            }
        }
        int union = sa.size() + sb.size() - inter;
        return union == 0 ? 0.0 : (double) inter / union;
    }

    private static double weightedJaccard(
            Map<String, List<String>> seeker, Map<String, List<String>> cand) {
        double sum = 0.0;
        for (Map.Entry<String, Double> e : CATEGORY_WEIGHTS.entrySet()) {
            String col = e.getKey();
            double w = e.getValue();
            List<String> a = seeker.getOrDefault(col, Collections.emptyList());
            List<String> b = cand.getOrDefault(col, Collections.emptyList());
            sum += jaccard(a, b) * w;
        }
        return sum;
    }

    private static double psychReward(double a1, double av1, double a2, double av2) {
        double trap = (a1 * av2) + (a2 * av1);
        double r = 50.0;
        if (trap > 35.0) {
            r -= 40.0;
        }
        if (a1 < 3.0 && a2 < 3.0) {
            r += 20.0;
        }
        return r;
    }

    private static Seeker loadSeeker(Context context) {
        SharedPreferences p = ProfilePreferences.get(context);
        String gender = p.getString(ProfilePreferences.KEY_GENDER, "");
        String target = p.getString(ProfilePreferences.KEY_TARGET_GENDER, "");
        String location = p.getString(ProfilePreferences.KEY_LOCATION, "");
        if (!GenderOptions.isValidGenderValue(gender) || !GenderOptions.isValidTargetGenderValue(target)) {
            return null;
        }
        if (location == null || location.trim().length() < ProfileFormValidator.MIN_LOCATION_LENGTH) {
            return null;
        }

        double anxiety = AssessmentPreferences.getLastAnxietyScore(context);
        double avoidance = AssessmentPreferences.getLastAvoidanceScore(context);
        if (Double.isNaN(anxiety)) {
            anxiety = 4.0;
        }
        if (Double.isNaN(avoidance)) {
            avoidance = 4.0;
        }

        Map<String, List<String>> interests = UserInterestStore.loadInterestMap(context);
        return new Seeker(gender, target, location.trim(), anxiety, avoidance, interests);
    }

    private static final class Seeker {
        final String gender;
        final String targetGender;
        final String location;
        final double anxiety;
        final double avoidance;
        final Map<String, List<String>> interestsByColumn;

        Seeker(
                String gender,
                String targetGender,
                String location,
                double anxiety,
                double avoidance,
                Map<String, List<String>> interestsByColumn) {
            this.gender = gender;
            this.targetGender = targetGender;
            this.location = location;
            this.anxiety = anxiety;
            this.avoidance = avoidance;
            this.interestsByColumn = interestsByColumn;
        }
    }

    private static final class Scored {
        final DemoPerson person;
        final double score;

        Scored(DemoPerson person, double score) {
            this.person = person;
            this.score = score;
        }
    }

    private static final class DemoPerson {
        final String id;
        final String displayName;
        final String gender;
        final String targetGender;
        final String location;
        final String occupation;
        final double anxiety;
        final double avoidance;
        final Map<String, List<String>> interestsByColumn;

        DemoPerson(
                String id,
                String displayName,
                String gender,
                String targetGender,
                String location,
                String occupation,
                double anxiety,
                double avoidance,
                Map<String, List<String>> interestsByColumn) {
            this.id = id;
            this.displayName = displayName;
            this.gender = gender;
            this.targetGender = targetGender;
            this.location = location;
            this.occupation = occupation;
            this.anxiety = anxiety;
            this.avoidance = avoidance;
            this.interestsByColumn = interestsByColumn;
        }
    }

    private static Map<String, List<String>> m(String... pairs) {
        Map<String, List<String>> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String col = pairs[i];
            String csv = pairs[i + 1];
            String[] parts = csv.split("\\|");
            List<String> list = new ArrayList<>();
            for (String part : parts) {
                String t = part.trim();
                if (!t.isEmpty()) {
                    list.add(t);
                }
            }
            map.put(col, list);
        }
        return map;
    }

    /** Demo pool: several Sri Lankan cities + reciprocal gender pairs for classroom demo. */
    private static final List<DemoPerson> DEMO_PEOPLE;

    static {
        List<DemoPerson> d = new ArrayList<>();
        d.add(
                new DemoPerson(
                        "demo-01",
                        "Nethmi Bandara",
                        "female",
                        "male",
                        "Colombo",
                        "Teacher",
                        3.8,
                        3.2,
                        m(
                                "Lifestyle",
                                "Yoga|Fitness|Early Bird",
                                "Arts & Creativity",
                                "Photography|Writing",
                                "Music",
                                "Indie|Classical",
                                "Movies & Shows",
                                "Documentaries|Romance",
                                "Intellectual & Learning",
                                "Psychology|Books & Reading",
                                "Food & Drinks",
                                "Coffee|Cooking",
                                "Sports & Outdoor",
                                "Swimming|Hiking",
                                "Gaming & Digital",
                                "Board Games",
                                "Travel & Culture",
                                "Beaches|Museums",
                                "Personality & Values",
                                "Family-Oriented|Environmentalist",
                                "Relationship Intent",
                                "Long-Term Relationship")));
        d.add(
                new DemoPerson(
                        "demo-02",
                        "Malith Fernando",
                        "male",
                        "female",
                        "Colombo",
                        "Software Developer",
                        2.9,
                        3.5,
                        m(
                                "Lifestyle",
                                "Gym|Night Owl",
                                "Arts & Creativity",
                                "Graphic Design",
                                "Music",
                                "EDM|Pop",
                                "Movies & Shows",
                                "Sci-Fi|Thriller",
                                "Intellectual & Learning",
                                "Technology|AI & Machine Learning",
                                "Food & Drinks",
                                "Street Food|Coffee",
                                "Sports & Outdoor",
                                "Cricket|Football",
                                "Gaming & Digital",
                                "PC Gaming|eSports",
                                "Travel & Culture",
                                "Road Trips|Backpacking",
                                "Personality & Values",
                                "Career-Focused",
                                "Relationship Intent",
                                "Long-Term Relationship")));
        d.add(
                new DemoPerson(
                        "demo-03",
                        "Samadhi Wijesinghe",
                        "female",
                        "male",
                        "Colombo",
                        "Doctor",
                        4.5,
                        5.8,
                        m(
                                "Lifestyle",
                                "Meditation|Pet Lover|Early Bird",
                                "Personality & Values",
                                "Spiritual|Family-Oriented",
                                "Relationship Intent",
                                "Marriage",
                                "Food & Drinks",
                                "Fine Dining|Wine",
                                "Travel & Culture",
                                "Luxury Travel|Mountains",
                                "Intellectual & Learning",
                                "Science|Self-Improvement",
                                "Music",
                                "Jazz",
                                "Movies & Shows",
                                "K-Dramas",
                                "Sports & Outdoor",
                                "Swimming",
                                "Gaming & Digital",
                                "Mobile Gaming",
                                "Arts & Creativity",
                                "Interior Design")));
        d.add(
                new DemoPerson(
                        "demo-04",
                        "Ravin Perera",
                        "male",
                        "female",
                        "Gampaha",
                        "Civil Servant",
                        3.5,
                        4.0,
                        m(
                                "Lifestyle",
                                "Fitness|Traveling",
                                "Relationship Intent",
                                "Still Figuring It Out",
                                "Food & Drinks",
                                "Spicy Food|Street Food",
                                "Sports & Outdoor",
                                "Cricket|Cycling",
                                "Intellectual & Learning",
                                "History|Philosophy",
                                "Music",
                                "Rock",
                                "Movies & Shows",
                                "Sitcoms",
                                "Personality & Values",
                                "Politically Active",
                                "Travel & Culture",
                                "Cultural Festivals",
                                "Gaming & Digital",
                                "Console Gaming",
                                "Arts & Creativity",
                                "DIY & Crafts")));
        d.add(
                new DemoPerson(
                        "demo-05",
                        "Tharushi Silva",
                        "female",
                        "male",
                        "Gampaha",
                        "Marketing Executive",
                        3.1,
                        2.8,
                        m(
                                "Lifestyle",
                                "Nightlife|Fitness",
                                "Relationship Intent",
                                "Casual Dating",
                                "Music",
                                "Hip-Hop|K-Pop",
                                "Movies & Shows",
                                "Horror|Anime",
                                "Food & Drinks",
                                "Craft Beer|Baking",
                                "Sports & Outdoor",
                                "Basketball",
                                "Intellectual & Learning",
                                "Startups|Technology",
                                "Personality & Values",
                                "Feminist",
                                "Travel & Culture",
                                "Beaches",
                                "Gaming & Digital",
                                "VR",
                                "Arts & Creativity",
                                "Fashion|Filmmaking")));
        d.add(
                new DemoPerson(
                        "demo-06",
                        "Kasun Jayawardena",
                        "male",
                        "female",
                        "Kandy",
                        "Bank Officer",
                        2.5,
                        2.9,
                        m(
                                "Lifestyle",
                                "Early Bird|Pet Lover",
                                "Relationship Intent",
                                "Marriage",
                                "Personality & Values",
                                "Religious|Family-Oriented",
                                "Food & Drinks",
                                "Cooking|Coffee",
                                "Travel & Culture",
                                "Mountains|Road Trips",
                                "Sports & Outdoor",
                                "Hiking|Camping",
                                "Intellectual & Learning",
                                "Books & Reading",
                                "Music",
                                "Classical",
                                "Movies & Shows",
                                "Romance",
                                "Gaming & Digital",
                                "Board Games",
                                "Arts & Creativity",
                                "Poetry")));
        d.add(
                new DemoPerson(
                        "demo-07",
                        "Dilhani Ratnayake",
                        "female",
                        "male",
                        "Kandy",
                        "Nurse",
                        4.2,
                        4.1,
                        m(
                                "Lifestyle",
                                "Yoga|Meditation",
                                "Relationship Intent",
                                "Long-Term Relationship",
                                "Intellectual & Learning",
                                "Psychology|Self-Improvement",
                                "Food & Drinks",
                                "Vegetarian|Cooking",
                                "Personality & Values",
                                "Environmentalist",
                                "Travel & Culture",
                                "Backpacking|Museums",
                                "Sports & Outdoor",
                                "Hiking|Swimming",
                                "Music",
                                "Indie|Singing",
                                "Movies & Shows",
                                "Documentaries",
                                "Gaming & Digital",
                                "Dungeons & Dragons",
                                "Arts & Creativity",
                                "Painting")));
        d.add(
                new DemoPerson(
                        "demo-08",
                        "Jordan Lee",
                        "non_binary",
                        "any",
                        "Colombo",
                        "Journalist",
                        3.4,
                        3.9,
                        m(
                                "Lifestyle",
                                "Digital Nomad|Night Owl",
                                "Relationship Intent",
                                "Open Relationship",
                                "Personality & Values",
                                "Feminist|Atheist",
                                "Intellectual & Learning",
                                "Philosophy|Books & Reading",
                                "Music",
                                "Indie|Jazz",
                                "Movies & Shows",
                                "Thriller|Documentaries",
                                "Food & Drinks",
                                "Coffee|Wine",
                                "Travel & Culture",
                                "Languages|Cultural Festivals",
                                "Sports & Outdoor",
                                "Surfing",
                                "Gaming & Digital",
                                "Web3",
                                "Arts & Creativity",
                                "Writing|Poetry|Photography")));
        d.add(
                new DemoPerson(
                        "demo-09",
                        "Amaya Senanayake",
                        "female",
                        "non_binary",
                        "Colombo",
                        "Lawyer",
                        3.0,
                        3.3,
                        m(
                                "Lifestyle",
                                "Early Bird|Gym",
                                "Relationship Intent",
                                "Long-Term Relationship",
                                "Personality & Values",
                                "Career-Focused",
                                "Intellectual & Learning",
                                "Science|Technology",
                                "Food & Drinks",
                                "Fine Dining",
                                "Travel & Culture",
                                "Luxury Travel",
                                "Music",
                                "Classical|Pop",
                                "Movies & Shows",
                                "Sitcoms|Romance",
                                "Sports & Outdoor",
                                "Swimming",
                                "Gaming & Digital",
                                "Mobile Gaming",
                                "Arts & Creativity",
                                "Fashion")));
        d.add(
                new DemoPerson(
                        "demo-10",
                        "Ravi Kumar",
                        "male",
                        "female",
                        "Kalutara",
                        "Electrician",
                        4.0,
                        5.5,
                        m(
                                "Lifestyle",
                                "Fitness|Pet Lover",
                                "Relationship Intent",
                                "Marriage",
                                "Sports & Outdoor",
                                "Cricket|Football|Swimming",
                                "Food & Drinks",
                                "Spicy Food|Street Food",
                                "Music",
                                "Rock|Hip-Hop",
                                "Movies & Shows",
                                "Horror|Thriller",
                                "Intellectual & Learning",
                                "Technology",
                                "Travel & Culture",
                                "Beaches",
                                "Personality & Values",
                                "Family-Oriented",
                                "Gaming & Digital",
                                "Console Gaming",
                                "Arts & Creativity",
                                "Photography")));
        d.add(
                new DemoPerson(
                        "demo-11",
                        "Ishara Mendis",
                        "female",
                        "male",
                        "Kalutara",
                        "Tourism Guide",
                        2.8,
                        3.1,
                        m(
                                "Lifestyle",
                                "Traveling|Early Bird",
                                "Relationship Intent",
                                "Long-Term Relationship",
                                "Travel & Culture",
                                "Beaches|Road Trips|Languages",
                                "Food & Drinks",
                                "Street Food|Coffee",
                                "Sports & Outdoor",
                                "Swimming|Hiking",
                                "Music",
                                "Pop|Rock",
                                "Movies & Shows",
                                "Romance",
                                "Intellectual & Learning",
                                "History",
                                "Personality & Values",
                                "Environmentalist",
                                "Gaming & Digital",
                                "Board Games",
                                "Arts & Creativity",
                                "Photography|Painting")));
        d.add(
                new DemoPerson(
                        "demo-12",
                        "Chris Taylor",
                        "male",
                        "male",
                        "Colombo",
                        "Chef",
                        3.6,
                        4.2,
                        m(
                                "Lifestyle",
                                "Night Owl",
                                "Relationship Intent",
                                "Casual Dating",
                                "Food & Drinks",
                                "Fine Dining|Cooking|Wine",
                                "Personality & Values",
                                "Career-Focused",
                                "Music",
                                "Jazz",
                                "Movies & Shows",
                                "Documentaries",
                                "Travel & Culture",
                                "Luxury Travel|Road Trips",
                                "Sports & Outdoor",
                                "Swimming",
                                "Intellectual & Learning",
                                "Self-Improvement",
                                "Gaming & Digital",
                                "PC Gaming",
                                "Arts & Creativity",
                                "Interior Design")));
        DEMO_PEOPLE = Collections.unmodifiableList(d);
    }

    @NonNull
    public static String formatEcrLine(double anxiety, double avoidance) {
        return String.format(
                Locale.US,
                "ECR-RS · Anxiety %.1f · Avoidance %.1f (1–7)",
                anxiety,
                avoidance);
    }
}
