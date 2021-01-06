package io.agora.rtcn.webrtc.services;

import android.util.Base64;
import android.util.Log;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.RTCStats;
import org.webrtc.RTCStatsCollectorCallback;
import org.webrtc.RTCStatsReport;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.agora.rtcn.media.services.MediaStreamTrackWrapper;
import io.agora.rtcn.webrtc.enums.Action;
import io.agora.rtcn.webrtc.enums.RTCIceCredentialType;
import io.agora.rtcn.webrtc.models.RTCConfiguration;
import io.agora.rtcn.webrtc.models.RTCDataChannelInit;
import io.agora.rtcn.webrtc.models.RTCIceServer;
import io.agora.rtcn.webrtc.models.RTCOfferOptions;

import static org.webrtc.RtpParameters.DegradationPreference.BALANCED;
import static org.webrtc.RtpParameters.DegradationPreference.MAINTAIN_FRAMERATE;
import static org.webrtc.RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION;


public class RTCPeerConnection {
    static final String TAG = RTCPeerConnection.class.getCanonicalName();

    Supervisor supervisor;
    String pc_id;

    RTCConfiguration config;
    PeerConnection peerConnection;
    //    MediaStream localStream;
    MediaStream remoteStream;
    PeerConnection.PeerConnectionState state;

    Map<Integer, DataChannel> channels;

    public interface Supervisor {
        void onDisconnect(RTCPeerConnection pc);

        void onObserveEvent(String id, Action action, String message, String usage);
    }

    public interface MessageHandler {
        void success();

        void success(String msg);

        void error(String msg);
    }

    public RTCPeerConnection(Supervisor supervisor, String pc_id, RTCConfiguration config) {
        this.supervisor = supervisor;
        this.pc_id = pc_id;
        Log.v(TAG, "DUALSTREAM pc_id" + pc_id);

        this.config = config;

        this.channels = new HashMap<>();
    }

    public String getPc_id() {
        return pc_id;
    }

    PeerConnection.RTCConfiguration generateConfiguration(RTCConfiguration config) {
        LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
        for (RTCIceServer iceServer : config.iceServers) {
            if (iceServer.urls == null || iceServer.urls.length == 0) {
                continue;
            }
            PeerConnection.IceServer.Builder builder =
                    PeerConnection.IceServer.builder(Arrays.asList(iceServer.urls));
            if (iceServer.username != null) {
                builder.setUsername(iceServer.username);
            }
            if (iceServer.credential != null &&
                    (iceServer.credentialType == null ||
                            iceServer.credentialType == RTCIceCredentialType.password)) {
                builder.setPassword(iceServer.credential.toString());
            }

            iceServers.add(builder.createIceServer());
        }

        PeerConnection.RTCConfiguration configuration =
                new PeerConnection.RTCConfiguration(iceServers);
        if (config.bundlePolicy != null) {
            configuration.bundlePolicy = PeerConnection.BundlePolicy.valueOf(
                    config.bundlePolicy.name().replace("-", "").toUpperCase());
        }

        if (config.iceCandidatePoolSize != 0) {
            configuration.iceCandidatePoolSize = config.iceCandidatePoolSize;
        }

        if (config.iceTransportPolicy != null) {
            configuration.iceTransportsType = PeerConnection.IceTransportsType.valueOf(
                    config.iceTransportPolicy.name().toUpperCase());
        }

        if (config.rtcpMuxPolicy != null) {
            configuration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.valueOf(
                    config.rtcpMuxPolicy.name().toUpperCase());
        }

        return configuration;
    }

    public void createInstance(MessageHandler handler) {

        //TODO
        peerConnection = PCFactory.factory().createPeerConnection(
                generateConfiguration(config),
                new RTCObserver(this, "createPeerConnection:" + pc_id) {
                });
    }

    public boolean setConfiguration(RTCConfiguration config) {
        return peerConnection.setConfiguration(generateConfiguration(config));
    }

