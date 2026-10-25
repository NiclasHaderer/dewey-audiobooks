import type { UUID } from "./UUID"

export interface DeweyUserWithPermissions<PERMISSIONS extends NonNullable<unknown>> {
  id: UUID
  permissions: PERMISSIONS
  username: string
}
