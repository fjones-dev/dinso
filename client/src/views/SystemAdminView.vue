<script setup lang="ts">
import type { CompanyAction } from '../data/customer'
import type { ActionInfo, ProfilePermissions } from '../stores/adminPermissions'
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import Panel from '../components/Panel.vue'
import PermissionEditor from '../components/PermissionEditor.vue'

defineProps<{
  title: string
  description: string
  profiles: ProfilePermissions[]
  catalog: ActionInfo[]
  loading: boolean
  savingId: string
  savedId: string
  error: string
  t: (source: string) => string
}>()

const emit = defineEmits<{
  save: [authorizationId: string, actions: CompanyAction[]]
}>()
</script>

<template>
  <section>
    <PageHeader :title="title" :description="description" />
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <EmptyState
      v-if="!loading && profiles.length === 0"
      :title="t('Inga företagsprofiler')"
      :description="t('Det finns inga profiler att hantera i den här kundvarianten.')"
    />
    <Panel
      v-for="profile in profiles"
      :key="profile.id"
      class="profile-panel"
      :title="profile.name"
    >
      <p class="profile-description">{{ t(profile.description) }}</p>
      <PermissionEditor
        v-for="item in profile.companyPermissions"
        :key="item.authorizationId"
        :permissions="item"
        :catalog="catalog"
        :saving="savingId === item.authorizationId"
        :saved="savedId === item.authorizationId"
        :t="t"
        @save="(id, actions) => emit('save', id, actions)"
      />
    </Panel>
  </section>
</template>

<style scoped>
.profile-description {
  color: var(--muted);
  margin: 0 0 16px;
}
.form-error {
  background: var(--color-danger-surface);
  color: var(--color-danger);
  margin: 0 0 16px;
  padding: 12px;
}
.profile-panel {
  margin-bottom: 16px;
}
</style>
