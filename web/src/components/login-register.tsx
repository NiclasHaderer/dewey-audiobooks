import { RefreshCwIcon, LockIcon, UserIcon } from "lucide-react"
import { FC, useEffect, useRef, useState } from "react"
import { toast } from "sonner"
import { useLocation, useSearch } from "wouter"
import { session } from "@dewey/client"
import { Logo } from "@dewey/components/icons/logo"
import { ManagedInput } from "@dewey/components/input/managed-input"
import { usePasswordToggle } from "@dewey/components/input/password-toggle"
import { Link } from "@dewey/components/link.tsx"
import { Button } from "@dewey/components/ui/button"
import { Form, useForm } from "@dewey/hooks/use-form"

export const LoginRegister: FC<{ type: "register" | "login"; redirectPath?: string }> = ({ type, redirectPath }) => {
  const isRegister = type === "register"
  const [, navigate] = useLocation()
  const search = useSearch()

  const passwordRef = useRef("")
  const form = useForm(
    {
      username: "",
      password: "",
      confirmPassword: "",
    },
    {
      validate: {
        username: value => value.length > 0 || "Username is required",
        password: value => {
          if (value.length === 0) return "Password is required"
          if (isRegister && value.length < 6) return "Password must be at least 6 characters long"
          return true
        },
        confirmPassword: value => {
          if (!isRegister) return true
          if (value.length === 0) return "Please confirm your password"
          return value === passwordRef.current || "Passwords do not match"
        },
      },
    }
  )
  useEffect(() => {
    passwordRef.current = form.fields.password
  }, [form.fields.password])

  const password = usePasswordToggle()
  const [submitting, setSubmitting] = useState(false)

  const loginOrRegister = async ({ confirmPassword: _confirmPassword, ...credentials }: (typeof form)["fields"]) => {
    setSubmitting(true)
    try {
      await (isRegister ? session.register : session.login)(credentials)
      navigate(redirectPath || "/libraries", { replace: true })
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Something went wrong")
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-dvh w-full flex-col items-center justify-center gap-4 p-6">
      <div className="relative flex w-full max-w-sm">
        <h1 className="mb-4 font-serif text-3xl font-extrabold">{isRegister ? "Register" : "Login"}</h1>
        <div>
          <Logo className="absolute right-0 bottom-0 h-auto w-1/6" />
        </div>
      </div>
      <Form form={form} onSubmit={loginOrRegister}>
        <div className="sm:bg-card flex w-full max-w-sm flex-col gap-3 sm:rounded-xl sm:p-6">
          <ManagedInput
            name="username"
            groupClassName="bg-popover"
            placeholder={isRegister ? "Choose a username" : "Your username"}
            leftIcon={<UserIcon />}
            autoComplete="username"
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            autoFocus={true}
          />
          <ManagedInput
            name="password"
            type={password.type}
            groupClassName="bg-popover"
            placeholder={isRegister ? "At least 6 characters" : "Your password"}
            leftIcon={<LockIcon />}
            autoComplete={isRegister ? "new-password" : "current-password"}
            rightIcon={password.toggle}
          />
          {isRegister ? (
            <ManagedInput
              name="confirmPassword"
              type={password.type}
              groupClassName="bg-popover"
              placeholder="Re-enter your password"
              leftIcon={<LockIcon />}
              autoComplete="new-password"
            />
          ) : null}
          <Button type="submit" size="lg" isDisabled={submitting} className="w-full disabled:opacity-60">
            {submitting ? <RefreshCwIcon className="animate-spin" /> : isRegister ? "Register" : "Login"}
          </Button>
          <p className="text-muted-foreground text-center text-sm">
            {isRegister ? "Already have an account?" : "Don't have an account?"}{" "}
            <Link
              href={isRegister ? `/login?${search}` : `/register?${search}`}
              className="text-foreground focus-visible:ring-primary/60 rounded font-medium underline focus-visible:ring-2 focus-visible:outline-none"
            >
              {isRegister ? "Login" : "Register"}
            </Link>
          </p>
        </div>
      </Form>
    </div>
  )
}
