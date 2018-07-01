package com.robot.tuling.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;

import com.baidu.android.voicedemo.activity.ActivityOnline;
import com.baidu.android.voicedemo.activity.ActivityRecog;
import com.baidu.android.voicedemo.activity.ActivityUiDialog;
import com.baidu.android.voicedemo.activity.mini.ActivityMiniRecog;
import com.baidu.android.voicedemo.control.MyRecognizer;
import com.baidu.android.voicedemo.recognization.ChainRecogListener;
import com.baidu.android.voicedemo.recognization.CommonRecogParams;
import com.baidu.android.voicedemo.recognization.MessageStatusRecogListener;
import com.baidu.android.voicedemo.recognization.RecogResult;
import com.baidu.android.voicedemo.recognization.StatusRecogListener;
import com.baidu.android.voicedemo.recognization.nlu.NluRecogParams;
import com.baidu.android.voicedemo.recognization.offline.OfflineRecogParams;
import com.baidu.android.voicedemo.recognization.online.OnlineRecogParams;
import com.baidu.android.voicedemo.util.AutoCheck;
import com.baidu.speech.EventListener;
import com.baidu.speech.EventManager;
import com.baidu.speech.asr.SpeechConstant;
import com.baidu.voicerecognition.android.ui.BaiduASRDigitalDialog;
import com.baidu.voicerecognition.android.ui.DigitalDialogInput;
import com.baidu.voicerecognition.android.ui.SimpleTransApplication;
import com.robot.tuling.R;
import com.robot.tuling.adapter.ChatMessageAdapter;
import com.robot.tuling.constant.TulingParams;
import com.robot.tuling.control.NavigateManager;
import com.robot.tuling.control.RetrofitApi;
import com.robot.tuling.entity.MessageEntity;
import com.robot.tuling.util.IsNullOrEmpty;
import com.robot.tuling.util.KeyBoardUtil;
import com.robot.tuling.util.TimeUtil;
import com.sunfusheng.FirUpdater;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

import static com.baidu.android.voicedemo.recognization.IStatus.STATUS_FINISHED;
import static com.baidu.android.voicedemo.recognization.IStatus.STATUS_NONE;
import static com.baidu.android.voicedemo.recognization.IStatus.STATUS_READY;
import static com.baidu.android.voicedemo.recognization.IStatus.STATUS_RECOGNITION;
import static com.baidu.android.voicedemo.recognization.IStatus.STATUS_SPEAKING;

