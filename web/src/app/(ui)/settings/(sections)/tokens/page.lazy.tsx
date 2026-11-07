import { AccessTokens } from "@dewey/components/account/access-tokens"
import { SettingsSection } from "@dewey/components/settings/settings-section"
import { useCurrentUser } from "@dewey/queries/current-user-queries"

export const SettingsTokensOutlet = () => {
  const { data: user } = useCurrentUser()
  return (
    user && (
      <SettingsSection title="Access tokens">
        <AccessTokens user={user} />
      </SettingsSection>
    )
  )
}
