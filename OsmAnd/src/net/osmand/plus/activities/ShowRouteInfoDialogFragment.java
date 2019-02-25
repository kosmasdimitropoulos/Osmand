package net.osmand.plus.activities;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.text.ClipboardManager;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.OverScroller;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.data.Entry;

import net.osmand.AndroidUtils;
import net.osmand.GPXUtilities;
import net.osmand.GPXUtilities.GPXFile;
import net.osmand.GPXUtilities.TrkSegment;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.Location;
import net.osmand.PlatformUtil;
import net.osmand.data.LatLon;
import net.osmand.data.PointDescription;
import net.osmand.data.TransportRoute;
import net.osmand.data.TransportStop;
import net.osmand.plus.GpxSelectionHelper;
import net.osmand.plus.GpxSelectionHelper.GpxDisplayGroup;
import net.osmand.plus.LockableScrollView;
import net.osmand.plus.OsmAndFormatter;
import net.osmand.plus.OsmAndLocationProvider;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.R;
import net.osmand.plus.TargetPointsHelper;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.base.BaseOsmAndFragment;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.helpers.FontCache;
import net.osmand.plus.helpers.GpxUiHelper;
import net.osmand.plus.helpers.GpxUiHelper.GPXDataSetType;
import net.osmand.plus.mapcontextmenu.InterceptorLinearLayout;
import net.osmand.plus.mapcontextmenu.MenuBuilder;
import net.osmand.plus.mapcontextmenu.MenuController;
import net.osmand.plus.routepreparationmenu.MapRouteInfoMenu;
import net.osmand.plus.routepreparationmenu.cards.BaseCard;
import net.osmand.plus.routepreparationmenu.cards.PublicTransportCard;
import net.osmand.plus.routepreparationmenu.cards.RouteInfoCard;
import net.osmand.plus.routepreparationmenu.cards.RouteStatisticCard;
import net.osmand.plus.routing.RouteDirectionInfo;
import net.osmand.plus.routing.RoutingHelper;
import net.osmand.plus.transport.TransportStopRoute;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.TurnPathHelper;
import net.osmand.plus.views.controls.HorizontalSwipeConfirm;
import net.osmand.plus.widgets.TextViewEx;
import net.osmand.router.RouteSegmentResult;
import net.osmand.router.RouteStatistics;
import net.osmand.router.RouteStatistics.Incline;
import net.osmand.router.TransportRoutePlanner;
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

import static net.osmand.plus.mapcontextmenu.MapContextMenuFragment.CURRENT_Y_UNDEFINED;
import static net.osmand.plus.mapcontextmenu.MapContextMenuFragment.ZOOM_PADDING_TOP_DP;
import static net.osmand.plus.mapcontextmenu.MenuBuilder.SHADOW_HEIGHT_TOP_DP;

public class ShowRouteInfoDialogFragment extends BaseOsmAndFragment {

	public static final String TAG = "ShowRouteInfoDialogFragment";

	private static final org.apache.commons.logging.Log log = PlatformUtil.getLog(ShowRouteInfoDialogFragment.class);

	private static final String ROUTE_ID_KEY = "route_id_key";

	public static class MenuState {
		public static final int HEADER_ONLY = 1;
		public static final int HALF_SCREEN = 2;
		public static final int FULL_SCREEN = 4;
	}

	private InterceptorLinearLayout mainView;
	private View view;
	private View.OnLayoutChangeListener containerLayoutListener;
	private View zoomButtonsView;
	private ImageButton myLocButtonView;

	private boolean portrait;
	private boolean nightMode;
	private boolean moving;
	private boolean forceUpdateLayout;
	private boolean initLayout = true;
	private boolean wasDrawerDisabled;

	private int minHalfY;
	private int topScreenPosY;
	private int menuFullHeightMax;
	private int menuBottomViewHeight;
	private int menuFullHeight;
	private int screenHeight;
	private int viewHeight;
	private int topShadowMargin;
	private int currentMenuState;
	private int shadowHeight;
	private int zoomButtonsHeight;
	private int zoomPaddingTop;

	private int routeId;

	private OsmandApplication app;
	private RoutingHelper helper;
	private GPXUtilities.GPXFile gpx;
	private GpxUiHelper.OrderedLineDataSet slopeDataSet;
	private GpxUiHelper.OrderedLineDataSet elevationDataSet;
	private GpxSelectionHelper.GpxDisplayItem gpxItem;
	private List<BaseCard> menuCards = new ArrayList<>();

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState) {
		app = getMyApplication();
		helper = app.getRoutingHelper();
		this.helper = app.getRoutingHelper();

		view = inflater.inflate(R.layout.route_info_layout, container, false);
		List<RouteDirectionInfo> routeDirections = helper.getRouteDirections();
		AndroidUtils.addStatusBarPadding21v(getActivity(), view);

		Bundle args = getArguments();
		if (args != null) {
			routeId = args.getInt(ROUTE_ID_KEY);
		}
		zoomPaddingTop = dpToPx(ZOOM_PADDING_TOP_DP);

		Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
		toolbar.setNavigationIcon(getMyApplication().getUIUtilities().getThemedIcon(R.drawable.ic_arrow_back));
		toolbar.setNavigationContentDescription(R.string.shared_string_close);
		toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});

		buildMenuButtons();

		currentMenuState = getInitialMenuState();
		LinearLayout cardsContainer = (LinearLayout) view.findViewById(R.id.route_menu_cards_container);
		AndroidUtils.setBackground(app, cardsContainer, nightMode, R.color.route_info_bg_light, R.color.route_info_bg_dark);

