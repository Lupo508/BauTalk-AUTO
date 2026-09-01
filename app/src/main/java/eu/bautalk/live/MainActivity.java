package eu.bautalk.live;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQ_MIC = 11;
    private static final String AUTO_UTTERANCE = "bautalk_auto";

    private Spinner leftLang, rightLang;
    private Button autoButton, leftButton, rightButton, swapButton;
    private TextView status, live, history;
    private ScrollView historyScroll;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private OpenAITranslator translator;
    private final Handler main = new Handler(Looper.getMainLooper());

    private boolean autoMode = false;
    private boolean busy = false;
    private Lang autoSource;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        translator = new OpenAITranslator(this);
        tts = new TextToSpeech(this, this);
        setContentView(buildUi());
        refreshUi();
        requestMicIfNeeded();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.setBackgroundColor(Color.rgb(245,247,250));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("BauTalk AUTO  SR • PL • DE");
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(25,35,50));
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button settings = smallButton("⚙");
        settings.setOnClickListener(v -> showSettings());
        top.addView(settings);
        root.addView(top);

        TextView sub = new TextView(this);
        sub.setText("Govori → prevod → glas → automatski sluša odgovor druge strane.");
        sub.setTextSize(14);
        sub.setTextColor(Color.DKGRAY);
        sub.setPadding(0, dp(4), 0, dp(10));
        root.addView(sub);

        LinearLayout langRow = new LinearLayout(this);
        langRow.setGravity(Gravity.CENTER);
        leftLang = langSpinner(0);
        rightLang = langSpinner(1);
        langRow.addView(leftLang, new LinearLayout.LayoutParams(0, dp(52), 1));
        swapButton = smallButton("⇄");
        swapButton.setOnClickListener(v -> {
            int a = leftLang.getSelectedItemPosition();
            leftLang.setSelection(rightLang.getSelectedItemPosition());
            rightLang.setSelection(a);
            refreshUi();
        });
        langRow.addView(swapButton, new LinearLayout.LayoutParams(dp(58), dp(52)));
        langRow.addView(rightLang, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(langRow);

        leftLang.setOnItemSelectedListener(new SimpleItemSelected(this::refreshUi));
        rightLang.setOnItemSelectedListener(new SimpleItemSelected(this::refreshUi));

        autoButton = bigButton("▶  AUTO RAZGOVOR");
        autoButton.setTextSize(22);
        autoButton.setOnClickListener(v -> { if (autoMode) stopAuto("AUTO razgovor zaustavljen."); else startAuto(); });
        LinearLayout.LayoutParams autoLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(88));
        autoLp.bottomMargin = dp(10);
        root.addView(autoButton, autoLp);

        leftButton = bigButton("");
        leftButton.setOnClickListener(v -> listenOnce(currentLeft(), currentRight(), false));
        root.addView(leftButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));

        rightButton = bigButton("");
        rightButton.setOnClickListener(v -> listenOnce(currentRight(), currentLeft(), false));
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68));
        rightLp.topMargin = dp(6);
        root.addView(rightButton, rightLp);

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(Color.DKGRAY);
        status.setPadding(0, dp(10), 0, dp(5));
        root.addView(status);

        live = new TextView(this);
        live.setText("Izaberi dva jezika i pritisni AUTO RAZGOVOR.");
        live.setTextSize(18);
        live.setTextColor(Color.rgb(30,30,30));
        live.setPadding(dp(12), dp(12), dp(12), dp(12));
        live.setBackgroundColor(Color.WHITE);
        root.addView(live);

        LinearLayout histTop = new LinearLayout(this);
        histTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView hh = new TextView(this);
        hh.setText("Razgovor");
        hh.setTextSize(16);
        hh.setTypeface(Typeface.DEFAULT_BOLD);
        histTop.addView(hh, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button clear = smallButton("Obriši");
        clear.setOnClickListener(v -> history.setText(""));
        histTop.addView(clear);
        root.addView(histTop);

        historyScroll = new ScrollView(this);
        history = new TextView(this);
        history.setTextSize(16);
        history.setTextColor(Color.rgb(35,35,35));
        history.setPadding(dp(8), dp(8), dp(8), dp(20));
        historyScroll.addView(history);
        root.addView(historyScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private Spinner langSpinner(int initial) {
        Spinner s = new Spinner(this);
        ArrayAdapter<Lang> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Lang.values());
        s.setAdapter(a);
        s.setSelection(initial);
        return s;
    }

    private Button smallButton(String text) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); return b;
    }

    private Button bigButton(String text) {
        Button b = new Button(this); b.setText(text); b.setTextSize(19); b.setTypeface(Typeface.DEFAULT_BOLD); b.setAllCaps(false); return b;
    }

    private Lang currentLeft() { return (Lang) leftLang.getSelectedItem(); }
    private Lang currentRight() { return (Lang) rightLang.getSelectedItem(); }

    private void refreshUi() {
        if (leftButton == null) return;
        leftButton.setText("🎙  JEDNOM: " + currentLeft().label.toUpperCase(Locale.ROOT));
        rightButton.setText("🎙  JEDNOM: " + currentRight().label.toUpperCase(Locale.ROOT));
        String api = translator != null && translator.hasKey() ? "API spreman" : "unesi API ključ u ⚙";
        status.setText("AUTO radi rečenicu po rečenicu • " + api);
        boolean enabled = !autoMode && !busy;
        leftButton.setEnabled(enabled);
        rightButton.setEnabled(enabled);
        leftLang.setEnabled(enabled);
        rightLang.setEnabled(enabled);
        swapButton.setEnabled(enabled);
        autoButton.setText(autoMode ? "■  ZAUSTAVI AUTO" : "▶  AUTO RAZGOVOR");
    }

    private void requestMicIfNeeded() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
    }

    private boolean ensureMic() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicIfNeeded(); return false;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Na telefonu nije dostupan Speech Recognition servis.", Toast.LENGTH_LONG).show(); return false;
        }
        return true;
    }

    private void startAuto() {
        if (!ensureMic()) return;
        if (!translator.hasKey()) { showSettings(); return; }
        if (currentLeft() == currentRight()) { Toast.makeText(this, "Izaberi dva različita jezika.", Toast.LENGTH_LONG).show(); return; }
        autoMode = true;
        autoSource = currentLeft();
        busy = false;
        refreshUi();
        live.setText("🟢 AUTO uključen\nPrvo govori " + autoSource.label + ".");
        main.postDelayed(this::listenAuto, 300);
    }

    private void stopAuto(String msg) {
        autoMode = false;
        busy = false;
        main.removeCallbacksAndMessages(null);
        stopRecognizer();
        if (tts != null) tts.stop();
        refreshUi();
        if (msg != null) live.setText(msg);
    }

    private void listenAuto() {
        if (!autoMode || busy) return;
        Lang target = autoSource == currentLeft() ? currentRight() : currentLeft();
        listenOnce(autoSource, target, true);
    }

    private void listenOnce(Lang source, Lang target, boolean fromAuto) {
        if (!ensureMic()) return;
        if (!translator.hasKey()) { showSettings(); return; }
        stopRecognizer();
        busy = true;
        refreshUi();
        live.setText("🎙 Slušam: " + source.label + "…");
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() { live.setText("🎙 Govori: " + source.label); }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { live.setText("⏳ Obrada govora…"); }
            @Override public void onEvent(int eventType, Bundle params) {}
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> p = partialResults == null ? null : partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (p != null && !p.isEmpty()) live.setText("🎙 " + p.get(0));
            }
            @Override public void onError(int error) {
                stopRecognizer();
                busy = false;
                refreshUi();
                if (fromAuto && autoMode) {
                    live.setText("Nisam čuo. Ponovo slušam " + source.label + "…");
                    main.postDelayed(() -> { if (autoMode) listenAuto(); }, 850);
                } else live.setText("Govor nije prepoznat. Pokušaj ponovo.");
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> r = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                stopRecognizer();
                if (r == null || r.isEmpty() || r.get(0).trim().isEmpty()) {
                    busy = false; refreshUi();
                    if (fromAuto && autoMode) main.postDelayed(() -> { if (autoMode) listenAuto(); }, 700);
                    return;
                }
                translateAndSpeak(r.get(0).trim(), source, target, fromAuto);
            }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, source.speechTag);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, source.speechTag);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        try { recognizer.startListening(i); }
        catch (Exception e) {
            stopRecognizer(); busy = false; refreshUi(); live.setText("Mikrofon se nije mogao pokrenuti.");
            if (fromAuto && autoMode) main.postDelayed(this::listenAuto, 1000);
        }
    }

    private void translateAndSpeak(String original, Lang source, Lang target, boolean fromAuto) {
        live.setText("🗣 " + source.label + ": " + original + "\n\n⏳ Prevodim na " + target.label + "…");
        translator.translate(original, source, target, new TranslationCallback() {
            @Override public void onSuccess(String translated, String engine) {
                appendHistory(source, original, target, translated);
                live.setText("🗣 " + original + "\n\n🔊 " + translated);
                if (fromAuto && autoMode) autoSource = target;
                speak(translated, target, fromAuto);
            }
            @Override public void onError(String message) {
                busy = false; refreshUi(); live.setText("⚠ Prevod nije uspio:\n" + message);
                if (fromAuto) stopAuto("AUTO zaustavljen.\n" + message);
            }
        });
    }

    private void speak(String text, Lang lang, boolean continueAuto) {
        if (tts == null) { finishTurn(continueAuto); return; }
        int result = tts.setLanguage(lang.locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(this, "Glas za " + lang.label + " nije instaliran. Prevod je prikazan kao tekst.", Toast.LENGTH_LONG).show();
            finishTurn(continueAuto); return;
        }
        String id = continueAuto ? AUTO_UTTERANCE + System.currentTimeMillis() : "manual_" + System.currentTimeMillis();
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) finishTurn(continueAuto);
        else if (!continueAuto) { busy = false; refreshUi(); }
    }

    private void finishTurn(boolean continueAuto) {
        busy = false; refreshUi();
        if (continueAuto && autoMode) main.postDelayed(this::listenAuto, 500);
    }

    private void appendHistory(Lang s, String original, Lang t, String translated) {
        history.append(s.label + ":  " + original + "\n" + t.label + ":  " + translated + "\n\n");
        historyScroll.post(() -> historyScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), 0);
        TextView info = new TextView(this);
        info.setText("Unesi svoj OpenAI API ključ. Čuva se šifrovano na telefonu pomoću Android Keystore-a.");
        box.addView(info);
        EditText key = new EditText(this);
        key.setHint("sk-… OpenAI API key");
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        String existing = SecretStore.load(this);
        if (!existing.isEmpty()) key.setText(existing);
        box.addView(key);
        new AlertDialog.Builder(this).setTitle("Podešavanja")
                .setView(box)
                .setPositiveButton("Sačuvaj", (d,w) -> {
                    try {
                        String value = key.getText().toString().trim();
                        if (value.isEmpty()) SecretStore.clear(this); else SecretStore.save(this, value);
                        refreshUi(); Toast.makeText(this, "Sačuvano.", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) { Toast.makeText(this, "Ključ nije mogao biti sačuvan.", Toast.LENGTH_LONG).show(); }
                })
                .setNeutralButton("Obriši ključ", (d,w) -> { SecretStore.clear(this); refreshUi(); })
                .setNegativeButton("Nazad", null).show();
    }

    private void stopRecognizer() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    @Override public void onInit(int code) {
        if (code != TextToSpeech.SUCCESS || tts == null) return;
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onDone(String utteranceId) {
                if (utteranceId != null && utteranceId.startsWith(AUTO_UTTERANCE)) main.post(() -> finishTurn(true));
            }
            @Override public void onError(String utteranceId) {
                if (utteranceId != null && utteranceId.startsWith(AUTO_UTTERANCE)) main.post(() -> finishTurn(true));
            }
        });
    }

    @Override public void onBackPressed() {
        if (autoMode) stopAuto("AUTO razgovor zaustavljen."); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        autoMode = false; main.removeCallbacksAndMessages(null); stopRecognizer();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
