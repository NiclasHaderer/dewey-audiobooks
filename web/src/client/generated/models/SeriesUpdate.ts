import type { SeriesField } from "./SeriesField"
import type { UUID } from "./UUID"

export interface SeriesUpdate {
  books?: Array<UUID>
  cover?: string | null
  description?: string | null
  primaryWorks?: number | null
  provider?: string | null
  providerID?: string | null
  title?: string
  totalBooks?: number | null
  unlock?: Array<SeriesField>
}
