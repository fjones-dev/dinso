import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import customer from '@customer/config'
import type { CompanyAction, Portal, Profile } from '../data/customer'

type Company = { id: string; name: string; actions?: CompanyAction[] }

type StoredSession = {
  profileId: string
  portal: Portal
  token: string
  companyId: string
}

const useApi = import.meta.env.VITE_USE_API === 'true'
const storageKey = `dinso:${customer.key}:demo-session`

const readStoredSession = (): StoredSession | null => {
  try {
    const stored = window.sessionStorage.getItem(storageKey)
    if (!stored) return null

    const value = JSON.parse(stored) as Partial<StoredSession>
    if (
      typeof value.profileId !== 'string' ||
      typeof value.portal !== 'string' ||
      !['PRIVATE', 'COMPANY', 'SYSTEM'].includes(value.portal) ||
      typeof value.token !== 'string' ||
      typeof value.companyId !== 'string'
    )
      return null

    return value as StoredSession
  } catch {
    return null
  }
}

export const useDemoSessionStore = defineStore('demo-session', () => {
  const storedSession = readStoredSession()
  const storedProfile = storedSession
    ? (customer.profiles.find((item) => item.id === storedSession.profileId) ?? null)
    : null
  const allowedPortals = storedProfile?.portals ?? (storedProfile ? [storedProfile.portal] : [])
  const canRestore = storedProfile && storedSession && allowedPortals.includes(storedSession.portal)
  const profile = ref<Profile | null>(canRestore ? storedProfile : null)
  const selectedPortal = ref<Portal | null>(
    canRestore ? storedSession.portal : null,
  )
  const sessionToken = ref(canRestore ? storedSession.token : '')
  const companyId = ref(canRestore ? storedSession.companyId : '')
  const companyName = ref(
    canRestore
      ? storedProfile.companies?.find((name) => name === storedSession.companyId) ??
          storedProfile.company ??
          ''
      : '',
  )
  const companies = ref<Company[]>(
    canRestore ? (storedProfile.companies ?? []).map((name) => ({ id: name, name })) : [],
  )

  const persist = (): void => {
    if (!profile.value || !selectedPortal.value) return
    window.sessionStorage.setItem(
      storageKey,
      JSON.stringify({
        profileId: profile.value.id,
        portal: selectedPortal.value,
        token: sessionToken.value,
        companyId: companyId.value,
      } satisfies StoredSession),
    )
  }

  if (!canRestore && storedSession) window.sessionStorage.removeItem(storageKey)

  const activePortal = computed<Portal | null>(
    () => profile.value?.portal ?? null,
  )
  const isCompany = computed<boolean>(() => activePortal.value === 'COMPANY')
  const selectedCompany = computed<Company | undefined>(() =>
    companies.value.find((item) => item.id === companyId.value),
  )

  const can = (action: CompanyAction): boolean =>
    !useApi || (selectedCompany.value?.actions ?? []).includes(action)
  const availableCompanies = computed<Company[]>(() =>
    companies.value.length > 0
      ? companies.value
      : (profile.value?.companies ?? []).map((name) => ({ id: name, name })),
  )
  const selectedCompanyName = computed<string>(
    () => companyName.value || profile.value?.company || '',
  )

  const start = (
    selectedProfile: Profile,
    portal: Portal,
    token = '',
  ): void => {
    profile.value = selectedProfile
    selectedPortal.value = portal
    sessionToken.value = token
    companyId.value = ''
    companyName.value = selectedProfile.company ?? ''
    companies.value = (selectedProfile.companies ?? []).map((name) => ({
      id: name,
      name,
    }))
    persist()
  }

  const end = (): void => {
    profile.value = null
    selectedPortal.value = null
    sessionToken.value = ''
    companyId.value = ''
    companyName.value = ''
    companies.value = []
    window.sessionStorage.removeItem(storageKey)
  }

  const setCompanies = (items: Company[]): void => {
    companies.value = items
    const selected =
      items.find((item) => item.id === companyId.value) ??
      items.find((item) => item.name === companyName.value) ??
      items[0]
    if (!selected) return
    companyId.value = selected.id
    companyName.value = selected.name
    persist()
  }

  const selectCompany = (id: string): void => {
    const company = availableCompanies.value.find((item) => item.id === id)
    if (!company) return
    companyId.value = company.id
    companyName.value = company.name
    persist()
  }

  return {
    profile,
    selectedPortal,
    sessionToken,
    companyId,
    companyName,
    companies,
    activePortal,
    isCompany,
    can,
    availableCompanies,
    selectedCompanyName,
    start,
    end,
    setCompanies,
    selectCompany,
  }
})
