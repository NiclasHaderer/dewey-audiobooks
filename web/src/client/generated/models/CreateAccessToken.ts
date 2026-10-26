import type { AccessTokenPermissions } from "./AccessTokenPermissions"

export interface CreateAccessToken {
  expiresAt: string | null
  name: string
  permissions: AccessTokenPermissions
}
