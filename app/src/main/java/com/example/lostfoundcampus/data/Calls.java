package com.example.lostfoundcampus.data;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.lostfoundcampus.data.remote.ApiErrors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Adapte un appel Retrofit vers un ResultCallback (succès transformé, erreurs en français). */
public final class Calls {

    public interface Mapper<A, B> {
        B map(A value);
    }

    private Calls() {
    }

    public static <A, B> void enqueue(Context context, Call<A> call, Mapper<A, B> mapper, ResultCallback<B> cb) {
        final Context app = context.getApplicationContext();
        call.enqueue(new Callback<A>() {
            @Override
            public void onResponse(@NonNull Call<A> c, @NonNull Response<A> response) {
                if (!response.isSuccessful()) {
                    cb.onError(ApiErrors.fromResponse(app, response));
                    return;
                }
                B mapped;
                try {
                    mapped = mapper.map(response.body());
                } catch (RuntimeException e) {
                    cb.onError(ApiErrors.fromThrowable(app, e));
                    return;
                }
                cb.onSuccess(mapped);
            }

            @Override
            public void onFailure(@NonNull Call<A> c, @NonNull Throwable t) {
                if (c.isCanceled()) return;
                cb.onError(ApiErrors.fromThrowable(app, t));
            }
        });
    }

    public static <A> void enqueue(Context context, Call<A> call, ResultCallback<A> cb) {
        enqueue(context, call, value -> value, cb);
    }
}
