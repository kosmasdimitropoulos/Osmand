package net.osmand.plus.routepreparationmenu.cards;

import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;

import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.render.RenderingRuleSearchRequest;
import net.osmand.render.RenderingRulesStorage;
import net.osmand.router.RouteStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RouteInfoCard extends BaseCard {

	private MapActivity mapActivity;
	private RouteStatistics.Statistics routeStatistics;

	public RouteInfoCard(MapActivity mapActivity, RouteStatistics.Statistics routeStatistics) {
		super(mapActivity);
		this.mapActivity = mapActivity;
		this.routeStatistics = routeStatistics;
	}

	@Override
	public int getCardLayoutId() {
		return R.layout.route_info_card;
	}

	@Override
	protected void updateContent() {
		updateTitle();
		buildStatisticChart(view, routeStatistics);
		LinearLayout container = view.findViewById(R.id.route_items);
//		if (routeStatistics.getStatisticType() == RouteStatistics.StatisticType.SURFACE) {
			attachLegend(mapActivity.getLayoutInflater(), container, routeStatistics);
//		}
	}

	@Override
	protected void applyDayNightMode() {
		view.setBackgroundColor(ContextCompat.getColor(mapActivity, nightMode ? R.color.route_info_bg_dark : R.color.route_info_bg_light));
		TextView details = (TextView) view.findViewById(R.id.info_type_details);
		details.setTextColor(ContextCompat.getColor(app, nightMode ? R.color.active_buttons_and_links_dark : R.color.active_buttons_and_links_light));
	}

	private void updateTitle() {
		TextView title = (TextView) view.findViewById(R.id.info_type_title);
		String name = getInfoType();
		title.setText(name);
	}

	private String getInfoType() {
		if (routeStatistics.getStatisticType() == RouteStatistics.StatisticType.CLASS) {
			return app.getString(R.string.route_class_stat_container);
		} else if (routeStatistics.getStatisticType() == RouteStatistics.StatisticType.STEEPNESS) {
			return app.getString(R.string.route_steepness_stat_container);
		} else if (routeStatistics.getStatisticType() == RouteStatistics.StatisticType.SMOOTHNESS) {
			return app.getString(R.string.route_smoothness_stat_container);
		} else if (routeStatistics.getStatisticType() == RouteStatistics.StatisticType.SURFACE) {
			return app.getString(R.string.route_surface_stat_container);
		} else {
			return "";
		}
	}

	private int getColorFromStyle(String colorAttrName) {
		RenderingRulesStorage rrs = getMyApplication().getRendererRegistry().getCurrentSelectedRenderer();
		RenderingRuleSearchRequest req = new RenderingRuleSearchRequest(rrs);
		boolean nightMode = false;
		req.setBooleanFilter(rrs.PROPS.R_NIGHT_MODE, nightMode);
		if (req.searchRenderingAttribute(colorAttrName)) {
			return req.getIntPropertyValue(rrs.PROPS.R_ATTR_COLOR_VALUE);
		}
		return 0;
	}

	private <E> void buildStatisticChart(View view, RouteStatistics.Statistics<E> routeStatistics) {
		List<RouteStatistics.RouteSegmentAttribute<E>> segments = routeStatistics.getElements();
		HorizontalBarChart hbc = view.findViewById(R.id.route_chart);
		List<BarEntry> entries = new ArrayList<>();
		float[] stacks = new float[segments.size()];
		int[] colors = new int[segments.size()];
		for (int i = 0; i < stacks.length; i++) {
			RouteStatistics.RouteSegmentAttribute segment = segments.get(i);
			stacks[i] = segment.getDistance();
			colors[i] = getColorFromStyle(segment.getColorAttrName());
		}
		entries.add(new BarEntry(0, stacks));
		BarDataSet barDataSet = new BarDataSet(entries, "");
		barDataSet.setColors(colors);
		BarData data = new BarData(barDataSet);
		data.setDrawValues(false);
		hbc.setData(data);
		hbc.setDrawBorders(false);
		hbc.setTouchEnabled(false);
		hbc.disableScroll();
		hbc.getLegend().setEnabled(false);
		hbc.getDescription().setEnabled(false);
		XAxis xAxis = hbc.getXAxis();
		xAxis.setEnabled(false);
		YAxis leftYAxis = hbc.getAxisLeft();
		YAxis rightYAxis = hbc.getAxisRight();
		rightYAxis.setDrawLabels(true);
		rightYAxis.setGranularity(1f);
		rightYAxis.setValueFormatter(new IAxisValueFormatter() {
			@Override
			public String getFormattedValue(float value, AxisBase axis) {
				if (value > 100) {
					return String.valueOf(value);
				}
				return "";
			}
		});
		rightYAxis.setDrawGridLines(false);
		leftYAxis.setDrawLabels(false);
		leftYAxis.setEnabled(false);
		hbc.invalidate();
	}

	private <E> void attachLegend(LayoutInflater inflater, ViewGroup container, RouteStatistics.Statistics<E> routeStatistics) {
		Map<E, RouteStatistics.RouteSegmentAttribute<E>> partition = routeStatistics.getPartition();
		for (E key : partition.keySet()) {
			RouteStatistics.RouteSegmentAttribute<E> segment = partition.get(key);
			View view = inflater.inflate(R.layout.route_info_stat_item, container, false);
			TextView textView = view.findViewById(R.id.route_stat_item_text);
			String formattedDistance = OsmAndFormatter.getFormattedDistance(segment.getDistance(), getMyApplication());
			SpannableStringBuilder spannable = new SpannableStringBuilder(key.toString());
			spannable.append(": ");
			spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			spannable.append(formattedDistance);
			textView.setText(spannable);
			Drawable circle = app.getUIUtilities().getPaintedIcon(R.drawable.ic_action_circle, getColorFromStyle(segment.getColorAttrName()));
			ImageView imageView = view.findViewById(R.id.route_stat_item_image);
			imageView.setImageDrawable(circle);
			container.addView(view);
		}
	}
}