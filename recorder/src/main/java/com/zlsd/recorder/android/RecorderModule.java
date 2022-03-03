package com.zlsd.recorder.android;

import com.alibaba.fastjson.JSONObject;

import java.io.IOException;

import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.common.UniDestroyableModule;

/**
 * @Description
 * @Author Naruto Yang
 * @CreateDate 2021/11/24 0024
 * @Note
 */
public class RecorderModule extends UniDestroyableModule {
    @UniJSMethod(uiThread = false)
    public boolean start(JSONObject options, UniJSCallback jsCallback) {
        int frameSize = options.getIntValue("frameSize");
        return RecorderUtil.start(mUniSDKInstance.getContext(), new RecorderUtil.Listener(frameSize) {
            @Override
            protected boolean onStart() {
                return super.onStart();
            }

            @Override
            protected void onDataRead(byte[] audioData, int readSize) throws IOException {
                JSONObject result = new JSONObject();
                result.put("data", audioData);
                result.put("size", readSize);
                jsCallback.invokeAndKeepAlive(result);
            }

            @Override
            protected void onError(Throwable throwable) {
                JSONObject result = new JSONObject();
                result.put("error", throwable.getMessage());
                jsCallback.invoke(result);
            }
        });
    }

    @UniJSMethod(uiThread = false)
    public void stop() {
        RecorderUtil.stop();
    }

    @Override
    public void destroy() {
        stop();
    }
}
