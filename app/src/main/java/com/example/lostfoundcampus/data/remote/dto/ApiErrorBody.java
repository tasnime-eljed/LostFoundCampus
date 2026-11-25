package com.example.lostfoundcampus.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Corps d'erreur commun à GoTrue (Auth), PostgREST et Storage : tous les champs sont facultatifs. */
public class ApiErrorBody {
    @SerializedName("error_code")
    public String errorCode;
    @SerializedName("code")
    public Object code;
    @SerializedName("msg")
    public String msg;
    @SerializedName("message")
    public String message;
    @SerializedName("error")
    public String error;
    @SerializedName("error_description")
    public String errorDescription;

    public String text() {
        StringBuilder sb = new StringBuilder();
        for (String s : new String[]{errorCode, code == null ? null : String.valueOf(code), msg, message, error, errorDescription}) {
            if (s != null) sb.append(s).append(' ');
        }
        return sb.toString().toLowerCase(java.util.Locale.ROOT);
    }
}
