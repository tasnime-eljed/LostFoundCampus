package com.example.lostfoundcampus.ui.messages;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.model.ConversationSummary;
import com.example.lostfoundcampus.databinding.ItemConversationBinding;
import com.example.lostfoundcampus.ui.common.UiUtils;
import com.example.lostfoundcampus.util.DateUtil;

import java.util.ArrayList;
import java.util.List;

/** Lignes de l'écran Messages (résultat de la fonction SQL my_conversations). */
class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.Holder> {

    interface OnClick {
        void onConversation(ConversationSummary c);
    }

    private final List<ConversationSummary> items = new ArrayList<>();
    private final OnClick onClick;

    ConversationAdapter(OnClick onClick) {
        this.onClick = onClick;
    }

    void submit(List<ConversationSummary> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemConversationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ConversationSummary conv = items.get(position);
        Context c = h.b.getRoot().getContext();
        boolean unread = conv.unreadCount > 0;

        UiUtils.bindInitialsAvatar(h.b.txtAvatar, conv.other);
        h.b.txtName.setText(conv.other.fullName);
        h.b.txtName.setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);
        h.b.txtTime.setText(conv.hasMessages() ? DateUtil.conversationTime(c, conv.lastMessageAt) : "");
        h.b.txtPreview.setText(conv.hasMessages() ? conv.lastMessagePreview : c.getString(R.string.messages_no_message_yet));
        h.b.unreadDot.setVisibility(unread ? View.VISIBLE : View.INVISIBLE);
        boolean linked = conv.announcementTitle != null;
        h.b.txtLinked.setText(linked ? conv.announcementTitle : "");
        h.b.txtLinked.setVisibility(linked ? View.VISIBLE : View.GONE);

        // Une seule annonce vocale claire pour la ligne entière.
        StringBuilder cd = new StringBuilder(conv.other.fullName).append(". ");
        if (unread) cd.append(c.getString(R.string.cd_unread)).append(". ");
        cd.append(h.b.txtPreview.getText());
        if (linked) cd.append(". ").append(conv.announcementTitle);
        h.b.getRoot().setContentDescription(cd);
        h.b.getRoot().setOnClickListener(v -> onClick.onConversation(conv));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemConversationBinding b;

        Holder(ItemConversationBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
