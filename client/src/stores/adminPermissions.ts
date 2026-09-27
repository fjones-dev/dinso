import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { CompanyAction } from '../data/customer'
import { activeLocale, translate } from '../i18n'
import { useDemoSessionStore } from './demoSession'

export type ActionInfo = { action: CompanyAction; write: boolean }
export type CompanyPermissions = {
  authorizationId: string
  companyId: string
  companyName: string
  actions: CompanyAction[]
}
export type ProfilePermissions = {
  id: string
  name: string
  description: string
  role: string
  companyPermissions: CompanyPermissions[]
}

const apiUrl = import.meta.env.VITE_API_URL?.replace(/\/$/, '') ?? ''
const t = (source: string): string => translate(activeLocale.value, source)

export const useAdminPermissionsStore = defineStore('admin-permissions', () => {
  const session = useDemoSessionStore()
  const catalog = ref<ActionInfo[]>([])
  const profiles = ref<ProfilePermissions[]>([])
  const loading = ref(false)
  const savingId = ref('')
  const savedId = ref('')
  const error = ref('')

  const headers = (): Record<string, string> => ({
    Authorization: `Bearer ${session.sessionToken}`,
    'Content-Type': 'application/json',
  })

  const load = async (): Promise<void> => {
    if (!session.sessionToken) return
    loading.value = true
    error.value = ''
    try {
      const response = await fetch(`${apiUrl}/api/admin/profiles`, { headers: headers() })
      if (!response.ok) throw new Error('Could not load permissions')
      const data = (await response.json()) as {
        actions: ActionInfo[]
        profiles: ProfilePermissions[]
      }
      catalog.value = data.actions
      profiles.value = data.profiles
    } catch {
      error.value = t('Behörigheterna kunde inte hämtas.')
    } finally {
      loading.value = false
    }
  }

  const save = async (authorizationId: string, actions: CompanyAction[]): Promise<void> => {
    savingId.value = authorizationId
    savedId.value = ''
    error.value = ''
    try {
      const response = await fetch(
        `${apiUrl}/api/admin/authorizations/${encodeURIComponent(authorizationId)}/actions`,
        { method: 'PUT', headers: headers(), body: JSON.stringify({ actions }) },
      )
      if (!response.ok) {
        const body = (await response.json().catch(() => null)) as { message?: string } | null
        throw new Error(body?.message ?? t('Behörigheterna kunde inte sparas.'))
      }
      const updated = (await response.json()) as CompanyPermissions
      for (const profile of profiles.value) {
        const index = profile.companyPermissions.findIndex(
          (item) => item.authorizationId === updated.authorizationId,
        )
        if (index >= 0) profile.companyPermissions.splice(index, 1, updated)
      }
      savedId.value = authorizationId
    } catch (cause) {
      error.value =
        cause instanceof Error ? cause.message : t('Behörigheterna kunde inte sparas.')
    } finally {
      savingId.value = ''
    }
  }

  return { catalog, profiles, loading, savingId, savedId, error, load, save }
})