//		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//			@Override
//			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//				if (position < 2) {
//					return;
//				}
//				RouteDirectionInfo item = adapter.getItem(position - 2);
//				Location loc = helper.getLocationFromRouteDirection(item);
//				if (loc != null) {
//					MapRouteInfoMenu.directionInfo = position - 2;
//					OsmandSettings settings = getMyApplication().getSettings();
//					settings.setMapLocationToShow(loc.getLatitude(), loc.getLongitude(),
//							Math.max(13, settings.getLastKnownMapZoom()),
//							new PointDescription(PointDescription.POINT_TYPE_MARKER, item.getDescriptionRoutePart() + " " + getTimeDescription(item)),
//							false, null);
//					MapActivity.launchMapActivityMoveToTop(getActivity());
//					dismiss();
//				}
//			}
//		});

		if (routeId != -1) {
			List<TransportRoutePlanner.TransportRouteResult> routes = helper.getTransportRoutingHelper().getRoutes();
			if (routes != null && routes.size() > routeId) {
				long start = System.currentTimeMillis();
				TransportRoutePlanner.TransportRouteResult routeResult = helper.getTransportRoutingHelper().getRoutes().get(routeId);
				PublicTransportCard card = new PublicTransportCard(getMapActivity(), routeResult, routeId);
				menuCards.add(card);
				cardsContainer.addView(card.build(getMapActivity()));
				buildRowDivider(cardsContainer, false);
				buildTransportRouteRow(cardsContainer, routeResult, null, true);
				buildRowDivider(cardsContainer, false);
				long end = System.currentTimeMillis();
				log.debug("time = " + (end - start));
			}
		} else {
			makeGpx();
			if (gpx.hasAltitude) {
				RouteStatisticCard statisticCard = new RouteStatisticCard(getMapActivity(), gpx, new View.OnTouchListener() {
					@Override
					public boolean onTouch(View v, MotionEvent event) {
						mainView.requestDisallowInterceptTouchEvent(true);
						return false;
					}
				});
				menuCards.add(statisticCard);
				cardsContainer.addView(statisticCard.build(getMapActivity()));
				buildRowDivider(cardsContainer, false);
				slopeDataSet = statisticCard.getSlopeDataSet();
				elevationDataSet = statisticCard.getElevationDataSet();
				if (slopeDataSet != null) {
					List<Incline> inclines = createInclinesAndAdd100MetersWith0Incline(slopeDataSet.getValues());

					List<RouteSegmentResult> route = helper.getRoute().getOriginalRoute();
					if (route != null) {
						RouteStatistics routeStatistics = RouteStatistics.newRouteStatistic(route);

						RouteInfoCard routeClassCard = new RouteInfoCard(getMapActivity(), routeStatistics.getRouteClassStatistic());
						menuCards.add(routeClassCard);
						cardsContainer.addView(routeClassCard.build(app));
						buildRowDivider(cardsContainer, false);

						RouteInfoCard routeSurfaceCard = new RouteInfoCard(getMapActivity(), routeStatistics.getRouteSurfaceStatistic());
						menuCards.add(routeSurfaceCard);
						cardsContainer.addView(routeSurfaceCard.build(app));
						buildRowDivider(cardsContainer, false);

						RouteInfoCard routeSteepnessCard = new RouteInfoCard(getMapActivity(), routeStatistics.getRouteSteepnessStatistic(inclines));
						menuCards.add(routeSteepnessCard);
						cardsContainer.addView(routeSteepnessCard.build(app));
						buildRowDivider(cardsContainer, false);

						RouteInfoCard routeSmoothnessCard = new RouteInfoCard(getMapActivity(), routeStatistics.getRouteSmoothnessStatistic());
						menuCards.add(routeSmoothnessCard);
						cardsContainer.addView(routeSmoothnessCard.build(app));
						buildRowDivider(cardsContainer, false);
					}
				}
			}
		}
		for (int i = 0; i < routeDirections.size(); i++) {
			View view = getView(i, null, null, routeDirections.get(i), routeDirections);
			cardsContainer.addView(view);
		}

		final MapActivity mapActivity = requireMapActivity();
		processScreenHeight(container);

		portrait = AndroidUiHelper.isOrientationPortrait(mapActivity);
		topShadowMargin = AndroidUtils.dpToPx(mapActivity, 9f);

		shadowHeight = AndroidUtils.dpToPx(mapActivity, SHADOW_HEIGHT_TOP_DP);
		topScreenPosY = addStatusBarHeightIfNeeded(-shadowHeight);
		minHalfY = viewHeight - (int) (viewHeight * .75f);

		mainView = view.findViewById(R.id.main_view);
		nightMode = mapActivity.getMyApplication().getDaynightHelper().isNightModeForMapControls();

		// Zoom buttons
		zoomButtonsView = view.findViewById(R.id.map_hud_controls);
		ImageButton zoomInButtonView = (ImageButton) view.findViewById(R.id.map_zoom_in_button);
		ImageButton zoomOutButtonView = (ImageButton) view.findViewById(R.id.map_zoom_out_button);
		myLocButtonView = (ImageButton) view.findViewById(R.id.map_my_location_button);

		AndroidUtils.updateImageButton(app, zoomInButtonView, R.drawable.map_zoom_in, R.drawable.map_zoom_in_night,
				R.drawable.btn_circle_trans, R.drawable.btn_circle_night, nightMode);
		AndroidUtils.updateImageButton(app, zoomOutButtonView, R.drawable.map_zoom_out, R.drawable.map_zoom_out_night,
				R.drawable.btn_circle_trans, R.drawable.btn_circle_night, nightMode);
		zoomInButtonView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				doZoomIn();
			}
		});
		zoomOutButtonView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				doZoomOut();
			}
		});

		myLocButtonView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (OsmAndLocationProvider.isLocationPermissionAvailable(mapActivity)) {
					mapActivity.getMapViewTrackingUtilities().backToLocationImpl();
				} else {
					ActivityCompat.requestPermissions(mapActivity,
							new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
							OsmAndLocationProvider.REQUEST_LOCATION_PERMISSION);
				}
			}
		});
		updateMyLocation(mapActivity.getRoutingHelper());

		zoomButtonsView.setVisibility(View.VISIBLE);

		LockableScrollView bottomScrollView = (LockableScrollView) view.findViewById(R.id.route_menu_bottom_scroll);
		bottomScrollView.setScrollingEnabled(false);
		AndroidUtils.setBackground(app, bottomScrollView, nightMode, R.color.route_info_bg_light, R.color.route_info_bg_dark);

		AndroidUtils.setBackground(getMapActivity(), mainView, nightMode, R.drawable.bg_map_context_menu_light, R.drawable.bg_map_context_menu_dark);

		if (!portrait) {
			final TypedValue typedValueAttr = new TypedValue();
			mapActivity.getTheme().resolveAttribute(R.attr.left_menu_view_bg, typedValueAttr, true);
			mainView.setBackgroundResource(typedValueAttr.resourceId);
			mainView.setLayoutParams(new FrameLayout.LayoutParams(getResources().getDimensionPixelSize(R.dimen.dashboard_land_width), ViewGroup.LayoutParams.MATCH_PARENT));

			FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(AndroidUtils.dpToPx(mapActivity, 345f), ViewGroup.LayoutParams.WRAP_CONTENT);

			params.gravity = Gravity.BOTTOM;
		}

		runLayoutListener();

		final GestureDetector swipeDetector = new GestureDetector(getMapActivity(), new HorizontalSwipeConfirm(true));

		final View.OnTouchListener slideTouchListener = new View.OnTouchListener() {
			private float dy;
			private float dyMain;
			private float mDownY;

			private int minimumVelocity;
			private int maximumVelocity;
			private VelocityTracker velocityTracker;
			private OverScroller scroller;

			private boolean slidingUp;
			private boolean slidingDown;

			{
				scroller = new OverScroller(getMapActivity());
				final ViewConfiguration configuration = ViewConfiguration.get(getMapActivity());
				minimumVelocity = configuration.getScaledMinimumFlingVelocity();
				maximumVelocity = configuration.getScaledMaximumFlingVelocity();
			}

			@Override
			public boolean onTouch(View v, MotionEvent event) {

				if (!portrait) {
					if (swipeDetector.onTouchEvent(event)) {
						dismiss();

						recycleVelocityTracker();
						return true;
					}
				}

				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						mDownY = event.getRawY();
						dy = event.getY();
						dyMain = getViewY();

						initOrResetVelocityTracker();
						velocityTracker.addMovement(event);
						break;

					case MotionEvent.ACTION_MOVE:
						if (Math.abs(event.getRawY() - mDownY) > mainView.getTouchSlop()) {
							moving = true;
						}
						if (moving) {
							float y = event.getY();
							float newY = getViewY() + (y - dy);
							if (!portrait && newY > topScreenPosY) {
								newY = topScreenPosY;
							}
							setViewY((int) newY, false, false);

							ViewGroup.LayoutParams lp = mainView.getLayoutParams();
							lp.height = view.getHeight() - (int) newY + 10;
							mainView.setLayoutParams(lp);
							mainView.requestLayout();

							float newEventY = newY - (dyMain - dy);
							MotionEvent ev = MotionEvent.obtain(event.getDownTime(), event.getEventTime(), event.getAction(),
									event.getX(), newEventY, event.getMetaState());

							initVelocityTrackerIfNotExists();
							velocityTracker.addMovement(ev);
						}

						break;

					case MotionEvent.ACTION_UP:
						if (moving) {
							moving = false;
							int currentY = getViewY();

							final VelocityTracker velocityTracker = this.velocityTracker;
							velocityTracker.computeCurrentVelocity(1000, maximumVelocity);
							int initialVelocity = (int) velocityTracker.getYVelocity();

							if ((Math.abs(initialVelocity) > minimumVelocity)) {

								scroller.abortAnimation();
								scroller.fling(0, currentY, 0, initialVelocity, 0, 0,
										Math.min(viewHeight - menuFullHeightMax, getFullScreenTopPosY()),
										screenHeight,
										0, 0);
								currentY = scroller.getFinalY();
								scroller.abortAnimation();

								slidingUp = initialVelocity < -2000;
								slidingDown = initialVelocity > 2000;
							} else {
								slidingUp = false;
								slidingDown = false;
							}

							changeMenuState(currentY, slidingUp, slidingDown);
						}
						recycleVelocityTracker();
						break;
					case MotionEvent.ACTION_CANCEL:
						moving = false;
						recycleVelocityTracker();
						break;

				}
				return true;
			}

			private void initOrResetVelocityTracker() {
				if (velocityTracker == null) {
					velocityTracker = VelocityTracker.obtain();
				} else {
					velocityTracker.clear();
				}
			}

			private void initVelocityTrackerIfNotExists() {
				if (velocityTracker == null) {
					velocityTracker = VelocityTracker.obtain();
					velocityTracker.clear();
				}
			}

			private void recycleVelocityTracker() {
				if (velocityTracker != null) {
					velocityTracker.recycle();
					velocityTracker = null;
				}
			}
		};

		((InterceptorLinearLayout) mainView).setListener(slideTouchListener);
		mainView.setOnTouchListener(slideTouchListener);

		containerLayoutListener = new View.OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View view, int left, int top, int right, int bottom,
			                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
				if (forceUpdateLayout || bottom != oldBottom) {
					forceUpdateLayout = false;
					processScreenHeight(view.getParent());
					runLayoutListener();
				}
			}
		};

		runLayoutListener();

		return view;
	}

	private void updateMyLocation(RoutingHelper rh) {
		MapActivity mapActivity = getMapActivity();
		Location lastKnownLocation = mapActivity.getMyApplication().getLocationProvider().getLastKnownLocation();
		boolean enabled = lastKnownLocation != null;
		boolean tracked = mapActivity.getMapViewTrackingUtilities().isMapLinkedToLocation();

		if (!enabled) {
			myLocButtonView.setImageDrawable(getIcon(R.drawable.map_my_location, R.color.icon_color));
			AndroidUtils.setBackground(mapActivity, myLocButtonView, nightMode, R.drawable.btn_circle, R.drawable.btn_circle_night);
			myLocButtonView.setContentDescription(mapActivity.getString(R.string.unknown_location));
		} else if (tracked) {
			myLocButtonView.setImageDrawable(getIcon(R.drawable.map_my_location, R.color.color_myloc_distance));
			AndroidUtils.setBackground(mapActivity, myLocButtonView, nightMode, R.drawable.btn_circle, R.drawable.btn_circle_night);
		} else {
			myLocButtonView.setImageResource(R.drawable.map_my_location);
			AndroidUtils.setBackground(mapActivity, myLocButtonView, nightMode, R.drawable.btn_circle_blue, R.drawable.btn_circle_blue);
			myLocButtonView.setContentDescription(mapActivity.getString(R.string.map_widget_back_to_loc));
		}
		if (mapActivity.getMyApplication().accessibilityEnabled()) {
			myLocButtonView.setClickable(enabled && !tracked && rh.isFollowingMode());
		}
	}

	public void doZoomIn() {
		OsmandMapTileView map = getMapActivity().getMapView();
		if (map.isZooming() && map.hasCustomMapRatio()) {
			getMapActivity().changeZoom(2, System.currentTimeMillis());
		} else {
			getMapActivity().changeZoom(1, System.currentTimeMillis());
		}
	}

	public void doZoomOut() {
		getMapActivity().changeZoom(-1, System.currentTimeMillis());
	}

	public Drawable getCollapseIcon(boolean collapsed) {
		return app.getUIUtilities().getIcon(collapsed ? R.drawable.ic_action_arrow_down : R.drawable.ic_action_arrow_up,
				!nightMode ? R.color.ctx_menu_collapse_icon_color_light : R.color.ctx_menu_collapse_icon_color_dark);
	}

	private View buildSegmentItem(View view, TransportRoutePlanner.TransportRouteResultSegment segment, long startTime, View.OnClickListener listener) {
		TransportRoute transportRoute = segment.route;
		List<TransportStop> stops = segment.getForwardStops();
		TransportStop startStop = stops.get(0);
		TransportStopRoute transportStopRoute = TransportStopRoute.getTransportStopRoute(transportRoute, startStop);

		FrameLayout baseContainer = new FrameLayout(view.getContext());

		ImageView routeLine = new ImageView(view.getContext());
		FrameLayout.LayoutParams routeLineParams = new FrameLayout.LayoutParams(dpToPx(8f), ViewGroup.LayoutParams.MATCH_PARENT);
		routeLineParams.gravity = Gravity.START;
		routeLineParams.setMargins(dpToPx(24), dpToPx(14), dpToPx(22), dpToPx(28));
		routeLine.setLayoutParams(routeLineParams);
		int bgColor = transportStopRoute.getColor(app, nightMode);
		routeLine.setBackgroundColor(bgColor);
		baseContainer.addView(routeLine);

		LinearLayout stopsContainer = new LinearLayout(view.getContext());
		stopsContainer.setOrientation(LinearLayout.VERTICAL);
		baseContainer.addView(stopsContainer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		int drawableResId = transportStopRoute.type == null ? R.drawable.ic_action_bus_dark : transportStopRoute.type.getResourceId();
		Drawable icon = getContentIcon(drawableResId);

		String text = OsmAndFormatter.getFormattedTime(startTime, false);

		buildRow(stopsContainer, icon, text, transportStopRoute, new SpannableStringBuilder(startStop.getName()), getString(R.string.sit_on_the_stop) + ":", false, null,
				false, null, false, true, false, null, R.drawable.border_round_solid_light);

		MenuBuilder.CollapsableView collapsableView = null;
		if (stops.size() > 2) {
			collapsableView = getCollapsableTransportStopRoutesView(getMapActivity(), transportStopRoute, stops.subList(1, stops.size() - 1));
		}
		SpannableStringBuilder spannable = new SpannableStringBuilder("~");
		int startIndex = spannable.length();
		spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(app, R.color.secondary_text_light)), 0, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		spannable.append(OsmAndFormatter.getFormattedDuration(segment.getArrivalTime(), app));
		spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), startIndex, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		startIndex = spannable.length();
		spannable.append(" • ");
		spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(app, R.color.secondary_text_light)), startIndex, startIndex + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		startIndex = spannable.length();
		if (stops.size() > 2) {
			spannable.append(String.valueOf(stops.size())).append(" ").append(getString(R.string.transport_stops));
		}
		spannable.append(" • ").append(OsmAndFormatter.getFormattedDistance((float) segment.getTravelDist(), app));
		spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), startIndex, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

		String textType = "By " + transportStopRoute.type.name().toLowerCase() + ":";
		buildRow(stopsContainer, null, null, null, spannable, textType, true, collapsableView,
				false, null, false, false, false, null, 0);

		TransportStop endStop = stops.get(stops.size() - 1);
		long depTime = segment.depTime + segment.getArrivalTime();
		if (depTime <= 0) {
			depTime = startTime + segment.getArrivalTime();
		}
		String textTime = OsmAndFormatter.getFormattedTime(depTime, false);

		buildRow(stopsContainer, icon, textTime, null, new SpannableStringBuilder(endStop.getName()), getString(R.string.exit_at_the_stop) + ":", false, null,
				false, null, false, true, false, null, R.drawable.border_round_solid_light);

		((ViewGroup) view).addView(baseContainer);

		return stopsContainer;
	}

	private View createImagesContainer() {
		LinearLayout imagesContainer = new LinearLayout(view.getContext());
		FrameLayout.LayoutParams imagesContainerParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
		imagesContainer.setPadding(dpToPx(16), dpToPx(12), dpToPx(24), 0);
		imagesContainer.setOrientation(LinearLayout.VERTICAL);
		imagesContainer.setLayoutParams(imagesContainerParams);
		return imagesContainer;
	}

	private View createInfoContainer() {
		LinearLayout infoContainer = new LinearLayout(view.getContext());
		FrameLayout.LayoutParams infoContainerParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		infoContainer.setOrientation(LinearLayout.VERTICAL);
		infoContainer.setLayoutParams(infoContainerParams);
		return infoContainer;
	}

	private void buildTransportRouteRow(ViewGroup parent, TransportRoutePlanner.TransportRouteResult routeResult, View.OnClickListener listener, boolean showDivider) {
		TargetPointsHelper targetPointsHelper = app.getTargetPointsHelper();
		TargetPointsHelper.TargetPoint startPoint = targetPointsHelper.getPointToStart();
		TargetPointsHelper.TargetPoint endPoint = targetPointsHelper.getPointToNavigate();
		long startTime = System.currentTimeMillis() / 1000;

		List<TransportRoutePlanner.TransportRouteResultSegment> segments = routeResult.getSegments();
		boolean previousWalkItemUsed = false;

		for (int i = 0; i < segments.size(); i++) {
			TransportRoutePlanner.TransportRouteResultSegment segment = segments.get(i);
			long walkTime = (long) getWalkTime(segment.walkDist, routeResult.getWalkSpeed());

			if (i == 0) {
				buildStartItem(parent, startPoint, startTime, segment, routeResult.getWalkSpeed(), listener);
				startTime += walkTime;
			} else if (segment.walkDist > 0 && !previousWalkItemUsed) {
				SpannableStringBuilder spannable = new SpannableStringBuilder("~");
				int startIndex = spannable.length();
				spannable.append(OsmAndFormatter.getFormattedDuration((int) walkTime, app)).append(" ");
				spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), startIndex, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(app, R.color.primary_text_light)), startIndex, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				spannable.append(getString(R.string.on_the_foot)).append(" ").append(", ").append(OsmAndFormatter.getFormattedDistance((float) segment.walkDist, app));

				Drawable icon = getIcon(R.drawable.ic_action_pedestrian_dark, nightMode ? R.color.ctx_menu_bottom_view_url_color_dark : R.color.ctx_menu_bottom_view_url_color_light);

				buildRow(parent, icon, null, null, spannable, null, false,
						null, false, null, false, true, false, null, 0);

				buildRowDivider(parent, true);
				startTime += walkTime;
			}

			buildSegmentItem(parent, segment, startTime, listener);

			double finishWalkDist = routeResult.getFinishWalkDist();
			if (i == segments.size() - 1) {
				buildDestinationItem(parent, endPoint, startTime, segment, routeResult.getWalkSpeed(), listener);
			} else if (finishWalkDist > 0) {
				double walkTime2 = getWalkTime(finishWalkDist, routeResult.getWalkSpeed());
				startTime += walkTime2;
				if (i < segments.size() - 1) {
					TransportRoutePlanner.TransportRouteResultSegment nextSegment = segments.get(i + 1);
					if (nextSegment.walkDist > 0) {
						finishWalkDist += nextSegment.walkDist;
						walkTime2 += getWalkTime(nextSegment.walkDist, routeResult.getWalkSpeed());
						previousWalkItemUsed = true;
					} else {
						previousWalkItemUsed = false;
					}
				}
				buildRowDivider(parent, true);

				SpannableStringBuilder spannable = new SpannableStringBuilder("~");
				int startIndex = spannable.length();
				spannable.append(OsmAndFormatter.getFormattedDuration((int) walkTime2, app)).append(" ");
				spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), startIndex, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(app, R.color.primary_text_light)), startIndex, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				spannable.append(getString(R.string.on_the_foot)).append(" ").append(", ").append(OsmAndFormatter.getFormattedDistance((float) finishWalkDist, app));

				Drawable icon = getIcon(R.drawable.ic_action_pedestrian_dark, nightMode ? R.color.ctx_menu_bottom_view_url_color_dark : R.color.ctx_menu_bottom_view_url_color_light);

				buildRow(parent, icon, null, null, spannable, null, false,
						null, false, null, false, false, false, null, 0);
			}
			if (showDivider && i != segments.size() - 1) {
				buildRowDivider(parent, true);
			}
		}
	}

	private View buildStartItem(View view, TargetPointsHelper.TargetPoint start, long startTime, TransportRoutePlanner.TransportRouteResultSegment segment, double walkSpeed, View.OnClickListener listener) {
		FrameLayout baseItemView = new FrameLayout(view.getContext());

		LinearLayout imagesContainer = (LinearLayout) createImagesContainer();
		baseItemView.addView(imagesContainer);

		LinearLayout infoContainer = (LinearLayout) createInfoContainer();
		baseItemView.addView(infoContainer);

		String name;
		if (start != null) {
			name = start.getOnlyName().length() > 0 ? start.getOnlyName() :
					(getString(R.string.route_descr_map_location) + " " + getRoutePointDescription(start.getLatitude(), start.getLongitude()));
		} else {
			name = getString(R.string.shared_string_my_location);
		}
		String text = OsmAndFormatter.getFormattedTime(startTime, false);

		int drawableId = start == null ? R.drawable.ic_action_location_color : R.drawable.list_startpoint;
		Drawable icon = app.getUIUtilities().getIcon(drawableId);

		buildRow(infoContainer, icon, text, null, new SpannableStringBuilder(name), null, false, null, false,
				null, false, false, true, imagesContainer, R.drawable.border_round_solid_light);

		addWalkRouteIcon(imagesContainer);

		buildRowDivider(infoContainer, true);

		long walkTime = (long) getWalkTime(segment.walkDist, walkSpeed);

		SpannableStringBuilder spannable = new SpannableStringBuilder("~");
		int startIndex = spannable.length();
		spannable.append(OsmAndFormatter.getFormattedDuration((int) walkTime, app)).append(" ");
		spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), startIndex, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(app, R.color.primary_text_light)), startIndex, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		spannable.append(getString(R.string.on_the_foot)).append(" ").append(", ").append(OsmAndFormatter.getFormattedDistance((float) segment.walkDist, app));

		icon = getIcon(R.drawable.ic_action_pedestrian_dark, nightMode ? R.color.ctx_menu_bottom_view_url_color_dark : R.color.ctx_menu_bottom_view_url_color_light);

		buildRow(infoContainer, icon, null, null, spannable, null, false, null, false,
				null, false, false, false, imagesContainer, 0);

		buildRowDivider(infoContainer, true);

		((ViewGroup) view).addView(baseItemView);

		return baseItemView;
	}

	private void addWalkRouteIcon(LinearLayout container) {
		ImageView walkLineImage = new ImageView(view.getContext());
		walkLineImage.setImageDrawable(app.getUIUtilities().getIcon(R.drawable.walk_route_line));
		LinearLayout.LayoutParams walkImageLayoutParams = new LinearLayout.LayoutParams(dpToPx(10), dpToPx(14));
		walkImageLayoutParams.setMargins(dpToPx(7), dpToPx(8), 0, dpToPx(8));
		walkLineImage.setLayoutParams(walkImageLayoutParams);
		container.addView(walkLineImage);
	}

	private View buildDestinationItem(View view, TargetPointsHelper.TargetPoint destination, long startTime, TransportRoutePlanner.TransportRouteResultSegment segment, double walkSpeed, View.OnClickListener listener) {
		FrameLayout baseItemView = new FrameLayout(view.getContext());

		LinearLayout imagesContainer = (LinearLayout) createImagesContainer();
		baseItemView.addView(imagesContainer);

		LinearLayout infoContainer = (LinearLayout) createInfoContainer();
		baseItemView.addView(infoContainer);

		buildRowDivider(infoContainer, true);

		long walkTime = (long) getWalkTime(segment.walkDist, walkSpeed);

		SpannableStringBuilder spannable = new SpannableStringBuilder("~");
		int startIndex = spannable.length();
		spannable.append(OsmAndFormatter.getFormattedDuration((int) walkTime, app)).append(" ");
		spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), startIndex, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		spannable.setSpan(new ForegroundColorSpan(ContextCompat.getColor(app, R.color.primary_text_light)), startIndex, spannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		spannable.append(getString(R.string.on_the_foot)).append(" ").append(", ").append(OsmAndFormatter.getFormattedDistance((float) segment.walkDist, app));

		Drawable icon = getIcon(R.drawable.ic_action_pedestrian_dark, nightMode ? R.color.ctx_menu_bottom_view_url_color_dark : R.color.ctx_menu_bottom_view_url_color_light);

		buildRow(infoContainer, icon, null, null, spannable, null, false, null, false,
				null, false, false, false, imagesContainer, R.drawable.border_round_solid_light_small);

		buildRowDivider(infoContainer, true);

		addWalkRouteIcon(imagesContainer);

		String name = getRoutePointDescription(destination.point, destination.getOnlyName());
		String text = OsmAndFormatter.getFormattedTime(startTime + walkTime, false);

		buildRow(infoContainer, app.getUIUtilities().getIcon(R.drawable.list_destination), text, null, new SpannableStringBuilder(name), getString(R.string.route_descr_destination) + ":", false, null, false,
				null, false, false, false, imagesContainer, 0);

		((ViewGroup) view).addView(baseItemView);

		return baseItemView;
	}

	public View buildRow(final View view, Drawable icon, String timeText, TransportStopRoute transportStopRoute, final SpannableStringBuilder text,
	                     String secondaryText, boolean collapsable, final MenuBuilder.CollapsableView collapsableView, boolean isUrl,
	                     View.OnClickListener onClickListener, boolean intermediate, boolean big, boolean startItem, LinearLayout imagesContainer, int iconBgRes) {

		FrameLayout baseItemView = new FrameLayout(view.getContext());
		FrameLayout.LayoutParams baseViewLayoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		baseItemView.setLayoutParams(baseViewLayoutParams);

		LinearLayout baseView = new LinearLayout(view.getContext());
		baseView.setOrientation(LinearLayout.VERTICAL);

		baseView.setLayoutParams(baseViewLayoutParams);
		baseView.setGravity(Gravity.END);
		baseView.setBackgroundResource(AndroidUtils.resolveAttribute(view.getContext(), android.R.attr.selectableItemBackground));

		baseItemView.addView(baseView);

		LinearLayout ll = new LinearLayout(view.getContext());
		ll.setOrientation(LinearLayout.HORIZONTAL);
		LinearLayout.LayoutParams llParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		ll.setMinimumHeight(dpToPx(intermediate ? 36 : 48));
		ll.setPadding(dpToPx(64), 0, dpToPx(16), 0);
		ll.setLayoutParams(llParams);
		ll.setBackgroundResource(AndroidUtils.resolveAttribute(view.getContext(), android.R.attr.selectableItemBackground));
		ll.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				copyToClipboard(text.toString(), view.getContext());
				return true;
			}
		});
		baseView.addView(ll);

		// Icon
		if (icon != null) {
			ImageView iconView = new ImageView(view.getContext());
			iconView.setImageDrawable(icon);
			int iconSize = intermediate ? 22 : big ? 28 : 24;
			FrameLayout.LayoutParams imageViewLayoutParams = new FrameLayout.LayoutParams(dpToPx(iconSize), dpToPx(iconSize));
			imageViewLayoutParams.gravity = intermediate ? Gravity.CENTER_VERTICAL : Gravity.TOP;
			iconView.setLayoutParams(imageViewLayoutParams);
			iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

			if (imagesContainer != null) {
				imagesContainer.addView(iconView);
			} else {
				imageViewLayoutParams.setMargins(big ? dpToPx(14) : intermediate ? dpToPx(17) : dpToPx(16), intermediate ? 0 : dpToPx(8), 0, 0);
				iconView.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
				iconView.setBackgroundResource(iconBgRes);
				baseItemView.addView(iconView);
			}
		}

		// Text
		LinearLayout llText = new LinearLayout(view.getContext());
		llText.setOrientation(LinearLayout.VERTICAL);
		LinearLayout.LayoutParams llTextViewParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
		llTextViewParams.weight = 1f;
		llTextViewParams.gravity = Gravity.CENTER_VERTICAL;
		llText.setLayoutParams(llTextViewParams);
		ll.addView(llText);

		// Secondary text
		if (!TextUtils.isEmpty(secondaryText)) {
			TextViewEx textViewSecondary = new TextViewEx(view.getContext());
			LinearLayout.LayoutParams llTextSecondaryParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			llTextSecondaryParams.setMargins(0, dpToPx(8), 0, 0);
			textViewSecondary.setLayoutParams(llTextSecondaryParams);
			textViewSecondary.setTypeface(FontCache.getRobotoRegular(view.getContext()));
			textViewSecondary.setTextSize(14);
			AndroidUtils.setTextSecondaryColor(app, textViewSecondary, nightMode);
			textViewSecondary.setText(secondaryText);
			llText.addView(textViewSecondary);
		}

		// Primary text
		TextView titleView = new TextView(view.getContext());
		FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		titleParams.gravity = Gravity.CENTER_VERTICAL;
		titleView.setLayoutParams(titleParams);
		titleView.setTextSize(16);
		AndroidUtils.setTextPrimaryColor(app, titleView, nightMode);

		int linkTextColor = ContextCompat.getColor(view.getContext(), !nightMode ? R.color.ctx_menu_bottom_view_url_color_light : R.color.ctx_menu_bottom_view_url_color_dark);

		if (isUrl) {
			titleView.setTextColor(linkTextColor);
		}
		titleView.setText(text);
		llText.addView(titleView);

		if (!TextUtils.isEmpty(timeText)) {
			TextView timeView = new TextView(view.getContext());
			FrameLayout.LayoutParams timeViewParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			timeViewParams.gravity = Gravity.END | Gravity.TOP;
			timeViewParams.setMargins(0, startItem ? dpToPx(13) : dpToPx(8), 0, 0);
			timeView.setLayoutParams(timeViewParams);
			timeView.setPadding(0, 0, dpToPx(16), 0);
			timeView.setTextSize(16);
			timeView.setTextColor(app.getResources().getColor(!nightMode ? R.color.ctx_menu_bottom_view_text_color_light : R.color.ctx_menu_bottom_view_text_color_dark));

			timeView.setText(timeText);
			baseItemView.addView(timeView);
		}

		if (transportStopRoute != null) {
			TextView routeTypeView = new TextView(view.getContext());
			LinearLayout.LayoutParams routeTypeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			routeTypeParams.setMargins(0, dpToPx(6), 0, 0);
			routeTypeView.setLayoutParams(routeTypeParams);
			routeTypeView.setTextSize(16);
			AndroidUtils.setTextSecondaryColor(app, routeTypeView, nightMode);
			routeTypeView.setText(getString(R.string.layer_route) + ":");
			llText.addView(routeTypeView);

			View routeBadge = PublicTransportCard.createRouteBadge(getMapActivity(), transportStopRoute, nightMode);
			LinearLayout.LayoutParams routeBadgeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			routeBadgeParams.setMargins(0, dpToPx(6), 0, dpToPx(12));
			routeBadge.setLayoutParams(routeBadgeParams);
			llText.addView(routeBadge);
		}

		final ImageView iconViewCollapse = new ImageView(view.getContext());
		if (collapsable && collapsableView != null) {
			// Icon
			LinearLayout llIconCollapse = new LinearLayout(view.getContext());
			llIconCollapse.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(48f)));
			llIconCollapse.setOrientation(LinearLayout.HORIZONTAL);
			llIconCollapse.setGravity(Gravity.CENTER_VERTICAL);
			ll.addView(llIconCollapse);

			LinearLayout.LayoutParams llIconCollapseParams = new LinearLayout.LayoutParams(dpToPx(24f), dpToPx(24f));
			llIconCollapseParams.setMargins(0, dpToPx(12f), 0, dpToPx(12f));
			llIconCollapseParams.gravity = Gravity.CENTER_VERTICAL;
			iconViewCollapse.setLayoutParams(llIconCollapseParams);
			iconViewCollapse.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
			iconViewCollapse.setImageDrawable(getCollapseIcon(collapsableView.getContenView().getVisibility() == View.GONE));
			llIconCollapse.addView(iconViewCollapse);
			ll.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					LinearLayout contentView = (LinearLayout) collapsableView.getContenView();
					if (contentView.getVisibility() == View.VISIBLE) {
						contentView.setVisibility(View.GONE);
						iconViewCollapse.setImageDrawable(getCollapseIcon(true));
						collapsableView.setCollapsed(true);
						contentView.getChildAt(contentView.getChildCount() - 1).setVisibility(View.VISIBLE);
					} else {
						contentView.setVisibility(View.VISIBLE);
						iconViewCollapse.setImageDrawable(getCollapseIcon(false));
						collapsableView.setCollapsed(false);
					}
				}
			});
			if (collapsableView.isCollapsed()) {
				collapsableView.getContenView().setVisibility(View.GONE);
				iconViewCollapse.setImageDrawable(getCollapseIcon(true));
			}
			baseView.addView(collapsableView.getContenView());
		}

		if (onClickListener != null) {
			ll.setOnClickListener(onClickListener);
		} else if (isUrl) {
			ll.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setData(Uri.parse(text.toString()));
					v.getContext().startActivity(intent);
				}
			});
		}

		((LinearLayout) view).addView(baseItemView);

		return ll;
	}

	private MenuBuilder.CollapsableView getCollapsableTransportStopRoutesView(final Context context, TransportStopRoute transportStopRoute, List<TransportStop> stops) {
		LinearLayout view = (LinearLayout) buildCollapsableContentView(context, false, false);
		int drawableResId = transportStopRoute.type == null ? R.drawable.ic_action_bus_dark : transportStopRoute.type.getResourceId();
		Drawable icon = getContentIcon(drawableResId);
		for (int i = 0; i < stops.size(); i++) {
			buildRow(view, icon, null, null, new SpannableStringBuilder(stops.get(i).getName()), null,
					false, null, true, null, true, false, false, null, R.drawable.border_round_solid_light_small);
		}
		return new MenuBuilder.CollapsableView(view, null, false);
	}

	protected LinearLayout buildCollapsableContentView(Context context, boolean collapsed, boolean needMargin) {
		final LinearLayout view = new LinearLayout(context);
		view.setOrientation(LinearLayout.VERTICAL);
		view.setVisibility(collapsed ? View.GONE : View.VISIBLE);
		LinearLayout.LayoutParams llParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		view.setLayoutParams(llParams);
		return view;
	}

	public String getRoutePointDescription(double lat, double lon) {
		return getString(R.string.route_descr_lat_lon, lat, lon);
	}

	public String getRoutePointDescription(LatLon l, String d) {
		if (d != null && d.length() > 0) {
			return d.replace(':', ' ');
		}
		if (l != null) {
			return getString(R.string.route_descr_lat_lon, l.getLatitude(), l.getLongitude());
		}
		return "";
	}

	private double getWalkTime(double walkDist, double walkSpeed) {
		return walkDist / walkSpeed;
	}

	public void buildRowDivider(View view, boolean needMargin) {
		View horizontalLine = new View(view.getContext());
		LinearLayout.LayoutParams llHorLineParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1f));
		llHorLineParams.gravity = Gravity.BOTTOM;
		if (needMargin) {
			llHorLineParams.setMargins(dpToPx(64), 0, 0, 0);
		}
		horizontalLine.setLayoutParams(llHorLineParams);
		horizontalLine.setBackgroundColor(app.getResources().getColor(!nightMode ? R.color.ctx_menu_bottom_view_divider_light : R.color.ctx_menu_bottom_view_divider_dark));
		((LinearLayout) view).addView(horizontalLine);
	}

	public int dpToPx(float dp) {
		return AndroidUtils.dpToPx(app, dp);
	}

	protected void copyToClipboard(String text, Context ctx) {
		((ClipboardManager) app.getSystemService(Activity.CLIPBOARD_SERVICE)).setText(text);
		Toast.makeText(ctx,
				ctx.getResources().getString(R.string.copied_to_clipboard) + ":\n" + text,
				Toast.LENGTH_SHORT).show();
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
					sendIntent.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(generateHtml(helper.getRouteDirections(),
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

	public class CumulativeInfo {
		public int distance;
		public int time;

		public CumulativeInfo() {
			distance = 0;
			time = 0;
		}
	}

	public View getView(int position, View convertView, ViewGroup parent, RouteDirectionInfo model, List<RouteDirectionInfo> directionsInfo) {
		View row = convertView;
		if (row == null) {
			LayoutInflater inflater =
					(LayoutInflater) getMyApplication().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			row = inflater.inflate(R.layout.route_info_list_item, parent, false);
		}
		TextView label = (TextView) row.findViewById(R.id.description);
		TextView distanceLabel = (TextView) row.findViewById(R.id.distance);
		TextView timeLabel = (TextView) row.findViewById(R.id.time);
		TextView cumulativeDistanceLabel = (TextView) row.findViewById(R.id.cumulative_distance);
		TextView cumulativeTimeLabel = (TextView) row.findViewById(R.id.cumulative_time);
		ImageView icon = (ImageView) row.findViewById(R.id.direction);
		row.findViewById(R.id.divider).setVisibility(position == directionsInfo.size() - 1 ? View.INVISIBLE : View.VISIBLE);

		TurnPathHelper.RouteDrawable drawable = new TurnPathHelper.RouteDrawable(getResources(), true);
		drawable.setColorFilter(new PorterDuffColorFilter(!nightMode ? getResources().getColor(R.color.icon_color) : Color.WHITE, PorterDuff.Mode.SRC_ATOP));
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
				label.setText(String.valueOf(position + 1) + ". " + getString((position != directionsInfo.size() - 1) ? R.string.arrived_at_intermediate_point : R.string.arrived_at_destination));
			}
			distanceLabel.setText(""); //$NON-NLS-1$
			timeLabel.setText(""); //$NON-NLS-1$
			row.setContentDescription(""); //$NON-NLS-1$
		}
		CumulativeInfo cumulativeInfo = getRouteDirectionCumulativeInfo(position, directionsInfo);
		cumulativeDistanceLabel.setText(OsmAndFormatter.getFormattedDistance(
				cumulativeInfo.distance, getMyApplication()));
		cumulativeTimeLabel.setText(Algorithms.formatDuration(cumulativeInfo.time, getMyApplication().accessibilityEnabled()));
		return row;
	}

	public CumulativeInfo getRouteDirectionCumulativeInfo(int position, List<RouteDirectionInfo> directionInfos) {
		CumulativeInfo cumulativeInfo = new CumulativeInfo();
		for (RouteDirectionInfo routeDirectionInfo : directionInfos) {
			cumulativeInfo.time += routeDirectionInfo.getExpectedTime();
			cumulativeInfo.distance += routeDirectionInfo.distance;
		}
		return cumulativeInfo;
	}

	private String getTimeDescription(RouteDirectionInfo model) {
		final int timeInSeconds = model.getExpectedTime();
		return Algorithms.formatDuration(timeInSeconds, getMyApplication().accessibilityEnabled());
	}

	void print() {
		File file = generateRouteInfoHtml(helper.getRouteDirections(), helper.getGeneralRouteInformation());
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

	private File generateRouteInfoHtml(List<RouteDirectionInfo> directionsInfo, String title) {
		File file = null;
		if (directionsInfo == null) {
			return file;
		}

		final String fileName = "route_info.html";
		StringBuilder html = generateHtmlPrint(directionsInfo, title);
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

	private StringBuilder generateHtml(List<RouteDirectionInfo> directionInfos, String title) {
		StringBuilder html = new StringBuilder();
		if (!TextUtils.isEmpty(title)) {
			html.append("<h1>");
			html.append(title);
			html.append("</h1>");
		}
		final String NBSP = "&nbsp;";
		final String BR = "<br>";
		for (int i = 0; i < directionInfos.size(); i++) {
			RouteDirectionInfo routeDirectionInfo = directionInfos.get(i);
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

	private StringBuilder generateHtmlPrint(List<RouteDirectionInfo> directionsInfo, String title) {
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
		for (int i = 0; i < directionsInfo.size(); i++) {
			RouteDirectionInfo routeDirectionInfo = directionsInfo.get(i);
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
			CumulativeInfo cumulativeInfo = getRouteDirectionCumulativeInfo(i, directionsInfo);
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

	private int getInitialMenuState() {
		return MenuState.FULL_SCREEN;
	}

	public boolean slideUp() {
		int v = currentMenuState;
		for (int i = 0; i < 2; i++) {
			v = v << 1;
			if ((v & getSupportedMenuStates()) != 0) {
				currentMenuState = v;
				return true;
			}
		}
		return false;
	}

	public boolean slideDown() {
		int v = currentMenuState;
		for (int i = 0; i < 2; i++) {
			v = v >> 1;
			if ((v & getSupportedMenuStates()) != 0) {
				currentMenuState = v;
				return true;
			}
		}
		return false;
	}

	public int getCurrentMenuState() {
		return currentMenuState;
	}

	public int getSupportedMenuStates() {
		if (!portrait) {
			return MenuState.FULL_SCREEN;
		} else {
			return getSupportedMenuStatesPortrait();
		}
	}

	protected int getSupportedMenuStatesPortrait() {
		return MenuState.HEADER_ONLY | MenuState.HALF_SCREEN | MenuState.FULL_SCREEN;
	}

	public static boolean showInstance(final MapActivity mapActivity, int routeId) {
		try {
			Bundle args = new Bundle();
			args.putInt(ROUTE_ID_KEY, routeId);

			ShowRouteInfoDialogFragment fragment = new ShowRouteInfoDialogFragment();
			fragment.setArguments(args);
			mapActivity.getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.routeMenuContainer, fragment, TAG)
					.addToBackStack(TAG)
					.commitAllowingStateLoss();

			return true;

		} catch (RuntimeException e) {
			return false;
		}
	}

	@Nullable
	private MapActivity getMapActivity() {
		FragmentActivity activity = getActivity();
		if (activity instanceof MapActivity) {
			return (MapActivity) activity;
		} else {
			return null;
		}
	}

	@NonNull
	private MapActivity requireMapActivity() {
		FragmentActivity activity = getActivity();
		if (!(activity instanceof MapActivity)) {
			throw new IllegalStateException("Fragment " + this + " not attached to an activity.");
		}
		return (MapActivity) activity;
	}

	@Override
	public void onResume() {
		super.onResume();

		ViewParent parent = view.getParent();
		if (parent != null && containerLayoutListener != null) {
			((View) parent).addOnLayoutChangeListener(containerLayoutListener);
		}
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			mapActivity.getMapLayers().getMapControlsLayer().showMapControlsIfHidden();
			wasDrawerDisabled = mapActivity.isDrawerDisabled();
			if (!wasDrawerDisabled) {
				mapActivity.disableDrawer();
			}
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		if (view != null) {
			ViewParent parent = view.getParent();
			if (parent != null && containerLayoutListener != null) {
				((View) parent).removeOnLayoutChangeListener(containerLayoutListener);
			}
		}
		MapActivity mapActivity = getMapActivity();
		if (!wasDrawerDisabled && mapActivity != null) {
			mapActivity.enableDrawer();
		}
	}

	@Override
	public int getStatusBarColorId() {
		if (view != null) {
			if (Build.VERSION.SDK_INT >= 23) {
				view.setSystemUiVisibility(view.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
			}
			return nightMode ? R.color.dialog_divider_dark : R.color.dialog_divider_light;
		}
		return -1;
	}

	private int getViewY() {
		return (int) mainView.getY();
	}

	private void setViewY(int y, boolean animated, boolean adjustMapPos) {
		mainView.setY(y);
		zoomButtonsView.setY(getZoomButtonsY(y));
	}

	private void updateZoomButtonsVisibility(int menuState) {
		if (menuState == MenuController.MenuState.HEADER_ONLY) {
			if (zoomButtonsView.getVisibility() != View.VISIBLE) {
				zoomButtonsView.setVisibility(View.VISIBLE);
			}
		} else {
			if (zoomButtonsView.getVisibility() == View.VISIBLE) {
				zoomButtonsView.setVisibility(View.INVISIBLE);
			}
		}
	}

	private void processScreenHeight(ViewParent parent) {
		View container = (View) parent;
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			screenHeight = container.getHeight() + AndroidUtils.getStatusBarHeight(mapActivity);
			viewHeight = screenHeight - AndroidUtils.getStatusBarHeight(mapActivity);
		}
	}

	private int getFullScreenTopPosY() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			return topShadowMargin + mapActivity.getResources().getDimensionPixelSize(R.dimen.dashboard_map_toolbar);
		} else {
			return 0;
		}
	}

	private int addStatusBarHeightIfNeeded(int res) {
		MapActivity mapActivity = getMapActivity();
		if (Build.VERSION.SDK_INT >= 21 && mapActivity != null) {
			return res + AndroidUtils.getStatusBarHeight(mapActivity);
		}
		return res;
	}

	private int getHeaderOnlyTopY() {
		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			if (!menuCards.isEmpty()) {
				return viewHeight - menuCards.get(0).getViewHeight() - shadowHeight;
			} else {
				return viewHeight - AndroidUtils.dpToPx(mapActivity, 48f) - shadowHeight;
			}
		} else {
			return 0;
		}
	}

	private int getMenuStatePosY(int menuState) {
		if (!portrait) {
			return topScreenPosY;
		}
		switch (menuState) {
			case MenuState.HEADER_ONLY:
				return getHeaderOnlyTopY();
			case MenuState.HALF_SCREEN:
				return minHalfY;
			case MenuState.FULL_SCREEN:
				return getFullScreenTopPosY();
			default:
				return 0;
		}
	}

	private void changeMenuState(int currentY, boolean slidingUp, boolean slidingDown) {
		boolean needCloseMenu = false;

		int currentMenuState = getCurrentMenuState();
		if (portrait) {
			int headerDist = Math.abs(currentY - getMenuStatePosY(MenuState.HEADER_ONLY));
			int halfDist = Math.abs(currentY - getMenuStatePosY(MenuState.HALF_SCREEN));
			int fullDist = Math.abs(currentY - getMenuStatePosY(MenuState.FULL_SCREEN));
			int newState;
			if (headerDist < halfDist && headerDist < fullDist) {
				newState = MenuState.HEADER_ONLY;
			} else if (halfDist < headerDist && halfDist < fullDist) {
				newState = MenuState.HALF_SCREEN;
			} else {
				newState = MenuState.FULL_SCREEN;
			}

			if (slidingDown && currentMenuState == MenuState.FULL_SCREEN && getViewY() < getFullScreenTopPosY()) {
				slidingDown = false;
				newState = MenuState.FULL_SCREEN;
			}
			if (menuBottomViewHeight > 0 && slidingUp) {
				while (getCurrentMenuState() != newState) {
					if (!slideUp()) {
						break;
					}
				}
			} else if (slidingDown) {
				if (currentMenuState == MenuState.HEADER_ONLY) {
					needCloseMenu = true;
				} else {
					while (getCurrentMenuState() != newState) {
						if (!slideDown()) {
							needCloseMenu = true;
							break;
						}
					}
				}
			} else {
				if (currentMenuState < newState) {
					while (getCurrentMenuState() != newState) {
						if (!slideUp()) {
							break;
						}
					}
				} else {
					while (getCurrentMenuState() != newState) {
						if (!slideDown()) {
							break;
						}
					}
				}
			}
		}
		int newMenuState = getCurrentMenuState();
		boolean needMapAdjust = currentMenuState != newMenuState && newMenuState != MenuState.FULL_SCREEN;

		applyPosY(currentY, needCloseMenu, needMapAdjust, currentMenuState, newMenuState, 0);
	}


	private int getPosY(final int currentY, boolean needCloseMenu, int previousState) {
		if (needCloseMenu) {
			return screenHeight;
		}
		MapActivity mapActivity = getMapActivity();
		if (mapActivity == null) {
			return 0;
		}

		int destinationState = getCurrentMenuState();
		updateZoomButtonsVisibility(destinationState);

		int posY = 0;
		switch (destinationState) {
			case MenuState.HEADER_ONLY:
				posY = getMenuStatePosY(MenuState.HEADER_ONLY);
				break;
			case MenuState.HALF_SCREEN:
				posY = getMenuStatePosY(MenuState.HALF_SCREEN);
				break;
			case MenuState.FULL_SCREEN:
				if (currentY != CURRENT_Y_UNDEFINED) {
					int maxPosY = viewHeight - menuFullHeightMax;
					int minPosY = getMenuStatePosY(MenuState.FULL_SCREEN);
					if (maxPosY > minPosY) {
						maxPosY = minPosY;
					}
					if (currentY > minPosY || previousState != MenuState.FULL_SCREEN) {
						posY = minPosY;
					} else if (currentY < maxPosY) {
						posY = maxPosY;
					} else {
						posY = currentY;
					}
				} else {
					posY = getMenuStatePosY(MenuState.FULL_SCREEN);
				}
				break;
			default:
				break;
		}

		return posY;
	}

	private void updateMainViewLayout(int posY) {
		MapActivity mapActivity = getMapActivity();
		if (view != null && mapActivity != null) {
			ViewGroup.LayoutParams lp = mainView.getLayoutParams();
			lp.height = view.getHeight() - posY;
			mainView.setLayoutParams(lp);
			mainView.requestLayout();
		}
	}

	private void applyPosY(final int currentY, final boolean needCloseMenu, boolean needMapAdjust,
	                       final int previousMenuState, final int newMenuState, int dZoom) {
		final int posY = getPosY(currentY, needCloseMenu, previousMenuState);
		if (getViewY() != posY || dZoom != 0) {
			if (posY < getViewY()) {
				updateMainViewLayout(posY);
			}

			mainView.animate().y(posY)
					.setDuration(200)
					.setInterpolator(new DecelerateInterpolator())
					.setListener(new AnimatorListenerAdapter() {

						boolean canceled = false;

						@Override
						public void onAnimationCancel(Animator animation) {
							canceled = true;
						}

						@Override
						public void onAnimationEnd(Animator animation) {
							if (!canceled) {
								if (needCloseMenu) {
									dismiss();
								} else {
									updateMainViewLayout(posY);
									if (previousMenuState != 0 && newMenuState != 0 && previousMenuState != newMenuState) {
										doAfterMenuStateChange(previousMenuState, newMenuState);
									}
								}
							}
						}
					}).start();

			zoomButtonsView.animate().y(getZoomButtonsY(posY))
					.setDuration(200)
					.setInterpolator(new DecelerateInterpolator())
					.start();
		}
	}

	private int getZoomButtonsY(int y) {
		return y - zoomButtonsHeight - shadowHeight;
	}

	private void doAfterMenuStateChange(int previousState, int newState) {
		runLayoutListener();
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void runLayoutListener() {
		if (view != null) {
			ViewTreeObserver vto = view.getViewTreeObserver();
			vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
				@Override
				public void onGlobalLayout() {
					if (view != null) {
						ViewTreeObserver obs = view.getViewTreeObserver();
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
							obs.removeOnGlobalLayoutListener(this);
						} else {
							obs.removeGlobalOnLayoutListener(this);
						}

						if (getActivity() == null) {
							return;
						}
						zoomButtonsHeight = zoomButtonsView.getHeight();

						menuFullHeight = mainView.getHeight();
						menuBottomViewHeight = menuFullHeight;

						menuFullHeightMax = view.findViewById(R.id.route_menu_cards_container).getHeight();

						if (!moving) {
							doLayoutMenu();
						}
						initLayout = false;
					}
				}
			});
		}
	}

	private void doLayoutMenu() {
		final int posY = getPosY(getViewY(), false, getCurrentMenuState());
		setViewY(posY, true, !initLayout);
		updateMainViewLayout(posY);
	}

	public void dismiss() {
		FragmentActivity activity = getActivity();
		if (activity != null) {
			try {
				activity.getSupportFragmentManager().popBackStack(TAG,
						FragmentManager.POP_BACK_STACK_INCLUSIVE);
			} catch (Exception e) {
				//
			}
		}
	}
}