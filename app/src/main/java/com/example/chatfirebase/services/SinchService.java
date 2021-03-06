package com.example.chatfirebase.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.chatfirebase.R;
import com.example.chatfirebase.ui.CallEmitterActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.sinch.android.rtc.AudioController;
import com.sinch.android.rtc.ClientRegistration;
import com.sinch.android.rtc.MissingPermissionException;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.Sinch;
import com.sinch.android.rtc.SinchClient;
import com.sinch.android.rtc.SinchError;
import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallClient;
import com.sinch.android.rtc.calling.CallClientListener;
import com.sinch.android.rtc.calling.CallListener;

import java.util.List;

public class SinchService extends Service {

    private static final String TAG = "SinchService";

    private final IBinder binder = new SinchServiceBinder();
    private String currentUid, currentUserName, currentProfileUrl;
    private SinchClient sinchClient;
    private Intent notificationIntent;
    private Call call;
    private Bundle contactBundle;

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null) {
            currentUserName = currentUser.getDisplayName();
            Uri photoUri = currentUser.getPhotoUrl();

            if (!TextUtils.isEmpty(currentUserName) && photoUri != null) {
                currentUid = currentUser.getUid();
                currentProfileUrl = photoUri.toString();

                sinchClient = Sinch.getSinchClientBuilder()
                        .context(this)
                        .userId(currentUid)
                        .applicationKey(getString(R.string.sinch_key))
                        .applicationSecret(getString(R.string.sinch_secret))
                        .environmentHost(getString(R.string.sinch_hostname))
                        .build();

                sinchClient.setSupportCalling(true);

                sinchClient.addSinchClientListener(new SinchClientListener());
                sinchClient.getCallClient().addCallClientListener(new SinchCallClientListener());

                createNotificationChannel();

                Log.d(TAG, "Starting Sinch Client");
                sinchClient.start();
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(getString(R.string.channel_id),
                    getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(getString(R.string.channel_description));
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        if (intent.getExtras() != null) {
            if (sinchIsStarted()) callContact(intent.getExtras());
            else contactBundle = intent.getExtras();
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class SinchServiceBinder extends Binder {

        public SinchService getService() {
            return SinchService.this;
        }
    }

    public boolean sinchIsStarted() {
        return (sinchClient != null && sinchClient.isStarted());
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved");
        super.onTaskRemoved(rootIntent);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (sinchIsStarted()) {
            sinchClient.stopListeningOnActiveConnection();
            sinchClient.terminateGracefully();
        }
    }

    private void callContact(Bundle bundle) {
        if (call == null) {
            String contactId = bundle.getString(getString(R.string.contact_id));
            String contactName = bundle.getString(getString(R.string.contact_name));
            String contactProfileUrl = bundle.getString(getString(R.string.contact_profile_url));
            try {
                call = sinchClient.getCallClient().callUser(contactId);

                Intent emitterIntent = new Intent(SinchService.this, CallEmitterActivity.class);
                emitterIntent.putExtra(getString(R.string.contact_id), contactId);
                emitterIntent.putExtra(getString(R.string.contact_name), contactName);
                emitterIntent.putExtra(getString(R.string.contact_profile_url), contactProfileUrl);
                emitterIntent.putExtra(getString(R.string.user_id), currentUid);
                emitterIntent.putExtra(getString(R.string.user_name), currentUserName);
                emitterIntent.putExtra(getString(R.string.user_profile_url), currentProfileUrl);

                emitterIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(emitterIntent);
            }
            catch (MissingPermissionException e) {
                Toast.makeText(this, getString(R.string.permission_microphone_phone), Toast.LENGTH_LONG).show();
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public Call getCall() {
        return call;
    }

    public AudioController getAudioController() {
        return sinchClient.getAudioController();
    }

    public void callEnded() {
        call = null;
    }

    private class SinchCallClientListener implements CallClientListener {

        @Override
        public void onIncomingCall(CallClient callClient, Call incomingCall) {
            if (call == null) {
                call = incomingCall;
                call.addCallListener(new NotificationCallListener());

                notificationIntent = new Intent(SinchService.this, CallNotificationService.class);
                ContextCompat.startForegroundService(SinchService.this, notificationIntent);
            }
        }
    }

    private class NotificationCallListener implements CallListener {

        @Override
        public void onCallProgressing(Call progressingCall) {
            Log.d(TAG, "onCallProgressing");
        }

        @Override
        public void onCallEstablished(Call establishedCall) {
            Log.d(TAG, "onCallEstablished");
            call.removeCallListener(this);
        }

        @Override
        public void onCallEnded(Call endedCall) {
            Log.d(TAG, "onCallEnded");
            getApplicationContext().stopService(notificationIntent);
            call = null;
        }

        @Override
        public void onShouldSendPushNotification(Call call, List<PushPair> pushPairs) {

        }
    }

    private class SinchClientListener implements com.sinch.android.rtc.SinchClientListener {

        @Override
        public void onClientFailed(SinchClient client, SinchError error) {
            Log.e(TAG, "Sinch Client failed " + error.getMessage());
            Toast.makeText(SinchService.this, getString(R.string.failure_sinch_service), Toast.LENGTH_SHORT).show();
            sinchClient.terminate();
            stopSelf();
        }

        @Override
        public void onClientStarted(SinchClient client) {
            Log.d(TAG, "Sinch Client started");
            sinchClient.startListeningOnActiveConnection();
            if (contactBundle != null) {
                callContact(contactBundle);
                contactBundle = null;
            }
        }

        @Override
        public void onClientStopped(SinchClient client) {
            Log.d(TAG, "Sinch Client stopped");
        }

        @Override
        public void onLogMessage(int level, String area, String message) {
            switch (level) {
                case Log.DEBUG:
                    Log.d(area, message);
                    break;
                case Log.ERROR:
                    Log.e(area, message);
                    break;
                case Log.INFO:
                    Log.i(area, message);
                    break;
                case Log.VERBOSE:
                    Log.v(area, message);
                    break;
                case Log.WARN:
                    Log.w(area, message);
                    break;
            }
        }

        @Override
        public void onRegistrationCredentialsRequired(SinchClient client, ClientRegistration clientRegistration) {

        }
    }
}