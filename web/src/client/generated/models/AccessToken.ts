import type { AccessTokenPermissions } from "./AccessTokenPermissions"
import type { UUID } from "./UUID"

export interface AccessToken {
  createdAt: string
  expiresAt: string | null
  id: UUID
  lastUsedAt: string | null
  name: string
  permissions: AccessTokenPermissions
}
