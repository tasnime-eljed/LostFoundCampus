package com.example.lostfoundcampus.data;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.lostfoundcampus.data.model.Announcement;
import com.example.lostfoundcampus.data.model.AnnouncementType;
import com.example.lostfoundcampus.data.model.CampusLocation;
import com.example.lostfoundcampus.data.model.Category;
import com.example.lostfoundcampus.data.model.Match;
import com.example.lostfoundcampus.data.remote.SupabaseClient;
import com.example.lostfoundcampus.data.remote.dto.MatchDto;
import com.example.lostfoundcampus.data.remote.dto.PhotoAnalysisDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Annonces, analyses de photos et correspondances (tables Supabase + fonction match_candidates). */
public final class AnnouncementRepository {

    public enum ProfileFilter { ALL, LOST, FOUND, RETURNED }

    public static final int PAGE_SIZE = 20;

    private static AnnouncementRepository instance;
    private final Context app;
    private final SupabaseClient sb;

    private AnnouncementRepository(Context context) {
        app = context.getApplicationContext();
        sb = SupabaseClient.get();
    }

    public static synchronized AnnouncementRepository get(Context context) {
        if (instance == null) instance = new AnnouncementRepository(context);
        return instance;
    }

    /** Fil des annonces actives (RG06), paginé, filtré par type, catégorie et mot-clé. */
    public void feed(@Nullable AnnouncementType type, @Nullable Category category, @Nullable String query,
                     int offset, ResultCallback<List<Announcement>> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("select", "*");
        q.put("status", "eq.ACTIVE");
        if (type != null) q.put("type", "eq." + type.name());
        if (category != null) q.put("category", "eq." + category.name());
        String term = sanitize(query);
        if (!term.isEmpty()) {
            q.put("or", "(title.ilike.*" + term + "*,description.ilike.*" + term
                    + "*,location_precision.ilike.*" + term + "*)");
        }
        q.put("order", "created_at.desc,id.desc");   // id : ordre stable entre les pages
        q.put("limit", String.valueOf(PAGE_SIZE));
        q.put("offset", String.valueOf(offset));
        Calls.enqueue(app, sb.rest.announcements(q), Mappers::announcements, cb);
    }

    public void get(String id, ResultCallback<Announcement> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("select", "*");
        q.put("id", "eq." + id);
        Calls.enqueue(app, sb.rest.announcements(q), list -> {
            List<Announcement> out = Mappers.announcements(list);
            if (out.isEmpty()) throw new IllegalStateException("Annonce introuvable");
            return out.get(0);
        }, cb);
    }

    public void byIds(List<String> ids, ResultCallback<Map<String, Announcement>> cb) {
        if (ids.isEmpty()) {
            cb.onSuccess(new HashMap<>());
            return;
        }
        Map<String, String> q = new HashMap<>();
        q.put("select", "*");
        q.put("id", "in.(" + TextUtils.join(",", ids) + ")");
        Calls.enqueue(app, sb.rest.announcements(q), list -> {
            Map<String, Announcement> out = new HashMap<>();
            for (Announcement a : Mappers.announcements(list)) out.put(a.id, a);
            return out;
        }, cb);
    }

    public void mine(ProfileFilter filter, ResultCallback<List<Announcement>> cb) {
        Map<String, String> q = new HashMap<>();
        q.put("select", "*");
        q.put("author_id", "eq." + sb.session.userId());
        switch (filter) {
            case LOST:
                q.put("type", "eq.LOST");
                q.put("status", "eq.ACTIVE");
                break;
            case FOUND:
                q.put("type", "eq.FOUND");
                q.put("status", "eq.ACTIVE");
                break;
            case RETURNED:
                q.put("status", "eq.RETURNED");
                break;
            default:
                q.put("status", "neq.ARCHIVED");
                break;
        }
        q.put("order", "created_at.desc");
        Calls.enqueue(app, sb.rest.announcements(q), Mappers::announcements, cb);
    }

    public void insert(Map<String, Object> body, ResultCallback<Announcement> cb) {
        Calls.enqueue(app, sb.rest.insertAnnouncement(body), list -> firstOf(Mappers.announcements(list)), cb);
    }

    public void update(String id, Map<String, Object> body, ResultCallback<Announcement> cb) {
        Calls.enqueue(app, sb.rest.updateAnnouncement("eq." + id, body), list -> firstOf(Mappers.announcements(list)), cb);
    }

    public void markReturned(String id, ResultCallback<Announcement> cb) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "RETURNED");
        update(id, body, cb);
    }

    public void updateLocation(String id, CampusLocation location, ResultCallback<Announcement> cb) {
        Map<String, Object> body = new HashMap<>();
        body.put("latitude", location.latitude);
        body.put("longitude", location.longitude);
        body.put("location_precision", location.hasPrecision() ? location.precision.trim() : null);
        update(id, body, cb);
    }

    public void analyses(String announcementId, ResultCallback<List<PhotoAnalysisDto>> cb) {
        Calls.enqueue(app, sb.rest.analyses("eq." + announcementId, "photo_index.asc"),
                list -> list == null ? new ArrayList<PhotoAnalysisDto>() : list, cb);
    }

    /** Remplace les analyses d'une annonce (suppression puis insertion). */
    public void replaceAnalyses(String announcementId, List<Map<String, Object>> rows, ResultCallback<Void> cb) {
        Calls.enqueue(app, sb.rest.deleteAnalyses("eq." + announcementId), v -> null, new ResultCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                if (rows.isEmpty()) {
                    cb.onSuccess(null);
                } else {
                    Calls.enqueue(app, sb.rest.insertAnalyses(rows), v -> null, cb);
                }
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    /** Correspondances calculées par la base (RG04, RG05), au-dessus du seuil. */
    public void matches(String announcementId, int threshold, ResultCallback<List<Match>> cb) {
        Map<String, Object> body = new HashMap<>();
        body.put("p_announcement", announcementId);
        body.put("p_threshold", threshold);
        Calls.enqueue(app, sb.rest.matchCandidates(body), list -> {
            List<Match> out = new ArrayList<>();
            if (list != null) for (MatchDto d : list) out.add(new Match(d.announcementId, d.score));
            return out;
        }, cb);
    }

    private static Announcement firstOf(List<Announcement> list) {
        if (list.isEmpty()) throw new IllegalStateException("Aucune ligne renvoyée");
        return list.get(0);
    }

    /** Retire les caractères qui ont un sens dans la syntaxe de filtre PostgREST. */
    static String sanitize(@Nullable String query) {
        if (query == null) return "";
        return query.replaceAll("[,()*%:\"\\\\.]", " ").trim().replaceAll("\\s+", " ");
    }
}
