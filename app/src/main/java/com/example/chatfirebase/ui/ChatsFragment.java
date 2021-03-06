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

import com.example.chatfirebase.R;
import com.example.chatfirebase.data.Message;
import com.example.chatfirebase.interfaces.ChatsFragmentListener;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatsFragment extends Fragment {

    private static final String TAG = "ChatsFragment";

    private final Map<String, ChatItem> chatItemMap = new HashMap<>();
    private final List<ChatItem> chatItems = new ArrayList<>();

    private ChatsFragmentListener fragmentListener;
    private Query chatsQuery;
    private EventListener<QuerySnapshot> chatsEventListener;
    private ListenerRegistration chatsRegistration;
    private int totalUnreadMessages;

    private Context context;
    private RecyclerView recyclerView;
    private GroupAdapter<GroupieViewHolder> chatsAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        fragmentListener = (ChatsFragmentListener) getActivity();

        chatsQuery = FirebaseFirestore.getInstance().collection(getString(R.string.collection_talks))
                .document(fragmentListener.getUid())
                .collection(getString(R.string.collection_talks_chats))
                .orderBy(getString(R.string.last_message_timestamp), Query.Direction.DESCENDING);

        setChatsEventListener();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        context = view.getContext();
        recyclerView = view.findViewById(R.id.rv_chats);
        chatsAdapter = new GroupAdapter<>();

        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(chatsAdapter);

        chatsAdapter.setOnItemClickListener((item, v) -> {
            ChatItem chatItem = (ChatItem) item;
            fragmentListener.goToTalkActivity(chatItem.contactId, chatItem.contactName, chatItem.contactProfileUrl);
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");
        totalUnreadMessages = 0;
        chatsRegistration = chatsQuery.addSnapshotListener(chatsEventListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop()");
        chatsRegistration.remove();
        chatItemMap.clear();
        chatItems.clear();
        chatsAdapter.clear();
    }

    // Atualiza as conversas em tempo real
    private void setChatsEventListener() {
        chatsEventListener = (snapshots, e) -> {
            if (e == null) {
                if (snapshots != null) {

                    for (DocumentChange doc : snapshots.getDocumentChanges()) {
                        String contactId = doc.getDocument().getId();
                        Object unreadMessages = doc.getDocument().get(getString(R.string.unreadMessages));

                        switch (doc.getType()) {
                            case ADDED:
                                Log.d(TAG, "Chat " + contactId + " ADDED");
                                String contactName = doc.getDocument()
                                        .getString(context.getString(R.string.name));
                                String contactProfileUrl = doc.getDocument()
                                        .getString(context.getString(R.string.profile_url));
                                Message lastMessage = doc.getDocument()
                                        .get(context.getString(R.string.last_message), Message.class);
                                int messagesCount = (unreadMessages == null) ? 0 : (int) (long) unreadMessages;
                                ChatItem chatItem = new ChatItem(contactId, contactName, contactProfileUrl,
                                        lastMessage, messagesCount);
                                chatItemMap.put(contactId, chatItem);
                                chatItems.add(chatItem);
                                totalUnreadMessages += messagesCount;
                                break;
                            case MODIFIED:
                                Log.d(TAG, "Chat" + contactId + " MODIFIED");
                                ChatItem modifiedChatItem = chatItemMap.get(contactId);
                                if (modifiedChatItem != null) {
                                    Message modifiedLastMessage = doc.getDocument()
                                            .get(getString(R.string.last_message), Message.class);
                                    modifiedChatItem.setLastMessage(modifiedLastMessage);
                                    if (unreadMessages != null) {
                                        if ((long) unreadMessages > 0) {
                                            modifiedChatItem.incrementMessagesCount();
                                            totalUnreadMessages++;
                                        }
                                    }
                                }
                                else Log.e(TAG, "Null chat item");
                                break;
                        }
                    }

                    Collections.sort(chatItems);
                    chatsAdapter.update(chatItems, false);
                    chatsAdapter.notifyDataSetChanged();
                    fragmentListener.updateChatsTab(totalUnreadMessages);
                }
                else {
                    Log.e(TAG, "Null chats snapshot");
                    Toast.makeText(context, getString(R.string.failure_chats), Toast.LENGTH_SHORT).show();
                }
            }
            else {
                Log.e(TAG, "Chats snapshot listener failed", e);
                Toast.makeText(context, getString(R.string.failure_chats), Toast.LENGTH_SHORT).show();
            }
        };
    }

    private static class ChatItem extends Item<GroupieViewHolder> implements Comparable<ChatItem> {

        private final String contactId;
        private final String contactName;
        private final String contactProfileUrl;
        private Message lastMessage;
        private int messagesCount;

        public ChatItem(String contactId, String contactName, String contactProfileUrl, Message lastMessage, int messagesCount) {
            this.contactId = contactId;
            this.contactName = contactName;
            this.contactProfileUrl = contactProfileUrl;
            this.lastMessage = lastMessage;
            this.messagesCount = messagesCount;
        }

        public void setLastMessage(Message message) {
            lastMessage = message;
        }

        public void incrementMessagesCount() {
            messagesCount++;
        }

        @Override
        public void bind(@NonNull GroupieViewHolder viewHolder, int position) {
            ImageView civPhoto = viewHolder.itemView.findViewById(R.id.civ_card_chat_photo);
            ImageView ivMessageRead = viewHolder.itemView.findViewById(R.id.iv_chat_read_icon);
            TextView tvContactName = viewHolder.itemView.findViewById(R.id.tv_chat_username);
            TextView tvDate = viewHolder.itemView.findViewById(R.id.tv_chat_timestamp);
            TextView tvContactLastMessage = viewHolder.itemView.findViewById(R.id.tv_contact_last_message);
            TextView tvUserLastMessage = viewHolder.itemView.findViewById(R.id.tv_user_last_message);
            TextView tvChatBadge = viewHolder.itemView.findViewById(R.id.tv_chat_badge);

            Picasso.get().load(contactProfileUrl).fit().centerCrop()
                    .placeholder(R.drawable.profile_placeholder).into(civPhoto);
            tvContactName.setText(contactName);
            tvDate.setText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(lastMessage.getTimestamp()));

            if (messagesCount > 0) {
                if (messagesCount < 100) tvChatBadge.setText(String.valueOf(messagesCount));
                else tvChatBadge.setText(R.string.max_badge_number);
                tvChatBadge.setVisibility(View.VISIBLE);
            }
            else tvChatBadge.setVisibility(View.INVISIBLE);

            if (contactId.equals(lastMessage.getSenderId())) {
                tvUserLastMessage.setText(null);
                ivMessageRead.setImageDrawable(null);
                tvContactLastMessage.setText(lastMessage.getText());
            }
            else {
                int readIcon = lastMessage.isRead() ? R.drawable.ic_message_read_icon : R.drawable.ic_message_unread_icon;
                tvContactLastMessage.setText(null);
                tvUserLastMessage.setText(lastMessage.getText());
                ivMessageRead.setImageDrawable(ContextCompat.getDrawable(viewHolder.itemView.getContext(), readIcon));
            }
        }

        @Override
        public int getLayout() {
            return R.layout.card_chat;
        }

        @Override
        public int compareTo(ChatItem ci) {
            if (this == ci) return 0;

            int timestampDiff = Long.compare(ci.lastMessage.getTimestamp(), lastMessage.getTimestamp());
            if (timestampDiff != 0) return timestampDiff;

            return contactId.compareTo(ci.contactId);
        }
    }
}