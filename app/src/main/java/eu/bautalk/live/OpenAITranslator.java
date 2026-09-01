package eu.bautalk.live;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class OpenAITranslator {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    public OpenAITranslator(Context context) { this.context = context.getApplicationContext(); }
    public boolean hasKey() { return !SecretStore.load(context).trim().isEmpty(); }

    public void translate(String text, Lang source, Lang target, TranslationCallback cb) {
        final String key = SecretStore.load(context).trim();
        if (key.isEmpty()) { cb.onError("Nema OpenAI API ključa. Otvori ⚙ Podešavanja."); return; }
        new Thread(() -> {
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
                con.setRequestMethod("POST");
                con.setConnectTimeout(12000);
                con.setReadTimeout(30000);
                con.setDoOutput(true);
                con.setRequestProperty("Authorization", "Bearer " + key);
                con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                JSONObject body = new JSONObject();
                body.put("model", "gpt-5-nano");
                body.put("instructions", "Translate accurately from " + source.label + " to " + target.label + ". This is a construction and HVAC worksite conversation. Preserve technical terms, names, numbers and dimensions. Return only the translation.");
                body.put("input", text);
                body.put("max_output_tokens", 220);
                byte[] out = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = con.getOutputStream()) { os.write(out); }
                int code = con.getResponseCode();
                InputStream is = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
                String response = readAll(is);
                if (code < 200 || code >= 300) throw new Exception("OpenAI HTTP " + code + ": " + compactError(response));
                String translated = extractOutputText(new JSONObject(response));
                if (translated.trim().isEmpty()) throw new Exception("Prazan prevod iz API-ja.");
                main.post(() -> cb.onSuccess(translated.trim(), "OpenAI"));
            } catch (Exception e) {
                main.post(() -> cb.onError(e.getMessage() == null ? "Greška online prevoda." : e.getMessage()));
            } finally { if (con != null) con.disconnect(); }
        }).start();
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String extractOutputText(JSONObject root) {
        StringBuilder sb = new StringBuilder();
        JSONArray output = root.optJSONArray("output");
        if (output == null) return "";
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i); if (item == null) continue;
            JSONArray content = item.optJSONArray("content"); if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject c = content.optJSONObject(j);
                if (c != null && "output_text".equals(c.optString("type"))) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(c.optString("text", ""));
                }
            }
        }
        return sb.toString();
    }

    private static String compactError(String raw) {
        try { JSONObject e = new JSONObject(raw).optJSONObject("error"); if (e != null) return e.optString("message", raw); } catch (Exception ignored) {}
        return raw.length() > 240 ? raw.substring(0, 240) : raw;
    }
}