public class MainActivity extends BaseActivity {

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.lv_message)
    ListView lvMessage;
    @BindView(R.id.iv_send_msg)
    ImageView ivSendMsg;
    @BindView(R.id.et_msg)
    EditText etMsg;
    @BindView(R.id.rl_msg)
    RelativeLayout rlMsg;

    private List<MessageEntity> msgList = new ArrayList<>();
    private ChatMessageAdapter msgAdapter;

    //speech
    ImageView ivSpeech;
    protected MyRecognizer myRecognizer;
    protected Handler handler;
    /*
     * Api的参数类，仅仅用于生成调用START的json字符串，本身与SDK的调用无关
     */
    protected CommonRecogParams apiParams;

    protected void initRecog() {
        if (handler == null) {
            handler = new Handler() {

                /*
                 * @param msg
                 */
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    handleMsg(msg);
                }

            };
            StatusRecogListener listener = new MessageStatusRecogListener(handler);
            myRecognizer = new MyRecognizer(this, listener);
            apiParams = getApiParams();
        }
    }

    protected CommonRecogParams getApiParams() {
        return new NluRecogParams(this);
    }

    protected void handleMsg(Message msg) {
        switch (msg.what) { // 处理MessageStatusRecogListener中的状态回调
            case STATUS_FINISHED:
                if (msg.arg2 == 1) {
                    etMsg.setText(msg.obj.toString());
                    sendMessage();
                }
                ivSpeech.setImageDrawable(getResources().getDrawable(R.drawable.speech_end));
                break;
            case STATUS_NONE:
            case STATUS_READY:
            case STATUS_SPEAKING:
            case STATUS_RECOGNITION:
                break;
            default:
                break;

        }
    }

    protected void startRecord() {
        initRecog();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        //  上面的获取是为了生成下面的Map， 自己集成时可以忽略
        // / 集成时不需要上面的代码，只需要params参数。
        final Map<String, Object> params = apiParams.fetch(sp);

        // 这里打印出params， 填写至您自己的app中，直接调用下面这行代码即可。
        myRecognizer.start(params);
        ivSpeech.setImageDrawable(getResources().getDrawable(R.drawable.speech_start));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        new FirUpdater(this, "3c57fb226edf7facf821501e4eba08d2", "5704953c00fc74127000000a").checkVersion();

        ivSpeech = (ImageView) findViewById(R.id.iv_speech);
        ivSpeech.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Intent i = new Intent(view.getContext(), ActivityUiDialog.class);
                //Intent i = new Intent(view.getContext(), ActivityOnline.class);
                //startActivity(i);
                startRecord();//开始语音识别
            }
        });

        initData();
        initView();
        initListener();
    }

    private void initData() {
        if (msgList.size() == 0) {
            MessageEntity entity = new MessageEntity(ChatMessageAdapter.TYPE_LEFT, TimeUtil.getCurrentTimeMillis());
            entity.setText("你好！我是AI助手！\n你有什么要问的么？");
            msgList.add(entity);
        }
        msgAdapter = new ChatMessageAdapter(this, msgList);
        lvMessage.setAdapter(msgAdapter);
        lvMessage.setSelection(msgAdapter.getCount());
    }

    private void initView() {
        toolbar.setTitle(getString(R.string.app_name));
        setSupportActionBar(toolbar);
    }

    private void initListener() {
        ivSendMsg.setOnClickListener(v -> sendMessage());

        lvMessage.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                KeyBoardUtil.hideKeyboard(mActivity);
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case R.id.action_about:
                NavigateManager.gotoAboutActivity(mContext);
                return true;
            default:
                return false;
        }
    }

    // 给Turing发送问题
    public void sendMessage() {
        String msg = etMsg.getText().toString().trim();

        if (!IsNullOrEmpty.isEmpty(msg)) {
            MessageEntity entity = new MessageEntity(ChatMessageAdapter.TYPE_RIGHT, TimeUtil.getCurrentTimeMillis(), msg);
            msgList.add(entity);
            msgAdapter.notifyDataSetChanged();
            etMsg.setText("");

            // 仅使用 Retrofit 请求接口
//            requestApiByRetrofit(msg);

            // 使用 Retrofit 和 RxJava 请求接口
            requestApiByRetrofit_RxJava(msg);
        }
    }

    // 请求图灵API接口，获得问答信息
    private void requestApiByRetrofit(String info) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(TulingParams.TULING_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        RetrofitApi api = retrofit.create(RetrofitApi.class);

        Call<MessageEntity> call = api.getTuringInfo(TulingParams.TULING_KEY, info);
        call.enqueue(new Callback<MessageEntity>() {
            @Override
            public void onResponse(Call<MessageEntity> call, Response<MessageEntity> response) {
                handleResponseMessage(response.body());
            }

            @Override
            public void onFailure(Call<MessageEntity> call, Throwable t) {

            }
        });
    }

    // 请求图灵API接口，获得问答信息
    private void requestApiByRetrofit_RxJava(String info) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(TulingParams.TULING_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();

        RetrofitApi api = retrofit.create(RetrofitApi.class);

        api.getTuringInfoByRxJava(TulingParams.TULING_KEY, info)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::handleResponseMessage, Throwable::printStackTrace);
    }

    // 处理获得到的问答信息
    private void handleResponseMessage(MessageEntity entity) {
        if (entity == null) return;

        entity.setTime(TimeUtil.getCurrentTimeMillis());
        entity.setType(ChatMessageAdapter.TYPE_LEFT);

        switch (entity.getCode()) {
            case TulingParams.TulingCode.URL:
                entity.setText(entity.getText() + "，点击网址查看：" + entity.getUrl());
                break;
            case TulingParams.TulingCode.NEWS:
                entity.setText(entity.getText() + "，点击查看");
                break;
        }

        msgList.add(entity);
        msgAdapter.notifyDataSetChanged();
    }

}
