<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import PageHeader from '../components/PageHeader.vue'
import Panel from '../components/Panel.vue'
import DataTable, { type DataTableColumn } from '../components/DataTable.vue'
import EmptyState from '../components/EmptyState.vue'
import StatusBadge from '../components/StatusBadge.vue'

type Employee = {
  id?: string
  name: string
  plan: string
  salary: string
  status: string
}
type LeaveReason = { code: string; label: string }
type EmployeeAction = 'salary' | 'leave' | 'end'

const props = defineProps<{
  title: string
  description: string
  employees: Employee[]
  canAdd: boolean
  allowedActions: EmployeeAction[]
  search: string
  selectedEmployee: Employee | null
  employeeAction: EmployeeAction
  salaryDraft: number
  leaveReason: string
  leaveUntil: string
  endDate: string
  leaveReasons: LeaveReason[]
  saving: boolean
  error: string
  t: (source: string, values?: Record<string, string | number>) => string
  statusTone: (
    status: string,
  ) => 'positive' | 'pending' | 'attention' | 'neutral'
}>()

const emit = defineEmits<{
  'update:search': [value: string]
  'update:employeeAction': [value: EmployeeAction]
  'update:salaryDraft': [value: number]
  'update:leaveReason': [value: string]
  'update:leaveUntil': [value: string]
  'update:endDate': [value: string]
  manage: [employee: Employee]
  cancel: []
  add: []
  save: []
}>()

const searchModel = computed({
  get: () => props.search,
  set: (value) => emit('update:search', value),
})
const actionModel = computed({
  get: () => props.employeeAction,
  set: (value) => emit('update:employeeAction', value),
})
const salaryModel = computed({
  get: () => props.salaryDraft,
  set: (value) => emit('update:salaryDraft', value),
})
const leaveReasonModel = computed({
  get: () => props.leaveReason,
  set: (value) => emit('update:leaveReason', value),
})
const leaveUntilModel = computed({
  get: () => props.leaveUntil,
  set: (value) => emit('update:leaveUntil', value),
})
const endDateModel = computed({
  get: () => props.endDate,
  set: (value) => emit('update:endDate', value),
})
const columns = computed<DataTableColumn[]>(() => [
  { key: 'name', label: props.t('Namn') },
  { key: 'plan', label: props.t('Plan') },
  { key: 'salary', label: props.t('Lön'), align: 'right' },
  { key: 'status', label: props.t('Status') },
  ...(props.allowedActions.length > 0
    ? [{ key: 'actions', label: props.t('Åtgärder'), align: 'right' as const }]
    : []),
])

const employeeActionPanel = ref<HTMLElement | null>(null)

