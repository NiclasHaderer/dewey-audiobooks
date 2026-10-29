import type { TitledId } from "./titled-id"
import type { UUID } from "./uuid"

export interface Track {
  book: TitledId
  durationMs: number
  fileModifiedAt: string
  id: UUID
  title: string
  trackNr: number
}
