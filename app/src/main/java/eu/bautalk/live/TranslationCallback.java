package eu.bautalk.live;

public interface TranslationCallback {
    void onSuccess(String translated, String engine);
    void onError(String message);
}
