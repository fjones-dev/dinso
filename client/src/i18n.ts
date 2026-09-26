import { ref } from 'vue'
import customer from '@customer/config'

type Messages = Record<string, string>

export const activeLocale = ref(customer.defaultLocale)

const localeModules = import.meta.glob<Messages>(
  '../customers/*/locales/*.json',
  {
    eager: true,
    import: 'default',
  },
)

function customerMessages(locale: string): Messages {
  const path = `../customers/${__CUSTOMER__}/locales/${locale}.json`
  return localeModules[path] ?? {}
}

const enumLabels: Record<string, string> = {
  ACTIVE: 'Aktiv',
  INACTIVE: 'Inaktiv',
  PENDING: 'Väntar',
  COMPLETED: 'Genomförd',
  APPROVED: 'Godkänd',
  BOOKED: 'Bokförd',
  ONGOING: 'Pågående',
  UPCOMING: 'Kommande',
  AVAILABLE: 'Tillgängligt',
  FUND: 'Fondförsäkring',
  TRADITIONAL: 'Traditionell försäkring',
  RISK: 'Riskförsäkring',
  PAYMENT: 'Utbetalning',
  PAYING: 'Utbetalning pågår',
  LEAVE: 'Tjänstledig',
  ENDED: 'Avslutad',
  READ: 'Läsa information',
  APPROVE_CASE: 'Godkänna ärenden',
  ADD_EMPLOYEE: 'Lägga till medarbetare',
  CHANGE_SALARY: 'Ändra lön',
  REGISTER_LEAVE: 'Registrera tjänstledighet',
  END_EMPLOYMENT: 'Avsluta anställning',
}

export function translate(
  locale: string,
  source: string,
  values: Record<string, string | number> = {},
): string {
  const normalizedSource = enumLabels[source] ?? source
  const message = customerMessages(locale)[normalizedSource] ?? normalizedSource
  return Object.entries(values).reduce(
    (result, [key, value]) => result.replaceAll(`{${key}}`, String(value)),
    message,
  )
}
