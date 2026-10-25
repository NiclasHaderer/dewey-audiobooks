import type { UUID } from "./UUID"

export interface Chapter {
  endMs: number
  startMs: number
  title: string | null
  trackId: UUID
}
