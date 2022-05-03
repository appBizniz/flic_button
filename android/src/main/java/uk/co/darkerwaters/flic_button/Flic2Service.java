
// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package uk.co.darkerwaters.flic_button;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Handler;
import android.util.Log;
import androidx.core.app.JobIntentService;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.PluginRegistry.PluginRegistrantCallback;
import io.flutter.view.FlutterCallbackInformation;
import io.flutter.view.FlutterMain;
import io.flutter.view.FlutterNativeView;
import io.flutter.view.FlutterRunArguments;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.dart.DartExecutor.DartCallback;


class Flic2Service implements MethodCallHandler, JobIntentService {

    private MethodChannel mBackgroundChannel;
    private Context mContext;
    private AtomicBoolean sServiceStarted = AtomicBoolean(false);


    void startService(Context context){
        synchronized(sServiceStarted) {
            mContext = context;
            if (sBackgroundFlutterEngine == null) {
                sBackgroundFlutterEngine = FlutterEngine(context);

                var callbackHandle = context.getSharedPreferences(
                        GeofencingPlugin.SHARED_PREFERENCES_KEY,
                        Context.MODE_PRIVATE)
                        .getLong(GeofencingPlugin.CALLBACK_DISPATCHER_HANDLE_KEY, 0);
                if (callbackHandle == 0L) {
                    Log.e(TAG, "Fatal: no callback registered");
                    return;
                }

                var callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle);
                if (callbackInfo == null) {
                    Log.e(TAG, "Fatal: failed to find callback");
                    return;
                }

                var args = DartCallback(
                    context.getAssets(),
                    FlutterMain.findAppBundlePath(context),
                    callbackInfo
                );
                sBackgroundFlutterEngine.getDartExecutor().executeDartCallback(args);
                IsolateHolderService.setBackgroundFlutterEngine(sBackgroundFlutterEngine);
            }
        }
        mBackgroundChannel = MethodChannel(sBackgroundFlutterEngine.getDartExecutor().getBinaryMessenger(),
                "flic2_background_channel");
        mBackgroundChannel.setMethodCallHandler(this);
    }

    @Override 
    public void onCreate() {
        super.onCreate();
        startService(this);
    }

    @Override 
    void onMethodCall(MethodCall call ,Result result ) {
        switch(call.method) {
             case "initialized":  
                 synchronized(sServiceStarted) {
                     while (!queue.isEmpty()) {
                         mBackgroundChannel.invokeMethod("", queue.remove());
                     }
                     sServiceStarted.set(true);
                }
             case "promoteToForeground":  {
                 mContext.startForegroundService(Intent(mContext, IsolateHolderService.class.java));
             }
             case "demoteToBackground":  {
                 var intent = Intent(mContext, IsolateHolderService.class.java);
                 intent.setAction(IsolateHolderService.ACTION_SHUTDOWN);
                 mContext.startForegroundService(intent);
             }
             default: result.notImplemented();
         }
         result.success(null);
     }
  
    @Override
    void onHandleWork(Intent intent) {
        var callbackHandle = intent.getLongExtra(GeofencingPlugin.CALLBACK_HANDLE_KEY, 0);



        synchronized(sServiceStarted) {
            if (!sServiceStarted.get()) {
                // Queue up geofencing events while background isolate is starting
                //queue.add(geofenceUpdateList);
            } else {
                // Callback method name is intentionally left blank.
               // Handler(mContext.mainLooper).post { mBackgroundChannel.invokeMethod("", geofenceUpdateList) }
            }
        }
    }

}