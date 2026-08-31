import { createRouter, createWebHistory } from "vue-router";
import DashboardView from "./views/DashboardView.vue";
import FleetView from "./views/FleetView.vue";
import TrackingView from "./views/TrackingView.vue";
import AnalyticsView from "./views/AnalyticsView.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      redirect: "/dashboard",
    },
    {
      path: "/dashboard",
      component: DashboardView,
      meta: { title: "Dashboard" },
    },
    {
      path: "/fleet",
      component: FleetView,
      meta: { title: "Fleet" },
    },
    {
      path: "/tracking",
      component: TrackingView,
      meta: { title: "Tracking" },
    },
    {
      path: "/analytics",
      component: AnalyticsView,
      meta: { title: "Analytics" },
    },
  ],
});
