package io.agora.rtc.media.services;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.hardware.camera2.CameraManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.MediaConstraints;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import io.agora.rtc.media.enums.EMessage;
import io.agora.rtc.media.models.MediaDeviceInfo;
import io.agora.rtc.webrtc.models.MediaStreamConstraints;
import io.agora.rtc.webrtc.models.MediaTrackConstraintSet;
import io.agora.rtc.webrtc.models.MediaTrackConstraints;
import io.agora.rtc.webrtc.services.PCFactory;

public class MediaDevice {
    private final static String TAG = MediaDevice.class.getCanonicalName();

    static Activity activity;
    static Context context;
    static Intent screenCaptureIntent = null;

    public static void initialize(Activity activity, Context context) {
        MediaDevice.activity = activity;
        MediaDevice.context = context;
        SettingsContentObserver.initialize(activity, new Handler());
    }

    public static void setScreenCaptureIntent(Intent intent) {
        MediaDevice.screenCaptureIntent = intent;
    }

    public static void unInitialize() {
        SettingsContentObserver.unInitialize();
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static AudioDeviceInfo getAudioDeviceByID(String id) {
        AudioManager audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] audioInputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
        AudioDeviceInfo target = null;
        for (AudioDeviceInfo info :
                audioInputDevices) {
            if (info.getId() == Integer.parseInt(id)) {
                target = info;
            }
        }
        return target;
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static String enumerateDevices() {
        AudioManager audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        List<MediaDeviceInfo> infos = new ArrayList<>();

        AudioDeviceInfo[] audioInputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);

        int builtinMicCnt = 1;
        for (AudioDeviceInfo device :
                audioInputDevices) {

            String label = "";
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                    label = device.getProductName().toString() + " Built-in Microphone " + builtinMicCnt++;
                    break;
//                case AudioDeviceInfo.TYPE_TELEPHONY:
//                    label = device.getProductName().toString() + " Telephony Microphone";
//                    break;
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                    label = device.getProductName().toString() + " Wired Microphone";
                    break;
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                    label = device.getProductName().toString() + " Bluetooth Microphone";
                    break;
//                case AudioDeviceInfo.TYPE_USB_DEVICE:
//                    label = device.getProductName().toString() + " USB Microphone";
//                    break;
//                case AudioDeviceInfo.TYPE_USB_HEADSET:
//                    label = device.getProductName().toString() + " USB Headphones Microphone";
//                    break;
                default:
//                    label = Integer.toString(device.getType());
                    break;
            }
            if (label.length() > 0) {
                MediaDeviceInfo info = new MediaDeviceInfo(Integer.toString(device.getId()), "", "audioinput", label);
                infos.add(info);
            }
        }

