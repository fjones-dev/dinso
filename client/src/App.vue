<script setup lang="ts">
import { computed, ref, watch, watchEffect } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import customer from '@customer/config'
import faviconUrl from '@customer/favicon.svg'
import '@customer/theme.css'
import type { Portal, Profile } from './data/customer'
import { activeLocale, translate } from './i18n'
import AppHeader from './components/AppHeader.vue'
import PortalNav from './components/PortalNav.vue'
import LoginScreen from './components/LoginScreen.vue'
import { useDemoSessionStore } from './stores/demoSession'
import { useDemoPortalStore } from './stores/demoPortal'

const route = useRoute()
const router = useRouter()
const session = useDemoSessionStore()
const portal = useDemoPortalStore()
const selectedProfileId = ref('')
const locale = activeLocale
const loginError = ref('')
const useApi = import.meta.env.VITE_USE_API === 'true'
const apiUrl = import.meta.env.VITE_API_URL?.replace(/\/$/, '') ?? ''
const t = (source: string, values?: Record<string, string | number>): string =>
  translate(locale.value, source, values)

const routeNames: Record<Portal, Record<string, string | undefined>> = {
  PRIVATE: {
    overview: 'private-overview',
    insurance: 'private-insurance',
    events: 'private-events',
    documents: 'private-documents',
    payments: 'private-payments',
  },
  COMPANY: {
    overview: 'company-overview',
    employees: 'company-employees',
    plans: 'company-plans',
    cases: 'company-cases',
    documents: 'company-documents',
  },
  SYSTEM: { overview: 'system-admin-overview' },
}

const labels = computed(() => ({
  overview: t('Översikt'),
  insurance: t('Försäkringar'),
  events: t('Händelser'),
  documents: t('Dokument'),
  employees: t('Medarbetare'),
  plans: t('Avtal'),
  cases: t('Ärenden'),
  payments: t('Utbetalningar'),
  open: t('Öppna portal'),
}))
const selectedProfile = computed(
  () =>
    customer.profiles.find((item) => item.id === selectedProfileId.value) ??
    null,
)
const portals = computed(() => [
  {
    id: 'PRIVATE' as Portal,
    title: t('Individportalen'),
    description: t('Se pension, försäkringar, händelser och dokument.'),
    available:
      selectedProfile.value?.portals?.includes('PRIVATE') ??
      selectedProfile.value?.portal === 'PRIVATE',
  },
  {
    id: 'COMPANY' as Portal,
    title: t('Företagsportalen'),
    description: t('Administrera medarbetare, avtal och ärenden.'),
    available:
      selectedProfile.value?.portals?.includes('COMPANY') ??
      selectedProfile.value?.portal === 'COMPANY',
  },
  {
    id: 'SYSTEM' as Portal,
    title: t('Systemadmin'),
    description: t('Hantera vilka åtgärder varje profil får utföra i respektive företag.'),
    available:
      selectedProfile.value?.portals?.includes('SYSTEM') ??
      selectedProfile.value?.portal === 'SYSTEM',
  },
])
const loginProfiles = computed(() =>
  customer.profiles.map((item) => ({
    ...item,
    description: t(item.description),
    preview: t(item.preview),
  })),
)
const loginLabels = computed(() => ({
  heading: t('Välj person och portal'),
  intro: t(
    'Välj den person och portal du vill arbeta i.',
    { customer: customer.name },
  ),
  switchHint: t('Du kan byta portal eller person genom att logga ut.'),
  portalQuestion: t('Vilken portal vill du logga in i?'),
  portalLabel: t('Välj portal'),
  profileQuestion: t('Välj person'),
  profileLabel: t('Person'),
  chooseProfileFirst: t('Välj en person för att se tillgängliga portaler.'),
  chooseProfile: t('Välj person'),
  hint: t('Välj en person och sedan en portal för att fortsätta.'),
  open: labels.value.open,
}))
const portalLabel = computed(() =>
  t(
    session.activePortal === 'SYSTEM'
      ? 'Systemadmin'
      : session.isCompany
        ? 'Företag'
        : 'Privat',
  ),
)
const companies = computed(() =>
  session.isCompany ? session.availableCompanies : [],
)
const nav = computed<[string, string][]>(() =>
  session.activePortal === 'SYSTEM'
    ? [['overview', labels.value.overview]]
    : session.isCompany
      ? [
          ['overview', labels.value.overview],
          ['employees', labels.value.employees],
          ['plans', labels.value.plans],
          ['cases', labels.value.cases],
          ['documents', labels.value.documents],
        ]
      : [
          ['overview', labels.value.overview],
          ['insurance', labels.value.insurance],
          ['events', labels.value.events],
          ['documents', labels.value.documents],
          ['payments', labels.value.payments],
        ],
)
const activePage = computed(() =>
  route.meta.page === 'add-employee' ? 'employees' : (route.meta.page ?? 'overview'),
)

