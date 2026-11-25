package com.example.lostfoundcampus.ui.messages;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.AnnouncementRepository;
import com.example.lostfoundcampus.data.AuthRepository;
import com.example.lostfoundcampus.data.MessageRepository;
import com.example.lostfoundcampus.data.ProfileRepository;
import com.example.lostfoundcampus.data.ResultCallback;
import com.example.lostfoundcampus.data.model.Announcement;
import com.example.lostfoundcampus.data.model.Message;
import com.example.lostfoundcampus.data.model.User;
import com.example.lostfoundcampus.databinding.FragmentChatBinding;
import com.example.lostfoundcampus.ui.common.ImageLoader;
import com.example.lostfoundcampus.ui.common.Nav;
import com.example.lostfoundcampus.ui.common.UiUtils;
import com.example.lostfoundcampus.ui.detail.AnnouncementDetailFragment;
import com.example.lostfoundcampus.util.InsetsUtil;
import com.example.lostfoundcampus.util.Poller;

import java.util.ArrayList;
import java.util.List;

/**
 * Conversation liée à une annonce, mise à jour par polling (toutes les 4 s écran visible,
 * intervalle allongé en cas d'erreur réseau). Au début d'une conversation vide, un message
 * d'introduction est proposé en brouillon : il n'est jamais envoyé sans action de l'utilisateur.
 */
public class ChatFragment extends Fragment {

    private static final String ARG_ID = "conversation_id";
    private static final String ARG_OTHER = "other_user_id";
    private static final String ARG_ANNOUNCEMENT = "announcement_id";
    private static final String STATE_DRAFT_SHOWN = "draft_shown";

    private FragmentChatBinding b;
    private String conversationId;
    private String me;
    @Nullable
    private User other;
    @Nullable
    private Announcement linked;
    private final List<Message> messages = new ArrayList<>();
    private MessageAdapter adapter;
    private Poller poller;
    /** Plus grand identifiant serveur reçu : le polling ne demande que les messages suivants. */
    private long lastId;
    private boolean firstLoadDone;
    private boolean draftShown;
    private boolean fillingDraft;
    private boolean errorShown;
    /** Identifiants temporaires (négatifs) des messages en cours d'envoi. */
    private long nextLocalId = -1;

    public static Bundle args(String conversationId, @Nullable String otherUserId, @Nullable String announcementId) {
        Bundle args = new Bundle();
        args.putString(ARG_ID, conversationId);
        args.putString(ARG_OTHER, otherUserId);
        args.putString(ARG_ANNOUNCEMENT, announcementId);
        return args;
    }

    /**
     * « Contacter » : ouvre la conversation existante avec l'auteur de l'annonce ou la crée
     * (fonction SQL open_conversation, sans doublon), puis affiche l'écran de discussion.
     * @param trigger bouton désactivé pendant l'appel, pour éviter les doubles touchers.
     */
    public static void openWithAuthor(Fragment from, Announcement a, @Nullable View trigger) {
        if (trigger != null) trigger.setEnabled(false);
        MessageRepository.get(from.requireContext()).open(a.id, new ResultCallback<String>() {
            @Override
            public void onSuccess(String conversationId) {
                if (trigger != null) trigger.setEnabled(true);
                if (from.getView() == null || conversationId == null) return;
                Nav.go(from, R.id.chatFragment, args(conversationId, a.authorId, a.id));
            }

            @Override
            public void onError(String message) {
                if (trigger != null) trigger.setEnabled(true);
                if (from.getView() == null) return;
                Nav.main(from).showMessage(message);
            }
        });
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) draftShown = savedInstanceState.getBoolean(STATE_DRAFT_SHOWN);
        poller = new Poller(getResources().getInteger(R.integer.lf_poll_chat_ms),
                getResources().getInteger(R.integer.lf_poll_max_ms), this::poll);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentChatBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        InsetsUtil.applyTop(b.header);
        InsetsUtil.applyBottom(b.inputBar, true);
        b.btnBack.setOnClickListener(v -> Nav.back(this));

