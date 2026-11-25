package com.example.lostfoundcampus.data.remote;

import com.example.lostfoundcampus.data.remote.dto.AnnouncementDto;
import com.example.lostfoundcampus.data.remote.dto.ConversationRowDto;
import com.example.lostfoundcampus.data.remote.dto.MatchDto;
import com.example.lostfoundcampus.data.remote.dto.MessageDto;
import com.example.lostfoundcampus.data.remote.dto.PhotoAnalysisDto;
import com.example.lostfoundcampus.data.remote.dto.ProfileDto;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

/** PostgREST : SUPABASE_URL/rest/v1/ (les règles RLS s'appliquent avec le jeton de l'utilisateur). */
public interface RestApi {

    // ---------- Annonces
    @GET("announcements")
    Call<List<AnnouncementDto>> announcements(@QueryMap Map<String, String> query);

    /** Upsert sur l'id choisi par l'app : renvoyer la même annonce (réseau coupé) ne crée pas de doublon. */
    @Headers("Prefer: return=representation,resolution=merge-duplicates")
    @POST("announcements")
    Call<List<AnnouncementDto>> insertAnnouncement(@Body Map<String, Object> body);

    @Headers("Prefer: return=representation")
    @PATCH("announcements")
    Call<List<AnnouncementDto>> updateAnnouncement(@Query("id") String idFilter, @Body Map<String, Object> body);

    // ---------- Analyses de photos
    @GET("photo_analyses")
    Call<List<PhotoAnalysisDto>> analyses(@Query("announcement_id") String filter, @Query("order") String order);

    @POST("photo_analyses")
    Call<Void> insertAnalyses(@Body List<Map<String, Object>> rows);

    @DELETE("photo_analyses")
    Call<Void> deleteAnalyses(@Query("announcement_id") String filter);

    // ---------- Profils
    @GET("profiles")
    Call<List<ProfileDto>> profiles(@Query("id") String idFilter);

    @Headers("Prefer: return=representation")
    @PATCH("profiles")
    Call<List<ProfileDto>> updateProfile(@Query("id") String idFilter, @Body Map<String, Object> body);

    // ---------- Messagerie
    @POST("rpc/open_conversation")
    Call<String> openConversation(@Body Map<String, Object> body);

    @POST("rpc/my_conversations")
    Call<List<ConversationRowDto>> myConversations(@Body Map<String, Object> body);

    @POST("rpc/mark_conversation_read")
    Call<Void> markConversationRead(@Body Map<String, Object> body);

    @GET("messages")
    Call<List<MessageDto>> messages(@QueryMap Map<String, String> query);

    @Headers("Prefer: return=representation")
    @POST("messages")
    Call<List<MessageDto>> sendMessage(@Body Map<String, Object> body);

    // ---------- Correspondances
    @POST("rpc/match_candidates")
    Call<List<MatchDto>> matchCandidates(@Body Map<String, Object> body);
}
