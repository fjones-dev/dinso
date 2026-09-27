import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import customer from '@customer/config'
import type { CompanyAction } from '../data/customer'
import { activeLocale, translate } from '../i18n'
import { useDemoSessionStore } from './demoSession'

type Employee = {
  id?: string
  name: string
  plan: string
  salary: string
  status: string
}
type Case = {
  id: string
  name: string
  status: string
  value: string
  detail: string
}
type Row = {
  id: string
  title: string
  date: string
  detail: string
  status: string
}
type PrivateOverview = {
  insurances: { id: string; type: string }[]
}
type InsuranceDetail = {
  fundHoldings: { fundName: string; allocationPercent: string }[]
}
type Company = { id: string; name: string; actions: CompanyAction[] }
type CompanyEmployment = {
  id: string
  personName: string
  planName: string
  monthlySalary: string
  status: string
}
type CompanyCase = {
  id: string
  name: string
  status: string
  dueOn: string
  detail: string
}
type CompanyPlan = { id: string; name: string }
const useApi = import.meta.env.VITE_USE_API === 'true'
const apiUrl = import.meta.env.VITE_API_URL?.replace(/\/$/, '') ?? ''
const t = (source: string, values?: Record<string, string | number>): string =>
  translate(activeLocale.value, source, values)