        Bundle args = getArguments();
        conversationId = args == null ? null : args.getString(ARG_ID);
        me = AuthRepository.get(requireContext()).currentUserId();
        if (conversationId == null || me == null) {
            Nav.of(this).popBackStack();
            return;
        }

        // L'écran est recréé (rotation, retour) : on repart de zéro, le polling recharge tout.
        messages.clear();
        lastId = 0;
        firstLoadDone = false;
        adapter = new MessageAdapter(messages, me, this::retry);
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        b.list.setLayoutManager(lm);
        b.list.setAdapter(adapter);
        b.txtEmpty.setVisibility(View.GONE);
        b.cardLinked.setVisibility(View.GONE);

        String otherId = args.getString(ARG_OTHER);
        if (otherId != null) loadOther(otherId);
        String announcementId = args.getString(ARG_ANNOUNCEMENT);
        if (announcementId != null) loadLinked(announcementId);

        b.chipDraft.setCloseIconContentDescription(getString(R.string.cd_clear_draft));
        b.chipDraft.setOnCloseIconClickListener(v -> {
            b.etMessage.setText("");
            b.groupDraft.setVisibility(View.GONE);
        });
        b.etMessage.addTextChangedListener(new Nav.AfterTextChanged(s -> {
            if (fillingDraft) return;
            if (s.toString().trim().isEmpty()) b.groupDraft.setVisibility(View.GONE);
            updateSendButton();
        }));
        updateSendButton();
        b.btnSend.setOnClickListener(v -> send());
    }

    private void loadOther(String otherId) {
        User cached = ProfileRepository.get(requireContext()).cached(otherId);
        if (cached != null) bindOther(cached);
        ProfileRepository.get(requireContext()).loadUser(otherId, cached == null, new ResultCallback<User>() {
            @Override
            public void onSuccess(User user) {
                bindOther(user);
            }

            @Override
            public void onError(String message) {
                // L'en-tête reste vide ; la conversation fonctionne quand même.
            }
        });
    }

    private void bindOther(User user) {
        if (b == null) return;
        other = user;
        UiUtils.bindInitialsAvatar(b.txtAvatar, user);
        b.txtName.setText(user.fullName);
        maybeShowDraft();
    }

    private void loadLinked(String announcementId) {
        AnnouncementRepository.get(requireContext()).get(announcementId, new ResultCallback<Announcement>() {
            @Override
            public void onSuccess(Announcement a) {
                if (b == null) return;
                linked = a;
                b.cardLinked.setVisibility(View.VISIBLE);
                ImageLoader.announcementPhoto(b.imgLinked, a.coverPath());
                b.txtLinkedLabel.setText(getString(R.string.chat_linked, getString(a.type.labelRes)));
                b.txtLinkedTitle.setText(a.title);
                b.cardLinked.setOnClickListener(v -> Nav.go(ChatFragment.this, R.id.announcementDetailFragment,
                        AnnouncementDetailFragment.args(a.id)));
                maybeShowDraft();
            }

            @Override
            public void onError(String message) {
                // Annonce supprimée ou archivée : la conversation reste accessible sans la carte.
            }
        });
    }

    // ------------------------------------------------------------------ Polling

    private void poll(Poller p) {
        Poller.Done done = p.token();
        MessageRepository.get(requireContext()).messagesAfter(conversationId, lastId, new ResultCallback<List<Message>>() {
            @Override
            public void onSuccess(List<Message> fresh) {
                done.done(true);
                if (b == null) return;
                errorShown = false;
                merge(fresh);
                if (!firstLoadDone) {
                    firstLoadDone = true;
                    renderEmpty();
                    maybeShowDraft();
                }
            }

            @Override
            public void onError(String message) {
                done.done(false);
                if (b == null) return;
                if (!errorShown) {
                    errorShown = true;
                    Nav.main(ChatFragment.this).showMessage(firstLoadDone ? message : getString(R.string.chat_load_error));
                }
            }
        });
    }

    /** Ajoute les nouveaux messages en ignorant ceux déjà affichés (envoyés depuis cet écran). */
    private void merge(List<Message> fresh) {
        boolean receivedNew = false;
        int before = messages.size();
        for (Message m : fresh) {
            lastId = Math.max(lastId, m.id);
            if (indexOfServerId(m.id) >= 0) continue;
            insertSorted(m);
            if (!me.equals(m.senderId)) receivedNew = true;
        }
        if (messages.size() != before) {
            adapter.notifyDataSetChanged();
            renderEmpty();
            scrollToEnd();
        }
        if (receivedNew || !firstLoadDone) MessageRepository.get(requireContext()).markRead(conversationId);
    }

    /** Les messages confirmés restent avant ceux en attente d'envoi. */
    private void insertSorted(Message m) {
        int i = messages.size();
        while (i > 0 && (messages.get(i - 1).id < 0 || messages.get(i - 1).id > m.id)) i--;
        messages.add(i, m);
    }

    private int indexOfServerId(long id) {
        for (int i = 0; i < messages.size(); i++) if (messages.get(i).id == id) return i;
        return -1;
    }

    // ------------------------------------------------------------------ Envoi

    private void updateSendButton() {
        if (b != null) b.btnSend.setEnabled(!Nav.text(b.etMessage).isEmpty());
    }

    private void send() {
        String text = Nav.text(b.etMessage);
        if (text.isEmpty()) return;
        Message pending = new Message(nextLocalId--, text, System.currentTimeMillis(), me, Message.State.SENDING, false);
        messages.add(pending);
        adapter.notifyItemInserted(messages.size() - 1);
        if (messages.size() > 1) adapter.notifyItemChanged(messages.size() - 2);
        b.etMessage.setText("");
        b.groupDraft.setVisibility(View.GONE);
        renderEmpty();
        scrollToEnd();
        deliver(pending);
    }

    private void retry(Message m) {
        m.state = Message.State.SENDING;
        notifyMessage(m);
        deliver(m);
    }

    private void deliver(Message pending) {
        MessageRepository.get(requireContext()).send(conversationId, pending.text, new ResultCallback<Message>() {
            @Override
            public void onSuccess(Message saved) {
                if (b == null) return;
                int index = messages.indexOf(pending);
                if (index < 0) return;
                if (indexOfServerId(saved.id) >= 0) {
                    // Le polling l'a déjà ramené entre-temps : on retire la copie locale.
                    messages.remove(index);
                    adapter.notifyDataSetChanged();
                    return;
                }
                pending.id = saved.id;
                pending.sentAt = saved.sentAt;
                pending.state = Message.State.SENT;
                messages.remove(index);
                insertSorted(pending);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String message) {
                if (b == null) return;
                pending.state = Message.State.FAILED;
                notifyMessage(pending);
                Nav.main(ChatFragment.this).showMessage(message);
            }
        });
    }

    private void notifyMessage(Message m) {
        int index = messages.indexOf(m);
        if (index >= 0) adapter.notifyItemChanged(index);
    }

    // ------------------------------------------------------------------ Affichage

    /** Brouillon local : seulement pour une conversation vide, une seule fois, une fois tout chargé. */
    private void maybeShowDraft() {
        if (b == null || draftShown || !firstLoadDone || !messages.isEmpty() || linked == null || other == null) return;
        if (!Nav.text(b.etMessage).isEmpty()) return;
        draftShown = true;
        fillingDraft = true;
        b.etMessage.setText(IntroMessageTemplate.build(requireContext(), linked, other));
        fillingDraft = false;
        b.groupDraft.setVisibility(View.VISIBLE);
        updateSendButton();
    }

    private void renderEmpty() {
        b.txtEmpty.setVisibility(firstLoadDone && messages.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void scrollToEnd() {
        if (messages.isEmpty()) return;
        b.list.post(() -> {
            if (b != null && !messages.isEmpty()) b.list.scrollToPosition(messages.size() - 1);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (b != null && conversationId != null) poller.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        poller.stop();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_DRAFT_SHOWN, draftShown);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        poller.stop();
        b = null;
    }
}
