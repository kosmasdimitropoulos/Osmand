package net.osmand.plus.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;

import net.osmand.AndroidUtils;
import net.osmand.Location;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.GPXUtilities;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.GPXUtilities.GPXTrackAnalysis;
import net.osmand.GPXUtilities.TrkSegment;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayGroup;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayItem;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.helpers.GpxUiHelper;
import net.osmand.plus.helpers.GpxUiHelper.GPXDataSetAxisType;
import net.osmand.plus.helpers.GpxUiHelper.GPXDataSetType;
import net.osmand.plus.helpers.GpxUiHelper.OrderedLineDataSet;
import net.osmand.plus.routepreparationmenu.MapRouteInfoMenu;
import net.osmand.plus.routing.RouteDirectionInfo;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.views.TurnPathHelper;
import net.osmand.render.RenderingRuleSearchRequest;
import net.osmand.render.RenderingRulesStorage;
import net.osmand.router.RouteSegmentResult;
import net.osmand.router.RouteStatistics;
import net.osmand.router.RouteStatistics.Incline;
import net.osmand.router.RouteStatistics.RouteSegmentAttribute;
import net.osmand.router.RouteStatistics.Statistics;
import net.osmand.util.Algorithms;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class ShowRouteInfoDialogFragment extends DialogFragment {

	public static final String TAG = "ShowRouteInfoDialogFragment";

	private RoutingHelper helper;
	private View view;
	private ListView listView;
	private RouteInfoAdapter adapter;
	private GPXFile gpx;
	private OrderedLineDataSet slopeDataSet;
	private OrderedLineDataSet elevationDataSet;
	private GpxDisplayItem gpxItem;

	public ShowRouteInfoDialogFragment() {
	}

	private OsmandApplication getMyApplication() {
		return (OsmandApplication) getActivity().getApplication();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		boolean isLightTheme = getMyApplication().getSettings().OSMAND_THEME.get() == OsmandSettings.OSMAND_LIGHT_THEME;
		int themeId = isLightTheme ? R.style.OsmandLightThemeWithLightStatusBar : R.style.OsmandDarkTheme;
		setStyle(STYLE_NO_FRAME, themeId);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		final OsmandApplication app = getMyApplication();
		helper = app.getRoutingHelper();

		view = inflater.inflate(R.layout.route_info_layout, container, false);

		Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
		toolbar.setNavigationIcon(getMyApplication().getUIUtilities().getThemedIcon(R.drawable.ic_arrow_back));
		toolbar.setNavigationContentDescription(R.string.shared_string_close);
		toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});

		((ImageView) view.findViewById(R.id.distance_icon))
				.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_route_distance));
		((ImageView) view.findViewById(R.id.time_icon))
				.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_time_span));

		buildMenuButtons();

		listView = (ListView) view.findViewById(android.R.id.list);
		listView.setBackgroundColor(getResources().getColor(
				app.getSettings().isLightContent() ? R.color.ctx_menu_info_view_bg_light
						: R.color.ctx_menu_info_view_bg_dark));

		View topShadowView = inflater.inflate(R.layout.list_shadow_header, listView, false);
		listView.addHeaderView(topShadowView, null, false);
		View bottomShadowView = inflater.inflate(R.layout.list_shadow_footer, listView, false);
		listView.addFooterView(bottomShadowView, null, false);

		adapter = new RouteInfoAdapter(helper.getRouteDirections());
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (position < 2) {
					return;
				}
				RouteDirectionInfo item = adapter.getItem(position - 2);
				Location loc = helper.getLocationFromRouteDirection(item);
				if (loc != null) {
					MapRouteInfoMenu.directionInfo = position - 2;
					OsmandSettings settings = getMyApplication().getSettings();
					settings.setMapLocationToShow(loc.getLatitude(), loc.getLongitude(),
							Math.max(13, settings.getLastKnownMapZoom()),
							new PointDescription(PointDescription.POINT_TYPE_MARKER, item.getDescriptionRoutePart() + " " + getTimeDescription(item)),
							false, null);
					MapActivity.launchMapActivityMoveToTop(getActivity());
					dismiss();
				}
			}
		});

		int dist = helper.getLeftDistance();
		int time = helper.getLeftTime();
		int hours = time / (60 * 60);
		int minutes = (time / 60) % 60;
		((TextView) view.findViewById(R.id.distance)).setText(OsmAndFormatter.getFormattedDistance(dist, app));
		StringBuilder timeStr = new StringBuilder();
		if (hours > 0) {
			timeStr.append(hours).append(" ").append(getString(R.string.osmand_parking_hour)).append(" ");
		}
		if (minutes > 0) {
			timeStr.append(minutes).append(" ").append(getString(R.string.osmand_parking_minute));
		}
		((TextView) view.findViewById(R.id.time)).setText(timeStr);

		view.findViewById(R.id.go_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				MapActivity activity = (MapActivity) getActivity();
				if (activity != null) {
					activity.getMapLayers().getMapControlsLayer().startNavigation();
					dismiss();
				}
			}
		});

		makeGpx();
		if (gpx.hasAltitude) {
			View headerView = inflater.inflate(R.layout.route_info_header, null);
			buildHeader(headerView);
			listView.addHeaderView(headerView);
		}

		if (slopeDataSet != null) {
			List<Incline> inclines = createInclinesAndAdd100MetersWith0Incline(slopeDataSet.getValues());

			List<RouteSegmentResult> route = helper.getRoute().getOriginalRoute();
			if (route != null) {
				RouteStatistics routeStatistics = RouteStatistics.newRouteStatistic(route);
				buildChartAndAttachLegend(app, view, inflater, R.id.route_class_stat_chart,
						R.id.route_class_stat_items, routeStatistics.getRouteClassStatistic());
				buildChartAndAttachLegend(app, view, inflater, R.id.route_surface_stat_chart,
						R.id.route_surface_stat_items, routeStatistics.getRouteSurfaceStatistic());
				buildChartAndAttachLegend(app, view, inflater, R.id.route_smoothness_stat_chart,
						R.id.route_smoothness_stat_items, routeStatistics.getRouteSmoothnessStatistic());
				buildChartAndAttachLegend(app, view, inflater, R.id.route_steepness_stat_chart,
						R.id.route_steepness_stat_items, routeStatistics.getRouteSteepnessStatistic(inclines));
			}
		}
		return view;
	}

	private List<Incline> createInclinesAndAdd100MetersWith0Incline(List<Entry> entries) {
		int size = entries.size();
		List<Incline> inclines = new ArrayList<>();
		for (Entry entry : entries) {
			Incline incline = new Incline(entry.getY(), entry.getX() * 1000);
			inclines.add(incline);

		}
		for (int i = 0; i < 10; i++) {
			float distance = i * 5;
			inclines.add(i, new Incline(0f, distance));
		}
		float lastDistance = slopeDataSet.getEntryForIndex(size - 1).getX();
		for (int i = 1; i <= 10; i++) {
			float distance = lastDistance * 1000f + i * 5f;
			inclines.add(new Incline(0f, distance));
		}
		return inclines;
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

	private <E> void buildStatisticChart(View view, int chartId, Statistics<E> routeStatistics) {
		List<RouteSegmentAttribute<E>> segments = routeStatistics.getElements();
		HorizontalBarChart hbc = view.findViewById(chartId);
		List<BarEntry> entries = new ArrayList<>();
		float[] stacks = new float[segments.size()];
		int[] colors = new int[segments.size()];
		for (int i = 0; i < stacks.length; i++) {
			RouteSegmentAttribute segment = segments.get(i);
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

	private <E> void attachLegend(OsmandApplication app, LayoutInflater inflater, ViewGroup container, Statistics<E> routeStatistics) {
		Map<E, RouteSegmentAttribute<E>> partition = routeStatistics.getPartition();
		for (E key : partition.keySet()) {
			RouteSegmentAttribute<E> segment = partition.get(key);
			View view = inflater.inflate(R.layout.route_info_stat_item, container, false);
			TextView textView = view.findViewById(R.id.route_stat_item_text);
			String formattedDistance = OsmAndFormatter.getFormattedDistance(segment.getDistance(), getMyApplication());
			textView.setText(String.format("%s - %s", key, formattedDistance));
			Drawable circle = app.getUIUtilities().getPaintedIcon(R.drawable.ic_action_circle,getColorFromStyle(segment.getColorAttrName()));
			ImageView imageView = view.findViewById(R.id.route_stat_item_image);
			imageView.setImageDrawable(circle);
			container.addView(view);
		}
	}

	private void buildChartAndAttachLegend(OsmandApplication app, View view, LayoutInflater inflater, int chartId, int containerId, Statistics routeStatistics) {
		ViewGroup container = view.findViewById(containerId);
		buildStatisticChart(view, chartId, routeStatistics);
		attachLegend(app, inflater, container, routeStatistics);
	}

	private void makeGpx() {
		gpx = GpxUiHelper.makeGpxFromRoute(helper.getRoute(), getMyApplication());
		String groupName = getMyApplication().getString(R.string.current_route);
		GpxDisplayGroup group = getMyApplication().getSelectedGpxHelper().buildGpxDisplayGroup(gpx, 0, groupName);
		if (group != null && group.getModifiableList().size() > 0) {
			gpxItem = group.getModifiableList().get(0);
			if (gpxItem != null) {
				gpxItem.route = true;
			}
		}
	}

	private void buildHeader(View headerView) {
		OsmandApplication app = getMyApplication();
		final LineChart mChart = (LineChart) headerView.findViewById(R.id.chart);
		GpxUiHelper.setupGPXChart(app, mChart, 4);
		mChart.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				listView.requestDisallowInterceptTouchEvent(true);
				return false;
			}
		});

		GPXTrackAnalysis analysis = gpx.getAnalysis(0);
		if (analysis.hasElevationData) {
			List<ILineDataSet> dataSets = new ArrayList<>();
			elevationDataSet = GpxUiHelper.createGPXElevationDataSet(app, mChart, analysis,
					GPXDataSetAxisType.DISTANCE, false, true);
			if (elevationDataSet != null) {
				dataSets.add(elevationDataSet);
			}
			slopeDataSet = GpxUiHelper.createGPXSlopeDataSet(app, mChart, analysis,
					GPXDataSetAxisType.DISTANCE, elevationDataSet.getValues(), true, true);
			if (slopeDataSet != null) {
				dataSets.add(slopeDataSet);
			}
			LineData data = new LineData(dataSets);
			mChart.setData(data);

			mChart.setOnChartGestureListener(new OnChartGestureListener() {

				float highlightDrawX = -1;

				@Override
				public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
					if (mChart.getHighlighted() != null && mChart.getHighlighted().length > 0) {
						highlightDrawX = mChart.getHighlighted()[0].getDrawX();
					} else {
						highlightDrawX = -1;
					}
				}

				@Override
				public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {
					gpxItem.chartMatrix = new Matrix(mChart.getViewPortHandler().getMatrixTouch());
					Highlight[] highlights = mChart.getHighlighted();
					if (highlights != null && highlights.length > 0) {
						gpxItem.chartHighlightPos = highlights[0].getX();
					} else {
						gpxItem.chartHighlightPos = -1;
					}
				}

				@Override
				public void onChartLongPressed(MotionEvent me) {
				}

				@Override
				public void onChartDoubleTapped(MotionEvent me) {
				}

				@Override
				public void onChartSingleTapped(MotionEvent me) {
				}

				@Override
				public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {
				}

				@Override
				public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
				}

				@Override
				public void onChartTranslate(MotionEvent me, float dX, float dY) {
					if (highlightDrawX != -1) {
						Highlight h = mChart.getHighlightByTouchPoint(highlightDrawX, 0f);
						if (h != null) {
							mChart.highlightValue(h);
						}
					}
				}
			});

			mChart.setVisibility(View.VISIBLE);
		} else {
			elevationDataSet = null;
			slopeDataSet = null;
			mChart.setVisibility(View.GONE);
		}
		((TextView) headerView.findViewById(R.id.average_text))
				.setText(OsmAndFormatter.getFormattedAlt(analysis.avgElevation, app));

		String min = OsmAndFormatter.getFormattedAlt(analysis.minElevation, app);
		String max = OsmAndFormatter.getFormattedAlt(analysis.maxElevation, app);
		((TextView) headerView.findViewById(R.id.range_text))
				.setText(min + " - " + max);

		String asc = OsmAndFormatter.getFormattedAlt(analysis.diffElevationUp, app);
		String desc = OsmAndFormatter.getFormattedAlt(analysis.diffElevationDown, app);
		((TextView) headerView.findViewById(R.id.descent_text)).setText(desc);
		((TextView) headerView.findViewById(R.id.ascent_text)).setText(asc);

		((ImageView) headerView.findViewById(R.id.average_icon))
				.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_altitude_average));
		((ImageView) headerView.findViewById(R.id.range_icon))
				.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_altitude_average));
		((ImageView) headerView.findViewById(R.id.descent_icon))
				.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_altitude_descent));
		((ImageView) headerView.findViewById(R.id.ascent_icon))
				.setImageDrawable(app.getUIUtilities().getThemedIcon(R.drawable.ic_action_altitude_ascent));

		headerView.findViewById(R.id.details_view).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				openDetails();
			}
		});
	}

	void openDetails() {
		if (gpxItem != null) {
			LatLon location = null;
			WptPt wpt = null;
			gpxItem.chartTypes = new GPXDataSetType[]{GPXDataSetType.ALTITUDE, GPXDataSetType.SLOPE};
			if (gpxItem.chartHighlightPos != -1) {
				TrkSegment segment = gpx.tracks.get(0).segments.get(0);
				if (segment != null) {
					float distance = gpxItem.chartHighlightPos * elevationDataSet.getDivX();
					for (WptPt p : segment.points) {
						if (p.distance >= distance) {
							wpt = p;
							break;
						}
					}
					if (wpt != null) {
						location = new LatLon(wpt.lat, wpt.lon);
					}
				}
			}

			if (location == null) {
				location = new LatLon(gpxItem.locationStart.lat, gpxItem.locationStart.lon);
			}
			if (wpt != null) {
				gpxItem.locationOnMap = wpt;
			} else {
				gpxItem.locationOnMap = gpxItem.locationStart;
			}

			final MapActivity activity = (MapActivity) getActivity();
			if (activity != null) {
				dismiss();

				final OsmandSettings settings = activity.getMyApplication().getSettings();
				settings.setMapLocationToShow(location.getLatitude(), location.getLongitude(),
						settings.getLastKnownMapZoom(),
						new PointDescription(PointDescription.POINT_TYPE_WPT, gpxItem.name),
						false,
						gpxItem);

				final MapRouteInfoMenu mapRouteInfoMenu = activity.getMapLayers().getMapControlsLayer().getMapRouteInfoMenu();
				if (MapRouteInfoMenu.isVisible()) {
					// We arrived here by the route info menu.
					// First, we close it and then show the details.
					mapRouteInfoMenu.setOnDismissListener(new OnDismissListener() {
						@Override
						public void onDismiss(DialogInterface dialog) {
							mapRouteInfoMenu.setOnDismissListener(null);
							MapActivity.launchMapActivityMoveToTop(activity);
						}
					});
					mapRouteInfoMenu.hide();
				} else {
					// We arrived here by the dashboard.
					MapActivity.launchMapActivityMoveToTop(activity);
				}
			}
		}
	}

	private void buildMenuButtons() {
		UiUtilities iconsCache = getMyApplication().getUIUtilities();
		ImageButton printRoute = (ImageButton) view.findViewById(R.id.print_route);
		printRoute.setImageDrawable(iconsCache.getThemedIcon(R.drawable.ic_action_gprint_dark));
		printRoute.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				print();
			}
		});

		ImageButton saveRoute = (ImageButton) view.findViewById(R.id.save_as_gpx);
		saveRoute.setImageDrawable(iconsCache.getThemedIcon(R.drawable.ic_action_gsave_dark));
		saveRoute.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				MapActivityActions.createSaveDirections(getActivity(), helper).show();
			}
		});

		ImageButton shareRoute = (ImageButton) view.findViewById(R.id.share_as_gpx);
		shareRoute.setImageDrawable(iconsCache.getThemedIcon(R.drawable.ic_action_gshare_dark));
		shareRoute.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				final String trackName = new SimpleDateFormat("yyyy-MM-dd_HH-mm_EEE", Locale.US).format(new Date());
				final GPXFile gpx = helper.generateGPXFileWithRoute(trackName);
				final Uri fileUri = AndroidUtils.getUriForFile(getMyApplication(), new File(gpx.path));
				File dir = new File(getActivity().getCacheDir(), "share");
				if (!dir.exists()) {
					dir.mkdir();
				}
				File dst = new File(dir, "route.gpx");
				try {
					FileWriter fw = new FileWriter(dst);
					GPXUtilities.writeGpx(fw, gpx);
					fw.close();
					final Intent sendIntent = new Intent();
					sendIntent.setAction(Intent.ACTION_SEND);
					sendIntent.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(generateHtml(adapter,
							helper.getGeneralRouteInformation()).toString()));
					sendIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_route_subject));
					sendIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
					sendIntent.putExtra(
							Intent.EXTRA_STREAM, AndroidUtils.getUriForFile(getMyApplication(), dst));
					sendIntent.setType("text/plain");
					sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
					startActivity(sendIntent);
				} catch (IOException e) {
					// Toast.makeText(getActivity(), "Error sharing favorites: " + e.getMessage(),
					// Toast.LENGTH_LONG).show();
					e.printStackTrace();
				}
			}
		});
	}

	public static void showDialog(FragmentManager fragmentManager) {
		ShowRouteInfoDialogFragment fragment = new ShowRouteInfoDialogFragment();
		fragment.show(fragmentManager, TAG);
	}

	class RouteInfoAdapter extends ArrayAdapter<RouteDirectionInfo> {
		public class CumulativeInfo {
			public int distance;
			public int time;

			public CumulativeInfo() {
				distance = 0;
				time = 0;
			}
		}

		private final int lastItemIndex;
		private boolean light;

		RouteInfoAdapter(List<RouteDirectionInfo> list) {
			super(getActivity(), R.layout.route_info_list_item, list);
			lastItemIndex = list.size() - 1;
			this.setNotifyOnChange(false);
			light = getMyApplication().getSettings().isLightContent();
		}


		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			if (row == null) {
				LayoutInflater inflater =
						(LayoutInflater) getMyApplication().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				row = inflater.inflate(R.layout.route_info_list_item, parent, false);
			}
			RouteDirectionInfo model = getItem(position);
			TextView label = (TextView) row.findViewById(R.id.description);
			TextView distanceLabel = (TextView) row.findViewById(R.id.distance);
			TextView timeLabel = (TextView) row.findViewById(R.id.time);
			TextView cumulativeDistanceLabel = (TextView) row.findViewById(R.id.cumulative_distance);
			TextView cumulativeTimeLabel = (TextView) row.findViewById(R.id.cumulative_time);
			ImageView icon = (ImageView) row.findViewById(R.id.direction);
			row.findViewById(R.id.divider).setVisibility(position == getCount() - 1 ? View.INVISIBLE : View.VISIBLE);

			TurnPathHelper.RouteDrawable drawable = new TurnPathHelper.RouteDrawable(getResources(), true);
			drawable.setColorFilter(new PorterDuffColorFilter(light ? getResources().getColor(R.color.icon_color) : Color.WHITE, PorterDuff.Mode.SRC_ATOP));
			drawable.setRouteType(model.getTurnType());
			icon.setImageDrawable(drawable);

			label.setText(String.valueOf(position + 1) + ". " + model.getDescriptionRoutePart());
			if (model.distance > 0) {
				distanceLabel.setText(OsmAndFormatter.getFormattedDistance(
						model.distance, getMyApplication()));
				timeLabel.setText(getTimeDescription(model));
				row.setContentDescription(label.getText() + " " + timeLabel.getText()); //$NON-NLS-1$
			} else {
				if (label.getText().equals(String.valueOf(position + 1) + ". ")) {
					label.setText(String.valueOf(position + 1) + ". " + getString((position != lastItemIndex) ? R.string.arrived_at_intermediate_point : R.string.arrived_at_destination));
				}
				distanceLabel.setText(""); //$NON-NLS-1$
				timeLabel.setText(""); //$NON-NLS-1$
				row.setContentDescription(""); //$NON-NLS-1$
			}
			CumulativeInfo cumulativeInfo = getRouteDirectionCumulativeInfo(position);
			cumulativeDistanceLabel.setText(OsmAndFormatter.getFormattedDistance(
					cumulativeInfo.distance, getMyApplication()));
			cumulativeTimeLabel.setText(Algorithms.formatDuration(cumulativeInfo.time, getMyApplication().accessibilityEnabled()));
			return row;
		}

		public CumulativeInfo getRouteDirectionCumulativeInfo(int position) {
			CumulativeInfo cumulativeInfo = new CumulativeInfo();
			for (int i = 0; i < position; i++) {
				RouteDirectionInfo routeDirectionInfo = (RouteDirectionInfo) getItem(i);
				cumulativeInfo.time += routeDirectionInfo.getExpectedTime();
				cumulativeInfo.distance += routeDirectionInfo.distance;
			}
			return cumulativeInfo;
		}
	}

	private String getTimeDescription(RouteDirectionInfo model) {
		final int timeInSeconds = model.getExpectedTime();
		return Algorithms.formatDuration(timeInSeconds, getMyApplication().accessibilityEnabled());
	}

	void print() {
		File file = generateRouteInfoHtml(adapter, helper.getGeneralRouteInformation());
		if (file.exists()) {
			Uri uri = AndroidUtils.getUriForFile(getMyApplication(), file);
			Intent browserIntent;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) { // use Android Print Framework
				browserIntent = new Intent(getActivity(), PrintDialogActivity.class)
						.setDataAndType(uri, "text/html");
			} else { // just open html document
				browserIntent = new Intent(Intent.ACTION_VIEW).setDataAndType(
						uri, "text/html");
			}
			browserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
			startActivity(browserIntent);
		}
	}

	private File generateRouteInfoHtml(RouteInfoAdapter routeInfo, String title) {
		File file = null;
		if (routeInfo == null) {
			return file;
		}

		final String fileName = "route_info.html";
		StringBuilder html = generateHtmlPrint(routeInfo, title);
		FileOutputStream fos = null;
		try {
			file = getMyApplication().getAppPath(fileName);
			fos = new FileOutputStream(file);
			fos.write(html.toString().getBytes("UTF-8"));
			fos.flush();
		} catch (IOException e) {
			file = null;
			e.printStackTrace();
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (Exception e) {
					file = null;
					e.printStackTrace();
				}
			}
		}

		return file;
	}

	private StringBuilder generateHtml(RouteInfoAdapter routeInfo, String title) {
		StringBuilder html = new StringBuilder();
		if (!TextUtils.isEmpty(title)) {
			html.append("<h1>");
			html.append(title);
			html.append("</h1>");
		}
		final String NBSP = "&nbsp;";
		final String BR = "<br>";
		for (int i = 0; i < routeInfo.getCount(); i++) {
			RouteDirectionInfo routeDirectionInfo = (RouteDirectionInfo) routeInfo.getItem(i);
			StringBuilder sb = new StringBuilder();
			sb.append(OsmAndFormatter.getFormattedDistance(routeDirectionInfo.distance, getMyApplication()));
			sb.append(", ").append(NBSP);
			sb.append(getTimeDescription(routeDirectionInfo));
			String distance = sb.toString().replaceAll("\\s", NBSP);
			String description = routeDirectionInfo.getDescriptionRoutePart();
			html.append(BR);
			html.append("<p>" + String.valueOf(i + 1) + ". " + NBSP + description + NBSP + "(" + distance + ")</p>");
		}
		return html;
	}

	private StringBuilder generateHtmlPrint(RouteInfoAdapter routeInfo, String title) {
		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">");
		html.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"en\">");
		html.append("<head>");
		html.append("<title>Route info</title>");
		html.append("<meta http-equiv=\"Content-type\" content=\"text/html; charset=utf-8\" />");
		html.append("<style>");
		html.append("table, th, td {");
		html.append("border: 1px solid black;");
		html.append("border-collapse: collapse;}");
		html.append("th, td {");
		html.append("padding: 5px;}");
		html.append("</style>");
		html.append("</head>");
		html.append("<body>");


		if (!TextUtils.isEmpty(title)) {
			html.append("<h1>");
			html.append(title);
			html.append("</h1>");
		}
		html.append("<table style=\"width:100%\">");
		final String NBSP = "&nbsp;";
		final String BR = "<br>";
		for (int i = 0; i < routeInfo.getCount(); i++) {
			RouteDirectionInfo routeDirectionInfo = (RouteDirectionInfo) routeInfo.getItem(i);
			html.append("<tr>");
			StringBuilder sb = new StringBuilder();
			sb.append(OsmAndFormatter.getFormattedDistance(routeDirectionInfo.distance, getMyApplication()));
			sb.append(", ");
			sb.append(getTimeDescription(routeDirectionInfo));
			String distance = sb.toString().replaceAll("\\s", NBSP);
			html.append("<td>");
			html.append(distance);
			html.append("</td>");
			String description = routeDirectionInfo.getDescriptionRoutePart();
			html.append("<td>");
			html.append(String.valueOf(i + 1) + ". " + description);
			html.append("</td>");
			RouteInfoAdapter.CumulativeInfo cumulativeInfo = routeInfo.getRouteDirectionCumulativeInfo(i);
			html.append("<td>");
			sb = new StringBuilder();
			sb.append(OsmAndFormatter.getFormattedDistance(cumulativeInfo.distance, getMyApplication()));
			sb.append(" - ");
			sb.append(OsmAndFormatter.getFormattedDistance(cumulativeInfo.distance + routeDirectionInfo.distance,
					getMyApplication()));
			sb.append(BR);
			sb.append(Algorithms.formatDuration(cumulativeInfo.time, getMyApplication().accessibilityEnabled()));
			sb.append(" - ");
			sb.append(Algorithms.formatDuration(cumulativeInfo.time + routeDirectionInfo.getExpectedTime(),
					getMyApplication().accessibilityEnabled()));
			String cumulativeTimeAndDistance = sb.toString().replaceAll("\\s", NBSP);
			html.append(cumulativeTimeAndDistance);
			html.append("</td>");
			html.append("</tr>");
		}
		html.append("</table>");
		html.append("</body>");
		html.append("</html>");
		return html;
	}

}
