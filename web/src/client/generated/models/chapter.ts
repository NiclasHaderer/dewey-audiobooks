import type { UUID } from "./uuid"

export interface Chapter {
  endMs: number
  startMs: number
  title: string | null
  trackId: UUID
}
