package top.niunaijun.blackbox.fake.service;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.app.ActivityManager;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;
import android.os.Bundle;
import android.os.IBinder;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.env.AppSystemEnv;
import top.niunaijun.blackbox.core.env.ExternalActivityGuard;
import top.niunaijun.blackbox.core.env.SamsungHealthCompat;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.provider.FileProviderHandler;
import top.niunaijun.blackbox.utils.ComponentUtils;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;
import top.niunaijun.blackbox.utils.compat.StartActivityCompat;

import static android.content.pm.PackageManager.GET_META_DATA;


public class ActivityManagerCommonProxy {
    public static final String TAG = "CommonStub";
    private static final int START_RESULT_UNRESOLVED = Integer.MIN_VALUE;
    private static volatile int sStartIntentNotResolved = START_RESULT_UNRESOLVED;
    private static final String PKG_SAMSUNG_HEALTH = "com.sec.android.app.shealth";
    private static final String PKG_SAMSUNG_ACCOUNT = "com.osp.app.signin";
    private static final String ACTION_SAMSUNG_ACCOUNT_SIGNIN_POPUP = "com.msc.action.samsungaccount.SIGNIN_POPUP";
    private static final String ACTION_SAMSUNG_ACCOUNT_REQUEST_SIGN_IN_FROM_WEB_SDK = "com.samsung.android.samsungaccount.action.REQUEST_SIGN_IN_FROM_WEB_SDK";
    private static final String ACTION_SAMSUNG_ACCOUNT_REQUEST_CONFIRM_PASSWORD_FROM_WEB_SDK = "com.samsung.android.samsungaccount.action.REQUEST_CONFIRM_PASSWORD_FROM_WEB_SDK";
    private static final String ACTIVITY_SAMSUNG_HEALTH_AUTHENTICATOR = "com.samsung.android.app.shealth.accounts.AuthenticatorActivity";
    private static final String ACTIVITY_SAMSUNG_HEALTH_ACCOUNT_HANDLER = "com.samsung.android.app.shealth.jwt.AccountHandlerActivity";

    private static int getStartIntentNotResolvedCode() {
        int cached = sStartIntentNotResolved;
        if (cached != START_RESULT_UNRESOLVED) {
            return cached;
        }
        int resolved = -1;
        try {
            Field f = ActivityManager.class.getDeclaredField("START_INTENT_NOT_RESOLVED");
            f.setAccessible(true);
            resolved = f.getInt(null);
        } catch (Throwable ignored) {
        }
        sStartIntentNotResolved = resolved;
        return resolved;
    }

    @ProxyMethod("startActivity")
    public static class StartActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            Intent intent = getIntent(args);
            Slog.d(TAG, "Hook in : " + intent);
            assert intent != null;
            
            
            if (intent.getParcelableExtra("_B_|_target_") != null) {
                return method.invoke(who, args);
            }
            if (ComponentUtils.isRequestInstall(intent)) {
                File file = FileProviderHandler.convertFile(BActivityThread.getApplication(), intent.getData());
                
                
                if (file != null && file.exists()) {
                    try {
                        PackageInfo packageInfo = BlackBoxCore.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
                        if (packageInfo != null) {
                            String packageName = packageInfo.packageName;
                            String hostPackageName = BlackBoxCore.getHostPkg();
                            if (packageName.equals(hostPackageName)) {
                                Slog.w(TAG, "Blocked attempt to install BlackBox app from within BlackBox: " + packageName);
                                
                                return 0;
                            }
                        }
                    } catch (Exception e) {
                        Slog.w(TAG, "Could not verify if this is BlackBox app: " + e.getMessage());
                    }
                }
                
                if (BlackBoxCore.get().requestInstallPackage(file, BActivityThread.getUserId())) {
                    return 0;
                }
                intent.setData(FileProviderHandler.convertFileUri(BActivityThread.getApplication(), intent.getData()));
                return method.invoke(who, args);
            }
            String dataString = intent.getDataString();
            if (dataString != null && dataString.equals("package:" + BActivityThread.getAppPackageName())) {
                intent.setData(Uri.parse("package:" + BlackBoxCore.getHostPkg()));
            }