    public void createOffer(MessageHandler handler, RTCOfferOptions options) {
        MediaConstraints mediaConstraints = new MediaConstraints();
        if (options != null) {
            if (options.iceRestart) {
                mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("IceRestart", "true"));
            }
            mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", String.valueOf(options.offerToReceiveAudio)));
            mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(options.offerToReceiveVideo)));
            if (options.voiceActivityDetection) {
                mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("VoiceActivityDetection", "true"));
            }
        }
//        mediaConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        peerConnection.createOffer(new RTCObserver(this, "createOffer:" + pc_id) {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                String offer = "";
                try {
                    JSONObject sdp = new JSONObject();
                    sdp.put("type", "offer");
                    sdp.put("sdp", sessionDescription.description);
                    offer = sdp.toString();
                    handler.success(offer);
                } catch (JSONException e) {
                    Log.e(TAG, "CreateOffer success but to json string failed:" + e.toString());

                    handler.error(e.toString());
                }
            }
        }, mediaConstraints);
    }

    public void createAnswer(MessageHandler handler, RTCOfferOptions options) {
        MediaConstraints mediaConstraints = new MediaConstraints();
        if (options != null) {
            if (options.iceRestart) {
                mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("IceRestart", "true"));
            }
            mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", String.valueOf(options.offerToReceiveAudio)));
            mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", String.valueOf(options.offerToReceiveVideo)));
            if (options.voiceActivityDetection) {
                mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("VoiceActivityDetection", "true"));
            }
        }
        peerConnection.createAnswer(new RTCObserver(this, "createAnswer:" + pc_id) {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                String answer = "";
                try {
                    JSONObject sdp = new JSONObject();
                    sdp.put("type", "answer");
                    sdp.put("sdp", sessionDescription.description);
                    answer = sdp.toString();
                    handler.success(answer);
                } catch (JSONException e) {
                    Log.e(TAG, "CreateAnswer success but to json string failed:" + e.toString());

                    handler.error(e.toString());
                }
            }
        }, mediaConstraints);
    }

    public void createDataChannel(MessageHandler handler, String label, RTCDataChannelInit config) {
        DataChannel.Init init = new DataChannel.Init();
        init.id = (int) config.id;
        init.maxRetransmits = (int) config.maxRetransmits;
        init.maxRetransmitTimeMs = (int) config.maxPacketLifeTime;
        init.negotiated = config.negotiated;
        init.ordered = config.ordered;
        init.protocol = config.protocol;
        DataChannel dataChannel = peerConnection.createDataChannel(label, init);
        channels.put(dataChannel.id(), dataChannel);
        dataChannel.registerObserver(new DCObserver(dataChannel));
    }

    public void addTrack(String kind, MediaStreamTrackWrapper wrapper) {
        peerConnection.addTrack(wrapper.getTrack());
    }

    public void setLocalDescription(MessageHandler handler, String type, String description) {
        peerConnection.setLocalDescription(new RTCObserver(this, "setLocalDescription" + pc_id) {
            @Override
            public void onSetSuccess() {
                super.onSetSuccess();
                StringBuilder builder = new StringBuilder();
                builder.append("[");
                boolean first = true;
                for (RtpSender sender :
                        peerConnection.getSenders()) {
                    JSONObject obj = new JSONObject();
                    try {
                        obj.put("kind", sender.track().kind());
                        ObjectMapper objectMapper = new ObjectMapper();

                        obj.put("parameter", objectMapper.writeValueAsString(sender.getParameters()));
                    } catch (Exception e) {
                        Log.e(TAG, "put parameter failed:" + e.toString());
                    }
                    if (first) {
                        first = false;
                    } else {
                        builder.append(",");
                    }
                    builder.append(obj.toString());
                }
                builder.append("]");
                Log.e(TAG, "getSenders:" + builder.toString());
                handler.success(builder.toString());

            }

            @Override
            public void onSetFailure(String s) {
                super.onSetFailure(s);
                handler.error(s);
            }
        }, new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), description));
    }

    public void setRemoteDescription(MessageHandler handler, String type, String description) {
        peerConnection.setRemoteDescription(new RTCObserver(this, "setRemoteDescription" + pc_id + "desc: " + description) {
            @Override
            public void onSetSuccess() {
                super.onSetSuccess();
                handler.success();
            }

            @Override
            public void onSetFailure(String s) {
                super.onSetFailure(s);
                handler.error(s);
            }
        }, new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), description));
    }

    public void addIceCandidate(MessageHandler handler, String candidate) {
        try {
            JSONObject obj = new JSONObject(candidate);
            Log.e(TAG, "Debug....addIceCandidate:" + candidate);
            Log.e(TAG, "Debug....sdpMid:" + obj.getString("sdpMid"));
            Log.e(TAG, "Debug....sdpMLineIndex:" + obj.getInt("sdpMLineIndex"));
            Log.e(TAG, "Debug....candidate:" + obj.getString("candidate"));
            peerConnection.addIceCandidate(new IceCandidate(obj.getString("sdpMid"), obj.getInt("sdpMLineIndex"), obj.getString("candidate")));
            handler.success();
        } catch (Exception e) {
            Log.e(TAG, "setLocalDescription exception:" + e.toString());
            handler.error(e.toString());
        }
    }

    public void removeTrack(String kind, MediaStreamTrackWrapper wrapper) {
        for (RtpSender sender :
                peerConnection.getSenders()) {
            if (sender.track().kind().equals(kind)) {
                Log.w(TAG, "remove track " + kind);
                peerConnection.removeTrack(sender);
                break;
            }
        }
    }

    public void replaceTrack(String kind, MediaStreamTrack track) {
        for (RtpSender sender :
                peerConnection.getSenders()) {
            if (sender.track().kind().equals(kind)) {
                sender.setTrack(track, false);
                break;
            }
        }
    }

    public void setRtpSenderParameters(String kind, String degradation, int maxBitrate, int minBitrate, double scaleDown) {
        for (RtpSender sender :
                peerConnection.getSenders()) {
            if (sender.track().kind().equals(kind)) {
                RtpParameters parameters = sender.getParameters();
                if (degradation.length() > 0) {
                    degradation = degradation.replace("-", "_");
                    Log.e(TAG, "degration:" + BALANCED + " " + MAINTAIN_FRAMERATE + " " + MAINTAIN_RESOLUTION + " " + degradation);
                    parameters.degradationPreference = RtpParameters.DegradationPreference.valueOf(degradation.toUpperCase());
                }
                if (maxBitrate > 0) {
                    parameters.encodings.get(0).maxBitrateBps = maxBitrate;
                }
                if (minBitrate > 0) {
                    parameters.encodings.get(0).minBitrateBps = minBitrate;
                }
                if (scaleDown > 0) {
                    parameters.encodings.get(0).scaleResolutionDownBy = scaleDown;
                }
                StringBuilder l = new StringBuilder();
                l.append("setRtpSenderParameter ").append(parameters.degradationPreference).append(" ").
                        append(parameters.encodings.get(0).maxBitrateBps).append(" ").
                        append(parameters.encodings.get(0).minBitrateBps).append(" ").
                        append(parameters.encodings.get(0).scaleResolutionDownBy);
                Log.v(TAG, l.toString());
                sender.setParameters(parameters);
                break;
            }
        }
    }

    void closeStream() {
        for (RtpSender sender :
                peerConnection.getSenders()) {
            peerConnection.removeTrack(sender);
        }
        if (remoteStream != null) {
            peerConnection.removeStream(remoteStream);
            remoteStream = null;
        }
    }

    public void getStats(MessageHandler handler, MediaStreamTrackWrapper wrapper) {
        peerConnection.getStats(new StatsReport(handler, wrapper));
    }

    public void dispose() {
        if (supervisor == null) {
            return;
        }
        supervisor = null;

        for (Map.Entry<Integer, DataChannel> entry :
                channels.entrySet()) {
            entry.getValue().unregisterObserver();
            entry.getValue().close();
        }
        channels.clear();
        channels = null;

        MediaStreamTrackWrapper.removeMediaStreamTrackByPCId(pc_id);

        pc_id = null;
        closeStream();
        if (state == PeerConnection.PeerConnectionState.CONNECTED) {
            peerConnection.close();
        }
        config = null;
        peerConnection = null;
    }

    public void closeDC(int dcid) {
        DataChannel dataChannel = channels.get(dcid);
        if (dataChannel != null) {
            dataChannel.unregisterObserver();
            dataChannel.close();
            dataChannel.dispose();
        }
        channels.remove(dcid);
    }

    public void sendDC(int dcid, boolean binary, String msg) {
        DataChannel dataChannel = channels.get(dcid);
        if (dataChannel != null) {
            ByteBuffer buf = ByteBuffer.allocateDirect(msg.length());
            buf.put(msg.getBytes());
            buf.rewind();
            dataChannel.send(new DataChannel.Buffer(buf, binary));
        }
    }

    public class StatsReport implements RTCStatsCollectorCallback {
        private MessageHandler _handler;
        private MediaStreamTrackWrapper _wrapper;

        public StatsReport(MessageHandler handler, MediaStreamTrackWrapper wrapper) {
            _handler = handler;
            _wrapper = wrapper;
        }

        @Override
        public void onStatsDelivered(RTCStatsReport rtcStatsReport) {
            String targetId = null;
            String codecId = null;
            String trackId = null;
            String transportId = null;
            String remoteId = null;
            String mediaSourceId = null;
            if (_wrapper != null) {
                String targetType = _wrapper.isLocal() ? "outbound-rtp" : "inbound-rtp";
                for (Map.Entry<String, RTCStats> stat :
                        rtcStatsReport.getStatsMap().entrySet()) {
                    if (stat.getValue().getType().equals(targetType) &&
                            ((String) stat.getValue().getMembers().get("kind"))
                                    .equals(_wrapper.getTrack().kind())) {
                        targetId = stat.getKey();
                        codecId = (String) stat.getValue().getMembers().get("codecId");
                        remoteId = (String) stat.getValue().getMembers().get("remoteId");
                        trackId = (String) stat.getValue().getMembers().get("trackId");
                        transportId = (String) stat.getValue().getMembers().get("transportId");
                        mediaSourceId = (String) stat.getValue().getMembers().get("mediaSourceId");
                    }
                }
            }

            StringBuilder report = new StringBuilder();
            boolean bFirst = true;

            for (Map.Entry<String, RTCStats> stat :
                    rtcStatsReport.getStatsMap().entrySet()) {
                if (_wrapper != null) {
                    String reportId = stat.getKey();
                    String reportType = stat.getValue().getType();
                    if (!(reportType.equals("certificate") || reportType.equals("candidate-pair")
                            || reportType.equals("remote-candidate")
                            || reportType.equals("local-candidate")
                            || reportId.equals(targetId)
                            || reportId.equals(codecId)
                            || reportId.equals(trackId)
                            || reportId.equals(transportId)
                            || reportId.equals(remoteId)
                            || reportId.equals(mediaSourceId))) {
                        continue;
                    }
                }
                report.append(bFirst ? "{" : ",");
                if (bFirst) {
                    bFirst = false;
                }

                report.append("\"").append(stat.getKey()).append("\":");
                report.append("{\"id\":\"").append(stat.getValue().getId()).append("\"")
                        .append(",\"timestamp\":").append((long) stat.getValue().getTimestampUs() / 1000)
                        .append(",\"type\":\"").append(stat.getValue().getType()).append("\"");

                Iterator it = stat.getValue().getMembers().entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Object> entry = (Map.Entry) it.next();
                    report.append(", \"").append((String) entry.getKey()).append("\"").append(": ");
                    appendValue(report, entry.getValue());
                }
                report.append("}");
            }
            report.append("}");

            _handler.success(report.toString());
        }

        private void appendValue(StringBuilder builder, Object value) {
            if (value instanceof Object[]) {
                Object[] arrayValue = (Object[]) value;
                builder.append('[');

                for (int i = 0; i < arrayValue.length; ++i) {
                    if (i != 0) {
                        builder.append(", ");
                    }

                    appendValue(builder, arrayValue[i]);
                }

                builder.append(']');
            } else if (value instanceof String) {
                builder.append('"').append(value).append('"');
            } else {
                builder.append(value);
            }
        }
    }


    public class RTCObserver implements SdpObserver, PeerConnection.Observer {
        private String usage;
        RTCPeerConnection pc;

        public RTCObserver(RTCPeerConnection pc, String u) {
            usage = u;
            this.pc = pc;
        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.v(TAG, usage + " onSignalingChange " + signalingState.toString());
            if (supervisor != null) {
                supervisor.onObserveEvent(pc_id, Action.onSignalingStateChange, signalingState.toString().toLowerCase(), usage);
            }
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.v(TAG, usage + " onIceConnectionChange " + iceConnectionState.toString());

            if (supervisor != null) {
                supervisor.onObserveEvent(pc_id, Action.onIceConnectionStateChange, iceConnectionState.toString().toLowerCase(), usage);
            }
        }

        @Override
        public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
            Log.v(TAG, usage + " onConnectionChange " + newState.toString());
            Log.v(TAG, "DUALSTREAM pc_id" + pc_id + " " + newState.toString());
            state = newState;
            if (supervisor != null) {
                supervisor.onObserveEvent(pc_id, Action.onConnectionStateChange, newState.toString().toLowerCase(), usage);
            }
            if (newState == PeerConnection.PeerConnectionState.CLOSED ||
                    newState == PeerConnection.PeerConnectionState.FAILED) {

                if (supervisor != null) {
                    supervisor.onDisconnect(pc);
                }
                dispose();
            }
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            Log.v(TAG, usage + " onIceConnectionReceivingChange " + String.valueOf(b));
            if (supervisor != null) {
//                supervisor.onObserveEvent(pc_id, Action.onIceConnectionReceivingChange, String.valueOf(b), usage);
            }
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

            Log.v(TAG, usage + " onIceGatheringChange " + iceGatheringState.toString());
            if (supervisor != null) {
                if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                    //send empty candidate if complete
                    supervisor.onObserveEvent(pc_id, Action.onIceCandidate, "", usage);
                }
                supervisor.onObserveEvent(pc_id, Action.onIceGatheringStateChange, iceGatheringState.toString().toLowerCase(), usage);
            }
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            Log.v(TAG, usage + " onIceCandidate " + iceCandidate.toString());
            Log.v(TAG, usage + " onIceCandidateSDP " + iceCandidate.sdp);
            JSONObject obj = new JSONObject();
            try {
                obj.put("sdpMid", iceCandidate.sdpMid);
                obj.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                obj.put("candidate", iceCandidate.sdp);
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
            supervisor.onObserveEvent(pc_id, Action.onIceCandidate, obj.toString(), usage);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

            Log.v(TAG, usage + " onIceCandidatesRemoved ");

//            JSONArray array = new JSONArray();
//            for (IceCandidate iceCandidate : iceCandidates) {
//                JSONObject obj = new JSONObject();
//                try {
//                    obj.put("sdpMid", iceCandidate.sdpMid);
//                    obj.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
//                    obj.put("candidate", iceCandidate.sdp);
//                    array.put(obj);
//                } catch (Exception e) {
//                    Log.e(TAG, e.toString());
//                }
//            }
//            supervisor.onObserveEvent(pc_id, Action.onIceCandidatesRemoved, array.toString(), usage);
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            Log.v(TAG, usage + " onAddTrack " + rtpReceiver.track().kind());

            MediaStreamTrackWrapper wrapper = MediaStreamTrackWrapper.cacheMediaStreamTrackWrapper(pc_id, rtpReceiver);

            supervisor.onObserveEvent(pc_id, Action.onTrack, wrapper.toString(), usage);
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {

            Log.v(TAG, usage + " onAddStream " + mediaStream.videoTracks.size());
            if (remoteStream != null) {
                peerConnection.removeStream(remoteStream);
            }
            remoteStream = mediaStream;
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

            Log.v(TAG, usage + " onRemoveStream ");
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            channels.put(dataChannel.id(), dataChannel);
            Log.v(TAG, usage + " onDataChannel ");
            dataChannel.registerObserver(new DCObserver(dataChannel));
            if (supervisor != null) {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("id", dataChannel.id());
                    obj.put("state", dataChannel.state());
                    obj.put("bufferedAmount", dataChannel.bufferedAmount());
                    obj.put("label", dataChannel.label());
                } catch (Exception e) {
                    Log.e(TAG, "onDataChannel exception:" + e.toString());
                }
                supervisor.onObserveEvent(pc_id, Action.onDataChannel,
                        obj.toString(), "");
            }
        }

        @Override
        public void onRenegotiationNeeded() {

            Log.v(TAG, usage + " onRenegotiationNeeded ");
            if (supervisor != null) {
                supervisor.onObserveEvent(pc_id, Action.onNegotiationNeeded, "", usage);
            }
        }

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {

            Log.v(TAG, usage + " onCreateSuccess" + sessionDescription.description.toString());
        }

        @Override
        public void onSetSuccess() {

            Log.v(TAG, usage + " onSetSuccess");
        }

        @Override
        public void onCreateFailure(String s) {

            Log.v(TAG, usage + " onCreateFailure" + s.toString());
        }

        @Override
        public void onSetFailure(String s) {

            Log.v(TAG, usage + " onSetFailure" + s.toString());
        }
    }

    public class DCObserver implements DataChannel.Observer {
        DataChannel channel;

        public DCObserver(DataChannel dataChannel) {
            channel = dataChannel;
        }

        @Override
        public void onBufferedAmountChange(long l) {
            if (supervisor != null) {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("id", channel.id());
                    obj.put("previousAmount ", l);
                    obj.put("amount", channel.bufferedAmount());
                } catch (Exception e) {
                    Log.e(TAG, "onBufferedAmountChange exception:" + e.toString());
                }
                supervisor.onObserveEvent(pc_id, Action.onBufferedAmountChange,
                        obj.toString(), "");
            }
        }

        @Override
        public void onStateChange() {
            if (supervisor != null) {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("id", channel.id());
                    obj.put("state", channel.state().name().toLowerCase());
                } catch (Exception e) {
                    Log.e(TAG, "onStateChange exception:" + e.toString());
                }
                supervisor.onObserveEvent(pc_id, Action.onStateChange,
                        obj.toString(), "");
            }
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            if (supervisor != null) {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("id", channel.id());
                    obj.put("binary", buffer.binary);
                    if (!buffer.binary) {
                        obj.put("data", StandardCharsets.UTF_8.decode(buffer.data)
                                .toString());
                    } else {
                        byte[] buf = new byte[buffer.data.remaining()];
                        buffer.data.get(buf);
                        obj.put("data", Base64.encodeToString(buf, Base64.DEFAULT));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "onMessage exception:" + e.toString());
                }
                supervisor.onObserveEvent(pc_id, Action.onMessage,
                        obj.toString(), "");
            }
        }
    }
}
