var exec = require('cordova/exec');
var PLUGIN_NAME = 'Zoom';

function callNativeFunction (name, args, success, error) {
  args = args || [];
  success = success || function () {};
  error = error || function () {};
  exec(success, error, PLUGIN_NAME, name, args);
}
var zoom = {

    initialize: function (appKey, appSecret, success, error) {
        callNativeFunction('initialize', [appKey, appSecret], success, error);
    },
  
    joinMeeting: function (meetingNo, meetingPassword, displayName, options, success, error) {
        callNativeFunction('joinMeeting', [meetingNo, meetingPassword, displayName, options], success, error);
    }

};

window.meetingEnded = function () { 
    console.log("Call Ended!")
    navigator.app.exitApp();
}
module.exports = zoom;
