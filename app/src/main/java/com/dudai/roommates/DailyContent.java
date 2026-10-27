package com.dudai.roommates;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Picks today's items for the rotating widget from assets/content.json.
 * The choice depends only on the date, so it changes by itself every day and needs no updates.
 */
public class DailyContent {

    /** One card in the rotating widget. */
    public static class Card {
        public final String chip;       // small label at the top, e.g. "מילת היום"
        public final int chipColor;
        public final String title;
        public final String body;
        public final String extra;      // example sentence, or the joke's answer
        public final boolean english;   // English word: title and example are left-to-right
        public final boolean revealExtra;  // joke: the answer appears a few seconds later
        public final int titleSize;

        Card(String chip, int chipColor, String title, String body, String extra,
             boolean english, boolean revealExtra, int titleSize) {
            this.chip = chip;
            this.chipColor = chipColor;
            this.title = title;
            this.body = body;
            this.extra = extra;
            this.english = english;
            this.revealExtra = revealExtra;
            this.titleSize = titleSize;
        }
    }

    private static final int PINK = 0xFFFF8FC7, MINT = 0xFF7FE3C4, PEACH = 0xFFFFB37A, BLUE = 0xFF8CCBFF;

    /** Today's four cards: word, meal, animal fact, joke. */
    public static List<Card> today(Context ctx) {
        List<Card> cards = new ArrayList<>();
        try {
            JSONObject c = new JSONObject(readAsset(ctx, "content.json"));
            long day = LocalDate.now().toEpochDay();

            // Word of the day: Hebrew on even days, English on odd days
            if (day % 2 == 0) {
                JSONObject w = pick(c.getJSONArray("words"), day / 2);
                cards.add(new Card("מילת היום", PINK, w.getString("word"),
                        w.getString("meaning"), "\"" + w.getString("example") + "\"", false, false, 48));
            } else {
                JSONObject w = pick(c.getJSONArray("englishWords"), day / 2);
                cards.add(new Card("Word of the day", PINK, w.getString("word"),
                        w.getString("meaning"), w.getString("example"), true, false, 48));
            }

            JSONObject m = pick(c.getJSONArray("meals"), day);
            cards.add(new Card("מה אוכלות היום?", MINT, m.getString("name"), m.getString("note"), "",
                    false, false, 36));

            JSONObject f = pick(c.getJSONArray("facts"), day);
            cards.add(new Card("עובדה על ה" + f.getString("animal") + " של " + f.getString("owner"), PEACH,
                    f.getString("text"), "", "", false, false, 30));

            JSONObject j = pick(c.getJSONArray("jokes"), day);
            cards.add(new Card("בדיחת היום", BLUE, j.getString("q"), "", j.getString("a"),
                    false, true, 32));
        } catch (Exception e) {
            cards.add(new Card("היום", PINK, "יום טוב!", "", "", false, false, 48));
        }
        return cards;
    }

    private static JSONObject pick(JSONArray list, long index) throws Exception {
        return list.getJSONObject((int) Math.floorMod(index, (long) list.length()));
    }

    private static String readAsset(Context ctx, String name) throws Exception {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
