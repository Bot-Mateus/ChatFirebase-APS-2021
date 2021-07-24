package com.example.chatfirebase.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatfirebase.ChatFirebaseApplication;
import com.example.chatfirebase.R;
import com.example.chatfirebase.data.CallInfo;
import com.example.chatfirebase.services.SinchService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.squareup.picasso.Picasso;
import com.xwray.groupie.GroupAdapter;
import com.xwray.groupie.GroupieViewHolder;
import com.xwray.groupie.Item;

import java.text.DateFormat;
import java.util.LinkedList;
import java.util.List;

public class CallsFragment extends Fragment {

    private static final String TAG = "CallsFragment";

    private final List<CallItem> linkedCallItems = new LinkedList<>();

    private static String currentUid;
    private static SinchService sinchService;
    private Query callsQuery;
    private EventListener<QuerySnapshot> callsEventListener;
    private ListenerRegistration callsRegistration;

    private Context context;
    private RecyclerView recyclerView;
    private GroupAdapter<GroupieViewHolder> callsAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        currentUid = FirebaseAuth.getInstance().getUid();

        callsQuery = FirebaseFirestore.getInstance().collection(getString(R.string.collection_talks))
                .document(currentUid)
                .collection(getString(R.string.collection_talks_calls))
                .orderBy(getString(R.string.timestamp), Query.Direction.ASCENDING);

        setCallsEventListener();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calls, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        context = view.getContext();
        sinchService = ((ChatFirebaseApplication) context.getApplicationContext()).getSinchService();
        recyclerView = view.findViewById(R.id.rv_calls);
        callsAdapter = new GroupAdapter<>();

        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(callsAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");
        callsRegistration = callsQuery.addSnapshotListener(callsEventListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop()");
        callsRegistration.remove();
        linkedCallItems.clear();
    }

    private void setCallsEventListener() {
        callsEventListener = (snapshots, e) -> {
            if (e == null) {
                if (snapshots != null) {
                    for (DocumentChange doc : snapshots.getDocumentChanges()) {

                        if (doc.getType() == DocumentChange.Type.ADDED) {
                            Log.d(TAG, "Call " + doc.getDocument().getId() + " ADDED");
                            CallInfo callInfo = doc.getDocument().toObject(CallInfo.class);
                            linkedCallItems.add(0, new CallItem(callInfo));
                        }
                    }

                    callsAdapter.replaceAll(linkedCallItems);
                }
                else {
                    Log.e(TAG, "Null calls snapshot");
                    Toast.makeText(context, getString(R.string.failure_calls), Toast.LENGTH_SHORT).show();
                }
            }
            else {
                Log.e(TAG, "Calls snapshot listener failed", e);
                Toast.makeText(context, getString(R.string.failure_calls), Toast.LENGTH_SHORT).show();
            }
        };
    }

    private static class CallItem extends Item<GroupieViewHolder> {

        private final String contactId;
        private final String contactName;
        private final String contactProfileUrl;
        private final String date;
        private final boolean answered;

        public CallItem(CallInfo callInfo) {
            contactId = callInfo.getContactId();
            contactName = callInfo.getContactName();
            contactProfileUrl = callInfo.getContactProfileUrl();
            date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(callInfo.getTimestamp());
            answered = callInfo.isAnswered();
        }

        @Override
        public void bind(@NonNull GroupieViewHolder viewHolder, int position) {
            ImageView civPhoto = viewHolder.itemView.findViewById(R.id.civ_card_call_photo);
            ImageView ivCall = viewHolder.itemView.findViewById(R.id.iv_card_call);
            TextView tvContactName = viewHolder.itemView.findViewById(R.id.tv_call_username);
            TextView tvDate = viewHolder.itemView.findViewById(R.id.tv_call_timestamp);

            Picasso.get().load(contactProfileUrl).placeholder(R.drawable.profile_placeholder).into(civPhoto);
            ivCall.setImageDrawable(ContextCompat.getDrawable(viewHolder.itemView.getContext(), R.drawable.ic_call_black_icon));
            tvContactName.setText(contactName);
            tvDate.setText(date);

            ivCall.setOnClickListener(view -> sinchService.callUser(contactId, contactName, contactProfileUrl));
        }

        @Override
        public int getLayout() {
            return R.layout.card_call;
        }
    }
}