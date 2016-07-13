package com.nextapp.tuyatest.test.presenter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Toast;

import com.alibaba.fastjson.JSONObject;
import com.nextapp.tuyatest.R;
import com.nextapp.tuyatest.test.activity.EditDpTestActivity;
import com.nextapp.tuyatest.test.event.DpSendDataEvent;
import com.nextapp.tuyatest.test.event.DpSendDataModel;
import com.nextapp.tuyatest.test.model.DeviceTestModel;
import com.nextapp.tuyatest.test.model.IDeviceTestModel;
import com.nextapp.tuyatest.test.view.IDeviceTestView;
import com.tuya.smart.android.base.TuyaSmartSdk;
import com.tuya.smart.android.device.TuyaSmartPanel;
import com.tuya.smart.android.device.api.IDevicePanelCallback;
import com.tuya.smart.android.device.utils.PreferencesUtil;
import com.tuya.smart.android.hardware.model.IControlCallback;
import com.tuya.smart.android.mvp.presenter.BasePresenter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by letian on 16/7/11.
 */
public class DeviceTestPresenter extends BasePresenter implements DpSendDataEvent {

    public static final String INTENT_DEVICE_ID = "intent_device_id";
    private static final String TAG = "DeviceTestPresenter ggg";
    private final Context mContext;
    private final IDeviceTestView mView;

    private IDeviceTestModel mModel;
    private List<SendAndBackData> mSendAndBackDataList;
    private String mDevId;
    private TuyaSmartPanel mPanel;
    private Thread mThread;
    private DpCountDownLatch mLatch;
    private boolean mStart;
    private boolean mStop;

    public DeviceTestPresenter(Context context, IDeviceTestView view) {
        mContext = context;
        mModel = new DeviceTestModel(context, mHandler);
        mSendAndBackDataList = new ArrayList<>();
        mView = view;
        initIntentData();
//        initTestData();
        initDevicePanel();
        initEventBus();
    }

    private void initEventBus() {
        TuyaSmartSdk.getEventBus().register(this);
    }

    private void initDevicePanel() {
        mPanel = new TuyaSmartPanel(mDevId, mDevId, new IDevicePanelCallback() {
            @Override
            public void onDpUpdate(String deviceId, String value) {
                mView.log("onDpUpdate: " + value);
                JSONObject jsonObject = mModel.getDpValueWithOutROMode(mDevId, value);
                if (mLatch != null && mLatch.getCount() > 0 && !jsonObject.isEmpty()) {
                    mLatch.setReturnValue(jsonObject.toJSONString());
                    mLatch.setStatus(DpCountDownLatch.STATUS_SUCCESS);
                    mLatch.countDown();
                }
            }

            @Override
            public void onRemoved() {

            }

            @Override
            public void onStatusChanged(boolean online) {

            }

            @Override
            public void onNetworkStatusChanged(boolean status) {

            }

            @Override
            public void onGWRelationUpdate() {

            }

            @Override
            public void onDevInfoUpdate(String deviceId) {

            }
        });
    }

    private void initIntentData() {
        mDevId = ((Activity) mContext).getIntent().getStringExtra(INTENT_DEVICE_ID);
    }

    public void initTestData() {
        mSendAndBackDataList.addAll(mModel.getSendAndBackDpData());
    }

    public void startTest() {
        String string = PreferencesUtil.getString(mDevId);
        if (TextUtils.isEmpty(string)) {
            Toast.makeText(mContext, mContext.getString(R.string.please_input_dp_value), Toast.LENGTH_SHORT).show();
            return;
        }
        mSendAndBackDataList.clear();
        mSendAndBackDataList.addAll(JSONObject.parseArray(string, SendAndBackData.class));
        if (mSendAndBackDataList.size() == 0) {
            Toast.makeText(mContext, mContext.getString(R.string.please_input_dp_value), Toast.LENGTH_SHORT).show();
            return;
        }
        if (mStart) return;

        mStop = false;
        mStart = true;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                doQueue();
            }
        });
        mThread.start();
    }


    private void doQueue() {
        for (SendAndBackData sendAndBackData : mSendAndBackDataList) {
            if (mStop) return;
            mView.log("\n");
            mView.log("send value: " + JSONObject.toJSONString(sendAndBackData.getSendValue()));
            mLatch = new DpCountDownLatch(1);
            sendCommand(sendAndBackData.getSendValue());
            try {
                mLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (mStop) return;
            if (mLatch.getStatus() == DpCountDownLatch.STATUS_SUCCESS) {
                HashMap<String, Object> backValue = sendAndBackData.getBackValue();
                if (backValue == null) {
                    backValue = sendAndBackData.getSendValue();
                }
                if (mModel.checkValue(backValue, mLatch.getReturnValue())) {
                    mView.log("return value is right");
                } else {
                    mView.log("return error value: " + mLatch.getReturnValue());
                }
            } else {
                mView.log("send Failure!!");
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (mStop) return;
        }
        mStart = false;
    }


    public void stopTest() {
        if (mStop) return;
        mStop = true;
        mStart = false;
        while (mLatch != null && mLatch.getCount() > 0) {
            mLatch.countDown();
        }
        if (mThread != null) {
            mThread.interrupt();
        }
    }

    private void sendCommand(HashMap<String, Object> command) {
        String commandStr = JSONObject.toJSONString(command);
        mPanel.send(commandStr, new IControlCallback() {
            @Override
            public void onError(String code, String error) {
                mLatch.setStatus(DpCountDownLatch.STATUS_ERROR);
                mLatch.countDown();
            }

            @Override
            public void onSuccess() {

            }
        });
    }

    public void editTestValue() {
        Intent intent = new Intent(mContext, EditDpTestActivity.class);
        intent.putExtra(DeviceTestPresenter.INTENT_DEVICE_ID, mDevId);
        mContext.startActivity(intent);
    }

    @Override
    public void onEvent(DpSendDataModel model) {
        PreferencesUtil.set(mDevId, JSONObject.toJSONString(model.getData()));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        TuyaSmartSdk.getEventBus().unregister(this);
    }
}