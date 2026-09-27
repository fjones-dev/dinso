export type Role =
  | 'PRIVATE_CUSTOMER'
  | 'COMPANY_ADMIN'
  | 'COMPANY_VIEWER'
  | 'SYSTEM_ADMIN'
export type Portal = 'PRIVATE' | 'COMPANY' | 'SYSTEM'
export type CompanyAction =
  | 'READ'
  | 'APPROVE_CASE'
  | 'ADD_EMPLOYEE'
  | 'CHANGE_SALARY'
  | 'REGISTER_LEAVE'
  | 'END_EMPLOYMENT'
export type ProfilePortal = Portal
export interface Profile {
  id: string
  name: string
  role: Role
  portal: ProfilePortal
  portals?: Portal[]
  description: string
  preview: string
  company?: string
  companies?: string[]
}
export type PrivateOverviewMetricLayout = 'cards' | 'bottom-bar'

export interface Customer {
  key: string
  name: string
  locales: readonly string[]
  defaultLocale: string
  profiles: Profile[]
  insurance: {
    name: string
    type: string
    value: string
    status: string
    detail: string
  }[]
  transactions: {
    date: string
    title: string
    amount: string
    status: string
  }[]
  documents: { name: string; date: string; kind: string }[]
  employments: { name: string; plan: string; salary: string; status: string }[]
  rules: {
    maxFunds: number
    leaveMonths: number
    leaveTypes: string[]
    fees: boolean
    company: boolean
  }
  privateOverviewMetricLayout: PrivateOverviewMetricLayout
}
export const employees = (firstPlan: string, secondPlan: string) =>
  [
    'Alva Norberg',
    'Mio Sten',
    'Tilde Rask',
    'Hugo Dahl',
    'Nora Holst',
    'Ivar Holm',
    'Saga Mark',
    'Leo Nyberg',
  ].map((name, index) => ({
    name,
    plan: index % 2 ? secondPlan : firstPlan,
    salary: `${38 + index * 3} 000 kr/mån`,
    status: ['Aktiv', 'Aktiv', 'Tjänstledig', 'Kommande'][index % 4],
  }))
