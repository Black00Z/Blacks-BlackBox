package top.niunaijun.blackboxa.automation;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AutomationActivityTest {

    @Test
    public void missingApkPath_finishesQuickly() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, AutomationActivity.class);

        try (ActivityScenario<AutomationActivity> scenario = ActivityScenario.launch(intent)) {
            boolean destroyed = waitUntilDestroyed(scenario, 2000);
            assertTrue("AutomationActivity should finish when apk_path is missing", destroyed);
        }
    }

    @Test
    public void nonExistentApk_finishesQuickly() {
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(context, AutomationActivity.class);
        intent.putExtra(AutomationActivity.EXTRA_APK_PATH,
                "/sdcard/Android/data/top.niunaijun.blackbox/files/automation/does_not_exist.apk");
        intent.putExtra(AutomationActivity.EXTRA_USER_ID, 0);
        intent.putExtra(AutomationActivity.EXTRA_LAUNCH, false);

        try (ActivityScenario<AutomationActivity> scenario = ActivityScenario.launch(intent)) {
            boolean destroyed = waitUntilDestroyed(scenario, 2000);
            assertTrue("AutomationActivity should finish when apk_path doesn't exist", destroyed);
        }
    }

    private static boolean waitUntilDestroyed(ActivityScenario<?> scenario, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (scenario.getState() == Lifecycle.State.DESTROYED) return true;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return scenario.getState() == Lifecycle.State.DESTROYED;
    }
}
