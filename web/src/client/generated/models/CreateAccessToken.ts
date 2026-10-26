import type { AccessTokenPermissions } from "./AccessTokenPermissions"

export interface CreateAccessToken {
  expiresAt: string | null
  name: string
  password: string
  permissions: AccessTokenPermissions
}
