package com.example.lostfoundcampus.ui.messages;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.model.Message;
import com.example.lostfoundcampus.databinding.ItemMessageReceivedBinding;
import com.example.lostfoundcampus.databinding.ItemMessageSentBinding;
import com.example.lostfoundcampus.util.DateUtil;

import java.util.List;

/**
 * Bulles envoyées / reçues. « Envoi en cours… » tant que le serveur n'a pas confirmé,
 * « Échec de l'envoi » (touchez pour réessayer), « Envoyé · 10:43 » en fin de série.
 */
class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface OnRetry {
        void onRetry(Message message);
    }

    private static final int TYPE_SENT = 0;
    private static final int TYPE_RECEIVED = 1;

    private final List<Message> messages;
    private final String me;
    private final OnRetry onRetry;

    MessageAdapter(List<Message> messages, String me, OnRetry onRetry) {
        this.messages = messages;
        this.me = me;
        this.onRetry = onRetry;
    }

    @Override
    public int getItemViewType(int position) {
        return me.equals(messages.get(position).senderId) ? TYPE_SENT : TYPE_RECEIVED;
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SENT) {
            return new SentHolder(ItemMessageSentBinding.inflate(inflater, parent, false));
        }
        return new ReceivedHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message m = messages.get(position);
        if (holder instanceof SentHolder) {
            ItemMessageSentBinding b = ((SentHolder) holder).b;
            Context c = b.getRoot().getContext();
            b.txtBubble.setText(m.text);
            boolean pending = m.state != Message.State.SENT;
            ViewCompat.setBackgroundTintList(b.txtBubble, ColorStateList.valueOf(ContextCompat.getColor(c,
                    pending ? R.color.lf_violet_400 : R.color.lf_violet_600)));
            boolean endOfRun = position == messages.size() - 1
                    || !me.equals(messages.get(position + 1).senderId);
            b.txtMeta.setTextColor(ContextCompat.getColor(c,
                    m.state == Message.State.FAILED ? R.color.lf_error : R.color.lf_ink_secondary));
            if (m.state == Message.State.SENDING) {
                b.txtMeta.setText(R.string.chat_sending);
                b.txtMeta.setVisibility(View.VISIBLE);
            } else if (m.state == Message.State.FAILED) {
                b.txtMeta.setText(R.string.chat_failed);
                b.txtMeta.setVisibility(View.VISIBLE);
            } else if (endOfRun) {
                b.txtMeta.setText(c.getString(R.string.chat_sent_at, DateUtil.time(m.sentAt)));
                b.txtMeta.setVisibility(View.VISIBLE);
            } else {
                b.txtMeta.setVisibility(View.GONE);
            }
            if (m.state == Message.State.FAILED) {
                b.getRoot().setOnClickListener(v -> onRetry.onRetry(m));
                b.getRoot().setContentDescription(m.text + ". " + c.getString(R.string.chat_failed));
                ViewCompat.replaceAccessibilityAction(b.getRoot(),
                        androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                        c.getString(R.string.cd_retry_message), null);
            } else {
                b.getRoot().setOnClickListener(null);
                b.getRoot().setClickable(false);
                b.getRoot().setContentDescription(null);
            }
        } else {
            ((ReceivedHolder) holder).b.txtBubble.setText(m.text);
        }
    }

    static class SentHolder extends RecyclerView.ViewHolder {
        final ItemMessageSentBinding b;

        SentHolder(ItemMessageSentBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static class ReceivedHolder extends RecyclerView.ViewHolder {
        final ItemMessageReceivedBinding b;

        ReceivedHolder(ItemMessageReceivedBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
