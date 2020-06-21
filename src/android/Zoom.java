package cordova.plugin.zoom;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.util.Log;
import us.zoom.sdk.JoinMeetingOptions;
import us.zoom.sdk.MeetingViewsOptions;
import us.zoom.sdk.JoinMeetingParams;
import us.zoom.sdk.MeetingError;
import us.zoom.sdk.MeetingService;
import us.zoom.sdk.InMeetingService;
import us.zoom.sdk.MeetingEndReason;
import us.zoom.sdk.MeetingSettingsHelper;
import us.zoom.sdk.ZoomSDK;
import us.zoom.sdk.ZoomSDKInitializeListener;
import cordova.plugin.zoom.AuthThread;
import cordova.plugin.zoom.InMeetingListener;
import java.util.concurrent.FutureTask;

/**
 * Zoom
 *
 * A Cordova Plugin to use Zoom Video Conferencing services on Cordova applications.
 *
 * @author  Carson Chen (carson.chen@zoom.us)
 * @version v4.4.55130.0712
 */
public class Zoom extends CordovaPlugin implements ZoomSDKInitializeListener {
    /* Debug variables */
    private static final String TAG = "<------- ZoomCordovaPlugin ---------->";
    private static final boolean DEBUG = false;
    public static final Object LOCK = new Object();
    private final String WEB_DOMAIN = "zoom.us";
    private ZoomSDK mZoomSDK;
    private CallbackContext callbackContext;
    private InitAuthSDKCallback mInitAuthSDKCallback;
    private InMeetingListener listener = new InMeetingListener() {
        @Override
        public void onMeetingLeaveComplete(long l) {
            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    webView.loadUrl("javascript:meetingEnded()");
                }
            });
    }

    };

    /**
     * execute
     *
     * The bridging method to get parameters from JavaScript to execute the relevant Java methods.
     *
     * @param action            action name.
     * @param args              arguements.
     * @param callbackContext   callback context.
     * @return                  true if everything runs smooth / false if something is wrong.
     * @throws JSONException    might throw exceptions when parsing JSON arrays and objects.
     */
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
            throws JSONException {
        if (DEBUG) {
            Log.v(TAG, "----- [execute , action =" + action + "]");
            Log.v(TAG, "----- [execute , args =" + args + "]");
        }

        cordova.setActivityResultCallback(this);
        this.callbackContext = callbackContext;
        this.mZoomSDK = ZoomSDK.getInstance();
        this.registerMeetingServiceListener();
        switch (action) {
            case "initialize":
            String appKey = args.getString(0);
            String appSecret = args.getString(1);
            this.initialize(appKey, appSecret, callbackContext);
            break;
            case "joinMeeting":
                String meetingNo = args.getString(0);
                String meetingPassword = args.getString(1);
                String displayNameJ = args.getString(2);
                JSONObject optionsJ = args.getJSONObject(3);
                this.joinMeeting(meetingNo, meetingPassword, displayNameJ, optionsJ, callbackContext);
                break;
            default:
                return false;
        }
        return true;
    }

    private void registerMeetingServiceListener() {
        InMeetingService meetingService = this.mZoomSDK.getInMeetingService();
        if (meetingService != null) {
            meetingService.addListener(listener);
        }
    }

      /**
     * initialize
     *
     * Initialize Zoom SDK.
     *
     * @param appKey        Zoom SDK app key.
     * @param appSecret     Zoom SDK app secret.
     * @param callbackContext Cordova callback context.
     */
      private void initialize(String appKey, String appSecret, CallbackContext callbackContext) {
        if (DEBUG) {
            Log.v(TAG, "********** Zoom's initialize called **********");
            Log.v(TAG, "appKey length = " + appKey.length());
            Log.v(TAG, "appSecret length= " + appSecret.length());
        }
    
        if (appKey == null || appKey.trim().isEmpty() || appKey.equals("null")
                || appSecret == null || appSecret.trim().isEmpty() || appSecret.equals("null")) {
            callbackContext.error("Both SDK key and secret cannot be empty");
            return;
        }
        
        try {
            AuthThread at = new AuthThread();                           // Prepare Auth Thread
            at.setCordova(cordova);                                     // Set cordova
            at.setCallbackContext(callbackContext);                     // Set callback context
            at.setAction("initialize");                                 // Set action
            at.setLock(LOCK);
            at.setInitParameters(appKey, appSecret, this.WEB_DOMAIN);   // Set init parameters
            FutureTask<Boolean> fr = new FutureTask<Boolean>(at);

            cordova.getActivity().runOnUiThread(fr);                    // Run init method on main thread

            boolean threadSuccess = fr.get();                           // False if has error.
            if (DEBUG) {
                Log.v(TAG, "******************Return from Future is: " + threadSuccess);
            }

            if (threadSuccess) {
                // Wait until the initialize result is back.
                synchronized (LOCK) {
                    try {
                        if (DEBUG) {
                            Log.v(TAG, "Wait................................");
                        }
                        LOCK.wait();
                    } catch (InterruptedException e) {
                        if (DEBUG) {
                            Log.v(TAG, e.getMessage());
                        }
                    }
                }
            }

            callbackContext.success("Initialize successfully!");
        } catch (Exception e) {
            callbackContext.error(e.getMessage());
        }
        

      }

    /**
     * joinMeeting
     *
     * Join a meeting
     *
     * @param meetingNo         meeting number.
     * @param meetingPassword   meeting password
     * @param displayName       display name shown in meeting.
     * @param option            meeting options.
     * @param callbackContext   cordova callback context.
     */
    private void joinMeeting(String meetingNo, String meetingPassword, String displayName, JSONObject option, CallbackContext callbackContext) {

        if (DEBUG) { Log.v(TAG, "********** Zoom's join meeting called ,meetingNo=" + meetingNo + " **********"); }

        if (meetingNo == null || meetingNo.trim().isEmpty() || meetingNo.equals("null")) {
            callbackContext.error("Meeting number cannot be empty");
            return;
        }

        String meetingNumber = meetingNo.trim().replaceAll("[^0-9]", "");

        if (meetingNumber.length() < 9 || meetingNumber.length() > 11 || !meetingNumber.matches("\\d{8,11}")) {
            callbackContext.error("Please enter a valid meeting number.");
            return;
        }



        if (DEBUG) {
            Log.v(TAG, "[Going to Join Meeting]");
            Log.v(TAG, "[meetingNo=]" + meetingNumber);
        }
        PluginResult pluginResult = null;
        // If the meeting number is invalid, throw error.
        if (meetingNumber.length() == 0) {
            pluginResult =  new PluginResult(PluginResult.Status.ERROR, "You need to enter a meeting number which you want to join.");
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
            return;
        }

        // Get Zoom SDK instance.
        ZoomSDK zoomSDK = ZoomSDK.getInstance();

        // If the Zoom SDK instance is not initialized, throw error.
        if(!zoomSDK.isInitialized()) {
            pluginResult =  new PluginResult(PluginResult.Status.ERROR, "ZoomSDK has not been initialized successfully");
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
            return;
        }

        // Get meeting service instance.
        MeetingService meetingService = zoomSDK.getMeetingService();

        // Configure join meeting parameters.
        JoinMeetingParams params = new JoinMeetingParams();
        params.displayName = displayName;
        params.meetingNo = meetingNumber;

        // Set meeting password.
        if (meetingPassword.length() > 0) {
            params.password = meetingPassword;
        }

        if (option != null) {
            // If meeting option is provoded, setup meeting options and join meeting.
            JoinMeetingOptions opts = new JoinMeetingOptions();
            try {
                opts.no_driving_mode = option.isNull("no_driving_mode")? true : option.getBoolean("no_driving_mode");
                opts.no_invite = option.isNull("no_invite")? true : option.getBoolean("no_invite");
                opts.no_meeting_end_message = option.isNull("no_meeting_end_message")? true : option.getBoolean("no_meeting_end_message");
                opts.no_titlebar = option.isNull("no_titlebar")? false : option.getBoolean("no_titlebar");
                opts.no_bottom_toolbar = option.isNull("no_bottom_toolbar")? false : option.getBoolean("no_bottom_toolbar");
                opts.no_dial_in_via_phone = option.isNull("no_dial_in_via_phone")? false : option.getBoolean("no_dial_in_via_phone");
                opts.no_dial_out_to_phone = option.isNull("no_dial_out_to_phone")? true : option.getBoolean("no_dial_out_to_phone");
                opts.no_disconnect_audio = option.isNull("no_disconnect_audio")? true : option.getBoolean("no_disconnect_audio");
                
                //opts.no_share = option.isNull("no_share")? false : option.getBoolean("no_share");
                //opts.no_audio = option.isNull("no_audio")? false : option.getBoolean("no_audio");
                //opts.no_video = option.isNull("no_video")? false : option.getBoolean("no_video");
                opts.no_meeting_error_message = option.isNull("no_meeting_error_message")? true : option.getBoolean("no_meeting_error_message");
                MeetingSettingsHelper msHelper = zoomSDK.getMeetingSettingsHelper();
                msHelper.setAutoConnectVoIPWhenJoinMeeting(true);
                msHelper.setAlwaysShowMeetingToolbarEnabled(true);
                if (!option.isNull("is_consulting") && option.getBoolean("is_consulting")) {
                    opts.meeting_views_options =
                    MeetingViewsOptions.NO_BUTTON_PARTICIPANTS +
                    MeetingViewsOptions.NO_BUTTON_MORE +
                    MeetingViewsOptions.NO_TEXT_MEETING_ID +
                    MeetingViewsOptions.NO_TEXT_PASSWORD;
                    msHelper.setMuteMyMicrophoneWhenJoinMeeting(false);
                    msHelper.setTurnOffMyVideoWhenJoinMeeting(false);
                } else {
                    opts.meeting_views_options =
                    MeetingViewsOptions.NO_BUTTON_PARTICIPANTS +
                    MeetingViewsOptions.NO_BUTTON_MORE +
                    MeetingViewsOptions.NO_BUTTON_SHARE +
                    MeetingViewsOptions.NO_TEXT_MEETING_ID +
                    MeetingViewsOptions.NO_TEXT_PASSWORD;
                    msHelper.setMuteMyMicrophoneWhenJoinMeeting(true);
                    msHelper.setTurnOffMyVideoWhenJoinMeeting(true);
                }
                opts.custom_meeting_id = option.isNull("custom_meeting_id")? "" : option.getString("custom_meeting_id");
                opts.invite_options = 2;
                //

            } catch (JSONException ex) {
                if (DEBUG) { Log.i(TAG, ex.getMessage()); }
            }

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    int response = meetingService.joinMeetingWithParams(
                            cordova.getActivity().getApplicationContext(),params, opts);
                    Log.i(TAG, "In JoinMeeting, response=" + getMeetingErrorMessage(response));
                    PluginResult pluginResult = null;
                    if (response != MeetingError.MEETING_ERROR_SUCCESS) {
                        pluginResult =  new PluginResult(PluginResult.Status.ERROR, getMeetingErrorMessage(response));
                        pluginResult.setKeepCallback(true);
                        callbackContext.sendPluginResult(pluginResult);
                    } else {
                        pluginResult =  new PluginResult(PluginResult.Status.OK, getMeetingErrorMessage(response));
                        pluginResult.setKeepCallback(true);
                        callbackContext.sendPluginResult(pluginResult);
                    }
                }
            });
        } else {
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    // If meeting option is not provided, simply join meeting.
                    int response = meetingService.joinMeetingWithParams(
                            cordova.getActivity().getApplicationContext(), params);
                    if (DEBUG) { Log.i(TAG, "In JoinMeeting, response=" + getMeetingErrorMessage(response)); }
                    PluginResult pluginResult = null;
                    if (response != MeetingError.MEETING_ERROR_SUCCESS) {
                        pluginResult =  new PluginResult(PluginResult.Status.ERROR, getMeetingErrorMessage(response));
                        pluginResult.setKeepCallback(true);
                        callbackContext.sendPluginResult(pluginResult);
                    } else {
                        pluginResult =  new PluginResult(PluginResult.Status.OK, getMeetingErrorMessage(response));
                        pluginResult.setKeepCallback(true);
                        callbackContext.sendPluginResult(pluginResult);
                    }
                }
            });
        }
    }
    /**
     * getMeetingErrorMessage
     *
     * Get meeting error message.
     *
     * @param errorCode error code.
     * @return A string message.
     */
    private String getMeetingErrorMessage(int errorCode) {

        StringBuilder message = new StringBuilder();

        switch (errorCode) {
            case MeetingError.MEETING_ERROR_CLIENT_INCOMPATIBLE:
                message.append("Zoom SDK version is too low to connect to the meeting");
                break;
            case MeetingError.MEETING_ERROR_DISALLOW_HOST_RESGISTER_WEBINAR:
                message.append("Cannot register a webinar using the host email");
                break;
            case MeetingError.MEETING_ERROR_DISALLOW_PANELIST_REGISTER_WEBINAR:
                message.append("Cannot register a webinar using a panelist's email");
                break;
            case MeetingError.MEETING_ERROR_EXIT_WHEN_WAITING_HOST_START:
                message.append("User leave meeting when waiting host to start");
                break;
            case MeetingError.MEETING_ERROR_HOST_DENY_EMAIL_REGISTER_WEBINAR:
                message.append("The register to this webinar is denied by the host");
                break;
            case MeetingError.MEETING_ERROR_INCORRECT_MEETING_NUMBER:
                message.append("Incorrect meeting number");
                break;
            case MeetingError.MEETING_ERROR_INVALID_ARGUMENTS:
                message.append("Failed due to one or more invalid arguments.");
                break;
            case MeetingError.MEETING_ERROR_INVALID_STATUS:
                message.append("Meeting api can not be called now.");
                break;
            case MeetingError.MEETING_ERROR_LOCKED:
                message.append("Meeting is locked");
                break;
            case MeetingError.MEETING_ERROR_MEETING_NOT_EXIST:
                message.append("Meeting dose not exist");
                break;
            case MeetingError.MEETING_ERROR_MEETING_OVER:
                message.append("Meeting ended");
                break;
            case MeetingError.MEETING_ERROR_MMR_ERROR:
                message.append("Server error");
                break;
            case MeetingError.MEETING_ERROR_NETWORK_ERROR:
                message.append("Network error");
                break;
            case MeetingError.MEETING_ERROR_NETWORK_UNAVAILABLE:
                message.append("Network unavailable");
                break;
            case MeetingError.MEETING_ERROR_NO_MMR:
                message.append("No server is available for this meeting");
                break;
            case MeetingError.MEETING_ERROR_REGISTER_WEBINAR_FULL:
                message.append("Arrive maximum registers to this webinar");
                break;
            case MeetingError.MEETING_ERROR_RESTRICTED:
                message.append("Meeting is restricted");
                break;
            case MeetingError.MEETING_ERROR_RESTRICTED_JBH:
                message.append("Join this meeting before host is restricted");
                break;
            case MeetingError.MEETING_ERROR_SESSION_ERROR:
                message.append("Session error");
                break;
            case MeetingError.MEETING_ERROR_SUCCESS:
                message.append("Success");
                break;
            case MeetingError.MEETING_ERROR_TIMEOUT:
                message.append("Timeout");
                break;
            case MeetingError.MEETING_ERROR_UNKNOWN:
                message.append("Unknown error");
                break;
            case MeetingError.MEETING_ERROR_USER_FULL:
                message.append("Number of participants is full.");
                break;
            case MeetingError.MEETING_ERROR_WEB_SERVICE_FAILED:
                message.append("Request to web service failed.");
                break;
            case MeetingError.MEETING_ERROR_WEBINAR_ENFORCE_LOGIN:
                message.append("This webinar requires participants to login.");
                break;
            default:
                break;
        }

        if (DEBUG) {
            Log.v(TAG, "******getMeetingErrorMessage*********" + message.toString());
        }
        return message.toString();
    }
    
    @Override
    public void onZoomSDKInitializeResult(int errorCode, int internalErrorCode) {
        Log.i(TAG, "onZoomSDKInitializeResult, errorCode=" + errorCode + ", internalErrorCode=" + internalErrorCode);

        if (mInitAuthSDKCallback != null) {
            mInitAuthSDKCallback.onZoomSDKInitializeResult(errorCode, internalErrorCode);
        }
    }

    @Override
    public void onZoomAuthIdentityExpired() {
        Log.e(TAG, "onZoomAuthIdentityExpired in init");
    }

}
