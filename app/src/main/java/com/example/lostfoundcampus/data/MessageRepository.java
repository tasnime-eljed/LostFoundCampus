package com.example.lostfoundcampus.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lostfoundcampus.data.model.ConversationSummary;
import com.example.lostfoundcampus.data.model.Message;
import com.example.lostfoundcampus.data.remote.SupabaseClient;
import com.example.lostfoundcampus.data.remote.dto.ConversationRowDto;
import com.example.lostfoundcampus.data.remote.dto.MessageDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Messagerie par polling : conversations (RPC), messages après un identifiant, envoi. */
public final class MessageRepository {

    private static MessageRepository instance;
    private final Context app;
    private final SupabaseClient sb;

    private MessageRepository(Context context) {
        app = context.getApplicationContext();
        sb = SupabaseClient.get();
    }

    public static synchronized MessageRepository get(Context context) {
        if (instance == null) instance = new MessageRepository(context);
        return instance;
    }

    public void myConversations(@Nullable String search, ResultCallback<List<ConversationSummary>> cb) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_search", search == null ? "" : search.trim());
        Calls.enqueue(app, sb.rest.myConversations(body), list -> {
            List<ConversationSummary> out = new ArrayList<>();
            if (list != null) for (ConversationRowDto d : list) out.add(Mappers.conversation(d));
            return out;
        }, cb);
    }

    /** Ouvre (ou crée) la conversation avec l'auteur de l'annonce. Renvoie son identifiant. */
    public void open(String announcementId, ResultCallback<String> cb) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_announcement", announcementId);
        Calls.enqueue(app, sb.rest.openConversation(body), cb);
    }

    /** Messages d'id strictement supérieur à afterId, dans l'ordre (polling incrémental). */
    public void messagesAfter(String conversationId, long afterId, ResultCallback<List<Message>> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("select", "*");
        q.put("conversation_id", "eq." + conversationId);
        q.put("id", "gt." + afterId);
        q.put("order", "id.asc");
        q.put("limit", "200");
        Calls.enqueue(app, sb.rest.messages(q), list -> {
            List<Message> out = new ArrayList<>();
            if (list != null) for (MessageDto d : list) out.add(Mappers.message(d));
            return out;
        }, cb);
    }

    public void send(String conversationId, String text, ResultCallback<Message> cb) {
        Map<String, Object> body = new HashMap<>();
        body.put("conversation_id", conversationId);
        body.put("body", text.trim());
        Calls.enqueue(app, sb.rest.sendMessage(body), list -> {
            if (list == null || list.isEmpty()) throw new IllegalStateException("Message non enregistré");
            return Mappers.message(list.get(0));
        }, cb);
    }

    /** Marque les messages reçus comme lus (sans attendre de réponse). */
    public void markRead(String conversationId) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_conversation", conversationId);
        sb.rest.markConversationRead(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
            }
        });
    }
}
