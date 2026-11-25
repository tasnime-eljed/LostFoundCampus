package com.example.lostfoundcampus.data.remote;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

/** Supabase Storage : SUPABASE_URL/storage/v1/ (buckets privés). */
public interface StorageApi {

    @POST("object/{bucket}/{path}")
    Call<ResponseBody> upload(@Path("bucket") String bucket,
                              @Path(value = "path", encoded = true) String path,
                              @Header("x-upsert") String upsert,
                              @Body RequestBody body);

    @DELETE("object/{bucket}/{path}")
    Call<ResponseBody> delete(@Path("bucket") String bucket,
                              @Path(value = "path", encoded = true) String path);
}
