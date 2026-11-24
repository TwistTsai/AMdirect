package com.example.amdirect;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import java.lang.reflect.Constructor;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {

    private static final String TAG = "AMDirect";
    private static final int FLAG_DIRECT = 0x8;        // AUDIO_OUTPUT_FLAG_DIRECT
    private static final int FLAG_DEEP_BUFFER = 0x2;   // AUDIO_OUTPUT_FLAG_DEEP_BUFFER

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"com.apple.android.music".equals(lp.packageName)) return;

        for (Constructor<?> c : AudioTrack.class.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 6
                    && p[0] == AudioAttributes.class
                    && p[1] == AudioFormat.class
                    && p[2] == int.class && p[3] == int.class
                    && p[4] == int.class && p[5] == int.class) {

                XposedBridge.hookMethod(c, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam mp) {
                        AudioFormat fmt = (AudioFormat) mp.args[1];
                        if (fmt == null) return;

                        int enc = fmt.getEncoding();
                        int rate = fmt.getSampleRate();
                        boolean pcm = enc == AudioFormat.ENCODING_PCM_16BIT
                                || enc == AudioFormat.ENCODING_PCM_24BIT_PACKED
                                || enc == AudioFormat.ENCODING_PCM_32BIT;
                        boolean rateOk = rate == 44100 || rate == 48000
                                || rate == 88200 || rate == 96000
                                || rate == 176400 || rate == 192000;

                        if (!pcm || !rateOk) {
                            Log.i(TAG, "skip enc=" + enc + " rate=" + rate);
                            return;
                        }


                        int flags = (Integer) mp.args[5];
                        int want = (flags & ~FLAG_DEEP_BUFFER) | FLAG_DIRECT;
                        mp.args[5] = want;
                        Log.i(TAG, "direct rate=" + rate + " enc=" + enc
                                + " flags " + flags + " -> " + want);
                    }
                });
            }
        }
        Log.i(TAG, "hook installed");
    }
}
