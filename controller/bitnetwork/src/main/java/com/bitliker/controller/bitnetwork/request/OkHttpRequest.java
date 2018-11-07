package com.bitliker.controller.bitnetwork.request;


import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.SparseArray;

import com.bitliker.controller.bitnetwork.HttpClient;
import com.bitliker.controller.bitnetwork.response.OnHttpCallback;
import com.bitliker.controller.bitnetwork.response.Tags;
import com.bitliker.controller.bitnetwork.ssl.DefaultSSLConfig;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

/**
 * Created by Bitliker on 2017/7/3.
 */

public class OkHttpRequest extends HttpRequest<Call> {
    private OkHttpClient okHttpClient;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public OkHttpRequest() {
        super();
    }

    @Override
    protected HttpRequest init() {
        if (sslConfig == null) sslConfig = new DefaultSSLConfig();
        OkHttpClient.Builder builder = new OkHttpClient().newBuilder()
                .connectTimeout(readTimeout, TimeUnit.SECONDS)// 设置超时时间
                .readTimeout(writeTimeout, TimeUnit.SECONDS)// 设置读取超时时间
                .writeTimeout(connectTimeout, TimeUnit.SECONDS)// 设置写入超时时间
                .retryOnConnectionFailure(maxRetryCount > 0)
                .sslSocketFactory(sslConfig.createSSLSocketFactory(),
                        sslConfig.createTrustAllCerts())
                .hostnameVerifier(sslConfig.createHostnameVerifier());
        if (chcheDirectory != null)
            builder.cache(new Cache(chcheDirectory, chcheMaxSize));
        if (interceptors != null && interceptors.size() > 0) {
            for (Interceptor i : interceptors)
                builder.addInterceptor(i);
        }
        okHttpClient = builder.build();
        return this;
    }

    @Override
    public boolean cancelRequest(int what, SparseArray<Call> calls) {
        if (calls != null) {
            Call call = calls.get(what);
            if (call != null && call.isExecuted()) {
                try {
                    call.cancel();
                    return true;
                } catch (Exception e) {
                    return false;
                }
            } else {
                return true;
            }
        }
        return false;
    }

    private void postRequest(HttpClient.Builder mBuilder, final OnHttpCallback callback) throws Exception {
        Request.Builder builder = new Request.Builder();
        builder.url(mergeUrl(mBuilder.getUrl()));
        RequestBody body = getBody(mBuilder);
        if (mBuilder.getMode() == HttpClient.POST_JSON) {
            builder.post(RequestBody.create(MediaType.parse(mBuilder.getMediaType()), bodyToString(body)));
        } else {
            builder.post(body);
        }
        Headers2Builder(builder, mBuilder);
        enqueue(mBuilder, builder, callback);
    }

    private void getRequest(HttpClient.Builder httpBuilder, final OnHttpCallback callback) throws Exception {
        Request.Builder builder = new Request.Builder().url(mergeUrl(httpBuilder.getUrl()));
        Headers2Builder(builder, httpBuilder);
        enqueue(httpBuilder, builder, callback);
    }

    private void enqueue(HttpClient.Builder mBuilder, Request.Builder builder, final OnHttpCallback callback) {
        Call call = okHttpClient.newCall(builder.build());
        final Tags tags = mBuilder.getTags();
        final int what = tags.getWhat();
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onFailure(what, e.getMessage(), tags);
                    }
                });
            }

            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            callback.onSuccess(what, response.body().string(), tags);
                        } catch (IOException e) {
                            callback.onFailure(what, e.getMessage(), tags);
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
        addRequest(what, call);
    }

    private RequestBody getBody(HttpClient.Builder httpBuilder) {
        if (httpBuilder.getMode() == HttpClient.POST) {
            FormBody.Builder bodyBuilder = new FormBody.Builder();
            if (!httpBuilder.getParams().isEmpty()) {
                for (Map.Entry<String, Object> e : httpBuilder.getParams().entrySet()) {
                    if (e.getValue() == null || TextUtils.isEmpty(e.getKey()))
                        continue;
                    bodyBuilder.add(e.getKey(), e.getValue().toString());
                }
            }
            return bodyBuilder.build();
        } else {
            return new FormBody.Builder().build();
        }
    }

    private void Headers2Builder(Request.Builder builder, HttpClient.Builder mBuilder) {
        if (!mBuilder.getHeaders().isEmpty()) {
            for (Map.Entry<String, String> e : mBuilder.getHeaders().entrySet()) {
                if (e.getValue() == null || TextUtils.isEmpty(e.getKey()))
                    continue;
                builder.addHeader(e.getKey(), e.getValue());
            }
        }
        builder.addHeader("Content-Type", mBuilder.getMediaType());
    }

    public static String bodyToString(final RequestBody request) {
        try {
            final RequestBody copy = request;
            final Buffer buffer = new Buffer();
            if (copy != null)
                copy.writeTo(buffer);
            else
                return "";
            return buffer.readUtf8();
        } catch (final IOException e) {
            return "did not work";
        }
    }


    @Override
    public void request(HttpClient.Builder httpBuilder, OnHttpCallback onHttpCallback) {
        try {
            switch (httpBuilder.getMode()) {
                case HttpClient.POST:
                case HttpClient.POST_JSON:
                    postRequest(httpBuilder, onHttpCallback);
                    break;
                case HttpClient.GET:
                default:
                    getRequest(httpBuilder, onHttpCallback);
            }
        } catch (Exception e) {
            if (onHttpCallback != null) {
                onHttpCallback.onFailure(httpBuilder.getTags().getWhat(), e == null ? "" : e.getMessage(), httpBuilder.getTags());
            }
        }
    }


}