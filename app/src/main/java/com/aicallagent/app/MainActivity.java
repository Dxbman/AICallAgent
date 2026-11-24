package com.aicallagent.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.view.View;
import android.widget.Toast;
import com.aicallagent.app.local.LocalModelRuntime;
import com.aicallagent.app.local.LocalModelActivity;
import java.io.IOException;

import com.aicallagent.app.model.CallStateModel;
import com.aicallagent.app.model.AutoReplyFlow;

/**
 * AI 통화 비서의 기본 메인 화면.
 * 접근성 서비스의 실제 연결 상태와 마지막으로 관찰된 통화 상태를 안전하게 표시합니다.
 */
public class MainActivity extends Activity {

    private static final String DEFAULT_CALL_GREETING = "معك المساعد الآلي، تفضل.";
    private static final String GREETING_PREFS = "call_greeting";
    private static final String GREETING_KEY = "text";
    private static final String AI_PREFS = "ai_settings";

    private TextView textServiceStatus;
    private TextView textCallState;
    private Button btnOpenSettings;
    private Button btnAutoReply;
    private TextView textReplyStatus;
    private TextView textDiagnostics;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusRefresher = new Runnable() {
        @Override
        public void run() {
            updateUi();
            mainHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textServiceStatus = findViewById(R.id.text_service_status);
        textCallState = findViewById(R.id.text_call_state);
        btnOpenSettings = findViewById(R.id.btn_open_accessibility_settings);
        btnAutoReply = findViewById(R.id.btn_auto_reply);
        textReplyStatus = findViewById(R.id.text_reply_status);
        textDiagnostics = findViewById(R.id.text_call_diagnostics);
        findViewById(R.id.btn_open_local_model).setOnClickListener(v ->
                startActivity(new Intent(this, com.aicallagent.app.local.LocalModelActivity.class)));
        addCallGreetingControl();
        addAiSettingsControl();
        addVoiceTestControl();
        btnAutoReply.setOnClickListener(v -> {
            if (CallAccessibilityService.isAutoReplyEnabled() || CallAccessibilityService.isModelPreparing()) {
                CallAccessibilityService.setAutoReplyEnabled(false);
                updateUi();
            } else if (!CallAccessibilityService.hasPhoneStatePermission(this)) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.phone_state_permission_title)
                        .setMessage(R.string.phone_state_permission_notice)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.phone_state_permission_request, (dialog, which) ->
                                requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, 15))
                        .show();
            } else if (!hasLocalModel()) {
                startActivity(new Intent(this, LocalModelActivity.class));
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.auto_reply_confirm_title)
                        .setMessage(R.string.auto_reply_confirm_message)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.auto_reply_start, (dialog, which) -> {
                            CallAccessibilityService.setAutoReplyEnabled(true);
                            updateUi();
                        }).show();
            }
        });

        btnOpenSettings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
    }

    // The system browser owns microphone consent. No phone-call audio is captured here.
    private void addVoiceTestControl() {
        Button modelButton = findViewById(R.id.btn_open_local_model);
        if (!(modelButton.getParent() instanceof LinearLayout parent)) return;
        Button voiceTest = new Button(this);
        voiceTest.setText("OpenAI Voice Test / تجربة الصوت");
        parent.addView(voiceTest, parent.indexOfChild(modelButton),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        voiceTest.setOnClickListener(v -> {
            if (CallAccessibilityService.isAutoReplyEnabled()
                    || CallAccessibilityService.isModelPreparing()
                    || Boolean.TRUE.equals(CallAccessibilityService.getSystemCallActive())) {
                Toast.makeText(this, "أوقف الرد التلقائي وأنهِ المكالمة أولاً.", Toast.LENGTH_LONG).show();
                return;
            }
            final String origin;
            try {
                origin = AiServerEndpoint.normalizeOrigin(getSharedPreferences(AI_PREFS, MODE_PRIVATE)
                        .getString("server_origin", ""));
            } catch (IllegalArgumentException invalid) {
                Toast.makeText(this, "أضف عنوان خادم HTTPS في AI Settings أولاً.", Toast.LENGTH_LONG).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("تجربة صوت OpenAI")
                    .setMessage("اختبار مستقل في المتصفح. لا تستخدمه أثناء مكالمة هاتفية. "
                            + "سيطلب المتصفح إذن المايكروفون قبل إرسال صوتك إلى OpenAI. "
                            + "قد تُحتسب رسوم API. تأكد من أن عنوان الخادم يعود لك.")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("فتح الاختبار / Open", (d, w) -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    android.net.Uri.parse(origin + "/voice-test"))
                                    .addCategory(Intent.CATEGORY_BROWSABLE));
                        } catch (android.content.ActivityNotFoundException unavailable) {
                            Toast.makeText(this, "لا يوجد متصفح متاح.", Toast.LENGTH_SHORT).show();
                        }
                    }).show();
        });
    }

    private void addAiSettingsControl() {
        Button modelButton = findViewById(R.id.btn_open_local_model);
        if (!(modelButton.getParent() instanceof LinearLayout parent)) return;
        Button settings = new Button(this);
        settings.setText("AI Settings / إعدادات الذكاء الاصطناعي");
        parent.addView(settings, parent.indexOfChild(modelButton),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        settings.setOnClickListener(v -> showAiSettings());
    }

    // These preferences are preparation only: the call service still uses local Gemma.
    // No call content, API keys or authentication credentials are collected here.
    private void showAiSettings() {
        var prefs = getSharedPreferences(AI_PREFS, MODE_PRIVATE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, 0);

        TextView notice = new TextView(this);
        notice.setText("المحرك الفعّال حالياً: Gemma المحلي. اختر المحرك المفضل للإعداد المستقبلي. OpenAI غير مفعّل للمكالمات.\nActive engine: local Gemma. OpenAI is setup only; calls remain local.");
        content.addView(notice);
        RadioGroup engines = new RadioGroup(this);
        engines.setOrientation(RadioGroup.VERTICAL);
        RadioButton local = new RadioButton(this);
        local.setId(View.generateViewId());
        local.setText("Gemma — محلي / Offline");
        RadioButton cloud = new RadioButton(this);
        cloud.setId(View.generateViewId());
        cloud.setText("OpenAI — إعداد مستقبلي / Setup only");
        engines.addView(local);
        engines.addView(cloud);
        engines.check("openai".equals(prefs.getString("preferred_engine", "gemma"))
                ? cloud.getId() : local.getId());
        content.addView(engines);

        EditText server = new EditText(this);
        server.setHint("https://your-server.example");
        server.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        server.setSingleLine(true);
        server.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2048)});
        server.setText(prefs.getString("server_origin", ""));
        content.addView(server, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView status = new TextView(this);
        status.setText("اختبار الاتصال يرسل GET /health فقط إلى الخادم المحدد عبر HTTPS، دون مفتاح API أو محتوى المكالمات. لا يعني نجاحه تفعيل OpenAI.\nThe test checks server reachability only.");
        content.addView(status);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(content);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("AI Settings / إعدادات الذكاء الاصطناعي")
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("حفظ / Save", null)
                .setNeutralButton("اختبار الاتصال / Test", null)
                .create();

        dialog.setOnShowListener(unused -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                boolean wantsCloud = engines.getCheckedRadioButtonId() == cloud.getId();
                String raw = server.getText().toString().trim();
                String origin;
                try {
                    origin = raw.isEmpty() && !wantsCloud ? "" : AiServerEndpoint.normalizeOrigin(raw);
                } catch (IllegalArgumentException invalid) {
                    server.setError("أدخل عنوان HTTPS فقط بدون مسار أو بيانات دخول.");
                    return;
                }
                prefs.edit().putString("preferred_engine", wantsCloud ? "openai" : "gemma")
                        .putString("server_origin", origin).apply();
                Toast.makeText(this, "تم الحفظ. المحرك الفعّال: Gemma", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                final String origin;
                try {
                    origin = AiServerEndpoint.normalizeOrigin(server.getText().toString());
                    server.setError(null);
                } catch (IllegalArgumentException invalid) {
                    server.setError("أدخل عنوان HTTPS صالحاً.");
                    return;
                }
                Button test = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                test.setEnabled(false);
                status.setText("جاري اختبار الخادم / Testing HTTPS connection");
                new Thread(() -> {
                    String message;
                    javax.net.ssl.HttpsURLConnection connection = null;
                    try {
                        connection = (javax.net.ssl.HttpsURLConnection)
                                AiServerEndpoint.healthUri(origin).toURL().openConnection();
                        connection.setRequestMethod("GET");
                        connection.setInstanceFollowRedirects(false);
                        connection.setUseCaches(false);
                        connection.setConnectTimeout(5000);
                        connection.setReadTimeout(5000);
                        int code = connection.getResponseCode();
                        message = code >= 200 && code < 300
                                ? "الخادم يستجيب / Server reachable: HTTP " + code
                                    + ". OpenAI غير مفعّل للمكالمات."
                                : "استجابة الخادم / Server response: HTTP " + code
                                    + ". Health check unsuccessful.";
                    } catch (java.io.IOException | RuntimeException failure) {
                        message = "تعذّر اتصال HTTPS بالخادم / HTTPS connection failed.";
                    } finally {
                        if (connection != null) connection.disconnect();
                    }
                    final String result = message;
                    runOnUiThread(() -> {
                        if (dialog.isShowing() && !isFinishing() && !isDestroyed()) {
                            status.setText(result);
                            test.setEnabled(true);
                        }
                    });
                }, "ai-health-check").start();
            });
        });
        dialog.show();
    }

    // Samsung Phone owns the spoken opening greeting; never automate settings or clipboard reads.
    private void addCallGreetingControl() {
        Button modelButton = findViewById(R.id.btn_open_local_model);
        if (!(modelButton.getParent() instanceof LinearLayout parent)) return;
        Button greetingButton = new Button(this);
        greetingButton.setText("Call greeting / ترحيب المكالمة");
        parent.addView(greetingButton, parent.indexOfChild(modelButton),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        greetingButton.setOnClickListener(v -> showCallGreetingEditor());
    }

    private void showCallGreetingEditor() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, 0);
        TextView help = new TextView(this);
        help.setText("عدّل الترحيب واحفظه داخل التطبيق. لتفعيله صوتياً، انسخه والصقه في Samsung Phone > Text Call > Greetings، عند التحويل من مكالمة صوتية إلى Text Call.\nSamsung Phone controls the spoken greeting.");
        content.addView(help);

        EditText editor = new EditText(this);
        editor.setHint("ترحيب المكالمة / Call greeting");
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setSingleLine(true);
        editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        editor.setText(getSharedPreferences(GREETING_PREFS, MODE_PRIVATE)
                .getString(GREETING_KEY, DEFAULT_CALL_GREETING));
        editor.setSelectAllOnFocus(false);
        content.addView(editor, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button reset = new Button(this);
        reset.setText("استعادة الأصل / Reset");
        reset.setOnClickListener(v -> {
            editor.setText(DEFAULT_CALL_GREETING);
            editor.setSelection(editor.getText().length());
            editor.setError(null);
        });
        content.addView(reset);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("ترحيب المكالمة")
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("حفظ / Save", null)
                .setNeutralButton("حفظ ونسخ / Copy", null)
                .create();
        dialog.setOnShowListener(unused -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (saveGreeting(editor, false)) dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                if (saveGreeting(editor, true)) dialog.dismiss();
            });
        });
        dialog.show();
    }

    private boolean saveGreeting(EditText editor, boolean copy) {
        String greeting = editor.getText().toString().trim();
        if (greeting.isEmpty()) {
            editor.setError("اكتب الترحيب / Enter a greeting");
            return false;
        }
        getSharedPreferences(GREETING_PREFS, MODE_PRIVATE).edit()
                .putString(GREETING_KEY, greeting).apply();
        if (copy) {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Call greeting", greeting));
                Toast.makeText(this, "تم الحفظ والنسخ", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "تم الحفظ، تعذر النسخ", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 15) updateUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
        mainHandler.removeCallbacks(statusRefresher);
        mainHandler.postDelayed(statusRefresher, 1000);
    }

    @Override
    protected void onPause() {
        mainHandler.removeCallbacks(statusRefresher);
        super.onPause();
    }

    private void updateUi() {
        boolean connected = CallAccessibilityService.isServiceConnected();
        if (connected) {
            textServiceStatus.setText(R.string.status_service_connected);
        } else {
            textServiceStatus.setText(R.string.status_service_disconnected);
        }

        CallStateModel.CallState state = CallAccessibilityService.getLastObservedCallState();
        String stateText = state == CallStateModel.CallState.IDLE ? getString(R.string.call_not_observed)
                : getString(R.string.label_last_observed_state_format, getString(callStateLabel(state)));
        textCallState.setText(stateText);
        updateDiagnostics();
        boolean enabled = CallAccessibilityService.isAutoReplyEnabled();
        btnAutoReply.setText(CallAccessibilityService.isModelPreparing() ? R.string.local_cancel
                : enabled ? R.string.auto_reply_stop : R.string.auto_reply_start);
        btnAutoReply.setEnabled(connected);
        if (!connected) {
            textReplyStatus.setText(R.string.reply_needs_service);
        } else if (CallAccessibilityService.isModelPreparing()) {
            textReplyStatus.setText(R.string.local_loading);
        } else {
            textReplyStatus.setText(getString(R.string.reply_status_format, getString(replyStatus())));
        }
    }

    private void updateDiagnostics() {
        int window;
        switch (CallAccessibilityService.getWindowObservation()) {
            case NO_WINDOW: window = R.string.call_window_none; break;
            case OTHER_APP: window = R.string.call_window_other; break;
            case PHONE_NO_CONTROLS: window = R.string.call_window_no_controls; break;
            case CALL_VISIBLE: window = R.string.call_window_visible; break;
            case SYSTEM_IDLE: window = R.string.system_call_idle; break;
            default: window = R.string.call_not_observed; break;
        }
        String error = CallAccessibilityService.getLastFailure();
        String result = CallAccessibilityService.isGenerationRunning() ? getString(R.string.llm_call_running)
                : !error.isEmpty() ? LlmStatusText.error(this, error)
                : CallAccessibilityService.getGenerationResponseCount() > 0 ? getString(R.string.llm_call_success)
                : CallAccessibilityService.getGenerationRequestCount() > 0 ? getString(R.string.llm_call_no_response)
                : getString(R.string.llm_call_none);
        String model = getString(hasLocalModel() ? R.string.local_present : R.string.local_missing);
        Boolean inCall = CallAccessibilityService.getSystemCallActive();
        textDiagnostics.setText(getString(R.string.call_diagnostics_format, getString(window),
                CallAccessibilityService.getObservedSpeechCount(), CallAccessibilityService.getGenerationRequestCount(),
                CallAccessibilityService.getGenerationResponseCount(), CallAccessibilityService.getSentReplyCount(), result, model,
                getString(inCall == null
                        ? R.string.system_call_unknown : inCall
                        ? R.string.system_call_active : R.string.system_call_idle),
                getString(CallAccessibilityService.isDiagnosticInputReady() ? R.string.diagnostic_yes : R.string.diagnostic_no),
                getString(CallAccessibilityService.isDiagnosticInputMatches() ? R.string.diagnostic_yes : R.string.diagnostic_no),
                getString(CallAccessibilityService.isDiagnosticSubmitReady() ? R.string.diagnostic_yes : R.string.diagnostic_no)));
    }

    private boolean hasLocalModel() {
        try { return LocalModelRuntime.get(this).hasModel(); }
        catch (IOException failure) { return false; }
    }

    private int replyStatus() {
        if (!CallAccessibilityService.hasPhoneStatePermission(this)) return R.string.phone_state_permission_missing;
        switch (CallAccessibilityService.getReplyStage()) {
            case WAIT_INCOMING: return R.string.reply_waiting;
            case WAIT_CONNECTED: return R.string.reply_answering;
            case WAIT_ASSIST:
            case WAIT_TEXT:
            case WAIT_TEXT_ACTIVE: return R.string.reply_converting;
            case WAIT_REMOTE: return R.string.reply_listening;
            case WAIT_LLM: return R.string.reply_wait_llm;
            case WAIT_LLM_RETRY: return R.string.reply_retry_llm;
            case WAIT_INPUT:
            case VERIFY_DRAFT_CLEARED:
            case VERIFY_INPUT: return R.string.reply_preparing;
            case VERIFY_SENT: return R.string.reply_sending;
            case WAIT_SPEECH: return R.string.reply_wait_speech;
            case WAIT_END: return R.string.reply_wait_end;
            case FAILED:
                AutoReplyFlow.Failure failure = CallAccessibilityService.getReplyFailure();
                if (failure == AutoReplyFlow.Failure.EXISTING_INPUT) return R.string.reply_existing_input;
                if (failure == AutoReplyFlow.Failure.INPUT_CHANGED) return R.string.reply_input_changed;
                if (failure == AutoReplyFlow.Failure.LLM_FAILED) return R.string.reply_llm_failed;
                if (failure == AutoReplyFlow.Failure.CONTEXT_LIMIT) return R.string.reply_context_limit;
                if (failure == AutoReplyFlow.Failure.TIMEOUT) return R.string.reply_timeout;
                if (failure == AutoReplyFlow.Failure.SEND_REJECTED) return R.string.reply_send_rejected;
                if (failure == AutoReplyFlow.Failure.SEND_UNCONFIRMED) return R.string.reply_send_unconfirmed;
                if (failure == AutoReplyFlow.Failure.SPEECH_BUSY_TIMEOUT) return R.string.reply_speech_timeout;
                return R.string.reply_action_failed;
            case OFF:
            default: return R.string.reply_off;
        }
    }

    private static int callStateLabel(CallStateModel.CallState state) {
        if (state == null) return R.string.call_state_idle;
        switch (state) {
            case RINGING_INCOMING: return R.string.call_state_ringing;
            case CONNECTED_VOICE: return R.string.call_state_connected_voice;
            case AI_MENU_AVAILABLE: return R.string.call_state_ai_menu_available;
            case TEXT_CALL_ACTIVE: return R.string.call_state_text_call_active;
            case ENDED: return R.string.call_state_ended;
            default: return R.string.call_state_idle;
        }
    }
}
