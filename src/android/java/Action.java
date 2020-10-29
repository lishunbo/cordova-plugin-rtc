package com.agora.cordova.plugin.webrtc;

public enum Action {
    createInstance("createInstance"),
    createOffer("createOffer"),
    setLocalDescription("setLocalDescription"),
    setRemoteDescription("setRemoteDescription"),
    addIceCandidate("addIceCandidate"),
    close("close"),
    removeTrack("removeTrack"),
    getTransceivers("getTransceivers"),
    addTransceiver("addTransceiver"),
    addTrack("addTrack"),
    getStats("getStats"),
    getUserMedia("getUserMedia"),
    onIceCandidate("onIceCandidate");

    private String name;

    Action(String val) {
        this.name = val;
    }

    public String toString() {
        return this.name;
    }
}