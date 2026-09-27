<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import customer from '@customer/config'
import { activeLocale, translate } from '../i18n'
import { statusTone } from '../data/status'
import { useDemoSessionStore } from '../stores/demoSession'
import { useDemoPortalStore } from '../stores/demoPortal'
import { useAdminPermissionsStore } from '../stores/adminPermissions'
import OverviewView from './OverviewView.vue'
import InsuranceView from './InsuranceView.vue'
import EmployeesView from './EmployeesView.vue'
import PlansView from './PlansView.vue'
import CasesView from './CasesView.vue'
import ActivityView from './ActivityView.vue'
import SystemAdminView from './SystemAdminView.vue'
import AddEmployeeFlow from '../components/AddEmployeeFlow.vue'
import CaseModal from '../components/CaseModal.vue'
import DemoNotice from '../components/DemoNotice.vue'

const route = useRoute()
const router = useRouter()
const session = useDemoSessionStore()
const portal = useDemoPortalStore()
const admin = useAdminPermissionsStore()
const t = (source: string, values?: Record<string, string | number>): string =>
  translate(activeLocale.value, source, values)
const page = computed(() => route.meta.page ?? 'overview')
const selectedCase = ref<{
  id: string
  name: string
  status: string
  value: string
  detail: string
} | null>(null)
const isCompany = computed(() => session.isCompany)
const overviewTitle = computed(() =>
  isCompany.value
    ? session.selectedCompanyName
    : t('Hej {name}', { name: session.profile?.name.split(' ')[0] ?? '' }),
)
const activityTitle = computed(() =>
  page.value === 'documents'
    ? t('Dokument')
    : page.value === 'payments'
      ? t('Utbetalningar')
      : t('Händelser'),
)
const activityDescription = computed(() =>
  page.value === 'documents'
    ? t('Tillgängliga dokument med lokal metadata.')
    : page.value === 'payments'
      ? t('Kommande, pågående och avslutade utbetalningar.')
      : t('Senaste händelser och uppdateringar.'),
)
const activityRows = computed(() =>
  page.value === 'documents' ? portal.documentRows : portal.activityRows,
)
const systemAdminDescription = computed(() =>
  t('Hantera vilka åtgärder varje profil får utföra i respektive företag.'),
)
const overviewDescription = computed(() =>
  t(
    isCompany.value
      ? 'Följ planer, medarbetare och sådant som behöver hanteras.'
      : 'Din pension och dina försäkringar – samlade på ett ställe.',
  ),
)
const overviewBottomMetrics = computed(
  () => !isCompany.value && customer.privateOverviewMetricLayout === 'bottom-bar',
)
const overviewRows = computed(() =>
  isCompany.value ? portal.cases.slice(0, 5) : portal.insurance,
)
const overviewNextStep = computed(() => ({
  description: t(
    isCompany.value
      ? 'Granska den registrerade löneändringen innan den 18 september.'
      : 'Se hur ditt innehav är fördelat och justera fondvikter.',
  ),
  action: t(isCompany.value ? 'Visa ärenden' : 'Visa försäkringar'),
}))
const insuranceDescription = computed(() =>
  t('Detaljer om sparande, skydd och val för varje försäkring.'),
)
const employeeDescription = computed(() =>
  t('Sök och följ anställningar inom {company}.', {
    company: session.selectedCompanyName,
  }),
)
const caseModalLabels = computed(() => ({
  eyebrow: t('Ärende'),
  close: t('Stäng'),
  status: t('Status'),
  due: t('Förfaller'),
  details: t('Detaljer'),
  approve: t('Godkänn ärende'),
  approved: t('Godkänd'),
  readOnly: t('Du har läsbehörighet och kan inte godkänna ärenden.'),
}))
const activityPage = computed(
  () => page.value as 'events' | 'documents' | 'payments',
)

const employeeActions = computed(() => {
  const allowed: ('salary' | 'leave' | 'end')[] = []
  if (session.can('CHANGE_SALARY')) allowed.push('salary')
  if (session.can('REGISTER_LEAVE')) allowed.push('leave')
  if (session.can('END_EMPLOYMENT')) allowed.push('end')
  return allowed
})
const manageEmployee = (
  employee: Parameters<typeof portal.manageEmployee>[0],
): void => {
  portal.manageEmployee(employee)
  portal.employeeAction = employeeActions.value[0] ?? 'salary'
}
const navigate = (target: 'insurance' | 'cases'): void => {
  void router.push({
    name: target === 'insurance' ? 'private-insurance' : 'company-cases',
  })
}
const selectCase = (name: string): void => {
  selectedCase.value = portal.cases.find((item) => item.name === name) ?? null
}
const approveSelectedCase = async (): Promise<void> => {
  if (selectedCase.value) await portal.approveCase(selectedCase.value)
  selectedCase.value = null
}
onMounted(() => {
  if (session.activePortal === 'SYSTEM') void admin.load()
  else void (session.isCompany ? portal.loadCompanyData() : portal.loadFundAllocation())
})