async function manageEmployee(employee: Employee) {
  emit('manage', employee)
  await nextTick()
  employeeActionPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <section>
    <PageHeader :title="title" :description="description">
      <template v-if="canAdd" #action>
        <button class="button" @click="emit('add')">
          {{ t('Lägg till medarbetare') }}
        </button>
      </template>
    </PageHeader>
    <Panel>
      <input
        v-model="searchModel"
        class="search-input"
        :placeholder="t('Sök medarbetare')"
        :aria-label="t('Sök medarbetare')"
      />
      <EmptyState
        v-if="employees.length === 0"
        :title="t('Inga medarbetare matchar sökningen')"
        :description="
          t(
            'Prova ett annat namn, eller rensa sökfältet för att se alla medarbetare.',
          )
        "
      >
        <template #action>
          <button class="button secondary" @click="searchModel = ''">
            {{ t('Rensa sökning') }}
          </button>
        </template>
      </EmptyState>
      <DataTable
        v-else
        :columns="columns"
        :rows="employees"
      >
        <template #cell-name="{ row }">
          <strong>{{ (row as Employee).name }}</strong>
        </template>
        <template #cell-plan="{ row }">
          {{ t((row as Employee).plan) }}
        </template>
        <template #cell-status="{ row }">
          <StatusBadge
            :label="t((row as Employee).status)"
            :tone="statusTone((row as Employee).status)"
          />
        </template>
        <template #cell-actions="{ row }">
          <button
            class="table-link"
            type="button"
            @click="manageEmployee(row as Employee)"
          >
            {{ t('Hantera') }}
          </button>
        </template>
      </DataTable>
    </Panel>

    <div
      v-if="selectedEmployee && allowedActions.length > 0"
      ref="employeeActionPanel"
    >
      <Panel
        class="employee-action-panel"
        :title="t('Hantera {name}', { name: selectedEmployee.name })"
      >
      <p class="panel-lede">
        {{
          t(
            'Ändringar sparas för den valda anställningen.',
          )
        }}
      </p>
      <form class="employee-action-form" @submit.prevent="emit('save')">
        <label class="field">
          <span>{{ t('Åtgärd') }}</span>
          <select v-model="actionModel">
            <option v-if="allowedActions.includes('salary')" value="salary">
              {{ t('Ändra lön') }}
            </option>
            <option v-if="allowedActions.includes('leave')" value="leave">
              {{ t('Registrera tjänstledighet') }}
            </option>
            <option v-if="allowedActions.includes('end')" value="end">
              {{ t('Avsluta anställning') }}
            </option>
          </select>
        </label>
        <label v-if="employeeAction === 'salary'" class="field">
          <span>{{ t('Ny månadslön') }}</span>
          <input
            v-model.number="salaryModel"
            min="1"
            required
            type="number"
            inputmode="numeric"
          />
        </label>
        <template v-else-if="employeeAction === 'leave'">
          <label class="field">
            <span>{{ t('Orsak') }}</span>
            <select v-model="leaveReasonModel">
              <option
                v-for="reason in leaveReasons"
                :key="reason.code"
                :value="reason.code"
              >
                {{ t(reason.label) }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>{{ t('Tjänstledig till och med') }}</span>
            <input v-model="leaveUntilModel" required type="date" />
          </label>
        </template>
        <label v-else class="field">
          <span>{{ t('Sista anställningsdag') }}</span>
          <input v-model="endDateModel" required type="date" />
        </label>
        <p v-if="employeeAction === 'end'" class="destructive-note">
          {{ t('Kontrollera datumet innan du avslutar anställningen.') }}
        </p>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <div class="form-actions">
          <button
            class="button secondary"
            type="button"
            :disabled="saving"
            @click="emit('cancel')"
          >
            {{ t('Avbryt') }}
          </button>
          <button class="button" :disabled="saving" type="submit">
            {{ saving ? t('Sparar…') : t('Spara ändring') }}
          </button>
        </div>
      </form>
      </Panel>
    </div>
  </section>
</template>

<style scoped>
.table-link {
  border: 0;
  background: none;
  color: var(--color-primary);
  cursor: pointer;
  font: inherit;
  font-weight: 650;
  padding: 4px;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.search-input,
.field select,
.field input {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-control);
  box-sizing: border-box;
  font: inherit;
  min-height: 42px;
  padding: 8px 10px;
  width: 100%;
}
.search-input {
  margin-bottom: 16px;
  max-width: 360px;
}
.employee-action-panel {
  margin-top: 24px;
}
.panel-lede {
  color: var(--color-muted);
  margin: 0 0 16px;
}
.employee-action-form {
  display: grid;
  gap: 16px;
  max-width: 480px;
}
.field {
  display: grid;
  gap: 6px;
  font-weight: 650;
}
.destructive-note,
.form-error {
  margin: 0;
  padding: 12px;
}
.destructive-note {
  background: var(--color-danger-subtle);
  color: var(--color-danger);
}
.form-error {
  background: var(--color-danger-subtle);
  color: var(--color-danger);
}
.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
@media (max-width: 760px) {
  .search-input {
    max-width: none;
  }
  .form-actions .button {
    flex: 1;
  }
}
</style>
