<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { CompanyAction } from '../data/customer'
import type { ActionInfo, CompanyPermissions } from '../stores/adminPermissions'

const props = defineProps<{
  permissions: CompanyPermissions
  catalog: ActionInfo[]
  saving: boolean
  saved: boolean
  t: (source: string) => string
}>()

const emit = defineEmits<{
  save: [authorizationId: string, actions: CompanyAction[]]
}>()

const draft = ref<CompanyAction[]>([...props.permissions.actions])


watch(
  () => props.permissions.actions,
  (actions) => {
    draft.value = [...actions]
  },
)

const dirty = computed(
  () =>
    draft.value.length !== props.permissions.actions.length ||
    draft.value.some((action) => !props.permissions.actions.includes(action)),
)

const toggle = (action: CompanyAction, checked: boolean): void => {
  const next = new Set(draft.value)
  if (checked) {
    next.add(action)
    if (props.catalog.find((item) => item.action === action)?.write) next.add('READ')
  } else {
    next.delete(action)
    if (action === 'READ')
      for (const item of props.catalog) if (item.write) next.delete(item.action)
  }
  draft.value = [...next]
}
</script>

<template>
  <fieldset class="permission-editor">
    <legend>{{ permissions.companyName }}</legend>
    <label
      v-for="item in catalog"
      :key="item.action"
      class="permission-editor__option"
    >
      <input
        type="checkbox"
        :checked="draft.includes(item.action)"
        @change="toggle(item.action, ($event.target as HTMLInputElement).checked)"
      />
      <span>{{ t(item.action) }}</span>
    </label>
    <div class="permission-editor__footer">
      <button
        class="button"
        type="button"
        :disabled="!dirty || saving"
        @click="emit('save', permissions.authorizationId, [...draft])"
      >
        {{ saving ? t('Sparar…') : t('Spara ändring') }}
      </button>
      <span v-if="saved && !dirty" role="status">{{ t('Sparat') }}</span>
    </div>
  </fieldset>
</template>

<style scoped>
.permission-editor {
  border: 1px solid var(--border);
  border-radius: var(--radius-control);
  margin: 0 0 16px;
  padding: 12px 16px 16px;
}
.permission-editor legend {
  font-weight: 700;
  padding: 0 6px;
}
.permission-editor__option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}
.permission-editor__footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
</style>
