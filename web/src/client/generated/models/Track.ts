import type { TitledId } from "./TitledId"
import type { UUID } from "./UUID"

export interface Track {
  book: TitledId
  durationMs: number
  fileModifiedAt: string
  id: UUID
  title: string
  trackNr: number
}
