

console.log("mediaDevice.js onloading");

var media = require('./Media');
var stream = require('./Stream');

var mediaDevice = {}

// first class
mediaDevice.getUserMedia = function (config) {
    return new Promise((resolve, reject) => {
        var args = {}
        if (config.video !== undefined) {
            if (typeof config.video === 'boolean') {
                args.video = config.video.toString();
            }
        }
        if (config.audio !== undefined) {
            if (typeof config.audio === 'boolean') {
                args.audio = config.video.toString();
            }
        }

        cordova.exec(function (ev) {
            console.log("Got one stream object with id: " + ev);
            // pc.id = ev
            // console.log("check this.id done:" + pc.id)
            var stm = new stream.MediaStream();
            console.log("after create  stream object");
            console.log("add mock audio track to stream object");
            var audiotrack = new stream.MediaStreamTrack();
            audiotrack.kind = "audio"
            audiotrack.label = "mock audio"
            stm.addTrack(audiotrack);
            console.log("add mock video track to stream object");
            var videotrack = new stream.MediaStreamTrack();
            videotrack.kind = "video"
            videotrack.label = "mock video"
            stm.addTrack(videotrack);
            resolve(stm)
        }, function (ev) {
            console.log("Failed to create RTCPeerConnection object");
        }, 'Hook', 'getUserMedia', [this.id, args]);

    })
}

class MediaDeviceInfo {
    constructor(kind) {
        this.deviceId = "mock device id " + kind;
        this.groupId = kind;
        this.kind = kind;
        this.label = "mock device label " + kind;
    }
}

// first class
mediaDevice.enumerateDevices = function () {
    return new Promise((resolve, reject) => {
        //"audioinput" | "audiooutput" | "videoinput";
        var audioin = new MediaDeviceInfo(media.MediaDeviceKind.audioinput);
        var audioout = new MediaDeviceInfo(media.MediaDeviceKind.audiooutput);
        var video = new MediaDeviceInfo(media.MediaDeviceKind.videoinput);

        resolve([audioin, audioout, video]);
    });
}

mediaDevice.addEventListener = function (event, func) {
    console.log("mediaDevice.js addEventListener" + event);
}



cordova.addConstructor(function () {
    window.MediaDeviceInfo = MediaDeviceInfo;
});

module.exports = mediaDevice;

console.log("mediaDevice.js onloaded");
