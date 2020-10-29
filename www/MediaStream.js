
class MediaStream {

    constructor() {
        this.active = false;
        this.id = "";

        this.onaddtrack = null;
        this.onremovetrack = null;

        this.track = [];
    }

    addTrack(track) { this.track.push(track); }
    clone() { return new MediaStream() }

    getAudioTracks() { }
    getTrackById(trackId) { }
    getTracks() { return this.track; }
    getVideoTracks() { }
    removeTrack() { }
    //addEventListener
    //removeEventListener
}

class MediaStreamTrack {
    constructor() {
        console.log("new MediaStreamTrack");
        // this.enabled = false;
        // this.id = "";
        // this.isolated = false;
        // this.kind = "";
        // this.label = "";
        // this.muted = "";
        // this.onended = null;// ((this: MediaStreamTrack, ev: Event) => any) | null;
        // this.onisolationchange = null;// ((this: MediaStreamTrack, ev: Event) => any) | null;
        // this.onmute = null;// ((this: MediaStreamTrack, ev: Event) => any) | null;
        // this.onunmute = null;// ((this: MediaStreamTrack, ev: Event) => any) | null;
        // this.readyState = ""// MediaStreamTrackState;
        // this.applyConstraints = function (constraints) { return new Promise(() => { }); }
        // this.clone() = function () { return new MediaStreamTrack(); }
        // this.getCapabilities() = function () { }
        // this.getConstraints() = function () { }
        // this.getSettings() = function () { }
        // this.stop() = function () { }
        // addEventListener<K extends keyof MediaStreamTrackEventMap>(type: K, listener: (this: MediaStreamTrack, ev: MediaStreamTrackEventMap[K]) => any, options?: boolean | AddEventListenerOptions): void;
        // addEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | AddEventListenerOptions): void;
        // removeEventListener<K extends keyof MediaStreamTrackEventMap>(type: K, listener: (this: MediaStreamTrack, ev: MediaStreamTrackEventMap[K]) => any, options?: boolean | EventListenerOptions): void;
        // removeEventListener(type: string, listener: EventListenerOrEventListenerObject, options?: boolean | EventListenerOptions): void;
    }
}

cordova.addConstructor(function () {
    window.MediaStream = MediaStream;
    window.MediaStreamTrack = MediaStreamTrack;
    window.MediaDeviceInfo=MediaDeviceInfo;
    return window.MediaStreamTrack;
});

class MediaDeviceInfo {
    constructor() {
        this.deviceId = "";
        this.groupId = "";
        this.Kind = Media.audioinput;
        this.label = "";
    }
    toJson() {
        return JSON.stringify(this);
    }
}

// /**
//  * @module Stream
//  */
// module.exports = {
//     // MediaStream,
//     // MediaStreamTrack,
//     MediaDeviceInfo,
// }