const roleLabel = (item: Profile): string =>
  t(
    item.role === 'SYSTEM_ADMIN'
      ? 'Systemadministratör'
      : item.role === 'COMPANY_VIEWER'
        ? 'Läsbehörighet'
        : item.role === 'COMPANY_ADMIN'
          ? 'Företagsadmin'
          : 'Privatkund',
  )
const selectPage = (page: string): void => {
  const routeName = session.activePortal
    ? routeNames[session.activePortal][page]
    : undefined
  if (routeName) void router.push({ name: routeName })
}
const login = async (selected: Profile): Promise<void> => {
  const portal = session.selectedPortal
  if (!portal) return
  loginError.value = ''
  let token = ''
  if (useApi) {
    try {
      const response = await fetch(`${apiUrl}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ profileId: selected.id }),
      })
      if (response.status === 403)
        throw new Error(t('Du har ingen behörighet till företagsportalen.'))
      if (!response.ok) throw new Error(t('Profilen kunde inte öppnas.'))
      token = ((await response.json()) as { token: string }).token
    } catch (error) {
      loginError.value =
        error instanceof Error
          ? error.message
          : t('Profilen kunde inte öppnas.')
      return
    }
  }
  session.start(selected, portal, token)
  await router.push({ name: routeNames[portal].overview })
}
const selectCompany = (companyId: string): void => {
  session.selectCompany(companyId)
  void portal.loadCompanyData()
}

const logout = async (): Promise<void> => {
  if (useApi && session.sessionToken)
    await fetch(`${apiUrl}/api/auth/logout`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${session.sessionToken}` },
    }).catch(() => undefined)
  session.end()
  portal.reset()
  selectedProfileId.value = ''
  loginError.value = ''
  await router.push({ name: 'login' })
}

watchEffect(() => {
  document.documentElement.lang = locale.value
  document.title = `${customer.name} — ${session.activePortal === 'SYSTEM' ? t('Systemadmin') : session.isCompany ? t('Företag') : t('Privat')}`
  const favicon = document.querySelector<HTMLLinkElement>('#customer-favicon')
  if (favicon) favicon.href = faviconUrl
})
watch(
  [() => session.profile, () => route.fullPath],
  ([profile]) => {
    if (!profile && route.name !== 'login') {
      void router.replace({ name: 'login' })
      return
    }
    if (!profile || route.name === 'login') return
    const portal = route.meta.portal
    const allowedPortals = profile.portals ?? [profile.portal]
    if (
      !portal ||
      !allowedPortals.includes(portal) ||
      (route.meta.roles &&
        !route.meta.roles.includes(profile.role) &&
        profile.role !== 'SYSTEM_ADMIN')
    )
      void router.replace({
        name: routeNames[session.activePortal ?? 'PRIVATE'].overview,
      })
  },
  { immediate: true },
)
watch(selectedProfileId, () => {
  session.selectedPortal = null
})
</script>

<template>
  <LoginScreen
    v-if="!session.profile"
    :customer-name="customer.name"
    :customer-icon-url="faviconUrl"
    :portals="portals"
    :selected-portal="session.selectedPortal"
    :profiles="loginProfiles"
    :selected-profile-id="selectedProfileId"
    :login-error="loginError"
    :role-label="roleLabel"
    :labels="loginLabels"
    @select-portal="session.selectedPortal = $event"
    @select-profile="selectedProfileId = $event"
    @submit="selectedProfile && login(selectedProfile)"
  />
  <div v-else class="app">
    <AppHeader
      :customer-name="customer.name"
      :customer-icon-url="faviconUrl"
      :portal-label="portalLabel"
      :company-label="t('Arbetsgivare')"
      :companies="companies"
      :selected-company-id="session.companyId || session.selectedCompanyName"
      :signed-in-as-label="t('Inloggad som')"
      :profile-name="session.profile.name"
      :sign-out-label="t('Logga ut')"
      @sign-out="logout"
      @select-company="selectCompany"
    />
    <main id="mainContent" class="shell" tabindex="-1">
      <PortalNav
        :items="nav"
        :active="activePage"
        @select="selectPage"
      />
      <RouterView />
      <div v-if="customer.locales.length > 1" class="language-switch">
        <label for="language">{{ t('Språk') }}</label>
        <select id="language" v-model="locale">
          <option value="sv">{{ t('Svenska') }}</option>
          <option value="en">{{ t('English') }}</option>
        </select>
      </div>
    </main>
  </div>
</template>

<style scoped>
.shell {
  max-width: var(--shell-max-width, 1440px);
  margin: auto;
  padding: var(--space-shell-padding, 32px 24px 72px);
}
.language-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 28px;
  color: var(--muted);
  font-size: 0.9rem;
}
.language-switch select {
  border: 1px solid var(--border);
  border-radius: var(--radius-control);
  padding: 6px 8px;
  background: var(--surface);
  color: var(--ink);
}
</style>