export const useDemoPortalStore = defineStore('demo-portal', () => {
  const session = useDemoSessionStore()
  const allocation = ref(60)
  const confirmed = ref(false)
  const insurance = ref(customer.insurance.map((item) => ({ ...item })))
  const employees = ref<Employee[]>(
    customer.employments.map((item) => ({ ...item })),
  )
  const search = ref('')
  const selectedEmployee = ref<Employee | null>(null)
  const employeeAction = ref<'salary' | 'leave' | 'end'>('salary')
  const salaryDraft = ref(0)
  const leaveReason = ref('PARENTAL_LEAVE')
  const leaveUntil = ref('2026-11-30')
  const endDate = ref('2026-10-01')
  const saving = ref(false)
  const error = ref('')
  const message = ref('')
  const companyPlans = ref<CompanyPlan[]>([])
  const cases = ref<Case[]>([
    {
      id: 'salary-mio',
      name: 'Löneändring · Mio Sten',
      status: 'Att granska',
      value: '18 sep.',
      detail:
        'Mios månadslön har registrerats som 52 000 kr. Kontrollera uppgifterna och godkänn ändringen.',
    },
    {
      id: 'leave-nora',
      name: 'Tjänstledighet · Nora Holst',
      status: 'Pågående',
      value: '30 sep.',
      detail:
        'Begäran om tjänstledighet från 1 oktober till 30 november väntar på handläggning.',
    },
    {
      id: 'enrolment-ivar',
      name: 'Nyanslutning · Ivar Holm',
      status: 'Komplett',
      value: '20 sep.',
      detail: 'Anslutningen är komplett och redo för nästa premieflöde.',
    },
  ])
  const plans = computed(() =>
    session.selectedCompanyName.includes('Nord')
      ? [
          {
            name: 'Ledningspension',
            members: '3 anslutna',
            premium: '42 600 kr/mån',
          },
          {
            name: 'Flexpension',
            members: '3 anslutna',
            premium: '18 900 kr/mån',
          },
        ]
      : [
          {
            name: 'Flexpension 2024',
            members: '17 anslutna',
            premium: '78 420 kr/mån',
          },
          { name: 'ITP 1', members: '11 anslutna', premium: '54 870 kr/mån' },
        ],
  )
  const filteredEmployees = computed(() =>
    employees.value.filter((item) =>
      item.name.toLowerCase().includes(search.value.toLowerCase()),
    ),
  )
  const overviewMetrics = computed(() =>
    session.isCompany
      ? [
          {
            label: t('Försäkrade medarbetare'),
            value: String(employees.value.length),
          },
          {
            label: t('Pågående ärenden'),
            value: String(
              cases.value.filter(
                (item) => !['Komplett', 'Godkänd'].includes(item.status),
              ).length,
            ),
          },
          {
            label: t('Aktiva pensionsplaner'),
            value: String(plans.value.length),
          },
        ]
      : [
          { label: t('Totalt försäkringsvärde'), value: '2 126 900 kr' },
          { label: t('Nästa utbetalning'), value: '25 sep.' },
          {
            label: t('Aktiva försäkringar'),
            value: String(insurance.value.length),
          },
        ],
  )
  const activityRows = computed<Row[]>(() =>
    customer.transactions.map((item) => ({
      id: item.title,
      title: t(item.title),
      date: item.date,
      detail: item.amount,
      status: t(item.status),
    })),
  )
  const documentRows = computed<Row[]>(() =>
    customer.documents.map((item) => ({
      id: item.name,
      title: item.name,
      date: item.date,
      detail: t(item.kind),
      status: t('Tillgängligt'),
    })),
  )
  const leaveReasons = computed(() =>
    customer.rules.leaveTypes.map((reason) => ({
      code: reason === 'Föräldraledighet' ? 'PARENTAL_LEAVE' : 'STUDIES',
      label: reason,
    })),
  )
  const planOptions = computed(() =>
    useApi && companyPlans.value.length > 0
      ? companyPlans.value
      : plans.value.map((plan) => ({ id: plan.name, name: plan.name })),
  )
  const companyRequest = (path: string): string =>
    `${apiUrl}/api/company/${path}?companyId=${encodeURIComponent(session.companyId)}`
  const formatSalary = (salary: string): string =>
    `${Number(salary).toLocaleString(customer.defaultLocale === 'en' ? 'en-US' : 'sv-SE')} ${t('kr/mån')}`
  const toEmployee = (item: CompanyEmployment): Employee => ({
    id: item.id,
    name: item.personName,
    plan: item.planName,
    salary: formatSalary(item.monthlySalary),
    status: item.status,
  })
  const toCase = (item: CompanyCase): Case => ({
    id: item.id,
    name: item.name,
    status: item.status,
    value: item.dueOn,
    detail: item.detail,
  })

  const loadCompanyData = async (): Promise<void> => {
    if (!useApi || !session.isCompany || !session.sessionToken) return

    try {
      const headers = { Authorization: `Bearer ${session.sessionToken}` }
      const companiesResponse = await fetch(`${apiUrl}/api/company/companies`, { headers })
      if (!companiesResponse.ok) throw new Error('Could not load companies')
      session.setCompanies((await companiesResponse.json()) as Company[])

      const [employmentsResponse, casesResponse, plansResponse] = await Promise.all([
        fetch(companyRequest('employments'), { headers }),
        fetch(companyRequest('cases'), { headers }),
        fetch(companyRequest('plans'), { headers }),
      ])
      if (!employmentsResponse.ok || !casesResponse.ok || !plansResponse.ok)
        throw new Error('Could not load company data')
      employees.value = ((await employmentsResponse.json()) as CompanyEmployment[]).map(toEmployee)
      cases.value = ((await casesResponse.json()) as CompanyCase[]).map(toCase)
      companyPlans.value = (await plansResponse.json()) as CompanyPlan[]
    } catch {
      error.value = t('Medarbetaren kunde inte registreras.')
    }
  }

  const loadFundAllocation = async (): Promise<void> => {
    if (!useApi || !session.sessionToken) return

    try {
      const headers = { Authorization: `Bearer ${session.sessionToken}` }
      const overviewResponse = await fetch(`${apiUrl}/api/private/overview`, { headers })
      if (!overviewResponse.ok) throw new Error('Could not load fund allocation')
      const overview = (await overviewResponse.json()) as PrivateOverview
      const fundInsurance = overview.insurances.find((item) => item.type === 'FUND')
      if (!fundInsurance) return

      const insuranceResponse = await fetch(
        `${apiUrl}/api/private/insurances/${fundInsurance.id}`,
        { headers },
      )
      if (!insuranceResponse.ok) throw new Error('Could not load fund allocation')
      const insuranceDetail = (await insuranceResponse.json()) as InsuranceDetail
      const globalIndex = insuranceDetail.fundHoldings.find(
        (holding) => holding.fundName === 'Global Index',
      )
      if (globalIndex) allocation.value = Number(globalIndex.allocationPercent)
    } catch {
      error.value = t('Fondfördelningen kunde inte sparas.')
    }
  }

  const confirmAllocation = async (): Promise<void> => {
    confirmed.value = false
    error.value = ''
    if (!useApi) {
      confirmed.value = true
      return
    }

    try {
      const headers = {
        Authorization: `Bearer ${session.sessionToken}`,
        'Content-Type': 'application/json',
      }
      const overviewResponse = await fetch(`${apiUrl}/api/private/overview`, { headers })
      if (!overviewResponse.ok) throw new Error('Could not load fund insurance')
      const overview = (await overviewResponse.json()) as PrivateOverview
      const fundInsurance = overview.insurances.find((item) => item.type === 'FUND')
      if (!fundInsurance) throw new Error('Fund insurance is missing')

      const response = await fetch(
        `${apiUrl}/api/private/insurances/${fundInsurance.id}/fund-allocation`,
        {
          method: 'PUT',
          headers,
          body: JSON.stringify({
            allocation: [
              { fundName: 'Global Index', percent: allocation.value },
              { fundName: 'Svenska Aktier', percent: 100 - allocation.value },
            ],
          }),
        },
      )
      if (!response.ok) throw new Error('Could not save fund allocation')

      const insuranceDetail = (await response.json()) as InsuranceDetail
      const globalIndex = insuranceDetail.fundHoldings.find(
        (holding) => holding.fundName === 'Global Index',
      )
      if (globalIndex) allocation.value = Number(globalIndex.allocationPercent)
      confirmed.value = true
    } catch {
      error.value = t('Fondfördelningen kunde inte sparas.')
    }
  }
  const manageEmployee = (employee: Employee): void => {
    selectedEmployee.value = employee
    salaryDraft.value = Number(employee.salary.replace(/[^0-9]/g, '')) || 0
    employeeAction.value = 'salary'
    error.value = ''
  }
  const saveEmployee = async (): Promise<void> => {
    if (!selectedEmployee.value) return
    saving.value = true
    error.value = ''
    try {
      if (!useApi) {
        if (employeeAction.value === 'salary')
          selectedEmployee.value.salary = `${salaryDraft.value.toLocaleString('sv-SE')} ${t('kr/mån')}`
        if (employeeAction.value === 'leave') selectedEmployee.value.status = t('Tjänstledig')
        if (employeeAction.value === 'end') selectedEmployee.value.status = t('Avslutad')
      } else {
        if (!selectedEmployee.value.id) throw new Error('Employment is missing')
        const action = employeeAction.value
        const body =
          action === 'salary'
            ? { salary: salaryDraft.value }
            : action === 'leave'
              ? { reason: leaveReason.value, until: leaveUntil.value }
              : { endsOn: endDate.value }
        const response = await fetch(
          companyRequest(`employments/${selectedEmployee.value.id}/${action === 'salary' ? 'salary' : action}`),
          {
            method: 'PUT',
            headers: {
              Authorization: `Bearer ${session.sessionToken}`,
              'Content-Type': 'application/json',
            },
            body: JSON.stringify(body),
          },
        )
        if (!response.ok) throw new Error('Could not save employment')
        const updated = toEmployee((await response.json()) as CompanyEmployment)
        const index = employees.value.findIndex((item) => item.id === updated.id)
        if (index >= 0) employees.value.splice(index, 1, updated)
      }
      message.value = t('Ändringen har sparats.')
      selectedEmployee.value = null
    } catch {
      error.value = t('Medarbetaren kunde inte registreras.')
    } finally {
      saving.value = false
    }
  }
  const addEmployee = async (draft: {
    name: string
    planId: string
    salary: number
    startsOn: string
  }): Promise<string | undefined> => {
    try {
      if (!useApi) {
        employees.value.push({
          name: draft.name,
          plan: planOptions.value.find((plan) => plan.id === draft.planId)?.name ?? '',
          salary: `${draft.salary.toLocaleString('sv-SE')} ${t('kr/mån')}`,
          status: t('Aktiv'),
        })
      } else {
        const response = await fetch(companyRequest('employees'), {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${session.sessionToken}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(draft),
        })
        if (!response.ok) throw new Error('Could not add employee')
        employees.value.push(toEmployee((await response.json()) as CompanyEmployment))
      }
      search.value = ''
      message.value = t('Medarbetaren är registrerad.')
      return undefined
    } catch {
      return t('Medarbetaren kunde inte registreras.')
    }
  }
  const approveCase = async (item: Case): Promise<void> => {
    if (!session.can('APPROVE_CASE')) return
    try {
      if (useApi) {
        const response = await fetch(companyRequest(`cases/${item.id}/approve`), {
          method: 'PUT',
          headers: { Authorization: `Bearer ${session.sessionToken}` },
        })
        if (!response.ok) throw new Error('Could not approve case')
        const updated = toCase((await response.json()) as CompanyCase)
        const index = cases.value.findIndex((caseItem) => caseItem.id === updated.id)
        if (index >= 0) cases.value.splice(index, 1, updated)
      } else {
        item.status = t('Godkänd')
      }
      message.value = t('Ärendet har godkänts.')
    } catch {
      error.value = t('Medarbetaren kunde inte registreras.')
    }
  }
  const reset = (): void => {
    allocation.value = 60
    confirmed.value = false
    message.value = ''
  }

  return {
    allocation,
    confirmed,
    insurance,
    employees,
    search,
    selectedEmployee,
    employeeAction,
    salaryDraft,
    leaveReason,
    leaveUntil,
    endDate,
    saving,
    error,
    message,
    cases,
    plans,
    filteredEmployees,
    overviewMetrics,
    activityRows,
    documentRows,
    leaveReasons,
    planOptions,
    loadCompanyData,
    loadFundAllocation,
    confirmAllocation,
    manageEmployee,
    saveEmployee,
    addEmployee,
    approveCase,
    reset,
  }
})
