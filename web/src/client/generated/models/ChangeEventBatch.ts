import type { ChangeEvent } from "./ChangeEvent"

export interface ChangeEventBatch {
  events: Array<ChangeEvent>
  missedPreviousEvents: boolean
}
