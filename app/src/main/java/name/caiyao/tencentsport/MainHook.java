package name.caiyao.tencentsport;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.util.SparseArray;

import java.lang.reflect.Field;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by 蔡小木 on 2016/2/16 0016.
 */
public class MainHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static final String WEXIN = "com.tencent.mm";
    private static final String QQ = "com.tencent.mobileqq";
    private static int weixinCount = 0, qqCount = 0;
    private static boolean isWeixin, isQQ, isAuto;
    private XSharedPreferences sharedPreferences;
    private static int mQQ, mWX, max = 100000;
    private static boolean isWeixinDisable;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        final Object activityThread = XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread", null), "currentActivityThread");
        final Context systemContext = (Context) XposedHelpers.callMethod(activityThread, "getSystemContext");
        IntentFilter intentFilter = new IntentFilter();
        String SETTING_CHANGED = "name.caiyao.tencentsport.SETTING_CHANGED";
        intentFilter.addAction(SETTING_CHANGED);
        systemContext.registerReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                isWeixin = intent.getExtras().getBoolean("weixin", false);
                isWeixinDisable = intent.getExtras().getBoolean("weixinDisable", false);
                isQQ = intent.getExtras().getBoolean("qq", false);
                mQQ = Integer.valueOf(intent.getExtras().getString("qq_magnification", "1"));
                mWX = Integer.valueOf(intent.getExtras().getString("weixin_magnification", "1"));
                isAuto = intent.getExtras().getBoolean("autoincrement", false);
                XposedBridge.log("isWeixin= " + isWeixin + "  微信倍率= " + mWX + "  isQQ= " + isQQ + "  QQ倍率= " + mQQ);
            }
        }, intentFilter);


        if (loadPackageParam.packageName.equals(WEXIN) || loadPackageParam.packageName.equals(QQ)) {
            getKey();
            final Class<?> sensorEL = XposedHelpers.findClass("android.hardware.SystemSensorManager$SensorEventQueue", loadPackageParam.classLoader);
            XposedBridge.hookAllMethods(sensorEL, "dispatchSensorEvent", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    int handle = (Integer) param.args[0];
                    Field field = param.thisObject.getClass().getDeclaredField("mSensorsEvents");
                    field.setAccessible(true);
                    Sensor ss = ((SparseArray<SensorEvent>) field.get(param.thisObject)).get(handle).sensor;
                    if (ss == null) {
                        XposedBridge.log("传感器为NULL");
                        return;
                    }
                    if (ss.getType() == Sensor.TYPE_STEP_COUNTER || ss.getType() == Sensor.TYPE_STEP_DETECTOR) {
                        if ((isWeixin && loadPackageParam.packageName.equals(WEXIN))) {
                            XposedBridge.log("微信请求");
                            if (isWeixinDisable) {
                                ((float[]) param.args[1])[0] = ((float[]) param.args[1])[0] * 0;
                                XposedBridge.log("微信不修改");
                            } else {
                                if (isAuto) {
                                    if (mWX * weixinCount <= max) {
                                        ((float[]) param.args[1])[0] = ((float[]) param.args[1])[0] + mWX * weixinCount;
                                        weixinCount += 1;
                                    } else {
                                        weixinCount = 0;
                                    }
                                } else {
                                    ((float[]) param.args[1])[0] = ((float[]) param.args[1])[0] * mWX;
                                    XposedBridge.log("微信修改成功");
                                }
                            }
                        }
                        if ((isQQ && loadPackageParam.packageName.equals(QQ))) {
                            XposedBridge.log("QQ请求");
                            if (isAuto) {
                                if (mQQ * qqCount <= max) {
                                    ((float[]) param.args[1])[0] = ((float[]) param.args[1])[0] + mQQ * qqCount;
                                    qqCount += 1;
                                } else {
                                    qqCount = 0;
                                }
                            } else {
                                ((float[]) param.args[1])[0] = ((float[]) param.args[1])[0] * mQQ;
                                XposedBridge.log("QQ修改成功");
                            }
                        }
                        XposedBridge.log(loadPackageParam.packageName + "传感器类型：" + ss.getType() + ",修改后：" + ((float[]) param.args[1])[0]);
                    }
                }
            });
        }
    }

    private void getKey() {
        sharedPreferences.reload();
        isWeixin = sharedPreferences.getBoolean("weixin", false);
        isWeixinDisable = sharedPreferences.getBoolean("weixinDisable", false);
        isQQ = sharedPreferences.getBoolean("qq", false);
        mQQ = Integer.valueOf(sharedPreferences.getString("qq_magnification", "1"));
        mWX = Integer.valueOf(sharedPreferences.getString("weixin_magnification", "1"));
        isAuto = sharedPreferences.getBoolean("autoincrement", false);
        XposedBridge.log("isWeixin= " + isWeixin + "  微信倍率= " + mWX + "  isQQ= " + isQQ + "  QQ倍率= " + mQQ);
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        sharedPreferences = new XSharedPreferences(BuildConfig.APPLICATION_ID);
    }
}
