import { cn } from "cn"
import { SettingsIcon } from "lucide-react"
import { FC, ReactNode, useEffect } from "react"
import { useLocation } from "wouter"
import { CollapsibleLabel } from "@dewey/components/menu/collapsible-label"
import { adminItems, adminPaths, licensesItem, userItems } from "@dewey/components/menu/settings-nav"
import { SideMenu } from "@dewey/components/menu/side-menu"
import { useBreakpoint } from "@dewey/hooks/use-media-query"
import { useCurrentUser } from "@dewey/queries/current-user-queries"

export const SettingsLayout: FC<{ children: ReactNode }> = ({ children }) => {
  const { data: user } = useCurrentUser()
  const isDesktop = useBreakpoint("md")
  const [pathname, navigate] = useLocation()

  const isAdmin = user?.permissions.isAdmin ?? false

  // Account is available to everyone; the admin sections are not. Bounce a
  // non-admin who lands on an admin-only path back to their account page.
  useEffect(() => {
    if (user && !isAdmin && adminPaths.some(p => pathname.startsWith(p))) navigate("/settings/account")
  }, [user, isAdmin, pathname, navigate])

  // Wait until we know who the user is before rendering the (partly admin-only) nav.
  if (!user) return null

  const navItems = isAdmin ? [...userItems, ...adminItems] : userItems

  return (
    <div className="flex min-h-0 grow overflow-hidden max-md:-mb-(--bottom-height)">
      {isDesktop ? (
        <SideMenu
          items={[...navItems, licensesItem]}
          header={collapsed => (
            <div className={cn("flex h-12 items-center gap-3 pl-4", collapsed ? "pr-0" : "pr-3")}>
              <SettingsIcon className="text-muted-foreground size-4 shrink-0" />
              <CollapsibleLabel collapsed={collapsed}>
                <span className="truncate text-sm font-medium">Settings</span>
              </CollapsibleLabel>
            </div>
          )}
        />
      ) : null}
      <main className="flex min-w-0 grow flex-col overflow-y-auto p-4 pb-4 max-md:pb-[calc(var(--bottom-height)+--spacing(4))] sm:p-8 md:pb-8">
        {children}
      </main>
    </div>
  )
}
