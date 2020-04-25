var exec = require('cordova/exec');
var PLUGIN_NAME = 'Zoom';

function callNativeFunction (name, args, success, error) {
  args = args || [];
  success = success || function () {};
  error = error || function () {};
  exec(success, error, PLUGIN_NAME, name, args);
}

var zoom = {

    joinMeeting: function (meetingNo, meetingPassword, displayName, options, success, error) {
        callNativeFunction('joinMeeting', [meetingNo, meetingPassword, displayName, options], success, error);
    }

};

module.exports = zoom;
