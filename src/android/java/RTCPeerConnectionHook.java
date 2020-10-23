package com.agora.cordova.plugin.webrtc;

// The native Toast API

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
// Cordova-required packages
import com.agora.demo.four.R;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class RTCPeerConnectionHook extends CordovaPlugin {
    static final String TAG = RTCPeerConnectionHook.class.getCanonicalName();

    public static final String CAMERA = Manifest.permission.CAMERA;
    public static final String INTERNET = Manifest.permission.INTERNET;
    public static final String NETWORK_STATE = Manifest.permission.ACCESS_NETWORK_STATE;
    public static final String WIFI_STATE = Manifest.permission.ACCESS_WIFI_STATE;
    public static final String RECORD_AUDIO = Manifest.permission.RECORD_AUDIO;
    public static final String MODIFY_AUDIO_SETTINGS = Manifest.permission.MODIFY_AUDIO_SETTINGS;
    public static final String WAKE_LOCK = Manifest.permission.WAKE_LOCK;

    public static final int NECESSARY_PERM_CODE = 900;
    private static final String PERMISSION_DENIED_ERROR = "Permission_Denied";

    //TODO maybe should protected by RWlock
    Map<String, RTCPeerConnection> allConnections;

    MessageBus server;
    MessageBusClient client;
    String hook_id = UUID.randomUUID().toString();
    String webrtclocal_id;

    String action;
    JSONArray args;
    CallbackContext callbackContext;

    String offer = "";

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        Log.e(TAG, "=== Realy in initialize");

        allConnections = new HashMap<>();

        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                server = new MessageBus(new InetSocketAddress("127.0.0.1", 9999));
                server.setReuseAddr(true);
                server.setTcpNoDelay(true);
                server.start();
            }
        });
        try {
            client = new MessageBusClient(new URI(this.cordova.getActivity().getString(R.string.internalws) + hook_id));
            client.setReuseAddr(true);
            client.setTcpNoDelay(true);
            client.connect();
        } catch (Exception e) {
            Log.e(TAG, "Fault cannot connect to server:" + e.toString());
        }

        webrtclocal_id = cordova.getActivity().getString(R.string.webrtclocal_id);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            server.stop(300);
        } catch (Exception e) {
            Log.e(TAG, "MessageBus stop exception:" + e.toString());
        }
    }

    @Override
    public boolean execute(String action, JSONArray args,
                           final CallbackContext callbackContext) {

        Log.v(TAG, "action:" + action + "\t" + "arguments:" + args.toString());
        // Verify that the user sent a 'show' action
        this.action = action;
        this.args = args;
        this.callbackContext = callbackContext;

        if (!(cordova.hasPermission(CAMERA) && cordova.hasPermission(INTERNET)
                && cordova.hasPermission(RECORD_AUDIO) && cordova.hasPermission(WAKE_LOCK))) {
            getNecessaryPermission(NECESSARY_PERM_CODE);
        } else {
            return continueWithPermissions(this.action, this.args, this.callbackContext);
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == 800) {
            webrtclocal_id = intent.getStringExtra(cordova.getActivity().getString(R.string.webrtclocal_id));
            Log.i(TAG, "hook get webrtclocal_id:" + webrtclocal_id);
        }
    }

    private boolean continueWithPermissions(String action, JSONArray args,
                                            final CallbackContext callbackContext) {
        try {
            String id = "";
            switch (action) {
                case "CreateInstance":
                    id = args.getString(0);
                    createInstance(id);
                    break;
                case "createOffer":
                    return impCreateOffer();

                case "setRemoteDescription":

                    return impSetRemoteDescription(args);
                default:
                    callbackContext.error("RTCPeerConnection not implement action:" + action);
                    return false;
            }
            // Send a positive result to the callbackContext
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
            callbackContext.success(id);
            callbackContext.sendPluginResult(pluginResult);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            return false;
        }
        return true;
    }

    boolean impSetRemoteDescription(JSONArray args) {
        Log.v(TAG, "impSetRemoteDescription...");
        try {
            String type = args.getString(0);
            String sdp = args.getString(1);
            Log.v(TAG, "impSetRemoteDescription type:" + type);
            Log.v(TAG, "impSetRemoteDescription sdp:" + sdp);
            MessageBus.Message msg = new MessageBus.Message();
            msg.Target = webrtclocal_id;
            msg.Action = type;
            msg.Payload = sdp;
            client.send(msg.toString());
        } catch (Exception e) {
            Log.e(TAG, "Cannot impSetRemoteDescription:" + e.toString());
        }
        return true;
    }

    protected void getNecessaryPermission(int requestCode) {
        cordova.requestPermissions(this, requestCode,
                new String[]{CAMERA,
                        INTERNET,
                        RECORD_AUDIO,
                        WAKE_LOCK,
                });
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        super.onRequestPermissionResult(requestCode, permissions, grantResults);
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                this.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR));
                return;
            }
        }
        this.continueWithPermissions(this.action, this.args, this.callbackContext);
    }

    public class RTCPeerConnection {
        UUID id;

        public RTCPeerConnection(String id) {

            this.id = UUID.fromString(id);

        }
    }

    String createInstance(String id) {
        RTCPeerConnection pc = new RTCPeerConnection(id);
        allConnections.put(pc.id.toString(), pc);

        return pc.id.toString();
    }

    boolean impCreateOffer() {
        if (!offer.equals("")) {
            Log.e(TAG, "impCreatOffer:" + offer);
            callbackContext.success(offer);
            return true;
        }
        this.cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                Intent intent = new Intent(cordova.getActivity().getApplicationContext(), WebRTCLocalActivity.class);

                intent.putExtra(cordova.getActivity().getString(R.string.hook_id), hook_id.toString());

                cordova.getActivity().startActivity(intent);
            }
        });

        return true;
    }

    public class MessageBusClient extends WebSocketClient {

        public MessageBusClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            Log.v(TAG, "Plugin connected to server:" + handshakedata.getHttpStatusMessage());
        }

        @Override
        public void onMessage(String message) {
            Log.e(TAG, "onMessage:" + message);
            MessageBus.Message msg = MessageBus.Message.formString(message);
            if (!msg.Target.equals(hook_id)) {
                Log.e(TAG, "invalid message has been received");
                return;
            }
            if (msg.Action.equals("offer")) {
                offer = msg.Payload;
                Log.e(TAG, "return offer to js:" + offer);
//                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
//                MessageBus.Message msg = new MessageBus.Message();
//                msg.Action = "offer";
//                msg.Payload = offer;
                callbackContext.success(offer);
//                callbackContext.sendPluginResult(pluginResult);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            Log.e(TAG, "Plugin not implement onClose");
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        client.reconnect();
                    } catch (Exception e) {
                        Log.e(TAG, "cannot create Client" + e.toString());
                    }
                }
            });
        }

        @Override
        public void onError(Exception ex) {
            Log.e(TAG, "Plugin not implement onError" + ex.toString());

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    client.reconnect();
                }
            });
        }

        public void send(String type, String payload) {
            MessageBus.Message msg = new MessageBus.Message();
            msg.Target = webrtclocal_id;
            msg.Action = "neecOffer";
            client.send(msg.toString());
        }
    }
}
