package com.yueyue.todolist.modules.main.ui;

import android.animation.ObjectAnimator;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;

import com.blankj.utilcode.util.SizeUtils;
import com.blankj.utilcode.util.Utils;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.yueyue.todolist.R;
import com.yueyue.todolist.base.RecyclerFragment;
import com.yueyue.todolist.common.C;
import com.yueyue.todolist.component.PreferencesManager;
import com.yueyue.todolist.component.RxBus;
import com.yueyue.todolist.event.MainTabsUpdateEvent;
import com.yueyue.todolist.modules.main.adapter.RvNoteListAdapter;
import com.yueyue.todolist.modules.main.db.NoteDbHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


/**
 * A placeholder fragment containing a simple view.
 */
public class MainTabsFragment extends RecyclerFragment {

    private static final String TAG = MainTabsFragment.class.getSimpleName();

    private RecyclerView mRecyclerView;
    private RvNoteListAdapter mAdapter;

    private List<Disposable> mDisposableList = new ArrayList<>();

    public static MainTabsFragment newInstance() {
        return new MainTabsFragment();
    }

    @Override
    protected void initViews() {
        super.initViews();
        mRecyclerView = getRecyclerView();
        initFab();
        initAdapter();
        registerMainTabsUpdateEvent();
    }

    private void registerMainTabsUpdateEvent() {
        Disposable disposable = RxBus.getDefault()
                .toObservable(MainTabsUpdateEvent.class)
                .doOnNext(event -> load())
                .subscribe();
        mDisposableList.add(disposable);
    }


    public void initAdapter() {
        int mode = PreferencesManager.getInstance().getNoteListShowMode(C.STYLE_LINEAR);
        RecyclerView.LayoutManager layoutManager;
        if (mode == C.STYLE_LINEAR) {
            layoutManager = new LinearLayoutManager(Utils.getApp());
        } else {
            layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        }

        mRecyclerView.setLayoutManager(layoutManager);
        mAdapter = new RvNoteListAdapter();
        //开启动画(默认为渐显效果)
        mAdapter.openLoadAnimation(BaseQuickAdapter.ALPHAIN);
        //禁止默认第一次加载Item进入回调onLoadMoreRequested方法
        mAdapter.disableLoadMoreIfNotFullPage(mRecyclerView);
        mRecyclerView.setAdapter(mAdapter);
        mAdapter.setEmptyView(getRecyclerViewEmptyView());
        mAdapter.notifyDataSetChanged();
    }

    //recyclerView 为空的时候展示
    private View getRecyclerViewEmptyView() {
        return LayoutInflater.from(getContext())
                .inflate(R.layout.fragment_main_tabs_empty, null, false);
    }


    private void initFab() {
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            //ViewConfiguration.getScaledTouchSlop () 用法 - CSDN博客
            // http://blog.csdn.net/axi295309066/article/details/53823061
            float touchSlop = ViewConfiguration.get(getActivity()).getScaledTouchSlop();

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                //大于这个距离mTouchSlop才可以触发
                if (Math.abs(dy) > touchSlop) {
                    if (dy > touchSlop) {
                        setAddFabIn();// 手指向下滑动
                    } else {
                        setAddFabOut(); // 手指向上滑动
                    }
                }
            }
        });

    }

    private void setAddFabOut() {
        FragmentActivity activity = getActivity();
        if (activity != null && activity instanceof MainActivity) {
            final FloatingActionButton fab = ((MainActivity) activity).mFab;
            if (fab == null) {
                return;
            }
            ObjectAnimator
                    .ofFloat(fab, "translationY", SizeUtils.dp2px(80))
                    .setDuration(150)
                    .start();
        }


    }

    private void setAddFabIn() {
        FragmentActivity activity = getActivity();
        if (activity != null && activity instanceof MainActivity) {
            final FloatingActionButton fab = ((MainActivity) getActivity()).mFab;
            if (fab == null) {
                return;
            }
            ObjectAnimator
                    .ofFloat(fab, "translationY", SizeUtils.dp2px(0))
                    .setDuration(150)
                    .start();
        }


    }

    private void load() {
//        mWeathers.clear();
        //简单的来说, subscribeOn() 指定的是上游发送事件的线程, observeOn() 指定的是下游接收事件的线程.
        Disposable disposable = Observable.timer(0, TimeUnit.SECONDS)
                .compose(this.bindToLifecycle())
                .doOnSubscribe(subscription -> changeRefresh(true))
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .map(aLong -> NoteDbHelper.getInstance().loadAll())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError(error -> {
                    changeRefresh(false);//发生错误,这里也要取消
                    mAdapter.notifyDataSetChanged();
                })
                .subscribe(noteEntityList -> {
                    changeRefresh(false);
                    mAdapter.notifyDataSetChanged();
                });
        mDisposableList.add(disposable);
    }

    @Override
    public void onRefresh() {
        load();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        //释放资源
        for (Disposable disposable : mDisposableList) {
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
        }
    }
}