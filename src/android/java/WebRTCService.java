package com.agora.cordova.plugin.webrtc;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.agora.cordova.plugin.webrtc.models.MediaStreamConstraints;
import com.agora.cordova.plugin.webrtc.models.RTCConfiguration;
import com.agora.cordova.plugin.webrtc.services.MediaDevice;
import com.agora.cordova.plugin.webrtc.services.PCFactory;
import com.agora.cordova.plugin.webrtc.services.RTCPeerConnection;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.DataChannel;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.RtpReceiver;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.apache.cordova.PluginResult.Status.OK;

public class WebRTCService {
    private final static String TAG = WebRTCService.class.getCanonicalName();

    Activity _mainActivity;

    List<RTCPeerConnection> allPC = new LinkedList<>();

    static class CallbackPCPeer {
        CallbackContext _context;
        RTCPeerConnection _pc;

        CallbackPCPeer setCallbackContext(CallbackContext context) {
            _context = context;
            return this;
        }

        CallbackPCPeer setPeerConnection(RTCPeerConnection pc) {
            _pc = pc;
            return this;
        }
    }

    Map<String, CallbackPCPeer> instances;

    public WebRTCService(Activity mainActivity) {
        _mainActivity = mainActivity;

        instances = new HashMap<>();

        MediaDevice.Initialize(_mainActivity, _mainActivity.getApplicationContext());
        PCFactory.initializationOnce(_mainActivity.getApplicationContext());
    }

    public boolean enumerateDevices(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        callbackContext.success(MediaDevice.enumerateDevices());
        return true;
    }

    public boolean getUserMedia(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        MediaStreamConstraints constraints = null;

        try {
            ObjectMapper mapper = new ObjectMapper();
            HashMap result = mapper.readValue(args.getJSONObject(1).toString(), HashMap.class);
            constraints = new MediaStreamConstraints(result);
        } catch (Exception e) {
            Log.e(TAG, "fault, cannot unmarshal MediaStreamConstraints:" + e.toString() + args.toString());
        }

        String summary = MediaDevice.getUserMedia(constraints);

        callbackContext.success(summary);
        return true;
    }

    public boolean createInstance(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        String id = args.getString(0);

        RTCConfiguration cfg = null;
        if (args.length() > 1) {
            String json = args.get(1).toString();
            if (json.length() != 0) {
                cfg = RTCConfiguration.fromJson(json);
            }
        }
        if (cfg == null) {
            Log.e(TAG, "Invalid RTCConfiguration config, using default");
            cfg = new RTCConfiguration();
        }

        RTCPeerConnection pc = new RTCPeerConnection(new SupervisorImp(), id, cfg);
        allPC.add(pc);

        pc.createInstance(new MessageHandler());

        //for eventCallback
        this.instances.put(id, new CallbackPCPeer().setCallbackContext(callbackContext).setPeerConnection(pc));

        PluginResult result = new PluginResult(OK);
        result.setKeepCallback(true);

        callbackContext.sendPluginResult(result);
        return true;
    }

    public boolean addTrack(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        String id = args.getString(0);

        VideoTrack videoTrack = MediaDevice.createLocalVideoTrack(true, 300, 400, 20);
        if (videoTrack == null) {
            callbackContext.error("no target video track");
            return true;
        }

        CallbackPCPeer peer = instances.get(id);
        assert peer != null;
        String summary = peer._pc.addTrack(videoTrack);

        callbackContext.success(summary);
        return true;
    }

    public boolean addTransceiver(JSONArray args) {
        return false;
    }

    public boolean getTransceivers(JSONArray args) {
        return false;
    }

    boolean createOffer(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        String id = args.getString(0);

        CallbackPCPeer peer = instances.get(id);
        assert peer != null;
        peer._pc.createOffer(new MessageHandler() {
            @Override
            public void success(String msg) {
                callbackContext.success(msg);
            }

            @Override
            public void error(String msg) {
                callbackContext.error(msg);
            }
        });

        return true;
    }

    public boolean setLocalDescription(JSONArray args, CallbackContext callbackContext) throws JSONException {
        String id = args.getString(0);
        CallbackPCPeer peer = instances.get(id);
        assert peer != null;
        peer._pc.setLocalDescription(new MessageHandler() {
            @Override
            public void success() {
                callbackContext.success();
            }

            @Override
            public void error(String msg) {
                callbackContext.error(msg);
            }
        }, args.getString(1), args.getString(2));

        return true;
    }

    public boolean setRemoteDescription(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        String id = args.getString(0);
        CallbackPCPeer peer = instances.get(id);
        assert peer != null;
        peer._pc.setRemoteDescription(new MessageHandler() {
            @Override
            public void success() {
                callbackContext.success();
            }

            @Override
            public void error(String msg) {
                callbackContext.error(msg);
            }
        }, args.getString(1), args.getString(2));
        return true;
    }

    public boolean addIceCandidate(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        String id = args.getString(0);

        CallbackPCPeer peer = instances.get(id);
        assert peer != null;
        peer._pc.addIceCandidate(new MessageHandler() {
            @Override
            public void success() {
                callbackContext.success();
            }

            @Override
            public void error(String msg) {
                callbackContext.error(msg);
            }
        }, args.getString(1));
        return true;
    }

    public boolean getStats(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        String id = args.getString(0);

        CallbackPCPeer peer = instances.get(id);
        assert peer != null;
        peer._pc.getStats(new MessageHandler() {
            @Override
            public void success(String msg) {
                callbackContext.success(msg);
            }
        });

        return true;
    }

    public boolean removeTrack(JSONArray args) {
        return false;
    }

    public boolean close(JSONArray args) {
        return false;
    }

    private Context getApplicationContext() {
        return _mainActivity.getApplicationContext();
    }

    private Object getSystemService(String name) {
        return _mainActivity.getSystemService(name);
    }


    class SupervisorImp implements RTCPeerConnection.Supervisor {

        @Override
        public void onAddTrack(String id, RtpReceiver rtpReceiver, MediaStream[] mediaStreams, String usage) {
            CallbackPCPeer peer = instances.get(id);
            assert peer != null;
//            peer._context.su

        }

        @Override
        public void onAddStream(String id, MediaStream stream, String usage) {
//            if
            Log.e(TAG, "onAddStream ");
        }

        @Override
        public void onRemoveStream(String id, MediaStream mediaStream, String usage) {

        }

        @Override
        public void onDataChannel(String id, DataChannel dataChannel, String usage) {

        }

        @Override
        public void onObserveEvent(String id, Action action, String message, String usage) {
            CallbackPCPeer peer = instances.get(id);
            assert (peer != null);

            Log.v(TAG, usage + " onObserveEvent " + action + " " + message);

            JSONObject obj = new JSONObject();
            try {
                obj.put("event", action.toString());
                obj.put("id", id);
                obj.put("payload", message);
            } catch (Exception e) {
                Log.e(TAG, "event exception:" + action);
            }
            PluginResult result = new PluginResult(OK, obj);
            result.setKeepCallback(true);
            peer._context.sendPluginResult(result);
        }
    }

    class MessageHandler implements RTCPeerConnection.MessageHandler {

        @Override
        public void success() {

        }

        @Override
        public void success(String msg) {

        }

        @Override
        public void error(String msg) {

        }
    }
}