            // Samsung Health default behavior: keep sign-in inside BlackBox.
            // If it attempts to launch Samsung Account's SIGNIN_POPUP and the host fallback is
            // disabled, rewrite to Samsung Health's own AuthenticatorActivity (Custom Tab / web
            // flow) so the result is delivered back into Samsung Health inside the profile.
            Intent rewritten = maybeRewriteSamsungHealthSigninPopupToAuthenticator(intent);
            if (rewritten != null) {
                replaceIntentArg(args, intent, rewritten);
                intent = rewritten;
            }

            // Samsung Health AuthenticatorActivity uses SaSDKManager, which normally launches the
            // host Samsung Account app's WebSdkActivity (REQUEST_SIGN_IN_FROM_WEB_SDK). That host
            // activity enforces caller signature checks and will see the BlackBox host as caller.
            // When fallback is disabled, prefer a pure in-profile web flow by rewriting the SaSDK
            // request into an ACTION_VIEW https://... browser launch (so the sasdk:// redirect
            // returns back into Samsung Health's ResponseReceiverActivity inside the profile).
            Intent webSdkRewritten = maybeRewriteSamsungHealthSaSdkToBrowser(intent);
            if (webSdkRewritten != null) {
                replaceIntentArg(args, intent, webSdkRewritten);
                intent = webSdkRewritten;
            }

            ResolveInfo resolveInfo = BlackBoxCore.getBPackageManager().resolveActivity(
                    intent,
                    GET_META_DATA,
                    StartActivityCompat.getResolvedType(args),
                    BActivityThread.getUserId());
            if (resolveInfo == null) {
                java.util.List<ResolveInfo> candidates = BlackBoxCore.getBPackageManager().queryIntentActivities(
                        intent,
                        GET_META_DATA,
                        StartActivityCompat.getResolvedType(args),
                        BActivityThread.getUserId());
                if (candidates != null && !candidates.isEmpty()) {
                    resolveInfo = candidates.get(0);
                }
            }
            if (resolveInfo == null) {
                String origPackage = intent.getPackage();
                if (intent.getPackage() == null && intent.getComponent() == null) {
                    intent.setPackage(BActivityThread.getAppPackageName());
                } else {
                    origPackage = intent.getPackage();
                }
                resolveInfo = BlackBoxCore.getBPackageManager().resolveActivity(
                        intent,
                        GET_META_DATA,
                        StartActivityCompat.getResolvedType(args),
                        BActivityThread.getUserId());
                if (resolveInfo == null) {
                    java.util.List<ResolveInfo> candidates = BlackBoxCore.getBPackageManager().queryIntentActivities(
                            intent,
                            GET_META_DATA,
                            StartActivityCompat.getResolvedType(args),
                            BActivityThread.getUserId());
                    if (candidates != null && !candidates.isEmpty()) {
                        resolveInfo = candidates.get(0);
                    }
                }
                if (resolveInfo == null) {
                    intent.setPackage(origPackage);

                    // Default behavior for Samsung Health should not use the phone's Samsung
                    // Account app. If the app attempts the SamsungAccount SIGNIN_POPUP flow and
                    // the per-app fallback toggle is OFF, fail as if no activity was found so the
                    // app can take its normal browser-based path (e.g., Brave inside BlackBox).
                    if (shouldBlockSamsungHealthSamsungAccount(intent)) {
                        Slog.w(TAG, "Blocked SamsungAccount SIGNIN_POPUP for Samsung Health (fallback disabled)");
                        return getStartIntentNotResolvedCode();
                    }

                    // Do NOT hard-block SamsungAccount WebSdk requests here. Returning
                    // START_INTENT_NOT_RESOLVED causes Samsung Health's AuthenticatorActivity to
                    // crash (ActivityNotFoundException), and BlackBox's crash recovery can leave
                    // the window in a broken state that triggers an InputDispatcher ANR.
                    //
                    // The preferred privacy-first fix is to prevent Samsung Health's SaSDK from
                    // detecting the host Samsung Account package when fallback is disabled (see
                    // IPackageManagerProxy), so this request is never attempted.

                    launchOpenHostActivityInOwnTaskIfNeeded(intent, args);
                    return method.invoke(who, args);
                }
            }


