package com.lcodecore.tkrefreshlayout.processor;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.animation.DecelerateInterpolator;

import com.lcodecore.tkrefreshlayout.TwinklingRefreshLayout;
import com.lcodecore.tkrefreshlayout.utils.ScrollingUtil;

import java.util.LinkedList;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Created by lcodecore on 2016/11/26.
 */

public class AnimProcessor implements IAnimRefresh, IAnimOverScroll {

    private TwinklingRefreshLayout.CoContext cp;
    private static final float animFraction = 1f;
    //动画的变化率
    private DecelerateInterpolator decelerateInterpolator;

    public AnimProcessor(TwinklingRefreshLayout.CoContext coProcessor) {
        this.cp = coProcessor;
        decelerateInterpolator = new DecelerateInterpolator(8);
    }

    private boolean scrollHeadLocked = false;
    private boolean scrollBottomLocked = false;

    public void scrollHeadByMove(float moveY) {
        float offsetY = decelerateInterpolator.getInterpolation(moveY / cp.getMaxHeadHeight() / 2) * moveY / 2;

        //禁止下拉刷新时下拉不显示头部
        if (cp.isPureScrollModeOn() || (!cp.enableRefresh() && !cp.isOverScrollTopShow())) {
            if (cp.getHeader().getVisibility() != GONE) {
                cp.getHeader().setVisibility(GONE);
            }
        } else {
            if (cp.getHeader().getVisibility() != VISIBLE) {
                cp.getHeader().setVisibility(VISIBLE);
            }
        }

        if (scrollHeadLocked && cp.isEnableKeepIView()) {
            cp.getHeader().setTranslationY(offsetY - cp.getHeader().getLayoutParams().height);
        } else {
            cp.getHeader().setTranslationY(0);
            cp.getHeader().getLayoutParams().height = (int) Math.abs(offsetY);
            cp.getHeader().requestLayout();
            cp.onPullingDown(offsetY);
        }

        if (!cp.isOpenFloatRefresh()) {
            cp.getTargetView().setTranslationY(offsetY);
            translateExHead((int) offsetY);
        }
    }

    public void scrollBottomByMove(float moveY) {
        float offsetY = decelerateInterpolator.getInterpolation(moveY / cp.getMaxBottomHeight() / 2) * moveY / 2;

        if (cp.isPureScrollModeOn() || (!cp.enableLoadmore() && !cp.isOverScrollBottomShow())) {
            if (cp.getFooter().getVisibility() != GONE) {
                cp.getFooter().setVisibility(GONE);
            }
        } else {
            if (cp.getFooter().getVisibility() != VISIBLE) {
                cp.getFooter().setVisibility(VISIBLE);
            }
        }

        if (scrollBottomLocked && cp.isEnableKeepIView()) {
            cp.getFooter().setTranslationY(cp.getHeader().getLayoutParams().height - offsetY);
        } else {
            cp.getFooter().getLayoutParams().height = (int) Math.abs(offsetY);
            cp.getFooter().requestLayout();
            cp.onPullingUp(-offsetY);
        }


        cp.getTargetView().setTranslationY(-offsetY);
    }

    public void dealPullDownRelease() {
        if (!cp.isPureScrollModeOn() && cp.enableRefresh() && getVisibleHeadHeight() >= cp.getHeadHeight() - cp.getTouchSlop()) {
            animHeadToRefresh();
        } else {
            animHeadBack(false);
        }
    }

    public void dealPullUpRelease() {
        if (!cp.isPureScrollModeOn() && cp.enableLoadmore() && getVisibleFootHeight() >= cp.getBottomHeight() - cp.getTouchSlop()) {
            animBottomToLoad();
        } else {
            animBottomBack(false);
        }
    }

    private int getVisibleHeadHeight() {
        Log.i("header translationY:", cp.getHeader().getTranslationY() + ",Visible head height:" + (cp.getHeader().getLayoutParams().height + cp.getHeader().getTranslationY()));
        return (int) (cp.getHeader().getLayoutParams().height + cp.getHeader().getTranslationY());
    }

    private int getVisibleFootHeight() {
        Log.i("footer translationY:", cp.getFooter().getTranslationY() + "");
        return (int) (cp.getFooter().getLayoutParams().height + cp.getFooter().getTranslationY());
    }

    private boolean isAnimHeadToRefresh = false;