const finishEmployee = async (draft: {
  name: string
  planId: string
  salary: number
  startsOn: string
}): Promise<string | undefined> => {
  const result = await portal.addEmployee(draft)
  if (!result) await router.push({ name: 'company-employees' })
  return result
}
</script>

<template>
  <SystemAdminView
    v-if="session.activePortal === 'SYSTEM'"
    :title="t('Systemadmin')"
    :description="systemAdminDescription"
    :profiles="admin.profiles"
    :catalog="admin.catalog"
    :loading="admin.loading"
    :saving-id="admin.savingId"
    :saved-id="admin.savedId"
    :error="admin.error"
    :t="t"
    @save="admin.save"
  />
  <OverviewView
    v-else-if="page === 'overview'"
    :is-company="isCompany"
    :title="overviewTitle"
    :description="overviewDescription"
    :metrics="portal.overviewMetrics"
    :bottom-metrics="overviewBottomMetrics"
    :rows="overviewRows"
    :next-step="overviewNextStep"
    :t="t"
    :status-tone="statusTone"
    @navigate="navigate"
    @open-case="selectCase($event.name)"
  />
  <InsuranceView
    v-else-if="page === 'insurance'"
    :title="t('Försäkringar')"
    :description="insuranceDescription"
    :insurance="portal.insurance"
    :allocation="portal.allocation"
    :confirmed="portal.confirmed"
    :error="portal.error"
    :max-funds="customer.rules.maxFunds"
    :t="t"
    :status-tone="statusTone"
    @update:allocation="portal.allocation = $event"
    @confirm="portal.confirmAllocation"
  />
  <EmployeesView
    v-else-if="page === 'employees'"
    :title="t('Medarbetare')"
    :description="employeeDescription"
    :employees="portal.filteredEmployees"
    :can-add="session.can('ADD_EMPLOYEE')"
    :allowed-actions="employeeActions"
    :search="portal.search"
    :selected-employee="portal.selectedEmployee"
    :employee-action="portal.employeeAction"
    :salary-draft="portal.salaryDraft"
    :leave-reason="portal.leaveReason"
    :leave-until="portal.leaveUntil"
    :end-date="portal.endDate"
    :leave-reasons="portal.leaveReasons"
    :saving="portal.saving"
    :error="portal.error"
    :t="t"
    :status-tone="statusTone"
    @update:search="portal.search = $event"
    @update:employee-action="portal.employeeAction = $event"
    @update:salary-draft="portal.salaryDraft = $event"
    @update:leave-reason="portal.leaveReason = $event"
    @update:leave-until="portal.leaveUntil = $event"
    @update:end-date="portal.endDate = $event"
    @manage="manageEmployee"
    @cancel="portal.selectedEmployee = null"
    @add="router.push({ name: 'company-add-employee' })"
    @save="portal.saveEmployee"
  />
  <AddEmployeeFlow
    v-else-if="page === 'add-employee'"
    :plans="portal.planOptions"
    :t="t"
    :on-submit="finishEmployee"
    @cancel="router.push({ name: 'company-employees' })"
  />
  <PlansView
    v-else-if="page === 'plans'"
    :title="t('Pensionsplaner')"
    :description="t('Avtal, premieflöden och anslutna medarbetare.')"
    :plans="portal.plans"
    :t="t"
  />
  <CasesView
    v-else-if="page === 'cases'"
    :title="t('Ärenden')"
    :cases="portal.cases"
    :t="t"
    :status-tone="statusTone"
    @open="selectCase($event.name)"
  />
  <ActivityView
    v-else
    :page="activityPage"
    :title="activityTitle"
    :description="activityDescription"
    :rows="activityRows"
    :t="t"
    :status-tone="statusTone"
  />
  <DemoNotice v-if="portal.message">{{ portal.message }}</DemoNotice>
  <CaseModal
    v-if="selectedCase"
    :item="selectedCase"
    :can-approve="session.can('APPROVE_CASE')"
    :labels="caseModalLabels"
    @close="selectedCase = null"
    @approve="approveSelectedCase"
  />
</template>