            intent.setExtrasClassLoader(who.getClass().getClassLoader());
            intent.setComponent(new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name));
            BlackBoxCore.getBActivityManager().startActivityAms(BActivityThread.getUserId(),
                    StartActivityCompat.getIntent(args),
                    StartActivityCompat.getResolvedType(args),
                    StartActivityCompat.getResultTo(args),
                    StartActivityCompat.getResultWho(args),
                    StartActivityCompat.getRequestCode(args),
                    StartActivityCompat.getFlags(args),
                    StartActivityCompat.getOptions(args));
            return 0;
        }

        private Intent getIntent(Object[] args) {
            int index;
            if (BuildCompat.isR()) {
                index = 3;
            } else {
                index = 2;
            }
            if (args[index] instanceof Intent) {
                return (Intent) args[index];
            }
            for (Object arg : args) {
                if (arg instanceof Intent) {
                    return (Intent) arg;
                }
            }
            return null;
        }

        private void launchOpenHostActivityInOwnTaskIfNeeded(Intent intent, Object[] args) {
            ResolveInfo hostResolve = BlackBoxCore.getContext().getPackageManager()
                    .resolveActivity(intent, GET_META_DATA);
            if (hostResolve == null || hostResolve.activityInfo == null) {
                return;
            }
            if (!AppSystemEnv.isOpenPackage(hostResolve.activityInfo.packageName)) {
                return;
            }

            boolean didRedirectSamsungAccount = false;
            // SamsungAccount SIGNIN_POPUP enforces caller signature checks and will instantly finish
            // when the launch is attributed to the BlackBox host package. For Samsung Health, prefer
            // launching the SamsungAccount app's normal entry point as a compatibility fallback.
            try {
                String callerPackage = BActivityThread.getAppPackageName();
                int userId = BActivityThread.getUserId();
                boolean fallbackEnabled = SamsungHealthCompat.isHostSamsungAccountFallbackEnabled(userId, callerPackage);
                if (fallbackEnabled
                        && PKG_SAMSUNG_HEALTH.equals(callerPackage)
                        && ACTION_SAMSUNG_ACCOUNT_SIGNIN_POPUP.equals(intent.getAction())
                        && PKG_SAMSUNG_ACCOUNT.equals(hostResolve.activityInfo.packageName)) {
                    // Samsung Account does not necessarily expose a LAUNCHER activity; use the
                    // exported AccountView entry point that handles ADD_SAMSUNG_ACCOUNT.
                    Intent redirected = new Intent("com.samsung.android.samsungaccount.action.ADD_SAMSUNG_ACCOUNT");
                    redirected.setComponent(new ComponentName(PKG_SAMSUNG_ACCOUNT, "com.osp.app.signin.AccountView"));
                    if (intent.getExtras() != null) {
                        redirected.putExtras(intent.getExtras());
                    }
                    didRedirectSamsungAccount = true;
                    redirected.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    redirected.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                    replaceIntentArg(args, intent, redirected);
                    intent = redirected;
                    ResolveInfo redirectedResolve = BlackBoxCore.getContext().getPackageManager()
                            .resolveActivity(intent, GET_META_DATA);
                    if (redirectedResolve != null && redirectedResolve.activityInfo != null) {
                        hostResolve = redirectedResolve;
                    }
                    Slog.d(TAG, "Redirected SamsungAccount SIGNIN_POPUP to AccountView: " + intent.getComponent());
                }
            } catch (Throwable ignored) {
            }

            int requestCode = StartActivityCompat.getRequestCode(args);
            if (requestCode >= 0 && !didRedirectSamsungAccount) {
                return;
            }

            intent.setComponent(new ComponentName(
                    hostResolve.activityInfo.packageName,
                    hostResolve.activityInfo.name));

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

            // Clear any "for result" linkage (resultTo/resultWho/requestCode). Index ordering can
            // vary across framework versions, so prefer a best-effort scan after the Intent.
            stripActivityResultArgs(args, intent);
            ExternalActivityGuard.markOpenHostActivityLaunch(
                    BActivityThread.getAppPackageName(),
                    BActivityThread.getUserId(),
                    hostResolve.activityInfo.packageName);
            Slog.d(TAG, "Launching open host activity in separate task: " + intent.getComponent());
        }

        private boolean shouldBlockSamsungHealthSamsungAccount(Intent intent) {
            try {
                if (intent == null) {
                    return false;
                }
                String callerPackage = BActivityThread.getAppPackageName();
                if (!PKG_SAMSUNG_HEALTH.equals(callerPackage)) {
                    return false;
                }
                if (!ACTION_SAMSUNG_ACCOUNT_SIGNIN_POPUP.equals(intent.getAction())) {
                    return false;
                }

                int userId = BActivityThread.getUserId();
                if (SamsungHealthCompat.isHostSamsungAccountFallbackEnabled(userId, callerPackage)) {
                    return false;
                }

                ResolveInfo hostResolve = BlackBoxCore.getContext().getPackageManager()
                        .resolveActivity(intent, GET_META_DATA);
                if (hostResolve == null || hostResolve.activityInfo == null) {
                    return false;
                }
                return PKG_SAMSUNG_ACCOUNT.equals(hostResolve.activityInfo.packageName);
            } catch (Throwable ignored) {
            }
            return false;
        }

        private boolean shouldBlockSamsungHealthSamsungAccountWebSdk(Intent intent) {
            try {
                if (intent == null) {
                    return false;
                }
                String callerPackage = BActivityThread.getAppPackageName();
                if (!PKG_SAMSUNG_HEALTH.equals(callerPackage)) {
                    return false;
                }
                String action = intent.getAction();
                if (!ACTION_SAMSUNG_ACCOUNT_REQUEST_SIGN_IN_FROM_WEB_SDK.equals(action)
                        && !ACTION_SAMSUNG_ACCOUNT_REQUEST_CONFIRM_PASSWORD_FROM_WEB_SDK.equals(action)) {
                    return false;
                }

                int userId = BActivityThread.getUserId();
                if (SamsungHealthCompat.isHostSamsungAccountFallbackEnabled(userId, callerPackage)) {
                    return false;
                }

                ResolveInfo hostResolve = BlackBoxCore.getContext().getPackageManager()
                        .resolveActivity(intent, GET_META_DATA);
                if (hostResolve == null || hostResolve.activityInfo == null) {
                    return false;
                }
                return PKG_SAMSUNG_ACCOUNT.equals(hostResolve.activityInfo.packageName);
            } catch (Throwable ignored) {
            }
            return false;
        }

        private Intent maybeRewriteSamsungHealthSigninPopupToAuthenticator(Intent intent) {
            try {
                if (intent == null) {
                    return null;
                }
                String callerPackage = BActivityThread.getAppPackageName();
                if (!PKG_SAMSUNG_HEALTH.equals(callerPackage)) {
                    return null;
                }
                if (!ACTION_SAMSUNG_ACCOUNT_SIGNIN_POPUP.equals(intent.getAction())) {
                    return null;
                }

                int userId = BActivityThread.getUserId();
                if (SamsungHealthCompat.isHostSamsungAccountFallbackEnabled(userId, callerPackage)) {
                    return null;
                }

                // Remember which BlackBox user initiated the SaSDK web sign-in so a host-level
                // sasdk:// redirect can be forwarded back into the correct virtual profile.
                SamsungHealthCompat.setLastSaSdkRedirectUserId(userId);

                // Diagnostics: log extras to keep visibility into request shape.
                logSamsungHealthSigninPopupExtras(intent);

                // Instead of trying to guess an OAuth redirect URL, delegate to Samsung Health's
                // own AuthenticatorActivity which is designed to return auth results back into
                // the app (Activity-for-result contract) without using the host Samsung Account.
                Intent authIntent = new Intent();
                authIntent.setComponent(new ComponentName(PKG_SAMSUNG_HEALTH, ACTIVITY_SAMSUNG_HEALTH_AUTHENTICATOR));
                authIntent.setPackage(PKG_SAMSUNG_HEALTH);
                authIntent.setFlags(intent.getFlags());
                if (intent.getExtras() != null) {
                    authIntent.putExtras(intent.getExtras());
                }

                // Samsung Health sets this extra when it calls AuthenticatorActivity normally.
                // Best-effort: identify the caller as AccountHandlerActivity (the component that
                // initiates SIGNIN_POPUP in current versions).
                try {
                    authIntent.putExtra("calling_activity", Class.forName(ACTIVITY_SAMSUNG_HEALTH_ACCOUNT_HANDLER));
                } catch (Throwable ignored) {
                }

                Slog.d(TAG, "Samsung Health SIGNIN_POPUP rewritten to AuthenticatorActivity: " + authIntent.getComponent());
                return authIntent;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private Intent maybeRewriteSamsungHealthSaSdkToBrowser(Intent intent) {
            try {
                if (intent == null) {
                    return null;
                }
                String callerPackage = BActivityThread.getAppPackageName();
                if (!PKG_SAMSUNG_HEALTH.equals(callerPackage)) {
                    return null;
                }
                String action = intent.getAction();
                if (!ACTION_SAMSUNG_ACCOUNT_REQUEST_SIGN_IN_FROM_WEB_SDK.equals(action)
                        && !ACTION_SAMSUNG_ACCOUNT_REQUEST_CONFIRM_PASSWORD_FROM_WEB_SDK.equals(action)) {
                    return null;
                }

                int userId = BActivityThread.getUserId();
                if (SamsungHealthCompat.isHostSamsungAccountFallbackEnabled(userId, callerPackage)) {
                    return null;
                }

                // Diagnostics: capture request shape (without leaking query params / tokens).
                logSamsungHealthSaSdkExtras(intent);

                // Best-effort: if the SaSDK request carries an embedded https:// URL, open it in a
                // sandbox browser. This avoids the host SamsungAccount app signature check and
                // allows the OAuth redirect back to the sasdk:// receiver inside Samsung Health.
                Uri uri = extractBrowsableUri(intent);
                if (uri == null) {
                    return null;
                }

                Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                viewIntent.addCategory(Intent.CATEGORY_BROWSABLE);
                viewIntent.setData(uri);
                viewIntent.setFlags(intent.getFlags());
                Slog.d(TAG, "Samsung Health SaSDK request rewritten to browser: " + sanitizeUriForLog(uri));
                return viewIntent;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private Uri extractBrowsableUri(Intent intent) {
            try {
                Uri data = intent.getData();
                Uri direct = tryParseBrowsableUri(data);
                if (direct != null) {
                    return direct;
                }

                Bundle extras = intent.getExtras();
                if (extras == null) {
                    return null;
                }

                // Prefer explicit/common key names first.
                Uri byKey = extractBrowsableUriFromBundle(extras, 0, true);
                if (byKey != null) {
                    return byKey;
                }

                // Fallback: scan all extras (including nested bundles) for embedded URLs.
                return extractBrowsableUriFromBundle(extras, 0, false);
            } catch (Throwable ignored) {
            }
            return null;
        }

        private Uri extractBrowsableUriFromBundle(Bundle bundle, int depth, boolean preferKnownKeys) {
            if (bundle == null || depth > 2) {
                return null;
            }
            try {
                java.util.ArrayList<String> keys = new java.util.ArrayList<>(bundle.keySet());
                // Keep the scan bounded; Samsung intents sometimes carry large bundles.
                int maxKeys = Math.min(keys.size(), 80);

                // Known keys that often carry a login URL.
                final String[] knownKeys = new String[]{
                        "url", "URL", "uri", "URI", "link", "deeplink", "deep_link",
                        "loginUrl", "login_url", "webUrl", "web_url", "web",
                        "redirect", "redirectUrl", "redirect_url", "callback", "callbackUrl", "callback_url",
                        "browser_fallback_url", "browserFallbackUrl", "fallbackUrl"
                };

                if (preferKnownKeys) {
                    for (String kk : knownKeys) {
                        if (!bundle.containsKey(kk)) {
                            continue;
                        }
                        Uri found = tryParseBrowsableUri(bundle.get(kk));
                        if (found != null) {
                            return found;
                        }
                    }
                }

                for (int i = 0; i < maxKeys; i++) {
                    String key = keys.get(i);
                    Object value = bundle.get(key);

                    if (value instanceof Bundle) {
                        Uri nested = extractBrowsableUriFromBundle((Bundle) value, depth + 1, preferKnownKeys);
                        if (nested != null) {
                            return nested;
                        }
                        continue;
                    }

                    Uri found = tryParseBrowsableUri(value);
                    if (found != null) {
                        return found;
                    }
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        private Uri tryParseBrowsableUri(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Uri) {
                Uri uri = (Uri) value;
                if (isBrowsableUri(uri)) {
                    return uri;
                }
                return null;
            }
            if (value instanceof String) {
                return tryParseBrowsableUriFromString((String) value);
            }
            return null;
        }

        private Uri tryParseBrowsableUri(Uri uri) {
            if (uri == null) {
                return null;
            }
            if (isBrowsableUri(uri)) {
                return uri;
            }
            return null;
        }

        private Uri tryParseBrowsableUriFromString(String str) {
            if (TextUtils.isEmpty(str)) {
                return null;
            }

            // Direct schemes.
            if (startsWithIgnoreCase(str, "http://")
                    || startsWithIgnoreCase(str, "https://")
                    || startsWithIgnoreCase(str, "intent://")) {
                Uri uri = Uri.parse(str.trim());
                if (isBrowsableUri(uri)) {
                    return uri;
                }
            }

            // Embedded URL inside a larger string.
            Uri embedded = findFirstBrowsableUriInText(str);
            if (embedded != null) {
                return embedded;
            }

            return null;
        }

        private Uri findFirstBrowsableUriInText(String text) {
            if (TextUtils.isEmpty(text)) {
                return null;
            }
            String lower = text.toLowerCase();
            int idx = indexOfAny(lower, "https://", "http://", "intent://");
            if (idx < 0) {
                return null;
            }
            String tail = text.substring(idx);
            // Stop at first whitespace or control char.
            int end = tail.length();
            for (int i = 0; i < tail.length(); i++) {
                char c = tail.charAt(i);
                if (Character.isWhitespace(c) || c == '\u0000') {
                    end = i;
                    break;
                }
            }
            String candidate = tail.substring(0, end).trim();
            if (TextUtils.isEmpty(candidate)) {
                return null;
            }
            try {
                Uri uri = Uri.parse(candidate);
                if (isBrowsableUri(uri)) {
                    return uri;
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        private int indexOfAny(String haystackLower, String... needlesLower) {
            int best = -1;
            for (String needle : needlesLower) {
                if (TextUtils.isEmpty(needle)) {
                    continue;
                }
                int i = haystackLower.indexOf(needle);
                if (i >= 0 && (best < 0 || i < best)) {
                    best = i;
                }
            }
            return best;
        }

        private boolean startsWithIgnoreCase(String value, String prefix) {
            if (value == null || prefix == null) {
                return false;
            }
            if (value.length() < prefix.length()) {
                return false;
            }
            return value.regionMatches(true, 0, prefix, 0, prefix.length());
        }

        private boolean isBrowsableUri(Uri uri) {
            if (uri == null) {
                return false;
            }
            String scheme = uri.getScheme();
            if (TextUtils.isEmpty(scheme)) {
                return false;
            }
            return "http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme)
                    || "intent".equalsIgnoreCase(scheme);
        }

        private void logSamsungHealthSigninPopupExtras(Intent intent) {
            try {
                if (intent == null) {
                    return;
                }
                Bundle extras = intent.getExtras();
                if (extras == null || extras.isEmpty()) {
                    Slog.d(TAG, "Samsung Health SIGNIN_POPUP extras: <empty>");
                    return;
                }
                Slog.d(TAG, "Samsung Health SIGNIN_POPUP extras keys: " + extras.keySet());
                logBundleExtrasSanitized(extras, 0);
            } catch (Throwable ignored) {
            }
        }

        private void logSamsungHealthSaSdkExtras(Intent intent) {
            try {
                if (intent == null) {
                    return;
                }
                Bundle extras = intent.getExtras();
                if (extras == null || extras.isEmpty()) {
                    Slog.d(TAG, "Samsung Health SaSDK extras: <empty>");
                    return;
                }
                Slog.d(TAG, "Samsung Health SaSDK extras keys: " + extras.keySet());
                logBundleExtrasSanitizedWithPrefix(extras, 0, "SaSDK");
            } catch (Throwable ignored) {
            }
        }

        private void logBundleExtrasSanitized(Bundle bundle, int depth) {
            if (bundle == null || depth > 2) {
                return;
            }
            try {
                java.util.ArrayList<String> keys = new java.util.ArrayList<>(bundle.keySet());
                int maxKeys = Math.min(keys.size(), 50);
                for (int i = 0; i < maxKeys; i++) {
                    String key = keys.get(i);
                    Object value = bundle.get(key);
                    if (value instanceof Bundle) {
                        Slog.d(TAG, "Samsung Health SIGNIN_POPUP extra bundle: " + key + " (depth=" + depth + ")");
                        logBundleExtrasSanitized((Bundle) value, depth + 1);
                        continue;
                    }
                    String summary = summarizeExtraValue(value);
                    Slog.d(TAG, "Samsung Health SIGNIN_POPUP extra: " + key + " = " + summary);
                }
            } catch (Throwable ignored) {
            }
        }

        private void logBundleExtrasSanitizedWithPrefix(Bundle bundle, int depth, String prefix) {
            if (bundle == null || depth > 2) {
                return;
            }
            try {
                java.util.ArrayList<String> keys = new java.util.ArrayList<>(bundle.keySet());
                int maxKeys = Math.min(keys.size(), 50);
                for (int i = 0; i < maxKeys; i++) {
                    String key = keys.get(i);
                    Object value = bundle.get(key);
                    if (value instanceof Bundle) {
                        Slog.d(TAG, "Samsung Health " + prefix + " extra bundle: " + key + " (depth=" + depth + ")");
                        logBundleExtrasSanitizedWithPrefix((Bundle) value, depth + 1, prefix);
                        continue;
                    }
                    String summary = summarizeExtraValue(value);
                    Slog.d(TAG, "Samsung Health " + prefix + " extra: " + key + " = " + summary);
                }
            } catch (Throwable ignored) {
            }
        }

        private String summarizeExtraValue(Object value) {
            if (value == null) {
                return "<null>";
            }
            if (value instanceof Uri) {
                return "Uri(" + sanitizeUriForLog((Uri) value) + ")";
            }
            if (value instanceof String) {
                String str = (String) value;
                Uri embedded = findFirstBrowsableUriInText(str);
                if (embedded != null) {
                    return "String(url=" + sanitizeUriForLog(embedded) + ")";
                }
                if (str.length() > 80) {
                    return "String(len=" + str.length() + ")";
                }
                return "String(\"" + str.replace("\n", "\\n") + "\")";
            }
            return value.getClass().getSimpleName();
        }

        private String sanitizeUriForLog(Uri uri) {
            if (uri == null) {
                return "<null>";
            }
            try {
                // Avoid leaking tokens/state in query parameters into logcat artifacts.
                Uri.Builder b = uri.buildUpon();
                b.encodedQuery(null);
                b.fragment(null);
                Uri sanitized = b.build();
                return sanitized.toString();
            } catch (Throwable ignored) {
                return String.valueOf(uri);
            }
        }

        private void replaceIntentArg(Object[] args, Intent oldIntent, Intent newIntent) {
            if (args == null || newIntent == null) {
                return;
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i] == oldIntent) {
                    args[i] = newIntent;
                    return;
                }
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Intent) {
                    args[i] = newIntent;
                    return;
                }
            }
        }

        private void stripActivityResultArgs(Object[] args, Intent intent) {
            if (args == null || intent == null) {
                return;
            }

            int intentIndex = -1;
            for (int i = 0; i < args.length; i++) {
                if (args[i] == intent) {
                    intentIndex = i;
                    break;
                }
            }
            if (intentIndex < 0) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof Intent) {
                        intentIndex = i;
                        break;
                    }
                }
            }
            if (intentIndex < 0) {
                return;
            }

            boolean clearedResultTo = false;
            boolean clearedResultWho = false;
            boolean clearedRequestCode = false;
            for (int i = intentIndex + 1; i < args.length; i++) {
                Object arg = args[i];
                if (!clearedResultTo && arg instanceof IBinder) {
                    args[i] = null;
                    clearedResultTo = true;
                    continue;
                }
                if (clearedResultTo && !clearedResultWho && arg instanceof String) {
                    String resultWho = (String) arg;
                    if (!TextUtils.isEmpty(resultWho)) {
                        args[i] = null;
                        clearedResultWho = true;
                    }
                    continue;
                }
                if (clearedResultTo && !clearedRequestCode && arg instanceof Integer) {
                    args[i] = -1;
                    clearedRequestCode = true;
                    break;
                }
            }
        }
    }

    @ProxyMethod("startActivities")
    public static class StartActivities extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int index = getIntents();
            Intent[] intents = (Intent[]) args[index++];
            String[] resolvedTypes = (String[]) args[index++];
            IBinder resultTo = (IBinder) args[index++];
            Bundle options = (Bundle) args[index];
            
            if (!ComponentUtils.isSelf(intents)) {
                return method.invoke(who, args);
            }

            for (Intent intent : intents) {
                intent.setExtrasClassLoader(who.getClass().getClassLoader());
            }
            return BlackBoxCore.getBActivityManager().startActivities(BActivityThread.getUserId(),
                    intents, resolvedTypes, resultTo, options);
        }

        public int getIntents() {
            if (BuildCompat.isR()) {
                return 3;
            }
            return 2;
        }
    }

    @ProxyMethod("startIntentSenderForResult")
    public static class StartIntentSenderForResult extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("activityResumed")
    public static class ActivityResumed extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            BlackBoxCore.getBActivityManager().onActivityResumed((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("activityDestroyed")
    public static class ActivityDestroyed extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            BlackBoxCore.getBActivityManager().onActivityDestroyed((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("finishActivity")
    public static class FinishActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            BlackBoxCore.getBActivityManager().onFinishActivity((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getAppTasks")
    public static class GetAppTasks extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getCallingPackage")
    public static class getCallingPackage extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return BlackBoxCore.getBActivityManager().getCallingPackage((IBinder) args[0], BActivityThread.getUserId());
        }
    }

    @ProxyMethod("getCallingActivity")
    public static class getCallingActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return BlackBoxCore.getBActivityManager().getCallingActivity((IBinder) args[0], BActivityThread.getUserId());
        }
    }
}
