﻿package com.zhangke.widget;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Scroller;
import android.widget.TextView;

import com.seagetech.ptduser.test.R;


/**
 * Created by 张可 on 2017/7/5.
 */

public class PullToRefreshRecyclerView extends FrameLayout {

    private static final String TAG = "PullToRefreshRecycler";

    private View rootView;
    private RecyclerView recyclerView;
    private View footView;
    private ProgressBar progressBar;
    private TextView tvLoadTag;
    private ImageView imgArrow;

    private int footViewHeight = 100;

    private int lastDownY;

    private boolean canScroll = false;
    private boolean canLoad = false;
    private boolean isLoading = false;//是否正在加载，正在加载时拦截滑动事件
    /**
     * 箭头方向是否向上
     */
    private boolean arrowIsTop = true;

    private RotateAnimation bottomAnimation;//箭头由上到下的动画
    private RotateAnimation topAnimation;//箭头由下到上的动画

    private Scroller mScroller;

    private OnPullToBottomListener onPullToBottomListener;

    private int lastOfferY = 0;

    public PullToRefreshRecyclerView(Context context) {
        super(context);
        init();
    }

    public PullToRefreshRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_pull_to_refresh_recycler, this);

        rootView = findViewById(R.id.root_view);

        recyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_view);
        footView = rootView.findViewById(R.id.footer_view);
        progressBar = (ProgressBar) rootView.findViewById(R.id.progress);
        tvLoadTag = (TextView) rootView.findViewById(R.id.tv_load_tag);
        imgArrow = (ImageView) rootView.findViewById(R.id.img_arrow);

        footViewHeight = UiTools.dip2px(getContext(), 80);

        bottomAnimation = new RotateAnimation(0, 180, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        bottomAnimation.setDuration(200);
        bottomAnimation.setFillAfter(true);

        topAnimation = new RotateAnimation(180, 0, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        topAnimation.setDuration(200);
        topAnimation.setFillAfter(true);

        mScroller = new Scroller(getContext());
    }

    public void setLayoutManager(RecyclerView.LayoutManager layout) {
        recyclerView.setLayoutManager(layout);
    }

    public void setAdapter(RecyclerView.Adapter adapter) {
        recyclerView.setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }

    public RecyclerView getRecyclerView() {
        return recyclerView;
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final int height = getMeasuredHeight() + footViewHeight;
        setMeasuredDimension(getMeasuredWidth(), height);

        LayoutParams rootLP = (LayoutParams) rootView.getLayoutParams();
        rootLP.height = height;
        rootView.setLayoutParams(rootLP);

        LayoutParams recyclerLp = (LayoutParams) recyclerView.getLayoutParams();
        recyclerLp.height = height - footViewHeight;
        recyclerView.setLayoutParams(recyclerLp);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (recyclerView == null || recyclerView.getChildCount() == 0)
            return super.onInterceptTouchEvent(ev);
        if (isLoading) return true;
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastDownY = (int) ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                int lastPosition = -1;

                RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                if (layoutManager instanceof GridLayoutManager) {
                    lastPosition = ((GridLayoutManager) layoutManager).findLastVisibleItemPosition();
                } else if (layoutManager instanceof LinearLayoutManager) {
                    lastPosition = ((LinearLayoutManager) layoutManager).findLastVisibleItemPosition();
                } else if (layoutManager instanceof StaggeredGridLayoutManager) {
                    int[] lastPositions = new int[((StaggeredGridLayoutManager) layoutManager).getSpanCount()];
                    ((StaggeredGridLayoutManager) layoutManager).findLastVisibleItemPositions(lastPositions);
                    lastPosition = findMax(lastPositions);
                }

                int offerY = (int) ev.getY() - lastDownY;
                if (offerY < 0) {
                    View lastView = recyclerView.getChildAt(recyclerView.getChildCount() - 1);
                    if (lastView != null && lastView.getBottom() + footViewHeight <= getHeight() && lastPosition == recyclerView.getLayoutManager().getItemCount() - 1) {
                        canScroll = true;
                        return true;
                    } else {
                        canScroll = false;
                    }
                } else {
                    canScroll = false;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (recyclerView == null || recyclerView.getChildCount() == 0)
            return super.onInterceptTouchEvent(ev);
        if (isLoading) return true;
        int offerY = 0;
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastDownY = (int) ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (canScroll) {
                    offerY = (int) ev.getY() - lastDownY;
                    lastOfferY = offerY;
                    scrollTo(getScrollX(), -offerY / 2);
                    imgArrow.setVisibility(VISIBLE);
                    if (Math.abs(offerY) / 2 < footViewHeight) {
                        progressBar.setVisibility(GONE);
                        tvLoadTag.setText("上拉加载数据");
                        if (!arrowIsTop) {
                            imgArrow.startAnimation(topAnimation);
                            arrowIsTop = true;
                        }
                        canLoad = false;
                    } else {
                        progressBar.setVisibility(GONE);
                        tvLoadTag.setText("松手加载更多");
                        if (arrowIsTop) {
                            imgArrow.startAnimation(bottomAnimation);
                            arrowIsTop = false;
                        }
                        canLoad = true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (canScroll) {
                    if (!canLoad) {
                        scrollTo(getScrollX(), 0);
                    } else {
                        mScroller.startScroll(getScrollX(), getScrollY(), getScrollX(), -(Math.abs(lastOfferY) / 2 - footViewHeight));
                        lastOfferY = 0;
                        loadData();
                    }
                    canScroll = false;
                }
                break;
        }
        return super.onTouchEvent(ev);
    }

    private void loadData() {
        isLoading = true;
        imgArrow.clearAnimation();
        imgArrow.setVisibility(GONE);
        progressBar.setVisibility(VISIBLE);
        tvLoadTag.setText("正在加载...");
        if (this.onPullToBottomListener != null) {
            this.onPullToBottomListener.onPullToBottom();
        } else {
            progressBar.setVisibility(GONE);
            scrollTo(getScrollX(), 0);
        }
    }

    /**
     * 设置是否正在加载，一般来书，在加载完毕之后应该调用此方法
     *
     * @param loading
     */
    public void setLoading(final boolean loading) {
        isLoading = loading;
        if (!isLoading) {
            post(new Runnable() {
                @Override
                public void run() {
                    if (!loading) {
                        progressBar.setVisibility(GONE);
                        scrollTo(getScrollX(), 0);
                    }
                }
            });
        }
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            invalidate();
        }
    }

    //找到数组中的最大值
    private int findMax(int[] lastPositions) {
        int max = lastPositions[0];
        for (int value : lastPositions) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    public void setOnPullToBottomListener(OnPullToBottomListener onPullToBottomListener) {
        this.onPullToBottomListener = onPullToBottomListener;
    }

    public interface OnPullToBottomListener {
        void onPullToBottom();
    }
}
