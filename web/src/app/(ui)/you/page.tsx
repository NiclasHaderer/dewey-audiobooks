import { cn } from "cn"
import { ChevronRightIcon, LogOutIcon, UserIcon } from "lucide-react"
import { Link } from "@dewey/components/link.tsx"
import { MobileTopBar } from "@dewey/components/menu/mobile-top-bar"
import { adminItems, licensesItem, userItems } from "@dewey/components/menu/settings-nav"
import { Avatar, AvatarFallback } from "@dewey/components/ui/avatar"
import { rowInteraction } from "@dewey/lib/interaction-styles"
import { useCurrentUser } from "@dewey/queries/current-user-queries"

export const YouOutlet = () => {
  const { data: user } = useCurrentUser()

  const isAdmin = user?.permissions?.isAdmin ?? false
  const entries = [...userItems, ...(isAdmin ? adminItems : [])]

  return (
    <div className="mx-auto flex min-h-full w-full max-w-3xl flex-col px-5 pb-4 md:pt-4">
      <MobileTopBar className="bg-background/75 md:hidden" />

      <div className="flex flex-col items-center gap-2 py-6 text-center">
        <Avatar className="size-16">
          <AvatarFallback className="text-xl font-medium">
            {user?.username ? user.username.charAt(0).toUpperCase() : <UserIcon className="size-6" />}
          </AvatarFallback>
        </Avatar>
        <div className="min-w-0">
          <div className="truncate text-lg font-semibold">{user?.username ?? "Account"}</div>
          <div className="text-muted-foreground text-sm">{isAdmin ? "Admin" : "User"}</div>
        </div>
      </div>

      <ul className="divide-border divide-y">
        {entries.map(({ href, Icon, label }) => (
          <li key={href}>
            <Link href={href} className={cn(rowInteraction, "-mx-2 flex h-14 items-center gap-3 px-2")}>
              <Icon aria-hidden className="text-muted-foreground size-5 shrink-0" />
              <span className="text-sm font-medium">{label}</span>
              <ChevronRightIcon aria-hidden className="text-muted-foreground ml-auto size-4" />
            </Link>
          </li>
        ))}
        <li>
          <Link
            href="/logout"
            className={cn(rowInteraction, "text-destructive -mx-2 flex h-14 items-center gap-3 px-2")}
          >
            <LogOutIcon aria-hidden className="size-5 shrink-0" />
            <span className="text-sm font-medium">Log out</span>
          </Link>
        </li>
      </ul>

      <Link
        href={licensesItem.href}
        className={cn(rowInteraction, "text-muted-foreground -mx-2 mt-auto flex h-14 items-center gap-3 px-2")}
      >
        <span className="text-sm">{licensesItem.label}</span>
      </Link>
    </div>
  )
}
