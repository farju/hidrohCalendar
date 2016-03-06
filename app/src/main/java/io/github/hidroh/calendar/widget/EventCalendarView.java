package io.github.hidroh.calendar.widget;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * A custom Calendar View, in the form of circular {@link ViewPager}
 * that supports month change event and state restoration.
 *
 * The {@link ViewPager} recycles adapter item views as users scroll
 * to first or last item.
 */
public class EventCalendarView extends ViewPager {

    private OnChangeListener mListener;
    private final MonthViewPagerAdapter mAdapter = new MonthViewPagerAdapter();

    /**
     * Callback interface for calendar view change events
     */
    public interface OnChangeListener {
        /**
         * Fired when active month has been changed
         * @param calendar    calendar object that represents active month
         */
        void onSelectedMonthChange(@NonNull Calendar calendar);
    }

    public EventCalendarView(Context context) {
        this(context, null);
    }

    public EventCalendarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // make this ViewPager's height WRAP_CONTENT
        View child = mAdapter.mViews.get(getCurrentItem());
        if (child != null) {
            child.measure(widthMeasureSpec, heightMeasureSpec);
            int height = child.getMeasuredHeight();
            setMeasuredDimension(getMeasuredWidth(), height);
        }
    }

    /**
     * Sets listener to be notified upon calendar view change events
     * @param listener    listener to be notified
     */
    public void setOnChangeListener(OnChangeListener listener) {
        mListener = listener;
        if (listener != null) {
            listener.onSelectedMonthChange(mAdapter.getCalendar(getCurrentItem()));
        }
    }

    private void init() {
        setAdapter(mAdapter);
        addOnPageChangeListener(new OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // no op
            }

            @Override
            public void onPageSelected(int position) {
                if (mListener != null) {
                    mListener.onSelectedMonthChange(mAdapter.getCalendar(position));
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager.SCROLL_STATE_IDLE) {
                    int position = getCurrentItem(), first = 0, last = mAdapter.getCount() - 1;
                    if (position == last) {
                        mAdapter.shiftLeft();
                        setCurrentItem(first + 1, false);
                    } else if (position == 0) {
                        mAdapter.shiftRight();
                        setCurrentItem(last - 1, false);
                    } else {
                        // rebind neighbours in case they have been invalidated due to shifting
                        mAdapter.bind(position - 1);
                        mAdapter.bind(position + 1);
                    }
                }
            }
        });
        setCurrentItem(mAdapter.getCount() / 2);
    }

    /**
     * A circular {@link PagerAdapter}, with a view pool of 5 items:
     * buffer, left, [active], right, buffer
     * Upon user scrolling to a buffer view, {@link ViewPager#setCurrentItem(int)}
     * should be called to wrap around and shift active view to the next non-buffer
     * @see #shiftLeft()
     * @see #shiftRight()
     */
    static class MonthViewPagerAdapter extends PagerAdapter {
        private static final String STATE_MONTH = "state:month";
        private static final String STATE_YEAR = "state:year";
        static final int ITEM_COUNT = 5; // buffer, left, active, right, buffer
        final List<MonthView> mViews = new ArrayList<>(getCount());
        private final List<Calendar> mCalendars = new ArrayList<>(getCount());

        public MonthViewPagerAdapter() {
            int mid = ITEM_COUNT / 2;
            for (int i = 0; i < getCount(); i++) {
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.MONTH, i - mid);
                mCalendars.add(calendar);
                mViews.add(null);
            }
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            MonthView view = new MonthView(container.getContext());
            view.setLayoutParams(new ViewPager.LayoutParams());
            mViews.set(position, view);
            container.addView(view);
            bind(position);
            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public int getCount() {
            return ITEM_COUNT;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Parcelable saveState() {
            Bundle bundle = new Bundle();
            bundle.putInt(STATE_MONTH, mCalendars.get(0).get(Calendar.MONTH));
            bundle.putInt(STATE_YEAR, mCalendars.get(0).get(Calendar.YEAR));
            return bundle;
        }

        @Override
        public void restoreState(Parcelable state, ClassLoader loader) {
            Bundle savedState = (Bundle) state;
            if (savedState == null) {
                return;
            }
            int month = savedState.getInt(STATE_MONTH), year = savedState.getInt(STATE_YEAR);
            for (int i = 0; i < getCount(); i++) {
                Calendar calendar = Calendar.getInstance();
                calendar.set(year, month, 1);
                calendar.add(Calendar.MONTH, i);
                mCalendars.set(i, calendar);
            }
        }

        Calendar getCalendar(int position) {
            return mCalendars.get(position);
        }

        /**
         * shift Jan, Feb, Mar, Apr, [May] to Apr, [May], Jun, Jul, Aug
         * rebind views in view pool if needed
         */
        void shiftLeft() {
            for (int i = 0; i < getCount() - 2; i++) {
                Calendar first = mCalendars.remove(0);
                first.add(Calendar.MONTH, getCount());
                mCalendars.add(first);
            }
            // rebind first 3 views
            for (int i = 0; i <= 2; i++) {
                bind(i);
            }
        }

        /**
         * shift [Jan], Feb, Mar, Apr, May to Oct, Nov, Dec, [Jan], Feb
         * rebind views in view pool if needed
         */
        void shiftRight() {
            for (int i = 0; i < getCount() - 2; i++) {
                Calendar last = mCalendars.remove(getCount() - 1);
                last.add(Calendar.MONTH, -getCount());
                mCalendars.add(0, last);
            }
            // rebind last 3 views
            for (int i = 0; i <= 2; i++) {
                bind(getCount() - 1 - i);
            }
        }

        void bind(int position) {
            if (mViews.get(position) != null) {
                mViews.get(position).setCalendar(mCalendars.get(position));
            }
        }
    }
}
