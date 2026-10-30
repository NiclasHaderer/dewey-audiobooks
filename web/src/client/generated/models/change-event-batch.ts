import type { ChangeEvent } from "./change-event"

export interface ChangeEventBatch {
  events: Array<ChangeEvent>
  missedPreviousEvents: boolean
}
