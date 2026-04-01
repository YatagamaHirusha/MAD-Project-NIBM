package com.mad.cw;

/**
 * Interest categories and tag vocabularies aligned with {@code Model/updated_data.csv}
 * (used for training; app persistence should use the same strings for DB / inference).
 */
public final class InterestTaxonomy {

    private InterestTaxonomy() {}

    public static final class Category {
        public final String columnName;
        public final String[] tags;
        public final boolean singleSelection;

        public Category(String columnName, String[] tags, boolean singleSelection) {
            this.columnName = columnName;
            this.tags = tags;
            this.singleSelection = singleSelection;
        }
    }

    /**
     * Order matches the model dataset. Tags are the unique values observed in the training CSV.
     */
    public static final Category[] CATEGORIES = {
            new Category(
                    "Lifestyle",
                    new String[]{
                            "Digital Nomad", "Early Bird", "Fitness", "Gym", "Meditation", "Night Owl",
                            "Nightlife", "Pet Lover", "Traveling", "Vegan", "Vegetarian", "Yoga",
                    },
                    false
            ),
            new Category(
                    "Arts & Creativity",
                    new String[]{
                            "DIY & Crafts", "Fashion", "Filmmaking", "Graphic Design", "Interior Design",
                            "Painting", "Photography", "Poetry", "Writing",
                    },
                    false
            ),
            new Category(
                    "Music",
                    new String[]{
                            "Classical", "EDM", "Hip-Hop", "Indie", "Jazz", "K-Pop", "Playing Instruments",
                            "Pop", "Rock", "Singing",
                    },
                    false
            ),
            new Category(
                    "Movies & Shows",
                    new String[]{
                            "Anime", "Documentaries", "Horror", "K-Dramas", "Romance", "Sci-Fi", "Sitcoms",
                            "Thriller",
                    },
                    false
            ),
            new Category(
                    "Intellectual & Learning",
                    new String[]{
                            "AI & Machine Learning", "Books & Reading", "History", "Philosophy", "Psychology",
                            "Science", "Self-Improvement", "Startups", "Technology",
                    },
                    false
            ),
            new Category(
                    "Food & Drinks",
                    new String[]{
                            "Baking", "Coffee", "Cooking", "Craft Beer", "Fine Dining", "Spicy Food",
                            "Street Food", "Wine",
                    },
                    false
            ),
            new Category(
                    "Sports & Outdoor",
                    new String[]{
                            "Adventure Sports", "Basketball", "Camping", "Cricket", "Cycling", "Football",
                            "Hiking", "Surfing", "Swimming",
                    },
                    false
            ),
            new Category(
                    "Gaming & Digital",
                    new String[]{
                            "Board Games", "Console Gaming", "Crypto", "Dungeons & Dragons", "Mobile Gaming",
                            "PC Gaming", "VR", "Web3", "eSports",
                    },
                    false
            ),
            new Category(
                    "Travel & Culture",
                    new String[]{
                            "Backpacking", "Beaches", "Cultural Festivals", "Languages", "Luxury Travel",
                            "Mountains", "Museums", "Road Trips",
                    },
                    false
            ),
            new Category(
                    "Personality & Values",
                    new String[]{
                            "Atheist", "Career-Focused", "Environmentalist", "Family-Oriented", "Feminist",
                            "Politically Active", "Religious", "Spiritual",
                    },
                    false
            ),
            new Category(
                    "Relationship Intent",
                    new String[]{
                            "Casual Dating", "Long-Term Relationship", "Marriage", "Open Relationship",
                            "Still Figuring It Out",
                    },
                    true
            ),
    };
}
