package com.example.chatfirebase.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.chatfirebase.R;
import com.example.chatfirebase.services.SinchService;
import com.sinch.android.rtc.PushPair;
import com.sinch.android.rtc.SinchError;
import com.sinch.android.rtc.calling.Call;
import com.sinch.android.rtc.calling.CallEndCause;
import com.sinch.android.rtc.calling.CallListener;
import com.squareup.picasso.Picasso;

import java.util.List;

public class CallEmitterActivity extends AppCompatActivity implements ServiceConnection {

    private static final String TAG = "CallEmitterActivity";

    private SinchService sinchService;
    private Call call;
    private boolean speakerEnabled;

    private ImageView vbtReject, vbtSpeaker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_emitter);

        ImageView vImgReceiver = findViewById(R.id.civ_receiver_photo);
        TextView vTxtReceiverName = findViewById(R.id.tv_receiver_name);
        vbtReject = findViewById(R.id.iv_emitter_hang_up);
        vbtSpeaker = findViewById(R.id.iv_emitter_speaker);

        Intent serviceIntent = new Intent(this, SinchService.class);
        bindService(serviceIntent, this, 0);

        String profileUrl = getIntent().getStringExtra(getString(R.string.user_profile_url));
        Picasso.get().load(profileUrl).placeholder(R.drawable.profile_placeholder).into(vImgReceiver);

        String username = getIntent().getStringExtra(getString(R.string.user_name));
        vTxtReceiverName.setText(username);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        SinchService.SinchServiceBinder binder = (SinchService.SinchServiceBinder) service;
        sinchService = binder.getService();

        call = sinchService.getCall();
        call.addCallListener(new SinchCallListener());

        vbtReject.setOnClickListener(view -> call.hangup());
        vbtSpeaker.setOnClickListener(view -> {
            int speakerIcon;
            if (speakerEnabled) {
                sinchService.getAudioController().disableSpeaker();
                Toast.makeText(this, getString(R.string.call_speaker_disabled), Toast.LENGTH_SHORT).show();
                speakerIcon = R.drawable.disabled_speaker;
            }
            else {
                sinchService.getAudioController().enableSpeaker();
                Toast.makeText(this, getString(R.string.call_speaker_enabled), Toast.LENGTH_SHORT).show();
                speakerIcon = R.drawable.enabled_speaker;
            }
            vbtSpeaker.setImageDrawable(ContextCompat.getDrawable(this, speakerIcon));
            speakerEnabled = !speakerEnabled;
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.e(TAG, "Sinch Service disconnected");
        Toast.makeText(this, getString(R.string.failure_sinch_service), Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public void onBackPressed() {
        // Do not do anything
    }

    private class SinchCallListener implements CallListener {

        @Override
        public void onCallProgressing(Call progressingCall) {
            Toast.makeText(CallEmitterActivity.this, getString(R.string.on_call_progressing), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCallEstablished(Call establishedCall) {
            CallEmitterActivity.this.setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
            vbtSpeaker.setVisibility(View.VISIBLE);
            Toast.makeText(CallEmitterActivity.this, getString(R.string.on_call_established), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCallEnded(Call endedCall) {
            sinchService.callEnded();
            CallEmitterActivity.this.setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);

            CallEndCause endCause = endedCall.getDetails().getEndCause();
            switch (endCause) {
                case HUNG_UP:
                    Toast.makeText(CallEmitterActivity.this, getString(R.string.call_hang_up), Toast.LENGTH_SHORT).show();
                    break;
                case FAILURE:
                    SinchError e = endedCall.getDetails().getError();
                    Toast.makeText(CallEmitterActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Toast.makeText(CallEmitterActivity.this, endCause.toString(), Toast.LENGTH_SHORT).show();
            }

            CallEmitterActivity.this.finish();
        }

        @Override
        public void onShouldSendPushNotification(Call call, List<PushPair> pushPairs) {

        }
    }
}