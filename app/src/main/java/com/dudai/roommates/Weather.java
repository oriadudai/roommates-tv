package com.dudai.roommates;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Current weather, sunrise and sunset from Open-Meteo (free, no API key).
 * Call {@link #fetch} from a background thread.
 */
public class Weather {

    public int temperature;
    public int code;
    public String sunrise;   // "06:12"
    public String sunset;    // "17:01"

    public static Weather fetch(double lat, double lon) throws Exception {
        String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code"
                + "&daily=sunrise,sunset&timezone=Asia%2FJerusalem&forecast_days=1";

        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10_000);
        con.setReadTimeout(10_000);
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);

            JSONObject json = new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            JSONObject current = json.getJSONObject("current");
            JSONObject daily = json.getJSONObject("daily");

            Weather w = new Weather();
            w.temperature = (int) Math.round(current.getDouble("temperature_2m"));
            w.code = current.getInt("weather_code");
            w.sunrise = timeOf(daily.getJSONArray("sunrise").getString(0));
            w.sunset = timeOf(daily.getJSONArray("sunset").getString(0));
            return w;
        } finally {
            con.disconnect();
        }
    }

    /** "2026-10-27T06:12" -> "06:12" */
    private static String timeOf(String isoDateTime) {
        int t = isoDateTime.indexOf('T');
        return t >= 0 ? isoDateTime.substring(t + 1, Math.min(t + 6, isoDateTime.length())) : isoDateTime;
    }

    /** Short Hebrew description of the WMO weather code. */
    public String description() {
        if (code == 0) return "בהיר";
        if (code <= 2) return "מעונן חלקית";
        if (code == 3) return "מעונן";
        if (code <= 48) return "ערפל";
        if (code <= 57) return "טפטוף";
        if (code <= 67) return "גשם";
        if (code <= 77) return "שלג";
        if (code <= 82) return "ממטרים";
        return "סופת רעמים";
    }

    /** Which icon to show. */
    public int icon() {
        if (code <= 1) return R.drawable.wx_sun;
        if (code <= 48) return R.drawable.wx_cloud;
        return R.drawable.wx_rain;
    }
}
