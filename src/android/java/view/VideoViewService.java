package com.agora.cordova.plugin.view;

import android.app.Activity;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.agora.cordova.plugin.view.model.PlayConfig;
import com.agora.cordova.plugin.webrtc.services.PCFactory;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.RendererCommon;

import java.util.HashMap;
import java.util.Map;

import static android.content.Context.WINDOW_SERVICE;
import static org.apache.cordova.PluginResult.Status.OK;

public class VideoViewService implements VideoView.Supervisor {
    private final static String TAG = VideoViewService.class.getCanonicalName();

    WindowManager windowManager;

    Activity mainActivity;

    int LAYOUT_FLAG;
    int windowWidth;
    int windowHeight;

    Map<String, CallbackVVPeer> instances;

    public VideoViewService(Activity mainActivity) {
        this.instances = new HashMap<>();

        this.mainActivity = mainActivity;
        this.windowManager = (WindowManager) this.mainActivity.getSystemService(WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }

        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        windowHeight = displayMetrics.heightPixels;
        windowWidth = displayMetrics.widthPixels;
    }

    @Override
    public void show(VideoView view, WindowManager.LayoutParams params) {
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CallbackVVPeer peer = instances.get(view.id);
                assert peer != null;
                try {
                    view.init(PCFactory.eglBase(), new RendererCommon.RendererEvents() {
                        @Override
                        public void onFirstFrameRendered() {
                            Log.v(TAG, "onFirstFrameRendered");
                        }

                        @Override
                        public void onFrameResolutionChanged(int i, int i1, int i2) {
                            Log.v(TAG, "onFrameResolutionChanged");

                        }
                    });

                    windowManager.addView(view, params);

                    peer.context.success();
                } catch (Exception e) {
                    Log.e(TAG, "show video view failed: " + e.toString());
                    peer.context.error(e.toString());
                }
            }
        });
    }

    @Override
    public void updateLayout(VideoView view, WindowManager.LayoutParams params) {
        windowManager.updateViewLayout(view, params);
    }

    static class CallbackVVPeer {
        CallbackContext context;
        VideoView vv;

        CallbackVVPeer setCallbackContext(CallbackContext context) {
            this.context = context;
            return this;
        }

        CallbackVVPeer setVideoView(VideoView vv) {
            this.vv = vv;
            return this;
        }
    }

    public boolean createInstance(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        String id = args.getString(0);

        PlayConfig cfg = null;
        if (args.length() > 1) {
            String json = args.get(1).toString();
            if (json.length() != 0) {
                cfg = PlayConfig.fromJson(json);
            }
        }
        if (cfg == null) {
            Log.e(TAG, "Invalid RTCConfiguration config, using default");
            cfg = new PlayConfig();
        }

        VideoView vp = new VideoView(this, id, mainActivity.getApplicationContext(), cfg);

        //for eventCallback
        this.instances.put(id, new CallbackVVPeer().setCallbackContext(callbackContext).setVideoView(vp));

        PluginResult result = new PluginResult(OK);
        result.setKeepCallback(true);

        callbackContext.sendPluginResult(result);
        return true;
    }

    public boolean updateConfig(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        String id = args.getString(0);

        PlayConfig cfg = null;
        String json = args.get(1).toString();
        if (json.length() != 0) {
            cfg = PlayConfig.fromJson(json);
        }
        if (cfg == null) {
            Log.e(TAG, "update Config: invalid arguments");
            callbackContext.error("invalid arguments");
        }

        CallbackVVPeer peer = this.instances.get(id);
        assert peer != null;
        peer.vv.updateConfig(cfg);

        callbackContext.success();
        return true;
    }

    public boolean updateVideoTrack(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        String id = args.getString(0);

        CallbackVVPeer peer = this.instances.get(id);
        assert peer != null;

        Log.v(TAG, "updateVideoTrack " + args.getString(1) + " " + args.getString(2));

        peer.vv.updateVideoTrack(args.getString(1));

//        peer.vv.show();

        callbackContext.success();
        return true;
    }

    public boolean play(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        String id = args.getString(0);

        CallbackVVPeer peer = this.instances.get(id);
        assert peer != null;

        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                peer.vv.show();
            }
        });


        PluginResult result = new PluginResult(OK);
        result.setKeepCallback(true);

        callbackContext.sendPluginResult(result);
        return true;
    }

    public boolean pause(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        callbackContext.success();
        return true;
    }

    public boolean destroy(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        callbackContext.success();
        return true;
    }

    public boolean getCurrentFrame(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        callbackContext.success();
        return true;
    }

    public boolean getWindowAttribute(JSONArray args, final CallbackContext callbackContext) throws JSONException {

        JSONObject obj = new JSONObject();
        obj.put("width", windowWidth);
        obj.put("height", windowHeight);

        callbackContext.success(obj.toString());
        return true;
    }

    public boolean setViewAttribute(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        String id = args.getString(0);
        int w = args.getInt(1);
        int h = args.getInt(2);
        int x = args.getInt(3);
        int y = args.getInt(4);

        CallbackVVPeer peer = this.instances.get(id);
        assert peer != null;
        peer.vv.setViewAttribute(w, h, LAYOUT_FLAG, x, y);
        callbackContext.success();
        return true;
    }

}