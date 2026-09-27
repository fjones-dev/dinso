import { defineComponent } from 'vue'
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import PortalView from './views/PortalView.vue'

export type Page =
  | 'overview'
  | 'insurance'
  | 'events'
  | 'documents'
  | 'payments'
  | 'employees'
  | 'plans'
  | 'cases'
  | 'add-employee'
export type PortalRoute = 'PRIVATE' | 'COMPANY' | 'SYSTEM'

declare module 'vue-router' {
  interface RouteMeta {
    page?: Page
    portal?: PortalRoute
    roles?: string[]
  }
}

const LoginRoute = defineComponent({ render: () => null })

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'login', component: LoginRoute },
  {
    path: '/privat',
    name: 'private-overview',
    component: PortalView,
    meta: { portal: 'PRIVATE', page: 'overview' },
  },
  {
    path: '/privat/forsakringar',
    name: 'private-insurance',
    component: PortalView,
    meta: { portal: 'PRIVATE', page: 'insurance' },
  },
  {
    path: '/privat/handelser',
    name: 'private-events',
    component: PortalView,
    meta: { portal: 'PRIVATE', page: 'events' },
  },
  {
    path: '/privat/dokument',
    name: 'private-documents',
    component: PortalView,
    meta: { portal: 'PRIVATE', page: 'documents' },
  },
  {
    path: '/privat/utbetalningar',
    name: 'private-payments',
    component: PortalView,
    meta: { portal: 'PRIVATE', page: 'payments' },
  },
  {
    path: '/foretag',
    name: 'company-overview',
    component: PortalView,
    meta: { portal: 'COMPANY', page: 'overview' },
  },
  {
    path: '/foretag/medarbetare',
    name: 'company-employees',
    component: PortalView,
    meta: { portal: 'COMPANY', page: 'employees' },
  },
  {
    path: '/foretag/medarbetare/lagg-till',
    name: 'company-add-employee',
    component: PortalView,
    meta: { portal: 'COMPANY', page: 'add-employee' },
  },
  {
    path: '/foretag/avtal',
    name: 'company-plans',
    component: PortalView,
    meta: { portal: 'COMPANY', page: 'plans' },
  },
  {
    path: '/foretag/arenden',
    name: 'company-cases',
    component: PortalView,
    meta: { portal: 'COMPANY', page: 'cases' },
  },
  {
    path: '/foretag/dokument',
    name: 'company-documents',
    component: PortalView,
    meta: { portal: 'COMPANY', page: 'documents' },
  },
  {
    path: '/systemadmin',
    name: 'system-admin-overview',
    component: PortalView,
    meta: { portal: 'SYSTEM', page: 'overview', roles: ['SYSTEM_ADMIN'] },
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
