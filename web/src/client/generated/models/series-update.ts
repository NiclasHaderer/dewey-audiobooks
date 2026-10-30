import type { SeriesField } from "./series-field"
import type { UUID } from "./uuid"

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