        AudioDeviceInfo[] audioOutputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);

        for (AudioDeviceInfo device :
                audioOutputDevices) {

            String label = "";
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                    label = device.getProductName().toString() + " Built-in Speaker";
                    break;
                case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                    label = device.getProductName().toString() + " Earphone Speaker";
                    break;
//                case AudioDeviceInfo.TYPE_TELEPHONY:
//                    label = device.getProductName().toString() + " Telephony Speaker";
//                    break;
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                    label = device.getProductName().toString() + " Bluetooth Speaker";
                    break;
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                    label = device.getProductName().toString() + " Bluetooth A2DP Speaker";
                    break;
//                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
//                    label = device.getProductName().toString() + " Wired Headphones Speaker";
//                    break;
                default:
//                    label = Integer.toString(device.getType());
                    break;
            }
            if (label.length() > 0) {
                MediaDeviceInfo info = new MediaDeviceInfo(Integer.toString(device.getId()), "", "audiooutput", label);
                infos.add(info);
            }
        }

        try {
            Camera1Enumerator enumerator = new Camera1Enumerator(false);
            for (String name :
                    enumerator.getDeviceNames()) {

                String label = "Front Camera";
                if (!enumerator.isFrontFacing(name)) {
                    label = "Back Camera";
                }

                MediaDeviceInfo info = new MediaDeviceInfo(name, "", "videoinput", label);

                infos.add(info);
            }
        } catch (Exception e) {
            Log.e(TAG, "enumerate CameraList exception: " + e.toString());
        }

        boolean firstComma = false;

        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (MediaDeviceInfo info :
                infos) {
            if (firstComma) {
                builder.append(",");
            } else {
                firstComma = true;
            }
            builder.append(info.toString());
        }
        builder.append("]");

        return builder.toString();
    }

    public static boolean cameraIsFront(String deviceId) {
        if (deviceId.length() == 0) {
            return true;
        }
        return deviceId.contains("front");
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static void setPlaybackDevice(int deviceId) {
        AudioManager audioManager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] audioOutputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);

        //if(shouldEnableExternalSpeaker) {
        //    if(isBlueToothConnected) {
        //        // 1. case - bluetooth device
        //        mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        //        mAudioManager.startBluetoothSco();
        //        mAudioManager.setBluetoothScoOn(true);
        //    } else {
        //        // 2. case - wired device
        //        mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        //        mAudioManager.stopBluetoothSco();
        //        mAudioManager.setBluetoothScoOn(false);
        //        mAudioManager.setSpeakerphoneOn(false);
        //    }
        //} else {
        //   // 3. case - phone speaker
        //   mAudioManager.setMode(AudioManager.MODE_NORMAL);
        //   mAudioManager.stopBluetoothSco();
        //   mAudioManager.setBluetoothScoOn(false);
        //   mAudioManager.setSpeakerphoneOn(true);
        //}
        for (AudioDeviceInfo device :
                audioOutputDevices) {
            if (device.getId() == deviceId) {
                Log.v(TAG, "found targetDevice: " + deviceId + " " + device.getType());
                switch (device.getType()) {
                    case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                        audioManager.setMode(AudioManager.MODE_NORMAL);
                        audioManager.stopBluetoothSco();
                        audioManager.setBluetoothScoOn(false);
                        audioManager.setSpeakerphoneOn(true);
                        break;
                    case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                        audioManager.stopBluetoothSco();
                        audioManager.setBluetoothScoOn(false);
                        audioManager.setSpeakerphoneOn(false);
                        break;
                    case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                        audioManager.startBluetoothSco();
                        audioManager.setBluetoothScoOn(true);
                        audioManager.setSpeakerphoneOn(false);
                        break;
                    case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                        audioManager.startBluetoothSco();
                        audioManager.setBluetoothScoOn(false);
                        audioManager.setSpeakerphoneOn(false);
                        break;
//                    case AudioDeviceInfo.TYPE_WIRED_HEADSET:
//                        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
//                        audioManager.stopBluetoothSco();
//                        audioManager.setBluetoothScoOn(false);
//                        audioManager.setSpeakerphoneOn(false);
//                        break;
                    default:
                        break;
                }
            }
        }
    }

    public static String getUserMedia(MediaStreamConstraints constraints) throws Exception {
        if (constraints == null) {
            Log.e(TAG, "fault, getUserMedia no sdp data");
            return "";
        }

        List<MediaStreamTrackWrapper> tracks = new LinkedList<>();

        if (constraints.audio != null) {
            int sampleRate = 48000;
            int channelCount = 1;
            boolean aec = false;
            boolean echoCancellation = false;
            boolean noiseSuppression = false;
            String deviceID = null;
            if (constraints.audio.deviceId != null) {
                if (constraints.audio.deviceId.exact != null) {
                    deviceID = constraints.audio.deviceId.exact;
                }
            }
            MediaStreamTrackWrapper wrapper = createLocalAudioTrack(deviceID, sampleRate,
                    channelCount, aec, echoCancellation, noiseSuppression);
            if (wrapper != null) {
                tracks.add(wrapper);
            }
        }

        if (constraints.video != null) {
            getVideoParameter(constraints.video);

            MediaStreamTrackWrapper wrapper = createLocalVideoTrack(constraints.video.deviceId.mean,
                    constraints.video.facingMode.mean.equals("user"), constraints.video.width.mean.intValue(),
                    constraints.video.height.mean.intValue(), constraints.video.frameRate.mean.intValue());

            if (wrapper != null) {
                tracks.add(wrapper);
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append("[");

        boolean first = true;
        for (MediaStreamTrackWrapper wrapper :
                tracks) {
            if (first) {
                first = false;
            } else {
                builder.append(",");
            }
            builder.append(wrapper.toString());
        }

        builder.append("]");


        return builder.toString();
    }

    static void getVideoParameter(MediaTrackConstraints video) {
        if (video.width == null) {
            video.width = new MediaTrackConstraintSet.ParamULongRange();
        }
        if (video.width.mean == null) {
            video.width.mean = 640L;
            if (video.width.max != null) {
                video.width.mean = video.width.max;
            }
        }
        if (video.height == null) {
            video.height = new MediaTrackConstraintSet.ParamULongRange();

        }
        if (video.height.mean == null) {
            video.height.mean = 480L;
            if (video.height.max != null) {
                video.height.mean = video.height.max;
            }
        }
        if (video.frameRate == null) {
            video.frameRate = new MediaTrackConstraintSet.ParamDoubleRange();

        }
        if (video.frameRate.mean == null) {
            video.frameRate.mean = (double) 15;
        }
        if (video.facingMode == null) {
            video.facingMode = new MediaTrackConstraintSet.ParamStringSet();

        }
        if (video.facingMode.mean == null) {
            video.facingMode.mean = "user";
        }
        if (video.deviceId == null) {
            video.deviceId = new MediaTrackConstraintSet.ParamStringSet();
        }
        if (video.deviceId.mean == null) {
            video.deviceId.mean = "";
            if (video.deviceId.exact != null) {
                video.deviceId.mean = video.deviceId.exact;
            }
        }
        if (video.optional != null) {
            for (MediaTrackConstraints.Optional opt :
                    video.optional) {
                if (opt.minWidth > 0) {
                    video.width.mean = (long) opt.minWidth;
                } else if (opt.minHeight > 0) {
                    video.height.mean = (long) opt.minHeight;
                } else if (opt.minFrameRate > 0) {
                    video.frameRate.mean = (double) opt.minFrameRate;
                } else if (opt.sourceId != null && opt.sourceId.length() > 0) {
                    video.facingMode.mean = opt.sourceId;
                }
            }
        }
        if (video.mandatory != null) {
            if (video.mandatory.sourceId != null) {
                video.deviceId.mean = video.mandatory.sourceId;
            }
        }
    }

    public static int getMaxVolume() {
        return SettingsContentObserver.getSettingsContentObserver().getStreamMaxVolume();
    }

    @TargetApi(Build.VERSION_CODES.P)
    public static int getMinVolume() {
        return SettingsContentObserver.getSettingsContentObserver().getStreamMinVolume();
    }

    public static int getVolume() {
        return SettingsContentObserver.getSettingsContentObserver().getVolume();
    }

    public static void setVolume(int volume) {
        SettingsContentObserver.getSettingsContentObserver().setVolume(volume);
    }

    public static class LocalAudioSampleSupervisor implements JavaAudioDeviceModule.SamplesReadyCallback {
        public static final LocalAudioSampleSupervisor supervisor = new LocalAudioSampleSupervisor();
        static List<LocalAudioSampleListener> listeners = new LinkedList<>();

        public interface LocalAudioSampleListener {
            public void onAudioLevel(double level);
        }

        public void addListener(LocalAudioSampleListener listener) {
            listeners.add(listener);
        }

        public void removeListener(LocalAudioSampleListener listener) {
            listeners.remove(listener);
        }

        @Override
        public void onWebRtcAudioRecordSamplesReady(JavaAudioDeviceModule.AudioSamples audioSamples) {
            short max = (short) ~(1 << 15);
            final ByteBuffer buf = ByteBuffer.wrap(audioSamples.getData())
                    .order(ByteOrder.LITTLE_ENDIAN);
//            StringBuilder builder = new StringBuilder();

            int sample = 0;
            while (buf.hasRemaining()) {
                sample += (int) buf.getShort();
            }
            sample = (int) (sample * 2.0 / audioSamples.getData().length);
            double level = (double) sample / max;
//            builder.append(sample).append(" ").append(max).append(" ").append(level);
//            Log.v("ADD", builder.toString());

            for (LocalAudioSampleListener listener :
                    listeners) {
                if (listener != null) {
                    listener.onAudioLevel(level);
                }
            }
        }

        private LocalAudioSampleSupervisor() {
        }
    }

    public static MediaStreamTrackWrapper createLocalAudioTrack(
            String deviceID, int sampleRate, int channelCount, boolean aec, boolean echo, boolean noise) {
        MediaConstraints mediaConstraints = new MediaConstraints();
        mediaConstraints.mandatory.add(new
                MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation2", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googDAEchoCancellation", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl2", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression2", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAudioMirroring", "false"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googHighpassFilter", "true"));

//        PCFactory factory = PCFactory.Builder.createPCFactory(context, deviceID, sampleRate, false, null);

        AudioSource audioSource = PCFactory.factory().createAudioSource(new MediaConstraints());
        AudioTrack audioTrack = PCFactory.factory().createAudioTrack(UUID.randomUUID().toString(), audioSource);

//        createMusicFileAudioTrack();

        return MediaStreamTrackWrapper.cacheMediaStreamTrackWrapper("", audioTrack, audioSource);
    }

    public static MediaStreamTrackWrapper createLocalScreenTrack(
            String deviceId, boolean isFront, int w, int h, int fps) throws Exception {
        if (screenCaptureIntent == null) {
            Log.e(TAG, EMessage.ENOSCREENPERMISSION.toString());
            throw new Exception(EMessage.ENOSCREENPERMISSION.toString());
        }
        ScreenCapturerAndroid videoCapturer = new ScreenCapturerAndroid(screenCaptureIntent, new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
                Log.v(TAG, "stop screen capture");
            }
        });
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(
                "ScreenCaptureThread", PCFactory.eglBase());
        VideoSource videoSource = PCFactory.factory().createVideoSource(videoCapturer.isScreencast());

        videoSource.adaptOutputFormat(w, h, fps);

        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        videoCapturer.startCapture(w, h, fps);

        VideoTrack videoTrack = PCFactory.factory().createVideoTrack(UUID.randomUUID().toString(), videoSource);

        return MediaStreamTrackWrapper.cacheMediaStreamTrackWrapper(
                "", videoTrack, videoCapturer, surfaceTextureHelper, videoSource, "back");
    }


    public static MediaStreamTrackWrapper createLocalVideoTrack(
            String deviceId, boolean isFront, int w, int h, int fps) throws Exception {
        if (deviceId.equals("screen")) {
            return createLocalScreenTrack(deviceId, isFront, w, h, fps);
        }

        VideoCapturer videoCapturer = createCameraCapturer(deviceId, isFront);
        if (videoCapturer == null) {
            return null;
        }
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(
                "CaptureThread", PCFactory.eglBase());
        MediaConstraints mediaConstraints = new MediaConstraints();
        VideoSource videoSource = PCFactory.factory().createVideoSource(videoCapturer.isScreencast());

        videoSource.adaptOutputFormat(w, h, fps);

        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());
        videoCapturer.startCapture(w, h, fps);

        VideoTrack videoTrack = PCFactory.factory().createVideoTrack(UUID.randomUUID().toString(), videoSource);

        return MediaStreamTrackWrapper.cacheMediaStreamTrackWrapper(
                "", videoTrack, videoCapturer, surfaceTextureHelper, videoSource, deviceId);
    }

    private static VideoCapturer createCameraCapturer(String deviceId, boolean isFront) {
        Log.w(TAG, "camera2 is support: " + Camera2Enumerator.isSupported(context));

        Camera1Enumerator enumerator = new Camera1Enumerator(false);
        if (deviceId.length() > 0) {
            return enumerator.createCapturer(deviceId, null);
        }

        final String[] deviceNames = enumerator.getDeviceNames();
        // First, try to find front facing camera
        for (String deviceName : deviceNames) {
            Log.v(TAG, "camera names" + deviceName);
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        // First, try to find front facing camera
        for (String deviceName : deviceNames) {
            Log.v(TAG, "camera names" + deviceName);
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    public static void reset() {
        MediaStreamTrackWrapper.reset();
        SettingsContentObserver.getSettingsContentObserver().clear();
    }

    public static class Media {
    }

    public static class SettingsContentObserver extends ContentObserver {
        int volume;
        int preVolume;
        Context context;
        AudioManager audio;

        List<VolumeChangeListener> listeners;

        static Activity activity;
        static SettingsContentObserver _this = null;
        static final int STREAM_TYPE = AudioManager.STREAM_VOICE_CALL;

        SettingsContentObserver(Context c, Handler handler) {
            super(handler);
            context = c;

            audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

            volume = audio.getStreamVolume(STREAM_TYPE);
            preVolume = volume;

            listeners = new LinkedList<>();
        }

        public static void initialize(Activity activity, Handler handler) {
            if (_this == null) {
                SettingsContentObserver.activity = activity;
                activity.setVolumeControlStream(STREAM_TYPE);
                _this = new SettingsContentObserver(activity.getApplication(), handler);
                activity.getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI,
                        true, _this);
            }
        }

        public static void unInitialize() {
            activity.getContentResolver().unregisterContentObserver(_this);
            _this = null;
        }

        public static SettingsContentObserver getSettingsContentObserver() {
            return _this;
        }

        public int getVolume() {
            return volume;
        }

        public int getStreamMaxVolume() {
            return audio.getStreamMaxVolume(STREAM_TYPE);
        }

        @TargetApi(Build.VERSION_CODES.P)
        public int getStreamMinVolume() {
            return audio.getStreamMinVolume(STREAM_TYPE);
        }

        public void setVolume(int volume) {
            audio.setStreamVolume(STREAM_TYPE, volume, AudioManager.ADJUST_SAME);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            volume = audio.getStreamVolume(STREAM_TYPE);
            if (volume != preVolume) {
                preVolume = volume;
                for (VolumeChangeListener listener :
                        listeners) {
                    listener.onChange(volume);
                }
            }
        }

        public void registerVolumeChangeListener(VolumeChangeListener listener) {
            listeners.add(listener);
            listener.onChange(volume);
        }

        public void unregisterVolumeChangeListener(VolumeChangeListener listener) {
            listeners.remove(listener);
        }

        public void clear() {
            listeners.clear();
        }

        public interface VolumeChangeListener {
            void onChange(int volume);
        }
    }
}
