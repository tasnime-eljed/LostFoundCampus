package com.example.lostfoundcampus.ui.messages;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.lostfoundcampus.R;
import com.example.lostfoundcampus.data.MessageRepository;
import com.example.lostfoundcampus.data.ResultCallback;
import com.example.lostfoundcampus.data.model.ConversationSummary;
import com.example.lostfoundcampus.databinding.FragmentMessagesBinding;
import com.example.lostfoundcampus.ui.common.Nav;
import com.example.lostfoundcampus.util.InsetsUtil;
import com.example.lostfoundcampus.util.Poller;

import java.util.List;

/** Liste des conversations, actualisée par polling (toutes les 15 s écran visible) et filtrable. */
public class MessagesFragment extends Fragment {

    private static final long SEARCH_DEBOUNCE_MS = 400;

    private FragmentMessagesBinding b;
    private ConversationAdapter adapter;
    private Poller poller;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean loadedOnce;
    private int requestId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        poller = new Poller(getResources().getInteger(R.integer.lf_poll_list_ms),
                getResources().getInteger(R.integer.lf_poll_max_ms), this::refresh);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentMessagesBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        InsetsUtil.applyTop(b.top);
        loadedOnce = false;
        adapter = new ConversationAdapter(c -> Nav.go(this, R.id.chatFragment,
                ChatFragment.args(c.id, c.other.uid, c.announcementId)));
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        b.list.setLayoutManager(lm);
        b.list.addItemDecoration(new DividerItemDecoration(requireContext(), lm.getOrientation()));
        b.list.setAdapter(adapter);
        b.list.setVisibility(View.GONE);
        b.empty.getRoot().setVisibility(View.GONE);
        b.etSearch.addTextChangedListener(new Nav.AfterTextChanged(s -> {
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(poller::now, SEARCH_DEBOUNCE_MS);
        }));
    }

    private void refresh(Poller p) {
        Poller.Done done = p.token();
        if (b == null) {
            done.done(true);
            return;
        }
        final String query = Nav.text(b.etSearch);
        final int myRequest = ++requestId;
        MessageRepository.get(requireContext()).myConversations(query, new ResultCallback<List<ConversationSummary>>() {
            @Override
            public void onSuccess(List<ConversationSummary> data) {
                done.done(true);
                if (b == null || myRequest != requestId) return;
                loadedOnce = true;
                adapter.submit(data);
                render(data.isEmpty(), !query.isEmpty(), null);
            }

            @Override
            public void onError(String message) {
                done.done(false);
                if (b == null || myRequest != requestId) return;
                if (!loadedOnce) render(true, false, message);
            }
        });
    }

    private void render(boolean empty, boolean searching, @Nullable String error) {
        b.list.setVisibility(empty ? View.GONE : View.VISIBLE);
        b.empty.getRoot().setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) return;
        b.empty.btnEmptyAction.setVisibility(View.VISIBLE);
        if (error != null) {
            b.empty.imgEmpty.setImageResource(R.drawable.ic_map_off);
            b.empty.txtEmptyTitle.setText(R.string.state_error_title);
            b.empty.txtEmptyBody.setText(error);
            b.empty.btnEmptyAction.setText(R.string.action_retry);
            b.empty.btnEmptyAction.setOnClickListener(v -> poller.now());
            return;
        }
        b.empty.imgEmpty.setImageResource(searching ? R.drawable.ic_search_off : R.drawable.ic_chat);
        b.empty.txtEmptyTitle.setText(searching ? R.string.home_empty_title : R.string.messages_empty_title);
        b.empty.txtEmptyBody.setText(R.string.messages_empty_body);
        b.empty.btnEmptyAction.setText(R.string.messages_empty_action);
        b.empty.btnEmptyAction.setOnClickListener(v -> Nav.main(this).selectTab(R.id.homeFragment));
    }

    @Override
    public void onResume() {
        super.onResume();
        poller.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        poller.stop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        poller.stop();
        handler.removeCallbacksAndMessages(null);
        b = null;
    }
}