    /**
     * 1.满足进入刷新的条件或者主动刷新时，把Head位移到刷新位置（当前位置 ~ HeadHeight）
     */
    public void animHeadToRefresh() {
        isAnimHeadToRefresh = true;
        animLayoutByTime(getVisibleHeadHeight(), cp.getHeadHeight(), animHeadUpListener, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimHeadToRefresh = false;

                cp.setRefreshVisible(true);
                if (cp.isEnableKeepIView()) {
                    if (!scrollHeadLocked) {
                        cp.setRefreshing(true);
                        cp.onRefresh();
                        scrollHeadLocked = true;
                    }
                } else {
                    cp.setRefreshing(true);
                    cp.onRefresh();
                }
            }
        });
    }

    private boolean isAnimHeadBack = false;

    /**
     * 2.动画结束或不满足进入刷新状态的条件，收起头部（当前位置 ~ 0）
     */
    public void animHeadBack(final boolean isFinishRefresh) {
        isAnimHeadBack = true;
        animLayoutByTime(getVisibleHeadHeight(), 0, animHeadUpListener, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimHeadBack = false;
                cp.setRefreshVisible(false);
                if (isFinishRefresh) {
                    if (scrollHeadLocked && cp.isEnableKeepIView()) {
                        cp.setPrepareFinishRefresh(true);
                        cp.getHeader().setTranslationY(0);
                        cp.getHeader().getLayoutParams().height = 0;
                        cp.getHeader().requestLayout();
                        scrollHeadLocked = false;
                        cp.setRefreshing(false);
                    }
                }
            }
        });
    }

    private boolean isAnimBottomToLoad = false;

    /**
     * 3.满足进入加载更多的条件或者主动加载更多时，把Footer移到加载更多位置（当前位置 ~ BottomHeight）
     */
    public void animBottomToLoad() {
        isAnimBottomToLoad = true;
        animLayoutByTime(getVisibleFootHeight(), cp.getBottomHeight(), animBottomUpListener, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimBottomToLoad = false;

                cp.setLoadVisible(true);
                if (cp.isEnableKeepIView()) {
                    if (!scrollBottomLocked) {
                        cp.setLoadingMore(true);
                        cp.onLoadMore();
                        scrollBottomLocked = true;
                    }
                } else {
                    cp.setLoadingMore(true);
                    cp.onLoadMore();
                }
            }
        });
    }

    private boolean isAnimBottomBack = false;

    /**
     * 4.加载更多完成或者不满足进入加载更多模式的条件时，收起尾部（当前位置 ~ 0）
     */
    public void animBottomBack(final boolean isFinishLoading) {
        isAnimBottomBack = true;
        animLayoutByTime(getVisibleFootHeight(), 0, new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (!ScrollingUtil.isViewToBottom(cp.getTargetView(), cp.getTouchSlop())) {
                    int dy = getVisibleFootHeight() - (int) animation.getAnimatedValue();
                    //可以让TargetView滚动dy高度，但这样两个方向上滚动感觉画面闪烁，改为dy/2是为了消除闪烁
                    if (dy > 0) {
                        if (cp.getTargetView() instanceof RecyclerView)
                            ScrollingUtil.scrollAViewBy(cp.getTargetView(), dy);
                        else ScrollingUtil.scrollAViewBy(cp.getTargetView(), dy / 2);
                    }
                }

                //decorate the AnimatorUpdateListener
                animBottomUpListener.onAnimationUpdate(animation);
            }
        }, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimBottomBack = false;

                cp.setLoadVisible(false);
                if (isFinishLoading) {
                    if (scrollBottomLocked && cp.isEnableKeepIView()) {
                        cp.setPrepareFinishLoadMore(true);
                        cp.getFooter().setTranslationY(0);
                        cp.getFooter().getLayoutParams().height = 0;
                        cp.getFooter().requestLayout();
                        scrollBottomLocked = false;
                        cp.setLoadingMore(false);
                    }
                }
            }
        });
    }

    private boolean isAnimHeadHide = false;

    /**
     * 5.当刷新处于可见状态，向上滑动屏幕时，隐藏刷新控件
     *
     * @param vy 手指向上滑动速度
     */
    public void animHeadHideByVy(int vy) {
        if (isAnimHeadHide) return;
        isAnimHeadHide = true;
        vy = Math.abs(vy);
        if (vy < 5000) vy = 8000;
        animLayoutByTime(getVisibleHeadHeight(), 0, 5 * Math.abs(getVisibleHeadHeight() * 1000 / vy), animHeadUpListener, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimHeadHide = false;
                cp.setRefreshVisible(false);
                if (!cp.isEnableKeepIView()) {
                    cp.setRefreshing(false);
                    cp.onRefreshCanceled();
                    cp.resetHeaderView();
                }
            }
        });
    }

    private boolean isAnimBottomHide = false;

    /**
     * 6.当加载更多处于可见状态时，向下滑动屏幕，隐藏加载更多控件
     *
     * @param vy 手指向下滑动的速度
     */
    public void animBottomHideByVy(int vy) {
        if (isAnimBottomHide) return;
        isAnimBottomHide = true;
        vy = Math.abs(vy);
        if (vy < 5000) vy = 8000;
        animLayoutByTime(getVisibleFootHeight(), 0, 5 * getVisibleFootHeight() * 1000 / vy, animBottomUpListener, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimBottomHide = false;
                cp.setLoadVisible(false);
                if (!cp.isEnableKeepIView()) {
                    cp.setLoadingMore(false);
                    cp.onLoadmoreCanceled();
                    cp.resetBottomView();
                }
            }
        });
    }

    private boolean isAnimOsTop = false;
    private boolean isOverScrollTopLocked = false;

    /**
     * 7.执行顶部越界  To executive cross-border springback at the top.
     * 越界高度height ∝ vy/computeTimes，此处采用的模型是 height=A*(vy + B)/computeTimes
     *
     * @param vy           满足越界条件的手指滑动速度  the finger sliding speed on the screen.
     * @param computeTimes 从满足条件到滚动到顶部总共计算的次数 Calculation times from sliding to top.
     */
    public void animOverScrollTop(float vy, int computeTimes) {
        if (isOverScrollTopLocked) return;
        isOverScrollTopLocked = true;
        isAnimOsTop = true;
        cp.setStatePTD();
        int oh = (int) Math.abs(vy / computeTimes / 2);
        final int overHeight = oh > cp.getOsHeight() ? cp.getOsHeight() : oh;
        final int time = overHeight <= 50 ? 115 : (int) (0.3 * overHeight + 100);
//        animLayoutByTime(0, overHeight, time, overScrollTopUpListener);
//        animLayoutByTime(overHeight, 0, 2 * time, overScrollTopUpListener, new AnimatorListenerAdapter() {
//            @Override
//            public void onAnimationEnd(Animator animation) {
//                isAnimOsTop = false;
//                isOverScrollTopLocked = false;
//            }
//        });
        animLayoutByTime(0, overHeight, time, overScrollTopUpListener, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animLayoutByTime(overHeight, 0, 2 * time, overScrollTopUpListener, new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        isAnimOsTop = false;
                        isOverScrollTopLocked = false;
                    }
                });
            }
        });
    }

    private boolean isAnimOsBottom = false;
    private boolean isOverScrollBottomLocked = false;

    /**
     * 8.执行底部越界
     *
     * @param vy           满足越界条件的手指滑动速度
     * @param computeTimes 从满足条件到滚动到顶部总共计算的次数
     */
    public void animOverScrollBottom(float vy, int computeTimes) {
        if (isOverScrollBottomLocked) return;
        cp.setStatePBU();
        int oh = (int) Math.abs(vy / computeTimes / 2);
        final int overHeight = oh > cp.getOsHeight() ? cp.getOsHeight() : oh;
        final int time = overHeight <= 50 ? 115 : (int) (0.3 * overHeight + 100);
        if (cp.autoLoadMore()) {
            cp.startLoadMore();
        } else {
            isOverScrollBottomLocked = true;
            isAnimOsBottom = true;
            animLayoutByTime(0, overHeight, time, overScrollBottomUpListener, new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animLayoutByTime(overHeight, 0, 2 * time, overScrollBottomUpListener, new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            isAnimOsBottom = false;
                            isOverScrollBottomLocked = false;
                        }
                    });
                }
            });
        }
    }

    private AnimatorUpdateListener animHeadUpListener = new AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            int height = (int) animation.getAnimatedValue();
            if (scrollHeadLocked && cp.isEnableKeepIView()) {
                cp.getHeader().setTranslationY(height - cp.getHeader().getLayoutParams().height);
            } else {
                cp.getHeader().setTranslationY(0);
                cp.getHeader().getLayoutParams().height = height;
                cp.getHeader().requestLayout();
                cp.onPullDownReleasing(height);
            }
            if (!cp.isOpenFloatRefresh()) {
                cp.getTargetView().setTranslationY(height);
                translateExHead(height);
            }
        }
    };

    private AnimatorUpdateListener animBottomUpListener = new AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            int height = (int) animation.getAnimatedValue();
            if (scrollBottomLocked && cp.isEnableKeepIView()) {
                cp.getFooter().setTranslationY(cp.getHeader().getLayoutParams().height - height);
            } else {
                cp.getFooter().setTranslationY(0);
                cp.getFooter().getLayoutParams().height = height;
                cp.getFooter().requestLayout();
                cp.onPullUpReleasing(height);
            }
            cp.getTargetView().setTranslationY(-height);
        }
    };

    //    private boolean isHeadLocked = false;
    private AnimatorUpdateListener overScrollTopUpListener = new AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            int height = (int) animation.getAnimatedValue();
            if (cp.isOverScrollTopShow()) {
                if (scrollHeadLocked && cp.isEnableKeepIView()) {
                    cp.getHeader().setTranslationY((height - cp.getHeader().getLayoutParams().height));
                } else {
                    cp.getHeader().setTranslationY(0);
                    cp.getHeader().getLayoutParams().height = height;
                    cp.getHeader().requestLayout();

                    cp.onPullDownReleasing(height);
                }
            } else {
                if (cp.getHeader().getVisibility() != GONE) {
                    cp.getHeader().setVisibility(GONE);
                }
                cp.onPullDownReleasing(height);
            }

            cp.getTargetView().setTranslationY(height);
            translateExHead(height);
        }
    };

    private AnimatorUpdateListener overScrollBottomUpListener = new AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            int height = (int) animation.getAnimatedValue();
            if (cp.isOverScrollBottomShow()) {
                if (scrollBottomLocked && cp.isEnableKeepIView()) {
                    cp.getFooter().setTranslationY(cp.getHeader().getLayoutParams().height - height);
                } else {
                    cp.getFooter().setTranslationY(0);
                    cp.getFooter().getLayoutParams().height = height;
                    cp.getFooter().requestLayout();

                    cp.onPullUpReleasing(height);
                }

            } else {
                if (cp.getFooter().getVisibility() != GONE) {
                    cp.getFooter().setVisibility(GONE);
                }
                cp.onPullUpReleasing(height);
            }
            cp.getTargetView().setTranslationY(-height);
        }
    };

    public void onScrolled(int distanceY) {
        //TODO 支持正常模式的Header  to support the normal-mode ex-header.
    }

    private void translateExHead(int offsetY) {
        if (!cp.isExHeadLocked()) cp.getExHead().setTranslationY(offsetY);
    }

    public void animLayoutByTime(int start, int end, long time, AnimatorUpdateListener listener, AnimatorListener animatorListener) {
        ValueAnimator va = ValueAnimator.ofInt(start, end);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(listener);
        va.addListener(animatorListener);
        va.setDuration(time);
        va.start();
//        offerToQueue(va);
    }

    public void animLayoutByTime(int start, int end, long time, AnimatorUpdateListener listener) {
        ValueAnimator va = ValueAnimator.ofInt(start, end);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(listener);
        va.setDuration(time);
        va.start();
//        offerToQueue(va);
    }

    public void animLayoutByTime(int start, int end, AnimatorUpdateListener listener, AnimatorListener animatorListener) {
        ValueAnimator va = ValueAnimator.ofInt(start, end);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(listener);
        va.addListener(animatorListener);
        va.setDuration((int) (Math.abs(start - end) * animFraction));
        va.start();
//        offerToQueue(va);
    }

    private void offerToQueue(Animator animator) {
        if (animator == null) return;
        animQueue.offer(animator);

        System.out.println("当前队列中的Animator数量：" + animQueue.size());

        animator.addListener(new AnimatorListenerAdapter() {
            long startTime = 0;

            @Override
            public void onAnimationStart(Animator animation) {
                startTime = System.currentTimeMillis();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                animQueue.poll();
                if (animQueue.size() > 0) {
                    animQueue.getFirst().start();
                }
                System.out.println("动画结束：开始时间->" + startTime + ",用时" + (System.currentTimeMillis() - startTime));
            }
        });
        if (animQueue.size() == 1) {
            animator.start();
        }
    }

    private LinkedList<Animator> animQueue = new LinkedList<>();
}
